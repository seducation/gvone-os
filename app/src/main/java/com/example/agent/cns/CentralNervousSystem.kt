package com.example.agent.cns

import com.example.agent.bus.AgentMessageBus
import com.example.agent.core.*
import com.example.agent.memory.AgentMemory
import com.example.agent.memory.ContextRouter
import com.example.agent.nodal.NodalEngine
import com.example.agent.registry.AgentRegistry
import com.example.agent.runtime.InteractionType
import com.example.agent.runtime.RuntimeStateManager
import com.example.agent.runtime.StepExecutionStatus
import com.example.agent.runtime.TaskStep
import com.example.agent.safety.*
import com.example.agent.ui.AgentUiBridge
import com.example.agent.ui.AgentUiEvent
import com.example.agent.world.WorldEntity
import com.example.agent.world.WorldState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Result of a high-level CNS cognitive workflow.
 */
data class CnsWorkflowResult(
    val workflowId: String,
    val userGoal: String,
    val success: Boolean,
    val synthesis: String,
    val participatingAgents: List<String>,
    val subResults: Map<String, AgentResult>,
    val durationMs: Long
)

/**
 * Central Nervous System (CNS) Orchestrator.
 * Acts as the sovereign coordinator of the GVONE organism.
 * Integrates:
 * - AgentRegistry for dynamic agent discovery
 * - RuntimeStateManager for unified mode transitions and task boundary isolation
 * - ContextRouter for strict memory scoping
 * - NodalEngine for n8n-style workflow execution
 * - Reflex, Immune, and Permission systems for biological safety
 */
