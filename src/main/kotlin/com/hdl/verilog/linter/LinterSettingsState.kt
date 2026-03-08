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
        var topFolder: String? = null
    )

    private var myState = State()

    var topFolder: String?
        get() = myState.topFolder
        set(value) {
            myState.topFolder = value
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
