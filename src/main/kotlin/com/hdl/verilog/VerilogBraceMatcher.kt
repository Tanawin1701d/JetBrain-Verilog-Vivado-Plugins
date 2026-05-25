package com.hdl.verilog

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class VerilogBraceMatcher : PairedBraceMatcher {
    private val pairs = arrayOf(
        // Character bracket pairs (false = not structural)
        BracePair(VerilogTokenTypes.LPAREN,   VerilogTokenTypes.RPAREN,        false),
        BracePair(VerilogTokenTypes.LBRACE,   VerilogTokenTypes.RBRACE,        false),
        BracePair(VerilogTokenTypes.LBRACKET, VerilogTokenTypes.RBRACKET,      false),
        // Structural keyword pairs (true = structural — IDE uses these for indentation hints)
        BracePair(VerilogTokenTypes.BEGIN_KW,    VerilogTokenTypes.END_KW,         true),
        BracePair(VerilogTokenTypes.CASE_KW,     VerilogTokenTypes.ENDCASE_KW,     true),
        BracePair(VerilogTokenTypes.MODULE_KW,   VerilogTokenTypes.ENDMODULE_KW,   true),
        BracePair(VerilogTokenTypes.FUNCTION_KW, VerilogTokenTypes.ENDFUNCTION_KW, true),
        BracePair(VerilogTokenTypes.TASK_KW,     VerilogTokenTypes.ENDTASK_KW,     true),
        BracePair(VerilogTokenTypes.GENERATE_KW, VerilogTokenTypes.ENDGENERATE_KW, true),
    )

    override fun getPairs(): Array<BracePair> = pairs
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
