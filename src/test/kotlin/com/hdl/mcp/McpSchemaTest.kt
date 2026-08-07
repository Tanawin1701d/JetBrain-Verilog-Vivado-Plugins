package com.hdl.mcp

import com.hdl.vivado.CommandParameter
import com.hdl.vivado.ParameterType
import com.hdl.vivado.PredefinedCommand
import com.hdl.vivado.PredefinedCommandLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the tools/list projection — the only description of your Vivado
 * surface the model ever sees. If a schema is wrong the model calls the tool
 * wrong, and the failure surfaces as confusing Tcl errors much later.
 */
class McpSchemaTest {

    private fun cmd(vararg params: CommandParameter) = PredefinedCommand(
        id = "demo",
        name = "Demo",
        description = "demo command",
        parameters = params.toList(),
        tclGenerator = { "puts demo" }
    )

    // -------------------------------------------------------------------------
    // inputSchema
    // -------------------------------------------------------------------------

    @Test
    fun `a command with no parameters gets an empty object schema`() {
        assertEquals(
            """{"type":"object","properties":{},"required":[]}""",
            McpSchema.inputSchema(cmd())
        )
    }

    @Test
    fun `parameter types map to JSON Schema keywords`() {
        val schema = McpSchema.inputSchema(cmd(
            CommandParameter("path",  ParameterType.STRING,  true,  "a path"),
            CommandParameter("jobs",  ParameterType.INT,     false, "job count"),
            CommandParameter("force", ParameterType.BOOLEAN, false, "force it")
        ))
        assertTrue(schema.contains(""""path":{"type":"string""""), schema)
        assertTrue(schema.contains(""""jobs":{"type":"integer""""), schema)
        assertTrue(schema.contains(""""force":{"type":"boolean""""), schema)
    }

    @Test
    fun `only required parameters appear in the required list`() {
        val schema = McpSchema.inputSchema(cmd(
            CommandParameter("must",  ParameterType.STRING, true,  "required one"),
            CommandParameter("maybe", ParameterType.STRING, false, "optional one")
        ))
        assertTrue(schema.contains(""""required":["must"]"""), schema)
        assertTrue(!schema.contains("\"maybe\"]"), "optional parameter leaked into required: $schema")
    }

    @Test
    fun `descriptions are escaped so a quote cannot break the schema`() {
        val schema = McpSchema.inputSchema(cmd(
            CommandParameter("p", ParameterType.STRING, false, """say "hi" \ now""")
        ))
        assertTrue(schema.contains("""\"hi\""""), schema)
        assertTrue(schema.contains("""\\"""), schema)
    }

    // -------------------------------------------------------------------------
    // toolsList
    // -------------------------------------------------------------------------

    @Test
    fun `toolsList renders a JSON array`() {
        val json = McpSchema.toolsList(listOf(cmd()))
        assertTrue(json.startsWith("["), json)
        assertTrue(json.endsWith("]"), json)
        assertTrue(json.contains(""""name":"demo""""), json)
        assertTrue(json.contains(""""inputSchema":{"""), json)
    }

    @Test
    fun `toolsList on an empty catalogue is an empty array`() {
        assertEquals("[]", McpSchema.toolsList(emptyList()))
    }

    @Test
    fun `the real library renders with balanced braces and brackets`() {
        val json = McpSchema.toolsList(PredefinedCommandLibrary.commands)

        // Walk the payload ignoring anything inside a string literal.
        var braces = 0
        var brackets = 0
        var inString = false
        var i = 0
        while (i < json.length) {
            val c = json[i]
            if (inString) {
                when (c) {
                    '\\' -> i++
                    '"'  -> inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> braces++
                    '}' -> braces--
                    '[' -> brackets++
                    ']' -> brackets--
                }
            }
            assertTrue(braces >= 0 && brackets >= 0, "unbalanced close at index $i")
            i++
        }
        assertEquals(0, braces, "unbalanced braces in tools/list")
        assertEquals(0, brackets, "unbalanced brackets in tools/list")
        assertTrue(!inString, "unterminated string in tools/list")
    }

    @Test
    fun `every library command appears in the rendered catalogue`() {
        val json = McpSchema.toolsList(PredefinedCommandLibrary.commands)
        for (c in PredefinedCommandLibrary.commands) {
            assertTrue(json.contains(""""name":"${c.id}""""), "missing tool ${c.id}")
        }
    }

    @Test
    fun `filtering out the raw-Tcl commands removes them from the catalogue`() {
        val safe = PredefinedCommandLibrary.commands
            .filter { it.id !in PredefinedCommandLibrary.rawTclToolIds }
        val json = McpSchema.toolsList(safe)
        for (id in PredefinedCommandLibrary.rawTclToolIds) {
            assertTrue(!json.contains(""""name":"$id""""), "gated tool $id leaked into tools/list")
        }
    }
}
