package com.example.ui.components

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.rules.*
import com.example.data.terminal.ConversationTaskNode
import com.example.data.terminal.ConversationTreeManager

private val CnsCardBg = Color(0xFF0D1117)
private val CnsCardSub = Color(0xFF161B22)
private val CnsBorderColor = Color(0xFF21262D)
private val CnsBorderLight = Color(0xFF30363D)
private val CnsPurple = Color(0xFFA855F7)
private val CnsCyan = Color(0xFF38BDF8)
private val CnsGreen = Color(0xFF3FB950)
private val CnsAmber = Color(0xFFEAB308)
private val CnsRed = Color(0xFFF85149)
private val CnsTextPrimary = Color(0xFFE6EDF3)
private val CnsTextMuted = Color(0xFF8B949E)
private val GemGradient = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF38BDF8)))

enum class RuleDashboardViewMode(val label: String, val icon: String) {
    GEMS("Gemini Gems", "💎"),
    GLOBAL("Global Scope", "🌐"),
    CHAT_SPECIFIC("Chat Specific", "💬"),
    ALL_RULES("All Rules Table", "⚡")
}

@Composable
fun RuleEngineTab(
    ruleEngine: RuleEngine = RuleEngine.global,
    conversationTreeManager: ConversationTreeManager = ConversationTreeManager.global,
    modifier: Modifier = Modifier
) {
    val rules by ruleEngine.rules.collectAsStateWithLifecycle()
    val traces by ruleEngine.tracesFlow.collectAsStateWithLifecycle()
    val chatTasks by conversationTreeManager.tasks.collectAsStateWithLifecycle()

    var viewMode by remember { mutableStateOf(RuleDashboardViewMode.GEMS) }
    var selectedChatFilterId by remember { mutableStateOf<String?>("ALL") }
    var selectedEventFilter by remember { mutableStateOf("ALL") }

    var showSimulator by remember { mutableStateOf(false) }
    var showTraces by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogPresetIsGem by remember { mutableStateOf(true) }

    // Promotion notice banner message (when user taps "Save as Global / Appear in all chats")
    var promotionNotice by remember { mutableStateOf<String?>(null) }

    // Simulator states
    var simGoal by remember { mutableStateOf("refactor kotlin coroutines for clean architecture") }
    var simChatContextId by remember { mutableStateOf<String?>("GLOBAL") }
    var simResultTrace by remember { mutableStateOf<RuleEvaluationTrace?>(null) }

    val globalCount = remember(rules) { rules.count { it.scope.equals("GLOBAL", ignoreCase = true) } }
    val chatScopedCount = remember(rules) { rules.count { it.scope.equals("CHAT", ignoreCase = true) } }
    val gemCount = remember(rules) { rules.count { it.isGem } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 4.dp)
            .testTag("rule_engine_dashboard")
    ) {
        // 1. Dashboard Sovereign Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "RULE ENGINE & GEMINI GEMS",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsPurple
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CnsPurple.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, CnsPurple)
                    ) {
                        Text(
                            text = "v2.0 ORCHESTRATOR",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF3E8FF),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = "$gemCount Gems • $globalCount Global • $chatScopedCount Chat-Specific (${rules.size} total rules)",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = CnsTextMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Dry-Run Simulator toggle
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (showSimulator) CnsCyan.copy(alpha = 0.2f) else CnsCardSub,
                    border = BorderStroke(1.dp, if (showSimulator) CnsCyan else CnsBorderColor),
                    modifier = Modifier
                        .clickable {
                            showSimulator = !showSimulator
                            if (showSimulator) showTraces = false
                        }
                        .testTag("rule_simulator_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Dry Run",
                            tint = CnsCyan,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Dry-Run",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = CnsCyan
                        )
                    }
                }

                // Traces toggle
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (showTraces) CnsGreen.copy(alpha = 0.2f) else CnsCardSub,
                    border = BorderStroke(1.dp, if (showTraces) CnsGreen else CnsBorderColor),
                    modifier = Modifier
                        .clickable {
                            showTraces = !showTraces
                            if (showTraces) showSimulator = false
                        }
                        .testTag("rule_traces_toggle")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.List,
                            contentDescription = "Traces",
                            tint = CnsGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Traces (${traces.size})",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = CnsGreen
                        )
                    }
                }

                // Add Gem Button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CnsPurple.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, CnsPurple),
                    modifier = Modifier
                        .clickable {
                            addDialogPresetIsGem = true
                            showAddDialog = true
                        }
                        .testTag("add_gem_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = "💎", fontSize = 10.sp)
                        Text(
                            text = "+ Gem",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF3E8FF)
                        )
                    }
                }

                // Add Rule Button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CnsCardSub,
                    border = BorderStroke(1.dp, CnsBorderLight),
                    modifier = Modifier
                        .clickable {
                            addDialogPresetIsGem = false
                            showAddDialog = true
                        }
                        .testTag("add_rule_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(text = "⚖️", fontSize = 9.sp)
                        Text(
                            text = "+ Rule",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = CnsTextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Promotion Notification Toast Banner (Triggered when user promotes chat-specific to global)
        AnimatedVisibility(
            visible = promotionNotice != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF064E3B),
                border = BorderStroke(1.dp, CnsGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = "✨", fontSize = 12.sp)
                        Text(
                            text = promotionNotice ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD1FAE5)
                        )
                    }
                    IconButton(
                        onClick = { promotionNotice = null },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss notice",
                            tint = Color(0xFFD1FAE5),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // 2. Primary Scope Navigation Bar (Gems, Global, Chat-Specific, All Rules)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RuleDashboardViewMode.values().forEach { mode ->
                val isSelected = viewMode == mode
                val count = when (mode) {
                    RuleDashboardViewMode.GEMS -> gemCount
                    RuleDashboardViewMode.GLOBAL -> globalCount
                    RuleDashboardViewMode.CHAT_SPECIFIC -> chatScopedCount
                    RuleDashboardViewMode.ALL_RULES -> rules.size
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) CnsPurple.copy(alpha = 0.25f) else CnsCardBg,
                    border = BorderStroke(1.dp, if (isSelected) CnsPurple else CnsBorderColor),
                    modifier = Modifier
                        .clickable { viewMode = mode }
                        .testTag("rule_tab_${mode.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = mode.icon, fontSize = 11.sp)
                        Text(
                            text = mode.label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color(0xFFF3E8FF) else CnsTextMuted
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) CnsPurple else CnsBorderLight
                        ) {
                            Text(
                                text = count.toString(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Dynamic Chat Selector Bar (Active when in CHAT_SPECIFIC or GEMS mode)
        if (viewMode == RuleDashboardViewMode.CHAT_SPECIFIC || viewMode == RuleDashboardViewMode.GEMS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "CHAT FILTER:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CnsAmber
                )

                // "All Chats" filter chip
                val isAllSelected = selectedChatFilterId == "ALL"
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (isAllSelected) CnsAmber.copy(alpha = 0.25f) else CnsCardSub,
                    border = BorderStroke(0.5.dp, if (isAllSelected) CnsAmber else CnsBorderColor),
                    modifier = Modifier.clickable { selectedChatFilterId = "ALL" }
                ) {
                    Text(
                        text = "All Scopes ($chatScopedCount)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isAllSelected) CnsAmber else CnsTextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                // Chips for each active chat/task in system
                chatTasks.forEach { task ->
                    val isTaskSelected = selectedChatFilterId == task.id
                    val taskRulesCount = rules.count { it.chatId == task.id }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isTaskSelected) CnsCyan.copy(alpha = 0.25f) else CnsCardSub,
                        border = BorderStroke(0.5.dp, if (isTaskSelected) CnsCyan else CnsBorderColor),
                        modifier = Modifier.clickable { selectedChatFilterId = task.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(text = task.icon, fontSize = 9.sp)
                            Text(
                                text = task.title.take(20),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = if (isTaskSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isTaskSelected) CnsCyan else CnsTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (taskRulesCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isTaskSelected) CnsCyan else CnsBorderLight
                                ) {
                                    Text(
                                        text = taskRulesCount.toString(),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        color = Color.Black,
                                        modifier = Modifier.padding(horizontal = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 4. Dry-Run Simulator View (Collapsible)
        AnimatedVisibility(visible = showSimulator) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F151C),
                border = BorderStroke(1.dp, CnsCyan.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = "⚡", fontSize = 12.sp)
                            Text(
                                text = "GEM & RULE DRY-RUN SIMULATOR",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = CnsCyan
                            )
                        }
                        IconButton(
                            onClick = { showSimulator = false },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = CnsTextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Chat context picker for dry-run
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Context:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = CnsTextMuted
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val isGlobalSim = simChatContextId == "GLOBAL"
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (isGlobalSim) CnsPurple.copy(alpha = 0.25f) else CnsCardBg,
                                border = BorderStroke(0.5.dp, if (isGlobalSim) CnsPurple else CnsBorderColor),
                                modifier = Modifier.clickable { simChatContextId = "GLOBAL" }
                            ) {
                                Text(
                                    text = "🌐 Global Context",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    color = if (isGlobalSim) Color(0xFFF3E8FF) else CnsTextMuted,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }

                            chatTasks.forEach { task ->
                                val isTaskSim = simChatContextId == task.id
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (isTaskSim) CnsCyan.copy(alpha = 0.25f) else CnsCardBg,
                                    border = BorderStroke(0.5.dp, if (isTaskSim) CnsCyan else CnsBorderColor),
                                    modifier = Modifier.clickable { simChatContextId = task.id }
                                ) {
                                    Text(
                                        text = "${task.icon} ${task.title.take(15)}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.5.sp,
                                        color = if (isTaskSim) CnsCyan else CnsTextMuted,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = simGoal,
                        onValueChange = { simGoal = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sim_goal_input"),
                        label = { Text("Simulation Goal / Instruction", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsTextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CnsCyan,
                            unfocusedBorderColor = CnsBorderLight,
                            focusedContainerColor = CnsCardBg,
                            unfocusedContainerColor = CnsCardBg
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val isCmd = simGoal.trim().startsWith("/")
                            val firstToken = simGoal.trim().split("\\s+".toRegex()).firstOrNull() ?: ""
                            val testEvent = if (isCmd) RuleEvents.COMMAND_PARSED else RuleEvents.REQUEST_RECEIVED
                            val targetChatId = if (simChatContextId != "GLOBAL") simChatContextId else null

                            val ctx = RuleContext(
                                event = testEvent,
                                chatId = targetChatId,
                                request = RuleRequestContext(
                                    goal = simGoal,
                                    command = if (isCmd) firstToken else "",
                                    queryArg = simGoal.removePrefix(firstToken).trim(),
                                    rawInput = simGoal,
                                    caller = "Simulation",
                                    chatId = targetChatId,
                                    taskId = targetChatId
                                ),
                                availableAgents = listOf("BrowserAgent", "FileAgent", "CodingAgent", "WebReviewAgent", "VoiceAgent", "CommandAgent", "WorkspaceAgent")
                            )
                            simResultTrace = ruleEngine.evaluate(ctx)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CnsCyan),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .testTag("run_simulation_btn")
                    ) {
                        Text(
                            text = "Evaluate Match & Scope Resolution",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    // Simulation Trace Result
                    if (simResultTrace != null) {
                        val trace = simResultTrace!!
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CnsCardBg,
                            border = BorderStroke(0.5.dp, CnsBorderLight),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Evaluated ${trace.totalRulesEvaluated} candidate rules (${trace.durationMs}ms)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = CnsTextMuted
                                    )
                                    Text(
                                        text = "${trace.matchedRules.size} matches",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (trace.matchedRules.isNotEmpty()) CnsGreen else CnsAmber
                                    )
                                }

                                val selected = trace.selectedRule
                                if (selected != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(text = if (selected.isGem) selected.gemIcon else "⚖️", fontSize = 11.sp)
                                        Text(
                                            text = "WINNER: [${selected.id}] ${selected.name}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CnsGreen
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(3.dp),
                                            color = if (selected.scope == "GLOBAL") CnsPurple.copy(alpha = 0.3f) else CnsAmber.copy(alpha = 0.3f)
                                        ) {
                                            Text(
                                                text = selected.scope,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (selected.scope == "GLOBAL") Color(0xFFF3E8FF) else CnsAmber,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Resolution: ${trace.conflictResolutionReason}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.5.sp,
                                        color = CnsCyan
                                    )
                                    Text(
                                        text = "Actions: ${selected.actions.joinToString { "${it.type}->${it.target ?: "default"}" }}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.5.sp,
                                        color = Color(0xFFF3E8FF)
                                    )
                                } else {
                                    Text(
                                        text = "No rule matched conditions for this context.",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        color = CnsAmber
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Execution Traces View (Collapsible)
        AnimatedVisibility(visible = showTraces) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F151C),
                border = BorderStroke(1.dp, CnsGreen.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "LIVE AUDIT TRACES & DETERMINISTIC LOGS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CnsGreen
                        )
                        IconButton(
                            onClick = { showTraces = false },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = CnsTextMuted,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    if (traces.isEmpty()) {
                        Text(
                            text = "No execution traces recorded yet. Run a command or dry-run to generate trace events.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            color = CnsTextMuted,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(traces) { trace ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CnsCardBg,
                                    border = BorderStroke(0.5.dp, CnsBorderLight),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = trace.event,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CnsPurple
                                            )
                                            Text(
                                                text = "${trace.durationMs}ms",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 7.5.sp,
                                                color = CnsTextMuted
                                            )
                                        }
                                        Text(
                                            text = "Selected: ${trace.selectedRule?.name ?: "None"} (${trace.conflictResolutionReason})",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 7.5.sp,
                                            color = if (trace.selectedRule != null) CnsGreen else CnsAmber
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Content Section (Filtered according to viewMode & chat filter)
        val displayedRules = remember(rules, viewMode, selectedChatFilterId, selectedEventFilter) {
            when (viewMode) {
                RuleDashboardViewMode.GEMS -> {
                    val gems = rules.filter { it.isGem }
                    if (selectedChatFilterId == "ALL") gems
                    else gems.filter { it.scope == "GLOBAL" || it.chatId == selectedChatFilterId }
                }
                RuleDashboardViewMode.GLOBAL -> {
                    rules.filter { it.scope.equals("GLOBAL", ignoreCase = true) }
                }
                RuleDashboardViewMode.CHAT_SPECIFIC -> {
                    val chatRules = rules.filter { it.scope.equals("CHAT", ignoreCase = true) }
                    if (selectedChatFilterId == "ALL") chatRules
                    else chatRules.filter { it.chatId == selectedChatFilterId }
                }
                RuleDashboardViewMode.ALL_RULES -> {
                    if (selectedEventFilter == "ALL") rules
                    else rules.filter { it.trigger.event == selectedEventFilter }
                }
            }
        }

        if (displayedRules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (viewMode == RuleDashboardViewMode.GEMS) "💎 No Gemini Gems in this view"
                        else if (viewMode == RuleDashboardViewMode.CHAT_SPECIFIC) "💬 No chat-specific rules yet"
                        else "No rules found",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsTextPrimary
                    )
                    Text(
                        text = if (viewMode == RuleDashboardViewMode.CHAT_SPECIFIC)
                            "Add a rule or gem for a specific chat, then click 'Save to All Chats' to promote it!"
                        else "Create custom gems or declarative rules with the buttons above.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        color = CnsTextMuted
                    )
                    Button(
                        onClick = {
                            addDialogPresetIsGem = (viewMode == RuleDashboardViewMode.GEMS)
                            showAddDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CnsPurple),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (viewMode == RuleDashboardViewMode.GEMS) "+ Create Gemini Gem" else "+ Add New Rule",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(displayedRules, key = { it.id }) { rule ->
                    if (rule.isGem) {
                        GeminiGemCard(
                            gem = rule,
                            chatTitle = chatTasks.firstOrNull { it.id == rule.chatId }?.title,
                            onToggle = { isEnabled -> ruleEngine.setRuleEnabled(rule.id, isEnabled) },
                            onPromoteToGlobal = {
                                val promoted = ruleEngine.promoteToGlobal(rule.id)
                                if (promoted != null) {
                                    promotionNotice = "✨ Gem '${promoted.name}' saved as Global! Now active across all chats."
                                }
                            },
                            onTestDryRun = {
                                simGoal = rule.description.ifBlank { rule.name }
                                simChatContextId = rule.chatId ?: "GLOBAL"
                                showSimulator = true
                            },
                            onDelete = { ruleEngine.unregisterRule(rule.id) }
                        )
                    } else {
                        RuleRowCard(
                            rule = rule,
                            chatTitle = chatTasks.firstOrNull { it.id == rule.chatId }?.title,
                            onToggle = { isEnabled -> ruleEngine.setRuleEnabled(rule.id, isEnabled) },
                            onPromoteToGlobal = {
                                val promoted = ruleEngine.promoteToGlobal(rule.id)
                                if (promoted != null) {
                                    promotionNotice = "✨ Rule '${promoted.name}' saved as Global! Now active across all chats."
                                }
                            },
                            onDelete = { ruleEngine.unregisterRule(rule.id) }
                        )
                    }
                }
            }
        }
    }

    // 7. Add Gem / Rule Dialog
    if (showAddDialog) {
        AddGeminiGemDialog(
            presetIsGem = addDialogPresetIsGem,
            activeChats = chatTasks,
            onDismiss = { showAddDialog = false },
            onAdd = { newRule, saveImmediatelyToGlobal ->
                val finalRule = if (saveImmediatelyToGlobal) {
                    newRule.copy(scope = "GLOBAL", chatId = null)
                } else {
                    newRule
                }
                ruleEngine.registerRule(finalRule)
                showAddDialog = false
                if (saveImmediatelyToGlobal) {
                    promotionNotice = "✨ '${finalRule.name}' saved directly to Global! Now active in all chats."
                } else if (finalRule.scope == "CHAT") {
                    promotionNotice = "💬 '${finalRule.name}' scoped to chat. Tap 'Save to All Chats' anytime to promote."
                }
            }
        )
    }
}

/**
 * Gemini Gem Card: Designed like Google Gemini Gems for custom persona, custom system instructions,
 * target agent, scope pill, one-click global promotion, and toggle.
 */
@Composable
fun GeminiGemCard(
    gem: RuleDefinition,
    chatTitle: String?,
    onToggle: (Boolean) -> Unit,
    onPromoteToGlobal: () -> Unit,
    onTestDryRun: () -> Unit,
    onDelete: () -> Unit
) {
    val isChatScoped = gem.scope.equals("CHAT", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CnsCardBg,
        border = BorderStroke(
            1.dp,
            if (gem.enabled) (if (isChatScoped) CnsAmber.copy(alpha = 0.6f) else CnsPurple.copy(alpha = 0.6f))
            else CnsBorderColor.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("gem_card_${gem.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: Gem Icon Avatar, Name, Scope Badge, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Gem Avatar with gradient ring
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isChatScoped) Brush.linearGradient(listOf(Color(0xFF78350F), Color(0xFF1E1B4B)))
                                else GemGradient
                            )
                            .border(1.dp, if (isChatScoped) CnsAmber else CnsCyan, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = gem.gemIcon, fontSize = 16.sp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = gem.name,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gem.enabled) CnsTextPrimary else CnsTextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Scope Badge
                            if (isChatScoped) {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = CnsAmber.copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, CnsAmber)
                                ) {
                                    Text(
                                        text = "💬 CHAT",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CnsAmber,
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(3.dp),
                                    color = CnsPurple.copy(alpha = 0.25f),
                                    border = BorderStroke(0.5.dp, CnsPurple)
                                ) {
                                    Text(
                                        text = "🌐 GLOBAL",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF3E8FF),
                                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (isChatScoped) "Bound to: ${chatTitle ?: gem.chatId ?: "Active Chat"}"
                            else "Available across all chats & autonomous agents",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            color = if (isChatScoped) CnsAmber.copy(alpha = 0.8f) else CnsCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Switch(
                        checked = gem.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CnsGreen,
                            checkedTrackColor = CnsGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor = CnsTextMuted,
                            uncheckedTrackColor = CnsCardSub
                        ),
                        modifier = Modifier.height(22.dp)
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete Gem",
                            tint = CnsTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Description
            if (gem.description.isNotBlank()) {
                Text(
                    text = gem.description,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = CnsTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Custom Instructions (The Gem System Persona)
            if (gem.gemInstructions.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF131B26),
                    border = BorderStroke(0.5.dp, Color(0xFF1F2E40)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "“", fontSize = 11.sp, color = CnsCyan, fontWeight = FontWeight.Bold)
                        Text(
                            text = gem.gemInstructions,
                            fontFamily = FontFamily.Monospace,
                            fontStyle = FontStyle.Italic,
                            fontSize = 7.5.sp,
                            color = Color(0xFFBAE6FD),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Footer / Metadata / Promotion Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Target Agent Pill
                val targetAgent = gem.actions.firstOrNull { it.type == RuleActionTypes.ROUTE_AGENT }?.target ?: "AutoAgent"
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = CnsCardSub,
                    border = BorderStroke(0.5.dp, CnsBorderLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = "🤖", fontSize = 8.sp)
                        Text(
                            text = targetAgent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // ONE-TIME "SAVE TO ALL CHATS" BUTTON FOR CHAT-SPECIFIC GEMS
                    if (isChatScoped) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF4C1D95),
                            border = BorderStroke(1.dp, CnsPurple),
                            modifier = Modifier
                                .clickable { onPromoteToGlobal() }
                                .testTag("promote_gem_${gem.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(text = "🌐", fontSize = 8.sp)
                                Text(
                                    text = "Save to All Chats",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF3E8FF)
                                )
                            }
                        }
                    }

                    // Dry-run test button
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CnsCardSub,
                        border = BorderStroke(0.5.dp, CnsCyan.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onTestDryRun() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Test Gem",
                                tint = CnsCyan,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "Test",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                color = CnsCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard Declarative Rule Row Card with Scope Tag & Global Promotion button
 */
@Composable
fun RuleRowCard(
    rule: RuleDefinition,
    chatTitle: String?,
    onToggle: (Boolean) -> Unit,
    onPromoteToGlobal: () -> Unit,
    onDelete: () -> Unit
) {
    val isChatScoped = rule.scope.equals("CHAT", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CnsCardBg,
        border = BorderStroke(
            1.dp,
            if (rule.enabled) CnsBorderColor else CnsBorderColor.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rule_card_${rule.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header: Priority, Event Tag, ID, Scope, Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Priority Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            rule.priority >= 1000 -> CnsRed.copy(alpha = 0.2f)
                            rule.priority >= 800 -> CnsAmber.copy(alpha = 0.2f)
                            else -> CnsPurple.copy(alpha = 0.2f)
                        },
                        border = BorderStroke(
                            0.5.dp,
                            when {
                                rule.priority >= 1000 -> CnsRed
                                rule.priority >= 800 -> CnsAmber
                                else -> CnsPurple
                            }
                        )
                    ) {
                        Text(
                            text = "P:${rule.priority}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                rule.priority >= 1000 -> CnsRed
                                rule.priority >= 800 -> CnsAmber
                                else -> Color(0xFFF3E8FF)
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Scope Tag
                    if (isChatScoped) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = CnsAmber.copy(alpha = 0.2f),
                            border = BorderStroke(0.5.dp, CnsAmber)
                        ) {
                            Text(
                                text = "💬 CHAT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = CnsAmber,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = CnsCyan.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, CnsCyan.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🌐 GLOBAL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                color = CnsCyan,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Rule Name
                    Text(
                        text = rule.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rule.enabled) CnsTextPrimary else CnsTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CnsGreen,
                            checkedTrackColor = CnsGreen.copy(alpha = 0.3f),
                            uncheckedThumbColor = CnsTextMuted,
                            uncheckedTrackColor = CnsCardSub
                        ),
                        modifier = Modifier.height(22.dp)
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete",
                            tint = CnsTextMuted,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Description / Chat binding info
            Text(
                text = if (isChatScoped && chatTitle != null) "${rule.description} • [Scoped to: $chatTitle]"
                else rule.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = CnsTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Condition & Action summary row + Global promotion button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "IF: ${rule.conditions.toReadableString()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.5.sp,
                        color = Color(0xFFA78BFA),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val actStr = rule.actions.joinToString(", ") { "${it.type} -> ${it.target ?: "default"}" }
                    Text(
                        text = "THEN: $actStr",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF93C5FD),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isChatScoped) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF4C1D95),
                        border = BorderStroke(1.dp, CnsPurple),
                        modifier = Modifier
                            .clickable { onPromoteToGlobal() }
                            .padding(start = 4.dp)
                            .testTag("promote_rule_${rule.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(text = "🌐", fontSize = 8.sp)
                            Text(
                                text = "Save to All Chats",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF3E8FF)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Add Gem or Rule Dialog with Global vs Chat-Specific selection and immediate "Save as Global" option.
 */
@Composable
fun AddGeminiGemDialog(
    presetIsGem: Boolean = true,
    activeChats: List<ConversationTaskNode> = emptyList(),
    onDismiss: () -> Unit,
    onAdd: (RuleDefinition, Boolean) -> Unit
) {
    var isGemMode by remember { mutableStateOf(presetIsGem) }
    var gemIcon by remember { mutableStateOf("💎") }
    var name by remember { mutableStateOf(if (presetIsGem) "Custom Code Architect Gem" else "Custom Safety Gate") }
    var desc by remember { mutableStateOf("User defined orchestration rule") }
    var instructions by remember { mutableStateOf("Enforce clean architecture, immutability, and state flow.") }
    var targetAgent by remember { mutableStateOf("CodingAgent") }

    // Scope configuration: "GLOBAL" or "CHAT"
    var scopeChoice by remember { mutableStateOf("GLOBAL") }
    var selectedChatId by remember { mutableStateOf(activeChats.firstOrNull()?.id ?: "") }
    var saveImmediatelyToGlobal by remember { mutableStateOf(false) }

    // Declarative Rule specifics
    var event by remember { mutableStateOf(RuleEvents.REQUEST_RECEIVED) }
    var field by remember { mutableStateOf("request.goal") }
    var operator by remember { mutableStateOf(ConditionOperator.CONTAINS) }
    var value by remember { mutableStateOf("refactor") }
    var actionType by remember { mutableStateOf(RuleActionTypes.ROUTE_AGENT) }
    var priorityStr by remember { mutableStateOf("250") }

    val gemIcons = listOf("💎", "🛡️", "🌐", "⚡", "💻", "📰", "🎙️", "📁", "🧠", "🎨")
    val agents = listOf("CodingAgent", "SearchAgent", "WebReviewAgent", "BrowserAgent", "FileAgent", "VoiceAgent", "WorkspaceAgent", "ImmuneSystem")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CnsCardBg,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Segmented Toggle: Gem vs Rule
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CnsCardSub, RoundedCornerShape(6.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isGemMode) CnsPurple else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isGemMode = true }
                    ) {
                        Text(
                            text = "💎 Gemini Gem",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isGemMode) Color.White else CnsTextMuted,
                            modifier = Modifier.padding(vertical = 4.dp),
                            maxLines = 1
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (!isGemMode) CnsCyan else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isGemMode = false }
                    ) {
                        Text(
                            text = "⚖️ Declarative Rule",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isGemMode) Color.Black else CnsTextMuted,
                            modifier = Modifier.padding(vertical = 4.dp),
                            maxLines = 1
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Gem Icon Picker
                if (isGemMode) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "GEM ICON AVATAR:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            color = CnsTextMuted
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            gemIcons.forEach { icon ->
                                val isSelected = gemIcon == icon
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CnsPurple.copy(alpha = 0.3f) else CnsCardSub,
                                    border = BorderStroke(1.dp, if (isSelected) CnsPurple else CnsBorderColor),
                                    modifier = Modifier.clickable { gemIcon = icon }
                                ) {
                                    Text(
                                        text = icon,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isGemMode) "Gem Name" else "Rule Name", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CnsPurple)
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description", fontSize = 8.5.sp, fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CnsPurple)
                )

                // Scope Configuration (Global vs Chat Specific)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CnsCardSub,
                    border = BorderStroke(0.5.dp, CnsBorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "APPLY SCOPE:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = CnsCyan
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val isGlobal = scopeChoice == "GLOBAL"
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isGlobal) CnsPurple.copy(alpha = 0.3f) else CnsCardBg,
                                border = BorderStroke(1.dp, if (isGlobal) CnsPurple else CnsBorderColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { scopeChoice = "GLOBAL" }
                            ) {
                                Text(
                                    text = "🌐 Global (All Chats)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = if (isGlobal) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isGlobal) Color(0xFFF3E8FF) else CnsTextMuted,
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp)
                                )
                            }

                            val isChat = scopeChoice == "CHAT"
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isChat) CnsAmber.copy(alpha = 0.3f) else CnsCardBg,
                                border = BorderStroke(1.dp, if (isChat) CnsAmber else CnsBorderColor),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { scopeChoice = "CHAT" }
                            ) {
                                Text(
                                    text = "💬 Specific Chat",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = if (isChat) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isChat) CnsAmber else CnsTextMuted,
                                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 4.dp)
                                )
                            }
                        }

                        // If Specific Chat chosen: Chat picker & "Save by one time appear in all chat" option
                        if (scopeChoice == "CHAT") {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Select Target Conversation:",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                color = CnsTextMuted
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                activeChats.forEach { task ->
                                    val isSelected = selectedChatId == task.id
                                    Surface(
                                        shape = RoundedCornerShape(3.dp),
                                        color = if (isSelected) CnsAmber.copy(alpha = 0.2f) else CnsCardBg,
                                        border = BorderStroke(0.5.dp, if (isSelected) CnsAmber else CnsBorderColor),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedChatId = task.id }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(text = task.icon, fontSize = 9.sp)
                                            Text(
                                                text = task.title,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.sp,
                                                color = if (isSelected) CnsAmber else CnsTextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // "Save by one time appear in all chat" instant toggle
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Checkbox(
                                    checked = saveImmediatelyToGlobal,
                                    onCheckedChange = { saveImmediatelyToGlobal = it },
                                    colors = CheckboxDefaults.colors(checkedColor = CnsPurple)
                                )
                                Column {
                                    Text(
                                        text = "One-Time Save to All Chats (Promote to Global)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF3E8FF)
                                    )
                                    Text(
                                        text = "Saves immediately so this rule appears in all chats.",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 7.sp,
                                        color = CnsTextMuted
                                    )
                                }
                            }
                        }
                    }
                }

                if (isGemMode) {
                    // Custom Persona Instructions text area
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        label = { Text("Gem System Instructions (Persona & Rules)", fontSize = 8.sp, fontFamily = FontFamily.Monospace) },
                        minLines = 3,
                        maxLines = 4,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CnsPurple)
                    )

                    // Target Agent Picker
                    Text(
                        text = "PRIMARY AGENT SPECIALIZATION:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.5.sp,
                        color = CnsTextMuted
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        agents.forEach { ag ->
                            val isSelected = targetAgent == ag
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) CnsCyan.copy(alpha = 0.25f) else CnsCardSub,
                                border = BorderStroke(0.5.dp, if (isSelected) CnsCyan else CnsBorderColor),
                                modifier = Modifier.clickable { targetAgent = ag }
                            ) {
                                Text(
                                    text = ag,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    color = if (isSelected) CnsCyan else CnsTextMuted,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Declarative Rule Controls
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = field,
                            onValueChange = { field = it },
                            label = { Text("Condition Field", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
                        )
                        OutlinedTextField(
                            value = value,
                            onValueChange = { value = it },
                            label = { Text("Match Value", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = targetAgent,
                            onValueChange = { targetAgent = it },
                            label = { Text("Target Agent / Action", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
                        )
                        OutlinedTextField(
                            value = priorityStr,
                            onValueChange = { priorityStr = it },
                            label = { Text("Priority", fontSize = 7.5.sp, fontFamily = FontFamily.Monospace) },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = priorityStr.toIntOrNull() ?: 200
                    val ruleId = if (isGemMode) "gem_${name.lowercase().replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis() % 1000}"
                    else "rule_${System.currentTimeMillis() % 10000}"

                    val newRule = if (isGemMode) {
                        RuleDefinition(
                            id = ruleId,
                            name = name,
                            description = desc,
                            priority = p,
                            trigger = RuleTrigger(RuleEvents.ALL),
                            conditions = SingleCondition("request.goal", ConditionOperator.CONTAINS, name.split(" ").firstOrNull()?.lowercase() ?: "gem"),
                            actions = listOf(
                                RuleAction(RuleActionTypes.ROUTE_AGENT, target = targetAgent),
                                RuleAction(RuleActionTypes.SET_CONTEXT, target = "active_gem", parameters = mapOf("gem" to name))
                            ),
                            scope = if (saveImmediatelyToGlobal) "GLOBAL" else scopeChoice,
                            chatId = if (saveImmediatelyToGlobal || scopeChoice == "GLOBAL") null else selectedChatId,
                            isGem = true,
                            gemIcon = gemIcon,
                            gemInstructions = instructions
                        )
                    } else {
                        RuleDefinition(
                            id = ruleId,
                            name = name,
                            description = desc,
                            priority = p,
                            trigger = RuleTrigger(event),
                            conditions = SingleCondition(field, operator, value),
                            actions = listOf(RuleAction(type = actionType, target = targetAgent.ifBlank { null })),
                            scope = if (saveImmediatelyToGlobal) "GLOBAL" else scopeChoice,
                            chatId = if (saveImmediatelyToGlobal || scopeChoice == "GLOBAL") null else selectedChatId
                        )
                    }

                    onAdd(newRule, saveImmediatelyToGlobal)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CnsPurple)
            ) {
                Text(
                    text = if (isGemMode) "Save Gemini Gem" else "Register Rule",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsTextMuted)
            }
        }
    )
}
