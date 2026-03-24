package com.hdl.verilog.linter

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class LinterDebuggerService {
    var lastRawOutput: String = ""
    var lastResults: List<LintResult> = emptyList()

    fun update(rawOutput: String, results: List<LintResult>) {
        this.lastRawOutput = rawOutput
        this.lastResults = results
    }

    companion object {
        fun getInstance(project: Project): LinterDebuggerService {
            return project.getService(LinterDebuggerService::class.java)
        }
    }
}
