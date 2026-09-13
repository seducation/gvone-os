package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.core.AgentStatus
import com.example.agent.memory.ContextRouter
import com.example.agent.memory.ConversationMessage
import com.example.data.terminal.ConversationTaskNode
import com.example.data.terminal.ConversationTreeManager
import java.text.SimpleDateFormat
import java.util.*

private val HistorySheetBg = Color(0xFF090C10)
private val CardBg = Color(0xFF0D1117)
private val CardSubBg = Color(0xFF161B22)
private val BorderColor = Color(0xFF21262D)
private val BorderLight = Color(0xFF30363D)

private val AccentCyan = Color(0xFF38BDF8)
private val AccentGreen = Color(0xFF3FB950)
private val AccentPurple = Color(0xFFA855F7)
private val AccentAmber = Color(0xFFEAB308)
private val AccentRed = Color(0xFFF85149)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextMuted = Color(0xFF8B949E)

enum class ConversationHistoryTab(val label: String, val icon: String) {
    TASKS("Tasks & Agents", "🌳"),
    CHAT("Chat & Voice", "💬"),
    COMMANDS("Commands", "⌨️")
}

/**
 * Modal Bottom Sheet showing the complete conversation history:
 * 1. Multi-Agent Conversation Trees & Tasks (prompts, agents, step logs, results)
 * 2. Conversational Chat & Voice exchanges (User <-> Assistant messages)
 * 3. Terminal Command execution history
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistorySheet(
    onDismiss: () -> Unit,
    onSelectPrompt: (String) -> Unit = {},
    commandHistory: List<String> = emptyList(),
    treeManager: ConversationTreeManager = ConversationTreeManager.global,
    contextRouter: ContextRouter = ContextRouter.global,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tasks by treeManager.tasks.collectAsState()
    val activeTaskId by treeManager.activeTaskId.collectAsState()
    val chatMessages = remember(contextRouter) { contextRouter.getAllConversationMessages() }

    var selectedTab by remember { mutableStateOf(ConversationHistoryTab.TASKS) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ALL") } // ALL, ACTIVE, COMPLETED
    var showClearConfirmationDialog by remember { mutableStateOf(false) }

    // Filter tasks based on search query and status
    val filteredTasks = remember(tasks, searchQuery, statusFilter) {
        tasks.filter { task ->
            val matchesStatus = when (statusFilter) {
                "ACTIVE" -> task.status == AgentStatus.EXECUTING || task.status == AgentStatus.PLANNING
                "COMPLETED" -> task.status == AgentStatus.COMPLETED
                else -> true
            }
            val matchesQuery = if (searchQuery.isBlank()) true else {
                task.title.contains(searchQuery, ignoreCase = true) ||
                    task.commandPrompt.contains(searchQuery, ignoreCase = true) ||
                    task.summaryResult?.contains(searchQuery, ignoreCase = true) == true ||
                    task.agents.any { agent -> agent.agentName.contains(searchQuery, ignoreCase = true) }
            }
            matchesStatus && matchesQuery
        }
    }

    val filteredChatMessages = remember(chatMessages, searchQuery) {
        if (searchQuery.isBlank()) chatMessages else {
            chatMessages.filter { it.text.contains(searchQuery, ignoreCase = true) || it.role.contains(searchQuery, ignoreCase = true) }
        }
    }

    val filteredCommands = remember(commandHistory, searchQuery) {
        if (searchQuery.isBlank()) commandHistory else {
            commandHistory.filter { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HistorySheetBg,
        tonalElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0xFF484F58), CircleShape)
            )
        },
        modifier = modifier.testTag("conversation_history_modal_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            // 1. Header Bar with Title, Counters, and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))),
                                CircleShape
                            )
                            .border(1.dp, AccentCyan.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Conversation History",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Conversation History",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AccentCyan.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, AccentCyan.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "${tasks.size} CONVERSATIONS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "History of all autonomous tasks, agent steps & dialogue",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { showClearConfirmationDialog = true },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("clear_conversation_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear History",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("close_conversation_history_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Search & Filter Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CardBg,
                border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search conversation history, prompts, agents...",
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentCyan
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("conversation_search_input")
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear search",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Tab Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConversationHistoryTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val count = when (tab) {
                        ConversationHistoryTab.TASKS -> filteredTasks.size
                        ConversationHistoryTab.CHAT -> filteredChatMessages.size
                        ConversationHistoryTab.COMMANDS -> filteredCommands.size
                    }
                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) AccentCyan.copy(alpha = 0.2f) else CardBg,
                        border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderColor),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("history_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(text = tab.icon, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) AccentCyan else TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "($count)",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) TextPrimary else TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Content Area Based on Tab
            when (selectedTab) {
                ConversationHistoryTab.TASKS -> {
                    // Task Status Filter Chips & Expand/Collapse All
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("ALL", "ACTIVE", "COMPLETED").forEach { filter ->
                                val isSelected = statusFilter == filter
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) AccentPurple.copy(alpha = 0.25f) else CardBg,
                                    border = BorderStroke(1.dp, if (isSelected) AccentPurple else BorderColor),
                                    modifier = Modifier.clickable { statusFilter = filter }
                                ) {
                                    Text(
                                        text = filter,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFFE9D5FF) else TextMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CardSubBg,
                                modifier = Modifier.clickable { treeManager.expandAll() }
                            ) {
                                Text(
                                    text = "▾ EXPAND ALL",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CardSubBg,
                                modifier = Modifier.clickable { treeManager.collapseAll() }
                            ) {
                                Text(
                                    text = "▸ COLLAPSE",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (filteredTasks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountTree,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No conversations match '$searchQuery'" else "No conversation tasks recorded yet",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Dispatch autonomous tasks with '/agent <goal>' in the terminal",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF6E7681)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredTasks, key = { it.id }) { task ->
                                ConversationHistoryTaskCard(
                                    task = task,
                                    isActive = task.id == activeTaskId,
                                    onToggleExpand = { treeManager.toggleTaskExpansion(task.id) },
                                    onToggleAgentExpand = { agentId -> treeManager.toggleAgentExpansion(task.id, agentId) },
                                    onContinueInTerminal = {
                                        val prompt = task.commandPrompt.ifBlank { "/agent ${task.title}" }
                                        onSelectPrompt(prompt)
                                        onDismiss()
                                    },
                                    onCopyTranscript = {
                                        val transcript = formatTaskTranscript(task)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Conversation Transcript", transcript)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Conversation copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = {
                                        treeManager.removeTask(task.id)
                                        Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                ConversationHistoryTab.CHAT -> {
                    if (filteredChatMessages.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Chat,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No chat messages match '$searchQuery'" else "No chat or voice messages recorded",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Chat dialogue is automatically archived from voice mode & conversational turns",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF6E7681)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredChatMessages) { message ->
                                ChatMessageHistoryItem(
                                    message = message,
                                    onSendToTerminal = {
                                        onSelectPrompt(message.text)
                                        onDismiss()
                                    },
                                    onCopy = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Chat Message", message.text)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Message copied", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }
                }

                ConversationHistoryTab.COMMANDS -> {
                    if (filteredCommands.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Terminal,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No commands match '$searchQuery'" else "No executed command history",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filteredCommands) { cmd ->
                                Surface(
                                    onClick = {
                                        onSelectPrompt(cmd)
                                        onDismiss()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = CardBg,
                                    border = BorderStroke(1.dp, BorderColor),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "$",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentGreen
                                            )
                                            Text(
                                                text = cmd,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = TextPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Run in Terminal",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Clear History Confirmation Dialog
    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            containerColor = CardBg,
            title = {
                Text(
                    text = "Clear Conversation History?",
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will remove all conversation tasks, execution trees, and chat logs from local history. This cannot be undone.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        treeManager.clearAllTasks()
                        contextRouter.clearConversationHistory()
                        showClearConfirmationDialog = false
                        Toast.makeText(context, "Conversation history cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmationDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

/**
 * Rich Card displaying an autonomous conversation task with full Level 1-3 details.
 */
