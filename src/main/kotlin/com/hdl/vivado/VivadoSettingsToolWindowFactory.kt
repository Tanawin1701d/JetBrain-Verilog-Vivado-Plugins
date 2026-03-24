package com.hdl.vivado

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.ui.components.JBScrollPane
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

class VivadoSettingsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(
            VivadoSettingsToolWindowContent(project).getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
}

class VivadoSettingsToolWindowContent(private val project: Project) {
    private val vivadoPathField = TextFieldWithBrowseButton()
    private val boardField = JBTextField()
    private val partField = JBTextField()
    private val ipRepoPathField = TextFieldWithBrowseButton()
    private val applyButton = JButton("Apply")
    private val resetButton = JButton("Reset")

    init {
        vivadoPathField.addBrowseFolderListener(
            "Select Vivado Executable",
            null,
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        ipRepoPathField.addBrowseFolderListener(
            "Select IP Repository Directory",
            null,
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        
        applyButton.addActionListener { applySettings() }
        resetButton.addActionListener { resetFields() }
        
        resetFields()
    }

    fun getContent(): JComponent {
        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Vivado Path:"), vivadoPathField, 1, false)
            .addLabeledComponent(JBLabel("Board:"), boardField, 1, false)
            .addLabeledComponent(JBLabel("Part:"), partField, 1, false)
            .addLabeledComponent(JBLabel("IP Repo Path:"), ipRepoPathField, 1, false)
            .addSeparator()
            .addComponent(JPanel().apply {
                add(applyButton)
                add(resetButton)
            })
            .addComponentFillVertically(JPanel(), 0)
            .panel

        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(JBScrollPane(formPanel), BorderLayout.CENTER)
        return mainPanel
    }

    private fun applySettings() {
        val settings = VivadoSettingsState.getInstance(project)
        settings.vivadoPath = vivadoPathField.text
        settings.board = boardField.text
        settings.part = partField.text
        settings.ipRepoPath = ipRepoPathField.text
    }

    private fun resetFields() {
        val settings = VivadoSettingsState.getInstance(project)
        vivadoPathField.text = settings.vivadoPath
        boardField.text = settings.board
        partField.text = settings.part
        ipRepoPathField.text = settings.ipRepoPath
    }
}
