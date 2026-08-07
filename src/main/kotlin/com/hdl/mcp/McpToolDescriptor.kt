package com.hdl.mcp

import com.hdl.vivado.ParameterType
import com.hdl.vivado.PredefinedCommand

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchema: String
)

/**
 * Renders PredefinedCommand records into the MCP tool schema the client sees.
 *
 * Pure: the caller decides *which* commands to expose (that is a permission
 * question, and lives in VivaMcpServer) — this only decides how they look.
 */
internal object McpSchema {

    /** JSON-Schema "object" describing one command's parameters. */
    fun inputSchema(cmd: PredefinedCommand): String {
        if (cmd.parameters.isEmpty()) {
            return """{"type":"object","properties":{},"required":[]}"""
        }
        // Map each internal ParameterType to its JSON-Schema type keyword.
        val props = cmd.parameters.joinToString(",") { p ->
            val typeStr = when (p.type) {
                ParameterType.INT     -> "integer"
                ParameterType.BOOLEAN -> "boolean"
                else                  -> "string"
            }
            """${McpJson.quote(p.name)}:{"type":"$typeStr","description":${McpJson.quote(p.description)}}"""
        }
        val required = cmd.parameters.filter { it.required }.joinToString(",") { McpJson.quote(it.name) }
        return """{"type":"object","properties":{$props},"required":[$required]}"""
    }

    /** The tools/list payload: a JSON array of tool descriptors. */
    fun toolsList(commands: List<PredefinedCommand>): String {
        val tools = commands.map { McpToolDescriptor(it.id, it.description, inputSchema(it)) }
        return "[" + tools.joinToString(",") { t ->
            """{"name":${McpJson.quote(t.name)},"description":${McpJson.quote(t.description)},"inputSchema":${t.inputSchema}}"""
        } + "]"
    }
}
