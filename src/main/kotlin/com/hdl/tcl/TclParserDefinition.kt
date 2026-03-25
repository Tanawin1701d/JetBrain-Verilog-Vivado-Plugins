package com.hdl.tcl

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class TclParserDefinition : ParserDefinition {
    companion object {
        val FILE = IFileElementType(TclLanguage)
        val COMMENTS = TokenSet.create(TclTokenTypes.LINE_COMMENT)
        val WHITESPACES = TokenSet.create(TclTokenTypes.WHITE_SPACE)
    }

    override fun createLexer(project: Project?): Lexer = TclLexer()
    override fun createParser(project: Project?): PsiParser = TclParser()
    override fun getFileNodeType(): IFileElementType = FILE
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getStringLiteralElements(): TokenSet = TokenSet.create(TclTokenTypes.STRING)
    override fun createElement(node: ASTNode?): PsiElement = TclPsiElement(node!!)
    override fun createFile(viewProvider: FileViewProvider): PsiFile = TclFile(viewProvider)
}
