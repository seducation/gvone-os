package com.example.agent.core

/**
 * Standard interface for all GVONE agents, designed for loose coupling,
 * modularity, and pluggability (including adapters like OpenHands and OpenClaw).
 */
interface Agent {
    /** Unique identity / name of the agent */
    fun identity(): String

    /** Unique ID of the agent */
    val id: String get() = identity()

    /** Human-readable name */
    val name: String get() = identity()

    /** High-level description of agent's role and purpose */
    val description: String get() = "Autonomous agent $name"

    /** Semantic version */
    val version: String get() = "1.0.0"

    /** List of declared capabilities */
    fun capabilities(): List<AgentCapability>

    /** Tools supported and utilized by this agent */
    val supportedTools: List<String> get() = emptyList()

    /** Permissions required by this agent */
    val permissions: List<String> get() = emptyList()

    /** Current active concurrent execution load */
    val currentLoad: Int get() = 0

    /** Maximum allowed concurrent tasks */
    val maxConcurrency: Int get() = 3

    /** Historical reliability score (0.0 to 1.0) */
    val reliability: Double get() = 1.0

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
