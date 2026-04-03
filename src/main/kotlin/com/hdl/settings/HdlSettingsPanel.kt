package com.hdl.settings

import com.hdl.verilog.linter.IcarusVerilogLinter
import com.hdl.verilog.linter.LinterSettingsState
import com.hdl.verilog.linter.VerilatorLinter
import com.hdl.vivado.VivadoSettingsState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class HdlSettingsPanel(
    private val project: Project,
    showActionButtons: Boolean
) {
    private val vivadoPathField = TextFieldWithBrowseButton()
    private val boardField = JBTextField()
    private val partField = JBTextField()
    private val ipRepoPathField = TextFieldWithBrowseButton()
    private val topFolderField = TextFieldWithBrowseButton()
    private val linterTypeComboBox = ComboBox(LinterSettingsState.LinterType.entries.toTypedArray())
    private val iverilogPathField = TextFieldWithBrowseButton()
    private val verilatorPathField = TextFieldWithBrowseButton()
    private val testIverilogButton = JButton("Test")
    private val testVerilatorButton = JButton("Test")
    private val applyButton = JButton("Apply")
    private val resetButton = JButton("Reset")
    private val content: JComponent

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
        topFolderField.addBrowseFolderListener(
            "Select Verilog Top Folder",
            null,
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )
        iverilogPathField.addBrowseFolderListener(
            "Select Icarus Verilog Executable",
            null,
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )
        verilatorPathField.addBrowseFolderListener(
            "Select Verilator Executable",
            null,
            project,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        testIverilogButton.addActionListener {
            val linter = IcarusVerilogLinter()
            if (linter.isAvailable(iverilogPathField.text)) {
                Messages.showInfoMessage(project, "Icarus Verilog is available.", "Linter Test")
            } else {
                Messages.showErrorDialog(project, "Icarus Verilog is not available at the selected path.", "Linter Test")
            }
        }

        testVerilatorButton.addActionListener {
            val linter = VerilatorLinter()
            if (linter.isAvailable(verilatorPathField.text)) {
                Messages.showInfoMessage(project, "Verilator is available.", "Linter Test")
            } else {
                Messages.showErrorDialog(project, "Verilator is not available at the selected path.", "Linter Test")
            }
        }

        applyButton.addActionListener { applyToState() }
        resetButton.addActionListener { resetFromState() }

        content = buildContent(showActionButtons)
        resetFromState()
    }

    fun getComponent(): JComponent = content

    fun isModified(): Boolean {
        val vivadoSettings = VivadoSettingsState.getInstance(project)
        val linterSettings = LinterSettingsState.getInstance(project)

        return vivadoPathField.text != vivadoSettings.vivadoPath ||
            boardField.text != vivadoSettings.board ||
            partField.text != vivadoSettings.part ||
            ipRepoPathField.text != vivadoSettings.ipRepoPath ||
            topFolderField.text != (linterSettings.topFolder ?: "") ||
            linterTypeComboBox.selectedItem != linterSettings.linterType ||
            iverilogPathField.text != linterSettings.iverilogPath ||
            verilatorPathField.text != linterSettings.verilatorPath
    }

    fun applyToState() {
        if (project.isDisposed) return

        val vivadoSettings = VivadoSettingsState.getInstance(project)
        val linterSettings = LinterSettingsState.getInstance(project)

        vivadoSettings.vivadoPath = vivadoPathField.text.trim()
        vivadoSettings.board = boardField.text.trim()
        vivadoSettings.part = partField.text.trim()
        vivadoSettings.ipRepoPath = ipRepoPathField.text.trim()

        linterSettings.topFolder = topFolderField.text.trim().ifEmpty { null }
        linterSettings.linterType = linterTypeComboBox.selectedItem as LinterSettingsState.LinterType
        linterSettings.iverilogPath = iverilogPathField.text.trim()
        linterSettings.verilatorPath = verilatorPathField.text.trim()
    }

    fun resetFromState() {
        if (project.isDisposed) return

        val vivadoSettings = VivadoSettingsState.getInstance(project)
        val linterSettings = LinterSettingsState.getInstance(project)

        vivadoPathField.text = vivadoSettings.vivadoPath
        boardField.text = vivadoSettings.board
        partField.text = vivadoSettings.part
        ipRepoPathField.text = vivadoSettings.ipRepoPath

        topFolderField.text = linterSettings.topFolder.orEmpty()
        linterTypeComboBox.selectedItem = linterSettings.linterType
        iverilogPathField.text = linterSettings.iverilogPath
        verilatorPathField.text = linterSettings.verilatorPath
    }

    private fun buildContent(showActionButtons: Boolean): JComponent {
        val iverilogPanel = JPanel(BorderLayout(5, 0)).apply {
            add(iverilogPathField, BorderLayout.CENTER)
            add(testIverilogButton, BorderLayout.EAST)
        }

        val verilatorPanel = JPanel(BorderLayout(5, 0)).apply {
            add(verilatorPathField, BorderLayout.CENTER)
            add(testVerilatorButton, BorderLayout.EAST)
        }

        val builder = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Vivado"))
            .addLabeledComponent(JBLabel("Executable Path:"), vivadoPathField, 1, false)
            .addLabeledComponent(JBLabel("Board:"), boardField, 1, false)
            .addLabeledComponent(JBLabel("Part:"), partField, 1, false)
            .addLabeledComponent(JBLabel("IP Repository:"), ipRepoPathField, 1, false)
            .addSeparator()
            .addComponent(TitledSeparator("Verilog Linter"))
            .addLabeledComponent(JBLabel("Top Folder:"), topFolderField, 1, false)
            .addLabeledComponent(JBLabel("Active Linter:"), linterTypeComboBox, 1, false)
            .addLabeledComponent(JBLabel("Iverilog Path:"), iverilogPanel, 1, false)
            .addLabeledComponent(JBLabel("Verilator Path:"), verilatorPanel, 1, false)

        if (showActionButtons) {
            builder
                .addSeparator()
                .addComponent(JPanel().apply {
                    add(applyButton)
                    add(resetButton)
                })
        }

        val formPanel = builder
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(formPanel), BorderLayout.CENTER)
        }
    }
}
