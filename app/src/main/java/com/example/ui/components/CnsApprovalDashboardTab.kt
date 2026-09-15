package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.cns.CentralNervousSystem
import com.example.agent.cns.CnsApprovalMode
import com.example.agent.safety.PermissionAuditEvent
import com.example.agent.safety.PermissionPolicy
import com.example.agent.safety.PermissionRequest
import com.example.agent.safety.PermissionSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CnsCardBg = Color(0xFF0D1117)
private val CnsCardSub = Color(0xFF161B22)
private val CnsBgColor = Color(0xFF0B0F17)
private val CnsBorderColor = Color(0xFF21262D)
private val CnsBorderLight = Color(0xFF30363D)
private val CnsPurple = Color(0xFFA855F7)
private val CnsCyan = Color(0xFF38BDF8)
private val CnsGreen = Color(0xFF3FB950)
private val CnsAmber = Color(0xFFEAB308)
private val CnsRed = Color(0xFFF85149)
private val CnsTextPrimary = Color(0xFFE6EDF3)
private val CnsTextSecondary = Color(0xFF94A3B8)
private val CnsTextMuted = Color(0xFF8B949E)

/**
 * Sovereign CNS Approval & Autonomy Governance Dashboard.
 * Provides the three explicit operational modes:
 * - "Full Approve" (Autonomous Sovereignty)
 * - "Ask for Approval" (Human-in-the-Loop)
 * - "Approve for Me" (AI Smart Adaptive)
 *
 * Includes live pending approval queues, domain-level policy matrix,
 * one-click testing simulators, and audit history.
 */
