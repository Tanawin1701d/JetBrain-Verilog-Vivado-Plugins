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
     */
    fun isAvailable(): Boolean

    /**
     * Lint a Verilog file
     * @param file The file to lint
     * @param topFolder The top folder for context (all files in this folder are accessible)
     * @return List of lint results
     */
    fun lint(file: VirtualFile, topFolder: VirtualFile?): List<LintResult>
}

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
