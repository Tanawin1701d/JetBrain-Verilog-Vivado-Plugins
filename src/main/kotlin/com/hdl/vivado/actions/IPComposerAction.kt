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
import java.io.IOException

class IPComposerAction : AnAction("IP Composer") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
            val folderName = virtualFile.name
            val prjFolderName = virtualFile.name + "_prj"
            val ipFolderName = virtualFile.name + "_ip"
            val ipRepoPath = settings.ipRepoPath
            
            if (ipRepoPath.isEmpty()) {
                Messages.showErrorDialog(
                    project,
                    "IP Repository Path is not configured. Please set it in Vivado Settings.",
                    "Configuration Error"
                )
                return
            }

            val vivadoExecDirPath = "${virtualFile.parent.path}/viva_ip_exec"
            val ipRepoDirPath = ipRepoPath

            val createAndAddFileCmd = VivadoUtils.genTclCreatePrjAndAddFilesCommand(
                                        prjFolderName, prjFolderName,
                                        settings.part, settings.board,
                                        VivadoUtils.collectHDLFiles(virtualFile))

            // Create TCL script for IP Composer
            // root_dir should be absolute or relative to where vivado is launched (vivadoExecDirPath)
            // But usually ipx::package_project -root_dir works best with absolute paths if they are available
            val tclScript = """
                ${createAndAddFileCmd}
                # LAUNCH IP COMPOSER
                ipx::package_project -root_dir ${File(ipRepoDirPath, ipFolderName).absolutePath} -vendor user.org -library user -taxonomy /UserIP -import_files
            """.trimIndent()

            val repoDir = File(ipRepoDirPath)
            if (!repoDir.exists() && !repoDir.mkdirs()) {
                throw IOException("Failed to create working directory: $repoDir")
            }

            VivadoUtils.launchVivado(
                settings.vivadoPath,
                vivadoExecDirPath,
                tclScript,
                mode = "gui",
                deleteIfExists = true
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