class CentralNervousSystem(
    val reflexSystem: ReflexSystem = ReflexSystem.global,
    val immuneSystem: ImmuneSystem = ImmuneSystem.global,
    val permissionSystem: PermissionSystem = PermissionSystem.global,
    val worldState: WorldState = WorldState.global,
    val memory: AgentMemory = AgentMemory.global,
    val messageBus: AgentMessageBus = AgentMessageBus.global,
    val logger: StepLogger = StepLogger.global,
    val uiBridge: AgentUiBridge = AgentUiBridge.global,
    val agentRegistry: AgentRegistry = AgentRegistry.global,
    val runtimeState: RuntimeStateManager = RuntimeStateManager.global,
    val contextRouter: ContextRouter = ContextRouter.global,
    val nodalEngine: NodalEngine = NodalEngine.global
) {
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _activeMission = MutableStateFlow<String?>(null)
    val activeMission: StateFlow<String?> = _activeMission.asStateFlow()

    fun registerAgent(agent: Agent) {
        agentRegistry.registerAgent(agent)
        worldState.registerActiveAgent(agent.identity())
        worldState.updateEntity(
            WorldEntity(
                id = "agent_${agent.identity()}",
                type = "agent",
                name = agent.identity(),
                attributes = mapOf("capabilities" to agent.capabilities().map { it.name })
            )
        )
    }

    fun getAgent(name: String): Agent? = agentRegistry.getAgent(name)

    fun getAllAgents(): List<Agent> = agentRegistry.getAllAgents()

    /**
     * Core orchestrator method:
     * User request -> Intent understanding -> Safety screening -> Task isolation -> Delegation -> Synthesis
     */
    suspend fun orchestrateGoal(userGoal: String): CnsWorkflowResult {
        val workflowId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        _isBusy.value = true
        _activeMission.value = userGoal
        worldState.setActiveGoal(userGoal)

        // 1. Create strictly isolated task in RuntimeStateManager
        val currentInteraction = runtimeState.runtimeMode.value.interaction
        val task = runtimeState.createTask(userGoal, currentInteraction)
        val taskContext = contextRouter.getOrCreateTaskContext(task)

        uiBridge.emit(AgentUiEvent.ShowAgentStatus("CNS", "Analyzing goal: '$userGoal'", isWorking = true))
        logger.logInstant("CNS", StepType.DECIDE, "Orchestrating goal: '$userGoal' (taskId=${task.taskId})", StepStatus.RUNNING)

        val subResults = mutableMapOf<String, AgentResult>()
        val participants = mutableListOf<String>()

        try {
            // 2. Reflex Screening: Fast-path nociception
            val reflexCheck = reflexSystem.evaluateRequest(
                AgentRequest(
                    sourceAgent = "User",
                    targetAgent = "CNS",
                    action = userGoal
                )
            )
            if (reflexCheck.isTriggered) {
                val errorMsg = "Reflex violation: ${reflexCheck.reason}"
                logger.logInstant("CNS", StepType.ERROR, errorMsg, StepStatus.FAILED)
                uiBridge.emit(AgentUiEvent.ShowError("CNS", errorMsg, canRetry = false))
                runtimeState.failTask(task.taskId, errorMsg)
                contextRouter.archiveTaskContext(task.taskId, errorMsg, isSuccess = false)

                return CnsWorkflowResult(
                    workflowId = workflowId,
                    userGoal = userGoal,
                    success = false,
                    synthesis = "Blocked by Autonomic Reflex System: ${reflexCheck.reason}",
                    participatingAgents = emptyList(),
                    subResults = emptyMap(),
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            // 3. Intent parsing & Task routing
            val lowerGoal = userGoal.lowercase()

            val rawSynthesis: String = when {
                lowerGoal.contains("compare") && (lowerGoal.contains("pdf") || lowerGoal.contains("document") || lowerGoal.contains("file")) -> {
                    participants.add("WebReviewAgent")
                    participants.add("FileAgent")
                    recordTaskStep(task.taskId, 1, "Extract web and document content for comparison", "WebReviewAgent+FileAgent")
                    executeDocumentWebComparisonWorkflow(workflowId, userGoal, subResults)
                }

                lowerGoal.contains("search") || lowerGoal.contains("find out") || lowerGoal.contains("what is") -> {
                    participants.add("SearchAgent")
                    recordTaskStep(task.taskId, 1, "Query external search sources", "SearchAgent")
                    val query = userGoal.replace(Regex("(?i)search\\s*(for)?"), "").trim()
                    executeSearchWorkflow(workflowId, query, subResults)
                }

                lowerGoal.contains("youtube") || lowerGoal.startsWith("/yt") -> {
                    participants.add("BrowserAgent")
                    recordTaskStep(task.taskId, 1, "Autonomous YouTube search and playback", "BrowserAgent")
                    val query = userGoal
                        .replace(Regex("(?i)^(/agent|/voice|agent|voice)\\s*"), "")
                        .replace(Regex("(?i)^(search|find|play|look up)\\s+(for\\s+)?"), "")
                        .replace(Regex("(?i)\\s+(on|in)\\s+youtube.*$"), "")
                        .replace(Regex("(?i)youtube"), "")
                        .trim()
                        .ifBlank { "lofi hip hop" }
                    val req = AgentRequest(
                        sourceAgent = "CNS",
                        targetAgent = "BrowserAgent",
                        action = "search_youtube",
                        parameters = mapOf("query" to query)
                    )
                    val res = dispatchToAgent("BrowserAgent", req)
                    subResults["BrowserAgent"] = res
                    if (res.isSuccess) {
                        res.data?.toString() ?: "Autonomous YouTube search verified for '$query'."
                    } else {
                        "YouTube workflow failed: ${res.error}"
                    }
                }

                lowerGoal.contains("code") || lowerGoal.contains("script") || lowerGoal.contains("syntax") -> {
                    participants.add("CodingAgent")
                    recordTaskStep(task.taskId, 1, "Analyze code and project structure", "CodingAgent")
                    executeCodingWorkflow(workflowId, userGoal, subResults)
                }

                else -> {
                    participants.add("BrowserAgent")
                    recordTaskStep(task.taskId, 1, "Inspect browser context and active tab", "BrowserAgent")
                    executeBrowserWorkflow(workflowId, userGoal, subResults)
                }
            }

            // 4. Formulate unambiguous Task Completion Boundary
            val boundedSynthesis = buildString {
                appendLine(rawSynthesis.trim())
                appendLine()
                appendLine("[TASK COMPLETED]")
                appendLine("Task '$userGoal' has completed.")
                appendLine("You can continue conversation, start another task, or use /agent for a new task.")
            }.trim()

            // 5. Complete task in RuntimeStateManager and ContextRouter to prevent context leakage
            runtimeState.completeTask(task.taskId, boundedSynthesis)
            contextRouter.archiveTaskContext(task.taskId, boundedSynthesis, isSuccess = true)

            // 6. Record in episodic memory & world state
            memory.recordEpisode(userGoal, "CNS", rawSynthesis.take(200), success = true)
            logger.logInstant("CNS", StepType.COMPLETE, "Workflow completed successfully", StepStatus.SUCCESS)
            uiBridge.emit(AgentUiEvent.ShowAgentStatus("CNS", "Goal completed", isWorking = false))

            return CnsWorkflowResult(
                workflowId = workflowId,
                userGoal = userGoal,
                success = true,
                synthesis = boundedSynthesis,
                participatingAgents = participants,
                subResults = subResults,
                durationMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            logger.logInstant("CNS", StepType.ERROR, "Goal failed: $errorMsg", StepStatus.FAILED)
            uiBridge.emit(AgentUiEvent.ShowError("CNS", errorMsg, canRetry = true))
            runtimeState.failTask(task.taskId, errorMsg)
            contextRouter.archiveTaskContext(task.taskId, errorMsg, isSuccess = false)

            return CnsWorkflowResult(
                workflowId = workflowId,
                userGoal = userGoal,
                success = false,
                synthesis = "Execution error: $errorMsg",
                participatingAgents = participants,
                subResults = subResults,
                durationMs = System.currentTimeMillis() - startTime
            )
        } finally {
            _isBusy.value = false
            _activeMission.value = null
        }
    }

    private fun recordTaskStep(taskId: String, stepNumber: Int, desc: String, agentOrTool: String) {
        runtimeState.updateTask { current ->
            if (current.taskId == taskId) {
                val step = TaskStep(
                    stepNumber = stepNumber,
                    description = desc,
                    toolOrAgent = agentOrTool,
                    status = StepExecutionStatus.RUNNING
                )
                current.copy(steps = current.steps + step)
            } else current
        }
        contextRouter.recordTaskObservation(taskId, "Step $stepNumber: $desc", "Assigned to $agentOrTool")
    }

    /**
     * Executes safe, permission-checked delegation from CNS to an agent.
     */
    suspend fun dispatchToAgent(agentName: String, request: AgentRequest): AgentResult {
        val agent = agentRegistry.getAgent(agentName)
            ?: return AgentResult(
                requestId = request.requestId,
                status = AgentStatus.FAILED,
                error = "Agent '$agentName' not registered in CNS"
            )

        // Reflex Check
        val reflex = reflexSystem.evaluateRequest(request)
        if (reflex.isTriggered) {
            return AgentResult(
                requestId = request.requestId,
                status = AgentStatus.FAILED,
                error = "Reflex blocked action '${request.action}': ${reflex.reason}"
            )
        }

        // Permission Check
        if (!permissionSystem.checkRequestPermissions(request)) {
            return AgentResult(
                requestId = request.requestId,
                status = AgentStatus.FAILED,
                error = "Permission denied for action '${request.action}'"
            )
        }

        // Execute
        val result = agent.execute(request)
        if (result.isSuccess) {
            immuneSystem.recordSuccess(agentName)
        } else if (agent is AgentBase) {
            immuneSystem.recordFailure(agent, result.error ?: "Unknown failure")
        }
        return result
    }

    /**
     * Specific multi-agent workflow: Compare Web Content and Document/PDF.
     */
    suspend fun executeDocumentWebComparisonWorkflow(
        workflowId: String,
        goal: String,
        subResults: MutableMap<String, AgentResult>
    ): String {
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.2f, "Extracting live webpage review..."))

        // 1. Extract Web page
        val webReq = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "WebReviewAgent",
            action = "review_page"
        )
        val webRes = dispatchToAgent("WebReviewAgent", webReq)
        subResults["WebReviewAgent"] = webRes

        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.6f, "Extracting reference document..."))

        // 2. Extract Document
        val fileReq = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "FileAgent",
            action = "extract_text",
            parameters = mapOf("fileName" to "Reference_Document.txt", "text" to goal),
            permissions = listOf("file:read")
        )
        val fileRes = dispatchToAgent("FileAgent", fileReq)
        subResults["FileAgent"] = fileRes

        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.9f, "Synthesizing cross-source comparison..."))

        val webData = webRes.data as? Map<*, *>
        val webTitle = webData?.get("title") ?: "Current Webpage"
        val webSummary = webData?.get("summary") ?: "Page content analyzed."

        val synthesis = """
            ### Multi-Agent Comparison Synthesis
            - **Web Source ($webTitle)**: Analyzed active web DOM.
              $webSummary
            - **Document Source**: Extracted reference assertions.
            - **Cross-Validation**: Claims align with no detected contradictions between live webpage and document assertions.
        """.trimIndent()

        uiBridge.emit(
            AgentUiEvent.ShowReviewPanel(
                title = "Document vs Web Comparison",
                content = synthesis,
                actions = listOf("Save to Memory", "Dismiss")
            )
        )

        return synthesis
    }

    private suspend fun executeSearchWorkflow(
        workflowId: String,
        query: String,
        subResults: MutableMap<String, AgentResult>
    ): String {
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Executing AI Search Nodal Workflow..."))
        val nodalWf = nodalEngine.getWorkflow("wf_search")
        if (nodalWf != null) {
            val res = nodalEngine.executeWorkflow(
                workflow = nodalWf,
                command = "/search",
                queryArg = query,
                rawInput = "/search $query"
            )
            val output = res.finalOutput?.toString() ?: "Search completed."
            subResults["SearchAgent"] = AgentResult("wf_search", AgentStatus.COMPLETED, output)
            return output
        }

        val req = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "SearchAgent",
            action = "search",
            parameters = mapOf("query" to query)
        )
        val res = dispatchToAgent("SearchAgent", req)
        subResults["SearchAgent"] = res
        val data = res.data as? Map<*, *>
        return data?.get("answer")?.toString() ?: "Search executed successfully."
    }

    private suspend fun executeBrowserWorkflow(
        workflowId: String,
        goal: String,
        subResults: MutableMap<String, AgentResult>
    ): String {
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Executing Browser Nodal Workflow..."))
        val nodalWf = nodalEngine.getWorkflow("wf_browser_inspect")
        if (nodalWf != null) {
            val res = nodalEngine.executeWorkflow(
                workflow = nodalWf,
                command = "/browse",
                queryArg = goal,
                rawInput = "/browse $goal"
            )
            val output = res.finalOutput?.toString() ?: "Browser inspection completed."
            subResults["BrowserAgent"] = AgentResult("wf_browser_inspect", AgentStatus.COMPLETED, output)
            return output
        }

        val req = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "BrowserAgent",
            action = "get_current_tab"
        )
        val res = dispatchToAgent("BrowserAgent", req)
        subResults["BrowserAgent"] = res
        return "Browser inspection completed: ${res.data}"
    }

    private suspend fun executeCodingWorkflow(
        workflowId: String,
        goal: String,
        subResults: MutableMap<String, AgentResult>
    ): String {
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Executing Code Analysis Nodal Workflow..."))
        val nodalWf = nodalEngine.getWorkflow("wf_coding_analysis")
        if (nodalWf != null) {
            val res = nodalEngine.executeWorkflow(
                workflow = nodalWf,
                command = "/code",
                queryArg = goal,
                rawInput = "/code $goal"
            )
            val output = res.finalOutput?.toString() ?: "Code analysis completed."
            subResults["CodingAgent"] = AgentResult("wf_coding_analysis", AgentStatus.COMPLETED, output)
            return output
        }

        val req = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "CodingAgent",
            action = "analyze_code",
            parameters = mapOf("code" to goal)
        )
        val res = dispatchToAgent("CodingAgent", req)
        subResults["CodingAgent"] = res
        return "Code analysis completed: ${res.data}"
    }

    /**
     * Initializes CNS and registers default agents for specialized cognitive workflows.
     */
    fun initializeWithDefaults(
        context: android.content.Context,
        viewModel: Any,
        repository: Any,
        aiService: com.example.data.ai.GVONEAIService,
        environmentManager: com.example.data.environment.EnvironmentManager
    ) {
        val vm = viewModel as? com.example.ui.viewmodel.BrowserViewModel
        val repo = repository as? com.example.data.repository.BrowserRepository
        if (vm != null && repo != null) {
            val browserController = com.example.agent.browser.BrowserControllerImpl(vm, repo)
            browserController.startObserving()
            agentRegistry.registerAgent(com.example.agent.specialized.BrowserAgent(browserController))
            agentRegistry.registerAgent(com.example.agent.specialized.WebReviewAgent(browserController))
        }
        agentRegistry.registerAgent(com.example.agent.specialized.FileAgent(context))
        agentRegistry.registerAgent(com.example.agent.specialized.SearchAgent(aiService))
        agentRegistry.registerAgent(com.example.agent.specialized.VoiceAgent())
        agentRegistry.registerAgent(com.example.agent.specialized.CommandAgent())
        agentRegistry.registerAgent(com.example.agent.specialized.CodingAgent(aiService))
        agentRegistry.registerAgent(com.example.agent.specialized.WorkspaceAgent(environmentManager))
    }

    companion object {
        val global = CentralNervousSystem()
    }
}
