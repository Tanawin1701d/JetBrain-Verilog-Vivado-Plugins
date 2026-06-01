package com.hdl.coterm

import com.hdl.mcp.McpStartAgreementDialog
import com.hdl.mcp.McpStartChoice
import com.hdl.mcp.VivaMcpServer
import com.hdl.vivado.PredefinedCommandLibrary
import com.hdl.vivado.TclBridgeService
import com.hdl.vivado.VivadoProcessManager
import com.hdl.vivado.VivadoSettingsState
import com.hdl.vivado.VivadoStatus
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.*

class VivaCoTermPanel(private val project: Project) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---- Output area --------------------------------------------------------
    private val outputPane = JTextPane().apply {
        isEditable = false
        background = Color(30, 30, 30)
        foreground = Color(220, 220, 220)
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        border = JBUI.Borders.empty(4)
    }
    private val outputDoc: StyledDocument = outputPane.styledDocument

    private val styleNormal = createStyle("normal", Color(220, 220, 220))
    private val styleError  = createStyle("error",  Color(255, 100, 100))
    private val styleWarn   = createStyle("warn",   Color(240, 180, 50))
    private val styleInfo   = createStyle("info",   Color(100, 180, 255))
    private val styleAi     = createStyle("ai",     Color(150, 220, 150))

    // ---- Status badge -------------------------------------------------------
    private val statusLabel = JLabel("● STOPPED").apply {
        foreground = JBColor.RED
        font = font.deriveFont(Font.BOLD, 12f)
        border = JBUI.Borders.emptyLeft(6)
    }

    // ---- MCP URL label ------------------------------------------------------
    private val mcpUrlLabel = JLabel("MCP: --").apply {
        font = font.deriveFont(11f)
        foreground = Color.GRAY
        border = JBUI.Borders.emptyLeft(8)
        toolTipText = "Click to copy MCP server URL"
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val url = "http://127.0.0.1:${VivadoSettingsState.getInstance(project).mcpPort}"
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
                appendLine("[VivaCo-Term] MCP URL copied to clipboard: $url", styleInfo)
            }
        })
    }

    // ---- Input field --------------------------------------------------------
    private val commandHistory = ArrayDeque<String>()
    private var historyIdx = -1

    private val inputField = JTextField().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        background = Color(40, 40, 40)
        foreground = Color(220, 220, 220)
        caretColor = Color(220, 220, 220)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(80, 80, 80)),
            JBUI.Borders.empty(3, 6)
        )
    }

    // ---- Toolbar buttons ----------------------------------------------------
    private val btnLaunch  = JButton("Launch Vivado")
    private val btnRestart = JButton("Restart")
    private val btnStop    = JButton("Stop")
    private val btnMcp     = JButton("Start MCP")
    private val btnClear   = JButton("Clear")
    private val btnCmd     = JButton("Run Command ▼")

    val component: JPanel = buildPanel()

    init {
        wireActions()
        subscribeToFlows()
        updateMcpLabel()
    }

    // -------------------------------------------------------------------------
    // Build UI
    // -------------------------------------------------------------------------

    private fun buildPanel(): JPanel {
        val scrollPane = JBScrollPane(outputPane).apply {
            border = null
            verticalScrollBarPolicy = JBScrollPane.VERTICAL_SCROLLBAR_ALWAYS
        }

        val inputRow = JPanel(BorderLayout(4, 0)).apply {
            background = Color(40, 40, 40)
            border = JBUI.Borders.empty(4)
            val prompt = JLabel("tcl>").apply {
                font = Font(Font.MONOSPACED, Font.BOLD, 13)
                foreground = Color(100, 200, 100)
                border = JBUI.Borders.emptyRight(4)
            }
            add(prompt, BorderLayout.WEST)
            add(inputField, BorderLayout.CENTER)
        }

        val toolbar = buildToolbar()

        val statusRow = JPanel(BorderLayout()).apply {
            background = Color(45, 45, 45)
            border = JBUI.Borders.empty(2, 4)
            add(statusLabel, BorderLayout.WEST)
            add(mcpUrlLabel, BorderLayout.CENTER)
        }

        return JPanel(BorderLayout()).apply {
            background = Color(30, 30, 30)
            add(toolbar,   BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                background = Color(40, 40, 40)
                add(statusRow, BorderLayout.NORTH)
                add(inputRow,  BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
        }
    }

    private fun buildToolbar(): JToolBar {
        return JToolBar().apply {
            isFloatable = false
            background = Color(45, 45, 45)
            border = JBUI.Borders.empty(2, 4)
            add(btnLaunch)
            add(btnRestart)
            add(btnStop)
            addSeparator()
            add(btnMcp)
            addSeparator()
            add(btnClear)
            addSeparator()
            add(btnCmd)
        }
    }

    // -------------------------------------------------------------------------
    // Actions
    // -------------------------------------------------------------------------

    private fun wireActions() {
        btnLaunch.addActionListener { showLaunchDialog() }
        btnRestart.addActionListener {
            VivadoProcessManager.getInstance(project).restartVivado()
        }
        btnStop.addActionListener {
            VivadoProcessManager.getInstance(project).shutdownVivado()
        }
        btnMcp.addActionListener { toggleMcpServer() }
        btnClear.addActionListener {
            try { outputDoc.remove(0, outputDoc.length) } catch (_: Exception) {}
        }
        btnCmd.addActionListener { e -> showCommandPalette(e.source as JComponent) }

        inputField.addActionListener { sendInputCommand() }

        // Up/down arrow for command history
        inputField.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "history-up")
        inputField.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "history-down")
        inputField.actionMap.put("history-up", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (commandHistory.isEmpty()) return
                historyIdx = (historyIdx + 1).coerceAtMost(commandHistory.size - 1)
                inputField.text = commandHistory[historyIdx]
            }
        })
        inputField.actionMap.put("history-down", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (historyIdx <= 0) { historyIdx = -1; inputField.text = ""; return }
                historyIdx--
                inputField.text = commandHistory[historyIdx]
            }
        })
    }

    private fun sendInputCommand() {
        val cmd = inputField.text.trim()
        if (cmd.isEmpty()) return
        if (commandHistory.isEmpty() || commandHistory.first() != cmd) {
            commandHistory.addFirst(cmd)
            if (commandHistory.size > 100) commandHistory.removeLast()
        }
        historyIdx = -1
        inputField.text = ""

        appendLine("tcl> $cmd", styleInfo)

        val bridge = TclBridgeService.getInstance(project)
        if (!bridge.isConnected()) {
            appendLine("[VivaCo-Term] Not connected. Launch Vivado first.", styleError)
            return
        }
        scope.launch {
            try {
                bridge.sendCommand(cmd).await()
            } catch (_: Exception) {
                // Error lines are already shown in the output area via TclBridgeService.publishOutput
            }
        }
    }

    private fun showLaunchDialog() {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            gridx = 0; gridy = 0
        }

        val xprField = JTextField(40)
        val xprBrowse = JButton("Browse...")
        val tclField  = JTextField(40)
        val tclBrowse = JButton("Browse...")

        xprBrowse.addActionListener {
            val fc = JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Vivado Project", "xpr") }
            if (fc.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) xprField.text = fc.selectedFile.absolutePath
        }
        tclBrowse.addActionListener {
            val fc = JFileChooser().apply { fileFilter = javax.swing.filechooser.FileNameExtensionFilter("TCL Script", "tcl") }
            if (fc.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) tclField.text = fc.selectedFile.absolutePath
        }

        fun row(label: String, field: JTextField, browse: JButton, row: Int) {
            gbc.gridy = row; gbc.gridx = 0; gbc.gridwidth = 1; panel.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.gridwidth = 2; panel.add(field, gbc)
            gbc.gridx = 3; gbc.gridwidth = 1; panel.add(browse, gbc)
        }
        row("XPR file (optional):", xprField, xprBrowse, 0)
        row("Initial TCL script (optional):", tclField, tclBrowse, 1)

        val result = JOptionPane.showConfirmDialog(
            null, panel, "Launch Vivado Console",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (result != JOptionPane.OK_OPTION) return

        val xpr = xprField.text.trim().takeIf { it.isNotEmpty() }
        val tcl = tclField.text.trim().takeIf { it.isNotEmpty() }
            ?.let { "source {$it}" }

        VivadoProcessManager.getInstance(project).launchVivado(xprPath = xpr, initialTcl = tcl)
    }

    private fun showCommandPalette(anchor: JComponent) {
        val menu = JPopupMenu()
        for (cmd in PredefinedCommandLibrary.commands) {
            menu.add(JMenuItem("${cmd.name}  (${cmd.id})").apply {
                addActionListener { showCommandDialog(cmd) }
            })
        }
        menu.show(anchor, 0, anchor.height)
    }

    private fun showCommandDialog(cmd: com.hdl.vivado.PredefinedCommand) {
        if (cmd.parameters.isEmpty()) {
            runCommand(cmd, emptyMap())
            return
        }
        val panel = JPanel(GridLayout(cmd.parameters.size, 2, 4, 4))
        panel.border = JBUI.Borders.empty(8)
        val fields = mutableMapOf<String, JTextField>()

        val settings = VivadoSettingsState.getInstance(project)
        for (param in cmd.parameters) {
            panel.add(JLabel("${param.name}${if (param.required) " *" else ""}:").apply {
                toolTipText = param.description
            })
            val field = JTextField(
                (param.default ?: when (param.name) {
                    "jobs" -> settings.defaultJobs
                    else -> ""
                }).toString()
            )
            fields[param.name] = field
            panel.add(field)
        }

        val result = JOptionPane.showConfirmDialog(
            null, panel, cmd.name, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (result != JOptionPane.OK_OPTION) return

        val args: Map<String, Any> = fields.mapValues { (_, v) -> v.text.trim() }
        runCommand(cmd, args)
    }

    private fun runCommand(cmd: com.hdl.vivado.PredefinedCommand, args: Map<String, Any>) {
        val bridge = TclBridgeService.getInstance(project)
        val tcl = try {
            cmd.tclGenerator(args)
        } catch (e: Exception) {
            appendLine("[VivaCo-Term] Parameter error: ${e.message}", styleError)
            return
        }
        appendLine("[AI] Executing: ${cmd.name}", styleAi)
        scope.launch {
            try {
                bridge.sendCommand(tcl).await()
                SwingUtilities.invokeLater {
                    Messages.showInfoMessage(project, "${cmd.name} completed.", "Vivado Console")
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    Messages.showErrorDialog(project, "${cmd.name} failed: ${e.message}", "Vivado Console")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Flow subscriptions
    // -------------------------------------------------------------------------

    private fun subscribeToFlows() {
        val bridge = TclBridgeService.getInstance(project)
        val manager = VivadoProcessManager.getInstance(project)

        scope.launch {
            bridge.outputFlow.collect { line ->
                SwingUtilities.invokeLater { appendLine(line, pickStyle(line)) }
            }
        }

        scope.launch {
            manager.statusFlow.collectLatest { status ->
                SwingUtilities.invokeLater { updateStatus(status) }
            }
        }
    }

    // Start (with an agreement dialog) or stop the MCP server. Independent of Vivado status.
    private fun toggleMcpServer() {
        val server = VivaMcpServer.getInstance(project)
        if (server.isRunning) {
            server.stop()
            appendLine("[VivaCo-Term] MCP server stopped.", styleInfo)
        } else {
            val dialog = McpStartAgreementDialog(project)
            dialog.showAndGet()   // exit code is reflected in dialog.choice
            when (dialog.choice) {
                McpStartChoice.CANCEL -> return
                McpStartChoice.ENABLE_RAW_TCL -> server.start(rawTclEnabled = true)
                McpStartChoice.ENABLE_SAFE    -> server.start(rawTclEnabled = false)
            }
            val port = VivadoSettingsState.getInstance(project).mcpPort
            val mode = if (server.rawTclAllowed) "raw Tcl ENABLED" else "raw Tcl disabled"
            appendLine("[VivaCo-Term] MCP server started on http://127.0.0.1:$port ($mode).", styleInfo)
        }
        updateMcpLabel()
    }

    // Reflect the live MCP server state in the toolbar button and status label.
    private fun updateMcpLabel() {
        val server = VivaMcpServer.getInstance(project)
        if (server.isRunning) {
            val port = VivadoSettingsState.getInstance(project).mcpPort
            val raw = if (server.rawTclAllowed) "ON" else "OFF"
            mcpUrlLabel.text = "MCP: http://127.0.0.1:$port  [raw Tcl $raw]"
            mcpUrlLabel.foreground = Color(120, 190, 120)
            btnMcp.text = "Stop MCP"
        } else {
            mcpUrlLabel.text = "MCP: stopped"
            mcpUrlLabel.foreground = Color.GRAY
            btnMcp.text = "Start MCP"
        }
    }

    private fun updateStatus(status: VivadoStatus) {
        val (text, color) = when (status) {
            VivadoStatus.STOPPED  -> "● STOPPED"  to JBColor.RED
            VivadoStatus.STARTING -> "● STARTING" to Color(240, 180, 50)
            VivadoStatus.RUNNING  -> "● RUNNING"  to Color(80, 200, 80)
            VivadoStatus.CRASHED  -> "● CRASHED"  to JBColor.RED
        }
        statusLabel.text = text
        statusLabel.foreground = color

        updateMcpLabel()

        btnLaunch.isEnabled  = status == VivadoStatus.STOPPED || status == VivadoStatus.CRASHED
        btnRestart.isEnabled = status == VivadoStatus.RUNNING
        btnStop.isEnabled    = status == VivadoStatus.RUNNING
        inputField.isEnabled = status == VivadoStatus.RUNNING
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

    private fun appendLine(text: String, style: AttributeSet) {
        try {
            val line = if (text.endsWith("\n")) text else "$text\n"
            outputDoc.insertString(outputDoc.length, line, style)
            outputPane.caretPosition = outputDoc.length
        } catch (_: BadLocationException) {}
    }

    private fun pickStyle(line: String): AttributeSet = when {
        line.startsWith("[AI]")                       -> styleAi
        line.startsWith("[VivaCo-Term]")              -> styleInfo
        line.contains("ERROR:", ignoreCase = true) ||
            line.contains("CRITICAL WARNING:", ignoreCase = true) -> styleError
        line.contains("WARNING:", ignoreCase = true)  -> styleWarn
        else                                          -> styleNormal
    }

    private fun createStyle(name: String, color: Color): Style {
        val style = outputPane.addStyle(name, null)
        StyleConstants.setForeground(style, color)
        return style
    }

    override fun dispose() {
        scope.cancel()
    }
}
