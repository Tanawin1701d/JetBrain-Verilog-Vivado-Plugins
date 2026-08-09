package com.hdl.mcp

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

    // Per-session bearer token, minted on start(). Loopback binding alone does not keep
    // browsers out, so every request must present this. Null while the server is stopped.
    @Volatile private var authToken: String? = null

    /** The token an MCP client must send as `Authorization: Bearer <token>`; null when stopped. */
    val sessionToken: String? get() = authToken

    // Tools that execute arbitrary Tcl; gated behind the per-session raw-Tcl permission.
    // Defined in the library so a command rename cannot silently disarm the gate.
    private val rawTclTools = PredefinedCommandLibrary.rawTclToolIds

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
        this.authToken = McpAuth.newToken()   // fresh credential per session; never reused
        val settings = VivadoSettingsState.getInstance(project)
        val port = settings.mcpPort
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        server.createContext("/") { ex -> handleRequest(ex) }   // single front door for every request
        server.executor = Executors.newCachedThreadPool()       // each request handled on a pool thread
        server.start()
        httpServer = server
    }

    // Tear down the HTTP server; safe to call when already stopped.
    // Clearing the token means a stopped server cannot be reached even if a
    // client kept the old credential.
    fun stop() {
        httpServer?.stop(0)
        httpServer = null
        authToken = null
    }

    // Loopback URL an MCP client points at; recomputed from settings each access.
    val serverUrl: String
        get() {
            val port = VivadoSettingsState.getInstance(project).mcpPort
            return "http://127.0.0.1:$port"
        }

    // ---- HTTP entry point + JSON-RPC dispatch ----

    // Single handler for every request: authenticates, then routes on the JSON-RPC "method" field.
    //
    // No CORS headers are sent, deliberately. MCP clients are not browsers and do not
    // need them; advertising Access-Control-Allow-Origin: * would invite every page the
    // user has open to drive their Vivado session.
    private fun handleRequest(ex: HttpExchange) {
        try {
            ex.responseHeaders.add("Content-Type", "application/json")

            // Auth gate: browser origins are refused outright, everyone else needs the
            // per-session bearer token shown in the Vivado Console panel.
            val expected = authToken
            if (expected == null) {
                sendResponse(ex, 503, errorResponse(null, -32603, "MCP server is not running"))
                return
            }
            val decision = McpAuth.evaluate(
                hostHeader = ex.requestHeaders.getFirst("Host"),
                originHeader = ex.requestHeaders.getFirst("Origin"),
                authHeader = ex.requestHeaders.getFirst("Authorization"),
                expectedToken = expected
            )
            if (decision != McpAuth.Decision.ALLOW) {
                sendResponse(ex, decision.httpStatus, errorResponse(null, -32600, decision.message))
                return
            }

            // HTTP-verb gate (distinct from the JSON-RPC method below); reject anything but POST/GET.
            if (ex.requestMethod != "POST" && ex.requestMethod != "GET") {
                sendResponse(ex, 405, errorResponse(null, -32700, "Method not allowed"))
                return
            }

            val body = ex.requestBody.bufferedReader(Charsets.UTF_8).readText()

            // Parse minimal JSON-RPC fields — "method" selects the operation, "id" is echoed back.
            val method = McpJson.stringField(body, "method")
            val idRaw = McpJson.scalarField(body, "id")
            val id = idRaw?.trim()

            // Dispatch on the JSON-RPC method string carried in the body (not the HTTP verb).
            when (method) {
                // Handshake: advertise protocol version + tools capability back to the client.
                "initialize" -> {
                    val resp = """{"jsonrpc":"2.0","id":$id,"result":{"protocolVersion":"2024-11-05","capabilities":{"tools":{}},"serverInfo":{"name":"VivaCo-Term MCP","version":"1.0.0"}}}"""
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
        val params = McpJson.objectField(body, "params") ?: run {
            sendResponse(ex, 200, errorResponse(id, -32602, "Missing params"))
            return
        }

        // Guard: params must name a tool.
        val toolName = McpJson.stringField(params, "name") ?: run {
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

        val argsJson = McpJson.objectField(params, "arguments") ?: "{}"   // absent arguments → empty object
        val args = McpJson.flatObject(argsJson).toMutableMap<String, Any>()  // mutable so defaults can be injected

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

        // Catalogue lookups are answered from plugin data, so they short-circuit ahead of the
        // Vivado-is-running guard: the client can browse the command reference and work out
        // what it needs before anyone launches Vivado.
        val localHandler = command.localHandler
        if (localHandler != null) {
            // Best-effort echo — the CoTerm panel may have no session behind it yet.
            try {
                TclBridgeService.getInstance(project).publishInfo("[AI] $toolName")
            } catch (_: Exception) {}

            val answer = try {
                localHandler(args)
            } catch (e: Exception) {
                sendResponse(ex, 200, toolCallResult(id, "Parameter error: ${e.message}", isError = true))
                return
            }
            sendResponse(ex, 200, toolCallResult(id, answer, isError = false))
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

        // Render the final TCL string; generator may throw on bad parameter values.
        val tcl = try {
            command.tclGenerator(args)
        } catch (e: Exception) {
            sendResponse(ex, 200, toolCallResult(id, "Parameter error: ${e.message}", isError = true))
            return
        }

        // Echo the AI-issued command into the CoTerm panel (first line only, "..." if multi-line).
        val bridge = TclBridgeService.getInstance(project)
        bridge.publishInfo("[AI] $toolName: ${tcl.lines().first()}${if (tcl.lines().size > 1) "..." else ""}")

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

    // Render the visible part of the command library as MCP tool descriptors.
    // Gating decided here (it is a permission question); rendering lives in McpSchema.
    private fun buildToolsListJson(): String =
        McpSchema.toolsList(
            PredefinedCommandLibrary.commands
                .filter { rawTclEnabled || it.id !in rawTclTools }   // hide gated tools when raw Tcl is off
        )

    // ---- Response envelope helpers ----

    // Wrap tool output as an MCP "result" with a single text content block; isError flags failures.
    private fun toolCallResult(id: String?, text: String, isError: Boolean): String {
        val content = """[{"type":"text","text":${McpJson.quote(text.trim())}}]"""
        return """{"jsonrpc":"2.0","id":$id,"result":{"content":$content,"isError":$isError}}"""
    }

    // Build a JSON-RPC error envelope (protocol-level failures, not tool failures).
    private fun errorResponse(id: String?, code: Int, msg: String): String =
        """{"jsonrpc":"2.0","id":${id ?: "null"},"error":{"code":$code,"message":${McpJson.quote(msg)}}}"""

    // Write status + UTF-8 body and close the exchange (use{} guarantees the stream is closed).
    private fun sendResponse(ex: HttpExchange, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        ex.sendResponseHeaders(status, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    // Service teardown hook: stop the HTTP server when the project closes.
    override fun dispose() = stop()

    companion object {
        // Standard IntelliJ project-service accessor.
        fun getInstance(project: Project): VivaMcpServer =
            project.getService(VivaMcpServer::class.java)
    }
}
