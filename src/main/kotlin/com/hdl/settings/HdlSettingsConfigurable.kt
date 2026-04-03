package com.hdl.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.JComponent

class HdlSettingsConfigurable(private val project: Project) : Configurable {
    private var settingsPanel: HdlSettingsPanel? = null

    override fun getDisplayName(): String = "HDL Settings"

    override fun createComponent(): JComponent {
        val panel = HdlSettingsPanel(project, showActionButtons = false)
        settingsPanel = panel
        return panel.getComponent()
    }

    override fun isModified(): Boolean = settingsPanel?.isModified() ?: false

    override fun apply() {
        settingsPanel?.applyToState()
    }

    override fun reset() {
        settingsPanel?.resetFromState()
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
