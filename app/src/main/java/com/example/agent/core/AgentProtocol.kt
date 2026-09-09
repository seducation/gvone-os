package com.example.agent.core

import java.util.UUID

/**
 * Execution status of an agent or task.
 */
enum class AgentStatus(val displayName: String) {
    IDLE("Idle"),
    PLANNING("Planning"),
    EXECUTING("Executing"),
    PAUSED("Paused"),
    WAITING("Waiting"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

/**
 * Declares a capability provided by an agent.
 */
data class AgentCapability(
    val name: String,
    val description: String,
    val supportedActions: List<String>,
    val requiresPermission: Boolean = false,
    val riskLevel: RiskLevel = RiskLevel.LOW
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
