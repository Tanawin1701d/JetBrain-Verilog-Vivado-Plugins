package com.hdl.settings

import com.hdl.verilog.linter.IcarusVerilogLinter
import com.hdl.verilog.linter.LinterSettingsBroadcaster
import com.hdl.verilog.linter.LinterSettingsState
import com.hdl.verilog.linter.VerilatorLinter
import com.hdl.vivado.VivadoSettingsState
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.SideBorder
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class HdlSettingsPanel(
    private val project: Project,
    private val showActionButtons: Boolean
) {
    // -------------------------------------------------------------------------
    // Vivado fields
    // -------------------------------------------------------------------------
    private val vivadoPathField   = TextFieldWithBrowseButton()
    private val vitisPathField    = TextFieldWithBrowseButton()
    private val boardField        = JBTextField()
    private val partField         = JBTextField()
    private val ipRepoPathField   = TextFieldWithBrowseButton()

    // -------------------------------------------------------------------------
    // Viva-CoTerm / MCP fields
    // -------------------------------------------------------------------------
    private val mcpPortSpinner     = JSpinner(SpinnerNumberModel(19999, 1024, 65535, 1))
    private val defaultJobsSpinner = JSpinner(SpinnerNumberModel(4, 1, 128, 1))
    private val cmdTimeoutSpinner  = JSpinner(SpinnerNumberModel(10, 1, 120, 1))

    // -------------------------------------------------------------------------
    // Linter fields
    // -------------------------------------------------------------------------
    private val topFolderField    = TextFieldWithBrowseButton()
    private val unsetTopFolderButton = JButton("Unset")
    private val linterTypeComboBox = ComboBox(LinterSettingsState.LinterType.entries.toTypedArray())
    private val iverilogPathField  = TextFieldWithBrowseButton()
    private val verilatorPathField = TextFieldWithBrowseButton()

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------
    private val testIverilogButton  = JButton("Test")
    private val testVerilatorButton = JButton("Test")
    private val applyButton         = JButton("Apply")
    private val resetButton         = JButton("Reset")
    private val helpButton          = JButton("? Help")

    // -------------------------------------------------------------------------
    // Unsaved-changes indicator + blink timer
    // -------------------------------------------------------------------------
    private val unsavedLabel = JLabel("\u25CF Unsaved changes").apply {
        foreground = Color(210, 100, 0)
        font       = font.deriveFont(Font.BOLD)
        isVisible  = false
    }

    /** Original button foreground; restored when blinking stops. */
    private val defaultApplyFg: Color get() = UIManager.getColor("Button.foreground") ?: applyButton.foreground

    private var blinkState  = false
    private var blinkTimer: Timer? = null

    /** Keeps the panel in sync when right-click actions change LinterSettingsState externally. */
    private val externalChangeListener: () -> Unit = {
        if (!project.isDisposed) resetFromState()
    }

    // -------------------------------------------------------------------------
    // Root component
    // -------------------------------------------------------------------------
    private val content: JComponent

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------
    init {
        setupBrowseListeners()
        setupTestButtons()
        setupActionButtons()
        setupChangeListeners()
        content = buildContent()
        resetFromState()
        subscribeToExternalChanges()
    }

    fun getComponent(): JComponent = content

    // -------------------------------------------------------------------------
    // Public API used by Configurable and ToolWindowFactory
    // -------------------------------------------------------------------------
    fun isModified(): Boolean {
        val vs = VivadoSettingsState.getInstance(project)
        val ls = LinterSettingsState.getInstance(project)
        return vivadoPathField.text    != vs.vivadoPath        ||
               vitisPathField.text     != vs.vitisPath         ||
               boardField.text         != vs.board              ||
               partField.text          != vs.part               ||
               ipRepoPathField.text    != vs.ipRepoPath         ||
               topFolderField.text     != (ls.topFolder ?: "")  ||
               linterTypeComboBox.selectedItem != ls.linterType  ||
               iverilogPathField.text  != ls.iverilogPath       ||
               verilatorPathField.text != ls.verilatorPath      ||
               (mcpPortSpinner.value as Int)    != vs.mcpPort   ||
               (defaultJobsSpinner.value as Int) != vs.defaultJobs ||
               (cmdTimeoutSpinner.value as Int)  != vs.cmdTimeoutMin
    }

    fun applyToState() {
        if (project.isDisposed) return

        val vs = VivadoSettingsState.getInstance(project)
        val ls = LinterSettingsState.getInstance(project)

        val previousLinterType = ls.linterType
        val previousIverilogPath = ls.iverilogPath
        val previousVerilatorPath = ls.verilatorPath

        vs.vivadoPath    = vivadoPathField.text.trim()
        vs.vitisPath     = vitisPathField.text.trim()
        vs.board         = boardField.text.trim()
        vs.part          = partField.text.trim()
        vs.ipRepoPath    = ipRepoPathField.text.trim()
        vs.mcpPort       = mcpPortSpinner.value as Int
        vs.defaultJobs   = defaultJobsSpinner.value as Int
        vs.cmdTimeoutMin = cmdTimeoutSpinner.value as Int

        val previousTopFolder = ls.topFolder
        val newTopFolder = topFolderField.text.trim().ifEmpty { null }
        ls.topFolder   = newTopFolder

        ls.linterType    = linterTypeComboBox.selectedItem as LinterSettingsState.LinterType
        ls.iverilogPath  = iverilogPathField.text.trim()
        ls.verilatorPath = verilatorPathField.text.trim()

        if (previousTopFolder != ls.topFolder ||
            previousLinterType != ls.linterType ||
            previousIverilogPath != ls.iverilogPath ||
            previousVerilatorPath != ls.verilatorPath) {
            ProjectView.getInstance(project).refresh()
            LinterSettingsBroadcaster.getInstance(project).notifyChanged()
        }

        stopBlinking()
    }

    fun resetFromState() {
        if (project.isDisposed) return

        val vs = VivadoSettingsState.getInstance(project)
        val ls = LinterSettingsState.getInstance(project)

        vivadoPathField.text    = vs.vivadoPath
        vitisPathField.text     = vs.vitisPath
        boardField.text         = vs.board
        partField.text          = vs.part
        ipRepoPathField.text    = vs.ipRepoPath
        mcpPortSpinner.value    = vs.mcpPort
        defaultJobsSpinner.value = vs.defaultJobs
        cmdTimeoutSpinner.value  = vs.cmdTimeoutMin

        topFolderField.text     = ls.topFolder.orEmpty()

        linterTypeComboBox.selectedItem = ls.linterType
        iverilogPathField.text  = ls.iverilogPath
        verilatorPathField.text = ls.verilatorPath

        stopBlinking()
    }

    /** Must be called when the panel is no longer needed (Configurable.disposeUIResources). */
    fun dispose() {
        stopBlinking()
        if (!project.isDisposed) {
            LinterSettingsBroadcaster.getInstance(project).unsubscribe(externalChangeListener)
        }
    }

    /**
     * Subscribe to external settings changes (right-click actions) so the panel
     * stays in sync without requiring the user to manually refresh.
     */
    private fun subscribeToExternalChanges() {
        if (project.isDisposed) return
        LinterSettingsBroadcaster.getInstance(project).subscribe(externalChangeListener)
    }

    // -------------------------------------------------------------------------
    // Browse listeners
    // -------------------------------------------------------------------------
    private fun setupBrowseListeners() {
        unsetTopFolderButton.addActionListener {
            topFolderField.text = ""
            onFieldChanged()
        }
        vivadoPathField.addBrowseFolderListener("Select Vivado Executable", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor())

        vitisPathField.addBrowseFolderListener("Select Vitis Executable", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor())

        ipRepoPathField.addBrowseFolderListener("Select IP Repository Directory", null, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor())

        topFolderField.addBrowseFolderListener("Select Verilog Top Folder", null, project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor())

        iverilogPathField.addBrowseFolderListener("Select Icarus Verilog Executable", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor())

        verilatorPathField.addBrowseFolderListener("Select Verilator Executable", null, project,
            FileChooserDescriptorFactory.createSingleFileDescriptor())
    }

    // -------------------------------------------------------------------------
    // Test buttons — run on a pooled thread so EDT is not blocked
    // -------------------------------------------------------------------------
    private fun setupTestButtons() {
        testIverilogButton.addActionListener {
            val path = iverilogPathField.text.trim()
            testIverilogButton.isEnabled = false
            ApplicationManager.getApplication().executeOnPooledThread {
                val (ok, msg) = IcarusVerilogLinter().verifyTool(path)
                SwingUtilities.invokeLater {
                    testIverilogButton.isEnabled = true
                    if (ok) Messages.showInfoMessage(project, msg, "Linter Verification")
                    else    Messages.showErrorDialog(project, msg, "Linter Verification")
                }
            }
        }

        testVerilatorButton.addActionListener {
            val path = verilatorPathField.text.trim()
            testVerilatorButton.isEnabled = false
            ApplicationManager.getApplication().executeOnPooledThread {
                val (ok, msg) = VerilatorLinter().verifyTool(path)
                SwingUtilities.invokeLater {
                    testVerilatorButton.isEnabled = true
                    if (ok) Messages.showInfoMessage(project, msg, "Linter Verification")
                    else    Messages.showErrorDialog(project, msg, "Linter Verification")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Apply / Reset / Help buttons
    // -------------------------------------------------------------------------
    private fun setupActionButtons() {
        applyButton.addActionListener { applyToState() }
        resetButton.addActionListener { resetFromState() }
        helpButton.addActionListener  { HdlTutorialDialog(project).show() }
    }

    // -------------------------------------------------------------------------
    // Change listeners — start blinking when any field is edited
    // -------------------------------------------------------------------------
    private fun setupChangeListeners() {
        val dl = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?)  = onFieldChanged()
            override fun removeUpdate(e: DocumentEvent?)  = onFieldChanged()
            override fun changedUpdate(e: DocumentEvent?) = onFieldChanged()
        }
        vivadoPathField.textField.document.addDocumentListener(dl)
        vitisPathField.textField.document.addDocumentListener(dl)
        boardField.document.addDocumentListener(dl)
        partField.document.addDocumentListener(dl)
        ipRepoPathField.textField.document.addDocumentListener(dl)
        topFolderField.textField.document.addDocumentListener(dl)
        iverilogPathField.textField.document.addDocumentListener(dl)
        verilatorPathField.textField.document.addDocumentListener(dl)
        linterTypeComboBox.addItemListener { onFieldChanged() }
        mcpPortSpinner.addChangeListener    { onFieldChanged() }
        defaultJobsSpinner.addChangeListener { onFieldChanged() }
        cmdTimeoutSpinner.addChangeListener  { onFieldChanged() }
    }

    private fun onFieldChanged() {
        if (!isModified()) {
            stopBlinking()
            return
        }
        startBlinking()
    }

    // -------------------------------------------------------------------------
    // Blink logic
    // -------------------------------------------------------------------------
    private fun startBlinking() {
        if (blinkTimer?.isRunning == true) return
        blinkTimer = Timer(550) {
            blinkState = !blinkState
            unsavedLabel.isVisible = blinkState
            if (showActionButtons) {
                applyButton.foreground = if (blinkState) Color(210, 100, 0) else defaultApplyFg
            }
        }.also { it.start() }
        // Show immediately on first change
        unsavedLabel.isVisible = true
        if (showActionButtons) applyButton.foreground = Color(210, 100, 0)
    }

    private fun stopBlinking() {
        blinkTimer?.stop()
        blinkTimer = null
        blinkState = false
        unsavedLabel.isVisible = false
        applyButton.foreground = defaultApplyFg
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------
    private fun buildContent(): JComponent {
        // Top bar: Help button (right-aligned) + unsaved indicator
        val topBar = JPanel(BorderLayout(8, 0)).apply {
            val helpPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                add(helpButton)
            }
            add(unsavedLabel, BorderLayout.WEST)
            add(helpPanel,    BorderLayout.EAST)
            maximumSize = Dimension(Int.MAX_VALUE, 28)
        }

        val iverilogRow = JPanel(BorderLayout(5, 0)).apply {
            add(iverilogPathField,  BorderLayout.CENTER)
            add(testIverilogButton, BorderLayout.EAST)
        }
        val verilatorRow = JPanel(BorderLayout(5, 0)).apply {
            add(verilatorPathField,  BorderLayout.CENTER)
            add(testVerilatorButton, BorderLayout.EAST)
        }

        val topFolderRow = JPanel(BorderLayout(5, 0)).apply {
            add(topFolderField, BorderLayout.CENTER)
            add(unsetTopFolderButton, BorderLayout.EAST)
            border = SideBorder(JBColor.YELLOW, SideBorder.ALL, 2)
        }

        val builder = FormBuilder.createFormBuilder()
            .addComponent(topBar)
            .addSeparator()
            .addComponent(TitledSeparator("Vivado"))
            .addLabeledComponent(JBLabel("Vivado Path:"), vivadoPathField,   1, false)
            .addLabeledComponent(JBLabel("Vitis Path:"),  vitisPathField,    1, false)
            .addLabeledComponent(JBLabel("Board:"),           boardField,         1, false)
            .addLabeledComponent(JBLabel("Part:"),            partField,          1, false)
            .addLabeledComponent(JBLabel("IP Repository:"),   ipRepoPathField,    1, false)
            .addSeparator()
            .addComponent(TitledSeparator("Verilog Linter"))
            .addLabeledComponent(JBLabel("Top Folder:"),      topFolderRow,       1, false)
            .addComponentToRightColumn(JBLabel("(to check syntax of the of linter please check the top folder if it not work please see the verilog linter debugger panel)").apply {
                font = font.deriveFont(Font.ITALIC, 11f)
                foreground = JBColor.YELLOW
            })
            .addComponentToRightColumn(JBLabel("Leave Top Folder blank to unset and remove icons.").apply {
                font = font.deriveFont(Font.ITALIC, 11f)
                foreground = Color.GRAY
            })
            .addLabeledComponent(JBLabel("Active Linter:"),   linterTypeComboBox, 1, false)
            .addLabeledComponent(JBLabel("Iverilog Path:"),   iverilogRow,        1, false)
            .addLabeledComponent(JBLabel("Verilator Path:"),  verilatorRow,       1, false)
            .addSeparator()
            .addComponent(TitledSeparator("Viva-CoTerm / MCP Server"))
            .addLabeledComponent(JBLabel("MCP Port:"),          mcpPortSpinner,      1, false)
            .addComponentToRightColumn(JBLabel("Configure Claude Code with: http://127.0.0.1:<port>").apply {
                font = font.deriveFont(Font.ITALIC, 11f)
                foreground = Color.GRAY
            })
            .addLabeledComponent(JBLabel("Default Build Jobs:"), defaultJobsSpinner, 1, false)
            .addLabeledComponent(JBLabel("Cmd Timeout (min):"),  cmdTimeoutSpinner,  1, false)

        if (showActionButtons) {
            builder
                .addSeparator()
                .addComponent(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                    add(applyButton)
                    add(resetButton)
                })
        }

        val formPanel = builder.addComponentFillVertically(JPanel(), 0).panel

        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(formPanel), BorderLayout.CENTER)
        }
    }
}
