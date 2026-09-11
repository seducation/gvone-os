package com.example.agent.core

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * High-level execution state of the Agent Runtime state machine.
 */
enum class RuntimeExecutionState {
    IDLE,
    INPUT,
    ROUTING,
    EXECUTING,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELLING,
    CANCELLED
}

/**
 * Result of a multi-agent orchestrated execution.
 */
data class MultiAgentExecutionResult(
    val executionId: String,
    val success: Boolean,
    val outputs: Map<String, AgentResult>,
    val finalSynthesis: String,
    val durationMs: Long
)

/**
 * GVONE OS Agent Runtime.
 * Manages the agent lifecycle state machine, sequential execution, parallel fan-out, and agent handoffs.
 */
class AgentRuntime(
    private val executor: AgentExecutor = AgentExecutor.global
) {
    private val _state = MutableStateFlow(RuntimeExecutionState.IDLE)
    val state: StateFlow<RuntimeExecutionState> = _state.asStateFlow()

    private val _activeExecutions = MutableStateFlow<Map<String, String>>(emptyMap())
    val activeExecutions: StateFlow<Map<String, String>> = _activeExecutions.asStateFlow()

    fun transitionState(newState: RuntimeExecutionState) {
        _state.value = newState
    }

    /**
     * Executes agents sequentially: Agent A -> Agent B -> Agent C.
     * Preserves context and passes previous step's output to the next agent.
     */
    suspend fun executeSequential(
        agents: List<Agent>,
        initialGoal: String,
        cancellationToken: CancellationToken = CancellationToken()
    ): MultiAgentExecutionResult {
        val executionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        transitionState(RuntimeExecutionState.EXECUTING)

        val results = mutableMapOf<String, AgentResult>()
        var currentContextData: Any? = initialGoal

        for ((index, agent) in agents.withIndex()) {
            if (cancellationToken.isCancelled) {
                transitionState(RuntimeExecutionState.CANCELLED)
                return MultiAgentExecutionResult(
                    executionId = executionId,
                    success = false,
                    outputs = results,
                    finalSynthesis = "Multi-agent execution cancelled",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            val request = AgentRequest(
                sourceAgent = if (index == 0) "User" else agents[index - 1].identity(),
                targetAgent = agent.identity(),
                action = "step_${index + 1}",
                parameters = mapOf(
                    "goal" to initialGoal,
                    "previousOutput" to currentContextData
                ),
                context = mapOf("executionId" to executionId, "stepIndex" to index)
            )

            val res = executor.execute(agent, request, cancellationToken)
            results[agent.identity()] = res

            if (!res.isSuccess) {
                transitionState(RuntimeExecutionState.FAILED)
                return MultiAgentExecutionResult(
                    executionId = executionId,
                    success = false,
                    outputs = results,
                    finalSynthesis = "Sequential execution failed at ${agent.identity()}: ${res.error}",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            currentContextData = res.data
        }

        transitionState(RuntimeExecutionState.COMPLETED)
        return MultiAgentExecutionResult(
            executionId = executionId,
            success = true,
            outputs = results,
            finalSynthesis = currentContextData?.toString() ?: "All ${agents.size} agents executed successfully.",
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Executes multiple agents in parallel (Fan-out) and aggregates results (Fan-in).
     */
    suspend fun executeParallel(
        agents: List<Agent>,
        goal: String,
        cancellationToken: CancellationToken = CancellationToken()
    ): MultiAgentExecutionResult = coroutineScope {
        val executionId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        transitionState(RuntimeExecutionState.EXECUTING)

        val deferredResults = agents.map { agent ->
            async {
                val req = AgentRequest(
                    sourceAgent = "AgentRuntime",
                    targetAgent = agent.identity(),
                    action = "parallel_task",
                    parameters = mapOf("goal" to goal),
                    context = mapOf("executionId" to executionId)
                )
                agent.identity() to executor.execute(agent, req, cancellationToken)
            }
        }

        val resultsMap = deferredResults.map { it.await() }.toMap()
        val allSuccessful = resultsMap.values.all { it.isSuccess }

        val synthesis = buildString {
            appendLine("Parallel Execution Results:")
            resultsMap.forEach { (agentName, res) ->
                appendLine("• $agentName: ${if (res.isSuccess) res.data else "ERROR: ${res.error}"}")
            }
        }.trim()

        transitionState(if (allSuccessful) RuntimeExecutionState.COMPLETED else RuntimeExecutionState.FAILED)
        MultiAgentExecutionResult(
            executionId = executionId,
            success = allSuccessful,
            outputs = resultsMap,
            finalSynthesis = synthesis,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Executes an explicit handoff from one agent to another with strict context preservation.
     */
    suspend fun executeHandoff(
        fromAgent: Agent,
        toAgent: Agent,
        handoffData: Map<String, Any?>,
        cancellationToken: CancellationToken = CancellationToken()
    ): AgentResult {
        transitionState(RuntimeExecutionState.EXECUTING)
        val req = AgentRequest(
            sourceAgent = fromAgent.identity(),
            targetAgent = toAgent.identity(),
            action = "handoff",
            parameters = handoffData,
            context = mapOf("handoffFrom" to fromAgent.identity())
        )
        val result = executor.execute(toAgent, req, cancellationToken)
        transitionState(if (result.isSuccess) RuntimeExecutionState.COMPLETED else RuntimeExecutionState.FAILED)
        return result
    }

    companion object {
        val global: AgentRuntime by lazy { AgentRuntime() }
    }
}
