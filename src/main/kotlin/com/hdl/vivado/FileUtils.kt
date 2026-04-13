package com.hdl.vivado

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.File

object FileUtils {
    /**
     * Deletes a file after asking for permission if it's not in the /tmp directory.
     * @return true if deleted, false otherwise
     */
    fun safeDelete(file: File, project: Project?): Boolean {
        if (!file.exists()) return false

        val path = file.absolutePath
        val isInTmp = path.startsWith("/tmp") || path.startsWith(System.getProperty("java.io.tmpdir"))

        if (!isInTmp) {
            val result = Messages.showYesNoDialog(
                project,
                "The plugin is attempting to delete a file outside of the temporary directory:\n$path\n\nDo you want to allow this?",
                "Permission to Delete File",
                Messages.getQuestionIcon()
            )
            if (result != Messages.YES) {
                return false
            }
        }

        return file.delete()
    }
}
