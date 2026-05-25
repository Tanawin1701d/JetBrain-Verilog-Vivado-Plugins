package com.hdl.verilog

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

object VerilogTokenTypes {
    val WHITE_SPACE = TokenType.WHITE_SPACE
    val BAD_CHARACTER = TokenType.BAD_CHARACTER
    
    val LINE_COMMENT = IElementType("LINE_COMMENT", VerilogLanguage)
    val BLOCK_COMMENT = IElementType("BLOCK_COMMENT", VerilogLanguage)
    val IDENTIFIER = IElementType("IDENTIFIER", VerilogLanguage)
    val NUMBER = IElementType("NUMBER", VerilogLanguage)
    val STRING = IElementType("STRING", VerilogLanguage)
    val OPERATOR = IElementType("OPERATOR", VerilogLanguage)
    val PUNCTUATION = IElementType("PUNCTUATION", VerilogLanguage)

    // Distinct token types for each bracket pair so BraceMatcher can match them correctly
    val LPAREN   = IElementType("LPAREN",   VerilogLanguage)  // (
    val RPAREN   = IElementType("RPAREN",   VerilogLanguage)  // )
    val LBRACE   = IElementType("LBRACE",   VerilogLanguage)  // {
    val RBRACE   = IElementType("RBRACE",   VerilogLanguage)  // }
    val LBRACKET = IElementType("LBRACKET", VerilogLanguage)  // [
    val RBRACKET = IElementType("RBRACKET", VerilogLanguage)  // ]

    // Structural keyword pairs — begin/end, case/endcase, etc.
    val BEGIN_KW      = IElementType("BEGIN_KW",      VerilogLanguage)
    val END_KW        = IElementType("END_KW",        VerilogLanguage)
    val CASE_KW       = IElementType("CASE_KW",       VerilogLanguage)
    val ENDCASE_KW    = IElementType("ENDCASE_KW",    VerilogLanguage)
    val MODULE_KW     = IElementType("MODULE_KW",     VerilogLanguage)
    val ENDMODULE_KW  = IElementType("ENDMODULE_KW",  VerilogLanguage)
    val FUNCTION_KW   = IElementType("FUNCTION_KW",   VerilogLanguage)
    val ENDFUNCTION_KW= IElementType("ENDFUNCTION_KW",VerilogLanguage)
    val TASK_KW       = IElementType("TASK_KW",       VerilogLanguage)
    val ENDTASK_KW    = IElementType("ENDTASK_KW",    VerilogLanguage)
    val GENERATE_KW   = IElementType("GENERATE_KW",   VerilogLanguage)
    val ENDGENERATE_KW= IElementType("ENDGENERATE_KW",VerilogLanguage)

    // Kept for syntax highlighter compatibility (non-structural keywords)
    val KEYWORD = IElementType("KEYWORD", VerilogLanguage)
}
