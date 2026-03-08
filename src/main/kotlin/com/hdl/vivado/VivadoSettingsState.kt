package com.hdl.vivado

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@State(
    name = "VivadoSettingsState",
    storages = [Storage("vivadoSettings.xml")]
)
class VivadoSettingsState : PersistentStateComponent<VivadoSettingsState.State> {
    
    data class State(
        var vivadoPath: String = "/tools/Xilinx/Vivado/2023.2/bin/vivado",
        var board: String = "",
        var part: String = "xc7a35tcpg236-1"
    )

    private var myState = State()

    var vivadoPath: String
        get() = myState.vivadoPath
        set(value) {
            myState.vivadoPath = value
        }

    var board: String
        get() = myState.board
        set(value) {
            myState.board = value
        }

    var part: String
        get() = myState.part
        set(value) {
            myState.part = value
        }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): VivadoSettingsState {
            return project.getService(VivadoSettingsState::class.java)
        }
    }
}
