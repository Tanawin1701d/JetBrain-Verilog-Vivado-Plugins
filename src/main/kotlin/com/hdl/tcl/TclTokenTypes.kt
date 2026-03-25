package com.hdl.tcl

import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.TokenType

interface TclTokenTypes {
    companion object {
        val FILE = IFileElementType(TclLanguage)

        val KEYWORD = IElementType("TCL_KEYWORD", TclLanguage)
        val COMMAND = IElementType("TCL_COMMAND", TclLanguage)
        val IDENTIFIER = IElementType("TCL_IDENTIFIER", TclLanguage)
        val NUMBER = IElementType("TCL_NUMBER", TclLanguage)
        val STRING = IElementType("TCL_STRING", TclLanguage)
        val VARIABLE = IElementType("TCL_VARIABLE", TclLanguage)
        val LINE_COMMENT = IElementType("TCL_LINE_COMMENT", TclLanguage)
        val BRACE = IElementType("TCL_BRACE", TclLanguage)
        val BRACKET = IElementType("TCL_BRACKET", TclLanguage)
        val OPERATOR = IElementType("TCL_OPERATOR", TclLanguage)
        val PUNCTUATION = IElementType("TCL_PUNCTUATION", TclLanguage)
        val BLOCK = IElementType("TCL_BLOCK", TclLanguage)
        val BAD_CHARACTER = TokenType.BAD_CHARACTER
        val WHITE_SPACE = TokenType.WHITE_SPACE
    }
}
