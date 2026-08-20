package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserSettings
import com.example.data.model.SearchEngineType
import com.example.data.tor.TorConnectionState
import com.example.data.tor.TorStatus
import com.example.data.tor.TorTestResult
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: BrowserSettings,
    torStatus: TorStatus = TorStatus(),
    torTestResult: TorTestResult? = null,
    isTorTesting: Boolean = false,
    onToggleTor: () -> Unit = {},
    onTestTor: () -> Unit = {},
    onRetryTor: () -> Unit = {},
    onSettingsChanged: (BrowserSettings) -> Unit,
    onClearBrowsingData: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSearchEngineDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = GVONETextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = GVONETextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F141D)
                )
            )
        },
        containerColor = Color(0xFF0B0E14),
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Search settings bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search settings", color = GVONETextSecondary, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = GVONETextSecondary
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .testTag("search_settings_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF161C27),
                        unfocusedContainerColor = Color(0xFF161C27),
                        focusedBorderColor = GVONEPrimary,
                        unfocusedBorderColor = Color(0xFF263245),
                        focusedTextColor = GVONETextPrimary,
                        unfocusedTextColor = GVONETextPrimary
                    ),
                    singleLine = true
                )
            }

            // Section: Network & Privacy / TOR Mode
            item {
                Text(
                    text = "Network & Privacy / TOR Mode",
                    color = GVONESecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
                )
            }

            // Main TOR Control Card with Toggle & Status
            item {
                TorControlCard(
                    settings = settings,
                    torStatus = torStatus,
                    torTestResult = torTestResult,
                    isTorTesting = isTorTesting,
                    onToggleTor = onToggleTor,
                    onTestTor = onTestTor,
                    onRetryTor = onRetryTor,
                    onUpdateProxyConfig = { host, port ->
                        onSettingsChanged(settings.copy(torProxyHost = host, torProxyPort = port))
                    }
                )
            }

            // Section: Basics
            item {
                Text(
                    text = "Basics",
                    color = GVONEPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }

            // Search engine item
            item {
                SettingsRowItem(
                    icon = Icons.Rounded.Search,
                    title = "Search engine",
                    subtitle = settings.searchEngine.displayName,
                    onClick = { showSearchEngineDialog = true }
                )
            }

            // Address bar layout
            item {
                SettingsSwitchItem(
                    icon = Icons.Rounded.Web,
                    title = "Address bar",
                    subtitle = if (settings.addressBarBottom) "Bottom floating pill" else "Top address bar",
                    checked = settings.addressBarBottom,
                    onCheckedChange = { onSettingsChanged(settings.copy(addressBarBottom = it)) }
                )
            }

            // Section: Privacy and security
            item {
                Text(
                    text = "Privacy and security",
                    color = GVONEPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Rounded.Shield,
                    title = "Tracking protection",
                    subtitle = "Block known trackers and malicious scripts",
                    checked = settings.trackingProtection,
                    onCheckedChange = { onSettingsChanged(settings.copy(trackingProtection = it)) }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Rounded.Block,
                    title = "Block intrusive popups",
                    subtitle = "Prevents new windows and auto-redirects",
                    checked = settings.blockPopups,
                    onCheckedChange = { onSettingsChanged(settings.copy(blockPopups = it)) }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Rounded.Lock,
                    title = "Always use HTTPS",
                    subtitle = "Upgrade connections and warn before HTTP",
                    checked = settings.forceHttps,
                    onCheckedChange = { onSettingsChanged(settings.copy(forceHttps = it)) }
                )
            }

            item {
                SettingsRowItem(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Clear browsing data",
                    subtitle = "Clear history, cookies, cache and site data",
                    onClick = { showClearDataDialog = true }
                )
            }

            // Section: GVONE AI & Search Intelligence
            item {
                Text(
                    text = "GVONE AI Search Intelligence",
                    color = GVONETertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Automatic AI Search Synthesis",
                    subtitle = "Trigger AI Answer cards for natural language queries",
                    checked = settings.aiSearchAutoTrigger,
                    onCheckedChange = { onSettingsChanged(settings.copy(aiSearchAutoTrigger = it)) }
                )
            }
        }
    }

    // Search Engine Selection Dialog
    if (showSearchEngineDialog) {
        AlertDialog(
            onDismissRequest = { showSearchEngineDialog = false },
            title = { Text("Select Search Engine", color = GVONETextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchEngineType.entries.forEach { engine ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onSettingsChanged(settings.copy(searchEngine = engine))
                                    showSearchEngineDialog = false
                                },
                            color = if (settings.searchEngine == engine) GVONEPrimary.copy(alpha = 0.2f) else Color(0xFF161F2E)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = settings.searchEngine == engine,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(engine.displayName, color = GVONETextPrimary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSearchEngineDialog = false }) {
                    Text("Done", color = GVONEPrimary)
                }
            },
            containerColor = Color(0xFF141923)
        )
    }

    // Clear Data Confirmation Dialog
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear Browsing Data", color = GVONETextPrimary) },
            text = { Text("This will delete history, cookies, and cached data from this device.", color = GVONETextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onClearBrowsingData()
                    showClearDataDialog = false
                }) {
                    Text("Clear Now", color = GVONEAccentRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            },
            containerColor = Color(0xFF141923)
        )
    }
}

