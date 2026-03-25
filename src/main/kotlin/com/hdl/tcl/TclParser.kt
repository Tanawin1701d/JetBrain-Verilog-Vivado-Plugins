package com.hdl.tcl

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

class TclParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        
        while (!builder.eof()) {
            parseToken(builder)
        }
        
        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseToken(builder: PsiBuilder) {
        if (builder.tokenType == TclTokenTypes.BRACE && builder.tokenText == "{") {
            val blockMarker = builder.mark()
            builder.advanceLexer()
            while (!builder.eof()) {
                if (builder.tokenType == TclTokenTypes.BRACE && builder.tokenText == "}") {
                    builder.advanceLexer()
                    break
                }
                parseToken(builder)
            }
            blockMarker.done(TclTokenTypes.BLOCK)
        } else {
            builder.advanceLexer()
        }
    }
}
