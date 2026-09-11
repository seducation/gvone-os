package com.example.agent.nodal

import com.example.agent.core.AgentOrchestrator
import com.example.agent.core.AgentRequest
import com.example.agent.core.AgentResult
import com.example.agent.core.AgentStatus
import com.example.agent.core.StepLogger
import com.example.agent.core.StepType
import com.example.agent.core.ToolRegistry
import com.example.agent.cns.CentralNervousSystem
import com.example.agent.runtime.InteractionType
import com.example.agent.runtime.ModePriority
import com.example.agent.runtime.RuntimeStateManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Nodal Workflow Engine (n8n-style) for GVONE OS.
 * Enables visual/nodal execution of all system workflows and commands.
 */
class NodalEngine(
    private val runtimeState: RuntimeStateManager = RuntimeStateManager.global,
    private val logger: StepLogger = StepLogger.global,
    private val orchestrator: AgentOrchestrator? = null,
    customAgentDispatcher: (suspend (agentName: String, request: AgentRequest) -> AgentResult)? = null
) {
    private var agentDispatcher: (suspend (agentName: String, request: AgentRequest) -> AgentResult)? = customAgentDispatcher

    fun setAgentDispatcher(dispatcher: suspend (agentName: String, request: AgentRequest) -> AgentResult) {
        this.agentDispatcher = dispatcher
    }

    private suspend fun dispatchAgent(agentName: String, request: AgentRequest): AgentResult {
        val custom = agentDispatcher
        if (custom != null) {
            return custom(agentName, request)
        }
        val orch = orchestrator ?: CentralNervousSystem.global
        return try {
            orch.dispatchToAgent(agentName, request)
        } catch (e: Exception) {
            AgentResult(request.requestId, AgentStatus.FAILED, error = "Failed to dispatch to $agentName: ${e.message}")
        }
    }

    private val _workflows = MutableStateFlow<Map<String, NodalWorkflow>>(emptyMap())
    val workflows: StateFlow<Map<String, NodalWorkflow>> = _workflows.asStateFlow()

    init {
        registerBuiltInWorkflows()
    }

    fun registerWorkflow(workflow: NodalWorkflow) {
        val map = _workflows.value.toMutableMap()
        map[workflow.id] = workflow
        _workflows.value = map
    }

    fun unregisterWorkflow(id: String) {
        val map = _workflows.value.toMutableMap()
        map.remove(id)
        _workflows.value = map
    }

    fun getWorkflow(id: String): NodalWorkflow? = _workflows.value[id]

    fun findWorkflowForCommand(commandToken: String): NodalWorkflow? {
        val clean = if (commandToken.startsWith("/")) commandToken.lowercase() else "/${commandToken.lowercase()}"
        return _workflows.value.values.find { wf ->
            wf.isEnabled && wf.triggerCommands.any { it.equals(clean, ignoreCase = true) }
        }
    }

    /**
     * Executes a nodal workflow graph step-by-step from triggers to result.
     */
    suspend fun executeWorkflow(
        workflow: NodalWorkflow,
        command: String,
        queryArg: String,
        rawInput: String,
        initialVariables: Map<String, Any?> = emptyMap()
    ): NodalWorkflowResult {
        val startTime = System.currentTimeMillis()
        val context = NodalExecutionContext(
            workflowId = workflow.id,
            rawInput = rawInput,
            command = command,
            queryArg = queryArg,
            variables = initialVariables.toMutableMap()
        )

        val triggerNodes = workflow.getTriggerNodes().ifEmpty {
            workflow.nodes.take(1)
        }

        if (triggerNodes.isEmpty()) {
            return NodalWorkflowResult(
                workflowId = workflow.id,
                success = false,
                error = "Workflow has no executable nodes",
                durationMs = System.currentTimeMillis() - startTime
            )
        }

        // Execute nodes via BFS / topological order along connections
        val executedNodeIds = mutableSetOf<String>()
        val queue = ArrayDeque<NodalNode>()
        triggerNodes.forEach { queue.add(it) }

        while (queue.isNotEmpty() && !context.isCancelled) {
            val currentNode = queue.removeFirst()
            if (executedNodeIds.contains(currentNode.id)) continue

            val nodeStart = System.currentTimeMillis()
            var nodeError: String? = null
            var nodeOutputSummary = ""

            try {
                when (currentNode.type) {
                    NodeType.TRIGGER, NodeType.TRIGGER_COMMAND -> {
                        context.setVariable("command", command)
                        context.setVariable("query", queryArg)
                        context.setVariable("input", rawInput)
                        nodeOutputSummary = "Triggered: $command with arg '$queryArg'"
                    }

                    NodeType.COMMAND -> {
                        context.setVariable("command", command)
                        context.setVariable("query", queryArg)
                        nodeOutputSummary = "Command executed: $command"
                    }

                    NodeType.VOICE_NODE -> {
                        val action = currentNode.config["action"]?.toString() ?: "enable_voice"
                        val isPrimary = currentNode.config["isPrimary"] as? Boolean ?: true
                        if (action == "enable_voice") {
                            runtimeState.activateVoiceOnly()
                            nodeOutputSummary = "Voice interaction mode enabled (priority=${if (isPrimary) "PRIMARY" else "SECONDARY"})"
                        } else if (action == "disable_voice") {
                            runtimeState.resetToTextChat()
                            nodeOutputSummary = "Voice interaction disabled"
                        }
                    }

                    NodeType.INTENT, NodeType.INTENT_ROUTER -> {
                        val goal = queryArg.ifBlank { rawInput }
                        context.setVariable("goal", goal)
                        // Dynamic capability + scorecard based routing without hardcoded strings
                        val bestAgent = com.example.agent.registry.AgentRegistry.global.findBestAgentForGoal(goal)
                        val targetAgent = bestAgent?.identity() ?: "BrowserAgent"
                        context.setVariable("targetAgent", targetAgent)
                        nodeOutputSummary = "Routed goal '$goal' to agent $targetAgent based on capability match"
                    }

                    NodeType.PLANNER -> {
                        val goal = context.getVariable("goal")?.toString() ?: queryArg.ifBlank { rawInput }
                        val plan = PlannerEngine.global.createPlan(goal)
                        context.setVariable("plan", plan.planId)
                        context.setVariable("targetAgent", plan.steps.firstOrNull()?.assignedAgent ?: "BrowserAgent")
                        nodeOutputSummary = "Formulated plan with ${plan.steps.size} steps for goal '$goal'"
                    }

                    NodeType.AGENT, NodeType.AGENT_NODE -> {
                        var targetAgent = currentNode.config["agent"]?.toString()
                            ?: context.getVariable("targetAgent")?.toString()
                            ?: "BrowserAgent"
                        val action = currentNode.config["action"]?.toString() ?: "execute_task"
                        val goal = context.getVariable("goal")?.toString() ?: queryArg

                        runtimeState.logDebug("NodalEngine executing AGENT_NODE [$targetAgent] action=$action goal='$goal'")

                        val request = AgentRequest(
                            sourceAgent = "NodalEngine",
                            targetAgent = targetAgent,
                            action = action,
                            parameters = mapOf("goal" to goal, "query" to queryArg)
                        )
                        var result = dispatchAgent(targetAgent, request)

                        // Fallback handling if configured or on failure
                        if (!result.isSuccess && currentNode.config.containsKey("fallbackAgent")) {
                            val fallbackAgent = currentNode.config["fallbackAgent"].toString()
                            runtimeState.logDebug("Primary agent $targetAgent failed. Executing fallback agent $fallbackAgent")
                            val fallbackReq = request.copy(targetAgent = fallbackAgent)
                            result = dispatchAgent(fallbackAgent, fallbackReq)
                            targetAgent = fallbackAgent
                        }

                        context.setVariable("agentResult", result.data)
                        if (!result.isSuccess) {
                            nodeError = result.error ?: "Agent execution error"
                        }
                        nodeOutputSummary = "Agent $targetAgent executed: ${result.data?.toString()?.take(80)}"
                    }

                    NodeType.TOOL, NodeType.TOOL_NODE -> {
                        val tool = currentNode.config["tool"]?.toString() ?: "generic_tool"
                        @Suppress("UNCHECKED_CAST")
                        val params = (currentNode.config["params"] as? Map<String, Any?>) ?: emptyMap()
                        val callingAgent = context.getVariable("targetAgent")?.toString() ?: "NodalEngine"

                        val toolRes = ToolRegistry.global.executeMediated(tool, params, callingAgent)
                        if (toolRes.success) {
                            context.setVariable("lastToolOutput", toolRes.data?.toString() ?: "Success")
                            nodeOutputSummary = "Executed tool $tool successfully"
                        } else {
                            nodeError = toolRes.error ?: "Tool $tool failed"
                            nodeOutputSummary = "Tool $tool failed: $nodeError"
                        }
                    }

                    NodeType.OBSERVER, NodeType.OBSERVER_NODE -> {
                        val target = currentNode.config["target"]?.toString() ?: "dom"
                        val obsData: String = when (target.lowercase()) {
                            "dom", "browser", "page" -> {
                                val req = AgentRequest(
                                    sourceAgent = "NodalEngine",
                                    targetAgent = "BrowserAgent",
                                    action = "observe_dom"
                                )
                                val res = dispatchAgent("BrowserAgent", req)
                                if (res.isSuccess) {
                                    res.data?.toString() ?: "DOM observation: OK"
                                } else {
                                    "DOM observation unavailable: ${res.error}"
                                }
                            }
                            "tabs" -> {
                                val req = AgentRequest(
                                    sourceAgent = "NodalEngine",
                                    targetAgent = "BrowserAgent",
                                    action = "get_tabs"
                                )
                                val res = dispatchAgent("BrowserAgent", req)
                                res.data?.toString() ?: "Tabs: none"
                            }
                            "state", "runtime" -> {
                                "Mode=${runtimeState.runtimeMode.value.toPromptLabel()}, ActiveTask=${runtimeState.activeTask.value?.taskId ?: "None"}"
                            }
                            else -> "Observed $target"
                        }
                        context.setVariable("observation", obsData)
                        nodeOutputSummary = "Observed state for $target: ${obsData.take(60)}"
                    }

                    NodeType.EVALUATOR, NodeType.EVALUATOR_NODE -> {
                        val condition = currentNode.config["verify"]?.toString() ?: "result_success"
                        val agentResult = context.getVariable("agentResult")
                        val observation = context.getVariable("observation")?.toString() ?: ""
                        val lastToolOutput = context.getVariable("lastToolOutput")?.toString() ?: ""
                        val lastOutput = context.getVariable("lastOutput")?.toString() ?: ""
                        val combined = "${agentResult ?: ""} $observation $lastToolOutput $lastOutput".trim()

                        val isVerified = when {
                            condition == "result_success" -> {
                                (agentResult != null && !context.variables.containsKey("error")) ||
                                        (observation.isNotBlank() && !observation.startsWith("DOM observation unavailable")) ||
                                        lastToolOutput.isNotBlank() ||
                                        lastOutput.isNotBlank()
                            }
                            condition.startsWith("contains:") -> {
                                val expected = condition.removePrefix("contains:").trim()
                                combined.contains(expected, ignoreCase = true)
                            }
                            condition.startsWith("equals:") -> {
                                val expected = condition.removePrefix("equals:").trim()
                                combined.equals(expected, ignoreCase = true) || agentResult?.toString()?.equals(expected, ignoreCase = true) == true
                            }
                            condition.startsWith("file_exists:") -> {
                                val filePath = condition.removePrefix("file_exists:").trim()
                                File(filePath).exists()
                            }
                            condition.startsWith("file_contains:") -> {
                                val parts = condition.removePrefix("file_contains:").split("=", limit = 2)
                                if (parts.size == 2) {
                                    val f = File(parts[0].trim())
                                    f.exists() && f.readText().contains(parts[1].trim(), ignoreCase = true)
                                } else false
                            }
                            condition == "non_empty" -> {
                                combined.isNotEmpty() && !combined.equals("null", ignoreCase = true)
                            }
                            condition == "always_true" -> true
                            else -> agentResult != null || lastToolOutput.isNotBlank()
                        }

                        context.setVariable("isVerified", isVerified)
                        if (!isVerified) {
                            nodeError = "Verification failed: required outcome '$condition' was not satisfied."
                        }
                        nodeOutputSummary = "Outcome verification '$condition': ${if (isVerified) "PASSED" else "FAILED"}"
                    }

                    NodeType.APPROVAL -> {
                        val permission = currentNode.config["permission"]?.toString() ?: "general.execute"
                        val hasPerm = com.example.agent.safety.PermissionSystem.global.hasPermission(permission)
                        if (!hasPerm) {
                            nodeError = "User approval required for permission '$permission'"
                        }
                        nodeOutputSummary = "Approval check for $permission: ${if (hasPerm) "APPROVED" else "PENDING"}"
                    }

                    NodeType.WAIT -> {
                        val duration = (currentNode.config["durationMs"] as? Number)?.toLong() ?: 50L
                        kotlinx.coroutines.delay(duration)
                        nodeOutputSummary = "Waited ${duration}ms"
                    }

                    NodeType.RETRY -> {
                        val maxRetries = (currentNode.config["maxRetries"] as? Number)?.toInt() ?: 3
                        val targetNodeId = currentNode.config["targetNodeId"]?.toString()
                        var retryCount = 0
                        var succeeded = false

                        if (targetNodeId != null) {
                            val targetNode = workflow.getNode(targetNodeId)
                            if (targetNode != null) {
                                while (retryCount < maxRetries && !succeeded) {
                                    retryCount++
                                    try {
                                        val agent = targetNode.config["agent"]?.toString()
                                            ?: context.getVariable("targetAgent")?.toString() ?: "BrowserAgent"
                                        val req = AgentRequest(
                                            sourceAgent = "RetryNode",
                                            targetAgent = agent,
                                            action = targetNode.config["action"]?.toString() ?: "execute_task"
                                        )
                                        val res = dispatchAgent(agent, req)
                                        if (res.isSuccess) {
                                            succeeded = true
                                            context.setVariable("agentResult", res.data)
                                            context.setVariable("lastOutput", res.data)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                        context.setVariable("retryAttempts", retryCount)
                        context.setVariable("retrySuccess", succeeded)
                        nodeOutputSummary = "Retry checkpoint reached (attempts=$retryCount, success=$succeeded)"
                    }

                    NodeType.FALLBACK -> {
                        val fallbackAgent = currentNode.config["fallbackAgent"]?.toString()
                        val fallbackTool = currentNode.config["fallbackTool"]?.toString()
                        val fallbackValue = currentNode.config["fallbackValue"]?.toString() ?: "Fallback execution succeeded"

                        var fallbackResult: Any? = fallbackValue
                        if (fallbackAgent != null) {
                            val req = AgentRequest(
                                sourceAgent = "FallbackNode",
                                targetAgent = fallbackAgent,
                                action = currentNode.config["action"]?.toString() ?: "execute_task",
                                parameters = mapOf("goal" to (context.getVariable("goal")?.toString() ?: queryArg))
                            )
                            val res = dispatchAgent(fallbackAgent, req)
                            fallbackResult = res.data ?: fallbackValue
                        } else if (fallbackTool != null) {
                            val tRes = ToolRegistry.global.executeMediated(fallbackTool, emptyMap(), "FallbackNode")
                            fallbackResult = tRes.data ?: fallbackValue
                        }

                        context.setVariable("fallbackResult", fallbackResult)
                        context.setVariable("agentResult", fallbackResult)
                        context.setVariable("lastOutput", fallbackResult)
                        nodeOutputSummary = "Fallback handler invoked: ${fallbackResult.toString().take(60)}"
                    }

                    NodeType.PARALLEL -> {
                        val actions = (currentNode.config["actions"] as? List<*>) ?: emptyList<Any>()
                        val parallelOutputs = mutableListOf<Any?>()

                        if (actions.isNotEmpty()) {
                            coroutineScope {
                                val deferreds = actions.map { actionItem ->
                                    async {
                                        if (actionItem is Map<*, *>) {
                                            val tool = actionItem["tool"]?.toString()
                                            val agent = actionItem["agent"]?.toString()
                                            if (tool != null) {
                                                @Suppress("UNCHECKED_CAST")
                                                val params = (actionItem["params"] as? Map<String, Any?>) ?: emptyMap()
                                                val tRes = ToolRegistry.global.executeMediated(tool, params, "ParallelNode")
                                                tRes.data ?: if (tRes.success) "Success" else "Failed: ${tRes.error}"
                                            } else if (agent != null) {
                                                val req = AgentRequest(
                                                    sourceAgent = "ParallelNode",
                                                    targetAgent = agent,
                                                    action = actionItem["action"]?.toString() ?: "execute_task",
                                                    parameters = (actionItem["parameters"] as? Map<String, Any?>) ?: emptyMap()
                                                )
                                                dispatchAgent(agent, req).data
                                            } else {
                                                actionItem.toString()
                                            }
                                        } else {
                                            actionItem.toString()
                                        }
                                    }
                                }
                                parallelOutputs.addAll(deferreds.awaitAll())
                            }
                        } else {
                            val outgoingConns = workflow.getOutgoingConnections(currentNode.id)
                            if (outgoingConns.isNotEmpty()) {
                                coroutineScope {
                                    val branchJobs = outgoingConns.map { conn ->
                                        async {
                                            val targetNode = workflow.getNode(conn.toNodeId)
                                            targetNode?.name ?: conn.toNodeId
                                        }
                                    }
                                    parallelOutputs.addAll(branchJobs.awaitAll())
                                }
                            }
                        }

                        context.setVariable("parallelResults", parallelOutputs)
                        context.setVariable("lastOutput", parallelOutputs)
                        nodeOutputSummary = "Parallel fork evaluated with ${parallelOutputs.size} branches"
                    }

                    NodeType.SEQUENCE -> {
                        val steps = (currentNode.config["steps"] as? List<*>) ?: emptyList<Any>()
                        val seqResults = mutableListOf<Any?>()
                        for (step in steps) {
                            if (step is Map<*, *>) {
                                val tool = step["tool"]?.toString()
                                if (tool != null) {
                                    @Suppress("UNCHECKED_CAST")
                                    val params = (step["params"] as? Map<String, Any?>) ?: emptyMap()
                                    val r = ToolRegistry.global.executeMediated(tool, params, "SequenceNode")
                                    seqResults.add(r.data ?: if (r.success) "Success" else "Failed")
                                } else {
                                    seqResults.add(step.toString())
                                }
                            } else {
                                seqResults.add(step.toString())
                            }
                        }
                        context.setVariable("sequenceResults", seqResults)
                        context.setVariable("lastOutput", seqResults)
                        nodeOutputSummary = "Sequence segment evaluated with ${steps.size} steps"
                    }

                    NodeType.LOOP -> {
                        val maxIterations = ((currentNode.config["maxIterations"] as? Number)?.toInt() ?: 50).coerceAtMost(50)
                        val configuredIterations = (currentNode.config["iterations"] as? Number)?.toInt()
                        val itemsList = (currentNode.config["items"] as? List<*>)
                            ?: (context.getVariable("loopItems") as? List<*>)
                            ?: (context.getVariable("parallelResults") as? List<*>)

                        val totalToRun = (itemsList?.size ?: configuredIterations ?: 3).coerceAtMost(maxIterations)
                        val loopResults = mutableListOf<Any?>()
                        val subTool = currentNode.config["tool"]?.toString()
                        val subAction = currentNode.config["action"]?.toString()

                        for (i in 0 until totalToRun) {
                            val currentItem = itemsList?.getOrNull(i) ?: i
                            context.setVariable("loopIndex", i)
                            context.setVariable("loopItem", currentItem)

                            if (subTool != null) {
                                val toolRes = ToolRegistry.global.executeMediated(
                                    subTool,
                                    mapOf("item" to currentItem, "index" to i),
                                    "LoopNode"
                                )
                                loopResults.add(toolRes.data ?: if (toolRes.success) "Success" else "Error")
                            } else if (subAction != null) {
                                loopResults.add("Processed item $i: $currentItem")
                            } else {
                                loopResults.add(currentItem)
                            }
                        }

                        context.setVariable("loopResults", loopResults)
                        context.setVariable("lastOutput", loopResults)
                        nodeOutputSummary = "Loop completed $totalToRun iterations (capped at $maxIterations)"
                    }

                    NodeType.RESULT, NodeType.RESULT_NODE -> {
                        val synthesis = context.getVariable("finalResult")?.toString()
                            ?: context.getVariable("agentResult")?.toString()
                            ?: context.getVariable("lastToolOutput")?.toString()
                            ?: context.getVariable("lastOutput")?.toString()
                            ?: "Workflow '${workflow.name}' completed successfully."
                        context.setVariable("finalResult", synthesis)
                        nodeOutputSummary = "Synthesis: ${synthesis.take(60)}"
                    }

                    NodeType.CONDITION, NodeType.CONDITION_NODE -> {
                        val variableName = currentNode.config["variable"]?.toString() ?: "isVerified"
                        val operator = currentNode.config["operator"]?.toString() ?: "is_true"
                        val expectedValue = currentNode.config["value"]?.toString()
                        val varValue = context.getVariable(variableName)

                        val conditionPassed = when (operator.lowercase()) {
                            "is_true" -> (varValue as? Boolean) == true || varValue?.toString()?.equals("true", ignoreCase = true) == true
                            "is_false" -> (varValue as? Boolean) == false || varValue?.toString()?.equals("false", ignoreCase = true) == true
                            "equals" -> varValue?.toString() == expectedValue
                            "not_equals" -> varValue?.toString() != expectedValue
                            "contains" -> varValue?.toString()?.contains(expectedValue ?: "", ignoreCase = true) == true
                            "greater_than" -> {
                                val num = varValue?.toString()?.toDoubleOrNull() ?: 0.0
                                val target = expectedValue?.toDoubleOrNull() ?: 0.0
                                num > target
                            }
                            "less_than" -> {
                                val num = varValue?.toString()?.toDoubleOrNull() ?: 0.0
                                val target = expectedValue?.toDoubleOrNull() ?: 0.0
                                num < target
                            }
                            "non_empty" -> varValue != null && varValue.toString().isNotBlank()
                            else -> (varValue as? Boolean) == true || (varValue != null && varValue.toString().isNotBlank())
                        }

                        context.setVariable("conditionResult", conditionPassed)
                        context.setVariable("${currentNode.id}_result", conditionPassed)
                        nodeOutputSummary = "Branch evaluated ($variableName $operator ${expectedValue ?: ""}): $conditionPassed"
                    }
                }
            } catch (e: Exception) {
                nodeError = e.message ?: e.toString()
            }

            val stepLog = NodalStepLog(
                nodeId = currentNode.id,
                nodeName = currentNode.name,
                nodeType = currentNode.type,
                status = if (nodeError == null) "SUCCESS" else "FAILED",
                inputSummary = queryArg,
                outputSummary = nodeOutputSummary,
                error = nodeError,
                durationMs = System.currentTimeMillis() - nodeStart
            )
            context.stepLogs.add(stepLog)
            executedNodeIds.add(currentNode.id)

            if (nodeError != null) {
                // Check if an explicit fallback branch exists
                val fallbackConn = workflow.getOutgoingConnections(currentNode.id).find { conn ->
                    val target = workflow.getNode(conn.toNodeId)
                    target?.type == NodeType.FALLBACK ||
                            conn.outputPort.equals("fallback", ignoreCase = true) ||
                            conn.outputPort.equals("error", ignoreCase = true)
                }
                if (fallbackConn != null) {
                    val fbNode = workflow.getNode(fallbackConn.toNodeId)
                    if (fbNode != null && !executedNodeIds.contains(fbNode.id)) {
                        queue.add(fbNode)
                        continue
                    }
                }
                break
            }

            // Find next connected nodes with condition routing
            val outgoing = workflow.getOutgoingConnections(currentNode.id)
            val nextConnections = if (currentNode.type == NodeType.CONDITION || currentNode.type == NodeType.CONDITION_NODE) {
                val conditionPassed = context.getVariable("conditionResult") as? Boolean ?: true
                val filtered = outgoing.filter { conn ->
                    when (conn.outputPort.lowercase()) {
                        "true", "then" -> conditionPassed
                        "false", "else" -> !conditionPassed
                        else -> true
                    }
                }
                if (filtered.isNotEmpty()) filtered else outgoing
            } else {
                outgoing.filter { it.outputPort != "fallback" && it.outputPort != "error" }
            }

            for (conn in nextConnections) {
                val nextNode = workflow.getNode(conn.toNodeId)
                if (nextNode != null && !executedNodeIds.contains(nextNode.id)) {
                    queue.add(nextNode)
                }
            }
        }

        val lastExecuted = context.stepLogs.lastOrNull()
        val hasFatalError = lastExecuted?.error != null || context.getVariable("isVerified") == false
        return NodalWorkflowResult(
            workflowId = workflow.id,
            success = !hasFatalError && !context.isCancelled,
            finalOutput = context.getVariable("finalResult") ?: context.getVariable("agentResult") ?: context.getVariable("lastOutput"),
            error = if (hasFatalError) lastExecuted?.error ?: "Workflow outcome verification failed" else null,
            logs = context.stepLogs,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Registers out-of-the-box standard nodal workflows.
     * Everything from /voice to /agent, /voice /agent, /agent /voice, and search are modeled nodally.
     */
    private fun registerBuiltInWorkflows() {
        // 1. /voice workflow (interaction only)
        val voiceWf = NodalWorkflow(
            id = "wf_voice_interaction",
            name = "Voice Conversation Mode",
            description = "Enables hands-free voice interaction loop without triggering autonomous tasks",
            triggerCommands = listOf("/voice"),
            nodes = listOf(
                NodalNode("node_trig_voice", NodeType.TRIGGER_COMMAND, "Voice Command Trigger"),
                NodalNode(
                    "node_voice_enable",
                    NodeType.VOICE_NODE,
                    "Activate Voice Loop",
                    mapOf("action" to "enable_voice", "isPrimary" to true)
                ),
                NodalNode("node_voice_result", NodeType.RESULT_NODE, "Voice Mode Confirmation")
            ),
            connections = listOf(
                NodeConnection("node_trig_voice", "node_voice_enable"),
                NodeConnection("node_voice_enable", "node_voice_result")
            ),
            isBuiltIn = true
        )

        // 2. /agent workflow (autonomous task execution)
        val agentWf = NodalWorkflow(
            id = "wf_agent_execution",
            name = "Agentic Task Workflow",
            description = "Plans, dispatches, executes, and observes autonomous multi-step goals",
            triggerCommands = listOf("/agent", "/agentic"),
            nodes = listOf(
                NodalNode("node_trig_agent", NodeType.TRIGGER_COMMAND, "Agent Trigger"),
                NodalNode("node_intent_router", NodeType.INTENT_ROUTER, "Intent & Capability Router"),
                NodalNode("node_agent_dispatch", NodeType.AGENT_NODE, "Specialized Agent Execution"),
                NodalNode("node_observer", NodeType.OBSERVER_NODE, "DOM & State Observer"),
                NodalNode("node_evaluator", NodeType.EVALUATOR_NODE, "Action Verification & Quality Gate"),
                NodalNode("node_result", NodeType.RESULT_NODE, "Executive Task Synthesis")
            ),
            connections = listOf(
                NodeConnection("node_trig_agent", "node_intent_router"),
                NodeConnection("node_intent_router", "node_agent_dispatch"),
                NodeConnection("node_agent_dispatch", "node_observer"),
                NodeConnection("node_observer", "node_evaluator"),
                NodeConnection("node_evaluator", "node_result")
            ),
            isBuiltIn = true
        )

        // 3. /voice /agent workflow (Voice is primary, user speaks goal, agent executes)
        val voiceAgentWf = NodalWorkflow(
            id = "wf_voice_agent_compound",
            name = "Voice-First Agentic Task",
            description = "Voice input streams directly into autonomous agent execution",
            triggerCommands = listOf("/voice /agent", "/voice_agent"),
            nodes = listOf(
                NodalNode("node_trig_va", NodeType.TRIGGER_COMMAND, "Voice-First Agent Trigger"),
                NodalNode(
                    "node_va_voice",
                    NodeType.VOICE_NODE,
                    "Voice Interaction Input",
                    mapOf("action" to "enable_voice", "isPrimary" to true)
                ),
                NodalNode("node_va_router", NodeType.INTENT_ROUTER, "Speech-to-Intent Router"),
                NodalNode("node_va_agent", NodeType.AGENT_NODE, "Autonomous Agent Operator"),
                NodalNode("node_va_result", NodeType.RESULT_NODE, "Spoken Task Completion")
            ),
            connections = listOf(
                NodeConnection("node_trig_va", "node_va_voice"),
                NodeConnection("node_va_voice", "node_va_router"),
                NodeConnection("node_va_router", "node_va_agent"),
                NodeConnection("node_va_agent", "node_va_result")
            ),
            isBuiltIn = true
        )

        // 4. /agent /voice workflow (Agent UI is primary, voice is secondary/accompaniment)
        val agentVoiceWf = NodalWorkflow(
            id = "wf_agent_voice_compound",
            name = "Agent-First Voice Task",
            description = "Agent visual workspace is primary with spoken telemetry feedback",
            triggerCommands = listOf("/agent /voice", "/agent_voice"),
            nodes = listOf(
                NodalNode("node_trig_av", NodeType.TRIGGER_COMMAND, "Agent-First Voice Trigger"),
                NodalNode("node_av_router", NodeType.INTENT_ROUTER, "Visual Intent Router"),
                NodalNode("node_av_agent", NodeType.AGENT_NODE, "Autonomous Agent Operator"),
                NodalNode(
                    "node_av_voice",
                    NodeType.VOICE_NODE,
                    "Voice Audio Telemetry",
                    mapOf("action" to "enable_voice", "isPrimary" to false)
                ),
                NodalNode("node_av_result", NodeType.RESULT_NODE, "Visual & Audio Result")
            ),
            connections = listOf(
                NodeConnection("node_trig_av", "node_av_router"),
                NodeConnection("node_av_router", "node_av_agent"),
                NodeConnection("node_av_agent", "node_av_voice"),
                NodeConnection("node_av_voice", "node_av_result")
            ),
            isBuiltIn = true
        )

        // 5. YouTube Song/Video Search Workflow
        val ytWf = NodalWorkflow(
            id = "wf_youtube_search",
            name = "YouTube Autonomous Search",
            description = "Opens YouTube, inspects search input, types song query, submits, and verifies results",
            triggerCommands = listOf("/yt", "/youtube"),
            nodes = listOf(
                NodalNode("node_trig_yt", NodeType.TRIGGER_COMMAND, "YouTube Trigger"),
                NodalNode(
                    "node_yt_agent",
                    NodeType.AGENT_NODE,
                    "Browser Agent YouTube Search",
                    mapOf("agent" to "BrowserAgent", "action" to "search_youtube")
                ),
                NodalNode("node_yt_observe", NodeType.OBSERVER_NODE, "Observe Video Cards & Player"),
                NodalNode("node_yt_eval", NodeType.EVALUATOR_NODE, "Verify Search Results"),
                NodalNode("node_yt_result", NodeType.RESULT_NODE, "YouTube Ready")
            ),
            connections = listOf(
                NodeConnection("node_trig_yt", "node_yt_agent"),
                NodeConnection("node_yt_agent", "node_yt_observe"),
                NodeConnection("node_yt_observe", "node_yt_eval"),
                NodeConnection("node_yt_eval", "node_yt_result")
            ),
            isBuiltIn = true
        )

        registerWorkflow(voiceWf)
        registerWorkflow(agentWf)
        registerWorkflow(voiceAgentWf)
        registerWorkflow(agentVoiceWf)
        registerWorkflow(ytWf)

        // 6. Search Workflow
        val searchWf = NodalWorkflow(
            id = "wf_search",
            name = "AI Search & Synthesis",
            description = "Queries SearchAgent with AI web synthesis and citations",
            triggerCommands = listOf("/search", "/find"),
            nodes = listOf(
                NodalNode("node_trig_search", NodeType.TRIGGER_COMMAND, "Search Trigger"),
                NodalNode("node_search_agent", NodeType.AGENT_NODE, "AI Search Agent", mapOf("agent" to "SearchAgent", "action" to "search")),
                NodalNode("node_search_eval", NodeType.EVALUATOR_NODE, "Synthesize Citations"),
                NodalNode("node_search_result", NodeType.RESULT_NODE, "Search Output")
            ),
            connections = listOf(
                NodeConnection("node_trig_search", "node_search_agent"),
                NodeConnection("node_search_agent", "node_search_eval"),
                NodeConnection("node_search_eval", "node_search_result")
            ),
            isBuiltIn = true
        )
        registerWorkflow(searchWf)

        // 7. Browser Inspection Workflow
        val browserWf = NodalWorkflow(
            id = "wf_browser_inspect",
            name = "Autonomous Browser Inspector",
            description = "Inspects active tab, DOM hierarchy, and viewport layout",
            triggerCommands = listOf("/browse", "/dom"),
            nodes = listOf(
                NodalNode("node_trig_browse", NodeType.TRIGGER_COMMAND, "Browser Trigger"),
                NodalNode("node_browse_agent", NodeType.AGENT_NODE, "Browser Agent", mapOf("agent" to "BrowserAgent", "action" to "get_current_tab")),
                NodalNode("node_browse_observe", NodeType.OBSERVER_NODE, "DOM Snapshot"),
                NodalNode("node_browse_result", NodeType.RESULT_NODE, "Browser State")
            ),
            connections = listOf(
                NodeConnection("node_trig_browse", "node_browse_agent"),
                NodeConnection("node_browse_agent", "node_browse_observe"),
                NodeConnection("node_browse_observe", "node_browse_result")
            ),
            isBuiltIn = true
        )
        registerWorkflow(browserWf)

        // 8. Coding Analysis Workflow
        val codingWf = NodalWorkflow(
            id = "wf_coding_analysis",
            name = "Code & Script Analysis",
            description = "Analyzes source code, checks syntax, and recommends modifications",
            triggerCommands = listOf("/code", "/analyze"),
            nodes = listOf(
                NodalNode("node_trig_code", NodeType.TRIGGER_COMMAND, "Code Trigger"),
                NodalNode("node_code_agent", NodeType.AGENT_NODE, "Coding Agent", mapOf("agent" to "CodingAgent", "action" to "analyze_code")),
                NodalNode("node_code_eval", NodeType.EVALUATOR_NODE, "Syntax Verification"),
                NodalNode("node_code_result", NodeType.RESULT_NODE, "Code Report")
            ),
            connections = listOf(
                NodeConnection("node_trig_code", "node_code_agent"),
                NodeConnection("node_code_agent", "node_code_eval"),
                NodeConnection("node_code_eval", "node_code_result")
            ),
            isBuiltIn = true
        )
        registerWorkflow(codingWf)
    }

    /**
     * Export all nodal workflows to a JSON string.
     */
    fun exportWorkflowsToJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        _workflows.value.values.forEach { arr.put(it.toJson()) }
        root.put("workflows", arr)
        return root.toString(2)
    }

    /**
     * Import nodal workflows from JSON string.
     */
    fun importWorkflowsFromJson(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("workflows") ?: return 0
        var count = 0
        for (i in 0 until arr.length()) {
            val wf = NodalWorkflow.fromJson(arr.getJSONObject(i))
            registerWorkflow(wf)
            count++
        }
        return count
    }

    companion object {
        val global: NodalEngine by lazy { NodalEngine() }
    }
}
