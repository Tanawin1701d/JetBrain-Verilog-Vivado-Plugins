package com.hdl.verilog

import com.hdl.verilog.linter.LinterSettingsState
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

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
            data.background = JBColor(Color(255, 255, 200), Color(60, 60, 0)) // Soft yellow background
            data.addText(" [Top Folder]", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
    }
}
