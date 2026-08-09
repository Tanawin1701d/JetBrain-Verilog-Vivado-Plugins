package com.hdl.vivado

/**
 * One entry in the command library, rendered 1:1 as an MCP tool.
 *
 * Most commands are Tcl: [tclGenerator] turns validated arguments into a script that is sent
 * to the running Vivado session. A few answer from plugin-local data instead — those set
 * [localHandler], and the MCP server replies with its return value without touching Vivado
 * (and without requiring Vivado to be running). Exactly one of the two is ever used:
 * when [localHandler] is non-null, [tclGenerator] is never called.
 */
data class PredefinedCommand(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<CommandParameter>,
    val tclGenerator: (Map<String, Any>) -> String,
    val localHandler: ((Map<String, Any>) -> String)? = null
) {
    /** True when this command is answered inside the plugin rather than by Vivado. */
    val isLocal: Boolean get() = localHandler != null
}

data class CommandParameter(
    val name: String,
    val type: ParameterType,
    val required: Boolean,
    val description: String,
    val default: Any? = null
)

enum class ParameterType { STRING, INT, BOOLEAN }
