package com.hdl.verilog

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class VerilogSyntaxHighlighter : SyntaxHighlighterBase() {
    companion object {
        val KEYWORD = TextAttributesKey.createTextAttributesKey(
            "VERILOG_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
        )
        val COMMENT = TextAttributesKey.createTextAttributesKey(
            "VERILOG_COMMENT",
            DefaultLanguageHighlighterColors.LINE_COMMENT
        )
        val STRING = TextAttributesKey.createTextAttributesKey(
            "VERILOG_STRING",
            DefaultLanguageHighlighterColors.STRING
        )
        val NUMBER = TextAttributesKey.createTextAttributesKey(
            "VERILOG_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
        )
        val OPERATOR = TextAttributesKey.createTextAttributesKey(
            "VERILOG_OPERATOR",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
        )
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "VERILOG_IDENTIFIER",
            DefaultLanguageHighlighterColors.IDENTIFIER
        )
        val BRACE = TextAttributesKey.createTextAttributesKey(
            "VERILOG_BRACE",
            DefaultLanguageHighlighterColors.BRACES
        )
        val PUNCTUATION = TextAttributesKey.createTextAttributesKey(
            "VERILOG_PUNCTUATION",
            DefaultLanguageHighlighterColors.COMMA
        )
    }

    override fun getHighlightingLexer(): Lexer = VerilogLexer()

    // Called by IntelliJ for every token produced by the lexer.
    // Acts as a lookup table: token type IN → color key(s) OUT.
    // Returns an array because a token can carry multiple styles (e.g. bold + color),
    // though in practice each entry here returns exactly one key.
    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when (tokenType) {
            // Plain keywords
            VerilogTokenTypes.KEYWORD -> arrayOf(KEYWORD)
            // Paired structural keywords — highlighted same as keywords
            VerilogTokenTypes.BEGIN_KW, VerilogTokenTypes.END_KW,
            VerilogTokenTypes.CASE_KW, VerilogTokenTypes.ENDCASE_KW,
            VerilogTokenTypes.MODULE_KW, VerilogTokenTypes.ENDMODULE_KW,
            VerilogTokenTypes.FUNCTION_KW, VerilogTokenTypes.ENDFUNCTION_KW,
            VerilogTokenTypes.TASK_KW, VerilogTokenTypes.ENDTASK_KW,
            VerilogTokenTypes.GENERATE_KW, VerilogTokenTypes.ENDGENERATE_KW -> arrayOf(KEYWORD)
            // Both comment styles share one color
            VerilogTokenTypes.LINE_COMMENT, VerilogTokenTypes.BLOCK_COMMENT -> arrayOf(COMMENT)
            VerilogTokenTypes.STRING      -> arrayOf(STRING)
            VerilogTokenTypes.NUMBER      -> arrayOf(NUMBER)
            VerilogTokenTypes.OPERATOR    -> arrayOf(OPERATOR)
            VerilogTokenTypes.IDENTIFIER  -> arrayOf(IDENTIFIER)
            // All bracket types share the same brace color
            VerilogTokenTypes.LPAREN, VerilogTokenTypes.RPAREN,
            VerilogTokenTypes.LBRACE, VerilogTokenTypes.RBRACE,
            VerilogTokenTypes.LBRACKET, VerilogTokenTypes.RBRACKET -> arrayOf(BRACE)
            VerilogTokenTypes.PUNCTUATION -> arrayOf(PUNCTUATION)
            else -> emptyArray() // WHITE_SPACE and BAD_CHARACTER get no color here
        }
    }
}
