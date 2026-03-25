package com.hdl.vivado.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup

class VivadoActionGroup : DefaultActionGroup() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isDirectory = virtualFile?.isDirectory == true
        val isTclFile = virtualFile != null && (virtualFile.fileType is com.hdl.tcl.TclFileType || virtualFile.extension in listOf("tcl", "xdc"))
        e.presentation.isEnabledAndVisible = isDirectory || isTclFile
    }
}