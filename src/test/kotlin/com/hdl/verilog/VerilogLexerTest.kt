package com.hdl.verilog

import com.intellij.psi.tree.IElementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tokenisation tests for the hand-written Verilog lexer.
 *
 * The paired-keyword token types exist purely so VerilogBraceMatcher can match
 * module/endmodule and friends — if the lexer stops emitting them, brace
 * matching silently degrades with no other visible symptom.
 */
class VerilogLexerTest {

    /** Run the lexer over [text] and collect (tokenType, text) for every token. */
    private fun lex(text: String): List<Pair<IElementType, String>> {
        val lexer = VerilogLexer()
        lexer.start(text, 0, text.length, 0)
        val out = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            out += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return out
    }

    /** Tokens with whitespace dropped — what actually carries meaning. */
    private fun significant(text: String) =
        lex(text).filter { it.first != VerilogTokenTypes.WHITE_SPACE }

    private fun typesOf(text: String) = significant(text).map { it.first }

    // -------------------------------------------------------------------------
    // Structural keyword pairs
    // -------------------------------------------------------------------------

    @Test
    fun `module and endmodule get their own token types`() {
        val types = typesOf("module m endmodule")
        assertEquals(VerilogTokenTypes.MODULE_KW, types.first())
        assertEquals(VerilogTokenTypes.ENDMODULE_KW, types.last())
    }

    @Test
    fun `every structural pair is tokenised distinctly`() {
        val pairs = listOf(
            "begin"       to VerilogTokenTypes.BEGIN_KW,
            "end"         to VerilogTokenTypes.END_KW,
            "case"        to VerilogTokenTypes.CASE_KW,
            "endcase"     to VerilogTokenTypes.ENDCASE_KW,
            "module"      to VerilogTokenTypes.MODULE_KW,
            "endmodule"   to VerilogTokenTypes.ENDMODULE_KW,
            "function"    to VerilogTokenTypes.FUNCTION_KW,
            "endfunction" to VerilogTokenTypes.ENDFUNCTION_KW,
            "task"        to VerilogTokenTypes.TASK_KW,
            "endtask"     to VerilogTokenTypes.ENDTASK_KW,
            "generate"    to VerilogTokenTypes.GENERATE_KW,
            "endgenerate" to VerilogTokenTypes.ENDGENERATE_KW
        )
        for ((word, expected) in pairs) {
            assertEquals(expected, typesOf(word).single(), "wrong token type for '$word'")
        }
    }

    @Test
    fun `a plain keyword is not a structural pair`() {
        assertEquals(VerilogTokenTypes.KEYWORD, typesOf("always").single())
        assertEquals(VerilogTokenTypes.KEYWORD, typesOf("assign").single())
    }

    @Test
    fun `an unknown word is an identifier`() {
        assertEquals(VerilogTokenTypes.IDENTIFIER, typesOf("my_signal").single())
    }

    @Test
    fun `keyword matching is exact not prefix based`() {
        // "endmodules" starts with a keyword but is an identifier.
        assertEquals(VerilogTokenTypes.IDENTIFIER, typesOf("endmodules").single())
        assertEquals(VerilogTokenTypes.IDENTIFIER, typesOf("module_name").single())
    }

    // -------------------------------------------------------------------------
    // Coverage and offsets
    // -------------------------------------------------------------------------

    @Test
    fun `tokens cover the input with no gaps or overlaps`() {
        val src = "module counter(input clk, output reg [7:0] q); endmodule"
        val tokens = lex(src)
        assertEquals(src, tokens.joinToString("") { it.second }, "token text must reassemble the source")
    }

    @Test
    fun `an empty buffer yields no tokens`() {
        assertTrue(lex("").isEmpty())
    }

    @Test
    fun `whitespace is collapsed into single tokens`() {
        val ws = lex("a   \n\t b").filter { it.first == VerilogTokenTypes.WHITE_SPACE }
        assertEquals(1, ws.size, "consecutive whitespace should be one token")
        assertEquals("   \n\t ", ws.single().second)
    }

    @Test
    fun `brackets are tokenised individually`() {
        val src = "[7:0]"
        val types = typesOf(src)
        assertTrue(types.isNotEmpty())
        // Each bracket must be its own token so the brace matcher can pair them.
        val texts = significant(src).map { it.second }
        assertTrue(texts.contains("["), texts.toString())
        assertTrue(texts.contains("]"), texts.toString())
    }

    // -------------------------------------------------------------------------
    // A realistic module
    // -------------------------------------------------------------------------

    @Test
    fun `a full module lexes into the expected structural skeleton`() {
        val src = """
            module counter (
                input  wire clk,
                output reg  [7:0] count
            );
                always @(posedge clk) begin
                    count <= count + 1;
                end
            endmodule
        """.trimIndent()

        val structural = typesOf(src).filter {
            it == VerilogTokenTypes.MODULE_KW || it == VerilogTokenTypes.ENDMODULE_KW ||
                it == VerilogTokenTypes.BEGIN_KW || it == VerilogTokenTypes.END_KW
        }
        assertEquals(
            listOf(
                VerilogTokenTypes.MODULE_KW,
                VerilogTokenTypes.BEGIN_KW,
                VerilogTokenTypes.END_KW,
                VerilogTokenTypes.ENDMODULE_KW
            ),
            structural
        )
    }
}
