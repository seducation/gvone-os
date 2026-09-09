package com.example.agent.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * StepLogger manages execution step transparency across all agents in GVONE OS.
 * Every action taken by any agent must be logged here.
 */
class StepLogger {
    private val nextId = AtomicLong(1)
    private val _steps = MutableStateFlow<List<AgentStep>>(emptyList())
    val steps: StateFlow<List<AgentStep>> = _steps.asStateFlow()

    fun startStep(
        agentName: String,
        action: StepType,
        target: String,
        metadata: Map<String, Any?>? = null
    ): AgentStep {
        val step = AgentStep(
            stepId = nextId.getAndIncrement(),
            agentName = agentName,
            action = action,
            target = target,
            status = StepStatus.RUNNING,
            timestamp = System.currentTimeMillis(),
            metadata = metadata
        )
        synchronized(this) {
            _steps.value = _steps.value + step
        }
        return step
    }

    fun completeStep(
        stepId: Long,
        durationMs: Long,
        metadata: Map<String, Any?>? = null
    ) {
        synchronized(this) {
            _steps.value = _steps.value.map { step ->
                if (step.stepId == stepId) {
                    val mergedMetadata = (step.metadata ?: emptyMap()) + (metadata ?: emptyMap())
                    step.copy(
                        status = StepStatus.SUCCESS,
                        durationMs = durationMs,
                        metadata = mergedMetadata
                    )
                } else step
            }
        }
    }

    fun failStep(
        stepId: Long,
        errorMessage: String,
        durationMs: Long
    ) {
        synchronized(this) {
            _steps.value = _steps.value.map { step ->
                if (step.stepId == stepId) {
                    step.copy(
                        status = StepStatus.FAILED,
                        durationMs = durationMs,
                        errorMessage = errorMessage
                    )
                } else step
            }
        }
    }

    fun logInstant(
        agentName: String,
        action: StepType,
        target: String,
        status: StepStatus,
        metadata: Map<String, Any?>? = null,
        errorMessage: String? = null
    ): AgentStep {
        val step = AgentStep(
            stepId = nextId.getAndIncrement(),
            agentName = agentName,
            action = action,
            target = target,
            status = status,
            timestamp = System.currentTimeMillis(),
            metadata = metadata,
            errorMessage = errorMessage
        )
        synchronized(this) {
            _steps.value = _steps.value + step
        }
        return step
    }

    fun getStepsForAgent(agentName: String): List<AgentStep> {
        return _steps.value.filter { it.agentName == agentName }
    }

    fun clear() {
        synchronized(this) {
            _steps.value = emptyList()
        }
    }

    companion object {
        val global = StepLogger()
    }
}
