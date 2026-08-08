package com.hdl.coterm

import com.hdl.vivado.TrafficDirection
import com.hdl.vivado.TrafficEvent
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the console session log.
 *
 * The format matters as much as the content: a recording is read after the fact, often by
 * grepping for the commands that ran, so every record has to stay on its own marked line —
 * including the multi-line block designs the MCP tools send.
 */
class CoTermRecorderTest {

    private val UTC = ZoneId.of("UTC")

    /** 2024-01-02 03:04:05.678 UTC — a fixed instant so the formatted stamps are assertable. */
    private val T0 = 1_704_164_645_678L

    private fun event(direction: TrafficDirection, text: String, at: Long = T0) =
        TrafficEvent(direction, text, at)

    private fun tempLog(): File =
        File.createTempFile("vivacoterm_test_", ".log").also { it.delete() }

    // -------------------------------------------------------------------------
    // Line format
    // -------------------------------------------------------------------------

    @Test
    fun `each direction gets its own marker`() {
        assertEquals(">>", SessionLogFormat.mark(TrafficDirection.SENT))
        assertEquals("<<", SessionLogFormat.mark(TrafficDirection.RECEIVED))
        assertEquals("--", SessionLogFormat.mark(TrafficDirection.INFO))
    }

    @Test
    fun `a record is one timestamped line`() {
        val line = SessionLogFormat.lines(event(TrafficDirection.SENT, "open_project {a.xpr}"), UTC)
        assertEquals("[2024-01-02 03:04:05.678] >> open_project {a.xpr}\n", line)
    }

    @Test
    fun `a multi-line command marks every line under one timestamp`() {
        val tcl = "create_bd_design design_1\nstartgroup\nendgroup"
        val out = SessionLogFormat.lines(event(TrafficDirection.SENT, tcl), UTC)

        val lines = out.trimEnd('\n').lines()
        assertEquals(3, lines.size, "one log line per command line")
        assertTrue(lines.all { it.startsWith("[2024-01-02 03:04:05.678] >> ") }, "got: $lines")
        assertEquals("[2024-01-02 03:04:05.678] >> endgroup", lines[2])
    }

    @Test
    fun `a trailing newline does not produce a blank record`() {
        val out = SessionLogFormat.lines(event(TrafficDirection.RECEIVED, "INFO: done\n"), UTC)
        assertEquals("[2024-01-02 03:04:05.678] << INFO: done\n", out)
    }

    @Test
    fun `default file names carry the start time so recordings sort by age`() {
        val name = SessionLogFormat.defaultFileName(LocalDateTime.of(2024, 1, 2, 3, 4, 5))
        assertEquals("vivado-console-20240102-030405.log", name)
    }

    // -------------------------------------------------------------------------
    // Writing a log
    // -------------------------------------------------------------------------

    @Test
    fun `a log opens with a header and closes with a record count`() {
        val file = tempLog()
        try {
            val writer = SessionLogWriter.open(file, append = false, projectName = "blink",
                startedAt = LocalDateTime.of(2024, 1, 2, 3, 4, 5))
            writer.write(event(TrafficDirection.SENT, "synth_design"))
            writer.write(event(TrafficDirection.RECEIVED, "INFO: synth done"))
            writer.write(event(TrafficDirection.RECEIVED, "WARNING: unconnected pin"))
            writer.write(event(TrafficDirection.INFO, "[VivaCo-Term] Bridge connected"))
            writer.close(LocalDateTime.of(2024, 1, 2, 3, 9, 9))

            val text = file.readText()
            assertTrue(text.startsWith("# VivaCo-Term console log"), text)
            assertTrue(text.contains("# project : blink"), text)
            assertTrue(text.contains("# started : 2024-01-02 03:04:05"), text)
            assertTrue(text.contains("# stopped : 2024-01-02 03:09:09"), text)
            assertTrue(text.contains("# records : 4 (sent 1, received 2, notices 1)"), text)
            assertTrue(text.contains(">> synth_design"), text)
            assertTrue(text.contains("<< WARNING: unconnected pin"), text)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `counts track each direction separately`() {
        val file = tempLog()
        try {
            val writer = SessionLogWriter.open(file, false, "p", LocalDateTime.now())
            repeat(3) { writer.write(event(TrafficDirection.SENT, "cmd $it")) }
            repeat(5) { writer.write(event(TrafficDirection.RECEIVED, "out $it")) }
            assertEquals(3, writer.stats.sent)
            assertEquals(5, writer.stats.received)
            assertEquals(0, writer.stats.info)
            assertEquals(8, writer.stats.total)
            writer.close(LocalDateTime.now())
        } finally {
            file.delete()
        }
    }

    @Test
    fun `append keeps the earlier session and overwrite replaces it`() {
        val file = tempLog()
        try {
            SessionLogWriter.open(file, false, "p", LocalDateTime.now()).apply {
                write(event(TrafficDirection.SENT, "first_session"))
                close(LocalDateTime.now())
            }

            SessionLogWriter.open(file, true, "p", LocalDateTime.now()).apply {
                write(event(TrafficDirection.SENT, "second_session"))
                close(LocalDateTime.now())
            }
            val appended = file.readText()
            assertTrue(appended.contains("first_session"), "append must not drop the earlier log")
            assertTrue(appended.contains("second_session"), appended)

            SessionLogWriter.open(file, false, "p", LocalDateTime.now()).apply {
                write(event(TrafficDirection.SENT, "third_session"))
                close(LocalDateTime.now())
            }
            val replaced = file.readText()
            assertFalse(replaced.contains("first_session"), "overwrite must truncate")
            assertTrue(replaced.contains("third_session"), replaced)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing folders are created rather than failing the recording`() {
        val root = File(System.getProperty("java.io.tmpdir"), "vivacoterm_test_${System.nanoTime()}")
        val file = File(root, "nested/deeper/session.log")
        try {
            SessionLogWriter.open(file, false, "p", LocalDateTime.now()).close(LocalDateTime.now())
            assertTrue(file.exists(), "expected ${file.absolutePath} to be created")
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * The bridge can deliver a line while the user is clicking Stop; a late write must be dropped
     * silently instead of throwing back into the socket reader.
     */
    @Test
    fun `writes after close are ignored`() {
        val file = tempLog()
        try {
            val writer = SessionLogWriter.open(file, false, "p", LocalDateTime.now())
            writer.close(LocalDateTime.now())
            writer.write(event(TrafficDirection.RECEIVED, "late_line"))
            writer.flush()

            assertFalse(file.readText().contains("late_line"))
            assertEquals(0, writer.stats.total)
        } finally {
            file.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    @Test
    fun `stats start empty`() {
        assertEquals(0, RecordingStats().total)
    }

    @Test
    fun `stats add up per direction`() {
        val stats = RecordingStats()
            .plus(TrafficDirection.SENT)
            .plus(TrafficDirection.RECEIVED)
            .plus(TrafficDirection.RECEIVED)
            .plus(TrafficDirection.INFO)
        assertEquals(1, stats.sent)
        assertEquals(2, stats.received)
        assertEquals(1, stats.info)
        assertEquals(4, stats.total)
    }
}
