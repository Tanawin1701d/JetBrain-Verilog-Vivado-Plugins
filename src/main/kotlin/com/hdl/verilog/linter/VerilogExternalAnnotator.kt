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

        val linter = when (settings.linterType) {
            LinterSettingsState.LinterType.IVERILOG  -> IcarusVerilogLinter()
            LinterSettingsState.LinterType.VERILATOR -> VerilatorLinter()
        }
        val toolPath = when (settings.linterType) {
            LinterSettingsState.LinterType.IVERILOG  -> settings.iverilogPath
            LinterSettingsState.LinterType.VERILATOR -> settings.verilatorPath
        }

        if (!linter.isAvailable(toolPath)) {
            LOG.warn("Linter ${linter.name} is not available at $toolPath")
            val rawOutput = "--- Linter: ${linter.name} (not available at $toolPath) ---\n"
            LinterDebuggerService.getInstance(project).update(rawOutput, emptyList())
            return emptyList()
        }

        val allResults = mutableListOf<LintResult>()
        val combinedRawOutput = StringBuilder()

        LOG.info("Starting multi-pass linting for: ${virtualFile.path}")

        // Pass 1: Lint each file individually to find syntax errors
        val filesToLint = if (topFolder != null) {
            val verilogExts = setOf("v", "vh", "sv", "svh")
            java.io.File(topFolder.path).walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in verilogExts }
                .toList()
        } else {
            listOf(java.io.File(virtualFile.path))
        }

        val excludedPaths = mutableSetOf<String>()

        for (f in filesToLint) {
            val vFile = fs.findFileByPath(f.absolutePath) ?: continue

            // Skip linting if the file is the current file itself
            if (vFile.path == virtualFile.path) continue

            val content = try {
                vFile.contentsToByteArray().toString(Charsets.UTF_8)
            } catch (e: Exception) {
                ""
            }

            val output = linter.lint(project, toolPath, vFile, content, null)

            // If there are errors, we consider it a candidate for exclusion from multi-file lint
            val hasSyntaxErrors = output.results.any {
                it.severity == LintResult.Severity.ERROR &&
                        it.message.contains("syntax", ignoreCase = true)
            }
            if (hasSyntaxErrors) {
                excludedPaths.add(vFile.path)
            }

            //allResults.addAll(output.results)
            combinedRawOutput.append("--- Pass 1: Single-file lint for ${vFile.name} ---\n")
            combinedRawOutput.append(output.rawOutput).append("\n")
        }

        // Pass 2: Global lint excluding "broken" files
        if (topFolder != null) {
            val globalOutput = linter.lint(project, toolPath, virtualFile, collectedInfo.text, topFolder, excludedPaths)
            
            // Filter results from global output: only add those NOT already found in Pass 1 for the same file/line
            // or just add all and let LinterDebuggerService handle it? 
            // Better to add all and let the Service store them.
            // Actually, Pass 1 already found errors in excluded files. 
            // Global lint will find errors in non-excluded files.
            
            allResults.addAll(globalOutput.results)
            combinedRawOutput.append("--- Pass 2: Global lint (excluding ${excludedPaths.size} files) ---\n")
            combinedRawOutput.append(globalOutput.rawOutput).append("\n")
        }

        LinterDebuggerService.getInstance(project).update(combinedRawOutput.toString(), allResults)
        
        // Refresh other open files to show potential errors found in them
        DaemonCodeAnalyzer.getInstance(project).restart()
        
        return allResults
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
