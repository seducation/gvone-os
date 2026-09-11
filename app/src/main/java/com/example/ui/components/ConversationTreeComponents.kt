package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.example.agent.runtime.StepExecutionStatus
import com.example.data.terminal.*
import java.text.SimpleDateFormat
import java.util.*

private val TreeBgColor = Color(0xFF090C10)
private val CardBgLevel1 = Color(0xFF0D1117)
private val CardBgLevel2 = Color(0xFF131822)
private val CardBgLevel3 = Color(0xFF161B26)
private val BorderLevel1 = Color(0xFF21262D)
private val BorderLevel2 = Color(0xFF30363D)
private val BorderLevel3 = Color(0xFF2B3442)
private val TreeLineColor = Color(0xFF38444D)

private val ColorSuccess = Color(0xFF3FB950)
private val ColorCyan = Color(0xFF38BDF8)
private val ColorPurple = Color(0xFFA855F7)
private val ColorAmber = Color(0xFFEAB308)
private val ColorError = Color(0xFFF85149)
private val ColorTextMuted = Color(0xFF8B949E)
private val ColorTextPrimary = Color(0xFFE6EDF3)

/**
 * Full 3-Level Collapsible Conversation Tree for GVONE OS CLI.
 * Level 1: Conversation / Task (independently expandable/collapsible)
 * Level 2: Agent / Execution (independently expandable/collapsible)
 * Level 3: Steps / Events / Details (terminal-native inspection)
 */
@Composable
fun ConversationHierarchyTreeView(
    treeManager: ConversationTreeManager = ConversationTreeManager.global,
    onContinueTask: (ConversationTaskNode) -> Unit = {},
    onInspectContext: (ConversationTaskNode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val tasks by treeManager.tasks.collectAsState()
    val selectedFilter by treeManager.selectedFilter.collectAsState()
    val activeTaskId by treeManager.activeTaskId.collectAsState()

    val filteredTasks = remember(tasks, selectedFilter) {
        when (selectedFilter) {
            "ACTIVE" -> tasks.filter { it.status == AgentStatus.EXECUTING || it.status == AgentStatus.PLANNING }
            "COMPLETED" -> tasks.filter { it.status == AgentStatus.COMPLETED }
            else -> tasks
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TreeBgColor)
            .testTag("conversation_hierarchy_tree_view")
    ) {
        // 1. Toolbar & Controls Header
        Surface(
            color = CardBgLevel1,
            border = BorderStroke(0.5.dp, BorderLevel1),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(ColorPurple, CircleShape)
                        )
                        Text(
                            text = "CONVERSATION & TASK HIERARCHY",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextPrimary
                        )
                        Text(
                            text = "(${tasks.size} tasks)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ColorTextMuted
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Expand All button
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF21262D),
                            modifier = Modifier
                                .clickable { treeManager.expandAll() }
                                .testTag("tree_expand_all_btn")
                        ) {
                            Text(
                                text = "▾ EXPAND ALL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        // Collapse All button
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF21262D),
                            modifier = Modifier
                                .clickable { treeManager.collapseAll() }
                                .testTag("tree_collapse_all_btn")
                        ) {
                            Text(
                                text = "▸ COLLAPSE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorTextMuted,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Filter chips and context isolation note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("ALL", "ACTIVE", "COMPLETED").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) ColorPurple.copy(alpha = 0.25f) else Color(0xFF161B22),
                            border = BorderStroke(1.dp, if (isSelected) ColorPurple else BorderLevel1),
                            modifier = Modifier
                                .clickable { treeManager.setFilter(filter) }
                                .testTag("filter_chip_$filter")
                        ) {
                            Text(
                                text = filter,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFE9D5FF) else ColorTextMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Context Isolation Guarantee Pill
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0F2D1F),
                        border = BorderStroke(0.5.dp, ColorSuccess.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Context Isolated",
                                tint = ColorSuccess,
                                modifier = Modifier.size(9.dp)
                            )
                            Text(
                                text = "STRICT CONTEXT ISOLATION",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorSuccess
                            )
                        }
                    }
                }
            }
        }

        // 2. Main Tree List
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
                        tint = ColorTextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No tasks matching '$selectedFilter'",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = ColorTextMuted
                    )
                    Text(
                        text = "Run /agent <goal> or /voice to create an isolated conversation tree",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF6E7681)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTasks, key = { it.id }) { task ->
                    Level1TaskItem(
                        task = task,
                        isActive = task.id == activeTaskId,
                        onToggleExpand = { treeManager.toggleTaskExpansion(task.id) },
                        onToggleAgentExpand = { agentId -> treeManager.toggleAgentExpansion(task.id, agentId) },
                        onContinue = { onContinueTask(task) },
                        onInspect = { onInspectContext(task) },
                        onRemove = { treeManager.removeTask(task.id) }
                    )
                }
            }
        }
    }
}

/**
 * Level 1: Top-level Item representing one independent user task or conversation.
 * Example:
 * ▸ 🎵 YouTube — Search & Play Song
 * ▸ 🌐 News — Search Latest News
 * ▾ 💻 Coding — Fix Authentication Bug
 */
