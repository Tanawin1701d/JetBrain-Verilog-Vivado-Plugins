package com.hdl.vivado

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class VivadoSettingsConfigurable(private val project: Project) : Configurable {
    private var settingsPanel: JPanel? = null
    private val vivadoPathField = TextFieldWithBrowseButton()
    private val boardField = JBTextField()
    private val partField = JBTextField()
    private val ipRepoPathField = TextFieldWithBrowseButton()

    override fun getDisplayName(): String = "Vivado Settings"

    override fun createComponent(): JComponent {
        val settings = VivadoSettingsState.getInstance(project)
        
        vivadoPathField.text = settings.vivadoPath
        boardField.text = settings.board
        partField.text = settings.part
        ipRepoPathField.text = settings.ipRepoPath

        settingsPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Vivado Path:"), vivadoPathField, 1, false)
            .addLabeledComponent(JBLabel("Board:"), boardField, 1, false)
            .addLabeledComponent(JBLabel("Part:"), partField, 1, false)
            .addLabeledComponent(JBLabel("IP Repo Path:"), ipRepoPathField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return settingsPanel!!
    }

    override fun isModified(): Boolean {
        val settings = VivadoSettingsState.getInstance(project)
        return vivadoPathField.text != settings.vivadoPath ||
               boardField.text != settings.board ||
               partField.text != settings.part ||
               ipRepoPathField.text != settings.ipRepoPath
    }

    override fun apply() {
        val settings = VivadoSettingsState.getInstance(project)
        settings.vivadoPath = vivadoPathField.text
        settings.board = boardField.text
        settings.part = partField.text
        settings.ipRepoPath = ipRepoPathField.text
    }

    override fun reset() {
        val settings = VivadoSettingsState.getInstance(project)
        vivadoPathField.text = settings.vivadoPath
        boardField.text = settings.board
        partField.text = settings.part
        ipRepoPathField.text = settings.ipRepoPath
    }
}
