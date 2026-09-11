package com.example.agent.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Unified tool interface for GVONE OS.
 * Every tool callable by specialized agents or NodalEngine must implement this interface.
 */
interface Tool {
    val name: String
    val description: String
    val riskLevel: RiskLevel get() = RiskLevel.LOW
    val requiresPermission: Boolean get() = false

    suspend fun execute(parameters: Map<String, Any?>): ToolResult
}

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val durationMs: Long = 0L
)

/**
 * Thread-safe registry of all operational tools in GVONE OS.
 */
class ToolRegistry private constructor() {
    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name.lowercase()] = tool
    }

    fun unregister(name: String) {
        tools.remove(name.lowercase())
    }

    fun getTool(name: String): Tool? = tools[name.lowercase()]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun hasTool(name: String): Boolean = tools.containsKey(name.lowercase())

    fun clear() {
        tools.clear()
    }

    companion object {
        val global: ToolRegistry by lazy { ToolRegistry() }
    }
}
