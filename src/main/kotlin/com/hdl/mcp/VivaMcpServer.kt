package com.hdl.mcp

import com.hdl.vivado.PredefinedCommand
import com.hdl.vivado.PredefinedCommandLibrary
import com.hdl.vivado.TclBridgeService
import com.hdl.vivado.VivadoProcessManager
import com.hdl.vivado.VivadoSettingsState
import com.hdl.vivado.VivadoStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Embedded HTTP server implementing the Model Context Protocol (MCP).
 * Binds to 127.0.0.1 only (loopback). Exposes all PredefinedCommandLibrary commands as MCP tools.
 */
@Service(Service.Level.PROJECT)
class VivaMcpServer(private val project: Project) : Disposable {

    private var httpServer: HttpServer? = null   // null when the server is stopped

    @Volatile private var rawTclEnabled = false  // whether raw-Tcl tools are permitted this session

    // Tools that execute arbitrary Tcl; gated behind the per-session raw-Tcl permission.
    private val rawTclTools = setOf("runTclRaw", "runTclScript")

    // Whether the HTTP server is currently bound and serving.
    val isRunning: Boolean get() = httpServer != null

    // Whether raw-Tcl tools are permitted for the current session.
    val rawTclAllowed: Boolean get() = rawTclEnabled

    // ---- Lifecycle ----

    // Bind a loopback HTTP server on the configured port. rawTclEnabled gates the
    // arbitrary-Tcl tools (runTclRaw / runTclScript) for this session.
    fun start(rawTclEnabled: Boolean) {
        stop()
        this.rawTclEnabled = rawTclEnabled
        val settings = VivadoSettingsState.getInstance(project)
        val port = settings.mcpPort
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { ex -> handleRequest(ex) }   // single front door for every request
        server.executor = Executors.newCachedThreadPool()       // each request handled on a pool thread
        server.start()
        httpServer = server
    }

    // Tear down the HTTP server; safe to call when already stopped.
    fun stop() {
        httpServer?.stop(0)
        httpServer = null
    }

    // Loopback URL an MCP client points at; recomputed from settings each access.
    val serverUrl: String
        get() {
            val port = VivadoSettingsState.getInstance(project).mcpPort
            return "http://127.0.0.1:$port"
        }

    // ---- HTTP entry point + JSON-RPC dispatch ----

