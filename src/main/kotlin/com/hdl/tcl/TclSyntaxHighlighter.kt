package com.hdl.tcl

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class TclSyntaxHighlighter : SyntaxHighlighterBase() {
    companion object {
        val KEYWORD = TextAttributesKey.createTextAttributesKey(
            "TCL_KEYWORD",
            DefaultLanguageHighlighterColors.KEYWORD
        )
        val COMMAND = TextAttributesKey.createTextAttributesKey(
            "TCL_COMMAND",
            DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
        )
        val COMMENT = TextAttributesKey.createTextAttributesKey(
            "TCL_COMMENT",
            DefaultLanguageHighlighterColors.LINE_COMMENT
        )
        val STRING = TextAttributesKey.createTextAttributesKey(
            "TCL_STRING",
            DefaultLanguageHighlighterColors.STRING
        )
        val NUMBER = TextAttributesKey.createTextAttributesKey(
            "TCL_NUMBER",
            DefaultLanguageHighlighterColors.NUMBER
        )
        val VARIABLE = TextAttributesKey.createTextAttributesKey(
            "TCL_VARIABLE",
            DefaultLanguageHighlighterColors.LOCAL_VARIABLE
        )
        val OPERATOR = TextAttributesKey.createTextAttributesKey(
            "TCL_OPERATOR",
            DefaultLanguageHighlighterColors.OPERATION_SIGN
        )
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey(
            "TCL_IDENTIFIER",
            DefaultLanguageHighlighterColors.IDENTIFIER
        )
        val BRACE = TextAttributesKey.createTextAttributesKey(
            "TCL_BRACE",
            DefaultLanguageHighlighterColors.BRACES
        )
        val BRACKET = TextAttributesKey.createTextAttributesKey(
            "TCL_BRACKET",
            DefaultLanguageHighlighterColors.BRACKETS
        )
        val PUNCTUATION = TextAttributesKey.createTextAttributesKey(
            "TCL_PUNCTUATION",
            DefaultLanguageHighlighterColors.COMMA
        )
    }

    override fun getHighlightingLexer(): Lexer = TclLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> {
        return when (tokenType) {
            TclTokenTypes.KEYWORD -> arrayOf(KEYWORD)
            TclTokenTypes.COMMAND -> arrayOf(COMMAND)
            TclTokenTypes.LINE_COMMENT -> arrayOf(COMMENT)
            TclTokenTypes.STRING -> arrayOf(STRING)
            TclTokenTypes.NUMBER -> arrayOf(NUMBER)
            TclTokenTypes.VARIABLE -> arrayOf(VARIABLE)
            TclTokenTypes.OPERATOR -> arrayOf(OPERATOR)
            TclTokenTypes.IDENTIFIER -> arrayOf(IDENTIFIER)
            TclTokenTypes.BRACE -> arrayOf(BRACE)
            TclTokenTypes.BRACKET -> arrayOf(BRACKET)
            TclTokenTypes.PUNCTUATION -> arrayOf(PUNCTUATION)
            else -> emptyArray()
        }
    }
}
