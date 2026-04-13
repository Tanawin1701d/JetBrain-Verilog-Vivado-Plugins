package com.hdl.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JComponent

class HdlSettingsToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowContent = HdlSettingsToolWindowContent(project)
        val content = ContentFactory.getInstance().createContent(
            toolWindowContent.getContent(),
            "",
            false
        )
        toolWindow.contentManager.addContent(content)
        Disposer.register(content, toolWindowContent)
    }
}

class HdlSettingsToolWindowContent(project: Project) : Disposable {
    private val settingsPanel = HdlSettingsPanel(project, showActionButtons = true)

    fun getContent(): JComponent = settingsPanel.getComponent()

    override fun dispose() {
        settingsPanel.dispose()
    }
}
