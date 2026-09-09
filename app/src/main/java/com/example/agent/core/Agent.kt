package com.example.agent.core

/**
 * Standard interface for all GVONE agents, designed for loose coupling,
 * modularity, and pluggability (including adapters like OpenHands and OpenClaw).
 */
interface Agent {
    /** Unique identity / name of the agent */
    fun identity(): String

    /** List of declared capabilities */
    fun capabilities(): List<AgentCapability>

    /** Current sensory observation / internal state */
    fun observe(): Map<String, Any?>

    /** Execute a structured request */
    suspend fun execute(request: AgentRequest): AgentResult

    /** Current agent status */
    fun status(): AgentStatus

    /** Cancel ongoing execution */
    fun cancel()

    /** Health and metabolic metrics */
    fun health(): AgentHealth

    /** Last computed result */
    fun result(): AgentResult?
}
