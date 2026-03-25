package com.hdl.tcl

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class TclFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()
        
        // Traverse the tree to find all BLOCK elements
        collectFoldRegions(root.node, descriptors, document)
        
        return descriptors.toTypedArray()
    }

    private fun collectFoldRegions(node: ASTNode, descriptors: MutableList<FoldingDescriptor>, document: Document) {
        if (node.elementType == TclTokenTypes.BLOCK) {
            val range = node.textRange
            if (range.length > 2) { // More than just {}
                val startLine = document.getLineNumber(range.startOffset)
                val endLine = document.getLineNumber(range.endOffset)
                if (startLine < endLine) {
                    descriptors.add(FoldingDescriptor(node, range))
                }
            }
        }
        
        var child = node.firstChildNode
        while (child != null) {
            collectFoldRegions(child, descriptors, document)
            child = child.treeNext
        }
    }

    override fun getPlaceholderText(node: ASTNode): String? = "{...}"

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false
}
