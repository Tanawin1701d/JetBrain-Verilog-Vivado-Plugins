package com.hdl.settings

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

class HdlTutorialDialog(project: Project) : DialogWrapper(project, false) {

    // ------------------------------------------------------------------
    // Tutorial sections — HTML without hardcoded colours so Darcula/Light
    // both render correctly via UIUtil.getHTMLEditorKit().
    // ------------------------------------------------------------------
    private val sections: List<Pair<String, String>> = listOf(

        "Welcome to the HDL Plugin" to """
            <html><body>
            <h2>Welcome to the JetBrains HDL Plugin</h2>
            <p>This plugin provides hardware description language (HDL) support for
            JetBrains IDEs, integrating Verilog/SystemVerilog development with
            Xilinx Vivado.</p>

            <h3>Available Features</h3>
            <ul>
              <li><b>Syntax Highlighting</b> — Verilog, SystemVerilog, and Tcl/XDC files</li>
              <li><b>Auto-completion</b> — Verilog and Tcl keywords, system tasks</li>
              <li><b>Tcl Code Folding</b> — Collapse proc bodies and brace blocks</li>
              <li><b>Real-time Linting</b> — Icarus Verilog or Verilator; errors shown inline</li>
              <li><b>Top Folder</b> — Define the root of your design for multi-file linting</li>
              <li><b>Top File</b> — Mark the elaboration entry-point module</li>
              <li><b>Vivado Integration</b> — Build projects, run TCL scripts, IP composition</li>
              <li><b>Linter Debugger</b> — Bottom panel showing raw linter output and results</li>
            </ul>
            <p>Use <b>Next &rsaquo;</b> to learn how to configure each feature.</p>
            </body></html>
        """.trimIndent(),

        "Setting Up the Linter" to """
            <html><body>
            <h2>Setting Up the Linter</h2>
            <p>The plugin supports two linters: <b>Icarus Verilog (iverilog)</b> and
            <b>Verilator</b>. Both handle <code>.v</code>, <code>.vh</code>,
            <code>.sv</code>, and <code>.svh</code> files.</p>

            <h3>Steps</h3>
            <ol>
              <li>Open the <b>HDL Settings</b> panel (right side) or go to
                  <b>Settings &rarr; Tools &rarr; HDL Settings</b></li>
              <li>Under <b>Verilog Linter</b>, choose <i>IVERILOG</i> or <i>VERILATOR</i></li>
              <li>Set the full path to the binary (e.g. <code>/usr/bin/iverilog</code>)</li>
              <li>Click <b>Test</b> — the plugin checks the binary exists, is executable,
                  and identifies itself correctly by inspecting its version output</li>
              <li>Click <b>Apply</b> to save</li>
            </ol>

            <h3>Installing the Tools</h3>
            <ul>
              <li><b>iverilog</b>: <code>sudo apt install iverilog</code></li>
              <li><b>Verilator</b>: <code>sudo apt install verilator</code></li>
            </ul>

            <p>The linter runs automatically when you edit a Verilog/SV file.
            Errors and warnings appear as red/yellow underlines; hover to read the message.</p>
            <p><i>Tip: open the <b>Verilog Linter Debugger</b> panel (bottom) to see
            the exact command and raw output for every lint run.</i></p>
            </body></html>
        """.trimIndent(),

        "Setting the Top Folder" to """
            <html><body>
            <h2>Setting the Top Folder</h2>
            <p>Without a Top Folder the linter analyses only the file you are currently
            editing in isolation. Setting a Top Folder gives the linter the full picture
            of your design by passing every <code>.v/.sv</code> file in that folder
            (recursively) to the tool.</p>

            <h3>How to Set</h3>
            <p><b>Option A — Right-click (recommended):</b></p>
            <ol>
              <li>In the <b>Project</b> view, right-click the root folder of your HDL design</li>
              <li>Select <b>"Set as Verilog Top Folder"</b></li>
              <li>The folder gets a special gold folder icon and tooltip
                  <i>"Verilog top folder"</i></li>
            </ol>
            <p><b>Option B — Settings panel:</b><br>
            Type or browse for the path in the <b>Top Folder</b> field, then click
            <b>Apply</b>.</p>
            <p>Both methods immediately synchronize: the project view icon updates and
            the settings panel refreshes to show the current path.</p>

            <h3>Clearing the Top Folder</h3>
            <p>Clear the Top Folder field and click Apply. The Top File is automatically
            cleared as well, since it must always reside inside the Top Folder.</p>
            </body></html>
        """.trimIndent(),

        "Setting the Top File" to """
            <html><body>
            <h2>Setting the Top File</h2>
            <p>The Top File identifies the <i>elaboration entry-point</i> of your design —
            the module the linter should treat as the top-level entity. This maps to:</p>
            <ul>
              <li><b>iverilog:</b> the <code>-s &lt;module&gt;</code> flag</li>
              <li><b>Verilator:</b> the <code>--top-module &lt;module&gt;</code> flag</li>
            </ul>
            <p>The plugin automatically extracts the first <code>module</code> declaration
            from the selected file. The linter still works fine even without a Top File.</p>

            <h3>Requirements</h3>
            <ul>
              <li>A <b>Top Folder must be set first</b></li>
              <li>The file must be <b>inside the Top Folder</b> (any depth)</li>
              <li>Must be a Verilog/SV file: <code>.v / .vh / .sv / .svh</code></li>
            </ul>

            <h3>How to Set</h3>
            <ol>
              <li>In the <b>Project</b> view, right-click the top-level module file</li>
              <li>Select <b>"Set as Verilog Top File"</b></li>
              <li>The file gets a star icon and tooltip <i>"Verilog top file"</i></li>
            </ol>

            <h3>Clearing</h3>
            <p>Clear the Top File field in the settings panel and click Apply, or clear
            the Top Folder (which clears the Top File automatically).</p>
            </body></html>
        """.trimIndent(),

        "Vivado Integration" to """
            <html><body>
            <h2>Vivado Integration</h2>
            <p>Right-click any folder or Tcl file in the Project view to access the
            <b>Vivado</b> submenu.</p>

            <h3>Actions</h3>
            <ul>
              <li><b>Build Project</b> — Collects all HDL files and creates a Vivado project
                  with synthesis-lint (<code>synth_design -lint</code>). Opens Vivado GUI.</li>
              <li><b>IP Composer</b> — Packages the folder as a Vivado IP core and places it
                  in the configured IP Repository directory.</li>
              <li><b>Run Tcl Script</b> — Opens the selected <code>.tcl</code> or
                  <code>.xdc</code> file in Vivado GUI.
                  Keyboard shortcut: <b>Ctrl+Alt+R</b></li>
              <li><b>Open Project</b> — Opens an existing <code>.xpr</code> project.</li>
            </ul>

            <h3>Configuration (HDL Settings)</h3>
            <ul>
              <li><b>Executable Path</b> — Full path to the <code>vivado</code> binary
                  (e.g. <code>/tools/Xilinx/Vivado/2023.2/bin/vivado</code>)</li>
              <li><b>Board</b> — Optional board identifier</li>
              <li><b>Part</b> — FPGA part number (e.g. <code>xc7a35tcpg236-1</code>)</li>
              <li><b>IP Repository</b> — Directory where packaged IP cores are stored</li>
            </ul>
            </body></html>
        """.trimIndent(),

        "Shortcuts, Tips &amp; Icon Legend" to """
            <html><body>
            <h2>Shortcuts, Tips &amp; Icon Legend</h2>

            <h3>Keyboard Shortcuts</h3>
            <ul>
              <li><b>Ctrl+Alt+R</b> — Run selected Tcl/XDC script in Vivado</li>
            </ul>

            <h3>Settings Panel Tips</h3>
            <ul>
              <li>The <b>&bull; Unsaved changes</b> label starts <b>blinking</b> as soon
                  as you edit any field — a reminder to Apply.</li>
              <li>Click <b>Reset</b> to discard unsaved edits.</li>
              <li>Click <b>? Help</b> to reopen this tutorial at any time.</li>
              <li>Changing Top Folder or Top File via right-click automatically updates the
                  settings panel fields (no need to reopen it).</li>
            </ul>

            <h3>Linter Debugger Panel</h3>
            <ul>
              <li>Open via <b>View &rarr; Tool Windows &rarr; Verilog Linter Debugger</b></li>
              <li>Top table: each diagnostic (file, line, severity, message)</li>
              <li>Bottom area: full raw linter command and output, updates every second</li>
            </ul>

            <h3>Icon Legend</h3>
            <ul>
              <li><b>Gold folder</b> — Top Folder</li>
              <li><b>Green file with gold star</b> — Top File (elaboration entry-point)</li>
              <li><b>Green square with V</b> — Verilog / SystemVerilog file type</li>
              <li><b>Blue chip</b> — HDL Settings / Linter Debugger tool window</li>
            </ul>
            </body></html>
        """.trimIndent()
    )

