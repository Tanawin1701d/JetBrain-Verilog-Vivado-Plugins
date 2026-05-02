package com.hdl.vivado.actions

import com.hdl.vivado.VivadoSettingsState
import com.hdl.vivado.VivadoUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages
import java.io.File

class CreateHlsAction : AnAction("Create HLS Kernel") {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.isDirectory) {
            Messages.showErrorDialog(project, "Please select a folder to create HLS kernel", "Invalid Selection")
            return
        }

        val hlsProjectName = Messages.showInputDialog(
            project,
            "Enter HLS Project Name:",
            "Create HLS Kernel",
            Messages.getQuestionIcon()
        ) ?: return

        if (hlsProjectName.isBlank()) return

        val settings = VivadoSettingsState.getInstance(project)
        val vitisPath = settings.vitisPath

        if (!File(vitisPath).exists()) {
            Messages.showErrorDialog(
                project,
                "Vitis not found at: $vitisPath\nPlease ensure Vitis path is correct in Settings.",
                "Vitis Not Found"
            )
            return
        }

        try {
            val part = settings.part
            val board = settings.board
            
            val tclScript = if (board.isNotBlank()) {
                """
                open_project $hlsProjectName
                set_top $hlsProjectName
                open_solution "solution1" -flow_target vitis
                set_part {$part}
                set_property board_part {$board} [current_project]
                create_clock -period 10 -name default
                # Additional parameters can be added here if needed
                exit
                """.trimIndent()
            } else {
                """
                open_project $hlsProjectName
                set_top $hlsProjectName
                open_solution "solution1" -flow_target vitis
                set_part {$part}
                create_clock -period 10 -name default
                exit
                """.trimIndent()
            }

            VivadoUtils.launchVitisUnifiedHls(vitisPath, virtualFile.path, tclScript)
            
            Messages.showInfoMessage(project, "HLS Project '$hlsProjectName' creation started.", "HLS Project Created")
        } catch (ex: Exception) {
            Messages.showErrorDialog(project, "Failed to launch Vitis HLS: ${ex.message}", "Launch Error")
        }
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.isDirectory == true
    }
}
