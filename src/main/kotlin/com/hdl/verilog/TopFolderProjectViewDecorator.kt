package com.hdl.verilog

import com.hdl.verilog.linter.LinterSettingsState
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.vfs.VirtualFile

class TopFolderProjectViewDecorator : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        val virtualFile = node.virtualFile ?: return
        val settings = LinterSettingsState.getInstance(project)

        // Decorate the top folder
        val topFolderPath = settings.topFolder
        if (topFolderPath != null && virtualFile.isDirectory && virtualFile.path == topFolderPath) {
            data.setIcon(VerilogIcons.TOP_FOLDER)
            data.tooltip = "Verilog top folder"
            return
        }

        // Decorate the top file
        val topFilePath = settings.topFile
        if (topFilePath != null && !virtualFile.isDirectory && virtualFile.path == topFilePath) {
            data.setIcon(VerilogIcons.TOP_FILE)
            data.tooltip = "Verilog top file (elaboration entry-point)"
        }
    }
}
