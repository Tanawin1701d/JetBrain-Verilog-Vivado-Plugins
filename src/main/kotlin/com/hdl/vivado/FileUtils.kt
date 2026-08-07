package com.hdl.vivado

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.io.File

object FileUtils {

    private val LOG = Logger.getInstance(FileUtils::class.java)

    /** True when [file] lives under the JVM temp directory (or /tmp on Unix). */
    private fun isUnderTempDir(file: File): Boolean {
        val path = file.absolutePath
        val tmp = System.getProperty("java.io.tmpdir")?.let { File(it).absolutePath }
        return path.startsWith("/tmp") || (tmp != null && path.startsWith(tmp))
    }

    /**
     * Delete a single file, asking first if it is outside the temp directory.
     * @return true if the file was deleted
     */
    fun safeDelete(file: File, project: Project?): Boolean {
        if (!file.exists()) return false

        // Temp files are ours; delete without ceremony.
        if (isUnderTempDir(file)) return file.delete()

        // Outside temp we must ask, and we can only ask from the EDT. Callers on a
        // background thread (both linters delete from doAnnotate) get a refusal —
        // never a silent delete, and never a deadlock-prone invokeAndWait.
        if (!ApplicationManager.getApplication().isDispatchThread) {
            LOG.warn("Refusing to delete outside the temp directory from a background thread: ${file.absolutePath}")
            return false
        }

        val result = Messages.showYesNoDialog(
            project,
            "The plugin is attempting to delete a file outside of the temporary directory:\n" +
                "${file.absolutePath}\n\nDo you want to allow this?",
            "Permission to Delete File",
            Messages.getQuestionIcon()
        )
        if (result != Messages.YES) return false

        return file.delete()
    }

    /**
     * Make [dir] exist and be empty, ready for a one-shot Vivado/Vitis launch.
     *
     * If the directory already has contents they belong to a previous run — and
     * that means a whole Vivado project: block designs, IP, constraints added in
     * the GUI, synthesis runs, bitstreams. So we ask before wiping it, and we
     * verify the wipe actually succeeded rather than building on half-deleted
     * state (a still-open Vivado session holds locks).
     *
     * Must be called on the EDT; [Messages] dialogs require it. Actions are
     * always invoked there.
     *
     * @return true if the directory is ready to use; false if the user declined
     *         or the filesystem refused, in which case the caller must abort.
     */
    fun prepareCleanWorkingDir(dir: File, project: Project?, description: String): Boolean {
        if (dir.exists() && !dir.isDirectory) {
            Messages.showErrorDialog(
                project,
                "A file already exists where the working directory should go:\n\n${dir.absolutePath}\n\n" +
                    "Move or rename it and try again.",
                "Cannot Create Working Directory"
            )
            return false
        }

        val existingEntries = dir.takeIf { it.isDirectory }?.list()?.size ?: 0
        if (existingEntries > 0) {
            val answer = Messages.showYesNoDialog(
                project,
                "A previous $description already exists at:\n\n${dir.absolutePath}\n\n" +
                    "Continuing deletes it and everything inside it — including any block designs, " +
                    "IP, constraints, runs or bitstreams you created in the Vivado GUI.\n\n" +
                    "This cannot be undone.",
                "Overwrite Existing $description?",
                "Delete and Continue",
                "Cancel",
                Messages.getWarningIcon()
            )
            if (answer != Messages.YES) return false

            // deleteRecursively() reports partial failure; a running Vivado will hold locks.
            if (!dir.deleteRecursively()) {
                Messages.showErrorDialog(
                    project,
                    "Could not fully delete:\n\n${dir.absolutePath}\n\n" +
                        "Close any Vivado session still using it, then try again.",
                    "Delete Failed"
                )
                return false
            }
        }

        if (!dir.exists() && !dir.mkdirs()) {
            Messages.showErrorDialog(
                project,
                "Failed to create working directory:\n\n${dir.absolutePath}",
                "Cannot Create Working Directory"
            )
            return false
        }

        return true
    }
}
