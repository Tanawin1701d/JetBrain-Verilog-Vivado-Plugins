package com.hdl.coterm

import com.hdl.mcp.VivaMcpServer
import com.hdl.vivado.VivadoSettingsState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class VivaCoTermToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = VivaCoTermPanel(project)
        val content = ContentFactory.getInstance().createContent(panel.component, "", false)
        toolWindow.contentManager.addContent(content)
        Disposer.register(content, panel)

        // Start MCP server when the tool window is first created
        val settings = VivadoSettingsState.getInstance(project)
        if (settings.mcpEnabled) {
            VivaMcpServer.getInstance(project).start()
        }
    }
}
