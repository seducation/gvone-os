package com.example.agent.core

/**
 * Status of an individual agent step execution.
 */
enum class StepStatus(val displayName: String, val icon: String) {
    PENDING("Pending", "◌"),
    RUNNING("Running", "→"),
    SUCCESS("Success", "✓"),
    FAILED("Failed", "✗"),
    SKIPPED("Skipped", "○")
}

/**
 * Single immutable source of truth for an agent step.
 * Real actions logged by system code rather than speculative AI hallucination.
 */
data class AgentStep(
    val stepId: Long,
    val agentName: String,
    val action: StepType,
    val target: String,
    val status: StepStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L,
    val metadata: Map<String, Any?>? = null,
    val errorMessage: String? = null
)
