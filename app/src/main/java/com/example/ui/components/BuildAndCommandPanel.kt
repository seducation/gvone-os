package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.viewmodel.BrowserViewModel

/**
 * Advanced Build & Command Panel:
 * Expandable via the "Build" icon in Floating Address Bar & Terminal.
 * Displays:
 * 1. Target Directory & Project Scoping Chips
 * 2. Interactive Approval Gates (for critical file writes / destructive actions)
 * 3. Interactive AI Clarification & Question Cards
 * 4. Step-by-Step Agent Inspection & Live Execution Timeline
 */
@Composable
fun BuildAndCommandPanel(
    viewModel: BrowserViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val executionStatus by viewModel.terminalExecutionStatus.collectAsState()
    val statusMessage by viewModel.terminalStatusMessage.collectAsState()
    val activeSteps by viewModel.activeAgentSteps.collectAsState()
    val pendingApproval by viewModel.pendingApprovalStep.collectAsState()
    val pendingQuestion by viewModel.pendingQuestionPrompt.collectAsState()
    val currentTargetDir by viewModel.terminalTargetDirectory.collectAsState()
    val isAgenticMode by viewModel.terminalCommandExecutor.isAgenticMode.collectAsState()

    var workspaceDirectories by remember { mutableStateOf<List<String>>(listOf("workspace", "workspace/web", "src", "web", "Projects")) }
    LaunchedEffect(Unit) {
        workspaceDirectories = viewModel.getWorkspaceProjectsAndSubfolders()
    }

    var showNewTargetDialog by remember { mutableStateOf(false) }
    var newTargetInput by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("build_and_command_panel"),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = Color(0xF510141D),
        border = BorderStroke(1.dp, Color(0xFF26334A)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Build Icon, Title, Execution Status Pill, Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = "Build Panel",
                            tint = Color(0xFF090D14),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "BUILD & AGENT IDE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0),
                                letterSpacing = 0.5.sp
                            )

                            // Status Pill
                            ExecutionStatusPill(status = executionStatus, message = statusMessage)
                        }
                        Text(
                            text = "Workspace: /$currentTargetDir",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF))
                        .testTag("build_panel_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Build Panel",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E2838), thickness = 0.5.dp)

            // 1. TARGET PROJECT / DIRECTORY SELECTION BAR
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TARGET WORKSPACE DIRECTORY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF94A3B8)
                    )

                    Text(
                        text = "Scopes Terminal & AI File Generation",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    workspaceDirectories.forEach { dir ->
                        val isSelected = dir.equals(currentTargetDir, ignoreCase = true)
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    viewModel.setTerminalTargetDirectory(dir)
                                }
                                .testTag("target_dir_chip_$dir"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF0F2B48) else Color(0xFF161E2C),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF28364E)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "/$dir",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    // Add Custom Target Directory Chip
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showNewTargetDialog = true }
                            .testTag("add_custom_target_chip"),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF161E2C),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Custom Target",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "New Target",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }

                    // Run Preview Shortcut Chip
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { viewModel.runProjectPreview() }
                            .testTag("run_preview_target_chip"),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F3B26),
                        border = BorderStroke(1.dp, Color(0xFF34D399))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Run Preview",
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "Run Preview",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }
                }
            }

            // 2. INTERACTIVE APPROVAL GATE (Rendered prominently when critical action is pending)
            if (pendingApproval != null) {
                val step = pendingApproval!!
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF221708),
                    border = BorderStroke(1.5.dp, Color(0xFFFFB74D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("interactive_approval_gate_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFB74D))
                                )
                                Text(
                                    text = "APPROVAL GATE REQUIRED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFFFB74D),
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Text(
                                text = "Paused for User Review",
                                fontSize = 10.sp,
                                color = Color(0xFFFBBF24)
                            )
                        }

                        Text(
                            text = step.title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFFBEB)
                        )

                        Text(
                            text = step.description,
                            fontSize = 11.5.sp,
                            color = Color(0xFFD4D4D8)
                        )

                        // Diff Preview Box
                        if (!step.codeDiff.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F0F12),
                                border = BorderStroke(1.dp, Color(0xFF332A15)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = step.codeDiff,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF86EFAC),
                                    modifier = Modifier.padding(10.dp),
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Approve and Deny Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.approveCurrentStep() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 40.dp)
                                    .testTag("approval_gate_approve_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Approve",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Approve & Write",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.rejectCurrentStep() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                                border = BorderStroke(1.dp, Color(0xFFF87171)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(0.7f)
                                    .heightIn(min = 40.dp)
                                    .testTag("approval_gate_reject_button")
                            ) {
                                Text(
                                    text = "Deny / Skip",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 3. INTERACTIVE AI QUESTION / CLARIFICATION CARD
            if (pendingQuestion != null) {
                val q = pendingQuestion!!
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1730),
                    border = BorderStroke(1.2.dp, Color(0xFFB388FF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("interactive_ai_question_card")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QuestionAnswer,
                                contentDescription = null,
                                tint = Color(0xFFB388FF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "AI REQUIREMENT CLARIFICATION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFB388FF),
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = q.question,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFEDE9FE)
                        )

                        // Option Chips
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            q.options.forEachIndexed { index, option ->
                                val isRec = index == q.recommendedIndex
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.answerQuestionPrompt(option) }
                                        .testTag("question_option_chip_$index"),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isRec) Color(0xFF2E1C4E) else Color(0xFF140F24),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isRec) Color(0xFFB388FF) else Color(0xFF382956)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = option,
                                            fontSize = 12.sp,
                                            fontWeight = if (isRec) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isRec) Color(0xFFF3E8FF) else Color(0xFFD8B4FE)
                                        )

                                        if (isRec) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF8B5CF6)
                                            ) {
                                                Text(
                                                    text = "RECOMMENDED",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
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

            // 4. STEP PROGRESSION & BUG DETERMINATION TIMELINE
            if (activeSteps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "AGENT EXECUTION PIPELINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF94A3B8)
                        )

                        Text(
                            text = "${activeSteps.count { it.status == AgentStepStatus.COMPLETED }}/${activeSteps.size} Completed",
                            fontSize = 10.sp,
                            color = Color(0xFF4ADE80)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0C1017))
                            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeSteps.forEachIndexed { idx, s ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val (icon, tint) = when (s.status) {
                                    AgentStepStatus.COMPLETED -> Icons.Rounded.CheckCircle to Color(0xFF4ADE80)
                                    AgentStepStatus.RUNNING -> Icons.Rounded.Sync to Color(0xFF38BDF8)
                                    AgentStepStatus.AWAITING_APPROVAL -> Icons.Rounded.PauseCircle to Color(0xFFFFB74D)
                                    AgentStepStatus.FAILED -> Icons.Rounded.Cancel to Color(0xFFF87171)
                                    AgentStepStatus.PENDING -> Icons.Rounded.RadioButtonUnchecked to Color(0xFF64748B)
                                }

                                Icon(
                                    imageVector = icon,
                                    contentDescription = s.status.name,
                                    tint = tint,
                                    modifier = Modifier.size(14.dp)
                                )

                                Text(
                                    text = "${s.type.iconLabel} ${s.title}: ${s.description}",
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (s.status == AgentStepStatus.COMPLETED) Color(0xFFCBD5E1) else tint,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            // 5. FAST ACTION BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.triggerAgentMultiFileGeneration(
                            "create a responsive landing page .html with modern style.css and interactive app.js",
                            currentTargetDir
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("build_panel_scaffold_btn"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Scaffold Web",
                        fontSize = 11.5.sp,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Button(
                    onClick = { viewModel.runProjectPreview() },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("build_panel_launch_preview_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInBrowser,
                        contentDescription = null,
                        tint = Color(0xFF93C5FD),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Live Preview",
                        fontSize = 11.5.sp,
                        color = Color(0xFFBFDBFE),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Dialog for adding custom target directory
    if (showNewTargetDialog) {
        AlertDialog(
            onDismissRequest = { showNewTargetDialog = false },
            title = {
                Text(text = "Add Target Workspace Directory", color = Color(0xFFF1F5F9))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Enter directory name relative to workspace root (e.g. 'web', 'src/components', 'Projects/Demo'):",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = newTargetInput,
                        onValueChange = { newTargetInput = it },
                        placeholder = { Text("e.g. web/landing", color = Color(0xFF64748B)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = newTargetInput.trim().removePrefix("/")
                        if (sanitized.isNotBlank()) {
                            viewModel.setTerminalTargetDirectory(sanitized)
                            workspaceDirectories = (workspaceDirectories + sanitized).distinct()
                            showNewTargetDialog = false
                            newTargetInput = ""
                        }
                    }
                ) {
                    Text("Select & Set")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewTargetDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF151C28)
        )
    }
}

/**
 * Status pill showing active execution status (Idle, Running, Awaiting Approval, Error).
 */
@Composable
fun ExecutionStatusPill(
    status: TerminalExecutionStatus,
    message: String = "",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, borderColor, icon) = when (status) {
        TerminalExecutionStatus.IDLE -> listOf(Color(0xFF1E2838), Color(0xFF94A3B8), Color(0xFF334155), Icons.Rounded.CheckCircle)
        TerminalExecutionStatus.RUNNING -> listOf(Color(0xFF1E2A4A), Color(0xFF38BDF8), Color(0xFF38BDF8), Icons.Rounded.Sync)
        TerminalExecutionStatus.AWAITING_APPROVAL -> listOf(Color(0xFF3B250A), Color(0xFFFFB74D), Color(0xFFFFB74D), Icons.Rounded.PauseCircle)
        TerminalExecutionStatus.ERROR -> listOf(Color(0xFF3B1214), Color(0xFFF87171), Color(0xFFF87171), Icons.Rounded.Warning)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .testTag("terminal_execution_status_pill_${status.name.lowercase()}"),
        shape = RoundedCornerShape(10.dp),
        color = bgColor as Color,
        border = BorderStroke(1.dp, (borderColor as Color).copy(alpha = 0.8f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon as androidx.compose.ui.graphics.vector.ImageVector,
                contentDescription = status.displayName,
                tint = textColor as Color,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = if (status == TerminalExecutionStatus.AWAITING_APPROVAL) "APPROVAL REQ" else status.displayName.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
        }
    }
}
