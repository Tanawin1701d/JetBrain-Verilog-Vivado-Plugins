package com.hdl.vivado.catalog

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for the runVivadoCommand argument gate.
 *
 * This is the reason runVivadoCommand can stay available in Safe mode while runTclRaw is
 * hidden, so both halves matter: real Vivado arguments must keep working, and every route
 * from an argument string to a second Tcl command must stay shut.
 */
class TclArgSanitizerTest {

    private fun accepted(args: String) =
        assertNull(TclArgSanitizer.reject(args), "should have been accepted: $args")

    private fun refused(args: String) =
        assertNotNull(TclArgSanitizer.reject(args), "should have been refused: $args")

    // -------------------------------------------------------------------------
    // Ordinary Vivado arguments keep working
    // -------------------------------------------------------------------------

    @Test
    fun `plain flags and values are accepted`() {
        accepted("")
        accepted("-period 10")
        accepted("-jobs 8 -to_step write_bitstream")
        accepted("synth_1")
    }

    @Test
    fun `read-only substitutions are accepted`() {
        accepted("-period 10 [get_ports clk]")
        accepted("[get_bd_cells axi_dma_0]")
        accepted("-of_objects [get_bd_pins {axi_dma_0/s_axi_lite_aclk}]")
        accepted("-dict [list CONFIG.c_include_sg 0] [get_bd_cells axi_dma_0]")
        accepted("[lindex [get_hw_devices] 0]")
        accepted("[current_design]")
        accepted("[all_ffs]")
    }

    @Test
    fun `braces and quotes used for paths are accepted`() {
        accepted("-norecurse {C:/Data/my project/top.v}")
        accepted("\"a quoted value\"")
        accepted("{CONFIG.PSU__USE__M_AXI_GP0 1}")
    }

    // -------------------------------------------------------------------------
    // Routes to a second command are refused
    // -------------------------------------------------------------------------

    @Test
    fun `statement separators are refused`() {
        refused("-period 10; close_project")
        refused("-period 10\nclose_project")
        refused("-period 10\rclose_project")
    }

    @Test
    fun `escape and substitution characters are refused`() {
        refused("C:\\Data\\top.v")          // backslash re-opens \n and line continuations
        refused("-period 10 \\")
        refused("`whoami`")
        refused("\$env(HOME)")
        refused("{*}\$argv")
    }

    @Test
    fun `substitutions that are not read-only queries are refused`() {
        refused("[exec ls]")
        refused("[file delete top.v]")
        refused("[open /etc/passwd]")
        refused("[source evil.tcl]")
        refused("[eval {close_project}]")
        refused("-period [expr 10] [close_project]")
    }

    @Test
    fun `unbalanced delimiters are refused`() {
        refused("[get_ports clk")
        refused("get_ports clk]")
        refused("{C:/Data/top.v")
        refused("C:/Data/top.v}")
        refused("\"unterminated")
    }

    @Test
    fun `a nested substitution is checked at every level`() {
        accepted("[get_bd_nets -of_objects [get_bd_pins axi_dma_0/s_axi_lite_aclk]]")
        refused("[get_bd_nets -of_objects [exec ls]]")
    }
}
