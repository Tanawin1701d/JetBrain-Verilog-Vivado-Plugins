package com.hdl.vivado.actions

import com.hdl.vivado.VivadoProcessManager
import com.hdl.vivado.VivadoUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

class LaunchVivaCoTermAction : AnAction("Launch Vivado Console") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Open (show) the Vivado Console tool window
        val tw = ToolWindowManager.getInstance(project).getToolWindow("Vivado Console")
        tw?.activate(null)

        // Optionally auto-open an XPR found in the selected folder
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val xprPath = virtualFile
            ?.takeIf { it.isDirectory }
            ?.let { VivadoUtils.findVivadoProject(it) }
            ?.path

        VivadoProcessManager.getInstance(project).launchVivado(xprPath = xprPath)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
