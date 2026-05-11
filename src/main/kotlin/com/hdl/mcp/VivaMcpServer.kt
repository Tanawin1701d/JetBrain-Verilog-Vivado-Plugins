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

    private var httpServer: HttpServer? = null

    fun start() {
        stop()
        val settings = VivadoSettingsState.getInstance(project)
        if (!settings.mcpEnabled) return

        val port = settings.mcpPort
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { ex -> handleRequest(ex) }
        server.executor = Executors.newCachedThreadPool()
        server.start()
        httpServer = server
    }

    fun stop() {
        httpServer?.stop(0)
        httpServer = null
    }

    val serverUrl: String
        get() {
            val port = VivadoSettingsState.getInstance(project).mcpPort
            return "http://127.0.0.1:$port"
        }

    private fun handleRequest(ex: HttpExchange) {
        try {
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
            ex.responseHeaders.add("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
            ex.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")

            if (ex.requestMethod == "OPTIONS") {
                sendResponse(ex, 204, "")
                return
            }

            if (ex.requestMethod != "POST" && ex.requestMethod != "GET") {
                sendResponse(ex, 405, errorResponse(null, -32700, "Method not allowed"))
                return
            }

            val body = ex.requestBody.bufferedReader(Charsets.UTF_8).readText()

            // Parse minimal JSON-RPC fields
            val method = extractStringField(body, "method")
            val idRaw = extractField(body, "id")
            val id = idRaw?.trim()

            when (method) {
                "initialize" -> {
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"VivaCo-Term MCP","version":"0.3.0"}}}"""
                    sendResponse(ex, 200, resp)
                }
                "notifications/initialized" -> {
                    sendResponse(ex, 200, "")
                }
                "tools/list" -> {
                    val toolsJson = buildToolsListJson()
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":{"tools":$toolsJson}}"""
                    sendResponse(ex, 200, resp)
                }
                "tools/call" -> {
                    handleToolCall(ex, body, id)
                }
                "ping" -> {
                    sendResponse(ex, 200, """{"jsonrpc":"2.0","id":$id,"result":{}}""")
                }
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

    private fun handleToolCall(ex: HttpExchange, body: String, id: String?) {
        val params = extractObjectField(body, "params") ?: run {
            sendResponse(ex, 200, errorResponse(id, -32602, "Missing params"))
            return
        }

        val toolName = extractStringField(params, "name") ?: run {
            sendResponse(ex, 200, errorResponse(id, -32602, "Missing tool name"))
            return
        }

        val command = PredefinedCommandLibrary.findById(toolName) ?: run {
            sendResponse(ex, 200, toolCallResult(id, "Unknown tool: $toolName", isError = true))
            return
        }

        val manager = VivadoProcessManager.getInstance(project)
        if (manager.statusFlow.value != VivadoStatus.RUNNING) {
            sendResponse(ex, 200, toolCallResult(id,
                "Vivado is not running. Please launch Vivado from the Vivado Console panel first.",
                isError = true))
            return
        }

        val argsJson = extractObjectField(params, "arguments") ?: "{}"
        val args = parseJsonObject(argsJson).toMutableMap<String, Any>()

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

        val tcl = try {
            command.tclGenerator(args)
        } catch (e: Exception) {
            sendResponse(ex, 200, toolCallResult(id, "Parameter error: ${e.message}", isError = true))
            return
        }

        val bridge = TclBridgeService.getInstance(project)
        bridge.publishOutput("[AI] $toolName: ${tcl.lines().first()}${if (tcl.lines().size > 1) "..." else ""}")

        val output = try {
            runBlocking { bridge.sendCommand(tcl).await() }
        } catch (e: Exception) {
            sendResponse(ex, 200, toolCallResult(id, e.message ?: "Command failed", isError = true))
            return
        }

        sendResponse(ex, 200, toolCallResult(id, output, isError = false))
    }

    private fun buildToolsListJson(): String {
        val tools = PredefinedCommandLibrary.commands.map { cmd ->
            val schema = buildInputSchema(cmd)
            McpToolDescriptor(cmd.id, cmd.description, schema)
        }
        return "[" + tools.joinToString(",") { t ->
            """{"name":${jsonStr(t.name)},"description":${jsonStr(t.description)},"inputSchema":${t.inputSchema}}"""
        } + "]"
    }

    private fun buildInputSchema(cmd: PredefinedCommand): String {
        if (cmd.parameters.isEmpty()) {
            return """{"type":"object","properties":{},"required":[]}"""
        }
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

    private fun toolCallResult(id: String?, text: String, isError: Boolean): String {
        val content = """[{"type":"text","text":${jsonStr(text.trim())}}]"""
        return """{"jsonrpc":"2.0","id":$id,"result":{"content":$content,"isError":$isError}}"""
    }

    private fun errorResponse(id: String?, code: Int, msg: String): String =
        """{"jsonrpc":"2.0","id":${id ?: "null"},"error":{"code":$code,"message":${jsonStr(msg)}}}"""

    private fun sendResponse(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    // -------------------------------------------------------------------------
    // Minimal JSON helpers (no external library needed for these simple structures)
    // -------------------------------------------------------------------------

    private fun jsonStr(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun extractStringField(json: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        return pattern.find(json)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", "\n")
            ?.replace("\\\\", "\\")
    }

    private fun extractField(json: String, field: String): String? {
        val pattern = Regex(""""$field"\s*:\s*([^,}\]]+)""")
        return pattern.find(json)?.groupValues?.get(1)?.trim()
    }

    private fun extractObjectField(json: String, field: String): String? {
        val idx = json.indexOf("\"$field\"")
        if (idx < 0) return null
        val objStart = json.indexOf('{', idx)
        if (objStart < 0) return null
        var depth = 0
        var i = objStart
        while (i < json.length) {
            when (json[i]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return json.substring(objStart, i + 1) }
            }
            i++
        }
        return null
    }

    /** Parse a flat JSON object with string/number values into a String→String map. */
    private fun parseJsonObject(json: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val kvPattern = Regex(""""([^"]+)"\s*:\s*(?:"((?:[^"\\]|\\.)*)"|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|true|false|null)""")
        for (match in kvPattern.findAll(json)) {
            val key = match.groupValues[1]
            val strVal = match.groupValues[2]
            val numVal = match.groupValues[3]
            val raw = match.value.substringAfter(':').trim()
            result[key] = when {
                strVal.isNotEmpty() || raw.startsWith("\"") -> strVal.replace("\\\"", "\"").replace("\\n", "\n")
                numVal.isNotEmpty() -> numVal
                else -> raw.trim('"')
            }
        }
        return result
    }

    override fun dispose() = stop()

    companion object {
        fun getInstance(project: Project): VivaMcpServer =
            project.getService(VivaMcpServer::class.java)
    }
}
