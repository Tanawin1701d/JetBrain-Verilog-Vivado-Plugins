package com.hdl.vivado.actions

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

class OpenProjectAction : AnAction("Open Project") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val settings = VivadoSettingsState.getInstance(project)

        // Check if Vivado exists
        if (!File(settings.vivadoPath).exists()) {
            Messages.showErrorDialog(
                project,
                "Vivado not found at: ${settings.vivadoPath}\nPlease configure the Vivado path in Settings > Tools > HDL Settings",
                "Vivado Not Found"
            )
            return
        }

        // Check if it's a .xpr file or a directory containing .xpr file
        val xprFile = if (virtualFile.extension == "xpr") {
            virtualFile
        } else if (virtualFile.isDirectory) {
            VivadoUtils.findVivadoProject(virtualFile)
        } else {
            null
        }

        if (xprFile == null) {
            Messages.showErrorDialog(
                project,
                "No Vivado project (.xpr) file found",
                "Project Not Found"
            )
            return
        }

        try {
            // Launch Vivado with the project file
            val command = listOf(settings.vivadoPath, xprFile.path)
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(xprFile.parent.path))
            processBuilder.start()

            NotificationGroupManager.getInstance()
                .getNotificationGroup("Vivado")
                .createNotification(
                    "Vivado Project Opened",
                    "Opened project: ${xprFile.name}",
                    NotificationType.INFORMATION
                )
                .notify(project)

        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to open Vivado project: ${ex.message}",
                "Open Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isXprFile = virtualFile?.extension == "xpr"
        val isDirectory = virtualFile?.isDirectory == true
        e.presentation.isEnabledAndVisible = isXprFile || isDirectory
    }
}
