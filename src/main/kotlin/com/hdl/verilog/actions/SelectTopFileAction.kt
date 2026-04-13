package com.hdl.verilog.actions

import com.hdl.verilog.linter.LinterSettingsBroadcaster
import com.hdl.verilog.linter.LinterSettingsState
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

class SelectTopFileAction : AnAction("Set as Verilog Top File") {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project     = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val settings    = LinterSettingsState.getInstance(project)
        val topFolder   = settings.topFolder

        if (topFolder == null) {
            Messages.showErrorDialog(
                project,
                "Please set the Top Folder first before selecting a Top File.",
                "No Top Folder Set"
            )
            return
        }

        if (!virtualFile.path.startsWith(topFolder)) {
            Messages.showErrorDialog(
                project,
                "The selected file must be inside the Top Folder:\n$topFolder",
                "Invalid Selection"
            )
            return
        }

        settings.topFile = virtualFile.path
        ProjectView.getInstance(project).refresh()

        // Notify the settings panel so it refreshes its displayed fields
        LinterSettingsBroadcaster.getInstance(project).notifyChanged()

        Messages.showInfoMessage(
            project,
            "Top file set to: ${virtualFile.path}",
            "Verilog Linter Settings"
        )
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project     = e.project

        if (virtualFile == null || project == null || virtualFile.isDirectory) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val ext = virtualFile.extension?.lowercase()
        e.presentation.isEnabledAndVisible = ext in listOf("v", "vh", "sv", "svh")
    }
}
