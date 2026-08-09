package com.hdl.coterm

import com.hdl.mcp.McpConnectionInfoDialog
import com.hdl.mcp.McpStartAgreementDialog
import com.hdl.mcp.McpStartChoice
import com.hdl.mcp.VivaMcpServer
import com.hdl.vivado.PredefinedCommandLibrary
import com.hdl.vivado.TclBridgeService
import com.hdl.vivado.VivadoProcessManager
import com.hdl.vivado.VivadoSettingsState
import com.hdl.vivado.VivadoStatus
import com.intellij.icons.AllIcons
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
import java.io.File
import java.time.LocalDateTime
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
                notice("[VivaCo-Term] MCP URL copied to clipboard: $url")
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

    // ---- Recording ----------------------------------------------------------
    private val recordDot   = RecordDotIcon()
    private val btnRecord   = JButton("Record", recordDot)
    private var blinkTimer: Timer? = null
    private var blinkOn     = false

    private val recordLabel = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = REC_BRIGHT
        border = JBUI.Borders.emptyRight(6)
        isVisible = false
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
        // A recording started before this tool window was reopened is still running — the
        // recorder lives on the project, not on the panel — so pick its state up rather than
        // showing an idle button over a live recording.
        updateRecordUi()
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
            add(recordLabel, BorderLayout.EAST)
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
            add(btnRecord)
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
        btnRecord.addActionListener { toggleRecording() }
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
            notice("[VivaCo-Term] ERROR: Not connected. Launch Vivado first.")
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
        // The palette above is the curated shortlist; the browser is the whole UG835
        // reference, so the person watching can reach everything the AI can.
        menu.addSeparator()
        menu.add(JMenuItem("Browse Vivado Commands…").apply {
            addActionListener { showCommandBrowser() }
        })
        menu.show(anchor, 0, anchor.height)
    }

    // Put the chosen command name in the input field rather than running it: the arguments
    // are the part that matters, and they should be typed deliberately.
    private fun showCommandBrowser() {
        val dialog = VivadoCommandBrowserDialog(project)
        if (!dialog.showAndGet()) return
        val command = dialog.selectedCommand ?: return
        inputField.text = "$command "
        inputField.requestFocusInWindow()
        inputField.caretPosition = inputField.text.length
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
            notice("[VivaCo-Term] ERROR: bad parameter — ${e.message}")
            return
        }
        notice("[AI] Executing: ${cmd.name}")
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
    // Session recording
    // -------------------------------------------------------------------------

    private fun toggleRecording() {
        val recorder = CoTermRecorder.getInstance(project)
        if (recorder.isRecording) {
            val stats = recorder.stats
            val file = recorder.stop()
            appendLine(
                "[VivaCo-Term] Recording stopped — ${stats.total} records " +
                    "(${stats.sent} sent, ${stats.received} received) written to ${file?.absolutePath}",
                styleInfo
            )
        } else {
            val target = askRecordTarget() ?: return
            try {
                val file = recorder.start(target.file, target.append)
                // Remember the folder so the next recording defaults next to this one.
                VivadoSettingsState.getInstance(project).recordLogDir = file.absoluteFile.parent.orEmpty()
                notice("[VivaCo-Term] Recording to ${file.absolutePath}")
            } catch (e: Exception) {
                appendLine("[VivaCo-Term] Cannot record to ${target.file.absolutePath}: ${e.message}", styleError)
                Messages.showErrorDialog(
                    project,
                    "Could not open the log file:\n${target.file.absolutePath}\n\n${e.message}",
                    "Start Recording"
                )
            }
        }
        updateRecordUi()
    }

    private data class RecordTarget(val file: File, val append: Boolean)

    /**
     * Ask where the log goes. Defaults to the folder used last (or set in HDL Settings), with a
     * timestamped file name, so repeated recordings never silently land on top of each other.
     */
    private fun askRecordTarget(): RecordTarget? {
        val settings = VivadoSettingsState.getInstance(project)
        val baseDir = settings.recordLogDir.trim()
            .ifEmpty { project.basePath ?: System.getProperty("user.home") }
        val suggested = File(baseDir, SessionLogFormat.defaultFileName(LocalDateTime.now()))

        val pathField = JTextField(suggested.absolutePath, 44)
        val browse    = JButton("Browse...")
        val appendBox = JCheckBox("Append if the file already exists", false)

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }
        gbc.gridy = 0; gbc.gridx = 0; panel.add(JLabel("Save log to:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; panel.add(pathField, gbc)
        gbc.gridx = 2; gbc.weightx = 0.0; panel.add(browse, gbc)
        gbc.gridy = 1; gbc.gridx = 1; gbc.gridwidth = 2; panel.add(appendBox, gbc)

        browse.addActionListener {
            val fc = JFileChooser().apply {
                dialogTitle = "Save Console Log"
                selectedFile = File(pathField.text.trim().ifEmpty { suggested.absolutePath })
                fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Log file", "log", "txt")
            }
            if (fc.showSaveDialog(panel) == JFileChooser.APPROVE_OPTION) {
                pathField.text = fc.selectedFile.absolutePath
            }
        }

        val result = JOptionPane.showConfirmDialog(
            null, panel, "Record Console Session",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
        )
        if (result != JOptionPane.OK_OPTION) return null

        val path = pathField.text.trim()
        if (path.isEmpty()) {
            Messages.showErrorDialog(project, "Choose a file to record into.", "Start Recording")
            return null
        }

        val file = File(path).absoluteFile
        if (file.isDirectory) {
            Messages.showErrorDialog(project, "${file.absolutePath} is a folder, not a file.", "Start Recording")
            return null
        }
        // An existing log is somebody's earlier session — never truncate it without asking.
        if (file.exists() && !appendBox.isSelected) {
            val answer = Messages.showYesNoDialog(
                project,
                "${file.absolutePath}\nalready exists. Overwrite it?",
                "Start Recording",
                "Overwrite", "Cancel",
                Messages.getWarningIcon()
            )
            if (answer != Messages.YES) return null
        }
        return RecordTarget(file, appendBox.isSelected)
    }

    /** Repaint the button, the blinking dot and the status label from the recorder's state. */
    private fun updateRecordUi() {
        val recorder = CoTermRecorder.getInstance(project)
        if (recorder.isRecording) {
            val file = recorder.targetFile
            btnRecord.text = "Stop Recording"
            btnRecord.toolTipText = "Recording this session to ${file?.absolutePath} — click to stop"
            recordLabel.isVisible = true
            startBlinking()
        } else {
            stopBlinking()
            btnRecord.text = "Record"
            btnRecord.toolTipText = "Record everything sent to and received from Vivado to a file"
            recordDot.color = REC_IDLE
            btnRecord.repaint()
            recordLabel.isVisible = false
        }
    }

    private fun startBlinking() {
        if (blinkTimer?.isRunning == true) return
        blinkOn = true
        blinkTimer = Timer(BLINK_INTERVAL_MS) { onBlinkTick() }.also { it.start() }
        onBlinkTick()
    }

    // One tick drives the dot, the status label and the live record count together, so the two
    // indicators cannot fall out of step with each other.
    private fun onBlinkTick() {
        val lit = blinkOn
        blinkOn = !blinkOn

        recordDot.color = if (lit) REC_BRIGHT else REC_DARK
        btnRecord.repaint()

        val recorder = CoTermRecorder.getInstance(project)
        val file = recorder.targetFile
        if (file != null) {
            recordLabel.text = "● REC ${file.name} (${recorder.stats.total})"
            recordLabel.foreground = if (lit) REC_BRIGHT else REC_DARK
        }
    }

    private fun stopBlinking() {
        blinkTimer?.stop()
        blinkTimer = null
        blinkOn = false
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
            notice("[VivaCo-Term] MCP server stopped.")
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
            notice("[VivaCo-Term] MCP server started on http://127.0.0.1:$port ($mode).")
            // The token is a live credential and session logs get shared, so it is shown in the
            // console only — the log gets a note that one was issued, not the value.
            appendLine("[VivaCo-Term] MCP token: ${server.sessionToken}", styleInfo)
            notice("[VivaCo-Term] MCP session token issued (kept out of the log).")

            // The server is listening, but the user still has to point a client at it —
            // show how, with the URL and a copyable config.
            McpConnectionInfoDialog(
                project, server.serverUrl, server.sessionToken.orEmpty(), server.rawTclAllowed
            ).show()

            // The server is up, but tools cannot run until a live Vivado session exists.
            // If Vivado isn't running yet, nudge the user to launch it now.
            promptLaunchVivadoIfNeeded()
        }
        updateMcpLabel()
    }

    // When the MCP server has just started but Vivado is not running, warn the user that
    // tools will fail until Vivado is up and offer to open the Launch dialog right away.
    private fun promptLaunchVivadoIfNeeded() {
        val status = VivadoProcessManager.getInstance(project).statusFlow.value
        if (status == VivadoStatus.RUNNING) return

        notice("[VivaCo-Term] WARNING: Vivado is not running — MCP tools will fail until it is launched.")
        val answer = Messages.showYesNoDialog(
            project,
            "The MCP server is started, but Vivado is not running yet.\n" +
                "MCP tools cannot execute until a Vivado session is live.\n\n" +
                "Launch Vivado now?",
            "Vivado Not Running",
            "Launch Vivado", "Later",
            Messages.getWarningIcon()
        )
        if (answer == Messages.YES) showLaunchDialog()
    }

    // Reflect the live MCP server state in the toolbar button and status label.
    // The icon carries the state (blocked vs live) and the text carries the action.
    private fun updateMcpLabel() {
        val server = VivaMcpServer.getInstance(project)
        if (server.isRunning) {
            val port = VivadoSettingsState.getInstance(project).mcpPort
            val raw = if (server.rawTclAllowed) "ON" else "OFF"
            mcpUrlLabel.text = "MCP: http://127.0.0.1:$port  [raw Tcl $raw]"
            mcpUrlLabel.foreground = Color(120, 190, 120)
            btnMcp.text = "Stop MCP"
            btnMcp.icon = AllIcons.General.InspectionsOK
            btnMcp.toolTipText = "MCP server is running on http://127.0.0.1:$port — click to stop it"
        } else {
            mcpUrlLabel.text = "MCP: stopped"
            mcpUrlLabel.foreground = Color.GRAY
            btnMcp.text = "Start MCP"
            btnMcp.icon = AllIcons.General.InspectionsTrafficOff
            btnMcp.toolTipText = "MCP server is not started — AI assistants cannot reach this project"
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

    /**
     * Print a console notice through the bridge instead of straight into the text pane.
     *
     * The panel renders it when it comes back on the output flow — once — and because it made
     * the round trip it also lands in a running session log. Anything written with [appendLine]
     * instead is console-only, on purpose: the `tcl>` echo (already logged as a sent record),
     * the MCP token, and messages about the recording itself.
     */
    private fun notice(text: String) {
        TclBridgeService.getInstance(project).publishInfo(text)
    }

    private fun appendLine(text: String, style: AttributeSet) {
        try {
            val line = if (text.endsWith("\n")) text else "$text\n"
            outputDoc.insertString(outputDoc.length, line, style)
            outputPane.caretPosition = outputDoc.length
        } catch (_: BadLocationException) {}
    }

    // Severity is checked before the source prefix: a "[VivaCo-Term] ERROR: ..." notice has to
    // read as an error, not as ordinary plugin chatter.
    private fun pickStyle(line: String): AttributeSet = when {
        line.contains("ERROR:", ignoreCase = true) ||
            line.contains("CRITICAL WARNING:", ignoreCase = true) -> styleError
        line.contains("WARNING:", ignoreCase = true)  -> styleWarn
        line.startsWith("[AI]")                       -> styleAi
        line.startsWith("[VivaCo-Term]")              -> styleInfo
        else                                          -> styleNormal
    }

    private fun createStyle(name: String, color: Color): Style {
        val style = outputPane.addStyle(name, null)
        StyleConstants.setForeground(style, color)
        return style
    }

    override fun dispose() {
        // Only the blink stops here. The recording itself belongs to the project, so closing the
        // tool window must not cut a session log short.
        stopBlinking()
        scope.cancel()
    }

    /**
     * The record indicator: a filled circle whose colour the blink timer swaps.
     *
     * A repainted icon rather than two swapped image assets, so the dot follows the button's
     * font size and stays crisp on a HiDPI screen.
     */
    private class RecordDotIcon(private val size: Int = JBUI.scale(10)) : Icon {

        var color: Color = REC_IDLE

        override fun getIconWidth()  = size
        override fun getIconHeight() = size

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = color
                g2.fillOval(x, y, size, size)
            } finally {
                g2.dispose()
            }
        }
    }

    companion object {
        private const val BLINK_INTERVAL_MS = 500

        private val REC_BRIGHT = Color(235, 70, 70)    // dot lit — recording
        private val REC_DARK   = Color(110, 45, 45)    // dot dimmed — the off half of the blink
        private val REC_IDLE   = Color(120, 90, 90)    // not recording
    }
}
