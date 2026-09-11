package com.example.agent.core

/**
 * Context isolation and inheritance policy for an agent.
 */
enum class ContextPolicy {
    STRICT_ISOLATED,  // New scratchpad for every task; no shared context
    INHERIT_SESSION,  // Inherits session context but not other task scratchpads
    PERSISTENT        // Shared long-term memory across related tasks
}

/**
 * Memory read/write policy for an agent.
 */
enum class MemoryPolicy {
    READ_ONLY,        // Can only read approved episodic/semantic memory
    READ_WRITE,       // Can read and record new memory entries
    EPHEMERAL         // No persistent memory access
}

/**
 * Execution strategy for an agent.
 */
enum class ExecutionPolicy {
    AUTONOMOUS_LOOP,  // Multi-step Plan -> Act -> Observe -> Verify loop
    SINGLE_SHOT,      // Single prompt synthesis without recursive tool use
    HUMAN_IN_THE_LOOP // Requires explicit approval before dangerous tools
}

/**
 * Comprehensive, production-grade definition for an Agent in GVONE OS.
 * Ensures agents are not one-off functions but structured, observable entities.
 */
data class AgentDefinition(
    val id: String,
    val name: String,
    val description: String,
    val capabilities: List<AgentCapability> = emptyList(),
    val tools: List<String> = emptyList(),
    val model: String = "gemini-2.5-flash",
    val systemInstructions: String = "",
    val permissions: List<String> = emptyList(),
    val contextPolicy: ContextPolicy = ContextPolicy.STRICT_ISOLATED,
    val memoryPolicy: MemoryPolicy = MemoryPolicy.READ_WRITE,
    val executionPolicy: ExecutionPolicy = ExecutionPolicy.AUTONOMOUS_LOOP,
    val inputTypes: List<String> = listOf("text", "command"),
    val outputTypes: List<String> = listOf("text", "structured_json", "tool_call"),
    val maxRetries: Int = 3,
    val timeoutMs: Long = 30000L,
    val isEnabled: Boolean = true
)
