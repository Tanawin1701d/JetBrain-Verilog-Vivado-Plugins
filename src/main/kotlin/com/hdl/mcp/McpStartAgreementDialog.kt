package com.hdl.mcp

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.UIManager

/** Outcome of the MCP start agreement dialog. */
enum class McpStartChoice { ENABLE_RAW_TCL, ENABLE_SAFE, CANCEL }

/**
 * User-agreement dialog shown every time the MCP server is started. Warns that an AI
 * assistant will be able to drive Vivado, and lets the user pick whether the arbitrary-Tcl
 * tools (runTclRaw / runTclScript) are permitted for the session.
 */
class McpStartAgreementDialog(project: Project) : DialogWrapper(project, true) {

    /** Set by the chosen action; defaults to CANCEL (also used when the dialog is dismissed). */
    var choice: McpStartChoice = McpStartChoice.CANCEL
        private set

    /** Use IntelliJ's HTML editor kit so dark/light theme colours are applied automatically. */
    private val contentPane = JEditorPane().apply {
        editorKit = UIUtil.getHTMLEditorKit()
        isEditable = false
        isOpaque = true
        background = UIManager.getColor("Panel.background") ?: background
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = UIUtil.getLabelFont()
        text = AGREEMENT_HTML
        caretPosition = 0
    }

    init {
        title = "Start MCP Server — Safety Agreement"
        isModal = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(contentPane).apply {
            preferredSize = Dimension(560, 320)
            border = BorderFactory.createEtchedBorder()
            viewport.background = UIManager.getColor("Panel.background") ?: viewport.background
        }
        return JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(scrollPane, BorderLayout.CENTER)
            preferredSize = Dimension(600, 380)
        }
    }

    override fun createActions(): Array<Action> {
        val safeAction = object : AbstractAction("Enable (No Raw Tcl)") {
            override fun actionPerformed(e: ActionEvent) {
                choice = McpStartChoice.ENABLE_SAFE
                close(OK_EXIT_CODE)
            }
        }.apply { putValue(DEFAULT_ACTION, true) }   // safest option is the default button

        val rawAction = object : AbstractAction("Enable with Raw Tcl") {
            override fun actionPerformed(e: ActionEvent) {
                choice = McpStartChoice.ENABLE_RAW_TCL
                close(OK_EXIT_CODE)
            }
        }

        // Built-in cancel action closes with CANCEL_EXIT_CODE and leaves choice == CANCEL.
        return arrayOf(safeAction, rawAction, cancelAction)
    }

    private companion object {
        val AGREEMENT_HTML = """
            <html><body style='margin:4px;'>
            <h2>MCP Server — Safety Agreement</h2>
            <p>Starting the MCP server lets an external <b>AI assistant</b> connect to this IDE and
            drive your live Vivado session by issuing Tcl commands <i>on your behalf</i>.</p>

            <p style='color:#d9534f;'><b>Warning:</b> AI assistants can make mistakes. Commands run with
            your user privileges and may create, modify, or <b>delete</b> files, launch processes, or
            otherwise change your system. Keep backups and review what the assistant does.</p>

            <p><b>Raw Tcl access.</b> The tools <code>runTclRaw</code> and <code>runTclScript</code> let
            the assistant execute <b>arbitrary Tcl</b> — including unrestricted file-system and process
            access. Enable this only if you trust the assistant and accept the risk. When disabled, these
            two tools are blocked and hidden from the assistant; all other tools (create project, run
            synthesis, generate bitstream, etc.) remain available.</p>

            <p>Choose one:</p>
            <ul>
              <li><b>Enable (No Raw Tcl)</b> — start the server with raw-Tcl tools blocked (recommended).</li>
              <li><b>Enable with Raw Tcl</b> — start the server and allow arbitrary Tcl execution.</li>
              <li><b>Cancel</b> — do not start the server.</li>
            </ul>

            <p style='color:gray;'>By choosing an "Enable" option you acknowledge these risks and accept
            responsibility for commands executed through the MCP server. This choice applies to the current
            session only.</p>
            </body></html>
        """.trimIndent()
    }
}
