package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.core.AgentStatus
import com.example.agent.runtime.*
import com.example.agent.sandbox.AgentPersona
import com.example.data.sync.WebAppConnectionState

@Composable
fun RuntimeStatusPillRow(
    runtimeMode: RuntimeMode,
    activeTask: AgentTask?,
    onToggleVoice: () -> Unit,
    onToggleAgent: () -> Unit,
    onToggleDebug: () -> Unit,
    onCancelTask: () -> Unit,
    modifier: Modifier = Modifier,
    isAgenticMode: Boolean = false,
    activePersona: AgentPersona = AgentPersona.AUTO,
    sandboxTabCount: Int = 0,
    onFocusSandbox: () -> Unit = {},
    bridgeConnectionState: WebAppConnectionState = WebAppConnectionState.IDLE,
    onBridgeClick: () -> Unit = {},
    onCnsClick: () -> Unit = {}
) {
    Surface(
        color = Color(0xFF0D1117),
        border = BorderStroke(0.5.dp, Color(0xFF21262D)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Agent Mode / Persona Pill Toggle
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (isAgenticMode) Color(0xFF3B0764) else Color(0xFF161B22),
                border = BorderStroke(1.dp, if (isAgenticMode) Color(0xFFA855F7) else Color(0xFF30363D)),
                modifier = Modifier
                    .clickable { onToggleAgent() }
                    .testTag("terminal_header_agent_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (isAgenticMode) Color(0xFFA855F7) else Color(0xFF6B7280), CircleShape)
                    )
                    Text(
                        text = if (isAgenticMode) "AGENT: ON (${activePersona.badge})" else "AGENT: OFF",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAgenticMode) Color(0xFFE9D5FF) else Color(0xFF8B949E)
                    )
                }
            }

            // 2. Interaction Mode Pill (Voice / Text)
            val (interactionIcon, interactionLabel, interactionColor) = when {
                runtimeMode.isVoicePrimary -> Triple(Icons.Rounded.AutoAwesome, "VOICE ➜ AGENT", Color(0xFFA855F7))
                runtimeMode.isAgentPrimary -> Triple(Icons.Rounded.SmartToy, "AGENT ➜ VOICE", Color(0xFFC084FC))
                runtimeMode.interaction == InteractionType.VOICE -> Triple(Icons.Rounded.Mic, "VOICE", Color(0xFF38BDF8))
                else -> Triple(Icons.Rounded.Keyboard, "TEXT", Color(0xFF8B949E))
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = interactionColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, interactionColor.copy(alpha = 0.4f)),
                modifier = Modifier
                    .clickable { onToggleVoice() }
                    .testTag("interaction_mode_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = interactionIcon,
                        contentDescription = interactionLabel,
                        tint = interactionColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = interactionLabel,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = interactionColor
                    )
                }
            }

            // 3. Sandbox Tab Cohort Pill
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF064E3B),
                border = BorderStroke(1.dp, Color(0xFF10B981)),
                modifier = Modifier
                    .clickable { onFocusSandbox() }
                    .testTag("terminal_header_sandbox_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "📦 SANDBOX${if (sandboxTabCount > 0) " ($sandboxTabCount)" else ""}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6EE7B7)
                    )
                }
            }

            // 4. Bridge Connection Status Pill
            val bridgeBg = when (bridgeConnectionState) {
                WebAppConnectionState.READY -> Color(0xFF064E3B)
                WebAppConnectionState.CONNECTING, WebAppConnectionState.PROCESSING -> Color(0xFF1E3A8A)
                WebAppConnectionState.UNAVAILABLE -> Color(0xFF450A0A)
                else -> Color(0xFF1F2937)
            }
            val bridgeBorder = when (bridgeConnectionState) {
                WebAppConnectionState.READY -> Color(0xFF10B981)
                WebAppConnectionState.CONNECTING, WebAppConnectionState.PROCESSING -> Color(0xFF3B82F6)
                WebAppConnectionState.UNAVAILABLE -> Color(0xFFEF4444)
                else -> Color(0xFF4B5563)
            }
            val bridgeText = when (bridgeConnectionState) {
                WebAppConnectionState.READY -> "BRIDGE: OK"
                WebAppConnectionState.CONNECTING -> "BRIDGE: ..."
                WebAppConnectionState.PROCESSING -> "BRIDGE: BUSY"
                WebAppConnectionState.UNAVAILABLE -> "BRIDGE: OFF"
                else -> "BRIDGE: IDLE"
            }
            val bridgeTextColor = when (bridgeConnectionState) {
                WebAppConnectionState.READY -> Color(0xFF6EE7B7)
                WebAppConnectionState.CONNECTING, WebAppConnectionState.PROCESSING -> Color(0xFF93C5FD)
                WebAppConnectionState.UNAVAILABLE -> Color(0xFFFCA5A5)
                else -> Color(0xFFD1D5DB)
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = bridgeBg,
                border = BorderStroke(1.dp, bridgeBorder),
                modifier = Modifier
                    .clickable { onBridgeClick() }
                    .testTag("terminal_header_bridge_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(bridgeBorder, CircleShape)
                    )
                    Text(
                        text = bridgeText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = bridgeTextColor
                    )
                }
            }

            // 4.5 CNS Dashboard UI Button (placed directly next to Agent Sandbox Bridge)
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFF3B0764).copy(alpha = 0.6f),
                border = BorderStroke(1.dp, Color(0xFFA855F7)),
                modifier = Modifier
                    .clickable { onCnsClick() }
                    .testTag("terminal_header_cns_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFFA855F7), CircleShape)
                    )
                    Text(
                        text = "🧠 CNS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE9D5FF)
                    )
                }
            }

            // 5. Debug Mode Toggle Pill
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (runtimeMode.isDebugEnabled) Color(0xFFEAB308).copy(alpha = 0.2f) else Color(0xFF161B22),
                border = BorderStroke(1.dp, if (runtimeMode.isDebugEnabled) Color(0xFFEAB308) else Color(0xFF30363D)),
                modifier = Modifier
                    .clickable { onToggleDebug() }
                    .testTag("terminal_debug_badge")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (runtimeMode.isDebugEnabled) "🐞 DEBUG: ON" else "DEBUG: OFF",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (runtimeMode.isDebugEnabled) Color(0xFFEAB308) else Color(0xFF8B949E)
                    )
                }
            }

            // 6. Active Task Cancel Action Pill
            if (activeTask != null && (activeTask.status.isRunning || activeTask.status == AgentStatus.PLANNING || activeTask.status == AgentStatus.WAITING)) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFF85149).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFFF85149)),
                    modifier = Modifier
                        .clickable { onCancelTask() }
                        .testTag("task_cancel_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel Task",
                            tint = Color(0xFFF85149),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "CANCEL TASK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF85149)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandableAgentTaskCard(
    task: AgentTask,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCancelTask: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (task.status) {
        AgentStatus.IDLE -> Color(0xFF8B949E)
        AgentStatus.PLANNING -> Color(0xFFA855F7)
        AgentStatus.RUNNING, AgentStatus.EXECUTING -> Color(0xFF38BDF8)
        AgentStatus.OBSERVING -> Color(0xFF00E5FF)
        AgentStatus.RETRYING -> Color(0xFFFF9100)
        AgentStatus.PAUSED -> Color(0xFFF59E0B)
        AgentStatus.WAITING -> Color(0xFFFBBF24)
        AgentStatus.BLOCKED -> Color(0xFFFF5252)
        AgentStatus.COMPLETED -> Color(0xFF3FB950)
        AgentStatus.FAILED -> Color(0xFFF85149)
        AgentStatus.CANCELLED -> Color(0xFF8B949E)
    }

    Surface(
        color = Color(0xFF161B22),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag("expandable_task_card")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Header Row (Always visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )

                    Text(
                        text = "TASK: \"${task.goal.take(40)}\"",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE6EDF3),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = statusColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = task.status.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (task.status.isRunning || task.status == AgentStatus.PLANNING || task.status == AgentStatus.WAITING) {
                        IconButton(
                            onClick = onCancelTask,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Cancel",
                                tint = Color(0xFFF85149),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF8B949E),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Details
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = Color(0xFF21262D), thickness = 0.5.dp)

                    // Step Execution Timeline
                    if (task.steps.isNotEmpty()) {
                        Text(
                            text = "Execution Steps (${task.steps.size}):",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )

                        task.steps.forEach { step ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val (stepIcon, iconColor) = when (step.status) {
                                    StepExecutionStatus.COMPLETED, StepExecutionStatus.VERIFIED -> Icons.Rounded.CheckCircle to Color(0xFF3FB950)
                                    StepExecutionStatus.RUNNING, StepExecutionStatus.OBSERVING -> Icons.Rounded.Sync to Color(0xFF38BDF8)
                                    StepExecutionStatus.RETRYING -> Icons.Rounded.Refresh to Color(0xFFFB923C)
                                    StepExecutionStatus.FAILED -> Icons.Rounded.Cancel to Color(0xFFF85149)
                                    StepExecutionStatus.PENDING, StepExecutionStatus.SKIPPED -> Icons.Rounded.Schedule to Color(0xFF8B949E)
                                }

                                Icon(
                                    imageVector = stepIcon,
                                    contentDescription = step.status.name,
                                    tint = iconColor,
                                    modifier = Modifier.size(12.dp)
                                )

                                Text(
                                    text = "${step.stepNumber}. [${step.toolOrAgent}] ${step.description}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFC9D1D9)
                                )
                            }
                        }
                    }

                    // Observations
                    if (task.observations.isNotEmpty()) {
                        Text(
                            text = "Observations & Data:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFBBF24)
                        )
                        task.observations.takeLast(3).forEach { obs ->
                            Text(
                                text = "• $obs",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = Color(0xFF8B949E)
                            )
                        }
                    }

                    // Result if completed
                    task.result?.let { res ->
                        Text(
                            text = "Synthesis Result:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3FB950)
                        )
                        Text(
                            text = res.take(250),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = Color(0xFFE6EDF3)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}
