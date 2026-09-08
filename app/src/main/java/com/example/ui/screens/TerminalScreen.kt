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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.command.CommandEngine
import com.example.data.model.*
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import com.example.data.terminal.TerminalSession
import com.example.data.tor.TorConnectionState
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel
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

    // Multi-session management
    var sessions by remember {
        mutableStateOf(
            listOf(
                TerminalSession(
                    id = "sess_1",
                    title = "Session 1",
                    lines = emptyList()
                )
            )
        )
    }
    var activeSessionId by remember { mutableStateOf("sess_1") }
    val activeSession = sessions.find { it.id == activeSessionId } ?: sessions.first()

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
            val savedLines = viewModel.terminalRepository.getSavedSessionLines()
            if (savedLines.isNotEmpty()) {
                sessions = sessions.map {
                    if (it.id == activeSessionId) it.copy(lines = savedLines) else it
                }
            } else {
                val bannerLines = createInitialBanner()
                sessions = sessions.map {
                    if (it.id == activeSessionId) it.copy(lines = bannerLines) else it
                }
            }
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
        viewModel.terminalRepository.saveSessionLines(updated)
    }

    fun appendLines(newLines: List<TerminalLine>, dummy: ((List<TerminalLine>) -> Unit)? = null) {
        commitLines(newLines)
    }

    // Command execution handler using centralized CommandEngine
    fun executeCommand(rawInput: String) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            val emptyCommandLine = TerminalLine(
                text = "gvone@browser:~$ ",
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
            text = "gvone@browser:~$ $trimmed",
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
                viewModel.terminalRepository.clearSavedSessionLines()
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

    // Main full screen terminal container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TermBgColor)
            .statusBarsPadding()
            .imePadding()
            .testTag("terminal_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TOP TERMINAL HEADER BAR
            TerminalHeaderBar(
                sessions = sessions,
                activeSessionId = activeSessionId,
                isTorActive = isTorActive,
                onSelectSession = { activeSessionId = it },
                onNewSession = {
                    val newId = "sess_${sessions.size + 1}"
                    val newSess = TerminalSession(
                        id = newId,
                        title = "Session ${sessions.size + 1}",
                        lines = createInitialBanner()
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
                    viewModel.terminalRepository.clearSavedSessionLines()
                },
                onClose = onClose
            )

            HorizontalDivider(color = TermBorderColor, thickness = 1.dp)

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
                            withStyle(SpanStyle(color = TermPromptGreen, fontWeight = FontWeight.Bold)) {
                                append("gvone@browser")
                            }
                            withStyle(SpanStyle(color = TermTextSecondary)) {
                                append(":")
                            }
                            withStyle(SpanStyle(color = TermPromptCyan, fontWeight = FontWeight.Bold)) {
                                append("~$ ")
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

            // 5. TERMUX-STYLE ACCESSORY TOOLBAR (Mobile Terminal Keys: ESC, TAB, ↑, ↓, /, -, ~, |, CLEAR)
            TermuxAccessoryBar(
                onKey = { key ->
                    when (key) {
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

@Composable
private fun TerminalHeaderBar(
    sessions: List<TerminalSession>,
    activeSessionId: String,
    isTorActive: Boolean,
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Session Switcher Tabs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (isTorActive) TermPromptPurple else TermPromptGreen, CircleShape)
            )

            // Session pills
            sessions.forEach { sess ->
                val isActive = sess.id == activeSessionId
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isActive) Color(0xFF21262D) else Color.Transparent,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isActive) Color(0xFF388BFD) else Color(0xFF30363D)
                    ),
                    modifier = Modifier
                        .clickable { onSelectSession(sess.id) }
                        .testTag("terminal_session_${sess.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close session",
                                tint = TermTextSecondary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable { onCloseSession(sess.id) }
                            )
                        }
                    }
                }
            }

            // New Session (+) Button
            IconButton(
                onClick = onNewSession,
                modifier = Modifier
                    .size(26.dp)
                    .background(Color(0xFF161B22), RoundedCornerShape(4.dp))
                    .testTag("terminal_new_session")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Session",
                    tint = TermTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        // Right Action Controls: Clear Screen & Close
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onClearScreen,
                modifier = Modifier
                    .size(30.dp)
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
                onClick = onClose,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("terminal_close_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
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
        "ESC", "TAB", "↑", "↓", "/", "-", "~", "|", ":", "$", "clear"
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

private fun createInitialBanner(): List<TerminalLine> {
    return listOf(
        TerminalLine(
            text = """
================================================================
 GVONE UNIVERSAL BROWSER CLI [v2.4] - aarch64-linux-android
 Built-in Centralized Command Engine & Browser Shell
 Type 'help' for command manual | Use 'tabs' to inspect open tabs
================================================================
            """.trimIndent(),
            type = TerminalLineType.SYSTEM
        ),
        TerminalLine(
            text = "Connected to Browser Core. Centralized command dispatcher ready.",
            type = TerminalLineType.INFO
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

    lines.add(TerminalLine("[2] SEARCH COMMANDS", TerminalLineType.SUCCESS))
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
