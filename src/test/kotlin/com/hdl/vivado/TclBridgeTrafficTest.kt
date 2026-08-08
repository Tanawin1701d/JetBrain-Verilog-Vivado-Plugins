package com.hdl.vivado

import com.intellij.openapi.project.Project
import java.lang.reflect.Proxy
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests the traffic taps the session recorder hangs off.
 *
 * The regression these lock down: console notices used to be painted straight into the text pane
 * by the panel, so lines the user could plainly see — "[AI] Executing: ..." above all — never
 * crossed the bridge and were missing from the recorded log. Anything the console shows now goes
 * out through publishInfo/publishOutput, and that is what a tap sees.
 */
class TclBridgeTrafficTest {

    /**
     * The bridge only reaches for its Project once a command is executed, which needs a live
     * socket; these tests never get that far, so an interface stub is enough to exercise the
     * publish path without an IDE. (A plain null cannot be used — Kotlin null-checks constructor
     * parameters.)
     */
    private fun stubProject(): Project =
        Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { _, method, _ -> if (method.name == "getName") "test" else null } as Project

    private fun bridgeWithTap(): Pair<TclBridgeService, MutableList<TrafficEvent>> {
        val bridge = TclBridgeService(stubProject())
        val seen = Collections.synchronizedList(mutableListOf<TrafficEvent>())
        bridge.addTrafficListener { seen.add(it) }
        return bridge to seen
    }

    @Test
    fun `a plugin notice reaches a tap`() {
        val (bridge, seen) = bridgeWithTap()

        bridge.publishInfo("[AI] Executing: Add IP Repository")

        assertEquals(1, seen.size, "the notice never reached the recorder")
        assertEquals(TrafficDirection.INFO, seen[0].direction)
        assertEquals("[AI] Executing: Add IP Repository", seen[0].text)
    }

    @Test
    fun `vivado output is tagged as received, not as our own notice`() {
        val (bridge, seen) = bridgeWithTap()

        bridge.publishOutput("INFO: [Common 17-206] Opening project...")

        assertEquals(TrafficDirection.RECEIVED, seen.single().direction)
    }

    @Test
    fun `published lines keep their order`() {
        val (bridge, seen) = bridgeWithTap()

        repeat(500) { bridge.publishOutput("line $it") }

        assertEquals(500, seen.size)
        assertEquals(List(500) { "line $it" }, seen.map { it.text })
    }

    @Test
    fun `events are stamped when they cross, not when they are written`() {
        val (bridge, seen) = bridgeWithTap()

        val before = System.currentTimeMillis()
        bridge.publishOutput("a line")
        val after = System.currentTimeMillis()

        val stamp = seen.single().timestampMs
        assertTrue(stamp in before..after, "stamp $stamp outside [$before, $after]")
    }

    @Test
    fun `a removed tap stops receiving`() {
        val bridge = TclBridgeService(stubProject())
        val seen = mutableListOf<TrafficEvent>()
        val tap = TrafficListener { seen.add(it) }

        bridge.addTrafficListener(tap)
        bridge.publishInfo("while recording")
        bridge.removeTrafficListener(tap)
        bridge.publishInfo("after stopping")

        assertEquals(listOf("while recording"), seen.map { it.text })
    }

    /** A recorder that fails mid-session must not take the Vivado session down with it. */
    @Test
    fun `a throwing tap does not break publishing`() {
        val bridge = TclBridgeService(stubProject())
        val seen = mutableListOf<String>()
        bridge.addTrafficListener { error("disk full") }
        bridge.addTrafficListener { seen.add(it.text) }

        bridge.publishOutput("still delivered")

        assertEquals(listOf("still delivered"), seen)
    }
}
