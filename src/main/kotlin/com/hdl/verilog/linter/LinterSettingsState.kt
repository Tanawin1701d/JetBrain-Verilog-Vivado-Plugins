package com.hdl.verilog.linter

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "LinterSettingsState",
    storages = [Storage("verilogLinterSettings.xml")]
)
class LinterSettingsState : PersistentStateComponent<LinterSettingsState.State> {

    data class State(
        var topFolder: String? = null,
        var linterType: LinterType = LinterType.IVERILOG,
        var iverilogPath: String = "/usr/bin/iverilog",
        var verilatorPath: String = "/usr/bin/verilator"
    )

    enum class LinterType {
        IVERILOG,
        VERILATOR
    }

    private var myState = State()

    var topFolder: String?
        get() = myState.topFolder
        set(value) {
            myState.topFolder = value
        }

    var linterType: LinterType
        get() = myState.linterType
        set(value) {
            myState.linterType = value
        }

    var iverilogPath: String
        get() = myState.iverilogPath
        set(value) {
            myState.iverilogPath = value
        }

    var verilatorPath: String
        get() = myState.verilatorPath
        set(value) {
            myState.verilatorPath = value
        }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): LinterSettingsState {
            return project.getService(LinterSettingsState::class.java)
        }
    }
}