@Composable
fun CnsApprovalDashboardTab(
    cns: CentralNervousSystem = CentralNervousSystem.global,
    permissionSystem: PermissionSystem = PermissionSystem.global,
    modifier: Modifier = Modifier
) {
    val approvalMode by cns.approvalMode.collectAsStateWithLifecycle()
    val activePrompt by permissionSystem.activePromptFlow.collectAsStateWithLifecycle()
    val scopePolicies by permissionSystem.scopePoliciesFlow.collectAsStateWithLifecycle()
    val grantedPermissions by permissionSystem.grantedPermissionsFlow.collectAsStateWithLifecycle()
    val auditLogs by permissionSystem.auditLogFlow.collectAsStateWithLifecycle()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var selectedDomainFilter by remember { mutableStateOf("All") }

    val categories = remember {
        listOf("All", "Filesystem", "Browser", "System & Shell", "Version Control", "Network", "Integrations")
    }

    val domainGroups = remember(selectedDomainFilter) {
        val all = listOf(
            DomainCategoryItem(
                name = "Filesystem Operations",
                category = "Filesystem",
                keys = listOf(PermissionSystem.PERM_FILESYSTEM_READ, PermissionSystem.PERM_FILESYSTEM_WRITE, PermissionSystem.PERM_FILESYSTEM_DELETE),
                description = "Read source files, write project code, delete obsolete artifacts."
            ),
            DomainCategoryItem(
                name = "Browser & Web Exploration",
                category = "Browser",
                keys = listOf(PermissionSystem.PERM_BROWSER_NAVIGATE, PermissionSystem.PERM_BROWSER_READ, PermissionSystem.PERM_BROWSER_INTERACT),
                description = "Navigate URLs, read web documents, execute browser DOM interactions."
            ),
            DomainCategoryItem(
                name = "System & Shell Execution",
                category = "System & Shell",
                keys = listOf(PermissionSystem.PERM_SHELL_EXECUTE),
                description = "Run terminal commands, build scripts, Gradle tasks, and linters."
            ),
            DomainCategoryItem(
                name = "Version Control (Git)",
                category = "Version Control",
                keys = listOf(PermissionSystem.PERM_GIT_READ, PermissionSystem.PERM_GIT_WRITE),
                description = "Read repository commit logs, stage changes, push git commits."
            ),
            DomainCategoryItem(
                name = "Network & Cloud APIs",
                category = "Network",
                keys = listOf(PermissionSystem.PERM_NETWORK_REQUEST, PermissionSystem.PERM_EXTERNAL_API),
                description = "Dispatch HTTP calls, query search engines, invoke Gemini API."
            )
        )
        if (selectedDomainFilter == "All") all
        else all.filter { it.category.equals(selectedDomainFilter, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("cns_approval_dashboard_tab"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Status Notice if triggered
        if (statusMessage != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF064E3B).copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, CnsGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🛡️", fontSize = 14.sp)
                            Text(
                                text = statusMessage!!,
                                color = Color(0xFFD1FAE5),
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(
                            onClick = { statusMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFFD1FAE5),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Overview Header Card
        item {
            ApprovalDashboardHeaderCard(
                approvalMode = approvalMode,
                totalGrants = grantedPermissions.size,
                pendingCount = if (activePrompt != null) 1 else 0
            )
        }

        // 3. Primary Mode Selector: Full Approve | Ask for Approval | Approve for Me
        item {
            Text(
                text = "SOVEREIGN AUTONOMY POLICY",
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CnsTextSecondary,
                letterSpacing = 1.sp
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Mode 1: Full Approve
                ApprovalModeSelectorCard(
                    mode = CnsApprovalMode.FULL_APPROVE,
                    isSelected = approvalMode == CnsApprovalMode.FULL_APPROVE,
                    accentColor = CnsGreen,
                    badgeText = "AUTONOMOUS",
                    icon = Icons.Rounded.CheckCircle,
                    onSelect = {
                        cns.setApprovalMode(CnsApprovalMode.FULL_APPROVE)
                        statusMessage = "⚡ 'Full Approve' activated. All agent actions execute autonomously."
                    }
                )

                // Mode 2: Ask for Approval
                ApprovalModeSelectorCard(
                    mode = CnsApprovalMode.ASK_FOR_APPROVAL,
                    isSelected = approvalMode == CnsApprovalMode.ASK_FOR_APPROVAL,
                    accentColor = CnsAmber,
                    badgeText = "STRICT PROMPT",
                    icon = Icons.Rounded.Shield,
                    onSelect = {
                        cns.setApprovalMode(CnsApprovalMode.ASK_FOR_APPROVAL)
                        statusMessage = "🛡️ 'Ask for Approval' activated. Sensitive actions require manual consent."
                    }
                )

                // Mode 3: Approve for Me
                ApprovalModeSelectorCard(
                    mode = CnsApprovalMode.APPROVE_FOR_ME,
                    isSelected = approvalMode == CnsApprovalMode.APPROVE_FOR_ME,
                    accentColor = CnsPurple,
                    badgeText = "AI ADAPTIVE",
                    icon = Icons.Rounded.AutoAwesome,
                    onSelect = {
                        cns.setApprovalMode(CnsApprovalMode.APPROVE_FOR_ME)
                        statusMessage = "✨ 'Approve for Me' activated. Safe operations auto-approved; critical risks prompt."
                    }
                )
            }
        }

        // 4. Pending Approval Requests Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PENDING APPROVAL QUEUE",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CnsTextSecondary,
                    letterSpacing = 1.sp
                )

                if (activePrompt != null) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CnsAmber.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, CnsAmber)
                    ) {
                        Text(
                            text = "1 ACTION REQUIRES REVIEW",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CnsAmber,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            if (activePrompt != null) {
                ActiveApprovalPromptCard(
                    request = activePrompt!!,
                    onApprove = {
                        cns.approvePendingRequest(activePrompt!!.id, rememberPolicy = false)
                        statusMessage = "✅ Request '${activePrompt!!.permission}' approved for ${activePrompt!!.agentName}."
                    },
                    onApproveForMe = {
                        cns.approvePendingRequest(activePrompt!!.id, rememberPolicy = true)
                        statusMessage = "✨ '${activePrompt!!.permission}' set to 'Approve for Me' (auto-approved permanently)."
                    },
                    onDeny = {
                        cns.denyPendingRequest(activePrompt!!.id, rememberPolicy = false)
                        statusMessage = "❌ Request '${activePrompt!!.permission}' denied."
                    }
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CnsCardBg,
                    border = BorderStroke(1.dp, CnsBorderColor.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = "Safe",
                            tint = CnsGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Zero Pending Approvals",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CnsTextPrimary
                            )
                            Text(
                                text = "All agents operating smoothly under '${approvalMode.title}' mode.",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                color = CnsTextMuted
                            )
                        }

                        // Simulation button to test UI
                        Button(
                            onClick = {
                                cns.simulateApprovalRequest()
                                statusMessage = "⚡ Test approval request triggered: 'shell.execute' by CommandAgent."
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CnsCardSub),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, CnsCyan.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("simulate_approval_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = CnsCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Simulate Approval Request",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = CnsCyan
                            )
                        }
                    }
                }
            }
        }

        // 5. Domain Category Policies Matrix
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "DOMAIN PERMISSION MATRIX",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = CnsTextSecondary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Granular Domain Overrides",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CnsCyan
                )
            }
        }

        // Category Filter Chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedDomainFilter == cat
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) CnsCyan.copy(alpha = 0.2f) else CnsCardBg,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) CnsCyan else CnsBorderColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.clickable { selectedDomainFilter = cat }
                    ) {
                        Text(
                            text = cat,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) CnsCyan else CnsTextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Domain Category Cards
        items(domainGroups) { domain ->
            DomainPolicyRowCard(
                domain = domain,
                permissionSystem = permissionSystem,
                onSetPolicy = { policy ->
                    domain.keys.forEach { key ->
                        permissionSystem.setPolicy(key, policy)
                    }
                    statusMessage = "Updated ${domain.name} policy to ${policy.name}."
                }
            )
        }

        // 6. Quick Safety Management Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showResetDialog = true },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, CnsAmber.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CnsAmber),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reset Defaults", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
                }

                OutlinedButton(
                    onClick = {
                        permissionSystem.revokeAll()
                        statusMessage = "🛑 All permissions revoked. Strict isolation engaged."
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, CnsRed.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CnsRed),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Revoke All", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
                }

                OutlinedButton(
                    onClick = {
                        permissionSystem.clearAuditLogs()
                        statusMessage = "Audit log stream cleared."
                    },
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, CnsBorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CnsTextSecondary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Clear Log", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp)
                }
            }
        }

        // 7. Recent Audit Trail Stream
        item {
            Text(
                text = "RECENT APPROVAL AUDIT STREAM",
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = CnsTextSecondary,
                letterSpacing = 1.sp
            )
        }

        if (auditLogs.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CnsCardBg,
                    border = BorderStroke(0.5.dp, CnsBorderColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No audit events recorded yet.",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CnsTextMuted
                        )
                    }
                }
            }
        } else {
            items(auditLogs.take(8)) { event ->
                AuditLogRow(event = event)
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            containerColor = CnsCardBg,
            title = {
                Text(
                    text = "Reset Default Safety Policies?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CnsTextPrimary
                )
            },
            text = {
                Text(
                    text = "This restores standard default policies (Read/Browse = ALLOW, Write/Delete/Shell = ASK).",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    color = CnsTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        permissionSystem.resetToDefaults()
                        cns.setApprovalMode(CnsApprovalMode.APPROVE_FOR_ME)
                        showResetDialog = false
                        statusMessage = "Reset to default safety policies and 'Approve for Me' mode."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CnsAmber),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Reset", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = CnsTextMuted)
                }
            }
        )
    }
}

