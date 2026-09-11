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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

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

                        val isVerified = when {
                            condition == "result_success" -> {
                                (agentResult != null && !context.variables.containsKey("error")) ||
                                        (observation.isNotBlank() && !observation.startsWith("DOM observation unavailable")) ||
                                        lastToolOutput.isNotBlank()
                            }
                            condition.startsWith("contains:") -> {
                                val expected = condition.removePrefix("contains:").trim()
                                val combined = "${agentResult ?: ""} $observation $lastToolOutput"
                                combined.contains(expected, ignoreCase = true)
                            }
                            condition == "non_empty" -> {
                                val combined = "${agentResult ?: ""} $observation $lastToolOutput".trim()
                                combined.isNotEmpty() && !combined.equals("null", ignoreCase = true)
                            }
                            condition == "always_true" -> {
                                agentResult != null || observation.isNotBlank() || lastToolOutput.isNotBlank()
                            }
                            else -> {
                                agentResult != null
                            }
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
                        nodeOutputSummary = "Retry checkpoint reached"
                    }

                    NodeType.FALLBACK -> {
                        nodeOutputSummary = "Fallback handler invoked"
                    }

                    NodeType.PARALLEL -> {
                        nodeOutputSummary = "Parallel fork evaluated"
                    }

                    NodeType.SEQUENCE -> {
                        nodeOutputSummary = "Sequence segment evaluated"
                    }

                    NodeType.LOOP -> {
                        nodeOutputSummary = "Loop condition evaluated"
                    }

                    NodeType.RESULT, NodeType.RESULT_NODE -> {
                        val synthesis = context.getVariable("agentResult")?.toString()
                            ?: context.getVariable("lastToolOutput")?.toString()
                            ?: "Workflow '${workflow.name}' completed successfully."
                        context.setVariable("finalResult", synthesis)
                        nodeOutputSummary = "Synthesis: ${synthesis.take(60)}"
                    }

                    NodeType.CONDITION, NodeType.CONDITION_NODE -> {
                        nodeOutputSummary = "Branch evaluated"
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
                break
            }

            // Find next connected nodes
            val outgoing = workflow.getOutgoingConnections(currentNode.id)
            for (conn in outgoing) {
                val nextNode = workflow.getNode(conn.toNodeId)
                if (nextNode != null && !executedNodeIds.contains(nextNode.id)) {
                    queue.add(nextNode)
                }
            }
        }

        val hasErrors = context.stepLogs.any { it.error != null }
        return NodalWorkflowResult(
            workflowId = workflow.id,
            success = !hasErrors && !context.isCancelled,
            finalOutput = context.getVariable("finalResult") ?: context.getVariable("agentResult"),
            error = context.stepLogs.find { it.error != null }?.error,
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
