package com.hdl.vivado

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests for the one-shot launcher's working-directory layout.
 *
 * These exist because of a data-loss bug: the exec directory used to be
 * "<parent>/viva_prj_exec" with no source-folder level, so every folder under
 * one parent shared it — and Build Project deletes that directory before
 * launching. Building project B destroyed project A's entire Vivado project.
 */
class VivadoUtilsTest {

    private val PROJECT = VivadoUtils.ExecDirKind.PROJECT
    private val IP      = VivadoUtils.ExecDirKind.IP

    // -------------------------------------------------------------------------
    // The regression
    // -------------------------------------------------------------------------

    @Test
    fun `sibling source folders never share a working directory`() {
        val blink = VivadoUtils.execWorkingDir("/designs", "blink", PROJECT)
        val uart  = VivadoUtils.execWorkingDir("/designs", "uart",  PROJECT)
        assertNotEquals(blink, uart, "sibling folders must not share an exec dir — building one would wipe the other")
    }

    @Test
    fun `the source folder name is the last path segment`() {
        val dir = VivadoUtils.execWorkingDir("/designs", "blink", PROJECT)
        assertEquals("blink", File(dir).name)
        assertEquals("viva_prj_exec", File(dir).parentFile.name)
    }

    @Test
    fun `project and IP launches use separate trees`() {
        val prj = VivadoUtils.execWorkingDir("/designs", "blink", PROJECT)
        val ip  = VivadoUtils.execWorkingDir("/designs", "blink", IP)
        assertNotEquals(prj, ip)
        assertEquals("viva_prj_exec", File(prj).parentFile.name)
        assertEquals("viva_ip_exec",  File(ip).parentFile.name)
    }

    // -------------------------------------------------------------------------
    // Path construction
    // -------------------------------------------------------------------------

    @Test
    fun `the exec dir is nested under the parent path`() {
        val dir = VivadoUtils.execWorkingDir("/designs", "blink", PROJECT)
        assertEquals(File(File("/designs", "viva_prj_exec"), "blink").path, dir)
    }

    @Test
    fun `a trailing separator on the parent path is normalised away`() {
        assertEquals(
            VivadoUtils.execWorkingDir("/designs", "blink", PROJECT),
            VivadoUtils.execWorkingDir("/designs/", "blink", PROJECT)
        )
    }

    @Test
    fun `the same inputs always give the same directory`() {
        assertEquals(
            VivadoUtils.execWorkingDir("/designs", "blink", PROJECT),
            VivadoUtils.execWorkingDir("/designs", "blink", PROJECT)
        )
    }

    @Test
    fun `folder names differing only in case or suffix stay distinct`() {
        val a = VivadoUtils.execWorkingDir("/designs", "top",     PROJECT)
        val b = VivadoUtils.execWorkingDir("/designs", "top_old", PROJECT)
        assertNotEquals(a, b)
    }

    @Test
    fun `the result stays inside the parent path`() {
        val dir = VivadoUtils.execWorkingDir("/designs", "blink", PROJECT)
        assertTrue(dir.startsWith(File("/designs").path), "exec dir escaped the parent: $dir")
    }

    // -------------------------------------------------------------------------
    // Kind metadata
    // -------------------------------------------------------------------------

    @Test
    fun `exec dir names match the gitignore patterns`() {
        // .gitignore carries *viva_prj_exec and *viva_ip_exec; renaming these
        // without updating it would start committing Vivado build output.
        assertEquals("viva_prj_exec", PROJECT.dirName)
        assertEquals("viva_ip_exec",  IP.dirName)
    }
}
