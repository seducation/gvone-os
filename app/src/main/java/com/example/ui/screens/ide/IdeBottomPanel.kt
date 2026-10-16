package com.example.ui.screens.ide

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.GVONEAIService
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Dockable Bottom Panel for VS Code IDE (Terminal, Output, Problems, AI Review).
 */
@Composable
fun IdeBottomPanel(
    activeBottomTab: IdeBottomTab,
    onSelectBottomTab: (IdeBottomTab) -> Unit,
    isPanelOpen: Boolean,
    onClosePanel: () -> Unit,
    isMaximized: Boolean,
    onToggleMaximize: () -> Unit,
    terminalLines: List<TerminalLine>,
    onExecuteTerminalCommand: (String) -> Unit,
    onClearTerminal: () -> Unit,
    activeTab: IdeTab?,
    aiService: GVONEAIService,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    var terminalInput by remember { mutableStateOf("") }
    val terminalListState = rememberLazyListState()

    // Output logs buffer
    val outputLogs = remember { mutableStateListOf<String>() }

    // Problems diagnostics
    val diagnostics = remember(activeTab?.content) {
        val list = mutableListOf<IdeDiagnostic>()
        val content = activeTab?.content.orEmpty()
        content.lines().forEachIndexed { index, line ->
            if (line.contains("TODO") || line.contains("FIXME")) {
                list.add(
                    IdeDiagnostic(
                        severity = DiagnosticSeverity.WARNING,
                        message = line.trim(),
                        fileRelativePath = activeTab?.fileItem?.path.orEmpty(),
                        line = index + 1,
                        column = line.indexOf("TODO").takeIf { it >= 0 } ?: 1
                    )
                )
            }
        }
        list
    }

    // AI Review state
    var aiReviewText by remember { mutableStateOf("") }
    var isReviewing by remember { mutableStateOf(false) }

    // Auto-scroll terminal on new output
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            terminalListState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    if (!isPanelOpen) return

    val panelHeight = if (isMaximized) 420.dp else 230.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(panelHeight)
            .testTag("ide_bottom_panel"),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ================= PANEL HEADER / TAB STRIP =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Tab Selection Pills
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IdeBottomTab.values().forEach { tab ->
                        val isSelected = activeBottomTab == tab
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onSelectBottomTab(tab) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.label.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (tab == IdeBottomTab.PROBLEMS && diagnostics.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "${diagnostics.size}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Panel Window Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            when (activeBottomTab) {
                                IdeBottomTab.TERMINAL -> onClearTerminal()
                                IdeBottomTab.OUTPUT -> outputLogs.clear()
                                IdeBottomTab.AI_REVIEW -> aiReviewText = ""
                                else -> {}
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleMaximize,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isMaximized) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                            contentDescription = "Resize Panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onClosePanel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close Panel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 1.dp
            )

            // ================= PANEL BODY CONTENT =================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0F172A)) // Classic VS Code terminal dark slate
            ) {
                when (activeBottomTab) {
                    IdeBottomTab.TERMINAL -> {
                        TerminalView(
                            lines = terminalLines,
                            listState = terminalListState,
                            commandInput = terminalInput,
                            onCommandInputChange = { terminalInput = it },
                            onExecuteCommand = { cmd ->
                                if (cmd.isNotBlank()) {
                                    onExecuteTerminalCommand(cmd)
                                    terminalInput = ""
                                }
                            }
                        )
                    }
                    IdeBottomTab.OUTPUT -> {
                        OutputView(logs = outputLogs)
                    }
                    IdeBottomTab.PROBLEMS -> {
                        ProblemsView(diagnostics = diagnostics)
                    }
                    IdeBottomTab.AI_REVIEW -> {
                        AiReviewView(
                            activeTab = activeTab,
                            reviewText = aiReviewText,
                            isReviewing = isReviewing,
                            onStartReview = { focusArea ->
                                isReviewing = true
                                coroutineScope.launch {
                                    try {
                                        val code = activeTab?.content.orEmpty()
                                        val prompt = """
                                            Perform a thorough VS Code code review on the following file (${activeTab?.fileItem?.name ?: "active file"}):
                                            Focus Area: $focusArea
                                            ```
                                            $code
                                            ```
                                            List potential bugs, performance bottlenecks, and concrete code improvements.
                                        """.trimIndent()
                                        aiReviewText = aiService.askAI(prompt) ?: "Review completed. No critical issues detected."
                                    } catch (e: Exception) {
                                        aiReviewText = "Error running review: ${e.message}"
                                    } finally {
                                        isReviewing = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Terminal View integrated with real terminal execution
 */
@Composable
private fun TerminalView(
    lines: List<TerminalLine>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    commandInput: String,
    onCommandInputChange: (String) -> Unit,
    onExecuteCommand: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Quick command chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val quickChips = listOf("ls -la", "git status", "git log --oneline -5", "pwd", "date", "help")
            quickChips.forEach { chipCmd ->
                Surface(
                    color = Color(0xFF334155),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.clickable { onExecuteCommand(chipCmd) }
                ) {
                    Text(
                        text = chipCmd,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Terminal lines log
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            items(lines) { line ->
                val textColor = when (line.type) {
                    TerminalLineType.COMMAND -> Color(0xFF38BDF8) // Light blue
                    TerminalLineType.ERROR -> Color(0xFFF87171) // Red
                    TerminalLineType.SUCCESS -> Color(0xFF4ADE80) // Green
                    TerminalLineType.SYSTEM -> Color(0xFFA78BFA) // Purple
                    else -> Color(0xFFE2E8F0) // Light slate
                }
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    ),
                    color = textColor
                )
            }
        }

        // Terminal prompt input line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "code-studio$ ",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                color = Color(0xFF4ADE80)
            )

            BasicTextField(
                value = commandInput,
                onValueChange = onCommandInputChange,
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Color.White
                ),
                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onExecuteCommand(commandInput) }),
                modifier = Modifier
                    .weight(1f)
                    .testTag("ide_terminal_input")
            )

            IconButton(
                onClick = { onExecuteCommand(commandInput) },
                enabled = commandInput.isNotBlank(),
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send Command",
                    tint = if (commandInput.isNotBlank()) Color(0xFF38BDF8) else Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Output Logs View
 */
@Composable
private fun OutputView(logs: List<String>) {
    if (logs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No build or task output yet.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF94A3B8)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

/**
 * Problems / Linting View
 */
@Composable
private fun ProblemsView(diagnostics: List<IdeDiagnostic>) {
    if (diagnostics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No problems detected in current file.",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFF4ADE80)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(diagnostics) { diag ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (diag.severity == DiagnosticSeverity.ERROR) Icons.Rounded.Cancel
                        else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (diag.severity == DiagnosticSeverity.ERROR) Color(0xFFF87171)
                        else Color(0xFFFBBF24),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "[Ln ${diag.line}, Col ${diag.column}] ${diag.message}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * AI Code Review View
 */
@Composable
private fun AiReviewView(
    activeTab: IdeTab?,
    reviewText: String,
    isReviewing: Boolean,
    onStartReview: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val reviewChips = listOf("Full Code Audit", "Security & Safety", "Performance", "Refactor Tips")
            reviewChips.forEach { chip ->
                Button(
                    onClick = { onStartReview(chip) },
                    enabled = !isReviewing,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(chip, fontSize = 10.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isReviewing) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Copilot is reviewing file...", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                }
            }
        } else if (reviewText.isBlank()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Click an audit action above to review ${activeTab?.fileItem?.name ?: "current file"}.",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFF94A3B8)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                item {
                    Text(
                        text = reviewText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        ),
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}
