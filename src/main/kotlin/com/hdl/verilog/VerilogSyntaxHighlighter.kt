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

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when (tokenType) {
            VerilogTokenTypes.KEYWORD -> arrayOf(KEYWORD)
            VerilogTokenTypes.LINE_COMMENT, VerilogTokenTypes.BLOCK_COMMENT -> arrayOf(COMMENT)
            VerilogTokenTypes.STRING -> arrayOf(STRING)
            VerilogTokenTypes.NUMBER -> arrayOf(NUMBER)
            VerilogTokenTypes.OPERATOR -> arrayOf(OPERATOR)
            VerilogTokenTypes.IDENTIFIER -> arrayOf(IDENTIFIER)
            VerilogTokenTypes.BRACE -> arrayOf(BRACE)
            VerilogTokenTypes.PUNCTUATION -> arrayOf(PUNCTUATION)
            else -> emptyArray()
        }
    }
}
