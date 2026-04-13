package com.hdl.verilog.linter

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Verilator linter implementation.
 */
class VerilatorLinter : VerilogLinter {
    private val LOG = Logger.getInstance(VerilatorLinter::class.java)
    override val name: String = "verilator"

    // -------------------------------------------------------------------------
    // isAvailable — lightweight check used before every lint run
    // -------------------------------------------------------------------------
    override fun isAvailable(toolPath: String?): Boolean {
        val path = toolPath ?: "verilator"
        return try {
            val process = ProcessBuilder(path, "--version")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    // -------------------------------------------------------------------------
    // verifyTool — thorough check used by the "Test" button in settings
    // -------------------------------------------------------------------------
    override fun verifyTool(toolPath: String): Pair<Boolean, String> {
        val file = File(toolPath)
        if (file.isAbsolute) {
            if (!file.exists())     return false to "File not found:\n$toolPath"
            if (!file.canExecute()) return false to "File is not executable:\n$toolPath"
        }
        return try {
            val process = ProcessBuilder(toolPath, "--version")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.contains("Verilator", ignoreCase = true)) {
                true to "Verilator verified successfully.\n\nVersion output:\n$output"
            } else {
                false to "The binary at '$toolPath' does not appear to be Verilator.\n\nActual output:\n$output"
            }
        } catch (e: Exception) {
            false to "Failed to run '$toolPath':\n${e.message}"
        }
    }

    // -------------------------------------------------------------------------
    // lint
    // -------------------------------------------------------------------------
    override fun lint(
        toolPath: String?,
        file: VirtualFile,
        content: String,
        topFolder: VirtualFile?,
        topFile: VirtualFile?,
        excludePaths: Set<String>
    ): LinterOutput {
        val results   = mutableListOf<LintResult>()
        val rawOutput = StringBuilder()
        var tempFile: File? = null
        val path = toolPath ?: "verilator"

        try {
            tempFile = FileUtil.createTempFile("linter_", ".${file.extension}", true)
            FileUtil.writeToFile(tempFile, content)

            val commandLine = GeneralCommandLine(path, "--lint-only", "-Wall")

            // Pass the top-module name (--top-module flag)
//            val topModuleName = topFile?.let { resolveTopModuleName(it, file, content) }
//            if (topModuleName != null) {
//                commandLine.addParameter("--top-module")
//                commandLine.addParameter(topModuleName)
//            }

            if (topFolder != null) {
                // Use java.io.File.walkTopDown() to collect files from disk directly.
                // This avoids stale/empty VirtualFile.children from the VFS cache.
                val currentFileCanonical = File(file.path).canonicalPath
                val allPaths = collectVerilogFilePaths(topFolder.path)

                if (allPaths.isEmpty()) {
                    commandLine.addParameter(tempFile.absolutePath)
                } else {
                    var currentIncluded = false
                    val canonicalExcludes = excludePaths.map { File(it).canonicalPath }.toSet()
                    for (p in allPaths) {
                        val pCanonical = File(p).canonicalPath
                        if (pCanonical in canonicalExcludes) continue

                        if (pCanonical == currentFileCanonical) {
                            commandLine.addParameter(tempFile.absolutePath)
                            currentIncluded = true
                        } else {
                            commandLine.addParameter(p)
                        }
                    }
                    if (!currentIncluded && currentFileCanonical !in canonicalExcludes) {
                        commandLine.addParameter(tempFile.absolutePath)
                    }
                }
            } else {
                commandLine.addParameter(tempFile.absolutePath)
            }

            LOG.info("Running verilator: ${commandLine.commandLineString}")
            rawOutput.append("Running: ").append(commandLine.commandLineString).append("\n\n")

            val process  = commandLine.createProcess()
            val reader    = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
            val errReader = BufferedReader(InputStreamReader(process.errorStream,  StandardCharsets.UTF_8))

            fun readStream(r: BufferedReader) {
                var line: String?
                while (r.readLine().also { line = it } != null) {
                    line?.let {
                        rawOutput.append(it).append("\n")
                        parseLintOutput(it, results, file.path, tempFile!!.absolutePath)
                    }
                }
            }
            readStream(reader)
            readStream(errReader)

            val exitCode = process.waitFor()
            rawOutput.append("\nExit code: ").append(exitCode).append("\n")
            reader.close()
            errReader.close()

        } catch (e: Exception) {
            LOG.error("Failed to run verilator", e)
            rawOutput.append("\nError running verilator: ").append(e.message).append("\n")
            results.add(LintResult(file.path, 0, severity = LintResult.Severity.ERROR,
                message = "Failed to run verilator: ${e.message}"))
        } finally {
            tempFile?.delete()
        }

        return LinterOutput(results, rawOutput.toString())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun collectVerilogFilePaths(folderPath: String): List<String> {
        val verilogExts = setOf("v", "vh", "sv", "svh")
        return File(folderPath)
            .walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in verilogExts }
            .map  { it.canonicalPath }
            .toList()
    }

    private fun resolveTopModuleName(topFile: VirtualFile, currentFile: VirtualFile, content: String): String? {
        val topContent = if (topFile.path == currentFile.path) {
            content
        } else {
            try { topFile.contentsToByteArray().toString(Charsets.UTF_8) } catch (e: Exception) { return null }
        }
        return extractModuleName(topContent)
    }

    private fun extractModuleName(content: String): String? {
        val regex = Regex("""(?:^|[\r\n])\s*module\s+(\w+)""")
        return regex.find(content)?.groupValues?.get(1)
    }

    private fun parseLintOutput(
        line: String,
        results: MutableList<LintResult>,
        originalFilePath: String,
        tempFilePath: String
    ) {
        val regex = Regex("""^%(Error|Warning-[\w-]+):\s*(.+?):(\d+):(\d+):\s*(.*)$""", RegexOption.IGNORE_CASE)
        val match = regex.find(line) ?: return

        val type     = match.groupValues[1]
        val filePath = match.groupValues[2]
        val lineNum  = match.groupValues[3]
        val colNum   = match.groupValues[4]
        val message  = match.groupValues[5]

        val severity = if (type.startsWith("Error", ignoreCase = true))
            LintResult.Severity.ERROR else LintResult.Severity.WARNING

        val absolutePath = File(filePath).canonicalPath
        val finalPath    = if (absolutePath == File(tempFilePath).canonicalPath) originalFilePath else absolutePath

        results.add(LintResult(file = finalPath, line = lineNum.toIntOrNull() ?: 0,
            column = colNum.toIntOrNull() ?: 0, severity = severity, message = message))
    }
}
