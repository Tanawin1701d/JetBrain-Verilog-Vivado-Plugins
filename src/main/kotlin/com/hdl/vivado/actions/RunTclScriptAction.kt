package com.hdl.vivado.actions

import com.hdl.tcl.TclFileType
import com.hdl.vivado.VivadoSettingsState
import com.hdl.vivado.VivadoUtils
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import java.io.File

class RunTclScriptAction : AnAction("Run Tcl Script") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (virtualFile.fileType !is TclFileType) {
            return
        }

        val settings = VivadoSettingsState.getInstance(project)

        // Check if Vivado exists
        if (!File(settings.vivadoPath).exists()) {
            Messages.showErrorDialog(
                project,
                "Vivado not found at: ${settings.vivadoPath}\nPlease configure Vivado path in Settings > Tools > Vivado Settings",
                "Vivado Not Found"
            )
            return
        }

        try {
            // Use the parent folder of the script as working directory
            val workingDir = virtualFile.parent.path

            // Launch Vivado with the script
            VivadoUtils.launchVivado(
                vivadoPath = settings.vivadoPath,
                workingDirectory = workingDir,
                tclFilePath = virtualFile.path,
                mode = "gui"
            )

            NotificationGroupManager.getInstance()
                .getNotificationGroup("Vivado")
                .createNotification(
                    "Vivado TCL Script Executed",
                    "Running script: ${virtualFile.name}",
                    NotificationType.INFORMATION
                )
                .notify(project)

        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to run Vivado TCL script: ${ex.message}",
                "Execution Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile != null && virtualFile.fileType is TclFileType
    }
}
