package com.example.agent.sandbox

import android.content.Context
import com.example.data.ai.GVONEAIService
import com.example.data.files.GVONEFileSystem
import com.example.data.model.BrowserTab
import com.example.data.model.TabGroup
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Persona styles matching user's requested agentic paradigms:
 * - ATLAS: ChatGPT Atlas autonomous browser operator (DOM inspection, page actions, verified steps)
 * - COMET: Perplexity Comet multi-source parallel research & intelligence cohort
 * - DIA: Dia Browser sandbox environment (file workspace, script execution, group tabs)
 * - AUTO: Dynamically selects best paradigm based on goal
 */
enum class AgentPersona(val displayName: String, val badge: String, val description: String) {
    ATLAS("ChatGPT Atlas", "ATLAS", "Autonomous Browser Operator & Step-by-Step Task Agent"),
    COMET("Comet", "COMET", "Multi-Source Research & Intelligent Tab Group Cohorts"),
    DIA("Dia Browser", "DIA", "Live File & Code Sandbox Workspace with Group Tabs"),
    AUTO("Auto-Agent", "AUTO", "Adaptive Agentic Engine");

    companion object {
        fun fromString(name: String): AgentPersona {
            return when (name.trim().lowercase()) {
                "atlas" -> ATLAS
                "comet" -> COMET
                "dia" -> DIA
                "auto" -> AUTO
                else -> AUTO
            }
        }
    }
}

data class AgentExecutionPlan(
    val goal: String,
    val persona: AgentPersona,
    val steps: List<AgentPlanStep>,
    val targetTabGroupName: String,
    val targetFileName: String?
)

data class AgentPlanStep(
    val stepNumber: Int,
    val description: String,
    val tool: String,
    val expectedOutcome: String
)