    private var currentIndex = 0

    private val titleLabel = JLabel().apply {
        font = font.deriveFont(Font.BOLD, 14f)
        border = BorderFactory.createEmptyBorder(0, 0, 6, 0)
    }

    /** Use IntelliJ's HTML editor kit so dark/light theme colours are applied automatically. */
    private val contentPane = JEditorPane().apply {
        editorKit = UIUtil.getHTMLEditorKit()
        isEditable = false
        // Explicitly inherit the IDE panel background (critical for Darcula)
        isOpaque = true
        background = UIManager.getColor("Panel.background") ?: background
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = UIUtil.getLabelFont()
    }

    private val pageLabel  = JLabel("", SwingConstants.CENTER)
    private val prevButton = JButton("\u2039 Previous")
    private val nextButton = JButton("Next \u203a")

    init {
        title = "HDL Plugin Tutorial"
        isModal = false
        init()
        loadSection(0)
    }

    override fun createCenterPanel(): JComponent {
        prevButton.addActionListener { navigate(-1) }
        nextButton.addActionListener { navigate(1) }

        val navPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(prevButton)
            add(Box.createHorizontalStrut(8))
            add(pageLabel)
            add(Box.createHorizontalStrut(8))
            add(nextButton)
        }

        val scrollPane = JBScrollPane(contentPane).apply {
            preferredSize = Dimension(600, 360)
            border = BorderFactory.createEtchedBorder()
            // Scroll pane viewport should also use the IDE background
            viewport.background = UIManager.getColor("Panel.background") ?: viewport.background
        }

        return JPanel(BorderLayout(0, 8)).apply {
            border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
            add(titleLabel,  BorderLayout.NORTH)
            add(scrollPane,  BorderLayout.CENTER)
            add(navPanel,    BorderLayout.SOUTH)
            preferredSize = Dimension(640, 480)
        }
    }

    override fun createActions(): Array<Action> = arrayOf(myOKAction.apply {
        putValue(Action.NAME, "Close")
    })

    private fun navigate(delta: Int) {
        val next = currentIndex + delta
        if (next in sections.indices) loadSection(next)
    }

    private fun loadSection(index: Int) {
        currentIndex = index
        val (sectionTitle, html) = sections[index]
        titleLabel.text = "${index + 1}. $sectionTitle"
        contentPane.text = html
        contentPane.caretPosition = 0
        pageLabel.text = "Section ${index + 1} of ${sections.size}"
        prevButton.isEnabled = index > 0
        nextButton.isEnabled = index < sections.lastIndex
    }
}