@Composable
fun Level1TaskItem(
    task: ConversationTaskNode,
    isActive: Boolean,
    onToggleExpand: () -> Unit,
    onToggleAgentExpand: (String) -> Unit,
    onContinue: () -> Unit,
    onInspect: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (statusColor, statusLabel) = when (task.status) {
        AgentStatus.COMPLETED -> Pair(ColorSuccess, "DONE")
        AgentStatus.EXECUTING -> Pair(ColorCyan, "RUNNING")
        AgentStatus.PLANNING -> Pair(ColorAmber, "PLANNING")
        AgentStatus.PAUSED -> Pair(ColorAmber, "PAUSED")
        AgentStatus.FAILED, AgentStatus.CANCELLED -> Pair(ColorError, "FAILED")
        else -> Pair(ColorAmber, task.status.name)
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardBgLevel1),
        border = BorderStroke(
            1.dp,
            if (isActive) ColorPurple else if (task.isExpanded) Color(0xFF38444D) else BorderLevel1
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("level1_task_${task.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Level 1 Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expand / Collapse Glyph: ▾ or ▸
                Text(
                    text = if (task.isExpanded) "▾" else "▸",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (task.isExpanded) ColorPurple else ColorTextMuted
                )

                // Category Icon
                Text(
                    text = task.icon,
                    fontSize = 15.sp
                )

                // Title
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = task.title,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Metadata Subtitle: Agent count, steps, memory scope
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "${task.activeAgentCount} agents • ${task.totalStepsCount} steps",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = ColorTextMuted
                        )

                        // Isolated Scope Tag
                        Text(
                            text = "🔒 scope: ${task.isolatedMemoryScopeId.take(8)}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = ColorCyan.copy(alpha = 0.8f)
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, statusColor.copy(alpha = 0.6f))
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
            }

            // Expanded Level 1 Body: Level 2 Agent List
            AnimatedVisibility(
                visible = task.isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070A0E))
                        .padding(start = 16.dp, end = 8.dp, bottom = 8.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Task Summary / Quick Action bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AGENTS INVOLVED (${task.agents.size})",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorTextMuted
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Copy Context Button
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF161B22),
                                border = BorderStroke(0.5.dp, BorderLevel2),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val export = buildString {
                                        appendLine("=== TASK: ${task.title} ===")
                                        appendLine("Scope: ${task.isolatedMemoryScopeId}")
                                        appendLine("Command: ${task.commandPrompt}")
                                        task.agents.forEach { ag ->
                                            appendLine("  [AGENT] ${ag.agentName} (${ag.status})")
                                            ag.steps.forEach { st ->
                                                appendLine("    - [${st.type}] ${st.title} ${st.content ?: ""}")
                                            }
                                        }
                                        if (task.summaryResult != null) {
                                            appendLine("Result: ${task.summaryResult}")
                                        }
                                    }
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Task Tree", export))
                                    Toast.makeText(context, "Task context copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "COPY CONTEXT",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    color = ColorCyan,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            // Dismiss Task
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF211515),
                                border = BorderStroke(0.5.dp, ColorError.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { onRemove() }
                            ) {
                                Text(
                                    text = "DISMISS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    color = ColorError,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (task.agents.isEmpty()) {
                        Text(
                            text = "   └── No agents dispatched for this task yet.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = ColorTextMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        task.agents.forEachIndexed { index, agent ->
                            val isLast = index == task.agents.size - 1
                            Level2AgentItem(
                                agent = agent,
                                isLastInList = isLast,
                                onToggleExpand = { onToggleAgentExpand(agent.id) }
                            )
                        }
                    }

                    // Optional summary result banner if present
                    if (!task.summaryResult.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF0F2618),
                            border = BorderStroke(0.5.dp, ColorSuccess.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "🎯", fontSize = 12.sp)
                                Column {
                                    Text(
                                        text = "TASK SYNTHESIS & OUTCOME",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorSuccess
                                    )
                                    Text(
                                        text = task.summaryResult,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFFD1E7DD),
                                        modifier = Modifier.padding(top = 2.dp)
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

/**
 * Level 2: Agent / Execution involved in the task.
 * Example:
 * ▾ 💻 Coding — Fix Authentication Bug
 *    ▾ 🤖 Coding Agent
 *    ▸ 🌐 Browser Agent
 *    ▸ 📁 File Agent
 */
@Composable
fun Level2AgentItem(
    agent: AgentExecutionNode,
    isLastInList: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val branchConnector = if (isLastInList) "└── " else "├── "

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("level2_agent_${agent.id}")
    ) {
        // Level 2 Header
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CardBgLevel2,
            border = BorderStroke(
                0.5.dp,
                if (agent.isExpanded) ColorPurple.copy(alpha = 0.6f) else BorderLevel2
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tree Branch Connector
                Text(
                    text = branchConnector,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TreeLineColor
                )

                // Glyph ▾ / ▸
                Text(
                    text = if (agent.isExpanded) "▾" else "▸",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (agent.isExpanded) ColorPurple else ColorTextMuted
                )

                // Agent Icon
                Text(
                    text = agent.icon,
                    fontSize = 13.sp
                )

                // Agent Name & Role
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = agent.agentName,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (agent.isExpanded) Color(0xFFF3E8FF) else ColorTextPrimary
                    )
                    Text(
                        text = agent.role,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = ColorTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Step count chip
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = Color(0xFF1E2530)
                ) {
                    Text(
                        text = "${agent.steps.size} steps",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = ColorCyan,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Level 3 Details: Steps / Events / Details
        AnimatedVisibility(
            visible = agent.isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (agent.steps.isEmpty()) {
                    Text(
                        text = "└── (No events recorded)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = ColorTextMuted,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                } else {
                    agent.steps.forEachIndexed { sIdx, step ->
                        val isLastStep = sIdx == agent.steps.size - 1
                        Level3DetailItem(
                            step = step,
                            isLastInAgent = isLastStep
                        )
                    }
                }
            }
        }
    }
}

/**
 * Level 3: Individual execution step, tool call, reasoning/status event, output, error, or result.
 * Example:
 *    ▾ 🤖 Coding Agent
 *       ✓ Analyze repository
 *       ✓ Locate authentication bug
 *       ⚡ Tool: grep -r "AuthToken"
 *       💬 Reasoning: Found token validation logic in AuthService.kt
 *       ✓ Patch auth token verification
 *       ✓ Run test suite
 *       ✔ Result: Fixed in 420ms
 */
@Composable
fun Level3DetailItem(
    step: ExecutionDetailNode,
    isLastInAgent: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }

    val branchGlyph = if (isLastInAgent) "└── " else "├── "

    val (badgeIcon, badgeColor, badgeLabel) = when (step.type) {
        DetailNodeType.STEP -> {
            when (step.status) {
                StepExecutionStatus.COMPLETED, StepExecutionStatus.VERIFIED -> Triple("✓", ColorSuccess, "STEP")
                StepExecutionStatus.RUNNING, StepExecutionStatus.OBSERVING -> Triple("⟳", ColorCyan, "RUNNING")
                StepExecutionStatus.FAILED -> Triple("✗", ColorError, "FAILED")
                else -> Triple("○", ColorTextMuted, "PENDING")
            }
        }
        DetailNodeType.TOOL_CALL -> Triple("⚡", ColorPurple, "TOOL")
        DetailNodeType.REASONING -> Triple("💬", ColorAmber, "THOUGHT")
        DetailNodeType.OBSERVATION -> Triple("👁️", ColorCyan, "OBSERVE")
        DetailNodeType.OUTPUT -> Triple("📄", ColorTextPrimary, "OUTPUT")
        DetailNodeType.ERROR -> Triple("✗", ColorError, "ERROR")
        DetailNodeType.RESULT -> Triple("✔", ColorSuccess, "RESULT")
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = CardBgLevel3,
        border = BorderStroke(0.5.dp, BorderLevel3),
        modifier = modifier
            .fillMaxWidth()
            .testTag("level3_detail_${step.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Branch connector
                Text(
                    text = branchGlyph,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = TreeLineColor
                )

                // Status / Type Icon (✓, ⚡, 💬, ✗, etc.)
                Text(
                    text = badgeIcon,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )

                // Tool / Type Tag if applicable
                if (step.type == DetailNodeType.TOOL_CALL && step.toolName != null) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = ColorPurple.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, ColorPurple)
                    ) {
                        Text(
                            text = "TOOL: ${step.toolName}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD8B4FE),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else if (step.type == DetailNodeType.REASONING) {
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = ColorAmber.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, ColorAmber)
                    ) {
                        Text(
                            text = "REASONING",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorAmber,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Title
                Text(
                    text = step.title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (step.type == DetailNodeType.RESULT) ColorSuccess else ColorTextPrimary,
                    fontWeight = if (step.type == DetailNodeType.RESULT) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Duration badge if recorded
                if (step.durationMs != null) {
                    Text(
                        text = "${step.durationMs}ms",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = ColorTextMuted
                    )
                }

                // Expand button if content exists
                if (!step.content.isNullOrBlank() || !step.toolArgs.isNullOrBlank()) {
                    Text(
                        text = if (isExpanded) "▲" else "▼",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = ColorTextMuted,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(2.dp)
                    )
                }
            }

            // Expandable Content / Arguments / Output
            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, start = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!step.toolArgs.isNullOrBlank()) {
                        SelectionContainer {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF0D1117),
                                border = BorderStroke(0.5.dp, Color(0xFF21262D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$ ${step.toolArgs}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = ColorCyan,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }

                    if (!step.content.isNullOrBlank()) {
                        SelectionContainer {
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFF090C10),
                                border = BorderStroke(0.5.dp, Color(0xFF21262D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = step.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = Color(0xFFB1BAC4),
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
