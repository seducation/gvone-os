package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.cns.CentralNervousSystem
import com.example.agent.core.Agent
import com.example.agent.core.AgentStep
import com.example.agent.core.StepStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDashboardSheet(
    cns: CentralNervousSystem,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isBusy by cns.isBusy.collectAsStateWithLifecycle()
    val activeMission by cns.activeMission.collectAsStateWithLifecycle()
    val immuneStatus by cns.immuneSystem.status.collectAsStateWithLifecycle()
    val isFrozen by cns.reflexSystem.isFrozen.collectAsStateWithLifecycle()
    val steps by cns.logger.steps.collectAsStateWithLifecycle()

    var goalInput by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Overview, 1 = Agents, 2 = Steps Audit

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF10141D),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF3B4354))
        },
        modifier = modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isFrozen) Color.Red else if (isBusy) Color(0xFF00E5FF) else Color(0xFF00E676))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "KAI.KAMUI // CNS ORGANISM",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF90A4AE)
                    )
                }
            }

            // High-Level Health Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161C28)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "IMMUNE INFLAMMATION",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF90A4AE)
                        )
                        LinearProgressIndicator(
                            progress = { immuneStatus.inflammationScore.toFloat() },
                            modifier = Modifier
                                .width(120.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (immuneStatus.isFeverMode) Color.Red else Color(0xFF00E5FF),
                            trackColor = Color(0xFF242C3D),
                        )
                    }

                    if (immuneStatus.isFeverMode) {
                        Surface(
                            color = Color(0xFFB71C1C),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "FEVER MODE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (isFrozen) {
                        Button(
                            onClick = { cns.reflexSystem.clearEmergencyFreeze() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("THAW SYSTEM", fontSize = 10.sp)
                        }
                    } else {
                        Surface(
                            color = Color(0xFF1B5E20),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "SPINAL REFLEX OK",
                                color = Color(0xFF81C784),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Goal Input Bar
            OutlinedTextField(
                value = goalInput,
                onValueChange = { goalInput = it },
                placeholder = { Text("Enter mission goal (e.g., 'Compare page with document')", fontSize = 13.sp, color = Color(0xFF607D8B)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (goalInput.isNotBlank()) {
                                val goal = goalInput
                                goalInput = ""
                                coroutineScope.launch {
                                    cns.orchestrateGoal(goal)
                                }
                            }
                        },
                        enabled = !isBusy && goalInput.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Execute Goal",
                            tint = if (!isBusy && goalInput.isNotBlank()) Color(0xFF00E5FF) else Color(0xFF455A64)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF2C3549),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Tabs Switcher
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFF00E5FF),
                divider = {},
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Agents (${cns.getAllAgents().size})", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Step Audit (${steps.size})", fontSize = 12.sp) }
                )
            }

            // Body
            when (selectedTab) {
                0 -> AgentsListView(agents = cns.getAllAgents())
                1 -> StepAuditView(steps = steps.reversed())
            }
        }
    }
}

@Composable
private fun AgentsListView(agents: List<Agent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(agents) { agent ->
            val health = agent.health()
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161C28)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = agent.identity(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Surface(
                            color = if (health.isQuarantined) Color.Red else Color(0xFF263238),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (health.isQuarantined) "QUARANTINED" else health.status.name,
                                color = if (health.isQuarantined) Color.White else Color(0xFF90A4AE),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Metabolic Stress: ${(health.metabolicStress * 100).toInt()}%",
                            fontSize = 11.sp,
                            color = Color(0xFF78909C)
                        )
                        LinearProgressIndicator(
                            progress = { health.metabolicStress.toFloat() },
                            modifier = Modifier
                                .width(80.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFFFFB300),
                            trackColor = Color(0xFF2C3549)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Caps: " + agent.capabilities().joinToString(", ") { it.name },
                        fontSize = 10.sp,
                        color = Color(0xFF546E7A),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun StepAuditView(steps: List<AgentStep>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(steps) { step ->
            val color = when (step.status) {
                StepStatus.SUCCESS -> Color(0xFF00E676)
                StepStatus.FAILED -> Color(0xFFFF5252)
                StepStatus.RUNNING -> Color(0xFF00E5FF)
                else -> Color(0xFFB0BEC5)
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, Color(0xFF202738), RoundedCornerShape(8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.status.icon,
                        color = color,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "[${step.agentName}]",
                                fontSize = 11.sp,
                                color = Color(0xFF00E5FF),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = step.action.name,
                                fontSize = 10.sp,
                                color = Color(0xFF90A4AE),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Text(
                            text = step.target,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        if (step.errorMessage != null) {
                            Text(
                                text = "Err: ${step.errorMessage}",
                                fontSize = 10.sp,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }
                    if (step.durationMs > 0) {
                        Text(
                            text = "${step.durationMs}ms",
                            fontSize = 10.sp,
                            color = Color(0xFF546E7A),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
