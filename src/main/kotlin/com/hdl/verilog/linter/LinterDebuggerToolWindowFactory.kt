package com.hdl.verilog.linter

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.table.DefaultTableModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.util.Disposer
import com.intellij.util.Alarm
import java.awt.Font

class LinterDebuggerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = LinterDebuggerToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
        Disposer.register(content, toolWindowContent)
    }
}

class LinterDebuggerToolWindowContent(private val project: Project) : Disposable {
    private val rawOutputArea = JBTextArea()
    private val resultsTableModel = DefaultTableModel(arrayOf("File", "Line", "Severity", "Message"), 0)
    private val resultsTable = JBTable(resultsTableModel)
    private val mainPanel = JPanel(BorderLayout())
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    init {
        rawOutputArea.isEditable = false
        val editorFont = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
        rawOutputArea.font = Font(editorFont.name, editorFont.style, editorFont.size)

        val resultsScrollPane = JBScrollPane(resultsTable)
        val rawScrollPane = JBScrollPane(rawOutputArea)

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, resultsScrollPane, rawScrollPane)
        splitPane.dividerLocation = 200

        val infoLabel = JBLabel("(to check syntax of the linter please check the top folder; if it does not work, please see the Verilog linter debugger panel below)").apply {
            foreground = JBColor.YELLOW
            border = JBUI.Borders.empty(4)
        }

        mainPanel.add(infoLabel, BorderLayout.NORTH)
        mainPanel.add(splitPane, BorderLayout.CENTER)

        // Periodically update the UI
        scheduleUpdate()
    }

    private fun scheduleUpdate() {
        if (project.isDisposed) return
        alarm.addRequest({
            updateContent()
            scheduleUpdate()
        }, 1000)
    }

    private fun updateContent() {
        if (project.isDisposed) return
        val service = LinterDebuggerService.getInstance(project)
        if (rawOutputArea.text != service.lastRawOutput) {
            rawOutputArea.text = service.lastRawOutput
            
            resultsTableModel.rowCount = 0
            for (result in service.getAllResults()) {
                resultsTableModel.addRow(arrayOf(
                    result.file.substringAfterLast('/'),
                    result.line,
                    result.severity,
                    result.message
                ))
            }
        }
    }

    fun getContent(): JPanel = mainPanel

    override fun dispose() {
        // Alarm is disposed automatically as it's registered with this Disposable
    }
}
