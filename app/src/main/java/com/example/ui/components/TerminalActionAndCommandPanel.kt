package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AgentQuestionPrompt
import com.example.data.model.AgentStepStatus
import com.example.data.model.AgentStepType
import com.example.data.model.AgentTaskStep

/**
 * Expandable Action & Command Panel activated by the "Build" icon in the Terminal.
 * Displays AI questions, improvement prompts, interactive approval gates,
 * step inspection, bug diagnosis, and quick multi-file IDE triggers.
 */
@Composable
fun TerminalActionAndCommandPanel(
    targetDirectory: String,
    pendingQuestion: AgentQuestionPrompt?,
    steps: List<AgentTaskStep>,
    onClose: () -> Unit,
    onSubmitAnswer: (String) -> Unit,
    onApproveStep: (AgentTaskStep) -> Unit,
    onRejectStep: (AgentTaskStep) -> Unit,
    onExecuteCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var writeInText by remember { mutableStateOf("") }
    var selectedOptionIndex by remember { mutableStateOf(pendingQuestion?.recommendedIndex ?: 0) }

    Surface(
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        shadowElevation = 12.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("terminal_action_command_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Build Icon, Title, Target Directory, Close Button
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
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0284C7).copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Build,
                            contentDescription = "Build Action Panel",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "ACTION & COMMAND PANEL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF1F5F9),
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "Target: $targetDirectory",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF38BDF8)
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("action_panel_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Action Panel",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

            // 1. AI QUESTIONS & CLARIFICATION / IMPROVEMENT SECTION
            val activePrompt = pendingQuestion ?: AgentQuestionPrompt(
                question = "What would you like the AI Agent to build in $targetDirectory?",
                options = listOf(
                    "(Recommended) Create modern responsive .html landing page",
                    "Scaffold multi-file project with HTML, CSS & JavaScript",
                    "Create Kotlin Calculator component",
                    "Inspect codebase and determine bugs"
                ),
                recommendedIndex = 0
            )

            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AI QUERY & QUESTIONS",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFFA855F7)
                        )
                    }

                    Text(
                        text = activePrompt.question,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF8FAFC)
                    )

                    // Options list
                    activePrompt.options.forEachIndexed { index, option ->
                        val isSelected = selectedOptionIndex == index
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF0369A1).copy(alpha = 0.35f) else Color(0xFF0F172A),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOptionIndex = index }
                                .testTag("action_panel_option_$index")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFF38BDF8) else Color(0xFF475569)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                }

                                Text(
                                    text = option,
                                    fontSize = 12.sp,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Write-in answer field
                    OutlinedTextField(
                        value = writeInText,
                        onValueChange = { writeInText = it },
                        placeholder = {
                            Text("Or type custom instructions / answer...", fontSize = 12.sp, color = Color(0xFF64748B))
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = Color(0xFFF1F5F9)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("action_panel_writein_field"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            val answer = writeInText.trim().ifEmpty {
                                activePrompt.options.getOrNull(selectedOptionIndex) ?: ""
                            }
                            if (answer.isNotBlank()) {
                                onSubmitAnswer(answer)
                                writeInText = ""
                            }
                        })
                    )

                    // Submit Answer button
                    Button(
                        onClick = {
                            val answer = writeInText.trim().ifEmpty {
                                activePrompt.options.getOrNull(selectedOptionIndex) ?: ""
                            }
                            if (answer.isNotBlank()) {
                                onSubmitAnswer(answer)
                                writeInText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("action_panel_submit_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Submit to AI Agent",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 2. APPROVAL GATES & STEP INSPECTION SECTION
            Surface(
                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "APPROVAL GATES & BUG DETERMINATION",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF34D399)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF065F46).copy(alpha = 0.5f),
                            border = BorderStroke(0.5.dp, Color(0xFF34D399))
                        ) {
                            Text(
                                text = "LIVE SAFEGUARD",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Display active steps or default inspection item
                    val activeSteps = if (steps.isNotEmpty()) steps else listOf(
                        AgentTaskStep(
                            title = "Multi-file Generation & Validation",
                            description = "Generate index.html and style in $targetDirectory with bug check",
                            type = AgentStepType.CREATE_FILE,
                            status = AgentStepStatus.AWAITING_APPROVAL,
                            targetFile = "$targetDirectory/index.html",
                            approvalRequired = true
                        )
                    )

                    activeSteps.forEach { step ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = step.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE2E8F0)
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (step.status == AgentStepStatus.AWAITING_APPROVAL) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF10B981).copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = step.status.displayName,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (step.status == AgentStepStatus.AWAITING_APPROVAL) Color(0xFFFBBF24) else Color(0xFF34D399),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = step.description,
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF94A3B8)
                                )

                                step.targetFile?.let { file ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Target File: $file",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.5.sp,
                                            color = Color(0xFF38BDF8)
                                        )
                                    }
                                }

                                // Interactive Approval Gate Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onApproveStep(step) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .testTag("action_panel_approve_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Approve", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            onExecuteCommand("debug bugcheck $targetDirectory")
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .testTag("action_panel_bugcheck_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.BugReport,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Bug Check", fontSize = 11.5.sp, color = Color(0xFFF59E0B))
                                    }

                                    IconButton(
                                        onClick = { onRejectStep(step) },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Reject",
                                            tint = Color(0xFFEF4444),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. QUICK MULTI-FILE IDE SHORTCUTS
            Text(
                text = "MULTI-FILE CREATION & ACTIONS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF94A3B8)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Shortcut 1: Create .html
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                    modifier = Modifier
                        .clickable {
                            onExecuteCommand("run website $targetDirectory Modern Interactive App")
                        }
                        .testTag("action_panel_create_html")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Create .html Website", fontSize = 11.sp, color = Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold)
                    }
                }

                // Shortcut 2: Run in Live Tab
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFF34D399)),
                    modifier = Modifier
                        .clickable {
                            onExecuteCommand("run")
                        }
                        .testTag("action_panel_run_live")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Run in Live Tab", fontSize = 11.sp, color = Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold)
                    }
                }

                // Shortcut 3: Create Kotlin File
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFFA855F7)),
                    modifier = Modifier
                        .clickable {
                            onExecuteCommand("touch $targetDirectory/Main.kt")
                        }
                        .testTag("action_panel_create_kt")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Code,
                            contentDescription = null,
                            tint = Color(0xFFA855F7),
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Create .kt File", fontSize = 11.sp, color = Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold)
                    }
                }

                // Shortcut 4: Step Inspection
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier
                        .clickable {
                            onExecuteCommand("/status")
                        }
                        .testTag("action_panel_step_inspection")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timeline,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text("Live Step Inspection", fontSize = 11.sp, color = Color(0xFFF1F5F9), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
