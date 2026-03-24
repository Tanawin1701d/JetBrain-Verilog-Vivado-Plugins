package com.hdl.verilog.linter

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
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
import java.awt.Font

class LinterDebuggerToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(
            LinterDebuggerToolWindowContent(project).getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
}

class LinterDebuggerToolWindowContent(private val project: Project) {
    private val rawOutputArea = JBTextArea()
    private val resultsTableModel = DefaultTableModel(arrayOf("File", "Line", "Severity", "Message"), 0)
    private val resultsTable = JBTable(resultsTableModel)
    private val mainPanel = JPanel(BorderLayout())

    init {
        rawOutputArea.isEditable = false
        val editorFont = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
        rawOutputArea.font = Font(editorFont.name, editorFont.style, editorFont.size)

        val resultsScrollPane = JBScrollPane(resultsTable)
        val rawScrollPane = JBScrollPane(rawOutputArea)

        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, resultsScrollPane, rawScrollPane)
        splitPane.dividerLocation = 200

        mainPanel.add(splitPane, BorderLayout.CENTER)

        // Periodically update the UI
        val timer = java.util.Timer()
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                ApplicationManager.getApplication().invokeLater {
                    updateContent()
                }
            }
        }, 0, 1000)
    }

    private fun updateContent() {
        val service = LinterDebuggerService.getInstance(project)
        if (rawOutputArea.text != service.lastRawOutput) {
            rawOutputArea.text = service.lastRawOutput
            
            resultsTableModel.rowCount = 0
            for (result in service.lastResults) {
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
}
