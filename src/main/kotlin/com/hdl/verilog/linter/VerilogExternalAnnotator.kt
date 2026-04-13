package com.hdl.verilog.linter

import com.hdl.verilog.VerilogFile
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class VerilogExternalAnnotator : ExternalAnnotator<PsiFile, List<LintResult>>() {
    private val LOG = Logger.getInstance(VerilogExternalAnnotator::class.java)

    override fun collectInformation(file: PsiFile): PsiFile? =
        if (file is VerilogFile) file else null

    override fun doAnnotate(collectedInfo: PsiFile?): List<LintResult>? {
        if (collectedInfo == null || collectedInfo !is VerilogFile) return null

        val virtualFile = collectedInfo.virtualFile ?: return null
        val project     = collectedInfo.project
        val settings    = LinterSettingsState.getInstance(project)

        val fs = virtualFile.fileSystem
        val topFolder = settings.topFolder?.let { fs.findFileByPath(it) }
        val topFile   = settings.topFile?.let   { fs.findFileByPath(it) }

        val linter = when (settings.linterType) {
            LinterSettingsState.LinterType.IVERILOG  -> IcarusVerilogLinter()
            LinterSettingsState.LinterType.VERILATOR -> VerilatorLinter()
        }
        val toolPath = when (settings.linterType) {
            LinterSettingsState.LinterType.IVERILOG  -> settings.iverilogPath
            LinterSettingsState.LinterType.VERILATOR -> settings.verilatorPath
        }

        val results   = mutableListOf<LintResult>()
        val rawOutput = StringBuilder()

        LOG.info("Starting linting for: ${virtualFile.path}")

        if (linter.isAvailable(toolPath)) {
            val output = linter.lint(toolPath, virtualFile, collectedInfo.text, topFolder, topFile)
            LOG.info("Linter ${linter.name} returned ${output.results.size} results")
            results.addAll(output.results)
            rawOutput.append("--- Linter: ${linter.name} ---\n").append(output.rawOutput).append("\n")
        } else {
            LOG.warn("Linter ${linter.name} is not available at $toolPath")
            rawOutput.append("--- Linter: ${linter.name} (not available at $toolPath) ---\n")
        }

        LinterDebuggerService.getInstance(project).update(rawOutput.toString(), results)
        
        // Refresh other open files to show potential errors found in them
        DaemonCodeAnalyzer.getInstance(project).restart()
        
        return results
    }

    override fun apply(file: PsiFile, annotationResult: List<LintResult>?, holder: AnnotationHolder) {
        val project = file.project
        val virtualFile = file.virtualFile ?: return
        val results = LinterDebuggerService.getInstance(project).getResultsForFile(virtualFile.path)

        for (result in results) {
            val line = maxOf(0, result.line - 1)
            val document = file.viewProvider.document ?: continue
            if (line >= document.lineCount) continue

            val range = TextRange(document.getLineStartOffset(line), document.getLineEndOffset(line))
            val severity = when (result.severity) {
                LintResult.Severity.ERROR   -> HighlightSeverity.ERROR
                LintResult.Severity.WARNING -> HighlightSeverity.WARNING
                LintResult.Severity.INFO    -> HighlightSeverity.INFORMATION
            }
            holder.newAnnotation(severity, result.message).range(range).create()
        }
    }
}