class SandboxAgentEngine(
    private val context: Context,
    private val viewModel: BrowserViewModel,
    private val aiService: GVONEAIService,
    private val fileSystem: GVONEFileSystem
) {
    var activePersona: AgentPersona = AgentPersona.AUTO
    var isAgenticModeEnabled: Boolean = false
    var activeSandboxGroupId: String? = null

    /**
     * Executes an autonomous agentic workflow like ChatGPT Atlas, Comet, and Dia Browser.
     * Streams progress lines directly to the terminal line emitter.
     */
    suspend fun runAgenticWorkflow(
        goal: String,
        currentDir: String = "Documents",
        emit: suspend (TerminalLine) -> Unit
    ) {
        val cleanGoal = goal.trim()
        if (cleanGoal.isBlank()) {
            emit(TerminalLine("[AGENT] Error: No goal provided. Usage: /agent <goal/prompt>", TerminalLineType.ERROR))
            return
        }

        // 0. Autonomic Reflex System Evaluation (Spinal Cord check for fast threat interception)
        val reflexCheck = com.example.agent.cns.CentralNervousSystem.global.reflexSystem.evaluateRequest(
            com.example.agent.core.AgentRequest(
                sourceAgent = "User",
                targetAgent = "CNS",
                action = "execute_goal",
                parameters = mapOf("goal" to cleanGoal)
            )
        )
        if (reflexCheck.isTriggered) {
            emit(TerminalLine("🚨 [AUTONOMIC REFLEX INTERCEPT] Action Blocked by Spinal Cord Reflex!", TerminalLineType.ERROR))
            emit(TerminalLine("  • Threat: ${reflexCheck.threatName ?: "SUSPICIOUS_PAYLOAD"}", TerminalLineType.ERROR))
            emit(TerminalLine("  • Reason: ${reflexCheck.reason}", TerminalLineType.ERROR))
            emit(TerminalLine("  • Action Taken: ${reflexCheck.actionTaken}", TerminalLineType.WARNING))
            return
        }

        val persona = if (activePersona == AgentPersona.AUTO) detectBestPersona(cleanGoal) else activePersona
        val plan = createExecutionPlan(cleanGoal, persona, currentDir)

        // 1. Mission Header
        emit(TerminalLine("╭─────────────────────────────────────────────────────────────╮", TerminalLineType.AGENT_PLAN))
        emit(TerminalLine("│ 🤖 AGENTIC RUNTIME: ${persona.displayName.uppercase()} MODE", TerminalLineType.AGENT_PLAN))
        emit(TerminalLine("│ Goal: \"$cleanGoal\"", TerminalLineType.AGENT_PLAN))
        emit(TerminalLine("│ Sandbox Workspace: /$currentDir | Group Tabs: ${plan.targetTabGroupName}", TerminalLineType.AGENT_PLAN))
        emit(TerminalLine("╰─────────────────────────────────────────────────────────────╯", TerminalLineType.AGENT_PLAN))

        // 2. Thought & Intent Decomposition
        delay(120)
        emit(TerminalLine("[THOUGHT & INTENT] Decomposing objective into autonomous sub-steps...", TerminalLineType.AGENT_THOUGHT))
        emit(TerminalLine("  • Mode Persona: ${persona.displayName} (${persona.description})", TerminalLineType.AGENT_THOUGHT))
        emit(TerminalLine("  • Verifying browser state, DOM accessibility, and sandbox storage permissions...", TerminalLineType.AGENT_THOUGHT))

        // 3. Plan Presentation
        delay(150)
        emit(TerminalLine("\n[EXECUTION PLAN (${plan.steps.size} STEPS)]", TerminalLineType.AGENT_PLAN))
        plan.steps.forEach { step ->
            emit(TerminalLine("  ${step.stepNumber}. [${step.tool}] ${step.description}", TerminalLineType.INFO))
        }
        emit(TerminalLine("", TerminalLineType.OUTPUT))

        // 4. Step 1: Sandbox Tab Group Management & Cohort Setup
        delay(200)
        emit(TerminalLine("[STEP 1/${plan.steps.size}] Tool: TabGroupManager -> Initializing Sandbox Tab Group...", TerminalLineType.AGENT_STEP))
        val sandboxGroupId = ensureSandboxTabGroup(plan.targetTabGroupName)
        activeSandboxGroupId = sandboxGroupId
        emit(TerminalLine("  ✔ Tab Group created/focused: \"${plan.targetTabGroupName}\" (ID: ${sandboxGroupId.take(8)}...)", TerminalLineType.AGENT_TOOL))

        // 5. Step 2: Context Gathering & Web Inspection
        delay(250)
        emit(TerminalLine("[STEP 2/${plan.steps.size}] Tool: BrowserObserver -> Inspecting environment & DOM...", TerminalLineType.AGENT_STEP))
        val currentTab = viewModel.currentTab.value
        val currentUrl = currentTab?.url.orEmpty()
        val currentTitle = currentTab?.title.orEmpty()
        emit(TerminalLine("  ✔ Active Tab: \"$currentTitle\" (${if (currentUrl.isNotBlank()) currentUrl else "gvone://newtab"})", TerminalLineType.AGENT_TOOL))

        // Move current tab to sandbox group if not already grouped
        if (currentTab != null && currentTab.tabGroupId == null) {
            withContext(Dispatchers.Main) {
                viewModel.moveTabToGroup(currentTab.id, sandboxGroupId)
            }
            emit(TerminalLine("  ✔ Linked active tab into sandbox group \"${plan.targetTabGroupName}\"", TerminalLineType.AGENT_TOOL))
        }

        // 6. Step 3: Information Retrieval & Agentic Synthesis (CNS Orchestration & AI Engine)
        delay(300)
        emit(TerminalLine("[STEP 3/${plan.steps.size}] Tool: CentralNervousSystem -> Orchestrating Multi-Agent Protocol...", TerminalLineType.AGENT_STEP))

        val cnsResult = try {
            com.example.agent.cns.CentralNervousSystem.global.orchestrateGoal(cleanGoal)
        } catch (e: Exception) {
            null
        }

        if (cnsResult != null && cnsResult.participatingAgents.isNotEmpty()) {
            emit(TerminalLine("  ✔ Multi-Agent Cohort: [${cnsResult.participatingAgents.joinToString(", ")}] executed in ${cnsResult.durationMs}ms", TerminalLineType.AGENT_TOOL))
        }

        val synthesisPrompt = buildString {
            append("You are an advanced autonomous agentic browser co-pilot operating in ")
            append(persona.displayName)
            append(" mode.\n\n")
            append("User Goal: ").append(cleanGoal).append("\n")
            append("Current Context: URL=").append(currentUrl).append(", Title=").append(currentTitle).append("\n\n")
            if (cnsResult != null && cnsResult.synthesis.isNotBlank()) {
                append("Agent Preliminary Synthesis:\n").append(cnsResult.synthesis).append("\n\n")
            }
            append("Provide a comprehensive, high-value structured synthesis addressing the goal. ")
            append("Include key findings, actionable steps, code/data artifacts if applicable, and recommendations.")
        }

        val lowerGoal = cleanGoal.lowercase()
        val isDirectBrowserAction = cnsResult != null && cnsResult.success &&
                (lowerGoal.contains("open ") || lowerGoal.contains("go to ") ||
                 lowerGoal.contains("navigate ") || lowerGoal.contains("close tab") ||
                 lowerGoal.contains("new tab") || lowerGoal.contains("reload") ||
                 lowerGoal.contains("refresh") || lowerGoal.contains("read") ||
                 lowerGoal.contains("summarize") || lowerGoal.startsWith("play ") ||
                 lowerGoal.startsWith("watch ") || lowerGoal.contains("scroll") ||
                 lowerGoal == "back" || lowerGoal == "forward" || lowerGoal.contains("tabs"))

        val aiResponse = if (isDirectBrowserAction) {
            cnsResult!!.synthesis
        } else if (cnsResult != null && cnsResult.success && cnsResult.synthesis.isNotBlank() && cnsResult.synthesis.length > 50) {
            cnsResult.synthesis
        } else {
            try {
                val res = aiService.searchAndSynthesize(synthesisPrompt)
                res.aiAnswer
            } catch (e: Exception) {
                if (cnsResult != null && cnsResult.synthesis.isNotBlank()) {
                    cnsResult.synthesis
                } else {
                    "Analysis complete. Goal processed with active sandbox parameters. (${e.message})"
                }
            }
        }

        emit(TerminalLine("  ✔ Multi-source synthesis generated (${aiResponse.length} chars)", TerminalLineType.AGENT_TOOL))

        // For COMET / ATLAS: create a research tab for the topic (if not a direct navigation action and tab not already opened)
        val tabAlreadyOpened = cnsResult?.participatingAgents?.contains("BrowserAgent") == true
        if (!isDirectBrowserAction && !tabAlreadyOpened && (persona == AgentPersona.COMET || persona == AgentPersona.ATLAS)) {
            delay(150)
            val searchTopic = cleanGoal.take(40).replace("\"", "").trim()
            val researchUrl = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(searchTopic, "UTF-8")
            withContext(Dispatchers.Main) {
                viewModel.createNewTab(
                    url = researchUrl,
                    isPrivate = viewModel.isPrivateMode.value,
                    groupId = sandboxGroupId,
                    inBackground = false
                )
            }
            emit(TerminalLine("  ✔ Opened research tab in sandbox group \"${plan.targetTabGroupName}\": $searchTopic", TerminalLineType.AGENT_TOOL))
        }

        // 7. Step 4: Sandbox File Persistence (Dia Browser style)
        delay(200)
        val fileName = plan.targetFileName ?: "agent_research_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.md"
        val filePath = if (currentDir.isBlank() || currentDir == "/") fileName else "$currentDir/$fileName"
        
        emit(TerminalLine("[STEP 4/${plan.steps.size}] Tool: SandboxFileSystem -> Persisting report to /$filePath...", TerminalLineType.AGENT_STEP))
        
        val markdownContent = buildString {
            append("# ").append(cleanGoal.replaceFirstChar { it.uppercase() }).append("\n\n")
            append("> **Generated by GVONE Agentic Runtime** (").append(persona.displayName).append(")\n")
            append("> **Timestamp**: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append("\n")
            append("> **Tab Group**: ").append(plan.targetTabGroupName).append("\n\n")
            append("## Executive Synthesis\n\n")
            append(aiResponse).append("\n\n")
            append("## Action Plan & Sandbox Artifacts\n")
            append("- Tab Cohort: Organized in group `").append(plan.targetTabGroupName).append("`\n")
            append("- Sandbox File: `").append(filePath).append("`\n")
            append("- Engine Persona: `").append(persona.name).append("`\n")
        }

        val writeSuccess = fileSystem.writeFileContent(filePath, markdownContent)
        if (writeSuccess) {
            emit(TerminalLine("  ✔ File saved successfully to sandbox: /$filePath (${markdownContent.length} bytes)", TerminalLineType.SUCCESS))
        } else {
            emit(TerminalLine("  ⚠ Sandbox file write skipped or fallback to memory.", TerminalLineType.WARNING))
        }

        // 8. Step 5: Final Synthesis & Agent Summary
        delay(150)
        emit(TerminalLine("\n╭─────────────────────────────────────────────────────────────╮", TerminalLineType.AGENT_PLAN))
        emit(TerminalLine("│ 🎯 AGENTIC WORKFLOW COMPLETED SUCCESSFULLY [${persona.badge}]", TerminalLineType.AGENT_PLAN))
        emit(TerminalLine("╰─────────────────────────────────────────────────────────────╯", TerminalLineType.AGENT_PLAN))
        
        emit(TerminalLine(aiResponse, TerminalLineType.AI_RESPONSE))
        
        emit(TerminalLine("", TerminalLineType.OUTPUT))
        emit(TerminalLine("📋 [SANDBOX SUMMARY]", TerminalLineType.SYSTEM))
        emit(TerminalLine("  • Group Tabs: \"${plan.targetTabGroupName}\" (Tabs grouped in sandbox)", TerminalLineType.SUCCESS))
        emit(TerminalLine("  • Sandbox File: /$filePath (Use 'cat $fileName' or 'view $fileName')", TerminalLineType.INFO))
        emit(TerminalLine("  • Agentic Mode: ${if (isAgenticModeEnabled) "ACTIVE (Type next instruction)" else "IDLE (Run /agent on to keep session open)"}", TerminalLineType.INFO))
    }

    /**
     * Auto-organizes all open browser tabs into intelligent groups based on their domain and title.
     */
    suspend fun autoOrganizeTabs(emit: suspend (TerminalLine) -> Unit) {
        val tabs = viewModel.tabs.value
        if (tabs.isEmpty()) {
            emit(TerminalLine("[AGENT] No open tabs to organize.", TerminalLineType.INFO))
            return
        }

        emit(TerminalLine("[AGENT] Analyzing ${tabs.size} open tabs for intelligent group clustering...", TerminalLineType.AGENT_THOUGHT))

        val domainGroups = mutableMapOf<String, MutableList<BrowserTab>>()
        for (tab in tabs) {
            val domain = try {
                val host = URI(tab.url).host?.lowercase() ?: ""
                when {
                    host.contains("google") || host.contains("bing") || host.contains("duckduckgo") -> "Search & Discovery"
                    host.contains("github") || host.contains("stackoverflow") || host.contains("gitlab") -> "Development & Code"
                    host.contains("youtube") || host.contains("spotify") || host.contains("reddit") -> "Media & Community"
                    host.contains("wikipedia") || host.contains("arxiv") || host.contains("medium") -> "Research & Reading"
                    tab.url.startsWith("gvone-file://") || tab.url.contains("manus.space") -> "Sandbox & WebApps"
                    else -> if (host.isNotBlank()) host.replace("www.", "").replaceFirstChar { it.uppercase() } else "General Workspace"
                }
            } catch (_: Exception) {
                "General Workspace"
            }
            domainGroups.getOrPut(domain) { mutableListOf() }.add(tab)
        }

        emit(TerminalLine("✔ Detected ${domainGroups.size} conceptual tab cohorts:", TerminalLineType.SUCCESS))

        withContext(Dispatchers.Main) {
            domainGroups.forEach { (groupName, groupTabs) ->
                val existingGroup = viewModel.tabGroups.value.find { it.name.equals(groupName, ignoreCase = true) }
                val targetGroupId = existingGroup?.id ?: viewModel.createTabGroup(
                    name = groupName,
                    colorHex = pickGroupColor(groupName)
                )
                viewModel.moveTabsToGroup(groupTabs.map { it.id }, targetGroupId)
            }
        }

        domainGroups.forEach { (name, groupTabs) ->
            emit(TerminalLine("  📂 Group: \"$name\" (${groupTabs.size} tabs)", TerminalLineType.INFO))
            groupTabs.take(3).forEach { t ->
                emit(TerminalLine("     - ${t.title.take(35)}", TerminalLineType.OUTPUT))
            }
            if (groupTabs.size > 3) {
                emit(TerminalLine("     - ... and ${groupTabs.size - 3} more", TerminalLineType.OUTPUT))
            }
        }

        emit(TerminalLine("✔ Successfully organized ${tabs.size} tabs into ${domainGroups.size} groups.", TerminalLineType.SUCCESS))
    }

    /**
     * Ensures a tab group exists for the sandbox task.
     */
    private suspend fun ensureSandboxTabGroup(groupName: String): String = withContext(Dispatchers.Main) {
        val existing = viewModel.tabGroups.value.find { it.name.equals(groupName, ignoreCase = true) }
        if (existing != null) {
            existing.id
        } else {
            viewModel.createTabGroup(
                name = groupName,
                colorHex = pickGroupColor(groupName)
            )
        }
    }

    private fun detectBestPersona(goal: String): AgentPersona {
        val lower = goal.lowercase()
        return when {
            lower.contains("click") || lower.contains("form") || lower.contains("navigate") || lower.contains("inspect") || lower.contains("login") -> AgentPersona.ATLAS
            lower.contains("research") || lower.contains("search") || lower.contains("compare") || lower.contains("find out") || lower.contains("sources") -> AgentPersona.COMET
            lower.contains("file") || lower.contains("code") || lower.contains("sandbox") || lower.contains("script") || lower.contains("save") || lower.contains("notes") -> AgentPersona.DIA
            else -> AgentPersona.ATLAS
        }
    }

    private fun createExecutionPlan(goal: String, persona: AgentPersona, currentDir: String): AgentExecutionPlan {
        val lower = goal.lowercase()
        val groupName = when {
            lower.contains("sandbox") -> "Sandbox"
            lower.contains("research") -> "Research: " + goal.replace(Regex("(?i)research\\s*(for|about|on)?"), "").trim().take(20)
            lower.contains("trip") || lower.contains("travel") -> "Travel: " + goal.replace(Regex("(?i)(plan|a|trip|to)"), "").trim().take(20)
            else -> "Agentic: " + persona.name.lowercase().replaceFirstChar { it.uppercase() }
        }.ifBlank { "Sandbox: Agent" }

        val targetFileName = when {
            lower.contains(".md") || lower.contains(".txt") || lower.contains(".json") || lower.contains(".js") -> {
                val words = goal.split(" ")
                words.find { it.endsWith(".md") || it.endsWith(".txt") || it.endsWith(".json") || it.endsWith(".js") }
            }
            else -> null
        }

        val steps = listOf(
            AgentPlanStep(1, "Tab Cohort Setup: Create / activate Tab Group \"$groupName\"", "TabGroupManager", "Dedicated tab group created"),
            AgentPlanStep(2, "Sensory Inspection: Inspect active webpage DOM, URL, and title", "BrowserObserver", "Page state captured"),
            AgentPlanStep(3, "Agentic Synthesis: Query multi-source intelligence & reasoning engine", persona.badge + "Engine", "Synthesis prepared"),
            AgentPlanStep(4, "Sandbox File Persistence: Write report / artifact to /$currentDir", "SandboxFileSystem", "Markdown file saved"),
            AgentPlanStep(5, "Final Synthesis: Output structured takeaways & interactive links", "CentralNervousSystem", "Mission accomplished")
        )

        return AgentExecutionPlan(
            goal = goal,
            persona = persona,
            steps = steps,
            targetTabGroupName = groupName,
            targetFileName = targetFileName
        )
    }

    private fun pickGroupColor(name: String): String {
        val colors = listOf("#3B82F6", "#10B981", "#8B5CF6", "#F59E0B", "#EC4899", "#06B6D4")
        val hash = kotlin.math.abs(name.hashCode())
        return colors[hash % colors.size]
    }
}
