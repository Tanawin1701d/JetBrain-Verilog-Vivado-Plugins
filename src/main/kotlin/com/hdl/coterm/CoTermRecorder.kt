package com.hdl.coterm

import com.hdl.vivado.TclBridgeService
import com.hdl.vivado.TrafficDirection
import com.hdl.vivado.TrafficEvent
import com.hdl.vivado.TrafficListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Text layout of a session log.
 *
 * Kept free of any file or platform dependency so the format can be asserted on directly:
 * every line is one record, prefixed with the direction, so the log stays greppable
 * (`grep '>>' session.log` gives you the commands that ran, in order).
 */
object SessionLogFormat {

    const val SENT_MARK     = ">>"
    const val RECEIVED_MARK = "<<"
    const val INFO_MARK     = "--"

    /** Full date on every record, not just the time — synthesis runs cross midnight. */
    private val STAMP  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val FULL   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val FOR_ID = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun mark(direction: TrafficDirection): String = when (direction) {
        TrafficDirection.SENT     -> SENT_MARK
        TrafficDirection.RECEIVED -> RECEIVED_MARK
        TrafficDirection.INFO     -> INFO_MARK
    }

    /** Default name for a new log — the start time makes consecutive recordings sort by age. */
    fun defaultFileName(at: LocalDateTime): String = "vivado-console-${FOR_ID.format(at)}.log"

    fun header(projectName: String, startedAt: LocalDateTime): String = buildString {
        appendLine("# VivaCo-Term console log")
        appendLine("# project : $projectName")
        appendLine("# started : ${FULL.format(startedAt)}")
        appendLine("# legend  : $SENT_MARK sent to Vivado" +
            "   $RECEIVED_MARK received from Vivado" +
            "   $INFO_MARK plugin notice")
        appendLine("#")
    }

    /**
     * One event as one or more log lines. A multi-line Tcl command gets the marker repeated on
     * every line, under a single timestamp, so the block reads as one send without breaking
     * line-oriented tooling.
     */
    fun lines(event: TrafficEvent, zone: ZoneId = ZoneId.systemDefault()): String {
        val stamp = STAMP.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(event.timestampMs), zone))
        val mark = mark(event.direction)
        return buildString {
            for (line in event.text.removeSuffix("\n").lines()) {
                appendLine("[$stamp] $mark $line")
            }
        }
    }

    fun footer(stoppedAt: LocalDateTime, stats: RecordingStats): String = buildString {
        appendLine("#")
        appendLine("# stopped : ${FULL.format(stoppedAt)}")
        appendLine("# records : ${stats.total} " +
            "(sent ${stats.sent}, received ${stats.received}, notices ${stats.info})")
    }
}

/** How much has gone into the log so far — shown live next to the record button. */
data class RecordingStats(val sent: Int = 0, val received: Int = 0, val info: Int = 0) {
    val total: Int get() = sent + received + info

    fun plus(direction: TrafficDirection): RecordingStats = when (direction) {
        TrafficDirection.SENT     -> copy(sent = sent + 1)
        TrafficDirection.RECEIVED -> copy(received = received + 1)
        TrafficDirection.INFO     -> copy(info = info + 1)
    }
}

/**
 * The open log file. Buffered rather than written straight through, because Vivado emits output
 * in bursts of thousands of lines during synthesis and the writes happen on the thread reading
 * the socket — [flush] is called on a timer instead, so a crash loses at most a second of log.
 *
 * Every method is synchronized and writes after [close] are ignored, so the recorder can be
 * stopped while the bridge is mid-burst.
 */
class SessionLogWriter private constructor(val file: File, private val out: BufferedWriter) {

    /** Volatile because the record count is read from the EDT while the bridge thread writes it. */
    @Volatile
    var stats: RecordingStats = RecordingStats()
        private set

    private var closed = false

    @Synchronized
    fun write(event: TrafficEvent) {
        if (closed) return
        out.write(SessionLogFormat.lines(event))
        stats = stats.plus(event.direction)
    }

