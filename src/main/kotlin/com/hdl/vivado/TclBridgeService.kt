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
import java.util.concurrent.CopyOnWriteArrayList

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

    /** Two-way traffic taps — see [TrafficListener]. Empty unless something is recording. */
    private val trafficListeners = CopyOnWriteArrayList<TrafficListener>()

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

                emitInfo("[VivaCo-Term] Bridge connected")
                launch { processCommandLoop() }
            } catch (e: Exception) {
                if (scope.isActive) emitInfo("[VivaCo-Term] Bridge accept error: ${e.message}")
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

    /**
     * Publish a line that came back from Vivado (e.g. process stdout) to the output flow.
     *
     * Delivered on the caller's thread rather than through `scope.launch`: one coroutine per
     * line gives no ordering guarantee between lines, which shuffles both the console and the
     * recorded log. `tryEmit` cannot fail here — the flow has spare buffer and drops its oldest
     * entry when full — so nothing is lost by not suspending.
     */
    fun publishOutput(line: String) {
        _outputFlow.tryEmit(line)
        notifyTraffic(TrafficDirection.RECEIVED, line)
    }

    /**
     * Publish a plugin-generated notice — a status message the console shows but that never
     * travelled over the socket. Kept apart from [publishOutput] so a recording can tell the
     * two apart instead of passing our own chatter off as Vivado output.
     */
    fun publishInfo(line: String) {
        _outputFlow.tryEmit(line)
        notifyTraffic(TrafficDirection.INFO, line)
    }

    /** Register a tap on the two-way traffic. Remove it with [removeTrafficListener] when done. */
    fun addTrafficListener(listener: TrafficListener) {
        trafficListeners.addIfAbsent(listener)
    }

    fun removeTrafficListener(listener: TrafficListener) {
        trafficListeners.remove(listener)
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

    private suspend fun emitReceived(line: String) {
        _outputFlow.emit(line)
        notifyTraffic(TrafficDirection.RECEIVED, line)
    }

    private suspend fun emitInfo(line: String) {
        _outputFlow.emit(line)
        notifyTraffic(TrafficDirection.INFO, line)
    }

    /**
     * Hand an event to every tap. Timestamped here, at the moment the data crosses, so a slow
     * listener cannot skew the log. A throwing listener is ignored — the bridge keeps running.
     */
    private fun notifyTraffic(direction: TrafficDirection, text: String) {
        if (trafficListeners.isEmpty()) return
        val event = TrafficEvent(direction, text, System.currentTimeMillis())
        for (listener in trafficListeners) {
            try {
                listener.onTraffic(event)
            } catch (_: Exception) { /* a broken tap must never take the session down */ }
        }
    }

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
                emitInfo("[VivaCo-Term] $msg")
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
        // Announced here rather than in sendCommand: this is the point the command leaves for
        // Vivado, so a recording shows the queue order the session actually executed in.
        notifyTraffic(TrafficDirection.SENT, tcl)

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
                    emitReceived(content)
                    sb.appendLine(content)
                }
                line.startsWith("ERR:") -> {
                    val content = line.removePrefix("ERR:")
                    emitReceived("ERROR: $content")
                    sb.appendLine(content)
                    hasError = true
                }
                else -> {
                    emitReceived(line)
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
