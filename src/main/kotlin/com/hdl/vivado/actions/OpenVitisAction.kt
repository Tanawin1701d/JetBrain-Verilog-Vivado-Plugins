package com.hdl.vivado.actions

import com.hdl.vivado.VivadoSettingsState
import com.hdl.vivado.VivadoUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import java.io.File

class OpenVitisAction : AnAction("Open Vitis") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.isDirectory) {
            Messages.showErrorDialog(project, "Please select a folder to open as Vitis workspace", "Invalid Selection")
            return
        }

        val settings = VivadoSettingsState.getInstance(project)
        val vitisPath = settings.vitisPath

        if (!File(vitisPath).exists()) {
            Messages.showErrorDialog(
                project,
                "Vitis not found at: $vitisPath\nPlease configure the Vitis path in Settings > Tools > HDL Settings",
                "Vitis Not Found"
            )
            return
        }

        try {
            val workingDir = virtualFile.parent?.path ?: virtualFile.path
            VivadoUtils.launchVitis(vitisPath, workingDir, virtualFile.path)
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, "Failed to launch Vitis: ${ex.message}", "Launch Error")
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.isDirectory == true
    }
}
