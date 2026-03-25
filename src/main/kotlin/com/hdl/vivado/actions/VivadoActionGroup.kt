package com.hdl.vivado.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class VivadoActionGroup : ActionGroup() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            IPComposerAction(),
            BuildProjectAction(),
            OpenProjectAction(),
            RunTclScriptAction()
        )
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isDirectory = virtualFile?.isDirectory == true
        val isTclFile = virtualFile?.fileType is com.hdl.tcl.TclFileType
        e.presentation.isEnabledAndVisible = isDirectory || isTclFile
    }
}