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
 * Icarus Verilog (iverilog) linter implementation.
 */
class IcarusVerilogLinter : VerilogLinter {
    private val LOG = Logger.getInstance(IcarusVerilogLinter::class.java)
    override val name: String = "iverilog"

    // -------------------------------------------------------------------------
    // isAvailable — lightweight check used before every lint run
    // -------------------------------------------------------------------------
    override fun isAvailable(toolPath: String?): Boolean {
        val path = toolPath ?: "iverilog"
        return try {
            val process = ProcessBuilder(path, "-V")
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
            if (!file.exists())  return false to "File not found:\n$toolPath"
            if (!file.canExecute()) return false to "File is not executable:\n$toolPath"
        }
        return try {
            val process = ProcessBuilder(toolPath, "-V")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (output.contains("Icarus Verilog", ignoreCase = true)) {
                true to "Icarus Verilog verified successfully.\n\nVersion output:\n$output"
            } else {
                false to "The binary at '$toolPath' does not appear to be Icarus Verilog.\n\nActual output:\n$output"
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
        topFile: VirtualFile?
    ): LinterOutput {
        val results    = mutableListOf<LintResult>()
        val rawOutput  = StringBuilder()
        var tempFile: File? = null
        val path = toolPath ?: "iverilog"

        try {
            tempFile = FileUtil.createTempFile("linter_", ".${file.extension}", true)
            FileUtil.writeToFile(tempFile, content)

            val commandLine = GeneralCommandLine(path, "-t", "null")

            // Pass the top-module name as the elaboration entry-point (-s flag)
            val topModuleName = topFile?.let { resolveTopModuleName(it, file, content) }
            if (topModuleName != null) {
                commandLine.addParameter("-s")
                commandLine.addParameter(topModuleName)
            }

            if (topFolder != null) {
                // Use java.io.File.walkTopDown() to collect files from disk directly.
                // This avoids stale/empty VirtualFile.children from the VFS cache.
                val currentFileCanonical = File(file.path).canonicalPath
                val allPaths = collectVerilogFilePaths(topFolder.path)

                if (allPaths.isEmpty()) {
                    // Top folder had no verilog files — still lint the current file
                    commandLine.addParameter(tempFile.absolutePath)
                } else {
                    var currentIncluded = false
                    for (p in allPaths) {
                        if (File(p).canonicalPath == currentFileCanonical) {
                            commandLine.addParameter(tempFile.absolutePath)
                            currentIncluded = true
                        } else {
                            commandLine.addParameter(p)
                        }
                    }
                    // If the file being linted is outside the top folder, include it anyway
                    if (!currentIncluded) {
                        commandLine.addParameter(tempFile.absolutePath)
                    }
                }
            } else {
                commandLine.addParameter(tempFile.absolutePath)
            }

            LOG.info("Running iverilog: ${commandLine.commandLineString}")
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
            LOG.error("Failed to run iverilog", e)
            rawOutput.append("\nError running iverilog: ").append(e.message).append("\n")
            results.add(LintResult(file.path, 0, severity = LintResult.Severity.ERROR,
                message = "Failed to run iverilog: ${e.message}"))
        } finally {
            tempFile?.delete()
        }

        return LinterOutput(results, rawOutput.toString())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Collect all Verilog/SV file paths under [folderPath] using the real
     * filesystem (java.io.File) so that we never see stale VFS data.
     */
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
        val regex = Regex("""^(.+?):(\d+)(?::\d+)?:\s*(?:(error|warning):\s*)?(.+)$""", RegexOption.IGNORE_CASE)
        val match = regex.find(line) ?: return

        val filePath    = match.groupValues[1]
        val lineNum     = match.groupValues[2]
        val severityStr = match.groupValues[3]
        val message     = match.groupValues[4]

        val severity = when (severityStr.lowercase()) {
            "warning" -> LintResult.Severity.WARNING
            else      -> LintResult.Severity.ERROR
        }

        val absolutePath = File(filePath).canonicalPath
        val finalPath    = if (absolutePath == File(tempFilePath).canonicalPath) originalFilePath else absolutePath

        results.add(LintResult(file = finalPath, line = lineNum.toIntOrNull() ?: 0,
            severity = severity, message = message))
    }
}
