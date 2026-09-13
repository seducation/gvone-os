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
import androidx.compose.ui.draw.scale
import com.example.ui.components.suggestions.SuggestionChipsBar
import com.example.ui.components.suggestions.DefaultQuickPrompts
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
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
import com.example.ui.components.ConversationHierarchyTreeView
import com.example.ui.components.Level1TaskItem
import com.example.ui.components.CnsDashboardSheet
import com.example.ui.components.ConversationHistorySheet
import com.example.agent.memory.ContextRouter
import com.example.data.command.CommandEngine
import com.example.data.files.GVONEFileSystem
import com.example.data.model.*
import com.example.data.sync.WebAppConnectionState
import com.example.data.terminal.*
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
    val isAgenticMode by viewModel.terminalCommandExecutor.isAgenticMode.collectAsStateWithLifecycle()
    var currentCwd by remember { mutableStateOf(shellEngine.promptPath) }
    var activePersona by remember { mutableStateOf(agentEngine.activePersona) }

    // Unified GVONE Runtime Mode & Task Lifecycle
    val runtimeStateManager = remember { RuntimeStateManager.global }
    val runtimeMode by runtimeStateManager.runtimeMode.collectAsStateWithLifecycle()
    val activeTask by runtimeStateManager.activeTask.collectAsStateWithLifecycle()
    var isTaskCardExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(runtimeMode.execution) {
        val isAgent = runtimeMode.execution == ExecutionType.AGENT
        if (viewModel.terminalCommandExecutor.isAgenticMode.value != isAgent) {
            viewModel.terminalCommandExecutor.setAgenticMode(isAgent)
        }
    }

    // 3-Level Collapsible Conversation Tree & CNS Dashboard states
    val conversationTreeManager = remember { ConversationTreeManager.global }
    var showCnsDashboard by remember { mutableStateOf(false) }
    var showConversationHistorySheet by remember { mutableStateOf(false) }

    var terminalHeightFraction by remember(settings.terminalHeightFraction) {
        mutableFloatStateOf(settings.terminalHeightFraction)
    }

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

    // Collapsed user input commands in stream log (all expanded by default)
    var collapsedCommandIds by remember { mutableStateOf(setOf<String>()) }

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

    // Command execution handler delegating directly to centralized TerminalCommandExecutor
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

        // Parse token and argument
        val spaceIdx = trimmed.indexOf(' ')
        val token = if (spaceIdx != -1) trimmed.substring(0, spaceIdx).trim() else trimmed
        val queryArg = if (spaceIdx != -1) trimmed.substring(spaceIdx + 1).trim() else ""
        val normalizedToken = if (token.startsWith("/")) token.lowercase() else "/${token.lowercase()}"

        // Handle terminal UI-specific built-ins (screen resizing, clear, exit)
        when (normalizedToken) {
            "/height", "/resize" -> {
                when {
                    queryArg.equals("full", ignoreCase = true) || queryArg.equals("max", ignoreCase = true) -> {
                        onToggleFullScreen(true)
                        commitLines(listOf(
                            TerminalLine("$promptPrefix$trimmed", TerminalLineType.COMMAND),
                            TerminalLine("🖥️ Terminal maximized to Fullscreen mode.", TerminalLineType.SUCCESS)
                        ))
                    }
                    queryArg.equals("dock", ignoreCase = true) -> {
                        onToggleFullScreen(false)
                        commitLines(listOf(
                            TerminalLine("$promptPrefix$trimmed", TerminalLineType.COMMAND),
                            TerminalLine("📱 Terminal docked to ${(terminalHeightFraction * 100).toInt()}%. ", TerminalLineType.INFO)
                        ))
                    }
                    queryArg.toIntOrNull() != null -> {
                        val pct = queryArg.toInt().coerceIn(50, 98)
                        val frac = pct / 100f
                        terminalHeightFraction = frac
                        viewModel.updateTerminalHeightFraction(frac)
                        if (isFullScreen) onToggleFullScreen(false)
                        commitLines(listOf(
                            TerminalLine("$promptPrefix$trimmed", TerminalLineType.COMMAND),
                            TerminalLine("📐 Terminal height adjusted to $pct% of screen.", TerminalLineType.SUCCESS)
                        ))
                    }
                    queryArg.equals("increase", ignoreCase = true) || queryArg.equals("up", ignoreCase = true) -> {
                        val newFrac = (terminalHeightFraction + 0.10f).coerceIn(0.50f, 0.98f)
                        terminalHeightFraction = newFrac
                        viewModel.updateTerminalHeightFraction(newFrac)
                        commitLines(listOf(
                            TerminalLine("$promptPrefix$trimmed", TerminalLineType.COMMAND),
                            TerminalLine("📐 Terminal height increased to ${(newFrac * 100).toInt()}% of screen.", TerminalLineType.SUCCESS)
                        ))
                    }
                    queryArg.equals("decrease", ignoreCase = true) || queryArg.equals("down", ignoreCase = true) -> {
                        val newFrac = (terminalHeightFraction - 0.10f).coerceIn(0.50f, 0.98f)
                        terminalHeightFraction = newFrac
                        viewModel.updateTerminalHeightFraction(newFrac)
                        commitLines(listOf(
                            TerminalLine("$promptPrefix$trimmed", TerminalLineType.COMMAND),
                            TerminalLine("📐 Terminal height adjusted to ${(newFrac * 100).toInt()}% of screen.", TerminalLineType.SUCCESS)
                        ))
                    }
                    else -> {
                        commitLines(listOf(
                            TerminalLine("$promptPrefix$trimmed", TerminalLineType.COMMAND),
                            TerminalLine("📐 Current Terminal Height: ${(terminalHeightFraction * 100).toInt()}% of screen (fullscreen: $isFullScreen)", TerminalLineType.INFO),
                            TerminalLine("Usage: /height [50-98 | increase | decrease | full | dock]", TerminalLineType.OUTPUT)
                        ))
                    }
                }
                inputText = TextFieldValue("")
                return
            }

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
                inputText = TextFieldValue("")
                return
            }

            else -> {
                // Delegate ALL command parsing, execution, and dispatch to centralized TerminalCommandExecutor
                viewModel.terminalCommandExecutor.executeCommand(
                    rawInput = trimmed,
                    origin = CommandOrigin.TERMINAL,
                    onOpenAgentDashboard = onOpenAgentDashboard
                )
                inputText = TextFieldValue("")
            }
        }
    }

    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    val isImeVisible = imeBottom > 0

    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Main terminal overlay container
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("terminal_screen")
    ) {
        val totalHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val isPinnedToScreen = settings.terminalPinnedToScreen
        val isSwappedPosition = settings.terminalSwappedPosition

        // Semi-transparent backdrop scrim over the background webpage when docked (Disabled when pinned to screen so web is interactive)
        if (!isFullScreen && !isPinnedToScreen) {
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

        // Terminal Panel (Docked on top of address bar / bottom bar, Half-Screen Pinned, or Fullscreen)
        Surface(
            color = TermBgColor,
            shape = if (isFullScreen) {
                RoundedCornerShape(0.dp)
            } else if (isSwappedPosition) {
                RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
            } else {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            },
            border = if (isFullScreen) null else BorderStroke(1.dp, TermBorderColor),
            shadowElevation = 16.dp,
            modifier = Modifier
                .align(if (isSwappedPosition) Alignment.TopCenter else Alignment.BottomCenter)
                .fillMaxWidth()
                .then(
                    if (isFullScreen) {
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .imePadding()
                    } else if (isPinnedToScreen) {
                        Modifier
                            .fillMaxHeight(0.50f)
                            .then(
                                if (isSwappedPosition) {
                                    Modifier.statusBarsPadding()
                                } else {
                                    Modifier.navigationBarsPadding().imePadding()
                                }
                            )
                    } else {
                        Modifier
                            .statusBarsPadding()
                            .padding(
                                bottom = if (isAddressBarBottom && !isSwappedPosition) {
                                    addressBarBottomPadding
                                } else {
                                    0.dp
                                }
                            )
                            .then(
                                if (!isAddressBarBottom || isSwappedPosition) {
                                    Modifier
                                        .navigationBarsPadding()
                                        .imePadding()
                                } else {
                                    Modifier
                                }
                            )
                            .fillMaxHeight(terminalHeightFraction.coerceIn(0.50f, 0.98f))
                    }
                )
                .offset { IntOffset(0, if (isSwappedPosition) -dragOffsetY.coerceAtLeast(0f).toInt() else dragOffsetY.coerceAtLeast(0f).toInt()) }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. In DEFAULT position: Suggestion Chips Bar is on TOP of the Terminal!
                // Layout hierarchy: Chip -> Terminal -> Address bar
                if (!isSwappedPosition) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0B0F17))
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .testTag("terminal_top_suggestion_chips")
                    ) {
                        SuggestionChipsBar(
                            prompts = DefaultQuickPrompts.items,
                            isSuggestivePopupOpen = false,
                            onToggleBulb = {
                                viewModel.setShowTerminalCommands(!viewModel.showTerminalCommands.value)
                            },
                            onSelectPrompt = { promptText ->
                                inputText = TextFieldValue(promptText, selection = androidx.compose.ui.text.TextRange(promptText.length))
                                focusRequester.requestFocus()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    HorizontalDivider(color = TermBorderColor, thickness = 0.5.dp)
                }

                // Top drag handle when docked and not swapped
                if (!isFullScreen && !isSwappedPosition) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp)
                            .pointerInput(totalHeightPx) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        if (dragOffsetY > 120f) {
                                            onClose()
                                        } else {
                                            viewModel.updateTerminalHeightFraction(terminalHeightFraction)
                                        }
                                        dragOffsetY = 0f
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        val deltaFraction = -dragAmount / totalHeightPx
                                        val candidate = terminalHeightFraction + deltaFraction
                                        if (candidate in 0.55f..0.98f) {
                                            terminalHeightFraction = candidate
                                        } else if (candidate < 0.55f && dragAmount > 0) {
                                            dragOffsetY += dragAmount
                                        }
                                    }
                                )
                            }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                val next = when {
                                    terminalHeightFraction < 0.80f -> 0.85f
                                    terminalHeightFraction < 0.90f -> 0.95f
                                    else -> 0.75f
                                }
                                terminalHeightFraction = next
                                viewModel.updateTerminalHeightFraction(next)
                                Toast.makeText(context, "Terminal height: ${(next * 100).toInt()}%", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(42.dp)
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
                    isPinnedToScreen = isPinnedToScreen,
                    onTogglePinToScreen = {
                        val newPinned = !isPinnedToScreen
                        viewModel.setTerminalPinnedToScreen(newPinned)
                        Toast.makeText(
                            context,
                            if (newPinned) "Terminal Pinned to Screen (50% Split View). Website below is fully interactive!" else "Terminal Unpinned. Normal docked mode restored.",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    terminalHeightFraction = terminalHeightFraction,
                    onAdjustHeight = {
                        val next = when {
                            terminalHeightFraction < 0.80f -> 0.85f
                            terminalHeightFraction < 0.90f -> 0.95f
                            else -> 0.75f
                        }
                        terminalHeightFraction = next
                        viewModel.updateTerminalHeightFraction(next)
                        Toast.makeText(context, "Terminal height: ${(next * 100).toInt()}%", Toast.LENGTH_SHORT).show()
                    },
                    onToggleFullScreen = { onToggleFullScreen(!isFullScreen) },
                    isSwappedPosition = isSwappedPosition,
                    onToggleSwapPosition = {
                        viewModel.toggleTerminalSwappedPosition()
                        val nextSwapped = !isSwappedPosition
                        Toast.makeText(
                            context,
                            if (nextSwapped) "Position swapped: Terminal at Top, Suggestions below" else "Position swapped: Terminal at Bottom, Suggestions above",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onOpenConversationHistory = {
                        showConversationHistorySheet = true
                    },
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
                    onToggleText = {
                        executeCommand("/text")
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
                    },
                    onCnsClick = {
                        showCnsDashboard = true
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

                // 2. MAIN CLI STREAM LOG (Grouped by User Input with Expandable/Collapsible Action Logs)
                val treeTasks by conversationTreeManager.tasks.collectAsState()
                val activeTaskIdTree by conversationTreeManager.activeTaskId.collectAsState()

                // Group terminal lines into blocks by user input command
                val commandGroups = remember(activeSession.lines) {
                    val groups = mutableListOf<TerminalCommandBlock>()
                    var currentCmd: TerminalLine? = null
                    val currentActions = mutableListOf<TerminalLine>()

                    for (line in activeSession.lines) {
                        if (line.type == TerminalLineType.COMMAND) {
                            if (currentCmd != null || currentActions.isNotEmpty()) {
                                groups.add(TerminalCommandBlock(currentCmd, currentActions.toList()))
                                currentActions.clear()
                            }
                            currentCmd = line
                        } else {
                            currentActions.add(line)
                        }
                    }
                    if (currentCmd != null || currentActions.isNotEmpty()) {
                        groups.add(TerminalCommandBlock(currentCmd, currentActions.toList()))
                    }
                    groups
                }

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
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        commandGroups.forEach { group ->
                            val cmd = group.commandLine
                            if (cmd == null) {
                                // Initial lines (welcome banner, status, etc.) before first user input
                                items(group.actionLines, key = { it.id }) { line ->
                                    TerminalLineItem(
                                        line = line,
                                        onCopy = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Line", line.text))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            } else {
                                val isExpanded = cmd.id !in collapsedCommandIds

                                // Every User Input: with right-direction collapsible/expandable icon
                                item(key = cmd.id) {
                                    UserInputCommandItem(
                                        commandLine = cmd,
                                        actionCount = group.actionLines.size,
                                        isExpanded = isExpanded,
                                        onToggleExpand = {
                                            collapsedCommandIds = if (isExpanded) {
                                                collapsedCommandIds + cmd.id
                                            } else {
                                                collapsedCommandIds - cmd.id
                                            }
                                        },
                                        onCopy = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Command", cmd.text))
                                            Toast.makeText(context, "Command copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }

                                if (isExpanded) {
                                    items(group.actionLines, key = { it.id }) { line ->
                                        if (line.type == TerminalLineType.EXPANDABLE_TASK) {
                                            val task = treeTasks.find { it.id == line.taskId }
                                            if (task != null) {
                                                Level1TaskItem(
                                                    task = task,
                                                    isActive = activeTaskIdTree == task.id,
                                                    onToggleExpand = {
                                                        conversationTreeManager.toggleTaskExpansion(task.id)
                                                    },
                                                    onToggleAgentExpand = { agentId ->
                                                        conversationTreeManager.toggleAgentExpansion(task.id, agentId)
                                                    },
                                                    onContinue = {
                                                        executeCommand(task.commandPrompt.ifBlank { "/agent ${task.title}" })
                                                    },
                                                    onInspect = {
                                                        executeCommand("/status")
                                                    },
                                                    onRemove = {
                                                        conversationTreeManager.removeTask(task.id)
                                                    },
                                                    modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 2.dp)
                                                )
                                            } else {
                                                TerminalLineItem(
                                                    line = line,
                                                    onCopy = {
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("Terminal Line", line.text))
                                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        } else {
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
                                } else if (group.actionLines.isNotEmpty()) {
                                    item(key = "${cmd.id}_collapsed_summary") {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    collapsedCommandIds = collapsedCommandIds - cmd.id
                                                }
                                                .padding(start = 12.dp, top = 1.dp, bottom = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "└── [▸ ${group.actionLines.size} action logs collapsed — tap to expand]",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFF6E7681)
                                            )
                                        }
                                    }
                                }
                            }
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
                            val isVoice = runtimeMode.isVoiceActive || runtimeMode.interaction == InteractionType.VOICE
                            if (isAgenticMode) {
                                withStyle(SpanStyle(color = Color(0xFFA855F7), fontWeight = FontWeight.Bold)) {
                                    append(if (isVoice) "agent[${activePersona.badge.lowercase()}:voice]" else "agent[${activePersona.badge.lowercase()}:text]")
                                }
                                withStyle(SpanStyle(color = TermTextSecondary)) {
                                    append(":")
                                }
                                withStyle(SpanStyle(color = TermPromptCyan, fontWeight = FontWeight.Bold)) {
                                    append("$currentCwd$ ")
                                }
                            } else {
                                withStyle(SpanStyle(color = if (isVoice) TermPromptCyan else TermPromptGreen, fontWeight = FontWeight.Bold)) {
                                    append(if (isVoice) "gvone(voice)@browser" else "gvone@browser")
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
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (inputText.text.isEmpty()) {
                            Text(
                                text = if (isAgenticMode) "Type autonomous goal (Agent is ON)..." else "Type command or /help (Agent is OFF)...",
                                color = Color(0xFF555D68),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.5.sp
                            )
                        }
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

            // 5. TERMUX-STYLE ACCESSORY TOOLBAR (Mobile Terminal Keys: agent, voice, text, groups, sandbox, ls, cd, cat, tabs, ESC, TAB, ↑, ↓, /, -, ~, |, CLEAR)
            TermuxAccessoryBar(
                onKey = { key ->
                    when (key) {
                        "agent" -> {
                            val prompt = "/agent "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "/on" -> {
                            val cur = inputText.text.trimEnd()
                            val nextText = if (cur.isEmpty()) "/on " else "$cur /on "
                            inputText = TextFieldValue(nextText, selection = androidx.compose.ui.text.TextRange(nextText.length))
                        }
                        "/off" -> {
                            val cur = inputText.text.trimEnd()
                            val nextText = if (cur.isEmpty()) "/off " else "$cur /off "
                            inputText = TextFieldValue(nextText, selection = androidx.compose.ui.text.TextRange(nextText.length))
                        }
                        "voice" -> {
                            val prompt = "/voice "
                            inputText = TextFieldValue(prompt, selection = androidx.compose.ui.text.TextRange(prompt.length))
                        }
                        "text" -> {
                            val prompt = "/text "
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

            // 6. When in SWAPPED position: Suggestion Chips Bar is at the BOTTOM!
            // Layout hierarchy when swapped: Terminal -> Chip -> Address bar
            if (isSwappedPosition) {
                HorizontalDivider(color = TermBorderColor, thickness = 0.5.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B0F17))
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("terminal_bottom_suggestion_chips")
                ) {
                    SuggestionChipsBar(
                        prompts = DefaultQuickPrompts.items,
                        isSuggestivePopupOpen = false,
                        onToggleBulb = {
                            viewModel.setShowTerminalCommands(!viewModel.showTerminalCommands.value)
                        },
                        onSelectPrompt = { promptText ->
                            inputText = TextFieldValue(promptText, selection = androidx.compose.ui.text.TextRange(promptText.length))
                            focusRequester.requestFocus()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Sovereign CNS Dashboard Modal Sheet
    if (showCnsDashboard) {
        CnsDashboardSheet(
            onDismiss = { showCnsDashboard = false },
            onExecuteGoal = { goal ->
                executeCommand(goal)
            }
        )
    }

    // Sovereign Conversation History Sheet
    if (showConversationHistorySheet) {
        ConversationHistorySheet(
            onDismiss = { showConversationHistorySheet = false },
            onSelectPrompt = { prompt ->
                inputText = TextFieldValue(prompt, TextRange(prompt.length))
                focusRequester.requestFocus()
                keyboardController?.show()
            },
            commandHistory = commandHistory,
            treeManager = conversationTreeManager,
            contextRouter = ContextRouter.global
        )
    }
}
}

@Composable
private fun TerminalHeaderBar(
    sessions: List<TerminalSession>,
    activeSessionId: String,
    isTorActive: Boolean,
    isFullScreen: Boolean,
    isPinnedToScreen: Boolean = false,
    onTogglePinToScreen: () -> Unit,
    terminalHeightFraction: Float = 0.85f,
    onAdjustHeight: () -> Unit,
    onToggleFullScreen: () -> Unit,
    isSwappedPosition: Boolean = false,
    onToggleSwapPosition: () -> Unit,
    onOpenConversationHistory: () -> Unit,
    onSelectSession: (String) -> Unit,
    onNewSession: () -> Unit,
    onCloseSession: (String) -> Unit,
    onClearScreen: () -> Unit,
    onClose: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TermSurfaceColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Conversation History Icon Button (History Icon placed on top left)
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF161B22),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier
                .padding(end = 6.dp)
                .clickable { onOpenConversationHistory() }
                .testTag("terminal_history_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = "Show Conversation History",
                    tint = TermPromptCyan,
                    modifier = Modifier.size(16.dp)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (isTorActive) TermPromptPurple else TermPromptGreen, CircleShape)
                )
            }
        }

        // Center / Sessions: Horizontally scrollable row taking remaining width
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

        // Right: Window action buttons (Swap Button + Consolidated Dropdown Menu + Close)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Quick Direct Button for Pin / Unpin Terminal
            IconButton(
                onClick = onTogglePinToScreen,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("terminal_header_pin_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = if (isPinnedToScreen) "Unpin Terminal" else "Pin Terminal to Screen",
                    tint = if (isPinnedToScreen) Color(0xFF38BDF8) else TermTextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Button for swiping/swapping the position between suggestions chip and terminal
            IconButton(
                onClick = onToggleSwapPosition,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("terminal_swap_position_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = if (isSwappedPosition) "Restore Position (Default: Chip Top, Terminal Bottom)" else "Swap Position (Terminal Top, Chip Bottom)",
                    tint = if (isSwappedPosition) Color(0xFF38BDF8) else TermTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Single Drop-Down Menu housing: Full Screen, Adjust Height, Pin to Screen, Clear Chat
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("terminal_more_menu_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Terminal Menu",
                        tint = if (isPinnedToScreen) Color(0xFF38BDF8) else TermTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(Color(0xFF161B22))
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(8.dp))
                ) {
                    // 0. Conversation History
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = "Conversation History",
                                    color = Color(0xFFE6EDF6),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "History of all tasks, agent trees & dialogue",
                                    color = Color(0xFF8B949E),
                                    fontSize = 10.5.sp
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = TermPromptCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onOpenConversationHistory()
                        },
                        modifier = Modifier.testTag("terminal_menu_conversation_history")
                    )

                    HorizontalDivider(color = Color(0xFF21262D), thickness = 0.5.dp)

                    // 1. Pin to Screen (Half Screen Split) - simultaneous interaction with website
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = if (isPinnedToScreen) "Unpin from Screen" else "Pin to Screen (50% Split)",
                                    color = Color(0xFFE6EDF6),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isPinnedToScreen) "Simultaneous web browsing active" else "Pin half-screen & interact simultaneously",
                                    color = if (isPinnedToScreen) Color(0xFF38BDF8) else Color(0xFF8B949E),
                                    fontSize = 10.5.sp
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = if (isPinnedToScreen) Color(0xFF38BDF8) else Color(0xFF8E9BAE),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (isPinnedToScreen) {
                                Text(
                                    text = "PINNED",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        },
                        onClick = {
                            showMenu = false
                            onTogglePinToScreen()
                        },
                        modifier = Modifier
                            .testTag("terminal_pin_toggle_btn")
                            .testTag("terminal_menu_pin_screen")
                    )

                    HorizontalDivider(color = Color(0xFF21262D), thickness = 0.5.dp)

                    // 2. Adjust Height
                    if (!isFullScreen) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Adjust Height (${(terminalHeightFraction * 100).toInt()}%)",
                                    color = Color(0xFFE6EDF6),
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Height,
                                    contentDescription = null,
                                    tint = TermPromptGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onAdjustHeight()
                            },
                            modifier = Modifier
                                .testTag("terminal_adjust_height_btn")
                                .testTag("terminal_menu_adjust_height")
                        )
                    }

                    // 3. Full Screen / Dock Toggle
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isFullScreen) "Dock Terminal" else "Full Screen Mode",
                                color = Color(0xFFE6EDF6),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (isFullScreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                contentDescription = null,
                                tint = TermPromptCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleFullScreen()
                        },
                        modifier = Modifier
                            .testTag("terminal_fullscreen_toggle_btn")
                            .testTag("terminal_menu_fullscreen")
                    )

                    // 4. Clear Chat / Screen
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Clear Chat / Screen",
                                color = Color(0xFFEF4444),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onClearScreen()
                        },
                        modifier = Modifier
                            .testTag("terminal_clear_btn")
                            .testTag("terminal_menu_clear_chat")
                    )

                    HorizontalDivider(color = Color(0xFF21262D), thickness = 0.5.dp)

                    // 5. Swap Suggestions & Terminal Position
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isSwappedPosition) "Restore Default (Chip on Top, Terminal Bottom)" else "Swap Position (Terminal on Top, Chip Bottom)",
                                color = Color(0xFFE6EDF6),
                                fontSize = 13.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.SwapVert,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleSwapPosition()
                        },
                        modifier = Modifier.testTag("terminal_menu_swap_position")
                    )
                }
            }

            // Close button
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

data class TerminalCommandBlock(
    val commandLine: TerminalLine?,
    val actionLines: List<TerminalLine>
)

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
        TerminalLineType.EXPANDABLE_TASK -> Color(0xFFA855F7) // Purple
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

/**
 * User Input Item: Renders the user input command line with a right-direction
 * expandable and collapsible icon next to it, allowing the subsequent action logs to expand and collapse.
 */
@Composable
private fun UserInputCommandItem(
    commandLine: TerminalLine,
    actionCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isExpanded) Color(0xFF0D121B) else Color(0xFF161B22),
        border = BorderStroke(
            0.5.dp,
            if (isExpanded) TermPromptCyan.copy(alpha = 0.35f) else Color(0xFF30363D)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (actionCount > 0) onToggleExpand() else onCopy() }
            .testTag("user_input_${commandLine.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = commandLine.text,
                color = TermPromptCyan,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 17.sp,
                modifier = Modifier.weight(1f)
            )

            // Right-direction expandable and collapsible icon next to user input
            if (actionCount > 0) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = if (isExpanded) TermPromptCyan.copy(alpha = 0.15f) else Color(0xFF21262D),
                    border = BorderStroke(0.5.dp, if (isExpanded) TermPromptCyan.copy(alpha = 0.5f) else Color(0xFF30363D)),
                    modifier = Modifier
                        .clickable { onToggleExpand() }
                        .testTag("user_input_toggle_${commandLine.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$actionCount actions",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpanded) TermPromptCyan else Color(0xFF8B949E)
                        )
                        // Right direction chevron icon
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse Action Logs" else "Expand Action Logs",
                            tint = if (isExpanded) TermPromptCyan else Color(0xFF8B949E),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TermuxAccessoryBar(
    onKey: (String) -> Unit
) {
    val keys = listOf(
        "agent", "/on", "/off", "voice", "text", "groups", "sandbox", "ls", "cd", "cat", "tabs", "ESC", "TAB", "↑", "↓", "/", "-", "~", "|", ":", "$", "clear"
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
    val lines = mutableListOf<TerminalLine>()
    lines.add(
        TerminalLine(
            text = """
================================================================
 GVONE UNIVERSAL BROWSER CLI [v2.4] - aarch64-linux-android
 Built-in Centralized Command Engine & Browser Shell
 Type 'help' for command manual | 'cns' for neural dashboard
================================================================
            """.trimIndent(),
            type = TerminalLineType.SYSTEM
        )
    )
    lines.add(
        TerminalLine(
            text = "Connected to Browser Core. Address bar & command dispatcher ready.",
            type = TerminalLineType.INFO
        )
    )
    lines.add(
        TerminalLine(
            text = "Bridge Status: $bridgeStatus (" + (if (isBridgeSuccess) "SUCCESSFUL - Connected" else "NOT CONNECTED") + ")",
            type = if (isBridgeSuccess) TerminalLineType.SUCCESS else TerminalLineType.WARNING
        )
    )
    lines.add(
        TerminalLine(
            text = "── RECENT CONVERSATIONS (3-Level Expandable Stream Log) ──",
            type = TerminalLineType.SYSTEM
        )
    )

    // Seed the conversation tasks as live expandable blocks directly in the stream
    ConversationTreeManager.global.tasks.value.forEach { task ->
        lines.add(
            TerminalLine(
                text = task.title,
                type = TerminalLineType.EXPANDABLE_TASK,
                taskId = task.id
            )
        )
    }

    lines.add(
        TerminalLine(
            text = "💡 Tap any [TASK] above to expand Agents (Level 2) and Steps/Tools (Level 3).",
            type = TerminalLineType.INFO
        )
    )

    return lines
}

private fun generateHelpOutput(commands: List<CustomCommandEntity>): List<TerminalLine> {
    val lines = mutableListOf<TerminalLine>()
    lines.add(TerminalLine("--- GVONE COMMAND ENGINE MANUAL ---", TerminalLineType.SYSTEM))
    lines.add(TerminalLine("Syntax: <command> [arguments...] or /<command> [arguments...]", TerminalLineType.INFO))
    lines.add(TerminalLine("", TerminalLineType.OUTPUT))

    lines.add(TerminalLine("[1] TERMINAL & SYSTEM UTILITIES", TerminalLineType.SUCCESS))
    lines.add(TerminalLine("  help, ?              Display this manual", TerminalLineType.OUTPUT))
    lines.add(TerminalLine("  height [50-98|full]  Adjust or increase docked terminal height", TerminalLineType.OUTPUT))
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
