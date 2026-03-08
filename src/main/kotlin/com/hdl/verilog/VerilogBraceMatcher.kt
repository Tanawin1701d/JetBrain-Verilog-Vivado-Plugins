package com.hdl.verilog

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class VerilogBraceMatcher : PairedBraceMatcher {
    private val pairs = arrayOf(
        BracePair(VerilogTokenTypes.BRACE, VerilogTokenTypes.BRACE, false)
    )

    override fun getPairs(): Array<BracePair> = pairs
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset
}
