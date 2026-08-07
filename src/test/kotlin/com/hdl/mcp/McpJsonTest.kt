package com.hdl.mcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for the hand-rolled JSON layer that every MCP request passes through.
 *
 * The cases that matter most involve backslashes: Vivado paths, Tcl regexes and
 * property strings all carry them, and they are exactly what a naive unescaper
 * corrupts.
 */
class McpJsonTest {

    // -------------------------------------------------------------------------
    // quote
    // -------------------------------------------------------------------------

    @Test
    fun `quote wraps and escapes the JSON specials`() {
        assertEquals("\"plain\"", McpJson.quote("plain"))
        assertEquals("\"say \\\"hi\\\"\"", McpJson.quote("say \"hi\""))
        assertEquals("\"a\\\\b\"", McpJson.quote("a\\b"))
        assertEquals("\"l1\\nl2\"", McpJson.quote("l1\nl2"))
        assertEquals("\"a\\tb\"", McpJson.quote("a\tb"))
    }

    @Test
    fun `quote escapes control characters that would otherwise break the JSON`() {
        // A bare 0x01 inside a JSON string is invalid; it must become \u0001.
        assertEquals("\"x\\u0001y\"", McpJson.quote("x\u0001y"))
    }

    // -------------------------------------------------------------------------
    // unescape — the regression that motivated this file
    // -------------------------------------------------------------------------

    @Test
    fun `unescape keeps a backslash followed by n as two characters`() {
        // On the wire a Windows path C:\proj\new is escaped as C:\\proj\\new.
        // Chained replace() calls used to turn the second pair into a newline.
        assertEquals("C:\\proj\\new", McpJson.unescape("C:\\\\proj\\\\new"))
    }

    @Test
    fun `unescape handles every standard JSON escape`() {
        assertEquals("\"",     McpJson.unescape("\\\""))
        assertEquals("\\",     McpJson.unescape("\\\\"))
        assertEquals("/",      McpJson.unescape("\\/"))
        assertEquals("\n",     McpJson.unescape("\\n"))
        assertEquals("\r",     McpJson.unescape("\\r"))
        assertEquals("\t",     McpJson.unescape("\\t"))
        assertEquals("\u000C", McpJson.unescape("\\f"))
        assertEquals("\b",     McpJson.unescape("\\b"))
        assertEquals("A",      McpJson.unescape("\\u0041"))
    }

    @Test
    fun `unescape leaves a non-JSON escape alone`() {
        // Tcl brace escaping is common in Vivado property strings and is not
        // a JSON escape — the backslash must survive.
        assertEquals("\\{1\\}", McpJson.unescape("\\{1\\}"))
    }

    @Test
    fun `unescape tolerates a trailing lone backslash`() {
        assertEquals("path\\", McpJson.unescape("path\\"))
    }

    @Test
    fun `unescape leaves a malformed unicode escape intact`() {
        assertEquals("\\u12", McpJson.unescape("\\u12"))
    }

    @Test
    fun `quote then unescape round-trips`() {
        val nasty = listOf(
            "C:\\Xilinx\\Vivado\\2023.2\\bin\\vivado",
            "set_property CONFIG.C_A {1} [get_bd_cells foo]",
            "line1\nline2\ttabbed",
            "quote \" inside",
            "backslash-n literal: \\n",
            "trailing\\",
            "",
            "unicode ✓ ok"
        )
        for (s in nasty) {
            val quoted = McpJson.quote(s)
            val body = quoted.substring(1, quoted.length - 1)   // strip the wrapping quotes
            assertEquals(s, McpJson.unescape(body), "round trip failed for: $s")
        }
    }

    // -------------------------------------------------------------------------
    // field extraction
    // -------------------------------------------------------------------------

    @Test
    fun `stringField reads a plain field`() {
        val json = """{"jsonrpc":"2.0","method":"tools/call","id":3}"""
        assertEquals("tools/call", McpJson.stringField(json, "method"))
        assertNull(McpJson.stringField(json, "absent"))
    }

    @Test
    fun `stringField reads past an escaped quote in the value`() {
        val json = """{"text":"he said \"go\" loudly","next":1}"""
        assertEquals("he said \"go\" loudly", McpJson.stringField(json, "text"))
    }

    @Test
    fun `stringField unescapes a path value`() {
        val json = """{"xprPath":"C:\\work\\new_proj.xpr"}"""
        assertEquals("C:\\work\\new_proj.xpr", McpJson.stringField(json, "xprPath"))
    }

    @Test
    fun `scalarField reads numbers and literals`() {
        val json = """{"id":42,"ok":true,"nothing":null}"""
        assertEquals("42",   McpJson.scalarField(json, "id"))
        assertEquals("true", McpJson.scalarField(json, "ok"))
        assertEquals("null", McpJson.scalarField(json, "nothing"))
    }

    // -------------------------------------------------------------------------
    // nested object extraction
    // -------------------------------------------------------------------------

    @Test
    fun `objectField extracts a nested object`() {
        val json = """{"method":"tools/call","params":{"name":"runSynthesis","arguments":{"jobs":8}}}"""
        val params = McpJson.objectField(json, "params")
        assertEquals("""{"name":"runSynthesis","arguments":{"jobs":8}}""", params)
        assertEquals("""{"jobs":8}""", McpJson.objectField(params!!, "arguments"))
    }

    @Test
    fun `objectField is not confused by braces inside a string value`() {
        // Vivado property strings routinely contain Tcl braces. A naive brace
        // counter stops early here and returns a truncated object.
        val json = """{"params":{"arguments":{"properties":"CONFIG.C_A {1} CONFIG.C_B {0}"}},"id":1}"""
        val params = McpJson.objectField(json, "params")
        assertEquals("""{"arguments":{"properties":"CONFIG.C_A {1} CONFIG.C_B {0}"}}""", params)
    }

    @Test
    fun `objectField returns null when the field is absent or unbalanced`() {
        assertNull(McpJson.objectField("""{"a":1}""", "params"))
        assertNull(McpJson.objectField("""{"params":{"a":1""", "params"))
    }

    // -------------------------------------------------------------------------
    // flat object parsing — what tool arguments actually go through
    // -------------------------------------------------------------------------

    @Test
    fun `flatObject parses strings numbers and literals`() {
        val map = McpJson.flatObject("""{"name":"synth_1","jobs":8,"force":true,"note":null}""")
        assertEquals("synth_1", map["name"])
        assertEquals("8",       map["jobs"])      // numbers arrive as strings, by design
        assertEquals("true",    map["force"])
        assertEquals("null",    map["note"])
    }

    @Test
    fun `flatObject keeps an empty string as an empty string`() {
        val map = McpJson.flatObject("""{"board":"","part":"xck26"}""")
        assertTrue(map.containsKey("board"))
        assertEquals("", map["board"])
        assertEquals("xck26", map["part"])
    }

    @Test
    fun `flatObject unescapes backslash-bearing arguments`() {
        val map = McpJson.flatObject("""{"scriptPath":"C:\\scripts\\new_build.tcl"}""")
        assertEquals("C:\\scripts\\new_build.tcl", map["scriptPath"])
    }

    @Test
    fun `flatObject on an empty object yields an empty map`() {
        assertTrue(McpJson.flatObject("{}").isEmpty())
    }
}
