package com.hdl.vivado.actions

import com.hdl.vivado.VivadoSettingsState
import com.hdl.vivado.VivadoUtils
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import java.io.File

class IPComposerAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        if (!virtualFile.isDirectory) {
            Messages.showErrorDialog(
                project,
                "Please select a folder for IP composition",
                "Invalid Selection"
            )
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
            // Create TCL script for IP Composer
            val tclScript = """
                # Open IP Catalog
                create_project -in_memory -part ${settings.part}
                
                # User will manually create/configure IP and export
                puts "IP Composer launched. Please create your IP and click 'Generate' when ready."
            """.trimIndent()

            VivadoUtils.launchVivado(
                settings.vivadoPath,
                virtualFile.path,
                tclScript,
                mode = "gui"
            )

            NotificationGroupManager.getInstance()
                .getNotificationGroup("Vivado")
                .createNotification(
                    "Vivado IP Composer Launched",
                    "IP Composer started in folder: ${virtualFile.name}",
                    NotificationType.INFORMATION
                )
                .notify(project)
                
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to launch Vivado: ${ex.message}",
                "Launch Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.isDirectory == true
    }
}
