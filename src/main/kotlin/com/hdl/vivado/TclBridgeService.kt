package com.hdl.vivado

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Manages the bidirectional TCP socket bridge between the IDE and the live Vivado process.
 *
 * Architecture: Plugin acts as TCP server; the Vivado startup TCL script connects back as client.
 * This approach works correctly even after Vivado's start_gui is called, because the socket
 * fileevent callbacks are processed by Vivado's Tk event loop — unlike stdin which becomes
 * inaccessible after start_gui.
 */
@Service(Service.Level.PROJECT)
class TclBridgeService(private val project: Project) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var clientSocket: Socket? = null
    @Volatile private var writer: PrintWriter? = null
    @Volatile private var reader: BufferedReader? = null

    private val _outputFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 2048,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    private data class QueuedCommand(
        val tcl: String,
        val deferred: CompletableDeferred<String>
    )
    private val commandChannel = Channel<QueuedCommand>(Channel.UNLIMITED)

    /** Start the bridge TCP server. Returns the port Vivado should connect to. */
    fun startBridgeServer(): Int {
        serverSocket?.close()
        val ss = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        val port = ss.localPort

        scope.launch {
            try {
                val sock = withContext(Dispatchers.IO) { ss.accept() }
                clientSocket = sock
                writer = PrintWriter(BufferedWriter(OutputStreamWriter(sock.outputStream, Charsets.UTF_8)), true)
                reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.UTF_8))

                // Consume the handshake line (%%CONNECTED%%)
                withContext(Dispatchers.IO) { reader!!.readLine() }

                emit("[VivaCo-Term] Bridge connected")
                launch { processCommandLoop() }
            } catch (e: Exception) {
                if (scope.isActive) emit("[VivaCo-Term] Bridge accept error: ${e.message}")
            }
        }

        return port
    }

    /** Enqueue a TCL string for sequential execution. Returns a deferred with the output. */
    fun sendCommand(tcl: String): Deferred<String> {
        val deferred = CompletableDeferred<String>()
        commandChannel.trySend(QueuedCommand(tcl, deferred))
        return deferred
    }

    /** Publish a line directly to the output flow (e.g. process stdout). */
    fun publishOutput(line: String) {
        scope.launch { _outputFlow.emit(line) }
    }

    fun isConnected(): Boolean =
        clientSocket?.let { !it.isClosed && it.isConnected } == true

    fun disconnect() {
        clientSocket?.close()
        serverSocket?.close()
        writer = null
        reader = null
        clientSocket = null
    }

    private suspend fun emit(line: String) = _outputFlow.emit(line)

    private suspend fun processCommandLoop() {
        val settings = VivadoSettingsState.getInstance(project)

        for (queued in commandChannel) {
            val w = writer
            val r = reader
            if (w == null || r == null || clientSocket?.isClosed == true) {
                queued.deferred.completeExceptionally(Exception("Vivado is not connected"))
                continue
            }
            try {
                val timeoutMs = settings.cmdTimeoutMin * 60 * 1000L
                val output = withTimeout(timeoutMs) {
                    sendRaw(queued.tcl, w, r)
                }
                queued.deferred.complete(output)
            } catch (e: TimeoutCancellationException) {
                val msg = "Command timed out after ${settings.cmdTimeoutMin} min"
                emit("[VivaCo-Term] $msg")
                queued.deferred.completeExceptionally(e)
            } catch (e: Exception) {
                queued.deferred.completeExceptionally(e)
            }
        }
    }

    /**
     * Send a TCL string over the socket and collect the response.
     *
     * Protocol:
     *   → each line of the TCL command
     *   → "%%SEND%%"  (signals end-of-command to Vivado)
     *   ← "OUT:<line>" or "ERR:<line>" until "%%PROMPT%%"
     */
    private suspend fun sendRaw(tcl: String, w: PrintWriter, r: BufferedReader): String {
        withContext(Dispatchers.IO) {
            for (line in tcl.lines()) w.println(line)
            w.println("%%SEND%%")
            w.flush()
        }

        val sb = StringBuilder()
        var hasError = false

        while (true) {
            val line = withContext(Dispatchers.IO) { r.readLine() }
                ?: throw Exception("Bridge connection closed")
            when {
                line == "%%PROMPT%%" -> break
                line.startsWith("OUT:") -> {
                    val content = line.removePrefix("OUT:")
                    emit(content)
                    sb.appendLine(content)
                }
                line.startsWith("ERR:") -> {
                    val content = line.removePrefix("ERR:")
                    emit("ERROR: $content")
                    sb.appendLine(content)
                    hasError = true
                }
                else -> {
                    emit(line)
                    sb.appendLine(line)
                }
            }
        }

        val result = sb.toString()
        if (hasError) throw Exception(result)
        return result
    }

    override fun dispose() {
        commandChannel.close()
        disconnect()
        scope.cancel()
    }

    companion object {
        fun getInstance(project: Project): TclBridgeService =
            project.getService(TclBridgeService::class.java)
    }
}
