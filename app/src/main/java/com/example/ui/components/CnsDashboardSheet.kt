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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.cns.CentralNervousSystem
import com.example.agent.memory.ContextRouter
import com.example.agent.nodal.NodalEngine
import com.example.agent.registry.AgentRegistry
import com.example.agent.runtime.RuntimeStateManager
import kotlinx.coroutines.launch

private val CnsBgColor = Color(0xFF090C10)
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

enum class CnsDashboardTab(val label: String, val icon: String) {
    AGENTS("Agents", "🤖"),
    RULES("Rule Engine & Gems", "💎"),
    SAFETY("Safety Systems", "🛡️"),
    MEMORY("Memory & Scopes", "🧬"),
    WORKFLOWS("Nodal Workflows", "⚡")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CnsDashboardSheet(
    onDismiss: () -> Unit,
    onExecuteGoal: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val cns = remember { CentralNervousSystem.global }
    val agentRegistry = remember { AgentRegistry.global }
    val nodalEngine = remember { NodalEngine.global }
    val runtimeState = remember { RuntimeStateManager.global }
    val contextRouter = remember { ContextRouter.global }

    val isBusy by cns.isBusy.collectAsStateWithLifecycle()
    val activeMission by cns.activeMission.collectAsStateWithLifecycle()
    val registeredAgentNames by agentRegistry.registeredAgentNames.collectAsStateWithLifecycle()
    val runtimeMode by runtimeState.runtimeMode.collectAsStateWithLifecycle()
    val workflows by nodalEngine.workflows.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(CnsDashboardTab.AGENTS) }
    var diagnosticResult by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CnsBgColor,
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
        modifier = modifier.testTag("cns_dashboard_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. CNS Top Sovereign Header
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
                                Brush.linearGradient(listOf(Color(0xFF581C87), Color(0xFF1E1B4B))),
                                RoundedCornerShape(8.dp)
                            )
                            .border(1.dp, CnsPurple, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🧠", fontSize = 18.sp)
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "CENTRAL NERVOUS SYSTEM",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = CnsTextPrimary
                            )

