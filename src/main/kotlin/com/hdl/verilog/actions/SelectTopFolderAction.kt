package com.hdl.verilog.actions

import com.hdl.verilog.linter.LinterSettingsState
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

class SelectTopFolderAction : AnAction("Set as Verilog Top Folder") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        if (!virtualFile.isDirectory) {
            Messages.showErrorDialog(
                project,
                "Please select a folder",
                "Invalid Selection"
            )
            return
        }

        val settings = LinterSettingsState.getInstance(project)
        settings.topFolder = virtualFile.path

        Messages.showInfoMessage(
            project,
            "Top folder set to: ${virtualFile.path}",
            "Verilog Linter Settings"
        )
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile?.isDirectory == true
    }
}