package com.hdl.verilog

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

class VerilogParserDefinition : ParserDefinition {
    companion object {
        val FILE = IFileElementType(VerilogLanguage)
        val COMMENTS = TokenSet.create(VerilogTokenTypes.LINE_COMMENT, VerilogTokenTypes.BLOCK_COMMENT)
        val WHITESPACES = TokenSet.create(VerilogTokenTypes.WHITE_SPACE)
    }

    override fun createLexer(project: Project?): Lexer = VerilogLexer()

    override fun createParser(project: Project?): PsiParser = VerilogParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getCommentTokens(): TokenSet = COMMENTS

    override fun getStringLiteralElements(): TokenSet = TokenSet.create(VerilogTokenTypes.STRING)

    override fun createElement(node: ASTNode?): PsiElement = VerilogPsiElement(node!!)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = VerilogFile(viewProvider)
}
