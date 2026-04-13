package com.hdl.verilog.linter

import com.intellij.openapi.vfs.VirtualFile

/**
 * Interface for Verilog linters
 */
interface VerilogLinter {
    /** Name of the linter */
    val name: String

    /**
     * Check if the linter binary is reachable and exits successfully.
     * Used by the annotator before each lint run.
     */
    fun isAvailable(toolPath: String? = null): Boolean

    /**
     * Deeply verify that the binary at [toolPath] is actually this linter.
     * Checks file existence/executability (for absolute paths) and inspects
     * the version output to confirm identity.
     * @return Pair(ok, human-readable message)
     */
    fun verifyTool(toolPath: String): Pair<Boolean, String>

    /**
     * Lint a Verilog/SV file.
     * @param toolPath  Absolute path or system-PATH name of the linter binary.
     * @param file      The file being linted (for path metadata).
     * @param content   Actual file content (may differ from disk for unsaved buffers).
     * @param topFolder All files under this folder are passed to the linter for
     *                  cross-module resolution. Null = single-file mode.
     * @param topFile   The designated top-level file inside [topFolder]; its module
     *                  name is forwarded as the elaboration entry-point flag.
     *                  Null = no explicit top module.
     */
    fun lint(
        toolPath: String?,
        file: VirtualFile,
        content: String,
        topFolder: VirtualFile?,
        topFile: VirtualFile? = null
    ): LinterOutput
}

/** Aggregated result of a lint run. */
data class LinterOutput(
    val results: List<LintResult>,
    val rawOutput: String
)

/** A single diagnostic produced by the linter. */
data class LintResult(
    val file: String,
    val line: Int,
    val column: Int = 0,
    val severity: Severity,
    val message: String
) {
    enum class Severity { ERROR, WARNING, INFO }
}
