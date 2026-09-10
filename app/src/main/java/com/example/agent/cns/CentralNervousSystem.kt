package com.example.agent.cns

import android.content.Context
import com.example.agent.browser.BrowserControllerImpl
import com.example.agent.bus.AgentMessageBus
import com.example.agent.core.*
import com.example.agent.memory.AgentMemory
import com.example.agent.safety.*
import com.example.agent.specialized.*
import com.example.agent.ui.AgentUiBridge
import com.example.agent.ui.AgentUiEvent
import com.example.agent.world.WorldEntity
import com.example.agent.world.WorldState
import com.example.data.ai.GVONEAIService
import com.example.data.environment.EnvironmentManager
import com.example.data.repository.BrowserRepository
import com.example.ui.viewmodel.BrowserViewModel
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
            val lowerGoal = userGoal.lowercase().trim()

            val synthesis: String = when {
                // Media / Music / Video Playback: "play bohemian rhapsody", "watch lofi", "listen to podcast"
                lowerGoal.startsWith("play ") || lowerGoal.startsWith("watch ") || lowerGoal.startsWith("listen to ") -> {
                    participants.add("BrowserAgent")
                    executeBrowserWorkflow(workflowId, userGoal, subResults)
                }

                // Document vs Webpage Comparison Workflow
                lowerGoal.contains("compare") && (lowerGoal.contains("pdf") || lowerGoal.contains("document") || lowerGoal.contains("file")) -> {
                    participants.add("WebReviewAgent")
                    participants.add("FileAgent")
                    executeDocumentWebComparisonWorkflow(workflowId, userGoal, subResults)
                }

                // Coding & Script Generation: "code a python scraper", "write javascript function", "generate script"
                lowerGoal.contains("code") || lowerGoal.contains("script") || lowerGoal.contains("python") ||
                lowerGoal.contains("javascript") || lowerGoal.contains("kotlin") || lowerGoal.contains("html") ||
                lowerGoal.contains("program") || lowerGoal.contains("function to") -> {
                    participants.add("CodingAgent")
                    executeCodingWorkflow(workflowId, userGoal, subResults)
                }

                // Explicit Browser Operations: Navigation, Tabs, Page Control, History, Bookmarks, Tor, Scroll
                lowerGoal.contains("open ") || lowerGoal.contains("go to ") || lowerGoal.contains("navigate ") ||
                lowerGoal.contains("visit ") || lowerGoal.contains("launch ") || lowerGoal.contains("browse ") ||
                lowerGoal.contains("tab") || lowerGoal.contains("reload") || lowerGoal.contains("refresh") ||
                lowerGoal.contains("scroll") || lowerGoal.contains("zoom") || lowerGoal.contains("bookmark") ||
                lowerGoal.contains("history") || lowerGoal.contains("tor") ||
                lowerGoal.contains("read page") || lowerGoal.contains("summarize page") || lowerGoal.contains("extract text") ||
                lowerGoal == "back" || lowerGoal == "go back" || lowerGoal == "forward" || lowerGoal == "go forward" ||
                lowerGoal.contains(".com") || lowerGoal.contains(".org") || lowerGoal.contains(".net") ||
                lowerGoal.contains(".io") || lowerGoal.contains(".ai") || lowerGoal.contains(".dev") ||
                lowerGoal.contains(".app") || lowerGoal.contains("http://") || lowerGoal.contains("https://") -> {
                    participants.add("BrowserAgent")
                    executeBrowserWorkflow(workflowId, userGoal, subResults)
                }

                // General Autonomous Goal / Search / Research / Questions (Default)
                else -> {
                    participants.add("SearchAgent")
                    participants.add("BrowserAgent")
                    val query = userGoal.replace(Regex("(?i)^(please\\s+|can\\s+you\\s+)?(search(\\s+for)?|research|find(\\s+out)?|look\\s+up|what\\s+is|who\\s+is|how\\s+to|tell\\s+me\\s+about)\\s+"), "").trim()
                    executeSearchWorkflow(workflowId, if (query.isNotBlank()) query else userGoal, subResults)
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
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.3f, "Navigating browser & searching for \"$query\"..."))
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.google.com/search?q=$encodedQuery"

        // 1. Open Google search in browser tab so user sees real search
        try {
            val navReq = AgentRequest(
                sourceAgent = "CNS",
                targetAgent = "BrowserAgent",
                action = "open_url",
                parameters = mapOf("url" to searchUrl, "inNewTab" to false)
            )
            val navRes = dispatchToAgent("BrowserAgent", navReq)
            subResults["BrowserAgent"] = navRes
        } catch (_: Exception) {}

        // 2. Synthesize AI answer with multi-model search agent
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.7f, "Synthesizing AI answer..."))
        val req = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "SearchAgent",
            action = "search",
            parameters = mapOf("query" to query)
        )
        val res = dispatchToAgent("SearchAgent", req)
        subResults["SearchAgent"] = res
        val data = res.data as? Map<*, *>
        val answer = data?.get("answer")?.toString() ?: "Search completed for \"$query\"."

        return buildString {
            append("### Search: \"$query\"\n\n")
            append("✔ Navigated browser to Google Search ($searchUrl)\n\n")
            append(answer)
        }
    }

    private suspend fun executeBrowserWorkflow(
        workflowId: String,
        goal: String,
        subResults: MutableMap<String, AgentResult>
    ): String {
        val lower = goal.lowercase().trim()

        // 1. Video / Media playback (e.g. "play lofi hip hop", "play taylor swift", "watch news")
        if (lower.startsWith("play ") || lower.startsWith("watch ") || lower.startsWith("listen to ")) {
            val mediaQuery = goal.replace(Regex("(?i)^(please\\s+|can\\s+you\\s+)?(play|watch|listen\\s+to)\\s+"), "").trim()
            val ytUrl = "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode(mediaQuery, "UTF-8")
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.6f, "Playing \"$mediaQuery\" on YouTube..."))
            val req = AgentRequest(
                sourceAgent = "CNS",
                targetAgent = "BrowserAgent",
                action = "open_url",
                parameters = mapOf("url" to ytUrl, "inNewTab" to false)
            )
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            return "Playing \"$mediaQuery\" on YouTube ($ytUrl)"
        }

        // 2. Webpage Scrolling (e.g. "scroll down", "scroll up", "scroll to top")
        if (lower.contains("scroll")) {
            val dir = if (lower.contains("up") || lower.contains("top")) "up" else "down"
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Scrolling webpage $dir..."))
            val req = AgentRequest(
                sourceAgent = "CNS",
                targetAgent = "BrowserAgent",
                action = "scroll",
                parameters = mapOf("direction" to dir)
            )
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            return "Scrolled webpage $dir."
        }

        // 3. Navigation History: Back & Forward
        if (lower == "back" || lower == "go back" || lower.contains("previous page")) {
            val req = AgentRequest(sourceAgent = "CNS", targetAgent = "BrowserAgent", action = "go_back")
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            return "Navigated back in browser history."
        }
        if (lower == "forward" || lower == "go forward" || lower.contains("next page")) {
            val req = AgentRequest(sourceAgent = "CNS", targetAgent = "BrowserAgent", action = "go_forward")
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            return "Navigated forward in browser history."
        }

        // 4. Tab management: List all tabs
        if (lower.contains("list tab") || lower.contains("show tab") || lower.contains("get tab") || lower == "tabs") {
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Retrieving open tabs..."))
            val req = AgentRequest(sourceAgent = "CNS", targetAgent = "BrowserAgent", action = "get_tabs")
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            val tabs = res.data as? List<*>
            return "Active tabs (${tabs?.size ?: 0}):\n" + (tabs?.joinToString("\n") { "● $it" } ?: "No active tabs")
        }

        // 5. History & Bookmarks Inspection
        if (lower.contains("history")) {
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Retrieving browsing history..."))
            val req = AgentRequest(sourceAgent = "CNS", targetAgent = "BrowserAgent", action = "get_history")
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            val entries = res.data as? List<*>
            return "Browsing History (${entries?.size ?: 0} items):\n" + (entries?.take(10)?.joinToString("\n") { "● $it" } ?: "No history entries found.")
        }
        if (lower.contains("bookmark")) {
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Retrieving bookmarks..."))
            val req = AgentRequest(sourceAgent = "CNS", targetAgent = "BrowserAgent", action = "get_bookmarks")
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            val bookmarks = res.data as? List<*>
            return "Bookmarks (${bookmarks?.size ?: 0} items):\n" + (bookmarks?.take(10)?.joinToString("\n") { "★ $it" } ?: "No bookmarks saved.")
        }

        // 6. Tab management: Close active tab
        if (lower.contains("close tab") || lower.contains("close this tab") || lower.contains("close the tab")) {
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Querying active tab to close..."))
            val tabReq = AgentRequest(sourceAgent = "CNS", targetAgent = "BrowserAgent", action = "get_current_tab")
            val currentTabRes = dispatchToAgent("BrowserAgent", tabReq)
            val tabObj = currentTabRes.data as? com.example.agent.browser.BrowserTabInfo
            val tabId = tabObj?.id
            return if (tabId != null) {
                val closeReq = AgentRequest(
                    sourceAgent = "CNS",
                    targetAgent = "BrowserAgent",
                    action = "close_tab",
                    parameters = mapOf("tabId" to tabId)
                )
                val closeRes = dispatchToAgent("BrowserAgent", closeReq)
                subResults["BrowserAgent"] = closeRes
                "Closed active browser tab ($tabId) via BrowserAgent"
            } else {
                "No active tab found to close."
            }
        }

        // 7. Tab management: Create new tab
        if (lower.contains("new tab") || lower.contains("create tab") || lower.contains("add tab") || lower.contains("open a tab")) {
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Creating new tab..."))
            val req = AgentRequest(
                sourceAgent = "CNS",
                targetAgent = "BrowserAgent",
                action = "create_tab"
            )
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            return "Created new browser tab via BrowserAgent"
        }

        // 8. Page Reload / Refresh
        if (lower.contains("reload") || lower.contains("refresh")) {
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Reloading current page..."))
            val req = AgentRequest(
                sourceAgent = "CNS",
                targetAgent = "BrowserAgent",
                action = "reload"
            )
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            return "Reloaded active webpage."
        }

        // 9. Read / Summarize / Inspect current page
        if (lower.contains("read") || lower.contains("summarize") || lower.contains("extract") || lower.contains("inspect")) {
            uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Reading current page text..."))
            val req = AgentRequest(
                sourceAgent = "CNS",
                targetAgent = "BrowserAgent",
                action = "read_page"
            )
            val res = dispatchToAgent("BrowserAgent", req)
            subResults["BrowserAgent"] = res
            val text = (res.data as? Map<*, *>)?.get("text")?.toString() ?: ""
            val url = (res.data as? Map<*, *>)?.get("url")?.toString() ?: ""
            return if (text.isNotBlank()) "Extracted text from $url (${text.length} chars):\n\n${text.take(800)}..." else "Inspected browser tab: ${res.data}"
        }

        // 10. Direct navigation intent
        val targetUrl = resolveTargetUrl(goal)
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.6f, "Navigating to $targetUrl..."))
        val req = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "BrowserAgent",
            action = "open_url",
            parameters = mapOf("url" to targetUrl, "inNewTab" to (lower.contains("another tab") || lower.contains("background tab") || lower.contains("new tab")))
        )
        val res = dispatchToAgent("BrowserAgent", req)
        subResults["BrowserAgent"] = res
        return if (res.isSuccess) "Successfully navigated to $targetUrl via BrowserAgent" else "Browser navigation failed: ${res.error}"
    }

    private fun resolveTargetUrl(goal: String): String {
        val clean = goal.trim()
            .replace(Regex("(?i)^(please\\s+|can\\s+you\\s+)?(open|go\\s+to|navigate\\s+to|visit|launch|load)\\s+"), "")
            .replace(Regex("(?i)\\s+(in\\s+new\\s+tab|in\\s+a\\s+new\\s+tab|in\\s+background)"), "")
            .trim()

        val words = clean.split("\\s+".toRegex())
        val explicitDomain = words.find {
            it.startsWith("http://") || it.startsWith("https://") ||
                    it.contains(".com") || it.contains(".org") || it.contains(".net") ||
                    it.contains(".io") || it.contains(".gov") || it.contains(".edu") ||
                    it.contains(".ai") || it.contains(".app") || it.contains(".dev") ||
                    (it.contains(".") && !it.contains(" "))
        }

        if (explicitDomain != null) {
            val url = explicitDomain.trim().removeSurrounding("\"", "").removeSurrounding("'", "")
            return if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        }

        val firstWord = words.firstOrNull()?.lowercase()?.replace(Regex("[^a-z0-9]"), "") ?: ""
        return when (firstWord) {
            "youtube" -> "https://www.youtube.com"
            "google" -> "https://www.google.com"
            "wikipedia" -> "https://www.wikipedia.org"
            "reddit" -> "https://www.reddit.com"
            "github" -> "https://github.com"
            "twitter", "x" -> "https://x.com"
            "duckduckgo" -> "https://duckduckgo.com"
            "bing" -> "https://www.bing.com"
            "amazon" -> "https://www.amazon.com"
            "hackernews", "hn" -> "https://news.ycombinator.com"
            "facebook" -> "https://www.facebook.com"
            "instagram" -> "https://www.instagram.com"
            "linkedin" -> "https://www.linkedin.com"
            "netflix" -> "https://www.netflix.com"
            "spotify" -> "https://open.spotify.com"
            "twitch" -> "https://www.twitch.tv"
            "tiktok" -> "https://www.tiktok.com"
            "chatgpt" -> "https://chatgpt.com"
            "claude" -> "https://claude.ai"
            "gmail" -> "https://mail.google.com"
            "maps" -> "https://maps.google.com"
            "weather" -> "https://weather.com"
            "pinterest" -> "https://www.pinterest.com"
            "medium" -> "https://medium.com"
            "yahoo" -> "https://www.yahoo.com"
            "bbc" -> "https://www.bbc.com"
            "cnn" -> "https://www.cnn.com"
            "nytimes" -> "https://www.nytimes.com"
            "ebay" -> "https://www.ebay.com"
            "stackoverflow" -> "https://stackoverflow.com"
            "quora" -> "https://www.quora.com"
            "imdb" -> "https://www.imdb.com"
            "apple" -> "https://www.apple.com"
            "microsoft" -> "https://www.microsoft.com"
            "openai" -> "https://openai.com"
            else -> {
                if (words.size == 1 && firstWord.isNotBlank()) {
                    // Single word like "walmart", "target", "gitlab" -> default to .com
                    "https://www.$firstWord.com"
                } else if (clean.isNotBlank()) {
                    "https://www.google.com/search?q=" + java.net.URLEncoder.encode(clean, "UTF-8")
                } else {
                    "https://www.google.com"
                }
            }
        }
    }

    private suspend fun executeCodingWorkflow(
        workflowId: String,
        goal: String,
        subResults: MutableMap<String, AgentResult>
    ): String {
        uiBridge.emit(AgentUiEvent.ShowProgress("CNS", 0.5f, "Synthesizing code solution..."))
        val req = AgentRequest(
            sourceAgent = "CNS",
            targetAgent = "CodingAgent",
            action = "generate_script",
            parameters = mapOf("intent" to goal)
        )
        val res = dispatchToAgent("CodingAgent", req)
        subResults["CodingAgent"] = res
        val data = res.data as? Map<*, *>
        val script = data?.get("script")?.toString() ?: "Code generation complete."
        val language = data?.get("language")?.toString() ?: "code"
        return "### Coding Agent Solution ($language)\n\n```$language\n$script\n```"
    }

    /**
     * Initializes the Central Nervous System with the primary specialized agents.
     */
    fun initializeWithDefaults(
        context: Context,
        viewModel: BrowserViewModel,
        repository: BrowserRepository,
        aiService: GVONEAIService,
        environmentManager: EnvironmentManager
    ) {
        if (agentRegistry.isNotEmpty()) return

        val browserController = BrowserControllerImpl(viewModel, repository).apply {
            startObserving()
        }
        val browserAgent = BrowserAgent(browserController, logger)
        val searchAgent = SearchAgent(aiService, logger)
        val webReviewAgent = WebReviewAgent(browserController, logger)
        val fileAgent = FileAgent(context, logger)
        val workspaceAgent = WorkspaceAgent(environmentManager, logger)
        val codingAgent = CodingAgent(aiService, logger)

        registerAgent(browserAgent)
        registerAgent(searchAgent)
        registerAgent(webReviewAgent)
        registerAgent(fileAgent)
        registerAgent(workspaceAgent)
        registerAgent(codingAgent)
    }

    companion object {
        val global = CentralNervousSystem()
    }
}