/**
 * Top Overview Header Card with Live Status and Stats
 */
@Composable
private fun ApprovalDashboardHeaderCard(
    approvalMode: CnsApprovalMode,
    totalGrants: Int,
    pendingCount: Int
) {
    val modeColor = when (approvalMode) {
        CnsApprovalMode.FULL_APPROVE -> CnsGreen
        CnsApprovalMode.ASK_FOR_APPROVAL -> CnsAmber
        CnsApprovalMode.APPROVE_FOR_ME -> CnsPurple
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CnsCardBg,
        border = BorderStroke(1.dp, modeColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            .background(modeColor.copy(alpha = 0.15f))
                            .border(1.dp, modeColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = approvalMode.icon, fontSize = 16.sp)
                    }

                    Column {
                        Text(
                            text = "APPROVAL & AUTONOMY CENTER",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = CnsTextPrimary
                        )
                        Text(
                            text = "Current Mode: ${approvalMode.title}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = modeColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = modeColor.copy(alpha = 0.2f),
                    border = BorderStroke(0.5.dp, modeColor)
                ) {
                    Text(
                        text = approvalMode.subtitle.split("•").first().trim().uppercase(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = modeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = approvalMode.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = CnsTextSecondary,
                lineHeight = 13.sp
            )

            HorizontalDivider(color = CnsBorderColor.copy(alpha = 0.6f), thickness = 0.5.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatBadge(label = "ACTIVE GRANTS", value = "$totalGrants Perms", color = CnsCyan)
                StatBadge(label = "PENDING REVIEWS", value = "$pendingCount Requests", color = if (pendingCount > 0) CnsAmber else CnsGreen)
                StatBadge(label = "AUTONOMY GOVERNANCE", value = "ENFORCED", color = CnsGreen)
            }
        }
    }
}

@Composable
private fun StatBadge(label: String, value: String, color: Color) {
    Column {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.5.sp,
            color = CnsTextMuted
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Interactive Selection Card for each of the 3 Autonomy Modes
 */
@Composable
private fun ApprovalModeSelectorCard(
    mode: CnsApprovalMode,
    isSelected: Boolean,
    accentColor: Color,
    badgeText: String,
    icon: ImageVector,
    onSelect: () -> Unit
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else CnsBorderColor.copy(alpha = 0.6f),
        label = "border_anim"
    )

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) CnsCardBg else CnsBgColor.copy(alpha = 0.7f),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, animatedBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("mode_card_${mode.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Radio circle or checkmark
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else CnsCardSub)
                    .border(
                        1.dp,
                        if (isSelected) accentColor else CnsBorderLight,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Mode Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = mode.title,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) CnsTextPrimary else CnsTextSecondary
                    )

                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = accentColor.copy(alpha = if (isSelected) 0.25f else 0.12f),
                        border = BorderStroke(0.5.dp, accentColor.copy(alpha = if (isSelected) 0.9f else 0.4f))
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = mode.subtitle,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    color = if (isSelected) accentColor else CnsTextMuted,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = mode.description,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = CnsTextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 11.sp
                )
            }

            // Right Icon
            Icon(
                imageVector = icon,
                contentDescription = mode.title,
                tint = if (isSelected) accentColor else CnsTextMuted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Active Approval Prompt Card with Direct Decision Buttons
 */
@Composable
private fun ActiveApprovalPromptCard(
    request: PermissionRequest,
    onApprove: () -> Unit,
    onApproveForMe: () -> Unit,
    onDeny: () -> Unit
) {
    val isCritical = request.risk.equals("CRITICAL", ignoreCase = true)
    val riskColor = if (isCritical) CnsRed else CnsAmber

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CnsCardBg,
        border = BorderStroke(1.5.dp, riskColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_approval_prompt_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Risk & Agent
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
                        color = riskColor.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, riskColor)
                    ) {
                        Text(
                            text = "⚠️ ${request.risk} RISK",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = riskColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "Agent: ${request.agentName}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsTextPrimary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = CnsCardSub,
                    border = BorderStroke(0.5.dp, CnsBorderLight)
                ) {
                    Text(
                        text = request.permission,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = CnsCyan,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            // Description of what is requested
            Text(
                text = request.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = Color(0xFFE2E8F0),
                lineHeight = 13.sp
            )

            HorizontalDivider(color = CnsBorderColor.copy(alpha = 0.5f), thickness = 0.5.dp)

            // 3 Explicit Decision Buttons: Approve | Approve for Me | Deny
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Deny
                Button(
                    onClick = onDeny,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CnsRed.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, CnsRed),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Deny", tint = CnsRed, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Deny", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = CnsRed)
                }

                // Approve for Me (Smart Remember)
                Button(
                    onClick = onApproveForMe,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CnsPurple.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, CnsPurple),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, tint = CnsPurple, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Approve for Me", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF3E8FF))
                }

                // Approve
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CnsGreen),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.Check, contentDescription = "Approve", tint = Color.Black, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Approve", fontFamily = FontFamily.Monospace, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

