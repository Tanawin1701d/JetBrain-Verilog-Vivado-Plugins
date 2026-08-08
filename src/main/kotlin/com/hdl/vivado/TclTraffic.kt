package com.hdl.vivado

/**
 * One piece of traffic that crossed the Tcl bridge, in the direction it travelled.
 *
 * The console panel renders lines from [TclBridgeService.outputFlow], which only carries what
 * comes *back* from Vivado. Anything that wants the full two-way picture — the session recorder
 * above all — listens for these events instead, because they also cover what was sent, including
 * the commands an MCP client issues without ever touching the input field.
 */
enum class TrafficDirection {
    /** A Tcl command written to the socket. */
    SENT,

    /** A line read back from Vivado — command output or process stdout. */
    RECEIVED,

    /** A plugin notice ("[VivaCo-Term] ...", "[AI] ..."), printed to the console but never on the wire. */
    INFO
}

data class TrafficEvent(
    val direction: TrafficDirection,
    val text: String,
    /** Wall-clock time the data actually crossed the bridge, not the time it was written to disk. */
    val timestampMs: Long
)

/**
 * Notified synchronously, on whichever thread moved the data.
 *
 * Synchronous on purpose: a buffered flow would drop events under load, and a recording with
 * silent holes in it is worse than no recording. Implementations must return quickly and must
 * not throw — [TclBridgeService] swallows exceptions so a broken listener cannot stall the bridge.
 */
fun interface TrafficListener {
    fun onTraffic(event: TrafficEvent)
}
