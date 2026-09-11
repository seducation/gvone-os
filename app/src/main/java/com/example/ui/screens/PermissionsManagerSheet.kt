package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.agent.safety.PermissionAuditEvent
import com.example.agent.safety.PermissionDescriptor
import com.example.agent.safety.PermissionPolicy
import com.example.agent.safety.PermissionSystem
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsManagerSheet(
    permissionSystem: PermissionSystem = PermissionSystem.global,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grantedPermissions by permissionSystem.grantedPermissionsFlow.collectAsStateWithLifecycle()
    val scopePolicies by permissionSystem.scopePoliciesFlow.collectAsStateWithLifecycle()
    val auditLogs by permissionSystem.auditLogFlow.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Policies & Grants, 1 = Audit Trail
    var showResetDialog by remember { mutableStateOf(false) }
    var showRevokeAllDialog by remember { mutableStateOf(false) }

    val categories = remember {
        listOf("All", "Filesystem", "Browser", "System & Shell", "Version Control", "Network", "Integrations")
    }

    val filteredDescriptors = remember(searchQuery, selectedCategory, scopePolicies, grantedPermissions) {
        PermissionSystem.ALL_DESCRIPTORS.filter { desc ->
            val matchesCategory = (selectedCategory == "All" || desc.category.equals(selectedCategory, ignoreCase = true))
            val matchesQuery = searchQuery.isBlank() ||
                    desc.title.contains(searchQuery, ignoreCase = true) ||
                    desc.key.contains(searchQuery, ignoreCase = true) ||
                    desc.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    // Counts for overview banner
    val totalPermissions = PermissionSystem.ALL_DESCRIPTORS.size
    val activeGrantsCount = PermissionSystem.ALL_DESCRIPTORS.count { permissionSystem.hasPermission(it.key) }
    val allowPolicyCount = PermissionSystem.ALL_DESCRIPTORS.count { permissionSystem.getPolicy(it.key) == PermissionPolicy.ALLOW }
    val askPolicyCount = PermissionSystem.ALL_DESCRIPTORS.count { permissionSystem.getPolicy(it.key) == PermissionPolicy.ASK }
    val denyPolicyCount = PermissionSystem.ALL_DESCRIPTORS.count { permissionSystem.getPolicy(it.key) == PermissionPolicy.DENY }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0D121B),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF384459))
            )
        },
        modifier = modifier
            .fillMaxHeight(0.94f)
            .testTag("permissions_manager_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = GVONEPrimary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "Security Shield Icon",
                                tint = GVONEPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Permissions & Safety Gate",
                            color = GVONETextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Govern autonomous agent capabilities & safety boundaries",
                            color = GVONETextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("permissions_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = GVONETextSecondary
                    )
                }
            }

            // Tab Selector: Policies vs Audit
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF131822),
                contentColor = GVONEPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Policies & Scopes", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    },
                    modifier = Modifier.testTag("permissions_tab_policies")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Audit Trail (${auditLogs.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    },
                    modifier = Modifier.testTag("permissions_tab_audit")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                // Tab 0: Policies & Scopes
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Metric Summary Cards
                    item {
                        Surface(
                            color = Color(0xFF141A26),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFF242E42)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "Permission Governance Overview",
                                    color = GVONETextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MetricChip(
                                        title = "Active Grants",
                                        value = "$activeGrantsCount / $totalPermissions",
                                        color = GVONESecondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        title = "Auto-Allow",
                                        value = "$allowPolicyCount",
                                        color = GVONETertiary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        title = "Ask Confirm",
                                        value = "$askPolicyCount",
                                        color = GVONEAccentOrange,
                                        modifier = Modifier.weight(1f)
                                    )
                                    MetricChip(
                                        title = "Blocked",
                                        value = "$denyPolicyCount",
                                        color = GVONEAccentRed,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Quick Actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showResetDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("permissions_reset_defaults_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color(0xFF384459)),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp), tint = GVONETextSecondary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset Defaults", fontSize = 12.sp, color = GVONETextPrimary)
                                    }

                                    OutlinedButton(
                                        onClick = { showRevokeAllDialog = true },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("permissions_revoke_all_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, GVONEAccentRed.copy(alpha = 0.5f)),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.RemoveModerator, contentDescription = null, modifier = Modifier.size(15.dp), tint = GVONEAccentRed)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Revoke Grants", fontSize = 12.sp, color = GVONEAccentRed)
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            permissionSystem.triggerTestPrompt(
                                                permission = PermissionSystem.PERM_FILESYSTEM_DELETE,
                                                agentName = "FileAgent",
                                                description = "Simulating prompt for sandbox file removal"
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .testTag("permissions_test_prompt_btn"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF222E42)),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp), tint = GVONEPrimary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test Prompt", fontSize = 12.sp, color = GVONETextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    // Search input
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter permissions by keyword or scope...", color = GVONETextSecondary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = GVONETextSecondary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = GVONETextSecondary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF141923),
                                unfocusedContainerColor = Color(0xFF141923),
                                focusedBorderColor = GVONEPrimary,
                                unfocusedBorderColor = Color(0xFF2B3447),
                                focusedTextColor = GVONETextPrimary,
                                unfocusedTextColor = GVONETextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("permissions_search_input")
                        )
                    }

                    // Category Filter Chips
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(categories) { cat ->
                                val isSelected = cat == selectedCategory
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GVONEPrimary,
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF151C28),
                                        labelColor = GVONETextSecondary
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) GVONEPrimary else Color(0xFF273347)),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                        }
                    }

                    // Permission Items
                    items(filteredDescriptors, key = { it.key }) { descriptor ->
                        val currentPolicy = permissionSystem.getPolicy(descriptor.key)
                        val isGranted = permissionSystem.hasPermission(descriptor.key)

                        PermissionItemCard(
                            descriptor = descriptor,
                            currentPolicy = currentPolicy,
                            isGranted = isGranted,
                            onPolicyChange = { newPolicy ->
                                permissionSystem.setPolicy(descriptor.key, newPolicy)
                            },
                            onToggleGrant = {
                                if (isGranted) {
                                    permissionSystem.revokePermission(descriptor.key)
                                } else {
                                    permissionSystem.grantPermission(descriptor.key)
                                }
                            }
                        )
                    }
                }
            } else {
                // Tab 1: Audit Trail
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Real-time Permission Check & Enforcement Log",
                            color = GVONETextSecondary,
                            fontSize = 12.sp
                        )
                        if (auditLogs.isNotEmpty()) {
                            TextButton(
                                onClick = { permissionSystem.clearAuditLogs() },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("Clear History", color = GVONEAccentRed, fontSize = 12.sp)
                            }
                        }
                    }

                    if (auditLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF384459),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("No recent permission events logged", color = GVONETextSecondary, fontSize = 13.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(auditLogs, key = { it.id }) { event ->
                                AuditLogCard(event)
                            }
                        }
                    }
                }
            }
        }
    }

    // Reset Defaults Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to Secure Defaults?", color = GVONETextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This will restore standard safe permissions (Browser & Filesystem Read allowed, Shell and Deletion requiring user confirmation).",
                    color = GVONETextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        permissionSystem.resetToDefaults()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            },
            containerColor = Color(0xFF131822)
        )
    }

    // Revoke All Confirmation Dialog
    if (showRevokeAllDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeAllDialog = false },
            title = { Text("Revoke All Active Grants?", color = GVONETextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This clears all runtime granted permissions. Agents will be re-prompted or blocked according to their scope policies.",
                    color = GVONETextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        permissionSystem.revokeAll()
                        showRevokeAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEAccentRed)
                ) {
                    Text("Revoke All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeAllDialog = false }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            },
            containerColor = Color(0xFF131822)
        )
    }
}

