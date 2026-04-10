package com.hdl.verilog

import com.hdl.verilog.linter.LinterSettingsState
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.vfs.VirtualFile

class TopFolderProjectViewDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        val topFolderPath = LinterSettingsState.getInstance(project).topFolder ?: return
        val virtualFile = node.virtualFile ?: return

        if (!virtualFile.isDirectory) return
        if (!matchesTopFolder(virtualFile, topFolderPath)) return

        data.setIcon(VerilogIcons.TOP_FOLDER)
        data.tooltip = "Verilog top folder"
    }

    private fun matchesTopFolder(virtualFile: VirtualFile, topFolderPath: String): Boolean {
        return virtualFile.path == topFolderPath
    }
}