/**
 * Dedicated TOR Control Component with:
 * - Simple toggle: TOR "ON / OFF"
 * - Four explicit statuses: "Connected", "Connecting…", "Disconnected", "Connection Error"
 * - "Test TOR Connection" action with live diagnostic readout
 * - Reconnect / Retry controls & Fail-Closed protection indicator
 */
@Composable
fun TorControlCard(
    settings: BrowserSettings,
    torStatus: TorStatus,
    torTestResult: TorTestResult?,
    isTorTesting: Boolean,
    onToggleTor: () -> Unit,
    onTestTor: () -> Unit,
    onRetryTor: () -> Unit,
    onUpdateProxyConfig: (host: String, port: Int) -> Unit = { _, _ -> }
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isEnabled = settings.torEnabled
    val connectionState = torStatus.state
    var showPortDialog by remember { mutableStateOf(false) }
    var hostInput by remember(settings.torProxyHost) { mutableStateOf(settings.torProxyHost) }
    var portInput by remember(settings.torProxyPort) { mutableStateOf(settings.torProxyPort.toString()) }

    val launchOrbot = {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("org.torproject.android")
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                val marketIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=org.torproject.android"))
                try {
                    context.startActivity(marketIntent)
                } catch (e: Exception) {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://orbot.app/")))
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("tor_control_card"),
        color = Color(0xFF141A26),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled && connectionState == TorConnectionState.CONNECTED) GVONESecondary.copy(alpha = 0.5f)
            else if (isEnabled && connectionState == TorConnectionState.ERROR) GVONEAccentRed.copy(alpha = 0.5f)
            else Color(0xFF232C3D)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row with TOR Icon, Title, and ON/OFF Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled) GVONESecondary.copy(alpha = 0.2f)
                                else Color(0xFF20293A)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = "TOR Network",
                            tint = if (isEnabled) GVONESecondary else Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "TOR Mode",
                            color = GVONETextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isEnabled) "Active SOCKS5 (${torStatus.socksProxyHost}:${torStatus.socksProxyPort})" else "OFF (Direct routing)",
                            color = if (isEnabled) GVONESecondary else GVONETextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Simple ON / OFF Switch Toggle
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggleTor() },
                    modifier = Modifier.testTag("tor_toggle_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = GVONESecondary,
                        uncheckedThumbColor = GVONETextSecondary,
                        uncheckedTrackColor = Color(0xFF263245)
                    )
                )
            }

            HorizontalDivider(color = Color(0xFF232C3D), thickness = 1.dp)

            // Connection Status Display Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Connection Status",
                    color = GVONETextSecondary,
                    fontSize = 13.sp
                )

                // Current TOR Connection Status Pill
                TorStatusBadge(connectionState = connectionState, isEnabled = isEnabled)
            }

            // Diagnostics and Error Message if applicable
            if (isEnabled && connectionState == TorConnectionState.ERROR) {
                Surface(
                    color = GVONEAccentRed.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = GVONEAccentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tor Daemon Not Detected",
                                color = GVONEAccentRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = torStatus.lastError ?: "No active SOCKS5 proxy listening on ${settings.torProxyHost}:${settings.torProxyPort}. Direct traffic is held to protect anonymity.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = launchOrbot,
                                modifier = Modifier.weight(1f).testTag("tor_launch_orbot_settings_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = GVONESecondary),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Orbot", fontSize = 11.sp)
                            }

                            Button(
                                onClick = onRetryTor,
                                modifier = Modifier.weight(1f).testTag("tor_retry_connection_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry Proxy", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Actions & Proxy Configuration
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isEnabled) {
                    // "Test TOR Connection" Action Button
                    Button(
                        onClick = onTestTor,
                        enabled = !isTorTesting,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_tor_connection_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GVONESecondary.copy(alpha = 0.85f),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        if (isTorTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.NetworkCheck,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Test Connection", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showPortDialog = true },
                    modifier = Modifier.then(if (isEnabled) Modifier.weight(1f) else Modifier.fillMaxWidth()),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GVONETextPrimary),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = GVONEPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Proxy Config (${settings.torProxyPort})", fontSize = 13.sp)
                }
            }

            // Display Test Result Card if available
            AnimatedVisibility(
                visible = isEnabled && torTestResult != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (torTestResult != null) {
                    Surface(
                        color = if (torTestResult.isSuccessful) Color(0xFF10241B) else Color(0xFF261418),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (torTestResult.isSuccessful) Color(0xFF10B981) else GVONEAccentRed
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("tor_test_result_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (torTestResult.isSuccessful) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                                        contentDescription = null,
                                        tint = if (torTestResult.isSuccessful) Color(0xFF10B981) else GVONEAccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (torTestResult.isSuccessful) "Test Passed" else "Test Failed",
                                        color = if (torTestResult.isSuccessful) Color(0xFF10B981) else GVONEAccentRed,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (torTestResult.latencyMs > 0) {
                                    Text(
                                        text = "${torTestResult.latencyMs} ms",
                                        color = GVONETextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = torTestResult.message,
                                color = Color(0xFFF1F5F9),
                                fontSize = 12.sp
                            )
                            if (torTestResult.ipAddress != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Observed IP / Proxy: ${torTestResult.ipAddress}",
                                    color = GVONETextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Platform Notes & DNS Routing Details
            Surface(
                color = Color(0xFF10151F),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEnabled) "SOCKS5 proxy active (${settings.torProxyHost}:${settings.torProxyPort}). DNS requests resolved through remote proxy to prevent leakage."
                        else "Direct network active. Enable TOR to route web and search traffic through SOCKS5 proxy.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }

    if (showPortDialog) {
        AlertDialog(
            onDismissRequest = { showPortDialog = false },
            containerColor = Color(0xFF161C27),
            title = {
                Text(
                    text = "Tor Proxy Configuration",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Specify your local or remote Tor SOCKS5 proxy daemon details. Orbot default is 127.0.0.1:9050.",
                        color = GVONETextSecondary,
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Proxy Host IP / Hostname") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONESecondary,
                            unfocusedBorderColor = Color(0xFF263245)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("tor_proxy_host_input")
                    )

                    OutlinedTextField(
                        value = portInput,
                        onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Proxy Port (Default: 9050)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONESecondary,
                            unfocusedBorderColor = Color(0xFF263245)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("tor_proxy_port_input")
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("9050" to "Orbot", "9150" to "Tor Browser", "8118" to "HTTP").forEach { (p, label) ->
                            OutlinedButton(
                                onClick = { portInput = p },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (portInput == p) GVONESecondary else Color(0xFF94A3B8)
                                )
                            ) {
                                Text("$p ($label)", fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedPort = portInput.toIntOrNull() ?: 9050
                        val cleanHost = hostInput.trim().ifBlank { "127.0.0.1" }
                        onUpdateProxyConfig(cleanHost, parsedPort)
                        showPortDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                    modifier = Modifier.testTag("save_proxy_config_btn")
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPortDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Renders the exact four specified connection statuses:
 * - "Connected"
 * - "Connecting…"
 * - "Disconnected"
 * - "Connection Error"
 */
@Composable
fun TorStatusBadge(
    connectionState: TorConnectionState,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val (statusText, statusColor, bgColor, icon) = when {
        !isEnabled || connectionState == TorConnectionState.DISCONNECTED -> {
            Quadruple("Disconnected", Color(0xFF94A3B8), Color(0xFF1E293B), Icons.Rounded.PowerOff)
        }
        connectionState == TorConnectionState.CONNECTING -> {
            Quadruple("Connecting…", Color(0xFFF59E0B), Color(0xFF3B2912), Icons.Rounded.Sync)
        }
        connectionState == TorConnectionState.CONNECTED -> {
            Quadruple("Connected", Color(0xFF10B981), Color(0xFF0F392B), Icons.Rounded.CheckCircle)
        }
        connectionState == TorConnectionState.ERROR -> {
            Quadruple("Connection Error", GVONEAccentRed, Color(0xFF381419), Icons.Rounded.Error)
        }
        else -> {
            Quadruple("Disconnected", Color(0xFF94A3B8), Color(0xFF1E293B), Icons.Rounded.PowerOff)
        }
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.testTag("tor_status_badge_${statusText.replace(" ", "_").lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = Color(0xFF141A26)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GVONEPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, color = GVONETextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(subtitle, color = GVONETextSecondary, fontSize = 12.sp)
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = GVONETextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = Color(0xFF141A26)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GVONEPrimary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(title, color = GVONETextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(subtitle, color = GVONETextSecondary, fontSize = 12.sp)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GVONEPrimary,
                    uncheckedThumbColor = GVONETextSecondary,
                    uncheckedTrackColor = Color(0xFF263245)
                )
            )
        }
    }
}
