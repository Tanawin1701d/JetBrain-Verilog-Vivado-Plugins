package com.hdl.verilog.linter

import com.intellij.openapi.vfs.VirtualFile
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Icarus Verilog (iverilog) linter implementation
 */
class IcarusVerilogLinter : VerilogLinter {
    override val name: String = "iverilog"

    override fun isAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("iverilog", "-V").start()
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun lint(file: VirtualFile, topFolder: VirtualFile?): List<LintResult> {
        val results = mutableListOf<LintResult>()
        
        try {
            val command = mutableListOf("iverilog", "-t", "null")
            
            // Add all .v and .sv files in the top folder if specified
            if (topFolder != null) {
                val allVerilogFiles = collectVerilogFiles(topFolder)
                command.addAll(allVerilogFiles.map { it.path })
            } else {
                command.add(file.path)
            }

            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            val process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                line?.let { parseLintOutput(it, results) }
            }

            process.waitFor()
            reader.close()
        } catch (e: Exception) {
            results.add(
                LintResult(
                    file = file.path,
                    line = 0,
                    severity = LintResult.Severity.ERROR,
                    message = "Failed to run iverilog: ${e.message}"
                )
            )
        }

        return results
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

    private fun parseLintOutput(line: String, results: MutableList<LintResult>) {
        // iverilog output format: file:line: error/warning: message
        val regex = Regex("""^(.+):(\d+):\s*(error|warning):\s*(.+)$""")
        val match = regex.find(line)

        if (match != null) {
            val (file, lineNum, severityStr, message) = match.destructured
            val severity = when (severityStr.lowercase()) {
                "error" -> LintResult.Severity.ERROR
                "warning" -> LintResult.Severity.WARNING
                else -> LintResult.Severity.INFO
            }

            results.add(
                LintResult(
                    file = file,
                    line = lineNum.toIntOrNull() ?: 0,
                    severity = severity,
                    message = message
                )
            )
        }
    }
}
