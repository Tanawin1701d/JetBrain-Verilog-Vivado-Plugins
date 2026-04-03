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
 * Verilator linter implementation
 */
class VerilatorLinter : VerilogLinter {
    private val LOG = Logger.getInstance(VerilatorLinter::class.java)
    override val name: String = "verilator"

    override fun isAvailable(toolPath: String?): Boolean {
        val path = toolPath ?: "verilator"
        return try {
            val process = ProcessBuilder(path, "--version").start()
            val result = process.waitFor()
            result == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun lint(toolPath: String?, file: VirtualFile, content: String, topFolder: VirtualFile?): LinterOutput {
        val results = mutableListOf<LintResult>()
        val rawOutput = StringBuilder()
        var tempFile: File? = null
        val path = toolPath ?: "verilator"
        
        try {
            tempFile = FileUtil.createTempFile("linter_", "." + file.extension, true)
            FileUtil.writeToFile(tempFile, content)
            
            val commandLine = GeneralCommandLine(path, "--lint-only", "-Wall")
            
            if (topFolder != null) {
                val allVerilogFiles = collectVerilogFiles(topFolder)
                for (vFile in allVerilogFiles) {
                    if (vFile.path == file.path) {
                        commandLine.addParameter(tempFile.absolutePath)
                    } else {
                        commandLine.addParameter(vFile.path)
                    }
                }
            } else {
                commandLine.addParameter(tempFile.absolutePath)
            }

            LOG.info("Running verilator linter: ${commandLine.commandLineString}")
            rawOutput.append("Running: ").append(commandLine.commandLineString).append("\n\n")
            
            val process = commandLine.createProcess()
            val reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream, StandardCharsets.UTF_8))

            fun readStream(streamReader: BufferedReader) {
                var line: String?
                while (streamReader.readLine().also { line = it } != null) {
                    line?.let {
                        LOG.info("verilator output: $it")
                        rawOutput.append(it).append("\n")
                        parseLintOutput(it, results, file.path, tempFile!!.absolutePath)
                    }
                }
            }

            readStream(reader)
            readStream(errorReader)

            val exitCode = process.waitFor()
            LOG.info("verilator finished with exit code: $exitCode")
            rawOutput.append("\nExit code: ").append(exitCode).append("\n")
            
            reader.close()
            errorReader.close()
        } catch (e: Exception) {
            LOG.error("Failed to run verilator", e)
            rawOutput.append("\nError running verilator: ").append(e.message).append("\n")
            results.add(
                LintResult(
                    file = file.path,
                    line = 0,
                    severity = LintResult.Severity.ERROR,
                    message = "Failed to run verilator: ${e.message}"
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
        // Verilator format: %Error: file:line:col: message
        // or %Warning-ID: file:line:col: message
        val regex = Regex("""^%(Error|Warning-[\w-]+):\s*(.+?):(\d+):(\d+):\s*(.*)$""", RegexOption.IGNORE_CASE)
        val match = regex.find(line)

        if (match != null) {
            val type = match.groupValues[1]
            val filePath = match.groupValues[2]
            val lineNum = match.groupValues[3]
            val colNum = match.groupValues[4]
            val message = match.groupValues[5]
            
            val severity = if (type.startsWith("Error", ignoreCase = true)) {
                LintResult.Severity.ERROR
            } else {
                LintResult.Severity.WARNING
            }

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
                    column = colNum.toIntOrNull() ?: 0,
                    severity = severity,
                    message = message
                )
            )
        }
    }
}