@Composable
private fun ConversationHistoryTaskCard(
    task: ConversationTaskNode,
    isActive: Boolean,
    onToggleExpand: () -> Unit,
    onToggleAgentExpand: (String) -> Unit,
    onContinueInTerminal: () -> Unit,
    onCopyTranscript: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (statusColor, statusLabel) = when (task.status) {
        AgentStatus.COMPLETED -> Pair(AccentGreen, "DONE")
        AgentStatus.EXECUTING -> Pair(AccentCyan, "RUNNING")
        AgentStatus.PLANNING -> Pair(AccentAmber, "PLANNING")
        AgentStatus.PAUSED -> Pair(AccentAmber, "PAUSED")
        AgentStatus.FAILED, AgentStatus.CANCELLED -> Pair(AccentRed, "FAILED")
        else -> Pair(AccentAmber, task.status.name)
    }

    val timeFormatted = remember(task.createdAt) {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(task.createdAt))
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, if (isActive) AccentCyan else BorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Task Header: Icon, Title, Status, and Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category icon
                    Text(text = task.icon.ifBlank { task.category.defaultIcon }, fontSize = 16.sp)

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = timeFormatted,
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "·",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "${task.agents.size} agent(s) · ${task.totalStepsCount} step(s)",
                                fontSize = 10.sp,
                                color = TextMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Status Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = statusLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Expand / Collapse Chevron Button
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (task.isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                            contentDescription = if (task.isExpanded) "Collapse" else "Expand",
                            tint = AccentCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Command Prompt Box
            if (task.commandPrompt.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CardSubBg,
                    border = BorderStroke(0.5.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = ">",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                        Text(
                            text = task.commandPrompt,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF93C5FD),
                            maxLines = if (task.isExpanded) 4 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Summary Result (if available)
            if (!task.summaryResult.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AccentGreen.copy(alpha = 0.08f),
                    border = BorderStroke(0.5.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Result: ${task.summaryResult}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFA7F3D0),
                        modifier = Modifier.padding(8.dp),
                        maxLines = if (task.isExpanded) 8 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Expanded Level 2 & 3 Hierarchy (Agents, Steps, Tool Calls)
            AnimatedVisibility(visible = task.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "PARTICIPATING AGENTS & EXECUTION TREE:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )

                    task.agents.forEach { agent ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CardSubBg,
                            border = BorderStroke(0.5.dp, BorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onToggleAgentExpand(agent.id) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = if (agent.isExpanded) "▾" else "▸",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = AccentCyan
                                        )
                                        Text(
                                            text = agent.agentName,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentPurple
                                        )
                                        Text(
                                            text = "(${agent.role})",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Text(
                                        text = "${agent.steps.size} steps",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }

                                if (agent.isExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    agent.steps.forEach { step ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "•",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = AccentCyan
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = step.title,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp,
                                                    color = TextPrimary
                                                )
                                                if (!step.toolName.isNullOrBlank()) {
                                                    Text(
                                                        text = "Tool: ${step.toolName} ${step.toolArgs.orEmpty()}",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 9.sp,
                                                        color = Color(0xFFFDE047)
                                                    )
                                                }
                                                if (!step.content.isNullOrBlank()) {
                                                    Text(
                                                        text = step.content,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 9.sp,
                                                        color = TextMuted,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons: Continue in Terminal, Copy Transcript, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onContinueInTerminal,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Continue in Terminal",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onCopyTranscript,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Transcript",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Task",
                            tint = AccentRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chat Message History Item.
 */
@Composable
private fun ChatMessageHistoryItem(
    message: ConversationMessage,
    onSendToTerminal: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role.equals("user", ignoreCase = true)
    val bubbleColor = if (isUser) Color(0xFF1E293B) else Color(0xFF131A26)
    val borderColor = if (isUser) Color(0xFF334155) else AccentCyan.copy(alpha = 0.3f)
    val timeFormatted = remember(message.timestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(message.timestamp))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bubbleColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isUser) Color(0xFF38BDF8).copy(alpha = 0.2f) else AccentPurple.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (isUser) "USER" else "AI ASSISTANT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUser) AccentCyan else AccentPurple,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = timeFormatted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy message",
                            tint = TextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    IconButton(
                        onClick = onSendToTerminal,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Send to terminal",
                            tint = AccentCyan,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            SelectionContainer {
                Text(
                    text = message.text,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    fontFamily = if (message.text.startsWith("/")) FontFamily.Monospace else FontFamily.Default
                )
            }
        }
    }
}

/**
 * Helper to format a complete readable text transcript of a conversation task.
 */
private fun formatTaskTranscript(task: ConversationTaskNode): String {
    val sb = StringBuilder()
    sb.appendLine("=== CONVERSATION TRANSCRIPT ===")
    sb.appendLine("Title: ${task.title}")
    sb.appendLine("Category: ${task.category.label}")
    sb.appendLine("Status: ${task.status.name}")
    if (task.commandPrompt.isNotBlank()) {
        sb.appendLine("Prompt: ${task.commandPrompt}")
    }
    if (!task.summaryResult.isNullOrBlank()) {
        sb.appendLine("Result: ${task.summaryResult}")
    }
    sb.appendLine("Agents:")
    for (agent in task.agents) {
        sb.appendLine("  - Agent: ${agent.agentName} (${agent.role}) - Status: ${agent.status.name}")
        for (step in agent.steps) {
            sb.appendLine("      • Step: ${step.title}")
            if (!step.toolName.isNullOrBlank()) {
                sb.appendLine("        Tool: ${step.toolName} ${step.toolArgs.orEmpty()}")
            }
            if (!step.content.isNullOrBlank()) {
                sb.appendLine("        Output: ${step.content}")
            }
        }
    }
    sb.appendLine("===============================")
    return sb.toString()
}