/**
 * Domain Category Item representation
 */
private data class DomainCategoryItem(
    val name: String,
    val category: String,
    val keys: List<String>,
    val description: String
)

/**
 * Domain Policy Row Card with 3-Way Policy Switching
 */
@Composable
private fun DomainPolicyRowCard(
    domain: DomainCategoryItem,
    permissionSystem: PermissionSystem,
    onSetPolicy: (PermissionPolicy) -> Unit
) {
    // Current prevailing policy for this domain
    val policies = domain.keys.map { permissionSystem.getPolicy(it) }
    val prevailingPolicy = when {
        policies.all { it == PermissionPolicy.ALLOW } -> PermissionPolicy.ALLOW
        policies.all { it == PermissionPolicy.DENY } -> PermissionPolicy.DENY
        else -> PermissionPolicy.ASK
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CnsCardBg,
        border = BorderStroke(0.5.dp, CnsBorderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = domain.name,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CnsTextPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = when (prevailingPolicy) {
                            PermissionPolicy.ALLOW -> CnsGreen.copy(alpha = 0.2f)
                            PermissionPolicy.ASK -> CnsAmber.copy(alpha = 0.2f)
                            PermissionPolicy.DENY -> CnsRed.copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = prevailingPolicy.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (prevailingPolicy) {
                                PermissionPolicy.ALLOW -> CnsGreen
                                PermissionPolicy.ASK -> CnsAmber
                                PermissionPolicy.DENY -> CnsRed
                            },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // 3-way toggle buttons: ALLOW | ASK | DENY
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    PolicyToggleButton(
                        label = "Approve",
                        isActive = prevailingPolicy == PermissionPolicy.ALLOW,
                        activeColor = CnsGreen,
                        onClick = { onSetPolicy(PermissionPolicy.ALLOW) }
                    )
                    PolicyToggleButton(
                        label = "Ask",
                        isActive = prevailingPolicy == PermissionPolicy.ASK,
                        activeColor = CnsAmber,
                        onClick = { onSetPolicy(PermissionPolicy.ASK) }
                    )
                    PolicyToggleButton(
                        label = "Deny",
                        isActive = prevailingPolicy == PermissionPolicy.DENY,
                        activeColor = CnsRed,
                        onClick = { onSetPolicy(PermissionPolicy.DENY) }
                    )
                }
            }

            Text(
                text = domain.description,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = CnsTextMuted,
                lineHeight = 11.sp
            )
        }
    }
}

@Composable
private fun PolicyToggleButton(
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = if (isActive) activeColor.copy(alpha = 0.25f) else CnsCardSub,
        border = BorderStroke(0.5.dp, if (isActive) activeColor else CnsBorderLight),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 7.5.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) activeColor else CnsTextSecondary,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/**
 * Audit Log Stream Item
 */
@Composable
private fun AuditLogRow(event: PermissionAuditEvent) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val outcomeColor = when {
        event.outcome.contains("GRANTED") || event.outcome.contains("ALLOW") || event.outcome.contains("APPROVED") -> CnsGreen
        event.outcome.contains("PROMPT") || event.outcome.contains("ASK") -> CnsAmber
        else -> CnsRed
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = CnsCardBg.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, CnsBorderColor.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = timeFormat.format(Date(event.timestamp)),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    color = CnsTextMuted
                )

                Text(
                    text = event.caller,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = CnsTextPrimary
                )

                Text(
                    text = "→ ${event.permission}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = CnsTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = RoundedCornerShape(3.dp),
                color = outcomeColor.copy(alpha = 0.15f),
                border = BorderStroke(0.5.dp, outcomeColor.copy(alpha = 0.6f))
            ) {
                Text(
                    text = event.outcome,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = outcomeColor,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}