    // Single handler for every request: applies CORS, then routes on the JSON-RPC "method" field.
    private fun handleRequest(ex: HttpExchange) {
        try {
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
            ex.responseHeaders.add("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
            ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")

            // CORS preflight: answer immediately, never reaches the JSON-RPC switch.
            if (ex.requestMethod == "OPTIONS") {
                sendResponse(ex, 204, "")
                return
            }

            // HTTP-verb gate (distinct from the JSON-RPC method below); reject anything but POST/GET.
            if (ex.requestMethod != "POST" && ex.requestMethod != "GET") {
                sendResponse(ex, 405, errorResponse(null, -32700, "Method not allowed"))
                return
            }

            val body = ex.requestBody.bufferedReader(Charsets.UTF_8).readText()

            // Parse minimal JSON-RPC fields — "method" selects the operation, "id" is echoed back.
            val method = extractStringField(body, "method")
            val idRaw = extractField(body, "id")
            val id = idRaw?.trim()

            // Dispatch on the JSON-RPC method string carried in the body (not the HTTP verb).
            when (method) {
                // Handshake: advertise protocol version + tools capability back to the client.
                "initialize" -> {
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"VivaCo-Term MCP","version":"0.3.0"}}}"""
                    sendResponse(ex, 200, resp)
                }
                // Client's post-handshake ack — a notification, so no result body is expected.
                "notifications/initialized" -> {
                    sendResponse(ex, 200, "")
                }
                // Catalogue: every PredefinedCommandLibrary command rendered as an MCP tool schema.
                "tools/list" -> {
                    val toolsJson = buildToolsListJson()
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":{"tools":$toolsJson}}"""
                    sendResponse(ex, 200, resp)
                }
                // The only branch that actually runs TCL — delegated to the dedicated handler.
                "tools/call" -> {
                    handleToolCall(ex, body, id)
                }
                // Liveness probe: empty result is enough.
                "ping" -> {
                    sendResponse(ex, 200, """{"jsonrpc":"2.0","id":$id,"result":{}}""")
                }
                // Unknown method → standard JSON-RPC "method not found" error code.
                else -> {
                    sendResponse(ex, 200, errorResponse(id, -32601, "Method not found: $method"))
                }
            }
        } catch (e: Exception) {
            try {
                sendResponse(ex, 500, errorResponse(null, -32603, "Internal error: ${e.message}"))
            } catch (_: Exception) {}
        }
    }

    // Resolve a tools/call into a library command, validate args, run it through the bridge, reply.
    // Each guard returns early with a JSON-RPC/tool error rather than throwing.
    private fun handleToolCall(ex: HttpExchange, body: String, id: String?) {
        // Guard: request must carry a params object.
        val params = extractObjectField(body, "params") ?: run {
            sendResponse(ex, 200, errorResponse(id, -32602, "Missing params"))
            return
        }

        // Guard: params must name a tool.
        val toolName = extractStringField(params, "name") ?: run {
            sendResponse(ex, 200, errorResponse(id, -32602, "Missing tool name"))
            return
        }

        // Guard: tool name must map to a known library command.
        val command = PredefinedCommandLibrary.findById(toolName) ?: run {
            sendResponse(ex, 200, toolCallResult(id, "Unknown tool: $toolName", isError = true))
            return
        }

        // Guard: arbitrary-Tcl tools require the raw-Tcl session permission.
        if (command.id in rawTclTools && !rawTclEnabled) {
            sendResponse(ex, 200, toolCallResult(id,
                "Raw Tcl is disabled for this MCP session. Restart the MCP server with raw Tcl enabled to use '${command.id}'.",
                isError = true))
            return
        }

        // Guard: refuse to run TCL unless a live Vivado session is up (checked via statusFlow).
        val manager = VivadoProcessManager.getInstance(project)
        if (manager.statusFlow.value != VivadoStatus.RUNNING) {
            sendResponse(ex, 200, toolCallResult(id,
                "Vivado is not running. Please launch Vivado from the Vivado Console panel first.",
                isError = true))
            return
        }

        val argsJson = extractObjectField(params, "arguments") ?: "{}"   // absent arguments → empty object
        val args = parseJsonObject(argsJson).toMutableMap<String, Any>()  // mutable so defaults can be injected

        // Validate required parameters
        for (param in command.parameters.filter { it.required }) {
            if (!args.containsKey(param.name)) {
                sendResponse(ex, 200, toolCallResult(id,
                    "Missing required parameter '${param.name}': ${param.description}",
                    isError = true))
                return
            }
        }

        // Apply defaults for optional parameters
        for (param in command.parameters.filter { !it.required && it.default != null }) {
            if (!args.containsKey(param.name)) args[param.name] = param.default!!
        }

        // Render the final TCL string; generator may throw on bad parameter values.
        val tcl = try {
            command.tclGenerator(args)
        } catch (e: Exception) {
            sendResponse(ex, 200, toolCallResult(id, "Parameter error: ${e.message}", isError = true))
            return
        }

        // Echo the AI-issued command into the CoTerm panel (first line only, "..." if multi-line).
        val bridge = TclBridgeService.getInstance(project)
        bridge.publishOutput("[AI] $toolName: ${tcl.lines().first()}${if (tcl.lines().size > 1) "..." else ""}")

        // Block this pool thread on the bridge's serialized queue until Vivado returns the output.
        val output = try {
            runBlocking { bridge.sendCommand(tcl).await() }
        } catch (e: Exception) {
            sendResponse(ex, 200, toolCallResult(id, e.message ?: "Command failed", isError = true))
            return
        }

        sendResponse(ex, 200, toolCallResult(id, output, isError = false))
    }

    // ---- MCP tool/schema serialization ----

    // Render the whole command library as a JSON array of MCP tool descriptors.
    private fun buildToolsListJson(): String {
        val tools = PredefinedCommandLibrary.commands
            .filter { rawTclEnabled || it.id !in rawTclTools }   // hide gated tools when raw Tcl is off
            .map { cmd ->
            val schema = buildInputSchema(cmd)
            McpToolDescriptor(cmd.id, cmd.description, schema)
        }
        return "[" + tools.joinToString(",") { t ->
            """{"name":${jsonStr(t.name)},"description":${jsonStr(t.description)},"inputSchema":${t.inputSchema}}"""
        } + "]"
    }

    // Build a JSON-Schema "object" describing one command's parameters (types + required list).
    private fun buildInputSchema(cmd: PredefinedCommand): String {
        if (cmd.parameters.isEmpty()) {
            return """{"type":"object","properties":{},"required":[]}"""
        }
        // Map each internal ParameterType to its JSON-Schema type keyword.
        val props = cmd.parameters.joinToString(",") { p ->
            val typeStr = when (p.type) {
                com.hdl.vivado.ParameterType.INT -> "integer"
                com.hdl.vivado.ParameterType.BOOLEAN -> "boolean"
                else -> "string"
            }
            """${jsonStr(p.name)}:{"type":"$typeStr","description":${jsonStr(p.description)}}"""
        }
        val required = cmd.parameters.filter { it.required }.joinToString(",") { jsonStr(it.name) }
        return """{"type":"object","properties":{$props},"required":[$required]}"""
    }

    // ---- Response envelope helpers ----

    // Wrap tool output as an MCP "result" with a single text content block; isError flags failures.
    private fun toolCallResult(id: String?, text: String, isError: Boolean): String {
        val content = """[{"type":"text","text":${jsonStr(text.trim())}}]"""
        return """{"jsonrpc":"2.0","id":$id,"result":{"content":$content,"isError":$isError}}"""
    }

    // Build a JSON-RPC error envelope (protocol-level failures, not tool failures).
    private fun errorResponse(id: String?, code: Int, msg: String): String =
        """{"jsonrpc":"2.0","id":${id ?: "null"},"error":{"code":$code,"message":${jsonStr(msg)}}}"""

    // Write status + UTF-8 body and close the exchange (use{} guarantees the stream is closed).
    private fun sendResponse(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    // -------------------------------------------------------------------------
    // Minimal JSON helpers (no external library needed for these simple structures)
    // -------------------------------------------------------------------------

    // Quote + escape a string so it is safe to splice into the hand-built JSON above.
    private fun jsonStr(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")   // backslash first, so later escapes aren't double-escaped
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    // Pull a string-valued field out of raw JSON, unescaping the captured contents.
    private fun extractStringField(json: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*"((?:[^"\\]|\\.)*)"""")   // captures escaped string body
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\\\", "\\")
    }

    // Pull a non-string scalar field (number/bool/raw) up to the next delimiter.
    private fun extractField(json: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*([^,}\]]+)""")   // stop at , } or ]
        return pattern.find(json)?.groupValues?.get(1)?.trim()
    }

    // Extract a nested object value by scanning brace depth (regex can't balance braces).
    private fun extractObjectField(json: String, field: String): String? {
        val idx = json.indexOf("\"$field\"")
        if (idx < 0) return null
        val objStart = json.indexOf('{', idx)   // first '{' after the key
        if (objStart < 0) return null
        var depth = 0
        var i = objStart
        while (i < json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return json.substring(objStart, i + 1) }   // matching close
            }
            i++
        }
        return null
    }

    /** Parse a flat JSON object with string/number values into a String→String map. */
    private fun parseJsonObject(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        // One regex matching "key": followed by a string, number, or true/false/null literal.
        val kvPattern = Regex(""""([^"]+)"\s*:\s*(?:"((?:[^"\\]|\\.)*)"|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|true|false|null)""")
        for (match in kvPattern.findAll(json)) {
            val key = match.groupValues[1]      // group 1: the key
            val strVal = match.groupValues[2]   // group 2: string body (empty if not a string)
            val numVal = match.groupValues[3]   // group 3: numeric literal (empty if not a number)
            val raw = match.value.substringAfter(':').trim()   // fallback: everything after the colon
            // Prefer the string capture, then the number capture, else the de-quoted raw value.
            result[key] = when {
                strVal.isNotEmpty() || raw.startsWith("\"") -> strVal.replace("\\\"", "\"").replace("\\n", "\n")
                numVal.isNotEmpty() -> numVal
                else -> raw.trim('"')
            }
        }
        return result
    }

    // Service teardown hook: stop the HTTP server when the project closes.
    override fun dispose() = stop()

    companion object {
        // Standard IntelliJ project-service accessor.
        fun getInstance(project: Project): VivaMcpServer =
            project.getService(VivaMcpServer::class.java)
    }
}
