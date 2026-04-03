package com.hdl.verilog.linter

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Interface for Verilog linters
 */
interface VerilogLinter {
    /**
     * Name of the linter
     */
    val name: String

    /**
     * Check if the linter is available on the system
     * @param toolPath Custom path to the linter tool
     */
    fun isAvailable(toolPath: String? = null): Boolean

    /**
     * Lint a Verilog file
     * @param toolPath Custom path to the linter tool
     * @param file The file to lint (VirtualFile for metadata and path)
     * @param content The actual content of the file (to handle unsaved changes)
     * @param topFolder The top folder for context (all files in this folder are accessible)
     * @return Result object containing the lint results and the raw output
     */
    fun lint(toolPath: String?, file: VirtualFile, content: String, topFolder: VirtualFile?): LinterOutput
}

/**
 * Result of the linting process
 */
data class LinterOutput(
    val results: List<LintResult>,
    val rawOutput: String
)

/**
 * Result of linting a file
 */
data class LintResult(
    val file: String,
    val line: Int,
    val column: Int = 0,
    val severity: Severity,
    val message: String
) {
    enum class Severity {
        ERROR, WARNING, INFO
    }
}
