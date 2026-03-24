package com.hdl.verilog.linter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.io.FileUtil
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Icarus Verilog (iverilog) linter implementation
 */
class IcarusVerilogLinter : VerilogLinter {
    private val LOG = Logger.getInstance(IcarusVerilogLinter::class.java)
    override val name: String = "iverilog"

    override fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("iverilog", "-V").start()
            val result = process.waitFor()
            result == 0
        } catch (e: Exception) {
            // Log or handle the exception appropriately in a real-world scenario
            false
        }
    }

    override fun lint(file: VirtualFile, content: String, topFolder: VirtualFile?): LinterOutput {
        val results = mutableListOf<LintResult>()
        val rawOutput = StringBuilder()
        var tempFile: File? = null
        
        try {
            // Create a temporary file with the current content to handle unsaved changes
            // We use the same extension as the original file
            tempFile = FileUtil.createTempFile("linter_", "." + file.extension, true)
            FileUtil.writeToFile(tempFile, content)
            
            val commandLine = GeneralCommandLine("iverilog", "-t", "null")
            
            if (topFolder != null) {
                val allVerilogFiles = collectVerilogFiles(topFolder)
                for (vFile in allVerilogFiles) {
                    if (vFile.path == file.path) {
                        // Use the temporary file instead of the actual file on disk
                        commandLine.addParameter(tempFile.absolutePath)
                    } else {
                        commandLine.addParameter(vFile.path)
                    }
                }
            } else {
                commandLine.addParameter(tempFile.absolutePath)
            }

            LOG.info("Running iverilog linter: ${commandLine.commandLineString}")
            rawOutput.append("Running: ").append(commandLine.commandLineString).append("\n\n")
            
            val process = commandLine.createProcess()
            val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))

            // Read both streams
            fun readStream(streamReader: BufferedReader) {
                var line: String?
                while (streamReader.readLine().also { line = it } != null) {
                    line?.let {
                        LOG.info("iverilog output: $it")
                        rawOutput.append(it).append("\n")
                        parseLintOutput(it, results, file.path, tempFile!!.absolutePath)
                    }
                }
            }

            readStream(reader)
            readStream(errorReader)

            val exitCode = process.waitFor()
            LOG.info("iverilog finished with exit code: $exitCode")
            rawOutput.append("\nExit code: ").append(exitCode).append("\n")
            
            reader.close()
            errorReader.close()
        } catch (e: Exception) {
            LOG.error("Failed to run iverilog", e)
            rawOutput.append("\nError running iverilog: ").append(e.message).append("\n")
            results.add(
                LintResult(
                    file = file.path,
                    line = 0,
                    severity = LintResult.Severity.ERROR,
                    message = "Failed to run iverilog: ${e.message}"
                )
            )
        } finally {
            tempFile?.delete()
        }

        return LinterOutput(results, rawOutput.toString())
    }

    private fun collectVerilogFiles(folder: VirtualFile): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        
        fun traverse(dir: VirtualFile) {
            for (child in dir.children) {
                if (child.isDirectory) {
                    traverse(child)
                } else if (child.extension in listOf("v", "vh", "sv", "svh")) {
                    files.add(child)
                }
            }
        }
        
        traverse(folder)
        return files
    }

    private fun parseLintOutput(line: String, results: MutableList<LintResult>, originalFilePath: String, tempFilePath: String) {
        // iverilog output format: file:line: [error/warning:] message
        // Using a more flexible regex that handles potential column numbers and optional severity
        val regex = Regex("""^(.+?):(\d+)(?::\d+)?:\s*(?:(error|warning):\s*)?(.+)$""", RegexOption.IGNORE_CASE)
        val match = regex.find(line)

        if (match != null) {
            val filePath = match.groupValues[1]
            val lineNum = match.groupValues[2]
            val severityStr = match.groupValues[3]
            val message = match.groupValues[4]
            
            val severity = when (severityStr.lowercase()) {
                "error" -> LintResult.Severity.ERROR
                "warning" -> LintResult.Severity.WARNING
                else -> LintResult.Severity.ERROR // Default to error if not specified (e.g. syntax error)
            }

            // Convert path back to original path if it's the temp file
            val absolutePath = File(filePath).absolutePath
            val finalPath = if (absolutePath == File(tempFilePath).absolutePath) {
                originalFilePath
            } else {
                absolutePath
            }

            results.add(
                LintResult(
                    file = finalPath,
                    line = lineNum.toIntOrNull() ?: 0,
                    severity = severity,
                    message = message
                )
            )
        }
    }
}