    @Synchronized
    fun writeRaw(text: String) {
        if (closed) return
        out.write(text)
    }

    @Synchronized
    fun flush() {
        if (closed) return
        out.flush()
    }

    @Synchronized
    fun close(stoppedAt: LocalDateTime) {
        if (closed) return
        closed = true
        out.write(SessionLogFormat.footer(stoppedAt, stats))
        out.flush()
        out.close()
    }

    companion object {
        /**
         * Create or reopen [file] and write the header. Missing parent directories are created;
         * anything that goes wrong (no such path, read-only disk) surfaces as an IOException
         * for the caller to report — recording never starts half-open.
         */
        fun open(file: File, append: Boolean, projectName: String, startedAt: LocalDateTime): SessionLogWriter {
            file.absoluteFile.parentFile?.mkdirs()
            val out = BufferedWriter(OutputStreamWriter(FileOutputStream(file, append), Charsets.UTF_8))
            val writer = SessionLogWriter(file, out)
            writer.writeRaw(SessionLogFormat.header(projectName, startedAt))
            writer.flush()
            return writer
        }
    }
}

/**
 * Records everything crossing the Tcl bridge to a file, in both directions, from the moment
 * recording starts until it is stopped.
 *
 * Lives on the project rather than in the console panel so a recording survives the tool window
 * being closed, and so it captures commands issued by an MCP client as well as typed ones — both
 * go through [TclBridgeService], which is where the tap sits.
 */
@Service(Service.Level.PROJECT)
class CoTermRecorder(private val project: Project) : Disposable, TrafficListener {

    @Volatile private var writer: SessionLogWriter? = null

    /** Flushes the buffer on a timer; only alive while recording. */
    private var flushTimer: java.util.Timer? = null

    val isRecording: Boolean get() = writer != null
    val targetFile: File? get() = writer?.file
    val stats: RecordingStats get() = writer?.stats ?: RecordingStats()

    /**
     * Begin recording to [file]. Returns the file actually opened.
     *
     * @param append keep an existing file's contents and add this session below them.
     * @throws java.io.IOException if the file cannot be opened — nothing is started in that case.
     */
    @Synchronized
    fun start(file: File, append: Boolean): File {
        stop()
        val opened = SessionLogWriter.open(file, append, project.name, LocalDateTime.now())
        writer = opened
        TclBridgeService.getInstance(project).addTrafficListener(this)
        startFlushTimer()
        return opened.file
    }

    /** Stop recording and close the file. Returns the file written, or null if not recording. */
    @Synchronized
    fun stop(): File? {
        val open = writer ?: return null
        // Detach first: no event may reach a writer that is on its way out.
        TclBridgeService.getInstance(project).removeTrafficListener(this)
        writer = null
        stopFlushTimer()
        open.close(LocalDateTime.now())
        return open.file
    }

    override fun onTraffic(event: TrafficEvent) {
        val open = writer ?: return
        try {
            open.write(event)
        } catch (e: Exception) {
            // Disk full, unplugged drive — drop the recording rather than throw on every line
            // from here on. writer is cleared before reporting, so the notice this publishes
            // cannot come back around into a half-dead writer.
            stop()
            TclBridgeService.getInstance(project)
                .publishInfo("[VivaCo-Term] Recording stopped — cannot write ${open.file.name}: ${e.message}")
        }
    }

    private fun startFlushTimer() {
        stopFlushTimer()
        flushTimer = java.util.Timer("VivaCoTerm-record-flush", true).apply {
            schedule(object : java.util.TimerTask() {
                override fun run() {
                    try {
                        writer?.flush()
                    } catch (_: Exception) { /* reported by the next write */ }
                }
            }, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS)
        }
    }

    private fun stopFlushTimer() {
        flushTimer?.cancel()
        flushTimer = null
    }

    override fun dispose() {
        stop()
    }

    companion object {
        private const val FLUSH_INTERVAL_MS = 1000L

        fun getInstance(project: Project): CoTermRecorder =
            project.getService(CoTermRecorder::class.java)
    }
}
