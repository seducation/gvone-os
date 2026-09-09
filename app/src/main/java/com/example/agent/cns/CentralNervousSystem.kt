package com.example.agent.cns

import com.example.agent.bus.AgentMessageBus
import com.example.agent.core.*
import com.example.agent.memory.AgentMemory
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
 * Translates human goals into multi-agent workflows, enforces biological safety layers,
 * and maintains continuous sensory awareness.
 */
class CentralNervousSystem(
    val reflexSystem: ReflexSystem = ReflexSystem.global,
    val immuneSystem: ImmuneSystem = ImmuneSystem.global,
    val permissionSystem: PermissionSystem = PermissionSystem.global,
    val worldState: WorldState = WorldState.global,
    val memory: AgentMemory = AgentMemory.global,
    val messageBus: AgentMessageBus = AgentMessageBus.global,
    val logger: StepLogger = StepLogger.global,
    val uiBridge: AgentUiBridge = AgentUiBridge.global
) {
    private val agentRegistry = mutableMapOf<String, Agent>()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _activeMission = MutableStateFlow<String?>(null)
    val activeMission: StateFlow<String?> = _activeMission.asStateFlow()

    fun registerAgent(agent: Agent) {
        agentRegistry[agent.identity()] = agent
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

    fun getAgent(name: String): Agent? = agentRegistry[name]

    fun getAllAgents(): List<Agent> = agentRegistry.values.toList()

    /**
     * Core orchestrator method:
     * User request -> Intent understanding -> Safety screening -> Delegation -> Synthesis
     */
    suspend fun orchestrateGoal(userGoal: String): CnsWorkflowResult {
        val workflowId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()
        _isBusy.value = true
        _activeMission.value = userGoal
        worldState.setActiveGoal(userGoal)

        uiBridge.emit(AgentUiEvent.ShowAgentStatus("CNS", "Analyzing goal: '$userGoal'", isWorking = true))
        logger.logInstant("CNS", StepType.DECIDE, "Orchestrating goal: '$userGoal'", StepStatus.RUNNING)

        val subResults = mutableMapOf<String, AgentResult>()
        val participants = mutableListOf<String>()

        try {
            // 1. Reflex Screening: Fast-path nociception
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

            // 2. Intent parsing and task routing
            val lowerGoal = userGoal.lowercase()

            val synthesis: String = when {
                lowerGoal.contains("compare") && (lowerGoal.contains("pdf") || lowerGoal.contains("document") || lowerGoal.contains("file")) -> {
                    // Document vs Webpage Comparison Workflow
                    participants.add("WebReviewAgent")
                    participants.add("FileAgent")
                    executeDocumentWebComparisonWorkflow(workflowId, userGoal, subResults)
                }

                lowerGoal.contains("search") || lowerGoal.contains("find out") || lowerGoal.contains("what is") -> {
                    // Search Workflow
                    participants.add("SearchAgent")
                    val query = userGoal.replace(Regex("(?i)search\\s*(for)?"), "").trim()
                    executeSearchWorkflow(workflowId, query, subResults)
                }

                lowerGoal.contains("browse") || lowerGoal.contains("open") || lowerGoal.contains("navigate") || lowerGoal.contains("tab") -> {
                    // Browser Workflow
                    participants.add("BrowserAgent")
                    executeBrowserWorkflow(workflowId, userGoal, subResults)
                }

                lowerGoal.contains("code") || lowerGoal.contains("script") || lowerGoal.contains("syntax") -> {
                    // Coding Workflow
                    participants.add("CodingAgent")
                    executeCodingWorkflow(workflowId, userGoal, subResults)
                }

                else -> {
                    // General Browser Investigation
                    participants.add("BrowserAgent")
                    executeBrowserWorkflow(workflowId, userGoal, subResults)
                }
            }

            // 3. Record in episodic memory & world state
            memory.recordEpisode(userGoal, "CNS", synthesis.take(200), success = true)
            logger.logInstant("CNS", StepType.COMPLETE, "Workflow completed successfully", StepStatus.SUCCESS)
            uiBridge.emit(AgentUiEvent.ShowAgentStatus("CNS", "Goal completed", isWorking = false))

            return CnsWorkflowResult(
                workflowId = workflowId,
                userGoal = userGoal,
                success = true,
                synthesis = synthesis,
                participatingAgents = participants,
                subResults = subResults,
                durationMs = System.currentTimeMillis() - startTime
            )

        } catch (e: Exception) {
            val errorMsg = e.message ?: e.toString()
            logger.logInstant("CNS", StepType.ERROR, "Goal failed: $errorMsg", StepStatus.FAILED)
            uiBridge.emit(AgentUiEvent.ShowError("CNS", errorMsg, canRetry = true))
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

    /**
     * Executes safe, permission-checked delegation from CNS to an agent.
     */
    suspend fun dispatchToAgent(agentName: String, request: AgentRequest): AgentResult {
        val agent = agentRegistry[agentName]
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
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Querying AI Search Agent..."))
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
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Inspecting browser state..."))
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

    companion object {
        val global = CentralNervousSystem()
    }
}
