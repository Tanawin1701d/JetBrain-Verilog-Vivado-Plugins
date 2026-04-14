package com.hdl.verilog.linter

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

/**
 * Project-level service that acts as a notification hub for LinterSettingsState
 * changes triggered by right-click actions (SelectTopFolderAction / SelectTopFileAction).
 *
 * Using a plain service instead of MessageBus.syncPublish() avoids API-version
 * compatibility issues across IntelliJ releases.
 */
@Service(Service.Level.PROJECT)
class LinterSettingsBroadcaster(private val project: Project) {

    /** Registered callbacks; invoked on the EDT when settings change externally. */
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    fun subscribe(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: () -> Unit) {
        listeners.remove(listener)
    }

    /** Fire all registered listeners on the EDT. */
    fun notifyChanged() {
        val snapshot = listeners.toList()
        SwingUtilities.invokeLater {
            snapshot.forEach { it() }
            DaemonCodeAnalyzer.getInstance(project).restart()
        }
    }

    companion object {
        fun getInstance(project: Project): LinterSettingsBroadcaster =
            project.getService(LinterSettingsBroadcaster::class.java)
    }
}
