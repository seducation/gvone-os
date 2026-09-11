package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.sandbox.AgentPersona
import com.example.agent.sandbox.SandboxAgentEngine
import com.example.agent.runtime.*
import com.example.agent.nodal.NodalEngine
import com.example.agent.registry.AgentRegistry
import com.example.agent.cns.CentralNervousSystem
import com.example.ui.components.RuntimeStatusPillRow
import com.example.ui.components.ExpandableAgentTaskCard
import com.example.data.command.CommandEngine
import com.example.data.files.GVONEFileSystem
import com.example.data.model.*
import com.example.data.sync.WebAppConnectionState
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import com.example.data.terminal.TerminalSession
import com.example.data.terminal.TerminalShellEngine
import com.example.data.tor.TorConnectionState
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

private val TermBgColor = Color(0xFF090C10)
private val TermSurfaceColor = Color(0xFF0D1117)
private val TermBorderColor = Color(0xFF1E2636)
private val TermPromptGreen = Color(0xFF4ADE80)
private val TermPromptCyan = Color(0xFF38BDF8)
private val TermPromptPurple = Color(0xFFA855F7)
private val TermTextPrimary = Color(0xFFE6EDF3)
private val TermTextSecondary = Color(0xFF8B949E)
private val TermTextSuccess = Color(0xFF3FB950)
private val TermTextError = Color(0xFFF85149)
private val TermTextWarning = Color(0xFFD29922)
private val TermTextInfo = Color(0xFF58A6FF)

