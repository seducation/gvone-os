package com.example.agent.nodal

import com.example.agent.registry.AgentRegistry
import java.util.UUID

/**
 * Structured step in a planner execution plan.
 */
data class PlannedStep(
    val stepId: String = UUID.randomUUID().toString(),
    val stepNumber: Int,
    val description: String,
    val requiredCapability: String,
    val assignedAgent: String,
    val toolName: String? = null,
    val verifyCondition: String = "result_success"
)

/**
 * High-level plan formulated by PlannerEngine.
 */
data class ExecutionPlan(
    val planId: String = UUID.randomUUID().toString(),
    val goal: String,
    val steps: List<PlannedStep>,
    val workflow: NodalWorkflow
)

/**
 * PlannerEngine (Separation of Concerns: WHAT to do).
 * Formulates execution plans and compiles them into NodalWorkflow execution graphs.
 * The execution itself is performed strictly by NodalEngine (HOW to execute).
 * Does NOT contain hardcoded string heuristics; resolves agents via dynamic capabilities and scorecards.
 */
class PlannerEngine(
    private val agentRegistry: AgentRegistry = AgentRegistry.global
) {
    /**
     * Decomposes a user goal into an ExecutionPlan and compiles it into an executable NodalWorkflow.
     */
    fun createPlan(goal: String, preferredAgent: String? = null): ExecutionPlan {
        val planId = "plan_${System.currentTimeMillis()}"
        val steps = mutableListOf<PlannedStep>()

        val primaryAgent = if (!preferredAgent.isNullOrBlank()) {
            agentRegistry.getAgent(preferredAgent) ?: agentRegistry.findBestAgentForGoal(goal)
        } else {
            agentRegistry.findBestAgentForGoal(goal)
        }
        val agentName = primaryAgent?.identity() ?: "BrowserAgent"

        val step1 = PlannedStep(
            stepNumber = 1,
            description = "Execute operation for goal: $goal",
            requiredCapability = primaryAgent?.capabilities()?.firstOrNull()?.name ?: "general_task",
            assignedAgent = agentName,
            toolName = primaryAgent?.supportedTools?.firstOrNull(),
            verifyCondition = "result_success"
        )
        steps.add(step1)

        // Compile steps into a deterministic NodalWorkflow graph
        val nodes = mutableListOf<NodalNode>()
        val connections = mutableListOf<NodeConnection>()

        val triggerNode = NodalNode(
            id = "${planId}_trigger",
            type = NodeType.TRIGGER,
            name = "Goal Trigger",
            config = mapOf("goal" to goal)
        )
        nodes.add(triggerNode)

        val agentNode = NodalNode(
            id = "${planId}_agent",
            type = NodeType.AGENT,
            name = "Execute: $agentName",
            config = mapOf(
                "agent" to agentName,
                "action" to "execute_task",
                "goal" to goal
            )
        )
        nodes.add(agentNode)
        connections.add(NodeConnection(triggerNode.id, agentNode.id))

        val observerNode = NodalNode(
            id = "${planId}_observer",
            type = NodeType.OBSERVER,
            name = "Observe Outcome State",
            config = mapOf("target" to "state")
        )
        nodes.add(observerNode)
        connections.add(NodeConnection(agentNode.id, observerNode.id))

        val evaluatorNode = NodalNode(
            id = "${planId}_evaluator",
            type = NodeType.EVALUATOR,
            name = "Verify Outcome Quality",
            config = mapOf("verify" to "result_success")
        )
        nodes.add(evaluatorNode)
        connections.add(NodeConnection(observerNode.id, evaluatorNode.id))

        val resultNode = NodalNode(
            id = "${planId}_result",
            type = NodeType.RESULT,
            name = "Synthesize Final Result",
            config = mapOf("format" to "summary")
        )
        nodes.add(resultNode)
        connections.add(NodeConnection(evaluatorNode.id, resultNode.id))

        val workflow = NodalWorkflow(
            id = planId,
            name = "Dynamic Plan for '$goal'",
            description = "Compiled by PlannerEngine for agent $agentName",
            nodes = nodes,
            connections = connections,
            isBuiltIn = false
        )

        return ExecutionPlan(
            planId = planId,
            goal = goal,
            steps = steps,
            workflow = workflow
        )
    }

    companion object {
        val global: PlannerEngine by lazy { PlannerEngine() }
    }
}
