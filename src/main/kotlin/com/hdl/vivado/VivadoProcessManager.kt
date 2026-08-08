package com.hdl.vivado

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

enum class VivadoStatus { STOPPED, STARTING, RUNNING, CRASHED }

/**
 * Singleton project service that owns the long-running Vivado process used by Viva-CoTerm.
 *
 * Launch strategy to work around the start_gui stdin issue:
 *   1. TclBridgeService starts a TCP server and returns its port.
 *   2. Vivado is launched with "-mode tcl" and a startup script injected by this manager.
 *   3. The startup script connects BACK to our TCP server, then calls start_gui.
 *   4. After start_gui, Vivado's Tk event loop processes socket fileevent callbacks —
 *      so TCL commands sent through the socket are evaluated even while the GUI is open.
 */
@Service(Service.Level.PROJECT)
class VivadoProcessManager(private val project: Project) : Disposable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var process: Process? = null
    private val _statusFlow = MutableStateFlow(VivadoStatus.STOPPED)
    val statusFlow: StateFlow<VivadoStatus> = _statusFlow.asStateFlow()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    fun launchVivado(xprPath: String? = null, initialTcl: String? = null) {
        if (process?.isAlive == true) {
            ApplicationManager.getApplication().invokeLater {
                val result = Messages.showYesNoCancelDialog(
                    project,
                    "A Vivado session is already running. Do you want to restart it?\n\nYes = Restart, No = Keep existing",
                    "Vivado Already Running",
                    "Restart",
                    "Keep",
                    "Cancel",
                    null
                )
                when (result) {
                    Messages.YES -> scope.launch {
                        shutdownInternal()
                        doLaunch(xprPath, initialTcl)
                    }
                    // NO: keep existing session; CANCEL: do nothing
                }
            }
            return
        }
        scope.launch { doLaunch(xprPath, initialTcl) }
    }

    fun shutdownVivado() {
        scope.launch { shutdownInternal() }
    }

    fun restartVivado(xprPath: String? = null, initialTcl: String? = null) {
        scope.launch {
            shutdownInternal()
            doLaunch(xprPath, initialTcl)
        }
    }

    val isRunning: Boolean get() = process?.isAlive == true

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private suspend fun doLaunch(xprPath: String?, initialTcl: String?) {
        // Step 1: fetch the two services this function depends on
        val settings = VivadoSettingsState.getInstance(project)
        val bridge = TclBridgeService.getInstance(project)

        // Step 2: guard — if the Vivado binary doesn't exist, fail fast and tell the user
        if (!File(settings.vivadoPath).exists()) {
            bridge.publishInfo("[VivaCo-Term] ERROR: Vivado not found at '${settings.vivadoPath}'. Configure path in HDL Settings.")
            return
        }

        // Step 3: open the TCP bridge server first so the port is ready before Vivado starts
        _statusFlow.value = VivadoStatus.STARTING
        bridge.publishInfo("[VivaCo-Term] Starting Vivado...")
        val bridgePort = bridge.startBridgeServer()  // ServerSocket on a random port; Vivado will connect back to this

        // Step 4: write the auto-generated startup TCL to a temp file
        // Vivado needs a file path via -source; it can't receive inline script text from the command line
        val startupScript = buildStartupScript(bridgePort, xprPath, initialTcl)
        val scriptFile = File.createTempFile("vivacoterm_startup_", ".tcl")
        scriptFile.writeText(startupScript)
        scriptFile.deleteOnExit()  // JVM deletes it when the IDE closes

        // Step 5: spawn the Vivado process
        // -mode tcl  : headless TCL mode (no GUI yet — start_gui is called inside the startup script)
        // -source    : run the startup script immediately on boot
        // redirectErrorStream: merge stderr into stdout so one reader handles all output
        // DISPLAY    : required on Linux for start_gui to know which X11 display to use
        val pb = ProcessBuilder(
            settings.vivadoPath,
            "-mode", "tcl",
            "-source", scriptFile.absolutePath
        )
        pb.redirectErrorStream(true)
        pb.environment()["DISPLAY"] = System.getenv("DISPLAY") ?: ":0"

        // pb.start() is a blocking OS call — run it on the IO thread pool to avoid blocking the coroutine dispatcher
        val proc = withContext(Dispatchers.IO) { pb.start() }
        process = proc

        // Step 6: coroutine that reads Vivado stdout line-by-line and forwards to the CoTerm panel
        // Also listens for the handshake signal "VIVACOTERM_CONNECTED" which the startup script prints
        // after successfully connecting back to our TCP bridge — that's when we flip status to RUNNING
        scope.launch {
            val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val ln = line!!
                    bridge.publishOutput(ln)
                    if (ln.contains("VIVACOTERM_CONNECTED")) {
                        _statusFlow.value = VivadoStatus.RUNNING
                        bridge.publishInfo("[VivaCo-Term] Bridge active. Type TCL commands below.")
                    }
                }
            } catch (_: Exception) { /* stream closed when process dies — nothing to do */ }
        }

        // Step 7: coroutine that blocks until Vivado exits, then updates status and cleans up
        // exit code 0 = user closed Vivado normally (STOPPED), anything else = crash (CRASHED)
        scope.launch {
            withContext(Dispatchers.IO) { proc.waitFor() }
            val wasRunning = _statusFlow.value == VivadoStatus.RUNNING
            _statusFlow.value = if (proc.exitValue() == 0) VivadoStatus.STOPPED else VivadoStatus.CRASHED
            bridge.disconnect()
            bridge.publishInfo(
                if (proc.exitValue() == 0) "[VivaCo-Term] Vivado session ended."
                else "[VivaCo-Term] Vivado exited with code ${proc.exitValue()}."
            )
        }
    }

    private suspend fun shutdownInternal() {
        val bridge = TclBridgeService.getInstance(project)
        val proc = process ?: return

        try {
            bridge.sendCommand("exit").await()
        } catch (_: Exception) { /* ignore — process may already be down */ }

        withContext(Dispatchers.IO) {
            if (!proc.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly()
            }
        }

        _statusFlow.value = VivadoStatus.STOPPED
        bridge.disconnect()
        process = null
    }

    /**
     * Generates the Vivado startup TCL script.
     *
     * The script:
     *  1. Connects back to our plugin's TCP server (bridge port).
     *  2. Registers a fileevent handler so TCL commands arriving on the socket are evaluated
     *     by Vivado's event loop — this works correctly even after start_gui is called.
     *  3. Optionally opens an XPR or sources an initial TCL string.
     *  4. Calls start_gui to open the Vivado GUI window.
     *
     * NOTE: stdin is intentionally NOT used for command I/O because calling start_gui in
     * Vivado TCL mode makes stdin inaccessible for further commands. The socket approach
     * is the reliable workaround.
     */
    private fun buildStartupScript(bridgePort: Int, xprPath: String?, initialTcl: String?): String {
        val initialSection = buildString {
            if (xprPath != null) appendLine("catch {open_project {$xprPath}}")
            if (initialTcl != null) appendLine(initialTcl)
        }

        return """
# VivaCo-Term startup script — auto-generated by HDL+Vivado JetBrains plugin
namespace eval ::vivacoterm {
    variable _chan ""
    variable _buf  ""

    proc _connect {host port} {
        variable _chan
        if {[catch {set _chan [socket ${'$'}host ${'$'}port]} err]} {
            puts stderr "VivaCo-Term: bridge connect failed: ${'$'}err"
            return
        }
        fconfigure ${'$'}_chan -translation lf -buffering line -encoding utf-8
        fileevent  ${'$'}_chan readable ::vivacoterm::_on_readable
        puts       ${'$'}_chan "%%CONNECTED%%"
        flush      ${'$'}_chan
    }

    proc _on_readable {} {
        variable _chan
        variable _buf

        if {[eof ${'$'}_chan]} {
            catch {close ${'$'}_chan}
            set _chan ""
            return
        }

        set n [gets ${'$'}_chan line]
        if {${'$'}n < 0} return

        # %%SEND%% forces evaluation of whatever is buffered
        if {${'$'}line eq "%%SEND%%"} {
            set toEval ${'$'}_buf
            set _buf ""
            set rc [catch {uplevel #0 ${'$'}toEval} out]
            _send_result ${'$'}rc ${'$'}out
            return
        }

        if {${'$'}_buf eq ""} {
            set _buf ${'$'}line
        } else {
            append _buf "\n" ${'$'}line
        }

        # Auto-detect when a complete command has been accumulated
        if {[info complete ${'$'}_buf]} {
            set toEval ${'$'}_buf
            set _buf ""
            set rc [catch {uplevel #0 ${'$'}toEval} out]
            _send_result ${'$'}rc ${'$'}out
        }
    }

    proc _send_result {rc out} {
        variable _chan
        if {${'$'}_chan eq ""} return
        if {${'$'}rc == 0} {
            if {${'$'}out eq ""} {
                puts ${'$'}_chan "OUT:"
            } else {
                foreach part [split ${'$'}out "\n"] {
                    puts ${'$'}_chan "OUT:${'$'}part"
                }
            }
        } else {
            foreach part [split ${'$'}out "\n"] {
                puts ${'$'}_chan "ERR:${'$'}part"
            }
        }
        puts  ${'$'}_chan "%%PROMPT%%"
        flush ${'$'}_chan
    }
}

::vivacoterm::_connect localhost $bridgePort
puts  stdout "VIVACOTERM_CONNECTED"
flush stdout

$initialSection
start_gui
""".trimIndent()
    }

    override fun dispose() {
        scope.launch { shutdownInternal() }
        scope.cancel()
    }

    companion object {
        fun getInstance(project: Project): VivadoProcessManager =
            project.getService(VivadoProcessManager::class.java)
    }
}
