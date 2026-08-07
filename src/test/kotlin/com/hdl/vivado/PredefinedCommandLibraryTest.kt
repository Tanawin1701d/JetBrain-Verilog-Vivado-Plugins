package com.hdl.vivado

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the command library — the whole Vivado surface exposed to both the
 * Run Command palette and the MCP tool catalogue.
 *
 * Every generator produces Tcl that runs against a real user project, so the
 * cheap invariants here (unique ids, required params enforced, no blank output)
 * are worth more than their line count.
 */
class PredefinedCommandLibraryTest {

    private val commands = PredefinedCommandLibrary.commands

    /** A plausible value for each declared parameter; generators only interpolate. */
    private fun sampleArgs(cmd: PredefinedCommand): Map<String, Any> =
        cmd.parameters.associate { p ->
            p.name to when (p.type) {
                ParameterType.INT     -> "4"
                ParameterType.BOOLEAN -> "true"
                ParameterType.STRING  -> "sample_${p.name}"
            }
        }

    // -------------------------------------------------------------------------
    // Catalogue invariants
    // -------------------------------------------------------------------------

    @Test
    fun `library is not empty`() {
        assertTrue(commands.isNotEmpty())
    }

    @Test
    fun `command ids are unique`() {
        val dupes = commands.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(dupes.isEmpty(), "duplicate command ids: $dupes")
    }

    @Test
    fun `every command has an id name and description`() {
        for (cmd in commands) {
            assertTrue(cmd.id.isNotBlank(), "blank id")
            assertTrue(cmd.name.isNotBlank(), "blank name for ${cmd.id}")
            assertTrue(cmd.description.isNotBlank(), "blank description for ${cmd.id}")
        }
    }

    @Test
    fun `parameter names are unique within a command`() {
        for (cmd in commands) {
            val dupes = cmd.parameters.groupBy { it.name }.filterValues { it.size > 1 }.keys
            assertTrue(dupes.isEmpty(), "${cmd.id} has duplicate parameters: $dupes")
        }
    }

    @Test
    fun `every parameter has a description`() {
        for (cmd in commands) {
            for (p in cmd.parameters) {
                assertTrue(p.description.isNotBlank(), "${cmd.id}.${p.name} has no description")
            }
        }
    }

    @Test
    fun `findById resolves every command and rejects unknown ids`() {
        for (cmd in commands) {
            assertEquals(cmd, PredefinedCommandLibrary.findById(cmd.id))
        }
        assertNull(PredefinedCommandLibrary.findById("noSuchTool"))
    }

    // -------------------------------------------------------------------------
    // The raw-Tcl gate
    // -------------------------------------------------------------------------

    @Test
    fun `every gated raw-Tcl id still resolves to a real command`() {
        // If a command is renamed without updating rawTclToolIds, the MCP gate
        // silently stops matching and arbitrary Tcl becomes reachable.
        for (id in PredefinedCommandLibrary.rawTclToolIds) {
            assertNotNull(PredefinedCommandLibrary.findById(id), "gated id '$id' is not in the library")
        }
    }

    @Test
    fun `the gated set covers the commands that execute arbitrary Tcl`() {
        assertTrue("runTclRaw" in PredefinedCommandLibrary.rawTclToolIds)
        assertTrue("runTclScript" in PredefinedCommandLibrary.rawTclToolIds)
    }

    // -------------------------------------------------------------------------
    // Generators
    // -------------------------------------------------------------------------

    @Test
    fun `every command generates non-blank Tcl from a full argument set`() {
        for (cmd in commands) {
            val tcl = cmd.tclGenerator(sampleArgs(cmd))
            assertTrue(tcl.isNotBlank(), "${cmd.id} generated blank Tcl")
        }
    }

    @Test
    fun `omitting a required parameter is rejected`() {
        for (cmd in commands) {
            for (required in cmd.parameters.filter { it.required }) {
                val incomplete = sampleArgs(cmd) - required.name
                assertFailsWith<Exception>("${cmd.id} accepted a missing '${required.name}'") {
                    cmd.tclGenerator(incomplete)
                }
            }
        }
    }

    @Test
    fun `commands with no required parameters generate from an empty map`() {
        val optionalOnly = commands.filter { cmd -> cmd.parameters.none { it.required } }
        assertTrue(optionalOnly.isNotEmpty())
        for (cmd in optionalOnly) {
            val tcl = cmd.tclGenerator(emptyMap())
            assertTrue(tcl.isNotBlank(), "${cmd.id} generated blank Tcl from an empty map")
        }
    }

    @Test
    fun `optional parameters fall back to their declared default`() {
        // runSynthesis declares jobs with a default of 4.
        val synth = PredefinedCommandLibrary.findById("runSynthesis")!!
        assertEquals("4", synth.parameters.first { it.name == "jobs" }.default.toString())
        assertTrue(synth.tclGenerator(emptyMap()).contains("-jobs 4"))
    }

    // -------------------------------------------------------------------------
    // Specific Tcl shapes worth pinning
    // -------------------------------------------------------------------------

    @Test
    fun `runSynthesis launches and waits on synth_1`() {
        val tcl = PredefinedCommandLibrary.findById("runSynthesis")!!.tclGenerator(mapOf("jobs" to 8))
        assertTrue(tcl.contains("launch_runs synth_1"), tcl)
        assertTrue(tcl.contains("-jobs 8"), tcl)
        assertTrue(tcl.contains("wait_on_run synth_1"), tcl)
    }

    @Test
    fun `generateBitstreamAsync does not block on the run`() {
        // The whole point of the async variant is that it omits wait_on_run,
        // so it cannot pin an MCP call open for the length of the build.
        val tcl = PredefinedCommandLibrary.findById("generateBitstreamAsync")!!.tclGenerator(emptyMap())
        assertTrue(tcl.contains("launch_runs impl_1"), tcl)
        assertTrue(tcl.contains("write_bitstream"), tcl)
        assertTrue(!tcl.contains("wait_on_run"), "async variant must not wait: $tcl")
    }

    @Test
    fun `generateBdWrapper imports the generated wrapper`() {
        // make_wrapper without -import leaves the wrapper out of the fileset,
        // which then fails synthesis with a missing-top error.
        val tcl = PredefinedCommandLibrary.findById("generateBdWrapper")!!
            .tclGenerator(mapOf("bdName" to "design_1"))
        assertTrue(tcl.contains("make_wrapper"), tcl)
        assertTrue(tcl.contains("-import"), "wrapper must be imported into the fileset: $tcl")
    }

    @Test
    fun `numeric arguments arriving as strings still render correctly`() {
        // MCP parses every argument to a String; the generators must cope.
        val tcl = PredefinedCommandLibrary.findById("runImplementation")!!
            .tclGenerator(mapOf("jobs" to "16"))
        assertTrue(tcl.contains("-jobs 16"), tcl)
    }
}
