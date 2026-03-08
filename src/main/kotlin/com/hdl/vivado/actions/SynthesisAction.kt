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

class SynthesisAction : AnAction() {
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
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

        // Find Vivado project
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
            // Create synthesis TCL script
            val tclScript = """
                open_project "${xprFile.path}"
                reset_run synth_1
                launch_runs synth_1
                wait_on_run synth_1
                
                # Open synthesized design
                open_run synth_1
                
                puts "Synthesis completed!"
            """.trimIndent()

            // Launch Vivado in batch mode for synthesis
            VivadoUtils.launchVivado(
                settings.vivadoPath,
                xprFile.parent.path,
                tclScript,
                mode = "batch"
            )

            NotificationGroupManager.getInstance()
                .getNotificationGroup("Vivado")
                .createNotification(
                    "Synthesis Started",
                    "Running synthesis on project: ${xprFile.name}",
                    NotificationType.INFORMATION
                )
                .notify(project)
                
        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to run synthesis: ${ex.message}",
                "Synthesis Error"
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
