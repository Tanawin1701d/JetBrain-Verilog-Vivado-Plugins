package com.hdl.verilog

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

object VerilogTokenTypes {
    val WHITE_SPACE = TokenType.WHITE_SPACE
    val BAD_CHARACTER = TokenType.BAD_CHARACTER
    
    val LINE_COMMENT = IElementType("LINE_COMMENT", VerilogLanguage)
    val BLOCK_COMMENT = IElementType("BLOCK_COMMENT", VerilogLanguage)
    val KEYWORD = IElementType("KEYWORD", VerilogLanguage)
    val IDENTIFIER = IElementType("IDENTIFIER", VerilogLanguage)
    val NUMBER = IElementType("NUMBER", VerilogLanguage)
    val STRING = IElementType("STRING", VerilogLanguage)
    val OPERATOR = IElementType("OPERATOR", VerilogLanguage)
    val BRACE = IElementType("BRACE", VerilogLanguage)
    val PUNCTUATION = IElementType("PUNCTUATION", VerilogLanguage)
}
