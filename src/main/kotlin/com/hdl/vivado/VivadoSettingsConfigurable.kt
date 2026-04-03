package com.hdl.vivado

import com.hdl.verilog.linter.IcarusVerilogLinter
import com.hdl.verilog.linter.LinterSettingsState
import com.hdl.verilog.linter.VerilatorLinter
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class VivadoSettingsConfigurable(private val project: Project) : Configurable {
    private var settingsPanel: JPanel? = null
    
    // Vivado Settings
    private val vivadoPathField = TextFieldWithBrowseButton()
    private val boardField = JBTextField()
    private val partField = JBTextField()
    private val ipRepoPathField = TextFieldWithBrowseButton()
    
    // Linter Settings
    private val linterTypeComboBox = ComboBox(LinterSettingsState.LinterType.entries.toTypedArray())
    private val iverilogPathField = TextFieldWithBrowseButton()
    private val verilatorPathField = TextFieldWithBrowseButton()
    private val testIverilogButton = JButton("Test")
    private val testVerilatorButton = JButton("Test")

    override fun getDisplayName(): String = "Vivado Settings"

    override fun createComponent(): JComponent {
        val vivadoSettings = VivadoSettingsState.getInstance(project)
        val linterSettings = LinterSettingsState.getInstance(project)
        
        vivadoPathField.text = vivadoSettings.vivadoPath
        boardField.text = vivadoSettings.board
        partField.text = vivadoSettings.part
        ipRepoPathField.text = vivadoSettings.ipRepoPath
        
        linterTypeComboBox.selectedItem = linterSettings.linterType
        iverilogPathField.text = linterSettings.iverilogPath
        verilatorPathField.text = linterSettings.verilatorPath
        
        testIverilogButton.addActionListener {
            val linter = IcarusVerilogLinter()
            if (linter.isAvailable(iverilogPathField.text)) {
                Messages.showInfoMessage(project, "Icarus Verilog is available!", "Linter Test")
            } else {
                Messages.showErrorDialog(project, "Icarus Verilog is NOT available at the specified path.", "Linter Test")
            }
        }
        
        testVerilatorButton.addActionListener {
            val linter = VerilatorLinter()
            if (linter.isAvailable(verilatorPathField.text)) {
                Messages.showInfoMessage(project, "Verilator is available!", "Linter Test")
            } else {
                Messages.showErrorDialog(project, "Verilator is NOT available at the specified path.", "Linter Test")
            }
        }

        val iverilogPanel = JPanel(BorderLayout(5, 0))
        iverilogPanel.add(iverilogPathField, BorderLayout.CENTER)
        iverilogPanel.add(testIverilogButton, BorderLayout.EAST)
        
        val verilatorPanel = JPanel(BorderLayout(5, 0))
        verilatorPanel.add(verilatorPathField, BorderLayout.CENTER)
        verilatorPanel.add(testVerilatorButton, BorderLayout.EAST)

        settingsPanel = FormBuilder.createFormBuilder()
            .addComponent(TitledSeparator("Vivado Integration"))
            .addLabeledComponent(JBLabel("Vivado Path:"), vivadoPathField, 1, false)
            .addLabeledComponent(JBLabel("Board:"), boardField, 1, false)
            .addLabeledComponent(JBLabel("Part:"), partField, 1, false)
            .addLabeledComponent(JBLabel("IP Repo Path:"), ipRepoPathField, 1, false)
            .addSeparator()
            .addComponent(TitledSeparator("Verilog Linter"))
            .addLabeledComponent(JBLabel("Linter Tool:"), linterTypeComboBox, 1, false)
            .addLabeledComponent(JBLabel("Iverilog Path:"), iverilogPanel, 1, false)
            .addLabeledComponent(JBLabel("Verilator Path:"), verilatorPanel, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        return settingsPanel!!
    }

    override fun isModified(): Boolean {
        val vivadoSettings = VivadoSettingsState.getInstance(project)
        val linterSettings = LinterSettingsState.getInstance(project)
        
        return vivadoPathField.text != vivadoSettings.vivadoPath ||
               boardField.text != vivadoSettings.board ||
               partField.text != vivadoSettings.part ||
               ipRepoPathField.text != vivadoSettings.ipRepoPath ||
               linterTypeComboBox.selectedItem != linterSettings.linterType ||
               iverilogPathField.text != linterSettings.iverilogPath ||
               verilatorPathField.text != linterSettings.verilatorPath
    }

    override fun apply() {
        val vivadoSettings = VivadoSettingsState.getInstance(project)
        val linterSettings = LinterSettingsState.getInstance(project)
        
        vivadoSettings.vivadoPath = vivadoPathField.text
        vivadoSettings.board = boardField.text
        vivadoSettings.part = partField.text
        vivadoSettings.ipRepoPath = ipRepoPathField.text
        
        linterSettings.linterType = linterTypeComboBox.selectedItem as LinterSettingsState.LinterType
        linterSettings.iverilogPath = iverilogPathField.text
        linterSettings.verilatorPath = verilatorPathField.text
    }

    override fun reset() {
        val vivadoSettings = VivadoSettingsState.getInstance(project)
        val linterSettings = LinterSettingsState.getInstance(project)
        
        vivadoPathField.text = vivadoSettings.vivadoPath
        boardField.text = vivadoSettings.board
        partField.text = vivadoSettings.part
        ipRepoPathField.text = vivadoSettings.ipRepoPath
        
        linterTypeComboBox.selectedItem = linterSettings.linterType
        iverilogPathField.text = linterSettings.iverilogPath
        verilatorPathField.text = linterSettings.verilatorPath
    }
}