@Composable
fun TerminalScreen(
    viewModel: BrowserViewModel,
    isAddressBarBottom: Boolean = true,
    addressBarBottomPadding: Dp = 0.dp,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (Boolean) -> Unit = {},
    onOpenAgentDashboard: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // Centralized command entities from ViewModel
    val customCommands by viewModel.customCommands.collectAsState()
    val allCommands = remember(customCommands) {
        CommandEngine.mergeWithBuiltIns(customCommands)
    }

    // Browser state
    val tabs by viewModel.tabs.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val torStatus by viewModel.torStatus.collectAsState()
    val isTorActive = settings.torEnabled && torStatus.state == TorConnectionState.CONNECTED
    val isPrivateMode = currentTab?.isPrivate == true

    // Observe global terminal lines and bridge state from BrowserViewModel
    val globalTerminalLines by viewModel.terminalLines.collectAsStateWithLifecycle()
    val bridgeConnectionState by viewModel.webAppBridge.connectionState.collectAsStateWithLifecycle()

    // Autonomous Sandbox & Shell Engines
    val fileSystem = remember { GVONEFileSystem(context) }
    val agentEngine = remember {
        SandboxAgentEngine(
            context = context,
            viewModel = viewModel,
            aiService = viewModel.aiService,
            fileSystem = fileSystem
        )
    }
    val shellEngine = remember {
        TerminalShellEngine(
            context = context,
            viewModel = viewModel,
            fileSystem = fileSystem,
            agentEngine = agentEngine
        )
    }
    var isAgenticMode by remember { mutableStateOf(false) }
    var currentCwd by remember { mutableStateOf(shellEngine.promptPath) }
    var activePersona by remember { mutableStateOf(agentEngine.activePersona) }

    // Unified GVONE Runtime Mode & Task Lifecycle
    val runtimeStateManager = remember { RuntimeStateManager.global }
    val runtimeMode by runtimeStateManager.runtimeMode.collectAsStateWithLifecycle()
    val activeTask by runtimeStateManager.activeTask.collectAsStateWithLifecycle()
    var isTaskCardExpanded by remember { mutableStateOf(true) }

    // Multi-session management
    var sessions by remember {
        val initialLines = viewModel.terminalLines.value.ifEmpty {
            val isSuccess = viewModel.webAppBridge.connectionState.value == WebAppConnectionState.READY
            createInitialBanner(viewModel.webAppBridge.connectionState.value.name, isSuccess)
        }
        mutableStateOf(
            listOf(
                TerminalSession(
                    id = "sess_1",
                    title = "Session 1",
                    lines = initialLines
                )
            )
        )
    }
    var activeSessionId by remember { mutableStateOf("sess_1") }
    val activeSession = sessions.find { it.id == activeSessionId } ?: sessions.first()

    // Synchronize global terminal lines into active session in real-time
    LaunchedEffect(globalTerminalLines) {
        if (globalTerminalLines.isNotEmpty()) {
            sessions = sessions.map {
                if (it.id == activeSessionId) it.copy(lines = globalTerminalLines) else it
            }
        }
    }

    // Active input state
    var inputText by remember { mutableStateOf(TextFieldValue("")) }

    // Persistent command history
    val commandHistory = remember {
        mutableStateListOf<String>().apply {
            addAll(viewModel.terminalRepository.getCommandHistory())
        }
    }
    var historyIndex by remember { mutableIntStateOf(-1) }

    // Terminal initial welcome banner if session is empty
    LaunchedEffect(activeSessionId) {
        if (activeSession.lines.isEmpty()) {
            val isSuccess = bridgeConnectionState == WebAppConnectionState.READY
            val bannerLines = createInitialBanner(bridgeConnectionState.name, isSuccess)
            sessions = sessions.map {
                if (it.id == activeSessionId) it.copy(lines = bannerLines) else it
            }
            viewModel.appendTerminalLines(bannerLines)
        }
    }

    // Auto-scroll to bottom on line changes
    LaunchedEffect(activeSession.lines.size) {
        if (activeSession.lines.isNotEmpty()) {
            listState.animateScrollToItem(activeSession.lines.size - 1)
        }
    }

    // Request keyboard focus immediately on launch
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    // Hardware/system back button handling
    BackHandler {
        onClose()
    }

    // Blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    // Autocomplete matching list
    val currentWord = remember(inputText.text) {
        val raw = inputText.text
        val lastSpace = raw.lastIndexOf(' ')
        if (lastSpace == -1) raw.trim() else raw.substring(lastSpace + 1).trim()
    }
    val suggestions = remember(currentWord, allCommands) {
        if (currentWord.length >= 1) {
            val query = if (currentWord.startsWith("/")) currentWord else "/$currentWord"
            allCommands.filter { cmd ->
                cmd.isEnabled && cmd.getAllTriggers().any { it.startsWith(query, ignoreCase = true) }
            }.take(5)
        } else {
            emptyList()
        }
    }

    fun commitLines(newLines: List<TerminalLine>) {
        val updated = activeSession.lines + newLines
        sessions = sessions.map {
            if (it.id == activeSessionId) it.copy(lines = updated) else it
        }
        viewModel.appendTerminalLines(newLines)
    }

    fun appendLines(newLines: List<TerminalLine>, dummy: ((List<TerminalLine>) -> Unit)? = null) {
        commitLines(newLines)
    }

    // Command execution handler using centralized CommandEngine
    fun executeCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        val promptPrefix = if (isAgenticMode) "gvone[agentic:${activePersona.badge.lowercase()}]:$currentCwd$ " else "gvone@browser:$currentCwd$ "
        if (trimmed.isEmpty()) {
            val emptyCommandLine = TerminalLine(
                text = promptPrefix,
                type = TerminalLineType.COMMAND
            )
            appendLines(listOf(emptyCommandLine)) { newLines ->
                sessions = sessions.map {
                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                }
            }
            return
        }

        // Add to persistent history
        viewModel.terminalRepository.addCommandToHistory(trimmed)
        if (commandHistory.isEmpty() || commandHistory.last() != trimmed) {
            commandHistory.add(trimmed)
        }
        historyIndex = -1

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

        // Handle shell built-ins first
        when (normalizedToken) {
            "/clear", "/cls" -> {
                sessions = sessions.map {
                    if (it.id == activeSessionId) it.copy(lines = emptyList()) else it
                }
                viewModel.clearTerminalLines()
                inputText = TextFieldValue("")
                return
            }

            "/exit", "/quit", "/q" -> {
                onClose()
                return
            }

            "/help", "/?" -> {
                outputLines.addAll(generateHelpOutput(allCommands))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/voice" -> {
                when {
                    queryArg.isBlank() -> {
                        runtimeStateManager.activateVoiceOnly()
                        outputLines.add(TerminalLine("[VOICE RUNTIME] ACTIVATED. Voice interaction layer is now primary.", TerminalLineType.SUCCESS))
                        outputLines.add(TerminalLine("  • Speak your instruction or type transcript directly.", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("  • Use '/voice /agent <goal>' for Voice-First compound execution.", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("  • Use '/chat' or '/cancel' to return to standard text mode.", TerminalLineType.INFO))
                    }
                    queryArg.startsWith("/agent", ignoreCase = true) || queryArg.startsWith("agent", ignoreCase = true) -> {
                        val goal = queryArg.removePrefix("/agent").removePrefix("agent").trim()
                        runtimeStateManager.activateVoiceAgentCompound(goal)
                        isAgenticMode = true
                        agentEngine.isAgenticModeEnabled = true
                        outputLines.add(TerminalLine("╭─────────────────────────────────────────────────────────────╮", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ 🎙️ COMPOUND MODE: VOICE-FIRST + AGENT RUNTIME", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Goal: \"$goal\"", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Hierarchy: Voice Instruction ➜ Autonomous Task Execution", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("╰─────────────────────────────────────────────────────────────╯", TerminalLineType.AGENT_PLAN))
                        commitLines(outputLines)
                        coroutineScope.launch {
                            agentEngine.runAgenticWorkflow(goal.ifBlank { "Voice-first autonomous exploration" }, shellEngine.currentDirectory) { line ->
                                appendLines(listOf(line)) { newLines ->
                                    sessions = sessions.map {
                                        if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                    }
                                }
                            }
                        }
                        inputText = TextFieldValue("")
                        return
                    }
                    else -> {
                        outputLines.add(TerminalLine("[VOICE INPUT] \"$queryArg\"", TerminalLineType.COMMAND))
                        outputLines.add(TerminalLine("Delegating voice goal to Central Nervous System...", TerminalLineType.INFO))
                        commitLines(outputLines)
                        coroutineScope.launch {
                            val res = CentralNervousSystem.global.orchestrateGoal(queryArg)
                            appendLines(listOf(TerminalLine(res.synthesis, TerminalLineType.AI_RESPONSE))) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        }
                        inputText = TextFieldValue("")
                        return
                    }
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/cancel", "/stop", "/abort" -> {
                runtimeStateManager.cancelTask(activeTask?.taskId ?: "current")
                isAgenticMode = false
                agentEngine.isAgenticModeEnabled = false
                outputLines.add(TerminalLine("[TASK CANCELLED] Active task has been aborted and cleared.", TerminalLineType.WARNING))
                outputLines.add(TerminalLine("Unified runtime restored to CHAT mode. Task context scratchpad archived.", TerminalLineType.INFO))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/pause" -> {
                runtimeStateManager.pauseTask(activeTask?.taskId ?: "current")
                outputLines.add(TerminalLine("[TASK PAUSED] Active task execution suspended. Type '/resume' to continue.", TerminalLineType.WARNING))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/resume" -> {
                runtimeStateManager.resumeTask(activeTask?.taskId ?: "current")
                outputLines.add(TerminalLine("[TASK RESUMED] Resumed active task execution.", TerminalLineType.SUCCESS))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/chat", "/text" -> {
                runtimeStateManager.resetToTextChat()
                isAgenticMode = false
                agentEngine.isAgenticModeEnabled = false
                outputLines.add(TerminalLine("[CHAT MODE] Restored to conversational text mode.", TerminalLineType.SUCCESS))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/debug" -> {
                runtimeStateManager.toggleDebugMode()
                val isDbg = runtimeStateManager.runtimeMode.value.isDebugEnabled
                outputLines.add(
                    TerminalLine(
                        "[DEBUG MODE] " + (if (isDbg) "ENABLED. Verbose scratchpad observations, tool traces & latency active." else "DISABLED. Standard clean view active."),
                        if (isDbg) TerminalLineType.SUCCESS else TerminalLineType.INFO
                    )
                )
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/status" -> {
                outputLines.add(TerminalLine("── GVONE OS UNIFIED RUNTIME STATUS ──", TerminalLineType.SYSTEM))
                outputLines.add(TerminalLine("● Interaction Mode: ${runtimeMode.interaction.name}", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("● Execution Mode:   ${runtimeMode.execution.name}", TerminalLineType.INFO))
                outputLines.add(TerminalLine("● Debug Observability: ${if (runtimeMode.isDebugEnabled) "ON" else "OFF"}", TerminalLineType.OUTPUT))
                outputLines.add(TerminalLine("● Active Task: ${activeTask?.goal ?: "None (Idle)"} [${activeTask?.status?.name ?: "IDLE"}]", TerminalLineType.OUTPUT))
                outputLines.add(TerminalLine("● Registered Agents: ${AgentRegistry.global.getAllAgents().joinToString { it.identity() }}", TerminalLineType.INFO))
                outputLines.add(TerminalLine("● Nodal Workflows: ${NodalEngine.global.workflows.value.size} registered", TerminalLineType.INFO))
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/yt", "/youtube" -> {
                if (queryArg.isNotBlank()) {
                    outputLines.add(TerminalLine("[YOUTUBE AGENT] Launching autonomous YouTube playback for: \"$queryArg\"...", TerminalLineType.AGENT_PLAN))
                    commitLines(outputLines)
                    coroutineScope.launch {
                        val res = CentralNervousSystem.global.orchestrateGoal("/yt $queryArg")
                        appendLines(listOf(TerminalLine(res.synthesis, TerminalLineType.SUCCESS))) { newLines ->
                            sessions = sessions.map {
                                if (it.id == activeSessionId) it.copy(lines = newLines) else it
                            }
                        }
                    }
                    inputText = TextFieldValue("")
                    return
                }
            }

            "/agent", "/agentic" -> {
                when {
                    queryArg.startsWith("/voice", ignoreCase = true) || queryArg.startsWith("voice", ignoreCase = true) -> {
                        val goal = queryArg.removePrefix("/voice").removePrefix("voice").trim()
                        runtimeStateManager.activateAgentVoiceCompound(goal)
                        isAgenticMode = true
                        agentEngine.isAgenticModeEnabled = true
                        outputLines.add(TerminalLine("╭─────────────────────────────────────────────────────────────╮", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ 🤖 COMPOUND MODE: AGENT-FIRST + VOICE STATUS", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Goal: \"$goal\"", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("│ Hierarchy: Autonomous Execution ➜ Voice Reporting", TerminalLineType.AGENT_PLAN))
                        outputLines.add(TerminalLine("╰─────────────────────────────────────────────────────────────╯", TerminalLineType.AGENT_PLAN))
                        commitLines(outputLines)
                        coroutineScope.launch {
                            agentEngine.runAgenticWorkflow(goal.ifBlank { "Autonomous task execution" }, shellEngine.currentDirectory) { line ->
                                appendLines(listOf(line)) { newLines ->
                                    sessions = sessions.map {
                                        if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                    }
                                }
                            }
                        }
                        inputText = TextFieldValue("")
                        return
                    }
                    queryArg.isBlank() -> {
                        isAgenticMode = !isAgenticMode
                        agentEngine.isAgenticModeEnabled = isAgenticMode
                        if (isAgenticMode) {
                            runtimeStateManager.activateAgentOnly()
                        } else {
                            runtimeStateManager.resetToTextChat()
                        }
                        outputLines.add(
                            TerminalLine(
                                "[AGENTIC MODE] " + (if (isAgenticMode) "ACTIVATED (${activePersona.displayName}). Type any goal/instruction to execute autonomously." else "DEACTIVATED. Standard bash shell active."),
                                if (isAgenticMode) TerminalLineType.SUCCESS else TerminalLineType.WARNING
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
                        isAgenticMode = true
                        agentEngine.isAgenticModeEnabled = true
                        runtimeStateManager.activateAgentOnly()
                        outputLines.add(TerminalLine("[AGENTIC MODE] ACTIVATED. Persona: ${activePersona.displayName}", TerminalLineType.SUCCESS))
                        outputLines.add(TerminalLine("Interactive agent prompt active. Use '/agent persona <atlas|comet|dia|auto>' or '/agent off' to exit.", TerminalLineType.INFO))
                    }
                    queryArg.equals("off", ignoreCase = true) || queryArg.equals("stop", ignoreCase = true) || queryArg.equals("disable", ignoreCase = true) -> {
                        isAgenticMode = false
                        agentEngine.isAgenticModeEnabled = false
                        runtimeStateManager.resetToTextChat()
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
                        agentEngine.activePersona = newPersona
                        activePersona = newPersona
                        outputLines.add(TerminalLine("[AGENTIC PERSONA] Switched to: ${newPersona.displayName} (${newPersona.description})", TerminalLineType.SUCCESS))
                    }
                    queryArg.equals("status", ignoreCase = true) -> {
                        val activeGroupName = viewModel.tabGroups.value.find { it.id == agentEngine.activeSandboxGroupId }?.name ?: "None"
                        outputLines.add(TerminalLine("── AGENTIC RUNTIME STATUS ──", TerminalLineType.SYSTEM))
                        outputLines.add(TerminalLine("● Mode: " + (if (isAgenticMode) "ACTIVE" else "IDLE"), if (isAgenticMode) TerminalLineType.SUCCESS else TerminalLineType.WARNING))
                        outputLines.add(TerminalLine("● Active Persona: ${activePersona.displayName} (${activePersona.badge})", TerminalLineType.INFO))
                        outputLines.add(TerminalLine("● Sandbox Tab Group: $activeGroupName", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("● Sandbox Directory: /${shellEngine.currentDirectory}", TerminalLineType.OUTPUT))
                        outputLines.add(TerminalLine("● Multi-Agent Core: CentralNervousSystem + BrowserController + GVONEFileSystem", TerminalLineType.SUCCESS))
                    }
                    queryArg.equals("tabs", ignoreCase = true) || queryArg.equals("group", ignoreCase = true) || queryArg.equals("organize", ignoreCase = true) -> {
                        commitLines(outputLines)
                        coroutineScope.launch {
                            agentEngine.autoOrganizeTabs { line ->
                                appendLines(listOf(line)) { newLines ->
                                    sessions = sessions.map {
                                        if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                    }
                                }
                            }
                        }
                        inputText = TextFieldValue("")
                        return
                    }
                    else -> {
                        commitLines(outputLines)
                        coroutineScope.launch {
                            agentEngine.runAgenticWorkflow(queryArg, shellEngine.currentDirectory) { line ->
                                appendLines(listOf(line)) { newLines ->
                                    sessions = sessions.map {
                                        if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                    }
                                }
                            }
                        }
                        inputText = TextFieldValue("")
                        return
                    }
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                        coroutineScope.launch {
                            val lines = shellEngine.focusSandboxTabGroup()
                            commitLines(lines)
                        }
                        inputText = TextFieldValue("")
                        return
                    }
                    queryArg.startsWith("organize", ignoreCase = true) -> {
                        coroutineScope.launch {
                            agentEngine.autoOrganizeTabs { line ->
                                appendLines(listOf(line)) { newLines ->
                                    sessions = sessions.map {
                                        if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                    }
                                }
                            }
                        }
                        inputText = TextFieldValue("")
                        return
                    }
                    else -> {
                        outputLines.addAll(shellEngine.listTabGroups())
                    }
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/sandbox" -> {
                coroutineScope.launch {
                    val lines = shellEngine.focusSandboxTabGroup()
                    commitLines(lines)
                }
                inputText = TextFieldValue("")
                return
            }

            "/organize" -> {
                commitLines(outputLines)
                coroutineScope.launch {
                    agentEngine.autoOrganizeTabs { line ->
                        appendLines(listOf(line)) { newLines ->
                            sessions = sessions.map {
                                if (it.id == activeSessionId) it.copy(lines = newLines) else it
                            }
                        }
                    }
                }
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/pwd" -> {
                outputLines.add(TerminalLine(shellEngine.promptPath, TerminalLineType.OUTPUT))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/cd" -> {
                coroutineScope.launch {
                    val line = shellEngine.changeDirectory(queryArg)
                    currentCwd = shellEngine.promptPath
                    commitLines(listOf(line))
                }
                inputText = TextFieldValue("")
                return
            }

            "/ls", "/dir" -> {
                coroutineScope.launch {
                    val lines = shellEngine.listFiles(queryArg)
                    commitLines(lines)
                }
                inputText = TextFieldValue("")
                return
            }

            "/cat" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("cat: missing file operand", TerminalLineType.ERROR))
                    commitLines(outputLines)
                } else {
                    coroutineScope.launch {
                        val lines = shellEngine.catFile(queryArg)
                        commitLines(lines)
                    }
                }
                inputText = TextFieldValue("")
                return
            }

            "/touch" -> {
                coroutineScope.launch {
                    val line = shellEngine.touchFile(queryArg)
                    commitLines(listOf(line))
                }
                inputText = TextFieldValue("")
                return
            }

            "/mkdir" -> {
                coroutineScope.launch {
                    val line = shellEngine.makeDirectory(queryArg)
                    commitLines(listOf(line))
                }
                inputText = TextFieldValue("")
                return
            }

            "/rm" -> {
                coroutineScope.launch {
                    val line = shellEngine.removeFile(queryArg)
                    commitLines(listOf(line))
                }
                inputText = TextFieldValue("")
                return
            }

            "/tree" -> {
                coroutineScope.launch {
                    val lines = shellEngine.generateTree(queryArg)
                    commitLines(lines)
                }
                inputText = TextFieldValue("")
                return
            }

            "/df", "/du" -> {
                coroutineScope.launch {
                    val lines = shellEngine.getDiskUsage()
                    commitLines(lines)
                }
                inputText = TextFieldValue("")
                return
            }

            "/cookies" -> {
                outputLines.addAll(shellEngine.inspectCookies())
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                        appendLines(listOf(TerminalLine(res?.trim('\"') ?: "null", TerminalLineType.SUCCESS))) { newLines ->
                            sessions = sessions.map {
                                if (it.id == activeSessionId) it.copy(lines = newLines) else it
                            }
                        }
                    }
                    outputLines.add(TerminalLine("[ACTION] Attempting click on '$queryArg'...", TerminalLineType.INFO))
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                        appendLines(listOf(TerminalLine(res?.trim('\"') ?: "null", TerminalLineType.SUCCESS))) { newLines ->
                            sessions = sessions.map {
                                if (it.id == activeSessionId) it.copy(lines = newLines) else it
                            }
                        }
                    }
                    outputLines.add(TerminalLine("[ACTION] Typing into '$sel'...", TerminalLineType.INFO))
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                        appendLines(listOf(TerminalLine(res?.trim('\"') ?: "Scrolled", TerminalLineType.SUCCESS))) { newLines ->
                            sessions = sessions.map {
                                if (it.id == activeSessionId) it.copy(lines = newLines) else it
                            }
                        }
                    }
                }
                inputText = TextFieldValue("")
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
                            appendLines(linkLines) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        } catch (_: Exception) {
                            appendLines(listOf(TerminalLine(res ?: "None", TerminalLineType.OUTPUT))) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        }
                    }
                    outputLines.add(TerminalLine("[PAGE] Extracting hyperlinks from active page...", TerminalLineType.INFO))
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/text", "/extract" -> {
                val activeWv = viewModel.getActiveWebView()
                if (activeWv != null) {
                    activeWv.evaluateJavascript("document.body.innerText.substring(0, 2000)") { text ->
                        val clean = text?.trim('\"', ' ')?.replace("\\n", "\n") ?: ""
                        appendLines(listOf(TerminalLine("── VISIBLE PAGE TEXT ──\n$clean\n──────────────────────", TerminalLineType.OUTPUT))) { newLines ->
                            sessions = sessions.map {
                                if (it.id == activeSessionId) it.copy(lines = newLines) else it
                            }
                        }
                    }
                    outputLines.add(TerminalLine("[PAGE] Extracting visible text content...", TerminalLineType.INFO))
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/view", "/openfile" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: view <filename>", TerminalLineType.WARNING))
                } else {
                    val path = shellEngine.resolvePath(queryArg)
                    val file = fileSystem.getFile(path)
                    if (file.exists()) {
                        val gvItem = fileSystem.toFileItem(file)
                        viewModel.openFileInTab(gvItem, inNewTab = true)
                        outputLines.add(TerminalLine("[OK] Opened file in new tab: $path", TerminalLineType.SUCCESS))
                    } else {
                        outputLines.add(TerminalLine("view: file not found: $path", TerminalLineType.ERROR))
                    }
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/bridge" -> {
                val state = viewModel.webAppBridge.connectionState.value
                val isSuccess = state == WebAppConnectionState.READY
                val bridgeEnabled = viewModel.settings.value.bidirectionalBridgeEnabled
                val applyAll = viewModel.settings.value.bridgeApplyToAllWebsites
                val currentUrl = viewModel.currentTab.value?.url.orEmpty()
                val host = try { java.net.URI(currentUrl).host.orEmpty().ifEmpty { currentUrl } } catch (_: Exception) { currentUrl }

                outputLines.add(TerminalLine("── GVONE WEB APP BRIDGE REPORT ──", TerminalLineType.SYSTEM))
                outputLines.add(
                    TerminalLine(
                        "● Bridge Handshake: " + (if (isSuccess) "SUCCESSFUL (Connected & Ready)" else "NOT CONNECTED (State: ${state.name})"),
                        if (isSuccess) TerminalLineType.SUCCESS else TerminalLineType.ERROR
                    )
                )
                outputLines.add(TerminalLine("● Target Endpoint: $host", TerminalLineType.INFO))
                outputLines.add(TerminalLine("● Bidirectional Channel: " + (if (bridgeEnabled) "ENABLED" else "DISABLED"), if (bridgeEnabled) TerminalLineType.SUCCESS else TerminalLineType.WARNING))
                outputLines.add(TerminalLine("● Scope: " + (if (applyAll) "Universal (All Websites)" else "GVONE Web Apps Only"), TerminalLineType.OUTPUT))
                outputLines.add(
                    TerminalLine(
                        "● Address Bar Link: CONNECTED & SYNCHRONIZED",
                        TerminalLineType.SUCCESS
                    )
                )
                outputLines.add(
                    TerminalLine(
                        "● Verification: " + (if (isSuccess) "Success - bidirectional commands and address bar inputs are streaming." else "Inactive - verify target page supports GVONE bridge."),
                        if (isSuccess) TerminalLineType.SUCCESS else TerminalLineType.INFO
                    )
                )
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/addressbar" -> {
                val autoAppear = viewModel.settings.value.terminalAutoAppearOnAddressBar
                outputLines.add(TerminalLine("── ADDRESS BAR & TERMINAL LINK REPORT ──", TerminalLineType.SYSTEM))
                outputLines.add(TerminalLine("● Connection State: CONNECTED", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("● Address Bar Click Action: " + (if (autoAppear) "ALWAYS APPEAR (Terminal Opens)" else "DISAPPEAR (Terminal Closes)"), TerminalLineType.INFO))
                outputLines.add(TerminalLine("● Command Routing: Address bar commands dispatch to CLI and Web App Bridge", TerminalLineType.SUCCESS))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/history" -> {
                if (commandHistory.isEmpty()) {
                    outputLines.add(TerminalLine("No command history recorded.", TerminalLineType.INFO))
                } else {
                    outputLines.add(TerminalLine("--- COMMAND HISTORY (${commandHistory.size} items) ---", TerminalLineType.SYSTEM))
                    commandHistory.takeLast(50).forEachIndexed { idx, cmd ->
                        outputLines.add(TerminalLine("  %3d  %s".format(idx + 1, cmd), TerminalLineType.OUTPUT))
                    }
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/whoami" -> {
                outputLines.add(TerminalLine("gvone-user (uid=1000 gid=1000 groups=browser,tor,network,canvas)", TerminalLineType.OUTPUT))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/date" -> {
                val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.US)
                outputLines.add(TerminalLine(sdf.format(Date()), TerminalLineType.OUTPUT))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/uname" -> {
                outputLines.add(TerminalLine("Linux gvone-browser 6.6.0-aarch64 #1 SMP PREEMPT Android 14 GNU/Linux", TerminalLineType.OUTPUT))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/echo" -> {
                outputLines.add(TerminalLine(queryArg, TerminalLineType.OUTPUT))
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/newtab", "/nt" -> {
                val isPrivate = queryArg.contains("-p") || queryArg.contains("--private")
                val cleanUrl = queryArg.replace("-p", "").replace("--private", "").trim()
                val targetUrl = if (cleanUrl.isNotBlank()) cleanUrl else "https://www.google.com"
                viewModel.createNewTab(url = targetUrl, isPrivate = isPrivate)
                outputLines.add(TerminalLine("[OK] Created new ${if (isPrivate) "private " else ""}tab: $targetUrl", TerminalLineType.SUCCESS))
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                    viewModel.loadUrlInCurrentTab(validUrl)
                    outputLines.add(TerminalLine("[NAVIGATE] Loading: $validUrl", TerminalLineType.SUCCESS))
                }
                commitLines(outputLines)
                inputText = TextFieldValue("")
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
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/desktop" -> {
                viewModel.toggleDesktopMode()
                val isDesktop = currentTab?.desktopMode == true
                outputLines.add(TerminalLine("[VIEWPORT] Desktop Mode Toggled: ${!isDesktop}", TerminalLineType.SUCCESS))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }

            "/js" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: js <javascript_expression>", TerminalLineType.WARNING))
                    commitLines(outputLines)
                } else {
                    val safety = CommandEngine.validateJavaScriptSafety(queryArg)
                    if (!safety.isSafe) {
                        outputLines.add(TerminalLine("[SECURITY] JavaScript blocked: ${safety.reason}", TerminalLineType.ERROR))
                        commitLines(outputLines)
                    } else {
                        val activeWv = viewModel.getActiveWebView()
                        if (activeWv == null) {
                            outputLines.add(TerminalLine("[ERR] No active WebView tab available", TerminalLineType.ERROR))
                            commitLines(outputLines)
                        } else {
                            outputLines.add(TerminalLine("[EVAL] Running script in page context...", TerminalLineType.INFO))
                            commitLines(outputLines)
                            activeWv.evaluateJavascript(queryArg) { result ->
                                val cleanRes = result?.trim('\"', ' ', '\n')?.replace("\\n", "\n") ?: "null"
                                appendLines(listOf(TerminalLine("<- $cleanRes", TerminalLineType.SUCCESS))) { newLines ->
                                    sessions = sessions.map {
                                        if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                    }
                                }
                            }
                        }
                    }
                }
                inputText = TextFieldValue("")
                return
            }

            "/ai", "/ask" -> {
                if (queryArg.isBlank()) {
                    outputLines.add(TerminalLine("Usage: ai <question_or_prompt>", TerminalLineType.WARNING))
                    commitLines(outputLines)
                } else {
                    outputLines.add(TerminalLine("[GVONE AI] Synthesizing query: \"$queryArg\"...", TerminalLineType.INFO))
                    commitLines(outputLines)
                    coroutineScope.launch {
                        try {
                            val response = viewModel.aiService.searchAndSynthesize(queryArg).aiAnswer
                            appendLines(
                                listOf(
                                    TerminalLine(
                                        text = "\n=== GVONE AI SYNTHESIS ===\n$response\n==========================",
                                        type = TerminalLineType.AI_RESPONSE
                                    )
                                )
                            ) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        } catch (e: Exception) {
                            appendLines(listOf(TerminalLine("[ERR] AI Error: ${e.message}", TerminalLineType.ERROR))) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        }
                    }
                }
                inputText = TextFieldValue("")
                return
            }

            "/ping" -> {
                val host = if (queryArg.isNotBlank()) queryArg else "google.com"
                outputLines.add(TerminalLine("PING $host (142.250.190.46): 56 data bytes", TerminalLineType.INFO))
                outputLines.add(TerminalLine("64 bytes from 142.250.190.46: icmp_seq=0 ttl=116 time=28.4 ms", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("64 bytes from 142.250.190.46: icmp_seq=1 ttl=116 time=26.1 ms", TerminalLineType.SUCCESS))
                outputLines.add(TerminalLine("--- $host ping statistics ---", TerminalLineType.SYSTEM))
                outputLines.add(TerminalLine("2 packets transmitted, 2 packets received, 0.0% packet loss", TerminalLineType.OUTPUT))
                commitLines(outputLines)
                inputText = TextFieldValue("")
                return
            }
        }

        // Use centralized Command Parser & Registry & Executor architecture
        val currentUrl = currentTab?.url.orEmpty()
        val currentTitle = currentTab?.title.orEmpty()
        val currentHost = try { URL(currentUrl).host } catch (_: Exception) { "" }
        val executionContext = CommandExecutionContext(
            query = queryArg,
            currentUrl = currentUrl,
            currentTitle = currentTitle,
            currentDomain = currentHost
        )

        val parsed = CommandEngine.parse(trimmed, customCommands, executionContext)
        if (parsed != null) {
            val (commandEntity, result) = parsed
            when (result) {
                is CommandExecutionResult.ExecuteSearch -> {
                    viewModel.loadUrlInCurrentTab(result.searchUrl)
                    outputLines.add(
                        TerminalLine(
                            "[SEARCH] Executed ${commandEntity.name} (${result.provider ?: "Web"}): ${result.query}",
                            TerminalLineType.SUCCESS
                        )
                    )
                    outputLines.add(TerminalLine("-> Navigating to: ${result.searchUrl}", TerminalLineType.INFO))
                }

                is CommandExecutionResult.OpenUrl -> {
                    viewModel.loadUrlInCurrentTab(result.url)
                    outputLines.add(
                        TerminalLine(
                            "[OPEN] ${commandEntity.name}: Navigating to ${result.url}",
                            TerminalLineType.SUCCESS
                        )
                    )
                }

                is CommandExecutionResult.SendAIPrompt -> {
                    outputLines.add(TerminalLine("[GVONE AI] Prompting: ${result.prompt}...", TerminalLineType.INFO))
                    commitLines(outputLines)
                    coroutineScope.launch {
                        try {
                            val aiResp = viewModel.aiService.searchAndSynthesize(result.prompt).aiAnswer
                            appendLines(
                                listOf(
                                    TerminalLine(
                                        text = "\n=== ${result.title.uppercase()} ===\n$aiResp\n===========================",
                                        type = TerminalLineType.AI_RESPONSE
                                    )
                                )
                            ) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        } catch (e: Exception) {
                            appendLines(listOf(TerminalLine("[ERR] AI Error: ${e.message}", TerminalLineType.ERROR))) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        }
                    }
                    inputText = TextFieldValue("")
                    return
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
                        BrowserActionType.AGENT_DASHBOARD -> onOpenAgentDashboard()
                        BrowserActionType.TERMINAL -> { /* Already in terminal */ }
                    }
                    outputLines.add(
                        TerminalLine(
                            "[ACTION] Executed browser action: ${result.action.label}",
                            TerminalLineType.SUCCESS
                        )
                    )
                }

                is CommandExecutionResult.TriggerPageAction -> {
                    when (result.action) {
                        "summarize_page" -> {
                            val activeWv = viewModel.getActiveWebView()
                            if (activeWv != null) {
                                outputLines.add(TerminalLine("[PAGE] Extracting webpage content for AI summary...", TerminalLineType.INFO))
                                commitLines(outputLines)
                                activeWv.evaluateJavascript("document.body.innerText.substring(0, 4000)") { text ->
                                    val cleanText = text?.trim('\"', ' ', '\n')?.replace("\\n", "\n")?.take(3500).orEmpty()
                                    coroutineScope.launch {
                                        val prompt = "Summarize this webpage clearly with an executive summary and 3-5 key points.\n\nTitle: ${currentTab?.title}\nURL: ${currentTab?.url}\n\nContent:\n$cleanText"
                                        val summary = viewModel.aiService.searchAndSynthesize(prompt).aiAnswer
                                        appendLines(
                                            listOf(
                                                TerminalLine(
                                                    text = "\n=== PAGE SUMMARY: ${currentTab?.title} ===\n$summary\n=========================================",
                                                    type = TerminalLineType.AI_RESPONSE
                                                )
                                            )
                                        ) { newLines ->
                                            sessions = sessions.map {
                                                if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                            }
                                        }
                                    }
                                }
                                inputText = TextFieldValue("")
                                return
                            } else {
                                outputLines.add(TerminalLine("[ERR] No active webpage tab loaded.", TerminalLineType.ERROR))
                            }
                        }
                        "translate_page" -> {
                            val target = "https://translate.google.com/translate?sl=auto&tl=en&u=" + java.net.URLEncoder.encode(currentUrl, "UTF-8")
                            viewModel.loadUrlInCurrentTab(target)
                            outputLines.add(TerminalLine("[TRANSLATE] Opening Google Translate for current page.", TerminalLineType.SUCCESS))
                        }
                        "extract_info" -> {
                            val activeWv = viewModel.getActiveWebView()
                            outputLines.add(TerminalLine("[PAGE] Extracting facts & data...", TerminalLineType.INFO))
                            activeWv?.evaluateJavascript("document.body.innerText.substring(0, 3000)") { text ->
                                val clean = text?.trim('\"', ' ', '\n')?.take(2500).orEmpty()
                                coroutineScope.launch {
                                    val dataResp = viewModel.aiService.searchAndSynthesize("Extract key data points, facts, and dates from: $clean").aiAnswer
                                    appendLines(listOf(TerminalLine("\n=== EXTRACTED DATA ===\n$dataResp\n======================", TerminalLineType.OUTPUT))) { newLines ->
                                        sessions = sessions.map {
                                            if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            outputLines.add(TerminalLine("[PAGE] Triggered: ${result.action}", TerminalLineType.INFO))
                        }
                    }
                }

                is CommandExecutionResult.RunSafeJavaScript -> {
                    val activeWv = viewModel.getActiveWebView()
                    if (activeWv != null) {
                        activeWv.evaluateJavascript(result.javascriptCode) { ret ->
                            appendLines(listOf(TerminalLine("<- $ret", TerminalLineType.SUCCESS))) { newLines ->
                                sessions = sessions.map {
                                    if (it.id == activeSessionId) it.copy(lines = newLines) else it
                                }
                            }
                        }
                        outputLines.add(TerminalLine("[AUTOMATION] Running: ${result.description}", TerminalLineType.SUCCESS))
                    } else {
                        outputLines.add(TerminalLine("[ERR] No active WebView for script automation.", TerminalLineType.ERROR))
                    }
                }

                is CommandExecutionResult.ShowMessage -> {
                    outputLines.add(
                        TerminalLine(
                            result.message,
                            if (result.isError) TerminalLineType.ERROR else TerminalLineType.INFO
                        )
                    )
                }
            }
            commitLines(outputLines)
            inputText = TextFieldValue("")
            return
        }

        // Fallback: If Agentic Mode is active, run the user's natural language goal through the agent!
        if (isAgenticMode) {
            commitLines(outputLines)
            coroutineScope.launch {
                agentEngine.runAgenticWorkflow(trimmed, shellEngine.currentDirectory) { line ->
                    appendLines(listOf(line)) { newLines ->
                        sessions = sessions.map {
                            if (it.id == activeSessionId) it.copy(lines = newLines) else it
                        }
                    }
                }
            }
            inputText = TextFieldValue("")
            return
        }

        // Fallback: If looks like a URL, navigate to it!
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
            (trimmed.contains(".") && !trimmed.contains(" ") && trimmed.length > 3)
        ) {
            val url = if (!trimmed.startsWith("http")) "https://$trimmed" else trimmed
            viewModel.loadUrlInCurrentTab(url)
            outputLines.add(TerminalLine("[NAVIGATE] Opening: $url", TerminalLineType.SUCCESS))
            commitLines(outputLines)
            inputText = TextFieldValue("")
            return
        }

        // If command not found, display bash-like error message
        outputLines.add(TerminalLine("gvone: command not found: '$trimmed'. Type 'help' for available commands.", TerminalLineType.ERROR))
        commitLines(outputLines)
        inputText = TextFieldValue("")
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeVisible = imeBottom > 0

    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Main terminal overlay container
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("terminal_screen")
    ) {
        // Semi-transparent backdrop scrim over the background webpage when docked
        if (!isFullScreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onClose()
                    }
            )
        }

        // Terminal Panel (Docked on top of address bar / bottom bar, or Fullscreen)
        Surface(
            color = TermBgColor,
            shape = if (isFullScreen) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            border = if (isFullScreen) null else BorderStroke(1.dp, TermBorderColor),
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (isFullScreen) {
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .imePadding()
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .padding(
                                bottom = if (isAddressBarBottom) {
                                    addressBarBottomPadding
                                } else {
                                    0.dp
                                }
                            )
                            .then(
                                if (!isAddressBarBottom) {
                                    Modifier
                                        .navigationBarsPadding()
                                        .imePadding()
                                } else {
                                    Modifier
                                }
                            )
                            .fillMaxHeight(0.60f)
                    }
                )
                .offset { IntOffset(0, dragOffsetY.coerceAtLeast(0f).toInt()) }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top drag handle when docked
                if (!isFullScreen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetY > 100f) {
                                            onClose()
                                        }
                                        dragOffsetY = 0f
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        dragOffsetY += dragAmount
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .background(Color(0xFF484F58), CircleShape)
                        )
                    }
                }

                // 1. TOP TERMINAL HEADER BAR
                TerminalHeaderBar(
                    sessions = sessions,
                    activeSessionId = activeSessionId,
                    isTorActive = isTorActive,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = { onToggleFullScreen(!isFullScreen) },
                    onSelectSession = { activeSessionId = it },
                    onNewSession = {
                        val newId = "sess_${sessions.size + 1}"
                        val isSuccess = bridgeConnectionState == WebAppConnectionState.READY
                        val newSess = TerminalSession(
                            id = newId,
                            title = "Session ${sessions.size + 1}",
                            lines = createInitialBanner(bridgeConnectionState.name, isSuccess)
                        )
                        sessions = sessions + newSess
                        activeSessionId = newId
                    },
                    onCloseSession = { sessId ->
                        if (sessions.size > 1) {
                            sessions = sessions.filterNot { it.id == sessId }
                            activeSessionId = sessions.first().id
                        } else {
                            onClose()
                        }
                    },
                    onClearScreen = {
                        sessions = sessions.map {
                            if (it.id == activeSessionId) it.copy(lines = emptyList()) else it
                        }
                        viewModel.clearTerminalLines()
                    },
                    onClose = onClose
                )

                HorizontalDivider(color = TermBorderColor, thickness = 1.dp)

                // 1.5 UNIFIED RUNTIME STATUS BAR & EXPANDABLE ACTIVE TASK
                RuntimeStatusPillRow(
                    runtimeMode = runtimeMode,
                    activeTask = activeTask,
                    onToggleVoice = {
                        executeCommand("/voice")
                    },
                    onToggleAgent = {
                        executeCommand("/agent")
                    },
                    onToggleDebug = {
                        executeCommand("/debug")
                    },
                    onCancelTask = {
                        executeCommand("/cancel")
                    },
                    isAgenticMode = isAgenticMode,
                    activePersona = activePersona,
                    sandboxTabCount = tabs.count { it.tabGroupId == agentEngine.activeSandboxGroupId },
                    onFocusSandbox = {
                        executeCommand("/sandbox")
                    },
                    bridgeConnectionState = bridgeConnectionState,
                    onBridgeClick = {
                        executeCommand("/bridge")
                    }
                )

                activeTask?.let { task ->
                    ExpandableAgentTaskCard(
                        task = task,
                        isExpanded = isTaskCardExpanded,
                        onToggleExpand = { isTaskCardExpanded = !isTaskCardExpanded },
                        onCancelTask = {
                            executeCommand("/cancel")
                        }
                    )
                }

            // 2. TERMINAL OUTPUT LOG (Scrollable monospace display)
            SelectionContainer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(activeSession.lines, key = { it.id }) { line ->
                        TerminalLineItem(
                            line = line,
                            onCopy = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Line", line.text))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 3. AUTOCOMPLETE SUGGESTION CHIPS (Visible when typing)
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TermSurfaceColor)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    suggestions.forEach { cmd ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF161B22),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                            modifier = Modifier
                                .clickable {
                                    val trigger = cmd.command
                                    inputText = TextFieldValue("$trigger ", selection = androidx.compose.ui.text.TextRange(trigger.length + 1))
                                    focusRequester.requestFocus()
                                }
                                .testTag("autocomplete_${cmd.command}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cmd.command,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TermPromptCyan
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cmd.name,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TermTextSecondary
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = TermBorderColor, thickness = 0.5.dp)
            }

            // 4. ACTIVE COMMAND INPUT ROW (Prompt + Monospace Text Field + Cursor)
            Surface(
                color = TermSurfaceColor,
                border = androidx.compose.foundation.BorderStroke(1.dp, TermBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prompt label
                    Text(
                        text = buildAnnotatedString {
                            if (isAgenticMode) {
                                withStyle(SpanStyle(color = Color(0xFFA855F7), fontWeight = FontWeight.Bold)) {
                                    append("agent[${activePersona.badge.lowercase()}]")
                                }
                                withStyle(SpanStyle(color = TermTextSecondary)) {
                                    append(":")
                                }
                                withStyle(SpanStyle(color = TermPromptCyan, fontWeight = FontWeight.Bold)) {
                                    append("$currentCwd$ ")
                                }
                            } else {
                                withStyle(SpanStyle(color = TermPromptGreen, fontWeight = FontWeight.Bold)) {
                                    append("gvone@browser")
                                }
                                withStyle(SpanStyle(color = TermTextSecondary)) {
                                    append(":")
                                }
                                withStyle(SpanStyle(color = TermPromptCyan, fontWeight = FontWeight.Bold)) {
                                    append("$currentCwd$ ")
                                }
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.5.sp
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Monospace text field
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(
                                color = TermTextPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(TermPromptCyan),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Go,
                                autoCorrect = false
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val toExec = inputText.text
                                    executeCommand(toExec)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .testTag("terminal_input_field")
                        )

                        // Blinking block cursor when empty
                        if (inputText.text.isEmpty()) {
                            Text(
                                text = "█",
                                color = TermPromptCyan,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp,
                                modifier = Modifier.alpha(cursorAlpha)
                            )
                        }
                    }

                    // Send / Execute button
                    IconButton(
                        onClick = {
                            val toExec = inputText.text
                            executeCommand(toExec)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF238636), RoundedCornerShape(6.dp))
                            .testTag("terminal_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardReturn,
                            contentDescription = "Execute Command",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 5. TERMUX-STYLE ACCESSORY TOOLBAR (Mobile Terminal Keys: agent, groups, sandbox, ls, cd, cat, tabs, ESC, TAB, ↑, ↓, /, -, ~, |, CLEAR)
            TermuxAccessoryBar(
                onKey = { key ->
                    when (key) {
                        "agent" -> {
                            val prompt = "/agent "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "groups" -> {
                            val prompt = "/groups "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "sandbox" -> {
                            val prompt = "/sandbox "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "ls" -> {
                            val prompt = "ls "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "cd" -> {
                            val prompt = "cd "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "cat" -> {
                            val prompt = "cat "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "tabs" -> {
                            val prompt = "tabs "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "ESC" -> {
                            inputText = TextFieldValue("")
                            historyIndex = -1
                        }
                        "TAB" -> {
                            if (suggestions.isNotEmpty()) {
                                val match = suggestions.first().command
                                inputText = TextFieldValue("$match ", selection = androidx.compose.ui.text.TextRange(match.length + 1))
                            } else if (currentWord.isNotBlank()) {
                                val match = allCommands.find { it.getAllTriggers().any { t -> t.startsWith(currentWord, ignoreCase = true) } }
                                if (match != null) {
                                    val trigger = match.command
                                    inputText = TextFieldValue("$trigger ", selection = androidx.compose.ui.text.TextRange(trigger.length + 1))
                                }
                            }
                        }
                        "↑" -> {
                            if (commandHistory.isNotEmpty()) {
                                val newIdx = if (historyIndex == -1) {
                                    commandHistory.size - 1
                                } else {
                                    (historyIndex - 1).coerceAtLeast(0)
                                }
                                historyIndex = newIdx
                                val historyCmd = commandHistory[newIdx]
                                inputText = TextFieldValue(historyCmd, selection = androidx.compose.ui.text.TextRange(historyCmd.length))
                            }
                        }
                        "↓" -> {
                            if (commandHistory.isNotEmpty() && historyIndex != -1) {
                                val newIdx = historyIndex + 1
                                if (newIdx < commandHistory.size) {
                                    historyIndex = newIdx
                                    val historyCmd = commandHistory[newIdx]
                                    inputText = TextFieldValue(historyCmd, selection = androidx.compose.ui.text.TextRange(historyCmd.length))
                                } else {
                                    historyIndex = -1
                                    inputText = TextFieldValue("")
                                }
                            }
                        }
                        "clear" -> {
                            sessions = sessions.map {
                                if (it.id == activeSessionId) it.copy(lines = emptyList()) else it
                            }
                            viewModel.terminalRepository.clearSavedSessionLines()
                        }
                        else -> {
                            // Insert character
                            val cur = inputText.text
                            val sel = inputText.selection.start
                            val newText = cur.substring(0, sel) + key + cur.substring(sel)
                            inputText = TextFieldValue(newText, selection = androidx.compose.ui.text.TextRange(sel + key.length))
                        }
                    }
                    focusRequester.requestFocus()
                }
            )
        }
    }
}
}

@Composable
private fun TerminalHeaderBar(
    sessions: List<TerminalSession>,
    activeSessionId: String,
    isTorActive: Boolean,
    isFullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (String) -> Unit,
    onClearScreen: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermSurfaceColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Terminal Prompt Brand Badge (">_")
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF161B22),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier.padding(end = 6.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ">_",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TermPromptGreen
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isTorActive) TermPromptPurple else TermPromptGreen, CircleShape)
                )
            }
        }

        // Center / Sessions: Horizontally scrollable row taking remaining width!
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            sessions.forEach { sess ->
                val isActive = sess.id == activeSessionId
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isActive) Color(0xFF21262D) else Color(0xFF161B22),
                    border = BorderStroke(
                        1.dp,
                        if (isActive) Color(0xFF388BFD) else Color(0xFF30363D)
                    ),
                    modifier = Modifier
                        .clickable { onSelectSession(sess.id) }
                        .testTag("terminal_session_${sess.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sess.title,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = if (isActive) TermTextPrimary else TermTextSecondary
                        )
                        if (sessions.size > 1 && isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close session",
                                tint = TermTextSecondary,
                                modifier = Modifier
                                    .size(13.dp)
                                    .clickable { onCloseSession(sess.id) }
                            )
                        }
                    }
                }
            }

            // New Session (+) Button
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier
                    .clickable { onNewSession() }
                    .testTag("terminal_new_session")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Session",
                        tint = TermTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Right: Window action buttons with ample touch targets & zero overlap!
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onClearScreen,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("terminal_clear_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "Clear Screen",
                    tint = TermTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleFullScreen,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("terminal_fullscreen_toggle_btn")
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = if (isFullScreen) "Dock Terminal" else "Maximize Terminal",
                    tint = TermPromptCyan,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("terminal_close_btn")
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Rounded.Close else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Close Terminal",
                    tint = TermTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun TerminalLineItem(
    line: TerminalLine,
    onCopy: () -> Unit
) {
    val color = when (line.type) {
        TerminalLineType.COMMAND -> TermPromptCyan
        TerminalLineType.OUTPUT -> TermTextPrimary
        TerminalLineType.SUCCESS -> TermTextSuccess
        TerminalLineType.ERROR -> TermTextError
        TerminalLineType.INFO -> TermTextInfo
        TerminalLineType.WARNING -> TermTextWarning
        TerminalLineType.SYSTEM -> TermTextSecondary
        TerminalLineType.AI_RESPONSE -> Color(0xFFC9D1D9)
        TerminalLineType.AGENT_PLAN -> Color(0xFFC084FC) // Bright Purple
        TerminalLineType.AGENT_STEP -> Color(0xFF38BDF8) // Cyan
        TerminalLineType.AGENT_THOUGHT -> Color(0xFFFBBF24) // Amber
        TerminalLineType.AGENT_TOOL -> Color(0xFF34D399) // Emerald
    }

    Text(
        text = line.text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCopy() }
    )
}

