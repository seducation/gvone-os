package com.example.agent.core

import java.util.UUID

/**
 * Execution status and lifecycle states of an agent or task.
 * Supports the full lifecycle:
 * REGISTERED -> AVAILABLE -> SELECTED -> PLANNING -> EXECUTING -> OBSERVING -> EVALUATING -> COMPLETED
 * along with failure and waiting states.
 */
enum class AgentStatus(val displayName: String) {
    REGISTERED("Registered"),
    AVAILABLE("Available"),
    SELECTED("Selected"),
    IDLE("Idle"),
    PLANNING("Planning"),
    RUNNING("Running"),
    EXECUTING("Executing"),
    OBSERVING("Observing"),
    EVALUATING("Evaluating"),
    WAITING("Waiting"),
    WAITING_PERMISSION("Waiting for Permission"),
    WAITING_USER("Waiting for User Input"),
    RETRYING("Retrying"),
    PAUSED("Paused"),
    BLOCKED("Blocked"),
    TIMEOUT("Timed Out"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled");

    val isRunning: Boolean get() = this == RUNNING || this == EXECUTING || this == OBSERVING || this == EVALUATING || this == RETRYING
    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT
    val isWaiting: Boolean get() = this == WAITING || this == WAITING_PERMISSION || this == WAITING_USER || this == PAUSED || this == BLOCKED
}

/**
 * Declares a rich capability provided by an agent.
 */
data class AgentCapability(
    val name: String,
    val description: String,
    val supportedActions: List<String> = emptyList(),
    val requiresPermission: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.LOW,
    val id: String = name.lowercase().replace(" ", "_"),
    val category: String = "general",
    val keywords: List<String> = emptyList(),
    val proficiency: Double = 1.0, // 0.0 to 1.0
    val averageExecutionTime: Long = 500L,
    val asyncSupport: Boolean = true
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Provenance tracking to maintain auditability and origin of generated/extracted data.
 */
data class Provenance(
    val origin: String,
    val agentName: String,
    val confidence: Double = 1.0,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceHash: String? = null
)

/**
 * Structured inter-agent request. Replaces unstructured string passing.
 */
data class AgentRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val sourceAgent: String,
    val targetAgent: String,
    val action: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val context: Map<String, Any?> = emptyMap(),
    val permissions: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Structured inter-agent result.
 */
data class AgentResult(
    val requestId: String,
    val status: AgentStatus,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val provenance: Provenance? = null,
    val durationMs: Long = 0L
) {
    val isSuccess: Boolean get() = status == AgentStatus.COMPLETED && error == null
}

/**
 * Metabolic & functional health of an agent.
 */
data class AgentHealth(
    val agentName: String,
    val metabolicStress: Double = 0.0, // 0.0 to 1.0
    val failureCount: Int = 0,
    val isQuarantined: Boolean = false,
    val status: AgentStatus = AgentStatus.IDLE,
    val lastActiveTimestamp: Long = System.currentTimeMillis()
)
