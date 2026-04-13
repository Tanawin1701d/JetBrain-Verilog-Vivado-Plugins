package com.hdl.verilog.linter

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class LinterDebuggerService {
    var lastRawOutput: String = ""
    private val fileToResults = ConcurrentHashMap<String, List<LintResult>>()

    fun update(rawOutput: String, results: List<LintResult>) {
        this.lastRawOutput = rawOutput
        
        // Clear old results and update with new ones
        fileToResults.clear()
        for (result in results) {
            val absPath = java.io.File(result.file).absolutePath
            val list = fileToResults.getOrPut(absPath) { mutableListOf() } as MutableList<LintResult>
            // Avoid duplicate messages for the same file, line, and message
            if (list.none { it.line == result.line && it.message == result.message }) {
                list.add(result)
            }
        }
    }

    fun getResultsForFile(filePath: String): List<LintResult> {
        val absPath = java.io.File(filePath).absolutePath
        return fileToResults[absPath] ?: emptyList()
    }

    fun getAllResults(): List<LintResult> {
        return fileToResults.values.flatten()
    }

    companion object {
        fun getInstance(project: Project): LinterDebuggerService {
            return project.getService(LinterDebuggerService::class.java)
        }
    }
}
