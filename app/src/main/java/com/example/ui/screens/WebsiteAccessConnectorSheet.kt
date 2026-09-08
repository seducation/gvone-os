package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.connector.AccountDetectionStatus
import com.example.data.connector.WebsiteAccessContext
import com.example.data.connector.WebsiteAccessReport
import com.example.data.model.SitePermission

/**
 * GVONE Website Access & Account Connector Panel.
 * Automatically inspects the current active website/tab context:
 * - Website login detection (✓ Logged In, ○ Logged Out, ? Unknown)
 * - Account detection (username/email)
 * - Cookies count & management
 * - Permissions count & toggles
 * - Site data metrics (e.g. 8.4 MB)
 * - Environment-specific session isolation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteAccessConnectorSheet(
    report: WebsiteAccessReport,
    sitePermission: SitePermission?,
    onUpdatePermission: (SitePermission) -> Unit,
    onClearCookies: () -> Unit,
    onClearSiteData: () -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearCookiesConfirm by remember { mutableStateOf(false) }
    var showClearDataConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF50D111A), // Apple dark-mode glassmorphic background
        scrimColor = Color(0x77000000),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            // Drag Handle: Centered small rounded horizontal pill
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF475569))
                    .testTag("connector_sheet_drag_handle")
            )
        },
        modifier = modifier.testTag("website_access_connector_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Domain, Link Icon, and Environment Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E2638),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = "Connector",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = report.context.currentDomain,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Website Access & Account Connector",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF162032),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D56))
                ) {
                    Text(
                        text = "ENV: ${report.context.currentEnvironmentName.uppercase()}",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // 1. Account Detection Card (✓ Logged In, ○ Logged Out, ? Unknown)
            ConnectorSectionCard(
                title = "Account",
                icon = Icons.Outlined.AccountCircle,
                testTag = "connector_account_card"
            ) {
                when (val status = report.accountStatus) {
                    is AccountDetectionStatus.LoggedIn -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF064E3B), CircleShape)
                                    .border(1.dp, Color(0xFF10B981), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✓",
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Logged In",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF064E3B)
                                    ) {
                                        Text(
                                            text = "ACTIVE SESSION",
                                            color = Color(0xFF34D399),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = status.accountIdentifier,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("connector_account_identifier")
                                )
                                status.details?.let {
                                    Text(
                                        text = it,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    is AccountDetectionStatus.LoggedOut -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF451A03), CircleShape)
                                    .border(1.dp, Color(0xFFF59E0B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "○",
                                    color = Color(0xFFFCD34D),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Logged Out",
                                    color = Color(0xFFFCD34D),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = status.reason,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    is AccountDetectionStatus.Unknown -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF1E293B), CircleShape)
                                    .border(1.dp, Color(0xFF64748B), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "?",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "Unknown",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Login status cannot be reliably determined. Never guess login status.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Metrics Grid: Cookies, Permissions, Site Data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cookies Metric Box
                MetricCard(
                    title = "Cookies",
                    count = "${report.cookiesCount}",
                    subtitle = if (report.cookiesCount > 0) "Cookies in use" else "No cookies stored",
                    icon = Icons.Outlined.Cookie,
                    modifier = Modifier.weight(1f),
                    testTag = "connector_cookies_metric"
                )

                // Permissions Metric Box
                MetricCard(
                    title = "Permissions",
                    count = "${report.permissionsCount}",
                    subtitle = if (report.permissionsCount > 0) "${report.grantedPermissions.joinToString(", ")}" else "None requested",
                    icon = Icons.Outlined.Security,
                    modifier = Modifier.weight(1f),
                    testTag = "connector_permissions_metric"
                )

                // Site Data Metric Box
                MetricCard(
                    title = "Site Data",
                    count = report.siteDataFormatted,
                    subtitle = "HTML5 & Storage",
                    icon = Icons.Outlined.Storage,
                    modifier = Modifier.weight(1f),
                    testTag = "connector_site_data_metric"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Permissions Management Sub-panel
            ConnectorSectionCard(
                title = "Site Permissions",
                icon = Icons.Outlined.Shield,
                testTag = "connector_permissions_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val perm = sitePermission ?: SitePermission(domain = report.context.currentDomain)

                    PermissionToggleRow(
                        title = "Camera Access",
                        isAllowed = perm.cameraAllowed == true,
                        icon = Icons.Outlined.PhotoCamera,
                        onToggle = { allowed ->
                            onUpdatePermission(perm.copy(cameraAllowed = allowed))
                        }
                    )

                    PermissionToggleRow(
                        title = "Microphone Access",
                        isAllowed = perm.micAllowed == true,
                        icon = Icons.Outlined.Mic,
                        onToggle = { allowed ->
                            onUpdatePermission(perm.copy(micAllowed = allowed))
                        }
                    )

                    PermissionToggleRow(
                        title = "Location Access",
                        isAllowed = perm.locationAllowed == true,
                        icon = Icons.Outlined.LocationOn,
                        onToggle = { allowed ->
                            onUpdatePermission(perm.copy(locationAllowed = allowed))
                        }
                    )

                    PermissionToggleRow(
                        title = "Notifications",
                        isAllowed = perm.notificationsAllowed == true,
                        icon = Icons.Outlined.Notifications,
                        onToggle = { allowed ->
                            onUpdatePermission(perm.copy(notificationsAllowed = allowed))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Environment & Session Context
            ConnectorSectionCard(
                title = "Environment & Session Isolation",
                icon = Icons.Outlined.Layers,
                testTag = "connector_session_card"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ContextInfoRow("Active Tab ID", report.context.currentTabId)
                    ContextInfoRow("URL Endpoint", report.context.currentUrl)
                    ContextInfoRow("Domain Authority", report.context.currentDomain)
                    ContextInfoRow("Environment ID", report.context.currentEnvironmentId)
                    ContextInfoRow("Environment Name", report.context.currentEnvironmentName)
                    ContextInfoRow("Connection Type", if (report.isHttps) "TLS Encrypted (HTTPS)" else "Standard HTTP")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Actions Footer: Clear Cookies, Clear Site Data, Refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showClearCookiesConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF7F1D1D)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("connector_clear_cookies_button")
                ) {
                    Text("Clear Cookies", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { showClearDataConfirm = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFCA5A5)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF991B1B)),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("connector_clear_data_button")
                ) {
                    Text("Clear Site Data", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    modifier = Modifier.testTag("connector_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Confirmation dialog for clearing cookies
    if (showClearCookiesConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCookiesConfirm = false },
            title = { Text("Clear Cookies for ${report.context.currentDomain}?", color = Color.White) },
            text = { Text("This will remove all session cookies and log you out of this site.", color = Color(0xFF94A3B8)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearCookies()
                    showClearCookiesConfirm = false
                }) {
                    Text("Clear", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCookiesConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF161E2C)
        )
    }

    // Confirmation dialog for clearing site data
    if (showClearDataConfirm) {
        AlertDialog(
            onDismissRequest = { showClearDataConfirm = false },
            title = { Text("Clear Site Data for ${report.context.currentDomain}?", color = Color.White) },
            text = { Text("This will reset storage, cache, and offline databases for this origin.", color = Color(0xFF94A3B8)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearSiteData()
                    showClearDataConfirm = false
                }) {
                    Text("Clear All Data", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF161E2C)
        )
    }
}

@Composable
private fun ConnectorSectionCard(
    title: String,
    icon: ImageVector,
    testTag: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141C2B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222F43)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
            }

            content()
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    count: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF141C2B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222F43)),
        modifier = modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = count,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    isAllowed: Boolean,
    icon: ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isAllowed) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isAllowed) Color(0xFF38BDF8) else Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Switch(
            checked = isAllowed,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF1E293B)
            ),
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
private fun ContextInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}
