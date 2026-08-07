package com.hdl.vivado.actions

import com.hdl.vivado.FileUtils
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

class BuildProjectAction : AnAction("Build Project") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.isDirectory) {
            Messages.showErrorDialog(
                project,
                "Please select a folder containing HDL files",
                "Invalid Selection"
            )
            return
        }

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

        // Collect all HDL files recursively
        val hdlFiles = VivadoUtils.collectHDLFiles(virtualFile)

        if (hdlFiles.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No HDL files found in the selected folder",
                "No HDL Files"
            )
            return
        }

        try {

            val prjFolderName = virtualFile.name + "_prj"

            // Scoped by source folder name: two folders sharing a parent must not
            // share an exec directory, or building one wipes the other's project.
            val parentPath = virtualFile.parent?.path ?: virtualFile.path
            val vivadoExecDirPath = VivadoUtils.execWorkingDir(
                parentPath, virtualFile.name, VivadoUtils.ExecDirKind.PROJECT
            )

            // Asks before deleting a previous run; abort if the user says no.
            if (!FileUtils.prepareCleanWorkingDir(File(vivadoExecDirPath), project, "Vivado project")) {
                return
            }

            val createAndAddFileCmd = VivadoUtils.genTclCreatePrjAndAddFilesCommand(
                prjFolderName, prjFolderName,
                settings.part, settings.board, hdlFiles
            )

            val tclScript = """
                ${createAndAddFileCmd}
            """.trimIndent()

            // Launch Vivado
            VivadoUtils.launchVivado(
                settings.vivadoPath,
                vivadoExecDirPath,
                tclScript,
                mode = "gui"
            )

            NotificationGroupManager.getInstance()
                .getNotificationGroup("Vivado")
                .createNotification(
                    "Vivado Project Created",
                    "Created project with ${hdlFiles.size} HDL files",
                    NotificationType.INFORMATION
                )
                .notify(project)

        } catch (ex: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to create Vivado project: ${ex.message}",
                "Build Error"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.isDirectory == true
    }
}
