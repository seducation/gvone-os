package com.example.agent.specialized

import com.example.agent.cns.CentralNervousSystem
import com.example.agent.core.AgentBase
import com.example.agent.core.AgentCapability
import com.example.agent.core.AgentOrchestrator
import com.example.agent.core.AgentRequest
import com.example.agent.core.AgentResult
import com.example.agent.core.AgentStatus
import com.example.agent.core.CancellationToken
import com.example.agent.core.StepLogger
import com.example.agent.core.StepType
import com.example.agent.nodal.NodalEngine
import com.example.agent.runtime.InteractionType
import com.example.agent.runtime.ModePriority
import com.example.agent.runtime.RuntimeStateManager

/**
 * CommandAgent is the dedicated agent responsible for interpreting, routing,
 * and executing CLI/shell commands and nodal workflows.
 */
class CommandAgent(
    logger: StepLogger = StepLogger.global,
    private val runtimeState: RuntimeStateManager = RuntimeStateManager.global,
    nodalEngine: NodalEngine? = null,
    private val orchestrator: AgentOrchestrator? = null
) : AgentBase("CommandAgent", logger) {

    private val orchestratorInstance: AgentOrchestrator
        get() = orchestrator ?: CentralNervousSystem.global

    private val nodalEngineInstance: NodalEngine = nodalEngine
        ?: NodalEngine(runtimeState = runtimeState, logger = logger, orchestrator = orchestrator)

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "command_dispatch",
            description = "Parses and routes slash commands, compound modes, and nodal workflows",
            supportedActions = listOf("parse", "execute_command", "status", "cancel", "pause", "resume", "debug", "nodal_config")
        )
    )

    override fun observe(): Map<String, Any?> = mapOf(
        "runtimeMode" to runtimeState.runtimeMode.value.toPromptLabel(),
        "activeTask" to (runtimeState.activeTask.value?.goal ?: "None"),
        "debugEnabled" to runtimeState.runtimeMode.value.isDebugEnabled,
        "workflowCount" to nodalEngineInstance.workflows.value.size
    )

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        val rawInput = request.parameters["input"]?.toString() ?: request.parameters["command"]?.toString() ?: ""
        return executeAction(StepType.EXECUTE, "Process Command: $rawInput") {
            processCommand(rawInput, request.requestId)
        }
    }

    suspend fun processCommand(rawInput: String, requestId: String = "cmd_req"): AgentResult {
        val trimmed = rawInput.trim()

        // 1. Check for compound commands first:
        // /voice /agent [goal] -> Voice is primary (Interaction priority)
        if (trimmed.startsWith("/voice /agent", ignoreCase = true) || trimmed.startsWith("/voice_agent", ignoreCase = true)) {
            val goal = trimmed.substringAfter("/agent", "").trim()
            runtimeState.activateVoiceAgentCompound(goal.ifBlank { null })
            val wf = nodalEngineInstance.findWorkflowForCommand("/voice /agent")
            val output = if (wf != null && goal.isNotBlank()) {
                val wfRes = nodalEngineInstance.executeWorkflow(wf, "/voice /agent", goal, rawInput)
                wfRes.finalOutput ?: "Voice-first agent started for: $goal"
            } else {
                "Activated Voice-First Agent Mode (Interaction: VOICE, Execution: AGENT, Priority: INTERACTION).\n" +
                        if (goal.isNotBlank()) "Goal queued: '$goal'. Speak to direct the agent." else "Microphone ready. Speak your goal."
            }
            return AgentResult(requestId, AgentStatus.COMPLETED, output)
        }

        // /agent /voice [goal] -> Agent is primary (Execution priority)
        if (trimmed.startsWith("/agent /voice", ignoreCase = true) || trimmed.startsWith("/agent_voice", ignoreCase = true)) {
            val goal = trimmed.substringAfter("/voice", "").trim()
            runtimeState.activateAgentVoiceCompound(goal.ifBlank { null })
            val wf = nodalEngineInstance.findWorkflowForCommand("/agent /voice")
            val output = if (wf != null && goal.isNotBlank()) {
                val wfRes = nodalEngineInstance.executeWorkflow(wf, "/agent /voice", goal, rawInput)
                wfRes.finalOutput ?: "Agent-first voice task started: $goal"
            } else {
                "Activated Agent-First Voice Mode (Interaction: VOICE, Execution: AGENT, Priority: EXECUTION).\n" +
                        if (goal.isNotBlank()) "Task workspace opened for: '$goal'. Spoken telemetry active." else "Agent workspace ready with voice telemetry."
            }
            return AgentResult(requestId, AgentStatus.COMPLETED, output)
        }

        // 2. Single commands:
        if (trimmed.equals("/voice", ignoreCase = true) || trimmed.startsWith("/voice ", ignoreCase = true)) {
            runtimeState.activateVoiceOnly()
            return AgentResult(
                requestId,
                AgentStatus.COMPLETED,
                "Voice Conversation Mode enabled (Interaction: VOICE, Execution: CHAT).\n" +
                        "Continuous speech listening is active. Speak freely; this mode does not launch background agent tasks."
            )
        }

        if (trimmed.equals("/agent", ignoreCase = true) || trimmed.startsWith("/agent ", ignoreCase = true)) {
            val goal = trimmed.removePrefix("/agent").trim()
            val task = runtimeState.activateAgentOnly(goal.ifBlank { null })
            val wf = nodalEngineInstance.findWorkflowForCommand("/agent")
            if (wf != null && goal.isNotBlank()) {
                val wfRes = nodalEngineInstance.executeWorkflow(wf, "/agent", goal, rawInput)
                return if (wfRes.success) {
                    AgentResult(requestId, AgentStatus.COMPLETED, wfRes.finalOutput ?: "Agent task completed: $goal")
                } else {
                    AgentResult(requestId, AgentStatus.FAILED, error = wfRes.error ?: "Agent workflow failed")
                }
            } else if (goal.isNotBlank()) {
                val orchRes = orchestratorInstance.orchestrateGoal(goal)
                return if (orchRes.success) {
                    AgentResult(requestId, AgentStatus.COMPLETED, orchRes.synthesis)
                } else {
                    AgentResult(requestId, AgentStatus.FAILED, error = orchRes.synthesis)
                }
            } else {
                val output = "Agent Task Mode enabled (Interaction: TEXT, Execution: AGENT, Priority: EXECUTION).\n" +
                        "Ready for autonomous goal. Provide instructions or run '/agent <goal>'."
                return AgentResult(requestId, AgentStatus.COMPLETED, output)
            }
        }

        // Operational controls:
        when (trimmed.lowercase()) {
            "/cancel", "/stop" -> {
                val active = runtimeState.activeTask.value
                runtimeState.cancelTask()
                val msg = if (active != null) {
                    "Cancelled active task '${active.goal}' (TaskId: ${active.taskId}). Runtime mode reset to text chat."
                } else {
                    "No active task was running. Runtime mode reset to text chat."
                }
                return AgentResult(requestId, AgentStatus.COMPLETED, msg)
            }

            "/pause" -> {
                runtimeState.pauseTask()
                return AgentResult(requestId, AgentStatus.COMPLETED, "Active task paused.")
            }

            "/resume" -> {
                runtimeState.resumeTask()
                return AgentResult(requestId, AgentStatus.COMPLETED, "Task resumed.")
            }

            "/debug" -> {
                runtimeState.toggleDebugMode()
                val isDebug = runtimeState.runtimeMode.value.isDebugEnabled
                return AgentResult(requestId, AgentStatus.COMPLETED, "Agent Debug Mode: ${if (isDebug) "ENABLED" else "DISABLED"}")
            }

            "/status" -> {
                val mode = runtimeState.runtimeMode.value
                val active = runtimeState.activeTask.value
                val statusReport = buildString {
                    appendLine("=== GVONE OS Runtime Status ===")
                    appendLine("Interaction Mode: ${mode.interaction.displayName}")
                    appendLine("Execution Mode:   ${mode.execution.displayName}")
                    appendLine("Priority:         ${mode.priority.displayName}")
                    appendLine("Voice Active:     ${mode.isVoiceActive}")
                    appendLine("Debug Enabled:    ${mode.isDebugEnabled}")
                    appendLine("Active Task:      ${active?.let { "'${it.goal}' [${it.status}]" } ?: "None"}")
                    appendLine("Registered WFs:   ${nodalEngineInstance.workflows.value.size}")
                }
                return AgentResult(requestId, AgentStatus.COMPLETED, statusReport.trim())
            }

            "/commands", "/help" -> {
                val wfs = nodalEngineInstance.workflows.value.values
                val list = buildString {
                    appendLine("Available GVONE OS Commands & Nodal Workflows:")
                    appendLine("  /voice                 - Enable continuous hands-free voice conversation")
                    appendLine("  /agent <goal>          - Run autonomous agent task with tools & observation")
                    appendLine("  /voice /agent <goal>   - Voice-first agent task (spoken goal & instructions)")
                    appendLine("  /agent /voice <goal>   - Agent-first task with spoken telemetry accompaniment")
                    appendLine("  /cancel, /stop         - Cancel active task or workflow")
                    appendLine("  /pause, /resume        - Pause / Resume active task")
                    appendLine("  /status                - View runtime mode, active task, and agent health")
                    appendLine("  /debug                 - Toggle transparent planner & tool debug logging")
                    appendLine("  /nodal list            - List registered nodal workflows")
                    appendLine("  /nodal export          - Export nodal configuration to JSON")
                    appendLine("  /yt <query>            - Direct YouTube song search and playback workflow")
                }
                return AgentResult(requestId, AgentStatus.COMPLETED, list.trim())
            }
        }

        // 3. Nodal workflow inspection or export
        if (trimmed.startsWith("/nodal", ignoreCase = true)) {
            val sub = trimmed.removePrefix("/nodal").trim().lowercase()
            return when {
                sub == "list" -> {
                    val lines = nodalEngineInstance.workflows.value.values.joinToString("\n") { wf ->
                        "- ${wf.id} [${wf.name}]: triggers=${wf.triggerCommands.joinToString(", ")} (nodes=${wf.nodes.size})"
                    }
                    AgentResult(requestId, AgentStatus.COMPLETED, "Registered Nodal Workflows:\n$lines")
                }
                sub == "export" -> {
                    val json = nodalEngineInstance.exportWorkflowsToJson()
                    AgentResult(requestId, AgentStatus.COMPLETED, "Nodal Workflow Configuration Export:\n$json")
                }
                else -> {
                    AgentResult(requestId, AgentStatus.COMPLETED, "Usage: /nodal list | /nodal export")
                }
            }
        }

        // 4. Check if matched by any other registered nodal workflow
        val firstToken = trimmed.split(" ").firstOrNull() ?: ""
        val matchedWf = nodalEngineInstance.findWorkflowForCommand(firstToken)
        if (matchedWf != null) {
            val queryArg = trimmed.removePrefix(firstToken).trim()
            val result = nodalEngineInstance.executeWorkflow(matchedWf, firstToken, queryArg, trimmed)
            return AgentResult(
                requestId = requestId,
                status = if (result.success) AgentStatus.COMPLETED else AgentStatus.FAILED,
                data = result.finalOutput ?: "Workflow executed",
                error = result.error
            )
        }

        return AgentResult(
            requestId,
            AgentStatus.FAILED,
            error = "Unknown command '$trimmed'. Type /commands for a list of available commands and workflows."
        )
    }
}
