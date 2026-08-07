package com.hdl.mcp

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.UIManager

/**
 * Shown once, right after the MCP server starts, so the user knows what to do next:
 * how to point an AI assistant at the server they just enabled.
 *
 * Client-specific syntax is kept to the two shapes that are stable — a Claude Code
 * command and the generic MCP JSON object most clients accept — with the server URL
 * front and centre for everything else. Anything more specific goes stale faster
 * than the plugin ships.
 */
class McpConnectionInfoDialog(
    project: Project,
    private val url: String,
    private val rawTclEnabled: Boolean
) : DialogWrapper(project, true) {

    private val contentPane = JEditorPane().apply {
        editorKit = UIUtil.getHTMLEditorKit()
        isEditable = false
        isOpaque = true
        background = UIManager.getColor("Panel.background") ?: background
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = UIUtil.getLabelFont()
        text = buildHtml()
        caretPosition = 0
    }

    init {
        title = "MCP Server Started — Connect Your AI Assistant"
        isModal = true
        init()
    }

    override fun createCenterPanel(): JComponent {
        val scrollPane = JBScrollPane(contentPane).apply {
            preferredSize = Dimension(600, 400)
            border = BorderFactory.createEtchedBorder()
            viewport.background = UIManager.getColor("Panel.background") ?: viewport.background
        }
        return JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(scrollPane, BorderLayout.CENTER)
            preferredSize = Dimension(640, 460)
        }
    }

    override fun createActions(): Array<Action> {
        // Copy actions deliberately do not close the dialog — the user usually wants
        // to copy one thing, switch to their client, and come back for the other.
        val copyUrl = object : AbstractAction("Copy URL") {
            override fun actionPerformed(e: ActionEvent) {
                CopyPasteManager.getInstance().setContents(StringSelection(url))
            }
        }
        val copyJson = object : AbstractAction("Copy JSON Config") {
            override fun actionPerformed(e: ActionEvent) {
                CopyPasteManager.getInstance().setContents(StringSelection(jsonConfig()))
            }
        }
        return arrayOf(copyUrl, copyJson, okAction)
    }

    /** The generic MCP client config object — the shape most clients accept. */
    private fun jsonConfig(): String = """
        {
          "mcpServers": {
            "viva-coterm": {
              "type": "http",
              "url": "$url"
            }
          }
        }
    """.trimIndent()

    private fun buildHtml(): String {
        val rawTclNote = if (rawTclEnabled) {
            "<p style='color:#d9534f;'><b>Raw Tcl is ENABLED</b> for this session — the assistant " +
                "can execute arbitrary Tcl, including file-system and process access. All 27 tools " +
                "are visible to it.</p>"
        } else {
            "<p style='color:#4a8f4a;'><b>Raw Tcl is disabled</b> for this session. The assistant " +
                "sees 25 tools; <code>runTclRaw</code> and <code>runTclScript</code> are hidden and " +
                "will be refused if called.</p>"
        }

        return """
            <html><body style='margin:4px;'>
            <h2>The MCP server is running</h2>

            <p>Point your AI assistant at this address:</p>
            <p style='margin-left:12px;'><code style='font-size:14px;'><b>$url</b></code></p>
            <p style='color:gray;'>Loopback only — reachable from this machine.</p>

            $rawTclNote

            <h3>Claude Code</h3>
            <p>Run this in a terminal, in the project you want to use it from:</p>
            <p style='margin-left:12px;'><code>claude mcp add --transport http viva-coterm $url</code></p>
            <p>Or commit it to the project by adding the JSON below to <code>.mcp.json</code>
            in the project root.</p>

            <h3>Any other MCP client</h3>
            <p>Add an <b>HTTP</b> (streamable-HTTP) server pointing at the URL above. Most clients
            take the same JSON object — use <b>Copy JSON Config</b> below and paste it into:</p>
            <ul>
              <li><b>Claude Code</b> — <code>.mcp.json</code> in the project root</li>
              <li><b>Claude Desktop</b> — <code>claude_desktop_config.json</code></li>
              <li><b>Codex CLI</b> — <code>~/.codex/config.toml</code>, as a
                  <code>[mcp_servers.viva-coterm]</code> table with <code>url = "$url"</code>
                  <span style='color:gray;'>(key names differ between Codex releases — check
                  <code>codex --help</code> if it is not picked up)</span></li>
              <li><b>Junie / AI Assistant</b> — Settings &rarr; Tools &rarr; MCP, add an HTTP server</li>
            </ul>

            <h3>Before the assistant can do anything</h3>
            <ul>
              <li><b>Vivado must be running.</b> Every tool is refused while the status badge reads
                  anything other than <b>&#9679; RUNNING</b>. Use <b>Launch Vivado</b> in this panel.</li>
              <li>Ask the assistant to list its tools first — it should see the Vivado commands
                  (<code>createProject</code>, <code>runSynthesis</code>,
                  <code>generateBitstream</code>, &hellip;).</li>
              <li>Everything the assistant runs is echoed into this console as an
                  <code>[AI]</code> line <i>before</i> it executes, so you can watch it work.</li>
            </ul>

            <p style='color:gray;'>This choice applies to the current session only. Stopping the
            server or closing the project revokes access; restarting it asks for the safety
            agreement again.</p>
            </body></html>
        """.trimIndent()
    }
}
