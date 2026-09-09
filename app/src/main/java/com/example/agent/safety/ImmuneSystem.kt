package com.example.agent.safety

import com.example.agent.core.AgentBase
import com.example.agent.core.StepLogger
import com.example.agent.core.StepStatus
import com.example.agent.core.StepType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ImmuneStatus(
    val inflammationScore: Double, // 0.0 to 1.0
    val isFeverMode: Boolean,
    val quarantinedAgents: Set<String>,
    val anomalyCount: Int
)

/**
 * GVONE Immune System defends against anomalies, recurring agent failures,
 * and malicious behaviors. Activates "Fever Mode" to throttle execution when
 * systemic failure rate spikes.
 */
class ImmuneSystem(
    private val logger: StepLogger = StepLogger.global
) {
    private val _status = MutableStateFlow(
        ImmuneStatus(
            inflammationScore = 0.0,
            isFeverMode = false,
            quarantinedAgents = emptySet(),
            anomalyCount = 0
        )
    )
    val status: StateFlow<ImmuneStatus> = _status.asStateFlow()

    private val agentFailureHistory = mutableMapOf<String, Int>()

    /**
     * Report an anomaly or failure from an agent.
     */
    fun recordFailure(agent: AgentBase, reason: String) {
        val count = (agentFailureHistory[agent.name] ?: 0) + 1
        agentFailureHistory[agent.name] = count

        val current = _status.value
        val newScore = (current.inflammationScore + 0.25).coerceAtMost(1.0)
        val shouldTriggerFever = newScore >= 0.7

        val quarantined = current.quarantinedAgents.toMutableSet()
        // If an agent fails 3 times consecutively, quarantine it
        if (count >= 3) {
            quarantined.add(agent.name)
            agent.quarantine("Repeated failures ($count) triggered immune quarantine.")
        }

        _status.value = ImmuneStatus(
            inflammationScore = newScore,
            isFeverMode = shouldTriggerFever,
            quarantinedAgents = quarantined,
            anomalyCount = current.anomalyCount + 1
        )

        logger.logInstant(
            agentName = "ImmuneSystem",
            action = StepType.ANALYZE,
            target = "Recorded failure for ${agent.name}. Inflammation: $newScore, FeverMode: $shouldTriggerFever",
            status = if (shouldTriggerFever) StepStatus.FAILED else StepStatus.PENDING,
            metadata = mapOf("agent" to agent.name, "reason" to reason, "consecutiveFailures" to count)
        )
    }

    /**
     * Recovers inflammation when healthy actions complete.
     */
    fun recordSuccess(agentName: String) {
        agentFailureHistory[agentName] = 0
        val current = _status.value
        val newScore = (current.inflammationScore - 0.1).coerceAtLeast(0.0)
        val fever = newScore >= 0.7

        _status.value = current.copy(
            inflammationScore = newScore,
            isFeverMode = fever
        )
    }

    fun releaseQuarantine(agent: AgentBase) {
        agent.liftQuarantine()
        agentFailureHistory[agent.name] = 0
        val current = _status.value
        val updated = current.quarantinedAgents.toMutableSet().apply { remove(agent.name) }
        _status.value = current.copy(quarantinedAgents = updated)
    }

    fun resetImmuneState() {
        agentFailureHistory.clear()
        _status.value = ImmuneStatus(
            inflammationScore = 0.0,
            isFeverMode = false,
            quarantinedAgents = emptySet(),
            anomalyCount = 0
        )
    }

    companion object {
        val global = ImmuneSystem()
    }
}
