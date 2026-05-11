package com.hdl.vivado

data class PredefinedCommand(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<CommandParameter>,
    val tclGenerator: (Map<String, Any>) -> String
)

data class CommandParameter(
    val name: String,
    val type: ParameterType,
    val required: Boolean,
    val description: String,
    val default: Any? = null
)

enum class ParameterType { STRING, INT, BOOLEAN }
