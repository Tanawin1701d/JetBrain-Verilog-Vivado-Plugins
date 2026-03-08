package com.hdl.verilog.linter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.hdl.verilog.VerilogFile

class VerilogExternalAnnotator : ExternalAnnotator<PsiFile, List<LintResult>>() {
    private val LOG = Logger.getInstance(VerilogExternalAnnotator::class.java)
    
    private val linters = listOf<VerilogLinter>(
        IcarusVerilogLinter()
    )

    override fun collectInformation(file: PsiFile): PsiFile? {
        return if (file is VerilogFile) file else null
    }

    override fun doAnnotate(collectedInfo: PsiFile?): List<LintResult>? {
        if (collectedInfo == null || collectedInfo !is VerilogFile) return null

        val virtualFile = collectedInfo.virtualFile ?: return null
        val project = collectedInfo.project
        val settingsState = LinterSettingsState.getInstance(project)
        val topFolder = settingsState.topFolder?.let { 
            virtualFile.fileSystem.findFileByPath(it) 
        }

        val results = mutableListOf<LintResult>()
        
        LOG.info("Starting linting for: ${virtualFile.path}")
        val fileContent = collectedInfo.text
        for (linter in linters) {
            if (linter.isAvailable()) {
                val linterResults = linter.lint(virtualFile, fileContent, topFolder)
                LOG.info("Linter ${linter.name} returned ${linterResults.size} results")
                results.addAll(linterResults)
            } else {
                LOG.warn("Linter ${linter.name} is not available")
            }
        }

        return results
    }

    override fun apply(file: PsiFile, annotationResult: List<LintResult>?, holder: AnnotationHolder) {
        if (annotationResult == null) return

        val virtualFilePath = file.virtualFile?.path ?: return
        val absoluteVirtualFilePath = java.io.File(virtualFilePath).absolutePath

        for (result in annotationResult) {
            val resultFilePath = java.io.File(result.file).absolutePath
            if (resultFilePath != absoluteVirtualFilePath) continue
            
            val line = maxOf(0, result.line - 1)
            if (line >= file.textLength) continue

            val document = file.viewProvider.document ?: continue
            if (line >= document.lineCount) continue

            val lineStartOffset = document.getLineStartOffset(line)
            val lineEndOffset = document.getLineEndOffset(line)
            val range = TextRange(lineStartOffset, lineEndOffset)

            val severity = when (result.severity) {
                LintResult.Severity.ERROR -> HighlightSeverity.ERROR
                LintResult.Severity.WARNING -> HighlightSeverity.WARNING
                LintResult.Severity.INFO -> HighlightSeverity.INFORMATION
            }

            holder.newAnnotation(severity, result.message)
                .range(range)
                .create()
        }
    }
}