@Composable
fun MetricChip(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF0F141D),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF202736)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(title, color = GVONETextSecondary, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
fun PermissionItemCard(
    descriptor: PermissionDescriptor,
    currentPolicy: PermissionPolicy,
    isGranted: Boolean,
    onPolicyChange: (PermissionPolicy) -> Unit,
    onToggleGrant: () -> Unit
) {
    val riskColor = when (descriptor.riskLevel.uppercase()) {
        "CRITICAL" -> GVONEAccentRed
        "HIGH" -> GVONEAccentOrange
        else -> GVONETertiary
    }

    val icon: ImageVector = when {
        descriptor.key.contains("filesystem") || descriptor.key.contains("file") -> Icons.Rounded.Folder
        descriptor.key.contains("shell") -> Icons.Rounded.Terminal
        descriptor.key.contains("browser") -> Icons.Rounded.Language
        descriptor.key.contains("network") -> Icons.Rounded.VpnKey
        descriptor.key.contains("git") -> Icons.Rounded.Code
        descriptor.key.contains("external") -> Icons.Rounded.CloudSync
        else -> Icons.Rounded.Security
    }

    Surface(
        color = Color(0xFF141924),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF222B3D)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_item_${descriptor.key}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Icon, Title, Category, Risk
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = riskColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(imageVector = icon, contentDescription = null, tint = riskColor, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = descriptor.title,
                            color = GVONETextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        // Category chip
                        Surface(
                            color = Color(0xFF1F283B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = descriptor.category,
                                color = GVONETextSecondary,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = descriptor.key,
                        color = GVONETextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Risk Badge
                Surface(
                    color = riskColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, riskColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = descriptor.riskLevel,
                        color = riskColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = descriptor.description,
                color = GVONETextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Policy Selector Controls & Active Grant Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active Grant Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onToggleGrant() }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isGranted) GVONETertiary else Color(0xFF4B5563))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGranted) "Granted" else "Not Granted",
                        color = if (isGranted) GVONETertiary else GVONETextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 3-Way Policy Segmented Pill
                Surface(
                    color = Color(0xFF0F141E),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF263247))
                ) {
                    Row(
                        modifier = Modifier.padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        PolicyPill(
                            label = "ALLOW",
                            isSelected = currentPolicy == PermissionPolicy.ALLOW,
                            activeColor = GVONETertiary,
                            onClick = { onPolicyChange(PermissionPolicy.ALLOW) }
                        )
                        PolicyPill(
                            label = "ASK",
                            isSelected = currentPolicy == PermissionPolicy.ASK,
                            activeColor = GVONEAccentOrange,
                            onClick = { onPolicyChange(PermissionPolicy.ASK) }
                        )
                        PolicyPill(
                            label = "BLOCK",
                            isSelected = currentPolicy == PermissionPolicy.DENY,
                            activeColor = GVONEAccentRed,
                            onClick = { onPolicyChange(PermissionPolicy.DENY) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PolicyPill(
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) activeColor.copy(alpha = 0.25f) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        border = if (isSelected) BorderStroke(1.dp, activeColor.copy(alpha = 0.6f)) else null,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) activeColor else GVONETextSecondary
        )
    }
}

@Composable
fun AuditLogCard(event: PermissionAuditEvent) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(event.timestamp) { dateFormat.format(Date(event.timestamp)) }

    val isPositive = event.outcome.contains("ALLOW") || event.outcome.contains("APPROV") || event.outcome.contains("SUCCESS") || event.outcome.contains("GRANTED")
    val isPending = event.outcome.contains("PROMPT")
    val outcomeColor = when {
        isPositive -> GVONETertiary
        isPending -> GVONEAccentOrange
        else -> GVONEAccentRed
    }

    Surface(
        color = Color(0xFF121722),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF1E2638)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(outcomeColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "${event.caller} • ${event.action}",
                        color = GVONETextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = event.permission,
                        color = GVONETextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    color = outcomeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = event.outcome,
                        color = outcomeColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(timeStr, color = GVONETextSecondary, fontSize = 10.sp)
            }
        }
    }
}