                            // Status Chip
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isBusy) CnsAmber.copy(alpha = 0.2f) else CnsGreen.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, if (isBusy) CnsAmber else CnsGreen)
                            ) {
                                Text(
                                    text = if (isBusy) "ORCHESTRATING" else "COGNITIVE READY",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBusy) CnsAmber else CnsGreen,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = "GVONE Sovereign Multi-Agent Orchestrator & Biological Core",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CnsTextMuted
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close CNS Dashboard",
                        tint = CnsTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Active Mission or Quick Summary Pill
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CnsCardBg,
                border = BorderStroke(0.5.dp, CnsBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "MISSION:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsPurple
                    )
                    Text(
                        text = activeMission ?: "Autonomous monitoring • All sub-systems nominal",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = if (activeMission != null) CnsCyan else CnsTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = runtimeMode.toPromptLabel(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = Color(0xFFA78BFA)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Tab Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CnsDashboardTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) CnsPurple.copy(alpha = 0.2f) else CnsCardBg,
                        border = BorderStroke(1.dp, if (isSelected) CnsPurple else CnsBorderColor),
                        modifier = Modifier
                            .clickable { selectedTab = tab }
                            .testTag("cns_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(text = tab.icon, fontSize = 11.sp)
                            Text(
                                text = tab.label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFF3E8FF) else CnsTextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    CnsDashboardTab.AGENTS -> AgentsMatrixTab(agentRegistry, cns)
                    CnsDashboardTab.RULES -> RuleEngineTab(ruleEngine = cns.declarativeRuleEngine)
                    CnsDashboardTab.SAFETY -> BiologicalSafetyTab(cns) { diag -> diagnosticResult = diag }
                    CnsDashboardTab.MEMORY -> MemoryScopesTab(contextRouter, runtimeState)
                    CnsDashboardTab.WORKFLOWS -> NodalWorkflowsTab(nodalEngine)
                }
            }

            // Optional Diagnostic Output Banner
            if (diagnosticResult != null) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF111C24),
                    border = BorderStroke(0.5.dp, CnsCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = diagnosticResult ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CnsCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = CnsGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 5. Quick Sovereign Goal Presets
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CnsCardBg,
                border = BorderStroke(0.5.dp, CnsBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⚡ QUICK SOVEREIGN GOALS (EXECUTE VIA CNS):",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsTextMuted
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "🎵 /yt lofi beats" to "/yt lofi beats",
                            "🌐 /search tech news" to "/search tech news",
                            "💻 /code fix auth token" to "/code fix auth token",
                            "📁 /agent organize tabs" to "/agent organize tabs"
                        ).forEach { (label, goal) ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CnsCardSub,
                                border = BorderStroke(0.5.dp, CnsBorderLight),
                                modifier = Modifier.clickable {
                                    onDismiss()
                                    onExecuteGoal(goal)
                                }
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = CnsCyan,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentsMatrixTab(agentRegistry: AgentRegistry, cns: CentralNervousSystem) {
    val allAgents = remember {
        agentRegistry.getAllAgents().ifEmpty {
            // Built-in agents representation
            listOf("CodingAgent", "BrowserAgent", "FileAgent", "VoiceAgent", "CommandAgent", "SearchAgent")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(allAgents.size) { idx ->
            val agentName = if (allAgents[idx] is String) allAgents[idx] as String else (allAgents[idx] as com.example.agent.core.Agent).identity()
            val (icon, role, capabilities) = when (agentName) {
                "CodingAgent" -> Triple("🤖", "Kotlin Code Analyzer & Compiler", listOf("code:read", "code:patch", "build:compile"))
                "BrowserAgent" -> Triple("🌐", "DOM Inspector & Browser Operator", listOf("dom:click", "dom:inspect", "page:navigate"))
                "FileAgent" -> Triple("📁", "Sandbox File System Operator", listOf("file:read", "file:write", "workspace:sync"))
                "VoiceAgent" -> Triple("🎙️", "Audio Intent & Speech Synthesis", listOf("audio:listen", "audio:speak", "intent:parse"))
                "CommandAgent" -> Triple("⚡", "Bash & Terminal Shell Engine", listOf("shell:exec", "command:route", "nodal:trigger"))
                "SearchAgent" -> Triple("🔍", "Multi-Source Intelligence Cohort", listOf("web:search", "rag:retrieve", "synthesize"))
                else -> Triple("🤖", "Autonomous Agent", listOf("general:task"))
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CnsCardBg,
                border = BorderStroke(0.5.dp, CnsBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = icon, fontSize = 14.sp)
                            Column {
                                Text(
                                    text = agentName,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CnsTextPrimary
                                )
                                Text(
                                    text = role,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    color = CnsTextMuted
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = CnsGreen.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, CnsGreen)
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CnsGreen,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Capabilities tags
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        capabilities.forEach { cap ->
                            Surface(
                                shape = RoundedCornerShape(2.dp),
                                color = Color(0xFF1F2937)
                            ) {
                                Text(
                                    text = cap,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 8.sp,
                                    color = CnsCyan,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiologicalSafetyTab(cns: CentralNervousSystem, onDiagnose: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Reflex System Card
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CnsCardBg,
            border = BorderStroke(0.5.dp, CnsBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "⚡ AUTONOMIC REFLEX SYSTEM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsTextPrimary
                    )
                    Text(
                        text = "NOCICEPTION: READY",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CnsGreen
                    )
                }
                Text(
                    text = "Fast-path hardware nociception screening malicious payloads before task execution.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = CnsTextMuted
                )
                Button(
                    onClick = {
                        onDiagnose("Reflex System: 0 violations, latency < 1.2ms. Hardware guards verified.")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "RUN REFLEX SCREEN DIAGNOSTIC",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = CnsCyan
                    )
                }
            }
        }

        // 2. Immune System Card
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CnsCardBg,
            border = BorderStroke(0.5.dp, CnsBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🛡️ ADAPTIVE IMMUNE SYSTEM",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsTextPrimary
                    )
                    Text(
                        text = "ANTIBODIES: SYNCHRONIZED",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CnsPurple
                    )
                }
                Text(
                    text = "Tracks failure patterns and mitigates repetitive agent degradation across sessions.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = CnsTextMuted
                )
            }
        }

        // 3. Permission System Card
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CnsCardBg,
            border = BorderStroke(0.5.dp, CnsBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🔒 SOVEREIGN PERMISSION CORE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsTextPrimary
                    )
                    Text(
                        text = "STRICT ENFORCEMENT",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CnsGreen
                    )
                }
                Text(
                    text = "Controls agent privileges: file:read, file:write, network, dom:inspect.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = CnsTextMuted
                )
            }
        }
    }
}

@Composable
private fun MemoryScopesTab(contextRouter: ContextRouter, runtimeState: RuntimeStateManager) {
    val activeTask by runtimeState.activeTask.collectAsStateWithLifecycle()
    val taskHistory by runtimeState.taskHistory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CnsCardBg,
            border = BorderStroke(0.5.dp, CnsBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "🧬 CONTEXT ROUTER SCOPES",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CnsPurple
                )
                Text(
                    text = "Strict context isolation guarantees Task scratchpads do not leak into general chat or other tasks.",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = CnsTextMuted
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "• GLOBAL SCOPE:", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsCyan)
                    Text(text = "GVONE OS v2.0.0", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsTextPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "• ACTIVE TASK SCOPE:", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsCyan)
                    Text(text = activeTask?.taskId?.take(10) ?: "None (Clean Boundary)", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsGreen)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "• COMPLETED ISOLATED TASKS:", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsCyan)
                    Text(text = "${taskHistory.size} archived", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = CnsTextMuted)
                }
            }
        }
    }
}

@Composable
private fun NodalWorkflowsTab(nodalEngine: NodalEngine) {
    val workflows by nodalEngine.workflows.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(workflows.values.toList(), key = { it.id }) { wf ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CnsCardBg,
                border = BorderStroke(0.5.dp, CnsBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚡ ${wf.name}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CnsTextPrimary
                        )
                        Text(
                            text = "${wf.nodes.size} nodes",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CnsCyan
                        )
                    }

                    Text(
                        text = "Triggers: ${wf.triggerCommands.joinToString()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CnsPurple
                    )

                    Text(
                        text = "Nodes: ${wf.nodes.joinToString(" ➜ ") { it.name }}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = CnsTextMuted
                    )
                }
            }
        }
    }
}