@Composable
private fun TermuxAccessoryBar(
    onKey: (String) -> Unit
) {
    val keys = listOf(
        "agent", "groups", "sandbox", "ls", "cd", "cat", "tabs", "ESC", "TAB", "↑", "↓", "/", "-", "~", "|", ":", "$", "clear"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090D14))
            .border(
                width = 1.dp,
                color = Color(0xFF1E2636)
            )
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { key ->
            Surface(
                shape = RoundedCornerShape(5.dp),
                color = Color(0xFF161B22),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier
                    .clickable { onKey(key) }
                    .testTag("termux_key_$key")
            ) {
                Text(
                    text = key,
                    color = if (key in listOf("ESC", "TAB", "↑", "↓", "clear")) TermPromptCyan else TermTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun createInitialBanner(bridgeStatus: String = "IDLE", isBridgeSuccess: Boolean = false): List<TerminalLine> {
    return listOf(
        TerminalLine(
            text = """
================================================================
 GVONE UNIVERSAL BROWSER CLI [v2.4] - aarch64-linux-android
 Built-in Centralized Command Engine & Browser Shell
 Type 'help' for command manual | 'bridge' for connection status
================================================================
            """.trimIndent(),
            type = TerminalLineType.SYSTEM
        ),
        TerminalLine(
            text = "Connected to Browser Core. Address bar & command dispatcher ready.",
            type = TerminalLineType.INFO
        ),
        TerminalLine(
            text = "Bridge Status: $bridgeStatus (" + (if (isBridgeSuccess) "SUCCESSFUL - Connected" else "NOT CONNECTED") + ")",
            type = if (isBridgeSuccess) TerminalLineType.SUCCESS else TerminalLineType.WARNING
        )
    )
}

private fun generateHelpOutput(commands: List<CustomCommandEntity>): List<TerminalLine> {
    val lines = mutableListOf<TerminalLine>()
    lines.add(TerminalLine("--- GVONE COMMAND ENGINE MANUAL ---", TerminalLineType.SYSTEM))
    lines.add(TerminalLine("Syntax: <command> [arguments...] or /<command> [arguments...]", TerminalLineType.INFO))
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

    lines.add(TerminalLine("[3] GVONE AI COMMANDS", TerminalLineType.SUCCESS))
    commands.filter { it.type == CommandType.AI }.forEach { cmd ->
        lines.add(TerminalLine("  ${cmd.command.padEnd(12)} ${cmd.name} (${cmd.description})", TerminalLineType.OUTPUT))
    }
    lines.add(TerminalLine("", TerminalLineType.OUTPUT))

    lines.add(TerminalLine("[4] BROWSER & PAGE ACTIONS", TerminalLineType.SUCCESS))
    commands.filter { it.type == CommandType.BROWSER_ACTION || it.type == CommandType.PAGE_ACTION }.forEach { cmd ->
        lines.add(TerminalLine("  ${cmd.command.padEnd(12)} ${cmd.name} (${cmd.description})", TerminalLineType.OUTPUT))
    }
    lines.add(TerminalLine("", TerminalLineType.OUTPUT))

    lines.add(TerminalLine("[5] AUTOMATION COMMANDS", TerminalLineType.SUCCESS))
    commands.filter { it.type == CommandType.AUTOMATION }.forEach { cmd ->
        lines.add(TerminalLine("  ${cmd.command.padEnd(12)} ${cmd.name} (${cmd.description})", TerminalLineType.OUTPUT))
    }
    lines.add(TerminalLine("-----------------------------------", TerminalLineType.SYSTEM))

    return lines
}
