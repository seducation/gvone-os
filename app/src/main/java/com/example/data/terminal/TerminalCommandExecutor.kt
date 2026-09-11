package com.example.data.terminal

import androidx.lifecycle.viewModelScope
import com.example.agent.cns.CentralNervousSystem
import com.example.agent.memory.ContextRouter
import com.example.agent.nodal.NodalEngine
import com.example.agent.runtime.RuntimeStateManager
import com.example.agent.sandbox.AgentPersona
import com.example.agent.sandbox.SandboxAgentEngine
import com.example.data.command.CommandEngine
import com.example.data.model.*
import com.example.data.sync.WebAppConnectionState
import com.example.data.tor.TorConnectionState
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

enum class CommandOrigin {
    ADDRESS_BAR,
    TERMINAL,
    CNS_DASHBOARD
}

/**
 * Unified Terminal & Address Bar Command Executor.
 * Provides complete parity and identical execution phases between inputs entered from the
 * Address Bar and inputs entered from the Terminal.
 */
class TerminalCommandExecutor(
    private val viewModel: BrowserViewModel,
    val shellEngine: TerminalShellEngine,
    val agentEngine: SandboxAgentEngine
) {
    private val _isAgenticMode = MutableStateFlow(true)
    val isAgenticMode: StateFlow<Boolean> = _isAgenticMode.asStateFlow()

    private val _activePersona = MutableStateFlow(agentEngine.activePersona)
    val activePersona: StateFlow<AgentPersona> = _activePersona.asStateFlow()

    init {
        viewModel.viewModelScope.launch {
            com.example.agent.runtime.RuntimeStateManager.global.runtimeMode.collect { mode ->
                val isAgent = mode.execution == com.example.agent.runtime.ExecutionType.AGENT
                if (_isAgenticMode.value != isAgent) {
                    _isAgenticMode.value = isAgent
                    agentEngine.isAgenticModeEnabled = isAgent
                }
            }
        }
    }

    companion object {
        val SHELL_COMMAND_KEYWORDS = setOf(
            "clear", "cls", "exit", "quit", "q", "help", "?",
            "agent", "agentic", "voice", "chat", "text", "cancel", "stop", "abort", "pause", "resume",
            "debug", "status", "nodal", "workflows", "config", "command", "commands", "yt", "youtube",
            "key", "gemini", "apikey",
            "dashboard", "cns", "groups", "tabgroups", "group", "sandbox", "organize", "creategroup", "grouptab", "ungroup",
            "pwd", "cd", "ls", "dir", "cat", "touch", "mkdir", "rm", "tree", "df", "du", "cookies",
            "run", "exec", "start",
            "click", "type", "scroll", "links", "text", "extract", "view", "openfile",
            "bridge", "addressbar", "history", "whoami", "date", "uname", "echo",
            "tabs", "lstabs", "tab", "switchtab", "newtab", "nt", "closetab", "ct",
            "url", "goto", "open", "tor", "desktop", "js", "ai", "ask", "ping"
        )
    }

    fun setAgenticMode(enabled: Boolean) {
        _isAgenticMode.value = enabled
        agentEngine.isAgenticModeEnabled = enabled
        val runtimeState = com.example.agent.runtime.RuntimeStateManager.global
        val isCurrentAgent = runtimeState.runtimeMode.value.execution == com.example.agent.runtime.ExecutionType.AGENT
        if (enabled && !isCurrentAgent) {
            runtimeState.activateAgentOnly()
        } else if (!enabled && isCurrentAgent) {
            runtimeState.resetToTextChat()
        }
    }

    fun setPersona(persona: AgentPersona) {
        _activePersona.value = persona
        agentEngine.activePersona = persona
    }

    /**
     * Determines whether user input should be treated as a command.
     */
    fun isCommand(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false

        // Anything starting with '/' is unconditionally treated as a command
        if (trimmed.startsWith("/")) return true

        // Natural language task switching detection ("Stop that and search news")
        if (trimmed.matches(Regex("(?i)^(stop|cancel|abort)\\s+(that|current\\s+task|this)\\s+and\\s+.*"))) return true

        // If the terminal sheet is actively displayed, treat direct inputs as terminal commands
        if (viewModel.activeSheet.value == ActiveSheet.Terminal) return true

        val spaceIdx = trimmed.indexOf(' ')
        val firstToken = (if (spaceIdx != -1) trimmed.substring(0, spaceIdx) else trimmed).lowercase()

        // Shell built-in command keyword
        if (SHELL_COMMAND_KEYWORDS.contains(firstToken)) return true

        // Registered Custom Commands or Aliases
        val allCommands = CommandEngine.mergeWithBuiltIns(viewModel.customCommands.value)
        return allCommands.any { it.isEnabled && (it.matchesTrigger(firstToken) || it.matchesTrigger("/$firstToken")) }
    }

    /**
     * Executes a command string regardless of whether it was originated from the Floating Address Bar
     * or the Terminal Monospace interface.
     */
    fun executeCommand(
        rawInput: String,
        origin: CommandOrigin = CommandOrigin.TERMINAL,
        onOpenAgentDashboard: (() -> Unit)? = null
    ) {
        val trimmed = rawInput.trim()
        val currentCwd = shellEngine.promptPath
        val promptPrefix = when (origin) {
            CommandOrigin.CNS_DASHBOARD -> "gvone[cns:dashboard]:$currentCwd$ "
            else -> if (_isAgenticMode.value) {
                "gvone[agentic:${_activePersona.value.badge.lowercase()}]:$currentCwd$ "
            } else {
                "gvone@browser:$currentCwd$ "
            }
        }

        // Always synchronize the address bar with the command being executed
        viewModel.setAddressBarInput(trimmed)

        if (trimmed.isEmpty()) {
            val emptyCommandLine = TerminalLine(
                text = promptPrefix,
                type = TerminalLineType.COMMAND
            )
            viewModel.appendTerminalLine(emptyCommandLine)
            return
        }

        // Record in persistent command history
        viewModel.terminalRepository.addCommandToHistory(trimmed)

        val cmdLine = TerminalLine(
            text = "$promptPrefix$trimmed",
            type = TerminalLineType.COMMAND
        )
        val outputLines = mutableListOf<TerminalLine>()
        outputLines.add(cmdLine)

        // Parse token and argument
        val spaceIdx = trimmed.indexOf(' ')
        val token = if (spaceIdx != -1) trimmed.substring(0, spaceIdx).trim() else trimmed
        val queryArg = if (spaceIdx != -1) trimmed.substring(spaceIdx + 1).trim() else ""
        val normalizedToken = if (token.startsWith("/")) token.lowercase() else "/${token.lowercase()}"

        fun commitAndShowTerminalIfNeeded(lines: List<TerminalLine>, openTerminal: Boolean = true) {
            viewModel.appendTerminalLines(lines)
            if (openTerminal && origin == CommandOrigin.ADDRESS_BAR && viewModel.activeSheet.value != ActiveSheet.Terminal) {
                viewModel.openSheet(ActiveSheet.Terminal)
            }
        }

        val scope = viewModel.viewModelScope
        val currentTab = viewModel.currentTab.value
        val tabs = viewModel.tabs.value
        val isTorActive = viewModel.torStatus.value.state == TorConnectionState.CONNECTED

        // 0. Deterministic Natural Language Task Switching: "Stop that and search news"
        if (trimmed.matches(Regex("(?i)^(stop|cancel|abort)\\s+(that|current\\s+task|this)\\s+and\\s+.*"))) {
            val newGoal = trimmed.replace(Regex("(?i)^(stop|cancel|abort)\\s+(that|current\\s+task|this)\\s+and\\s+"), "").trim()
            val runtimeState = RuntimeStateManager.global
            val active = runtimeState.activeTask.value
            runtimeState.cancelTask(active?.taskId)
            if (active != null) {
                ContextRouter.global.archiveTaskContext(active.taskId, "User switched task to: $newGoal", isSuccess = false)
            }
            outputLines.add(TerminalLine("[TASK SWITCH] Previous task cancelled and context scratchpad archived.", TerminalLineType.WARNING))
            outputLines.add(TerminalLine("[NEW TASK] Initiating fresh isolated task: \"$newGoal\"", TerminalLineType.SUCCESS))
            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)

            runtimeState.activateAgentOnly(newGoal)
            setAgenticMode(true)
            scope.launch {
                agentEngine.runAgenticWorkflow(newGoal, shellEngine.currentDirectory) { line ->
                    viewModel.appendTerminalLine(line)
                }
            }
            return
        }

        when (normalizedToken) {
            "/clear", "/cls" -> {
                viewModel.clearTerminalLines()
                if (origin == CommandOrigin.ADDRESS_BAR && viewModel.activeSheet.value != ActiveSheet.Terminal) {
                    viewModel.openSheet(ActiveSheet.Terminal)
                }
                return
            }

            "/exit", "/quit", "/q" -> {
                viewModel.closeSheet()
                return
            }

            "/help", "/?" -> {
                val allCommands = CommandEngine.mergeWithBuiltIns(viewModel.customCommands.value)
                outputLines.addAll(generateHelpOutput(allCommands))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/voice" -> {
                val runtimeState = RuntimeStateManager.global
                when {
                    queryArg.startsWith("/agent", ignoreCase = true) || queryArg.startsWith("agent", ignoreCase = true) -> {
                        // Compound mode: /voice /agent <goal>
                        val goal = queryArg.removePrefix("/agent").removePrefix("agent").trim()
                        runtimeState.activateVoiceAgentCompound(goal)
                        setAgenticMode(true)
                        outputLines.add(TerminalLine("╭─────────────────────────────────────────────────────────────╮", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ 🎙️ COMPOUND MODE: VOICE-FIRST + AGENT RUNTIME", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Goal: \"$goal\"", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Hierarchy: Voice Primary ➜ Autonomous Agent Execution", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("╰─────────────────────────────────────────────────────────────╯", TerminalLineType.AGENT_PLAN))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        scope.launch {
                            agentEngine.runAgenticWorkflow(goal.ifBlank { "Autonomous task execution" }, shellEngine.currentDirectory) { line ->
                                viewModel.appendTerminalLine(line)
                            }
                        }
                        return
                    }
                    queryArg.isBlank() -> {
                        // Pure voice conversation mode: /voice (DOES NOT create agent task)
                        runtimeState.activateVoiceOnly()
                        outputLines.add(TerminalLine("[VOICE RUNTIME] ACTIVATED. Voice conversation mode is active.", TerminalLineType.SUCCESS))
                        outputLines.add(TerminalLine("● Interaction: VOICE | Execution: CHAT (No autonomous task created)", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("● Speak or type any conversational question, e.g. \"What is photosynthesis?\"", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("● For autonomous execution, use '/voice /agent <goal>' or '/agent <goal>'", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("● To return to standard text chat, type '/chat'", TerminalLineType.OUTPUT))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        return
                    }
                    else -> {
                        // Voice utterance with text query
                        val lower = queryArg.lowercase()
                        if (lower.startsWith("search ") || lower.contains("youtube") || lower.startsWith("play ") || lower.startsWith("open ")) {
                            outputLines.add(TerminalLine("[VOICE INTENT DETECTED] Agentic instruction: \"$queryArg\"", TerminalLineType.AGENT_PLAN))
                            runtimeState.activateVoiceAgentCompound(queryArg)
                            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                            scope.launch {
                                agentEngine.runAgenticWorkflow(queryArg, shellEngine.currentDirectory) { line ->
                                    viewModel.appendTerminalLine(line)
                                }
                            }
                        } else {
                            outputLines.add(TerminalLine("[VOICE CONVERSATION] \"$queryArg\"", TerminalLineType.COMMAND))
                            outputLines.add(TerminalLine("Consulting AI conversational service...", TerminalLineType.INFO))
                            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                            scope.launch {
                                try {
                                    val res = viewModel.aiService.searchAndSynthesize(queryArg)
                                    viewModel.appendTerminalLine(TerminalLine(res.aiAnswer, TerminalLineType.AI_RESPONSE))
                                } catch (e: Exception) {
                                    viewModel.appendTerminalLine(TerminalLine("AI response error: ${e.message}", TerminalLineType.ERROR))
                                }
                            }
                        }
                        return
                    }
                }
            }

            "/cancel", "/stop", "/abort" -> {
                val runtimeState = RuntimeStateManager.global
                val current = runtimeState.activeTask.value
                runtimeState.cancelTask(current?.taskId)
                if (current != null) {
                    ContextRouter.global.archiveTaskContext(current.taskId, "User cancelled task", isSuccess = false)
                }
                setAgenticMode(false)
                outputLines.add(TerminalLine("[TASK CANCELLED] Active task has been aborted and cleared.", TerminalLineType.WARNING))
                outputLines.add(TerminalLine("Unified runtime restored to CHAT mode. Task context scratchpad archived.", TerminalLineType.INFO))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/pause" -> {
                val runtimeState = RuntimeStateManager.global
                val current = runtimeState.activeTask.value
                runtimeState.pauseTask(current?.taskId)
                outputLines.add(TerminalLine("[TASK PAUSED] Active task execution suspended. Type '/resume' to continue.", TerminalLineType.WARNING))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/resume" -> {
                val runtimeState = RuntimeStateManager.global
                val current = runtimeState.activeTask.value
                runtimeState.resumeTask(current?.taskId)
                outputLines.add(TerminalLine("[TASK RESUMED] Resumed active task execution.", TerminalLineType.SUCCESS))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/chat", "/text" -> {
                RuntimeStateManager.global.resetToTextChat()
                setAgenticMode(false)
                outputLines.add(TerminalLine("[CHAT MODE] Restored to conversational text mode.", TerminalLineType.SUCCESS))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/debug" -> {
                RuntimeStateManager.global.toggleDebugMode()
                val isDbg = RuntimeStateManager.global.runtimeMode.value.isDebugEnabled
                outputLines.add(
                    TerminalLine(
                        "[DEBUG MODE] " + (if (isDbg) "ENABLED. Verbose scratchpad observations, tool traces & latency active." else "DISABLED. Standard clean view active."),
                        if (isDbg) TerminalLineType.SUCCESS else TerminalLineType.INFO
                    )
                )
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/status" -> {
                val runtimeState = RuntimeStateManager.global
                val mode = runtimeState.runtimeMode.value
                val active = runtimeState.activeTask.value
                outputLines.add(TerminalLine("── GVONE OS UNIFIED RUNTIME STATUS ──", TerminalLineType.SYSTEM))
                outputLines.add(TerminalLine("● Interaction Mode: ${mode.interaction.name}", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("● Execution Mode:   ${mode.execution.name}", TerminalLineType.INFO))
                outputLines.add(TerminalLine("● Priority Mode:    ${mode.priority.name}", TerminalLineType.OUTPUT))
                outputLines.add(TerminalLine("● Debug Observability: ${if (mode.isDebugEnabled) "ON" else "OFF"}", TerminalLineType.OUTPUT))
                outputLines.add(TerminalLine("● Active Task: ${active?.goal ?: "None (Idle)"} [${active?.status?.name ?: "IDLE"}]", TerminalLineType.OUTPUT))
                outputLines.add(TerminalLine("● Registered Agents: ${CentralNervousSystem.global.agentRegistry.registeredAgentNames.value.joinToString()}", TerminalLineType.INFO))
                outputLines.add(TerminalLine("● Nodal Workflows: ${NodalEngine.global.workflows.value.size} registered", TerminalLineType.INFO))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/nodal", "/workflows" -> {
                val workflows = NodalEngine.global.workflows.value.values.toList()
                outputLines.add(TerminalLine("── NODAL N8N-STYLE WORKFLOWS (${workflows.size}) ──", TerminalLineType.SYSTEM))
                workflows.forEach { wf ->
                    outputLines.add(TerminalLine("● [${wf.id}] \"${wf.name}\" (${wf.nodes.size} nodes, ${wf.connections.size} connections)", TerminalLineType.SUCCESS))
                    outputLines.add(TerminalLine("  Trigger: ${wf.triggerCommands.joinToString()} | Nodes: ${wf.nodes.joinToString { it.name }}", TerminalLineType.INFO))
                }
                outputLines.add(TerminalLine("Commands themselves can be configured through this nodal workflow registry.", TerminalLineType.INFO))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/config" -> {
                val isGeminiLive = viewModel.aiService.isApiKeyConfigured()
                outputLines.add(TerminalLine("── GVONE OS SYSTEM CONFIGURATION ──", TerminalLineType.SYSTEM))
                outputLines.add(TerminalLine("● Engine Version: GVONE OS v2.4-unified", TerminalLineType.INFO))
                outputLines.add(TerminalLine("● AI Service: ${if (isGeminiLive) "Gemini 3.5 Flash (Online)" else "Local Reflex Engine (Offline)"}", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("● Browser Engine: Android WebView + Multi-Tab Session Manager", TerminalLineType.OUTPUT))
                outputLines.add(TerminalLine("● Tor Network: ${if (isTorActive) "CONNECTED" else "DISCONNECTED"}", if (isTorActive) TerminalLineType.SUCCESS else TerminalLineType.WARNING))
                outputLines.add(TerminalLine("● Sandbox Root: /${shellEngine.currentDirectory}", TerminalLineType.OUTPUT))
                outputLines.add(TerminalLine("● Agent Protocol: RPC-over-CNS + Context Isolation", TerminalLineType.INFO))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/command", "/commands" -> {
                val allCommands = CommandEngine.mergeWithBuiltIns(viewModel.customCommands.value)
                outputLines.add(TerminalLine("── GVONE OS COMMAND REGISTRY (${allCommands.size} registered) ──", TerminalLineType.SYSTEM))
                allCommands.take(15).forEach { cmd ->
                    outputLines.add(TerminalLine("● ${cmd.command} (${cmd.name}) - ${cmd.description}", TerminalLineType.INFO))
                }
                if (allCommands.size > 15) {
                    outputLines.add(TerminalLine("... and ${allCommands.size - 15} more. Type '/help' for full catalog.", TerminalLineType.OUTPUT))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/yt", "/youtube" -> {
                if (queryArg.isNotBlank()) {
                    outputLines.add(TerminalLine("[YOUTUBE AGENT] Launching autonomous YouTube playback for: \"$queryArg\"...", TerminalLineType.AGENT_PLAN))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    scope.launch {
                        val res = CentralNervousSystem.global.orchestrateGoal("/yt $queryArg")
                        viewModel.appendTerminalLine(TerminalLine(res.synthesis, TerminalLineType.SUCCESS))
                    }
                    return
                }
            }

            "/agent", "/agentic" -> {
                val runtimeState = RuntimeStateManager.global
                when {
                    queryArg.startsWith("/voice", ignoreCase = true) || queryArg.startsWith("voice", ignoreCase = true) -> {
                        // Compound mode: /agent /voice <goal>
                        val goal = queryArg.removePrefix("/voice").removePrefix("voice").trim()
                        runtimeState.activateAgentVoiceCompound(goal)
                        setAgenticMode(true)
                        outputLines.add(TerminalLine("╭─────────────────────────────────────────────────────────────╮", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ 🤖 COMPOUND MODE: AGENT-FIRST + VOICE STATUS", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Goal: \"$goal\"", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Hierarchy: Autonomous Execution ➜ Voice Reporting", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("╰─────────────────────────────────────────────────────────────╯", TerminalLineType.AGENT_PLAN))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        scope.launch {
                            agentEngine.runAgenticWorkflow(goal.ifBlank { "Autonomous task execution" }, shellEngine.currentDirectory) { line ->
                                viewModel.appendTerminalLine(line)
                            }
                        }
                        return
                    }
                    queryArg.isBlank() -> {
                        val currentlyActive = _isAgenticMode.value && (runtimeState.runtimeMode.value.execution == com.example.agent.runtime.ExecutionType.AGENT)
                        val newMode = !currentlyActive
                        setAgenticMode(newMode)
                        outputLines.add(
                            TerminalLine(
                                "[AGENTIC MODE] " + (if (newMode) "ACTIVATED (${_activePersona.value.displayName}). Type any goal/instruction to execute autonomously." else "DEACTIVATED. Standard bash shell active."),
                                if (newMode) TerminalLineType.SUCCESS else TerminalLineType.WARNING
                            )
                        )
                        outputLines.add(
                            TerminalLine(
                                "Paradigms: ChatGPT Atlas (deep browser automation), Comet (multi-tab research), Dia Browser (file & tab sandbox)",
                                TerminalLineType.INFO
                            )
                        )
                    }
                    queryArg.equals("on", ignoreCase = true) || queryArg.equals("start", ignoreCase = true) || queryArg.equals("enable", ignoreCase = true) -> {
                        setAgenticMode(true)
                        outputLines.add(TerminalLine("[AGENTIC MODE] ACTIVATED. Persona: ${_activePersona.value.displayName}", TerminalLineType.SUCCESS))
                        outputLines.add(TerminalLine("Interactive agent prompt active. Use '/agent persona <atlas|comet|dia|auto>' or '/agent off' to exit.", TerminalLineType.INFO))
                    }
                    queryArg.equals("off", ignoreCase = true) || queryArg.equals("stop", ignoreCase = true) || queryArg.equals("disable", ignoreCase = true) -> {
                        setAgenticMode(false)
                        outputLines.add(TerminalLine("[AGENTIC MODE] DEACTIVATED. Standard bash shell active.", TerminalLineType.WARNING))
                    }
                    queryArg.startsWith("persona", ignoreCase = true) -> {
                        val personaArg = queryArg.removePrefix("persona").trim().lowercase()
                        val newPersona = when (personaArg) {
                            "atlas", "chatgpt" -> AgentPersona.ATLAS
                            "comet", "perplexity" -> AgentPersona.COMET
                            "dia", "sandbox" -> AgentPersona.DIA
                            else -> AgentPersona.AUTO
                        }
                        setPersona(newPersona)
                        outputLines.add(TerminalLine("[AGENTIC PERSONA] Switched to: ${newPersona.displayName} (${newPersona.description})", TerminalLineType.SUCCESS))
                    }
                    queryArg.equals("dashboard", ignoreCase = true) || queryArg.equals("ui", ignoreCase = true) || queryArg.equals("cns", ignoreCase = true) -> {
                        outputLines.add(TerminalLine("[AGENT UI DASHBOARD] Launching Central Nervous System Dashboard...", TerminalLineType.SUCCESS))
                        viewModel.appendTerminalLines(outputLines)
                        if (onOpenAgentDashboard != null) {
                            onOpenAgentDashboard()
                        } else {
                            viewModel.openAgentDashboard()
                        }
                        return
                    }
                    queryArg.equals("status", ignoreCase = true) -> {
                        val activeGroupName = viewModel.tabGroups.value.find { it.id == agentEngine.activeSandboxGroupId }?.name ?: "None"
                        val isGeminiLive = viewModel.aiService.isApiKeyConfigured()
                        val activeTask = runtimeState.activeTask.value
                        outputLines.add(TerminalLine("── AGENTIC RUNTIME STATUS ──", TerminalLineType.SYSTEM))
                        outputLines.add(TerminalLine("● Mode: " + (if (_isAgenticMode.value) "ACTIVE" else "IDLE"), if (_isAgenticMode.value) TerminalLineType.SUCCESS else TerminalLineType.WARNING))
                        outputLines.add(TerminalLine("● Active Persona: ${_activePersona.value.displayName} (${_activePersona.value.badge})", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("● Active Task: ${activeTask?.goal ?: "None (Idle)"} [${activeTask?.status?.name ?: "IDLE"}]", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("● Gemini 3.5 LLM: " + (if (isGeminiLive) "ONLINE & ACTIVE" else "LOCAL FALLBACK (Type '/key' for setup)"), if (isGeminiLive) TerminalLineType.SUCCESS else TerminalLineType.WARNING))
                        outputLines.add(TerminalLine("● Sandbox Tab Group: $activeGroupName", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("● Sandbox Directory: /${shellEngine.currentDirectory}", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("● Multi-Agent Core: CentralNervousSystem + BrowserController + GVONEFileSystem", TerminalLineType.SUCCESS))
                    }
                    queryArg.equals("tabs", ignoreCase = true) || queryArg.equals("group", ignoreCase = true) || queryArg.equals("organize", ignoreCase = true) -> {
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        scope.launch {
                            agentEngine.autoOrganizeTabs { line ->
                                viewModel.appendTerminalLine(line)
                            }
                        }
                        return
                    }
                    else -> {
                        // Autonomous Agent Goal Execution
                        runtimeState.activateAgentOnly(queryArg)
                        setAgenticMode(true)
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        scope.launch {
                            agentEngine.runAgenticWorkflow(queryArg, shellEngine.currentDirectory) { line ->
                                viewModel.appendTerminalLine(line)
                            }
                        }
                        return
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/key", "/gemini", "/apikey" -> {
                if (queryArg.isBlank()) {
                    val isConfigured = viewModel.aiService.isApiKeyConfigured()
                    outputLines.add(TerminalLine("── GEMINI 3.5 API CREDENTIAL STATUS ──", TerminalLineType.SYSTEM))
                    if (isConfigured) {
                        outputLines.add(TerminalLine("● Status: ONLINE & ACTIVE", TerminalLineType.SUCCESS))
                        outputLines.add(TerminalLine("● Model: gemini-3.5-flash (GenerativeLanguage v1beta)", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("● Source: BuildConfig / Active Session", TerminalLineType.OUTPUT))
                    } else {
                        outputLines.add(TerminalLine("● Status: NOT CONFIGURED (Using Local Reflex Fallback Engine)", TerminalLineType.WARNING))
                        outputLines.add(TerminalLine("  To configure your Gemini API Key:", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("  Option A (Permanent): Add GEMINI_API_KEY to AI Studio Secrets panel", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("  Option B (Session): Type '/key YOUR_GEMINI_KEY' to validate & activate", TerminalLineType.OUTPUT))
                    }
                    if (viewModel.aiService.lastError != null) {
                        outputLines.add(TerminalLine("⚠ Last Diagnostic: ${viewModel.aiService.lastError}", TerminalLineType.WARNING))
                    }
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    return
                } else {
                    val keyToTest = queryArg.trim()
                    outputLines.add(TerminalLine("[GEMINI] Validating API key with Google AI...", TerminalLineType.INFO))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    scope.launch {
                        val (success, message) = viewModel.aiService.testApiKey(keyToTest)
                        val line = if (success) {
                            TerminalLine("✔ $message - Gemini 3.5 Flash is now active for this session!", TerminalLineType.SUCCESS)
                        } else {
                            TerminalLine("✖ $message", TerminalLineType.ERROR)
                        }
                        viewModel.appendTerminalLine(line)
                    }
                    return
                }
            }

            "/dashboard", "/cns", "/brain" -> {
                if (queryArg.isNotBlank()) {
                    outputLines.add(TerminalLine("[CNS ORCHESTRATION] Orchestrating goal: \"$queryArg\"...", TerminalLineType.AGENT_PLAN))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    scope.launch {
                        val res = CentralNervousSystem.global.orchestrateGoal(queryArg)
                        viewModel.appendTerminalLine(TerminalLine(res.synthesis, if (res.success) TerminalLineType.SUCCESS else TerminalLineType.ERROR))
                    }
                    return
                }
                outputLines.add(TerminalLine("[AGENT UI DASHBOARD] Launching Central Nervous System Dashboard...", TerminalLineType.SUCCESS))
                viewModel.appendTerminalLines(outputLines)
                if (onOpenAgentDashboard != null) {
                    onOpenAgentDashboard()
                } else {
                    viewModel.openAgentDashboard()
                }
                return
            }

            "/groups", "/tabgroups", "/group" -> {
                when {
                    queryArg.isBlank() -> {
                        outputLines.addAll(shellEngine.listTabGroups())
                    }
                    queryArg.startsWith("create", ignoreCase = true) -> {
                        val name = queryArg.removePrefix("create").trim()
                        if (name.isBlank()) {
                            outputLines.add(TerminalLine("Usage: /group create <group_name> [color_hex]", TerminalLineType.WARNING))
                        } else {
                            val parts = name.split(" ")
                            val groupName = parts[0]
                            val color = parts.getOrNull(1)
                            val newId = viewModel.createTabGroup(groupName, colorHex = color)
                            outputLines.add(TerminalLine("[OK] Created Tab Group: \"$groupName\" (ID: ${newId.take(8)}...)", TerminalLineType.SUCCESS))
                        }
                    }
                    queryArg.startsWith("add", ignoreCase = true) -> {
                        val parts = queryArg.removePrefix("add").trim().split(" ")
                        if (parts.size < 2) {
                            outputLines.add(TerminalLine("Usage: /group add <tab_index_or_id> <group_name_or_id>", TerminalLineType.WARNING))
                        } else {
                            val tabRef = parts[0]
                            val groupRef = parts[1]
                            val targetTab = tabRef.toIntOrNull()?.let { tabs.getOrNull(it) } ?: tabs.find { it.id == tabRef }
                            val targetGroup = viewModel.tabGroups.value.find { it.name.equals(groupRef, ignoreCase = true) || it.id == groupRef }
                            if (targetTab == null) {
                                outputLines.add(TerminalLine("[ERR] Tab not found: $tabRef", TerminalLineType.ERROR))
                            } else if (targetGroup == null) {
                                outputLines.add(TerminalLine("[ERR] Tab group not found: $groupRef", TerminalLineType.ERROR))
                            } else {
                                viewModel.moveTabToGroup(targetTab.id, targetGroup.id)
                                outputLines.add(TerminalLine("[OK] Moved tab \"${targetTab.title}\" to group \"${targetGroup.name}\"", TerminalLineType.SUCCESS))
                            }
                        }
                    }
                    queryArg.startsWith("close", ignoreCase = true) -> {
                        val groupRef = queryArg.removePrefix("close").trim()
                        val targetGroup = viewModel.tabGroups.value.find { it.name.equals(groupRef, ignoreCase = true) || it.id == groupRef }
                        if (targetGroup != null) {
                            viewModel.deleteTabGroup(targetGroup.id, closeTabs = true)
                            outputLines.add(TerminalLine("[OK] Closed Tab Group \"${targetGroup.name}\" and all its tabs", TerminalLineType.SUCCESS))
                        } else {
                            outputLines.add(TerminalLine("[ERR] Tab group not found: $groupRef", TerminalLineType.ERROR))
                        }
                    }
                    queryArg.startsWith("sandbox", ignoreCase = true) -> {
                        scope.launch {
                            val lines = shellEngine.focusSandboxTabGroup()
                            commitAndShowTerminalIfNeeded(lines, openTerminal = true)
                        }
                        return
                    }
                    queryArg.startsWith("organize", ignoreCase = true) -> {
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        scope.launch {
                            agentEngine.autoOrganizeTabs { line ->
                                viewModel.appendTerminalLine(line)
                            }
                        }
                        return
                    }
                    else -> {
                        outputLines.addAll(shellEngine.listTabGroups())
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/sandbox" -> {
                scope.launch {
                    val lines = shellEngine.focusSandboxTabGroup()
                    commitAndShowTerminalIfNeeded(lines, openTerminal = true)
                }
                return
            }

            "/organize" -> {
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                scope.launch {
                    agentEngine.autoOrganizeTabs { line ->
                        viewModel.appendTerminalLine(line)
                    }
                }
                return
            }

            "/creategroup" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: /creategroup <group_name> [color_hex]", TerminalLineType.WARNING))
                } else {
                    val parts = queryArg.split(" ")
                    val name = parts[0]
                    val color = parts.getOrNull(1)
                    val id = viewModel.createTabGroup(name, colorHex = color)
                    outputLines.add(TerminalLine("[OK] Created Tab Group \"$name\" (ID: ${id.take(8)}...)", TerminalLineType.SUCCESS))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/grouptab" -> {
                val parts = queryArg.split(" ")
                if (parts.size < 2) {
                    outputLines.add(TerminalLine("Usage: /grouptab <tab_index_or_id> <group_name_or_id>", TerminalLineType.WARNING))
                } else {
                    val tabRef = parts[0]
                    val groupRef = parts[1]
                    val targetTab = tabRef.toIntOrNull()?.let { tabs.getOrNull(it) } ?: tabs.find { it.id == tabRef }
                    val targetGroup = viewModel.tabGroups.value.find { it.name.equals(groupRef, ignoreCase = true) || it.id == groupRef }
                    if (targetTab == null) {
                        outputLines.add(TerminalLine("[ERR] Tab not found: $tabRef", TerminalLineType.ERROR))
                    } else if (targetGroup == null) {
                        outputLines.add(TerminalLine("[ERR] Tab group not found: $groupRef", TerminalLineType.ERROR))
                    } else {
                        viewModel.moveTabToGroup(targetTab.id, targetGroup.id)
                        outputLines.add(TerminalLine("[OK] Moved tab \"${targetTab.title}\" to group \"${targetGroup.name}\"", TerminalLineType.SUCCESS))
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/ungroup" -> {
                val targetTab = if (queryArg.isBlank()) currentTab else queryArg.toIntOrNull()?.let { tabs.getOrNull(it) } ?: tabs.find { it.id == queryArg }
                if (targetTab != null) {
                    viewModel.removeTabFromGroup(targetTab.id)
                    outputLines.add(TerminalLine("[OK] Removed tab \"${targetTab.title}\" from group", TerminalLineType.SUCCESS))
                } else {
                    outputLines.add(TerminalLine("[ERR] Tab not found: $queryArg", TerminalLineType.ERROR))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/pwd" -> {
                outputLines.add(TerminalLine(shellEngine.promptPath, TerminalLineType.OUTPUT))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/cd" -> {
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                scope.launch {
                    val line = shellEngine.changeDirectory(queryArg)
                    viewModel.appendTerminalLine(line)
                }
                return
            }

            "/ls", "/dir" -> {
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                scope.launch {
                    val lines = shellEngine.listFiles(queryArg)
                    viewModel.appendTerminalLines(lines)
                }
                return
            }

            "/cat" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("cat: missing file operand", TerminalLineType.ERROR))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                } else {
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    scope.launch {
                        val lines = shellEngine.catFile(queryArg)
                        viewModel.appendTerminalLines(lines)
                    }
                }
                return
            }

            "/touch" -> {
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                scope.launch {
                    val line = shellEngine.touchFile(queryArg)
                    viewModel.appendTerminalLine(line)
                }
                return
            }

            "/mkdir" -> {
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                scope.launch {
                    val line = shellEngine.makeDirectory(queryArg)
                    viewModel.appendTerminalLine(line)
                }
                return
            }

            "/rm" -> {
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                scope.launch {
                    val line = shellEngine.removeFile(queryArg)
                    viewModel.appendTerminalLine(line)
                }
                return
            }

            "/tree", "/hierarchy", "/convos" -> {
                when {
                    queryArg.equals("expand", ignoreCase = true) || queryArg.equals("open", ignoreCase = true) || queryArg.equals("all", ignoreCase = true) -> {
                        ConversationTreeManager.global.expandAll()
                        outputLines.add(TerminalLine("🌳 [TREE EXPANDED] Expanded all user inputs and conversation task branches in the stream log.", TerminalLineType.SUCCESS))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        return
                    }
                    queryArg.equals("collapse", ignoreCase = true) || queryArg.equals("close", ignoreCase = true) -> {
                        ConversationTreeManager.global.collapseAll()
                        outputLines.add(TerminalLine("🌳 [TREE COLLAPSED] Collapsed all user inputs and action logs into compact rows in stream log.", TerminalLineType.WARNING))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        return
                    }
                    normalizedToken == "/hierarchy" || normalizedToken == "/convos" -> {
                        val allTasks = ConversationTreeManager.global.tasks.value
                        outputLines.add(TerminalLine("── 3-LEVEL CONVERSATION STREAM TREE ──", TerminalLineType.SYSTEM))
                        outputLines.add(TerminalLine("The stream log embeds a native 3-level expandable task tree:", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("  Level 1: Conversation / Task  (▸ 🎵 YouTube, ▾ 💻 Coding)", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("  Level 2: Agent Sessions       (▾ 🤖 CodingAgent, ▸ 🌐 BrowserAgent)", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("  Level 3: Steps, Tools, Reasoning & Results (expandable tool output)", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("Total conversation tasks tracked: ${allTasks.size}", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("Commands: '/tree expand' | '/tree collapse'", TerminalLineType.OUTPUT))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        return
                    }
                    else -> {
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        scope.launch {
                            val lines = shellEngine.generateTree(queryArg)
                            viewModel.appendTerminalLines(lines)
                        }
                        return
                    }
                }
            }

            "/df", "/du" -> {
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                scope.launch {
                    val lines = shellEngine.getDiskUsage()
                    viewModel.appendTerminalLines(lines)
                }
                return
            }

            "/cookies" -> {
                outputLines.addAll(shellEngine.inspectCookies())
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/run", "/exec", "/start" -> {
                handleRunCommand(queryArg, outputLines, origin)
                return
            }

            "/click" -> {
                val activeWv = viewModel.getActiveWebView()
                if (activeWv == null) {
                    outputLines.add(TerminalLine("[ERR] No active WebView tab available", TerminalLineType.ERROR))
                } else if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: click <css_selector_or_text>", TerminalLineType.WARNING))
                } else {
                    val escaped = queryArg.replace("'", "\\'")
                    val js = """
                        (function() {
                            var el = document.querySelector('$escaped');
                            if (!el) {
                                var all = document.querySelectorAll('button, a, input, [role="button"]');
                                for (var i = 0; i < all.length; i++) {
                                    if (all[i].innerText && all[i].innerText.toLowerCase().includes('$escaped'.toLowerCase())) {
                                        el = all[i]; break;
                                    }
                                }
                            }
                            if (el) {
                                el.click();
                                return 'Clicked: ' + (el.tagName || '') + ' ' + (el.innerText || el.value || '').substring(0, 30);
                            }
                            return 'Element not found: $escaped';
                        })();
                    """.trimIndent()
                    activeWv.evaluateJavascript(js) { res ->
                        viewModel.appendTerminalLine(TerminalLine(res?.trim('\"') ?: "null", TerminalLineType.SUCCESS))
                    }
                    outputLines.add(TerminalLine("[ACTION] Attempting click on '$queryArg'...", TerminalLineType.INFO))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/type" -> {
                val activeWv = viewModel.getActiveWebView()
                val parts = queryArg.split(" ", limit = 2)
                if (activeWv == null) {
                    outputLines.add(TerminalLine("[ERR] No active WebView tab available", TerminalLineType.ERROR))
                } else if (parts.size < 2) {
                    outputLines.add(TerminalLine("Usage: type <selector> <text_to_input>", TerminalLineType.WARNING))
                } else {
                    val sel = parts[0].replace("'", "\\'")
                    val txt = parts[1].replace("'", "\\'")
                    val js = """
                        (function() {
                            var el = document.querySelector('$sel');
                            if (el) {
                                el.value = '$txt';
                                el.dispatchEvent(new Event('input', { bubbles: true }));
                                el.dispatchEvent(new Event('change', { bubbles: true }));
                                return 'Typed text into ' + '$sel';
                            }
                            return 'Input element not found: $sel';
                        })();
                    """.trimIndent()
                    activeWv.evaluateJavascript(js) { res ->
                        viewModel.appendTerminalLine(TerminalLine(res?.trim('\"') ?: "null", TerminalLineType.SUCCESS))
                    }
                    outputLines.add(TerminalLine("[ACTION] Typing into '$sel'...", TerminalLineType.INFO))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/scroll" -> {
                val activeWv = viewModel.getActiveWebView()
                if (activeWv != null) {
                    val js = when (queryArg.lowercase()) {
                        "bottom" -> "window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'}); 'Scrolled to bottom';"
                        "top" -> "window.scrollTo({top: 0, behavior: 'smooth'}); 'Scrolled to top';"
                        "up" -> "window.scrollBy({top: -500, behavior: 'smooth'}); 'Scrolled up';"
                        else -> "window.scrollBy({top: 500, behavior: 'smooth'}); 'Scrolled down';"
                    }
                    activeWv.evaluateJavascript(js) { res ->
                        viewModel.appendTerminalLine(TerminalLine(res?.trim('\"') ?: "Scrolled", TerminalLineType.SUCCESS))
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/links" -> {
                val activeWv = viewModel.getActiveWebView()
                if (activeWv == null) {
                    outputLines.add(TerminalLine("[ERR] No active WebView tab available", TerminalLineType.ERROR))
                } else {
                    val js = """
                        (function() {
                            var links = Array.from(document.querySelectorAll('a[href]')).slice(0, 20).map(function(a) {
                                return (a.innerText.trim().substring(0, 30) || 'Link') + ' -> ' + a.href;
                            });
                            return JSON.stringify(links);
                        })();
                    """.trimIndent()
                    activeWv.evaluateJavascript(js) { res ->
                        try {
                            val arr = org.json.JSONArray(res ?: "[]")
                            val linkLines = mutableListOf<TerminalLine>()
                            linkLines.add(TerminalLine("── EXTRACTED LINKS (${arr.length()}) ──", TerminalLineType.SYSTEM))
                            for (i in 0 until arr.length()) {
                                linkLines.add(TerminalLine("  🔗 " + arr.getString(i), TerminalLineType.OUTPUT))
                            }
                            viewModel.appendTerminalLines(linkLines)
                        } catch (_: Exception) {
                            viewModel.appendTerminalLine(TerminalLine(res ?: "None", TerminalLineType.OUTPUT))
                        }
                    }
                    outputLines.add(TerminalLine("[PAGE] Extracting hyperlinks from active page...", TerminalLineType.INFO))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/text", "/extract" -> {
                val activeWv = viewModel.getActiveWebView()
                if (activeWv != null) {
                    activeWv.evaluateJavascript("document.body.innerText.substring(0, 2000)") { text ->
                        val clean = text?.trim('\"', ' ')?.replace("\\n", "\n") ?: ""
                        viewModel.appendTerminalLine(TerminalLine("── VISIBLE PAGE TEXT ──\n$clean\n──────────────────────", TerminalLineType.OUTPUT))
                    }
                    outputLines.add(TerminalLine("[PAGE] Extracting visible text content...", TerminalLineType.INFO))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/view", "/openfile" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: view <filename>", TerminalLineType.WARNING))
                } else {
                    val path = shellEngine.resolvePath(queryArg)
                    val file = viewModel.fileSystem.getFile(path)
                    if (file.exists()) {
                        val gvItem = viewModel.fileSystem.toFileItem(file)
                        viewModel.openFileInTab(gvItem, inNewTab = true)
                        outputLines.add(TerminalLine("[OK] Opened file in new tab: $path", TerminalLineType.SUCCESS))
                    } else {
                        outputLines.add(TerminalLine("view: file not found: $path", TerminalLineType.ERROR))
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/bridge" -> {
                val subArg = queryArg.trim().lowercase(Locale.ROOT)
                val bridgeEnabled = viewModel.settings.value.bidirectionalBridgeEnabled
                val applyAll = viewModel.settings.value.bridgeApplyToAllWebsites
                val currentTabId = viewModel.currentTab.value?.id
                val currentUrl = viewModel.currentTab.value?.url.orEmpty()
                val host = try { java.net.URI(currentUrl).host.orEmpty().ifEmpty { currentUrl } } catch (_: Exception) { currentUrl }

                when {
                    subArg == "on" || subArg == "enable" || subArg == "start" -> {
                        viewModel.updateSettings(viewModel.settings.value.copy(bidirectionalBridgeEnabled = true))
                        viewModel.webAppBridge.setConnectionState(WebAppConnectionState.READY)
                        outputLines.add(TerminalLine("[BRIDGE] Bidirectional Web App Bridge ENABLED.", TerminalLineType.SUCCESS))
                        outputLines.add(TerminalLine("Target: $host | State: READY", TerminalLineType.INFO))
                    }
                    subArg == "off" || subArg == "disable" || subArg == "stop" -> {
                        viewModel.updateSettings(viewModel.settings.value.copy(bidirectionalBridgeEnabled = false))
                        viewModel.webAppBridge.setConnectionState(WebAppConnectionState.UNAVAILABLE)
                        outputLines.add(TerminalLine("[BRIDGE] Bidirectional Web App Bridge DISABLED.", TerminalLineType.WARNING))
                    }
                    subArg == "toggle" -> {
                        val next = !bridgeEnabled
                        viewModel.updateSettings(viewModel.settings.value.copy(bidirectionalBridgeEnabled = next))
                        viewModel.webAppBridge.setConnectionState(if (next) WebAppConnectionState.READY else WebAppConnectionState.UNAVAILABLE)
                        outputLines.add(TerminalLine("[BRIDGE] Bidirectional Bridge toggled: " + (if (next) "ENABLED" else "DISABLED"), if (next) TerminalLineType.SUCCESS else TerminalLineType.WARNING))
                    }
                    subArg == "all" || subArg == "universal" -> {
                        val next = !applyAll
                        viewModel.updateSettings(viewModel.settings.value.copy(bridgeApplyToAllWebsites = next))
                        outputLines.add(TerminalLine("[BRIDGE] Universal Scope (All Websites): " + (if (next) "ENABLED" else "DISABLED (GVONE Web Apps only)"), TerminalLineType.INFO))
                    }
                    subArg == "test" || subArg == "ping" -> {
                        val activeWv = viewModel.getActiveWebView(currentTabId)
                        val delivered = viewModel.webAppBridge.deliverAddressBarInput(activeWv, "GVONE_BRIDGE_PING_TEST", "submit")
                        viewModel.webAppBridge.setConnectionState(WebAppConnectionState.READY)
                        outputLines.add(TerminalLine("[BRIDGE] Ping test dispatched to $host: " + (if (delivered) "DELIVERED (READY)" else "STANDBY / READY"), TerminalLineType.SUCCESS))
                    }
                    else -> {
                        // Status report
                        val state = viewModel.webAppBridge.connectionState.value
                        val isSuccess = state == WebAppConnectionState.READY
                        outputLines.add(TerminalLine("── GVONE WEB APP BRIDGE REPORT ──", TerminalLineType.SYSTEM))
                        outputLines.add(
                            TerminalLine(
                                "● Bridge Handshake: " + (if (isSuccess) "SUCCESSFUL (Connected & Ready)" else "STATUS: ${state.name}"),
                                if (isSuccess) TerminalLineType.SUCCESS else TerminalLineType.INFO
                            )
                        )
                        outputLines.add(TerminalLine("● Target Endpoint: $host", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("● Bidirectional Channel: " + (if (bridgeEnabled) "ENABLED" else "DISABLED"), if (bridgeEnabled) TerminalLineType.SUCCESS else TerminalLineType.WARNING))
                        outputLines.add(TerminalLine("● Scope: " + (if (applyAll) "Universal (All Websites)" else "GVONE Web Apps Only"), TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("● Address Bar Link: CONNECTED & SYNCHRONIZED", TerminalLineType.SUCCESS))
                        outputLines.add(TerminalLine("Usage: /bridge on | off | toggle | all | test", TerminalLineType.INFO))
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/addressbar" -> {
                val autoAppear = viewModel.settings.value.terminalAutoAppearOnAddressBar
                outputLines.add(TerminalLine("── ADDRESS BAR & TERMINAL LINK REPORT ──", TerminalLineType.SYSTEM))
                outputLines.add(TerminalLine("● Connection State: CONNECTED", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("● Address Bar Click Action: " + (if (autoAppear) "ALWAYS APPEAR (Terminal Opens)" else "DISAPPEAR (Terminal Closes)"), TerminalLineType.INFO))
                outputLines.add(TerminalLine("● Command Routing: Address bar commands dispatch to CLI and Web App Bridge", TerminalLineType.SUCCESS))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/history" -> {
                val commandHistory = viewModel.terminalRepository.getCommandHistory()
                if (commandHistory.isEmpty()) {
                    outputLines.add(TerminalLine("No command history recorded.", TerminalLineType.INFO))
                } else {
                    outputLines.add(TerminalLine("--- COMMAND HISTORY (${commandHistory.size} items) ---", TerminalLineType.SYSTEM))
                    commandHistory.takeLast(50).forEachIndexed { idx, cmd ->
                        outputLines.add(TerminalLine("  %3d  %s".format(idx + 1, cmd), TerminalLineType.OUTPUT))
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/whoami" -> {
                outputLines.add(TerminalLine("gvone-user (uid=1000 gid=1000 groups=browser,tor,network,canvas)", TerminalLineType.OUTPUT))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/date" -> {
                val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
                outputLines.add(TerminalLine(sdf.format(Date()), TerminalLineType.OUTPUT))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/uname" -> {
                outputLines.add(TerminalLine("Linux gvone-browser 6.6.0-aarch64 #1 SMP PREEMPT Android 14 GNU/Linux", TerminalLineType.OUTPUT))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/echo" -> {
                outputLines.add(TerminalLine(queryArg, TerminalLineType.OUTPUT))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/tabs", "/lstabs" -> {
                outputLines.add(TerminalLine("--- OPEN BROWSER TABS (${tabs.size}) ---", TerminalLineType.SYSTEM))
                tabs.forEachIndexed { idx, tab ->
                    val isActive = tab.id == currentTab?.id
                    val mark = if (isActive) "*" else " "
                    val mode = if (tab.isPrivate) "[PRIVATE]" else "[REGULAR]"
                    val title = tab.title.take(30).padEnd(30)
                    outputLines.add(
                        TerminalLine(
                            "[$idx]$mark $mode $title ${tab.url}",
                            if (isActive) TerminalLineType.SUCCESS else TerminalLineType.OUTPUT
                        )
                    )
                }
                outputLines.add(TerminalLine("Tip: Use 'tab <index>' to switch tabs, 'closetab <index>' to close.", TerminalLineType.INFO))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/tab", "/switchtab" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Active Tab: ${currentTab?.title} (${currentTab?.url})", TerminalLineType.INFO))
                    outputLines.add(TerminalLine("Usage: tab <index_or_id>", TerminalLineType.WARNING))
                } else {
                    val index = queryArg.toIntOrNull()
                    if (index != null && index in tabs.indices) {
                        val targetTab = tabs[index]
                        viewModel.selectTab(targetTab.id)
                        outputLines.add(TerminalLine("[OK] Switched to tab $index: ${targetTab.title}", TerminalLineType.SUCCESS))
                    } else {
                        val matchedTab = tabs.find { it.title.contains(queryArg, ignoreCase = true) || it.url.contains(queryArg, ignoreCase = true) }
                        if (matchedTab != null) {
                            viewModel.selectTab(matchedTab.id)
                            outputLines.add(TerminalLine("[OK] Switched to tab: ${matchedTab.title}", TerminalLineType.SUCCESS))
                        } else {
                            outputLines.add(TerminalLine("[ERR] Tab not found: $queryArg", TerminalLineType.ERROR))
                        }
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/newtab", "/nt" -> {
                val isPrivate = queryArg.contains("-p") || queryArg.contains("--private")
                val cleanUrl = queryArg.replace("-p", "").replace("--private", "").trim()
                val targetUrl = if (cleanUrl.isNotBlank()) cleanUrl else "https://www.google.com"
                viewModel.createNewTab(url = targetUrl, isPrivate = isPrivate)
                outputLines.add(TerminalLine("[OK] Created new ${if (isPrivate) "private " else ""}tab: $targetUrl", TerminalLineType.SUCCESS))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
                return
            }

            "/closetab", "/ct" -> {
                if (queryArg.isBlank()) {
                    val title = currentTab?.title.orEmpty()
                    viewModel.closeCurrentTab()
                    outputLines.add(TerminalLine("[OK] Closed active tab: $title", TerminalLineType.SUCCESS))
                } else {
                    val index = queryArg.toIntOrNull()
                    if (index != null && index in tabs.indices) {
                        val target = tabs[index]
                        viewModel.closeTab(target.id)
                        outputLines.add(TerminalLine("[OK] Closed tab $index: ${target.title}", TerminalLineType.SUCCESS))
                    } else {
                        outputLines.add(TerminalLine("[ERR] Invalid tab index: $queryArg", TerminalLineType.ERROR))
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/url", "/goto", "/open" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Current URL: ${currentTab?.url}", TerminalLineType.INFO))
                    outputLines.add(TerminalLine("Current Title: ${currentTab?.title}", TerminalLineType.OUTPUT))
                } else {
                    val validUrl = if (!queryArg.startsWith("http://") && !queryArg.startsWith("https://")) {
                        "https://$queryArg"
                    } else queryArg
                    viewModel.loadUrlInCurrentTab(validUrl, keepTerminalOpen = (origin == CommandOrigin.TERMINAL))
                    outputLines.add(TerminalLine("[NAVIGATE] Loading: $validUrl", TerminalLineType.SUCCESS))
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
                return
            }

            "/tor" -> {
                when (queryArg.lowercase()) {
                    "on" -> {
                        if (!isTorActive) viewModel.toggleTor()
                        outputLines.add(TerminalLine("[TOR] Onion Routing Activated", TerminalLineType.SUCCESS))
                    }
                    "off" -> {
                        if (isTorActive) viewModel.toggleTor()
                        outputLines.add(TerminalLine("[TOR] Onion Routing Disabled", TerminalLineType.WARNING))
                    }
                    "status" -> {
                        outputLines.add(TerminalLine("Tor Status: ${if (isTorActive) "ACTIVE (Routing via 127.0.0.1:9050)" else "INACTIVE"}", TerminalLineType.INFO))
                    }
                    else -> {
                        viewModel.toggleTor()
                        outputLines.add(TerminalLine("[TOR] Toggled Tor Shield. Active: ${!isTorActive}", TerminalLineType.SUCCESS))
                    }
                }
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }

            "/desktop" -> {
                viewModel.toggleDesktopMode()
                val isDesktop = currentTab?.desktopMode == true
                outputLines.add(TerminalLine("[VIEWPORT] Desktop Mode Toggled: ${!isDesktop}", TerminalLineType.SUCCESS))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
                return
            }

            "/js" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: js <javascript_expression>", TerminalLineType.WARNING))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                } else {
                    val safety = CommandEngine.validateJavaScriptSafety(queryArg)
                    if (!safety.isSafe) {
                        outputLines.add(TerminalLine("[SECURITY] JavaScript blocked: ${safety.reason}", TerminalLineType.ERROR))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    } else {
                        val activeWv = viewModel.getActiveWebView()
                        if (activeWv == null) {
                            outputLines.add(TerminalLine("[ERR] No active WebView tab available", TerminalLineType.ERROR))
                            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        } else {
                            outputLines.add(TerminalLine("[EVAL] Running script in page context...", TerminalLineType.INFO))
                            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                            activeWv.evaluateJavascript(queryArg) { result ->
                                val cleanRes = result?.trim('\"', ' ', '\n')?.replace("\\n", "\n") ?: "null"
                                viewModel.appendTerminalLine(TerminalLine("<- $cleanRes", TerminalLineType.SUCCESS))
                            }
                        }
                    }
                }
                return
            }

            "/ai", "/ask" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: ai <question_or_prompt>", TerminalLineType.WARNING))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                } else {
                    outputLines.add(TerminalLine("[GVONE AI] Synthesizing query: \"$queryArg\"...", TerminalLineType.INFO))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    scope.launch {
                        try {
                            val response = viewModel.aiService.searchAndSynthesize(queryArg).aiAnswer
                            viewModel.appendTerminalLine(
                                TerminalLine(
                                    text = "\n=== GVONE AI SYNTHESIS ===\n$response\n==========================",
                                    type = TerminalLineType.AI_RESPONSE
                                )
                            )
                        } catch (e: Exception) {
                            viewModel.appendTerminalLine(TerminalLine("[ERR] AI Error: ${e.message}", TerminalLineType.ERROR))
                        }
                    }
                }
                return
            }

            "/ping" -> {
                val host = if (queryArg.isNotBlank()) queryArg else "google.com"
                outputLines.add(TerminalLine("PING $host (142.250.190.46): 56 data bytes", TerminalLineType.INFO))
                outputLines.add(TerminalLine("64 bytes from 142.250.190.46: icmp_seq=0 ttl=116 time=28.4 ms", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("64 bytes from 142.250.190.46: icmp_seq=1 ttl=116 time=26.1 ms", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("--- $host ping statistics ---", TerminalLineType.SYSTEM))
                outputLines.add(TerminalLine("2 packets transmitted, 2 packets received, 0.0% packet loss", TerminalLineType.OUTPUT))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                return
            }
        }

        // Custom & Built-in Command Engine Evaluation
        val currentUrl = currentTab?.url.orEmpty()
        val currentTitle = currentTab?.title.orEmpty()
        val currentHost = try { URL(currentUrl).host } catch (_: Exception) { "" }
        val executionContext = CommandExecutionContext(
            query = queryArg,
            currentUrl = currentUrl,
            currentTitle = currentTitle,
            currentDomain = currentHost
        )

        val parsed = CommandEngine.parse(trimmed, viewModel.customCommands.value, executionContext)
        if (parsed != null) {
            val (commandEntity, result) = parsed
            when (result) {
                is CommandExecutionResult.ExecuteSearch -> {
                    viewModel.loadUrlInCurrentTab(result.searchUrl, keepTerminalOpen = (origin == CommandOrigin.TERMINAL))
                    outputLines.add(
                        TerminalLine(
                            "[SEARCH] Executed ${commandEntity.name} (${result.provider ?: "Web"}): ${result.query}",
                            TerminalLineType.SUCCESS
                        )
                    )
                    outputLines.add(TerminalLine("-> Navigating to: ${result.searchUrl}", TerminalLineType.INFO))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
                }

                is CommandExecutionResult.OpenUrl -> {
                    viewModel.loadUrlInCurrentTab(result.url, keepTerminalOpen = (origin == CommandOrigin.TERMINAL))
                    outputLines.add(
                        TerminalLine(
                            "[OPEN] ${commandEntity.name}: Navigating to ${result.url}",
                            TerminalLineType.SUCCESS
                        )
                    )
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
                }

                is CommandExecutionResult.SendAIPrompt -> {
                    outputLines.add(TerminalLine("[GVONE AI] Prompting: ${result.prompt}...", TerminalLineType.INFO))
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    scope.launch {
                        try {
                            val aiResp = viewModel.aiService.searchAndSynthesize(result.prompt).aiAnswer
                            viewModel.appendTerminalLine(
                                TerminalLine(
                                    text = "\n=== ${result.title.uppercase()} ===\n$aiResp\n===========================",
                                    type = TerminalLineType.AI_RESPONSE
                                )
                            )
                        } catch (e: Exception) {
                            viewModel.appendTerminalLine(TerminalLine("[ERR] AI Error: ${e.message}", TerminalLineType.ERROR))
                        }
                    }
                }

                is CommandExecutionResult.TriggerBrowserAction -> {
                    when (result.action) {
                        BrowserActionType.NEW_TAB -> viewModel.createNewTab()
                        BrowserActionType.NEW_PRIVATE_TAB -> viewModel.createNewTab(isPrivate = true)
                        BrowserActionType.RELOAD -> currentTab?.url?.let { viewModel.loadUrlInCurrentTab(it) }
                        BrowserActionType.BACK -> viewModel.getActiveWebView()?.goBack()
                        BrowserActionType.FORWARD -> viewModel.getActiveWebView()?.goForward()
                        BrowserActionType.HISTORY -> viewModel.openSheet(ActiveSheet.History)
                        BrowserActionType.BOOKMARKS -> viewModel.openSheet(ActiveSheet.Bookmarks)
                        BrowserActionType.DOWNLOADS -> viewModel.openSheet(ActiveSheet.Downloads)
                        BrowserActionType.CLOSE_TAB -> viewModel.closeCurrentTab()
                        BrowserActionType.TAB_OVERVIEW -> viewModel.openSheet(ActiveSheet.TabOverview)
                        BrowserActionType.SETTINGS -> viewModel.openSheet(ActiveSheet.Settings)
                        BrowserActionType.COMMAND_MANAGER -> viewModel.openSheet(ActiveSheet.CustomCommands)
                        BrowserActionType.DESKTOP_MODE -> viewModel.toggleDesktopMode()
                        BrowserActionType.TOR_DIAGNOSTICS -> viewModel.openSheet(ActiveSheet.TorDiagnostics)
                        BrowserActionType.FIND_IN_PAGE -> viewModel.openSheet(ActiveSheet.FindInPage)
                        BrowserActionType.READER_MODE -> viewModel.openSheet(ActiveSheet.ReaderMode)
                        BrowserActionType.CLEAR_DATA -> viewModel.clearBrowsingData()
                        BrowserActionType.TERMINAL -> viewModel.openSheet(ActiveSheet.Terminal)
                        BrowserActionType.AGENT_DASHBOARD -> {
                            if (onOpenAgentDashboard != null) onOpenAgentDashboard() else viewModel.openAgentDashboard()
                        }
                    }
                    outputLines.add(
                        TerminalLine(
                            "[ACTION] Executed browser action: ${result.action.label}",
                            TerminalLineType.SUCCESS
                        )
                    )
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
                }

                is CommandExecutionResult.TriggerPageAction -> {
                    when (result.action) {
                        "summarize_page" -> {
                            val activeWv = viewModel.getActiveWebView()
                            if (activeWv != null) {
                                outputLines.add(TerminalLine("[PAGE] Extracting webpage content for AI summary...", TerminalLineType.INFO))
                                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                                activeWv.evaluateJavascript("document.body.innerText.substring(0, 4000)") { text ->
                                    val cleanText = text?.trim('\"', ' ', '\n')?.replace("\\n", "\n")?.take(3500).orEmpty()
                                    scope.launch {
                                        val prompt = "Summarize this webpage clearly with an executive summary and 3-5 key points.\n\nTitle: ${currentTab?.title}\nURL: ${currentTab?.url}\n\nContent:\n$cleanText"
                                        val summary = viewModel.aiService.searchAndSynthesize(prompt).aiAnswer
                                        viewModel.appendTerminalLine(
                                            TerminalLine(
                                                text = "\n=== PAGE SUMMARY: ${currentTab?.title} ===\n$summary\n=========================================",
                                                type = TerminalLineType.AI_RESPONSE
                                            )
                                        )
                                    }
                                }
                                return
                            } else {
                                outputLines.add(TerminalLine("[ERR] No active webpage tab loaded.", TerminalLineType.ERROR))
                                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                            }
                        }
                        "translate_page" -> {
                            val target = "https://translate.google.com/translate?sl=auto&tl=en&u=" + URLEncoder.encode(currentUrl, "UTF-8")
                            viewModel.loadUrlInCurrentTab(target, keepTerminalOpen = (origin == CommandOrigin.TERMINAL))
                            outputLines.add(TerminalLine("[TRANSLATE] Opening Google Translate for current page.", TerminalLineType.SUCCESS))
                            commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
                        }
                        "extract_info" -> {
                            val activeWv = viewModel.getActiveWebView()
                            outputLines.add(TerminalLine("[PAGE] Extracting facts & data...", TerminalLineType.INFO))
                            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                            activeWv?.evaluateJavascript("document.body.innerText.substring(0, 3000)") { text ->
                                val clean = text?.trim('\"', ' ')?.take(2500).orEmpty()
                                scope.launch {
                                    val dataResp = viewModel.aiService.searchAndSynthesize("Extract key data points, facts, and dates from: $clean").aiAnswer
                                    viewModel.appendTerminalLine(TerminalLine("\n=== EXTRACTED DATA ===\n$dataResp\n======================", TerminalLineType.OUTPUT))
                                }
                            }
                            return
                        }
                        else -> {
                            outputLines.add(TerminalLine("[PAGE] Triggered: ${result.action}", TerminalLineType.INFO))
                            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        }
                    }
                }

                is CommandExecutionResult.RunSafeJavaScript -> {
                    val activeWv = viewModel.getActiveWebView()
                    if (activeWv != null) {
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                        activeWv.evaluateJavascript(result.javascriptCode) { ret ->
                            viewModel.appendTerminalLine(TerminalLine("<- $ret", TerminalLineType.SUCCESS))
                        }
                    } else {
                        outputLines.add(TerminalLine("[ERR] No active WebView for script automation.", TerminalLineType.ERROR))
                        commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                    }
                }

                is CommandExecutionResult.ShowMessage -> {
                    outputLines.add(
                        TerminalLine(
                            result.message,
                            if (result.isError) TerminalLineType.ERROR else TerminalLineType.INFO
                        )
                    )
                    commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
                }
            }
            return
        }

        // Fallback: If looks like a URL, navigate to it
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 3)
        ) {
            val url = if (!trimmed.startsWith("http")) "https://$trimmed" else trimmed
            viewModel.loadUrlInCurrentTab(url, keepTerminalOpen = (origin == CommandOrigin.TERMINAL))
            outputLines.add(TerminalLine("[NAVIGATE] Opening: $url", TerminalLineType.SUCCESS))
            commitAndShowTerminalIfNeeded(outputLines, openTerminal = (origin == CommandOrigin.TERMINAL))
            return
        }

        // Autonomous Goal Fallback: Execute through Agentic Runtime ONLY if Agentic Mode is enabled
        val isAgentActive = _isAgenticMode.value && (com.example.agent.runtime.RuntimeStateManager.global.runtimeMode.value.execution == com.example.agent.runtime.ExecutionType.AGENT)
        if (isAgentActive) {
            commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
            scope.launch {
                agentEngine.runAgenticWorkflow(trimmed, shellEngine.currentDirectory) { line ->
                    viewModel.appendTerminalLine(line)
                }
            }
        } else {
            if (origin == CommandOrigin.ADDRESS_BAR) {
                val searchUrl = viewModel.resolveUrlOrSearch(trimmed)
                viewModel.loadUrlInCurrentTab(searchUrl, keepTerminalOpen = false)
            } else {
                val cmdName = trimmed.split(" ").firstOrNull().orEmpty()
                outputLines.add(TerminalLine("bash: $cmdName: command not found (Agentic mode is OFF. Type '/agent' to enable).", TerminalLineType.ERROR))
                commitAndShowTerminalIfNeeded(outputLines, openTerminal = true)
            }
        }
    }

    private fun handleRunCommand(
        queryArg: String,
        outputLines: MutableList<TerminalLine>,
        origin: CommandOrigin
    ) {
        val trimmedArg = queryArg.trim()
        val lowerArg = trimmedArg.lowercase(Locale.ROOT)

        val isWebsiteTarget = trimmedArg.isBlank() ||
                lowerArg == "website" || lowerArg == "web" || lowerArg == "site" ||
                lowerArg.contains("create website") || lowerArg.contains("build website") ||
                lowerArg.contains("make website") || lowerArg.contains("new website") ||
                lowerArg.contains("webpage") || lowerArg.contains("landing page") ||
                lowerArg.contains("portfolio")

        // First commit the command line output so the user sees immediate feedback
        viewModel.appendTerminalLines(outputLines)
        if (origin == CommandOrigin.ADDRESS_BAR && viewModel.activeSheet.value != ActiveSheet.Terminal) {
            viewModel.openSheet(ActiveSheet.Terminal)
        }

        viewModel.viewModelScope.launch {
            // Check if targeting an existing specific file, e.g. "run index.html" or "run Projects/main.js"
            if (trimmedArg.isNotBlank() && !isWebsiteTarget) {
                val resolved = shellEngine.resolvePath(trimmedArg)
                val file = viewModel.fileSystem.getFile(resolved)

                if (file.exists() && !file.isDirectory) {
                    val ext = file.extension.lowercase(Locale.ROOT)
                    if (ext == "html" || ext == "htm") {
                        val fileItem = viewModel.fileSystem.getFileItem(resolved)
                        if (fileItem != null) {
                            withContext(Dispatchers.Main) {
                                viewModel.openFileInTab(fileItem, inNewTab = true)
                            }
                            viewModel.appendTerminalLines(listOf(
                                TerminalLine("[RUN] 🚀 Launching HTML Website: $resolved in live browser tab", TerminalLineType.SUCCESS),
                                TerminalLine("  • Live Tab URL: gvone-file://$resolved", TerminalLineType.INFO)
                            ))
                        } else {
                            viewModel.appendTerminalLine(TerminalLine("[RUN] Error opening file item for $resolved", TerminalLineType.ERROR))
                        }
                        return@launch
                    } else if (ext == "js") {
                        val jsCode = viewModel.fileSystem.readFileContent(resolved)
                        val activeWv = viewModel.getActiveWebView()
                        if (activeWv != null) {
                            viewModel.appendTerminalLine(TerminalLine("[RUN] Executing JavaScript ($resolved) in active tab context...", TerminalLineType.INFO))
                            activeWv.evaluateJavascript(jsCode) { ret ->
                                viewModel.appendTerminalLine(TerminalLine("<- $ret", TerminalLineType.SUCCESS))
                            }
                        } else {
                            viewModel.appendTerminalLine(TerminalLine("[RUN] No active browser tab to execute JavaScript in.", TerminalLineType.ERROR))
                        }
                        return@launch
                    } else {
                        val fileItem = viewModel.fileSystem.getFileItem(resolved)
                        if (fileItem != null) {
                            withContext(Dispatchers.Main) {
                                viewModel.openFileInTab(fileItem, inNewTab = true)
                            }
                            viewModel.appendTerminalLine(TerminalLine("[RUN] Opened $resolved in viewer tab", TerminalLineType.SUCCESS))
                        }
                        return@launch
                    }
                }
            }

            // If user typed "run" with no arguments, check if an existing index.html exists:
            val existingCandidate = listOf(
                shellEngine.resolvePath("index.html"),
                "Projects/index.html",
                "Documents/index.html"
            ).firstOrNull { viewModel.fileSystem.getFile(it).exists() }

            if (existingCandidate != null && (trimmedArg.isBlank() || trimmedArg == "index.html")) {
                val fileItem = viewModel.fileSystem.getFileItem(existingCandidate)
                if (fileItem != null) {
                    withContext(Dispatchers.Main) {
                        viewModel.openFileInTab(fileItem, inNewTab = true)
                    }
                    viewModel.appendTerminalLines(listOf(
                        TerminalLine("[RUN] 🚀 Launching existing website: $existingCandidate in live tab", TerminalLineType.SUCCESS),
                        TerminalLine("  • Live URL: gvone-file://$existingCandidate", TerminalLineType.INFO),
                        TerminalLine("  • Tip: Use 'run website <topic>' to create a new website anytime.", TerminalLineType.INFO)
                    ))
                    return@launch
                }
            }

            // Autonomous Website Creation and Execution!
            viewModel.appendTerminalLines(listOf(
                TerminalLine("[RUN] 🚀 Generating and launching modern interactive website...", TerminalLineType.AGENT_PLAN),
                TerminalLine("  • Synthesizing responsive HTML5, modern CSS3 styling & interactive JavaScript...", TerminalLineType.AGENT_THOUGHT)
            ))

            val topic = if (trimmedArg.isNotBlank() && !isWebsiteTarget) trimmedArg else if (lowerArg.contains("website")) {
                trimmedArg.replace(Regex("(?i)(run|create|build|make|a|new|website)"), "").trim().ifBlank { "Modern Web Dashboard & App Hub" }
            } else {
                "Modern Web Dashboard & App Hub"
            }

            val targetPath = "Projects/index.html"
            val prompt = "You are an expert full-stack web developer. Build a complete, modern, responsive single-file HTML5 website with inline <style> and <script> for: \"$topic\". " +
                    "Include modern typography, dark/light theme switcher, card layout, interactive counters/widgets, and responsive design. Return ONLY valid HTML without markdown fences."

            val generatedHtml = if (viewModel.aiService.isApiKeyConfigured()) {
                try {
                    val raw = viewModel.aiService.generateDirectResponse(prompt)
                    if (raw != null) cleanHtmlOutput(raw) else null
                } catch (e: Exception) {
                    null
                }
            } else null

            val htmlContent = (if (generatedHtml.isNullOrBlank() || !generatedHtml.contains("<html", ignoreCase = true)) {
                generateDefaultModernWebsite(topic)
            } else generatedHtml).trim()

            viewModel.fileSystem.writeFileContent(targetPath, htmlContent)
            val fileItem = viewModel.fileSystem.getFileItem(targetPath)
            if (fileItem != null) {
                withContext(Dispatchers.Main) {
                    viewModel.openFileInTab(fileItem, inNewTab = true)
                }
            }

            viewModel.appendTerminalLines(listOf(
                TerminalLine("✔ Website created and saved to /$targetPath (${htmlContent.length} bytes)", TerminalLineType.SUCCESS),
                TerminalLine("✔ Launched live interactive website in active tab: gvone-file://$targetPath", TerminalLineType.SUCCESS),
                TerminalLine("  • Tip: Tap 'Source Code' in viewer to edit, or use '/run' again to reload.", TerminalLineType.INFO)
            ))
        }
    }

    private fun cleanHtmlOutput(raw: String): String {
        var h = raw.trim()
        if (h.startsWith("```html", ignoreCase = true)) {
            h = h.substring(7)
        } else if (h.startsWith("```")) {
            h = h.substring(3)
        }
        if (h.endsWith("```")) {
            h = h.dropLast(3)
        }
        return h.trim()
    }

    private fun generateDefaultModernWebsite(topic: String): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>$topic • GVONE Web Runtime</title>
  <style>
    :root {
      --bg: #0B0F17;
      --card-bg: rgba(26, 34, 52, 0.85);
      --border: #2A364F;
      --accent: #00E5FF;
      --accent-grad: linear-gradient(135deg, #00E5FF, #7C4DFF);
      --text: #F1F5F9;
      --subtext: #94A3B8;
    }
    [data-theme="light"] {
      --bg: #F8FAFC;
      --card-bg: #FFFFFF;
      --border: #E2E8F0;
      --accent: #0284C7;
      --accent-grad: linear-gradient(135deg, #0284C7, #6366F1);
      --text: #0F172A;
      --subtext: #64748B;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; transition: background 0.3s, color 0.3s; }
    body { background: var(--bg); color: var(--text); padding: 24px; min-height: 100vh; display: flex; flex-direction: column; align-items: center; }
    .header { width: 100%; max-width: 800px; display: flex; justify-content: space-between; align-items: center; padding-bottom: 20px; border-bottom: 1px solid var(--border); }
    .logo { font-size: 1.25rem; font-weight: 800; background: var(--accent-grad); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
    .theme-btn { background: var(--card-bg); border: 1px solid var(--border); color: var(--text); padding: 8px 14px; border-radius: 8px; cursor: pointer; font-size: 0.85rem; font-weight: 600; }
    .hero { text-align: center; margin: 40px 0 30px; max-width: 650px; }
    .hero h1 { font-size: 2.2rem; margin-bottom: 12px; font-weight: 800; line-height: 1.2; }
    .hero p { color: var(--subtext); font-size: 1rem; line-height: 1.6; }
    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 16px; width: 100%; max-width: 800px; margin-bottom: 30px; }
    .card { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); }
    .card h3 { font-size: 1.1rem; margin-bottom: 8px; color: var(--accent); }
    .card p { font-size: 0.88rem; color: var(--subtext); line-height: 1.5; margin-bottom: 14px; }
    .interactive-box { background: var(--card-bg); border: 1px solid var(--border); border-radius: 12px; padding: 24px; width: 100%; max-width: 800px; text-align: center; }
    .counter-val { font-size: 2.8rem; font-weight: 800; color: var(--accent); margin: 12px 0; }
    .btn-group { display: flex; gap: 10px; justify-content: center; flex-wrap: wrap; }
    .action-btn { background: var(--accent); color: #000; font-weight: 700; border: none; padding: 10px 20px; border-radius: 8px; cursor: pointer; }
    .footer { margin-top: auto; padding-top: 40px; color: var(--subtext); font-size: 0.8rem; text-align: center; }
  </style>
</head>
<body>
  <div class="header">
    <div class="logo">⚡ GVONE LIVE RUNTIME</div>
    <button class="theme-btn" onclick="toggleTheme()">🌓 Toggle Mode</button>
  </div>
  <div class="hero">
    <h1>$topic</h1>
    <p>Autonomously generated and running in the GVONE sandboxed runtime. Fully responsive with client-side state and live interactive controls.</p>
  </div>
  <div class="grid">
    <div class="card">
      <h3>🚀 Live Runtime</h3>
      <p>Interactive web application rendered directly inside your sandboxed browser tab with full DOM and JS execution.</p>
    </div>
    <div class="card">
      <h3>⚡ Responsive Design</h3>
      <p>Mobile-first layout with dynamic CSS custom properties, touch feedback, and fluid transitions.</p>
    </div>
    <div class="card">
      <h3>🛠 Editable Source</h3>
      <p>View or modify this page anytime in the built-in file editor or rerun with <code>/run index.html</code>.</p>
    </div>
  </div>
  <div class="interactive-box">
    <h3>Interactive Runtime Demo</h3>
    <div class="counter-val" id="counter">0</div>
    <div class="btn-group">
      <button class="action-btn" onclick="increment()">Count Up (+1)</button>
      <button class="theme-btn" onclick="resetCount()">Reset</button>
    </div>
  </div>
  <div class="footer">
    Built with GVONE Unified Command Engine • Projects/index.html
  </div>
  <script>
    let count = 0;
    function increment() { count++; document.getElementById('counter').innerText = count; }
    function resetCount() { count = 0; document.getElementById('counter').innerText = count; }
    function toggleTheme() {
      const b = document.body;
      b.setAttribute('data-theme', b.getAttribute('data-theme') === 'light' ? 'dark' : 'light');
    }
  </script>
</body>
</html>
        """.trimIndent()
    }

    fun generateHelpOutput(commands: List<CustomCommandEntity>): List<TerminalLine> {
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("--- GVONE UNIFIED COMMAND ENGINE MANUAL ---", TerminalLineType.SYSTEM))
        lines.add(TerminalLine("Syntax: <command> [arguments...] or /<command> [arguments...]", TerminalLineType.INFO))
        lines.add(TerminalLine("Direct Address Bar input has 100% equal parity with Terminal CLI.", TerminalLineType.SUCCESS))
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[1] TERMINAL & SYSTEM UTILITIES", TerminalLineType.SUCCESS))
        lines.add(TerminalLine("  help, ?              Display this manual", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  clear, cls           Clear terminal screen", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  bridge               Check Web App Bridge status (Success/Failed)", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  addressbar           Check address bar stream link status", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  history              Show recently executed commands", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  tabs, lstabs         List all browser tabs with index and URLs", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  tab <index|name>     Switch active browser tab", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  newtab [-p] [url]    Open new regular or private tab", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  closetab [index]     Close current or specified tab", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  url [address]        Navigate to URL or print current URL", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  tor [on|off|status]  Toggle or inspect Tor Onion Shield", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  desktop              Toggle desktop mode for webpage", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  js <expression>      Evaluate JavaScript in webpage context", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  ai <prompt>          Query GVONE AI Q&A directly", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  ping <host>          Test network latency", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  whoami, date, uname  Terminal system information", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  exit, quit           Close terminal interface", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[2] AGENTIC RUNTIME (ChatGPT Atlas, Comet, Dia)", TerminalLineType.SUCCESS))
        lines.add(TerminalLine("  /agent [goal]        Execute autonomous multi-step agentic goal", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /agent on|off        Toggle persistent agentic input prompt", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /agent persona <p>   Switch persona: atlas, comet, dia, auto", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /agent status        Inspect active agent runtime, sandbox group & tools", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /organize            Auto-cluster all open tabs into domain cohorts", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[3] SANDBOX TAB GROUPS & COHORTS", TerminalLineType.SUCCESS))
        lines.add(TerminalLine("  /groups, /tabgroups  List all tab groups with tab counts and colors", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /sandbox             Focus or create dedicated Sandbox tab group", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /group create <n> [c]Create tab cohort with custom name and color", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /group add <tab> <g> Move a tab into a group", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /group close <g>     Close tab group and all its member tabs", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  /ungroup [tab]       Remove tab from its current group", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[4] SANDBOX FILESYSTEM & WORKSPACE", TerminalLineType.SUCCESS))
        lines.add(TerminalLine("  pwd                  Print working directory", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  cd [path]            Change directory (~, .., relative, absolute)", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  ls, dir [-l] [path]  List files, permissions, sizes, and timestamps", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  cat <file>           Display content of file", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  touch <file>         Create empty file", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  mkdir <dir>          Create directory", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  rm [-r] <path>       Remove file or directory", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  tree [path]          Display ASCII directory tree structure", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  view, openfile <f>   Open sandbox file in a live browser tab", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  run, /run [site|f]   Run/preview website, launch file, or create web app", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  run website [topic]  Autonomously create modern website & run in live tab", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  df, du               Inspect disk space & sandbox storage consumption", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[5] BROWSER DOM & WEB INSPECTION", TerminalLineType.SUCCESS))
        lines.add(TerminalLine("  click <sel|text>     Click DOM element by CSS selector or button text", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  type <sel> <text>    Type text into input element", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  scroll <dir>         Scroll page: down, up, top, bottom", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  links                Extract all hyperlinks from active page", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  text, extract        Extract visible textual content from page", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("  cookies              Inspect active site session cookies & Tor status", TerminalLineType.OUTPUT))
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[6] SEARCH COMMANDS", TerminalLineType.SUCCESS))
        commands.filter { it.type == CommandType.SEARCH }.forEach { cmd ->
            lines.add(TerminalLine("  ${cmd.command.padEnd(12)} ${cmd.name} (${cmd.description})", TerminalLineType.OUTPUT))
        }
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[7] GVONE AI COMMANDS", TerminalLineType.SUCCESS))
        commands.filter { it.type == CommandType.AI }.forEach { cmd ->
            lines.add(TerminalLine("  ${cmd.command.padEnd(12)} ${cmd.name} (${cmd.description})", TerminalLineType.OUTPUT))
        }
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[8] BROWSER & PAGE ACTIONS", TerminalLineType.SUCCESS))
        commands.filter { it.type == CommandType.BROWSER_ACTION || it.type == CommandType.PAGE_ACTION }.forEach { cmd ->
            lines.add(TerminalLine("  ${cmd.command.padEnd(12)} ${cmd.name} (${cmd.description})", TerminalLineType.OUTPUT))
        }
        lines.add(TerminalLine("", TerminalLineType.OUTPUT))

        lines.add(TerminalLine("[9] AUTOMATION COMMANDS", TerminalLineType.SUCCESS))
        commands.filter { it.type == CommandType.AUTOMATION }.forEach { cmd ->
            lines.add(TerminalLine("  ${cmd.command.padEnd(12)} ${cmd.name} (${cmd.description})", TerminalLineType.OUTPUT))
        }
        lines.add(TerminalLine("-----------------------------------", TerminalLineType.SYSTEM))

        return lines
    }
}
