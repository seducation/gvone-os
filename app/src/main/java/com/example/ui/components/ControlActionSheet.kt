package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.connector.WebsiteAccessContext
import com.example.data.model.BrowserSettings
import kotlinx.coroutines.launch

/**
 * Native Apple-inspired Dark Mode Control Center Bottom Sheet.
 * Layout Hierarchy:
 * 1. Terminal Screen in Big Screen (Top computer console with CLI options)
 * 2. Horizontal UI (Photos, Camera, Avatar, Connector, Terminal, Bridge)
 * 3. Bridge Options Below It Like Previous (Bidirectional bridge, URL, presets, auto-load)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlActionSheet(
    actions: List<ControlActionItem>,
    websiteContext: WebsiteAccessContext?,
    settings: BrowserSettings? = null,
    onUpdateSettings: ((BrowserSettings) -> Unit)? = null,
    onOpenTerminal: () -> Unit = {},
    onOpenCommandManager: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    var tempUrl by remember(settings?.autoLoadTargetUrl) {
        mutableStateOf(settings?.autoLoadTargetUrl ?: "https://charassist-c4uzg7hb.manus.space")
    }
    var tempAutoLoad by remember(settings?.autoLoadTargetOnFocus) {
        mutableStateOf(settings?.autoLoadTargetOnFocus ?: false)
    }
    var tempBridgeEnabled by remember(settings?.bidirectionalBridgeEnabled) {
        mutableStateOf(settings?.bidirectionalBridgeEnabled ?: true)
    }
    var tempBridgeApplyToAll by remember(settings?.bridgeApplyToAllWebsites) {
        mutableStateOf(settings?.bridgeApplyToAllWebsites ?: false)
    }

    val presetSites = listOf(
        Pair("GVONE CharAssist", "https://charassist-c4uzg7hb.manus.space"),
        Pair("RSS Group Feed", "https://rssgroupfeed-jaelvwfd.manus.space"),
        Pair("DuckDuckGo", "https://duckduckgo.com"),
        Pair("Google", "https://www.google.com"),
        Pair("Brave Search", "https://search.brave.com")
    )

    // Override the bridge action in actions list to smoothly scroll to the Bridge section
    val effectiveActions = remember(actions) {
        actions.map { item ->
            if (item.id == ControlActionRegistry.ACTION_BRIDGE) {
                item.copy(onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                })
            } else {
                item
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xF50D111A), // Dark mode glassmorphic tone
        scrimColor = Color(0x77000000),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF475569))
                    .testTag("control_action_sheet_drag_handle")
            )
        },
        modifier = modifier.testTag("control_action_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .align(Alignment.CenterHorizontally)
                .verticalScroll(scrollState)
                .padding(bottom = 36.dp)
        ) {
            // Header: Title, Website Indicator & Close Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Control & Terminal Center",
                        color = Color(0xFFF1F5F9),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (websiteContext != null && websiteContext.currentDomain.isNotBlank() && websiteContext.currentDomain != "Start Page") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E2638),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color(0xFF38BDF8), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = websiteContext.currentDomain,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // =========================================================================
            // 1. TERMINAL SCREEN IN BIG SCREEN (ABOVE THE HORIZONTAL UI)
            // =========================================================================
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF090D14),
                border = BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF818CF8)))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .testTag("bridge_dialog_computer_terminal_screen")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    // Monitor Screen Top Window Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF10B981)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.DesktopWindows,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "TERMINAL CLI CONSOLE",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        val isPinnedToAddressBar = settings?.terminalPinnedAboveAddressBar == true
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPinnedToAddressBar) Color(0x3338BDF8) else Color(0xFF1E293B),
                            border = BorderStroke(1.dp, if (isPinnedToAddressBar) Color(0xFF38BDF8) else Color(0xFF334155)),
                            modifier = Modifier
                                .clickable {
                                    val newPinned = !isPinnedToAddressBar
                                    val updated = settings?.copy(
                                        terminalPinnedAboveAddressBar = newPinned
                                    ) ?: BrowserSettings(terminalPinnedAboveAddressBar = newPinned)
                                    onUpdateSettings?.invoke(updated)
                                    Toast.makeText(
                                        context,
                                        if (newPinned) "Terminal pinned: appears on top of address bar while writing" else "Terminal unpinned from address bar",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .testTag("control_sheet_pin_terminal_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pin to Address Bar",
                                    tint = if (isPinnedToAddressBar) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isPinnedToAddressBar) "PINNED TO BAR" else "PIN TO BAR",
                                    color = if (isPinnedToAddressBar) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Text(
                        text = "Pin terminal above address bar while writing",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0D121D),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenTerminal()
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "gvone@browser",
                                    color = Color(0xFF4ADE80),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = ":",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "~",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "$ bridge status --interactive",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "● Bridge Engine: ACTIVE (HTTP/WS v2.4)\n" +
                                        "● Quick Action: Tap here to open full Terminal CLI",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Terminal Action Row (Open Terminal CLI + Commands)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenTerminal()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open Terminal CLI",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenCommandManager()
                            },
                            modifier = Modifier.height(38.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Commands",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 2. HORIZONTAL UI (PHOTOS, CAMERA, AVATAR, CONNECTOR, TERMINAL, BRIDGE)
            // =========================================================================
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "QUICK ACTIONS",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )

                    Text(
                        text = "Swipe for more →",
                        color = Color(0xFF475569),
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                HorizontalActionList(
                    actions = effectiveActions,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Optional Quick Connector & Terminal Context Card underneath
            if (websiteContext != null && websiteContext.currentDomain.isNotBlank() && websiteContext.currentDomain != "Start Page") {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF131926),
                    border = BorderStroke(1.dp, Color(0xFF222F43)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .clickable {
                            actions.firstOrNull { it.id == ControlActionRegistry.ACTION_CONNECTOR }?.onClick?.invoke()
                        }
                        .testTag("control_sheet_quick_connector_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Website Access Connector",
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Login status, cookies, permissions & site data",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "Open",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================================
            // 3. BRIDGE OPTIONS BELOW IT LIKE PREVIOUS
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SyncAlt,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Bidirectional Bridge & Target Controls",
                        color = Color(0xFFF8FAFC),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Configure the bidirectional bridge architecture, input router, and address bar behaviors.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                // 1. Bidirectional Bridge / InputRouter Master Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B2332),
                    border = BorderStroke(
                        1.dp,
                        if (tempBridgeEnabled) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0x33FFFFFF)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.SyncAlt,
                                    contentDescription = null,
                                    tint = if (tempBridgeEnabled) Color(0xFF38BDF8) else Color(0xFF8E9BAE),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Bidirectional Bridge / InputRouter",
                                    color = Color(0xFFF8FAFC),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Directly route address queries to webpage input/chat without reloading",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = tempBridgeEnabled,
                            onCheckedChange = { tempBridgeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF38BDF8)
                            )
                        )
                    }
                }

                // 2. Apply to All Websites (Universal Bridge) Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B2332),
                    border = BorderStroke(
                        1.dp,
                        if (tempBridgeApplyToAll && tempBridgeEnabled) Color(0xFF38BDF8).copy(alpha = 0.5f) else Color(0x33FFFFFF)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = if (tempBridgeApplyToAll && tempBridgeEnabled) Color(0xFF38BDF8) else Color(0xFF8E9BAE),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Apply Bridge to All Websites",
                                    color = if (tempBridgeEnabled) Color(0xFFF8FAFC) else Color(0xFF6B7A90),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (tempBridgeApplyToAll) "Active for all websites & AI web apps" else "Active only for trusted GVONE web apps",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = tempBridgeApplyToAll,
                            enabled = tempBridgeEnabled,
                            onCheckedChange = { tempBridgeApplyToAll = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF38BDF8)
                            )
                        )
                    }
                }

                // 3. Auto-load on Tap Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B2332),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "Auto-load Target on Tap",
                                color = Color(0xFFF8FAFC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Automatically load target website when focusing address bar",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = tempAutoLoad,
                            onCheckedChange = { tempAutoLoad = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF38BDF8)
                            )
                        )
                    }
                }

                // 4. Target Website URL Input
                Column {
                    Text(
                        text = "TARGET WEBSITE URL",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        placeholder = { Text("https://...", color = Color(0xFF6B7A90)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF161D2A),
                            unfocusedContainerColor = Color(0xFF161D2A),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF2C394E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("target_url_input_field")
                    )
                }

                // 5. Preset Website Chips
                Column {
                    Text(
                        text = "QUICK PRESETS",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetSites.take(3).forEach { (name, url) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (tempUrl == url) Color(0xFF38BDF8).copy(alpha = 0.25f) else Color(0xFF1C2433),
                                border = BorderStroke(
                                    1.dp,
                                    if (tempUrl == url) Color(0xFF38BDF8) else Color(0x22FFFFFF)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { tempUrl = url }
                            ) {
                                Text(
                                    text = name,
                                    color = if (tempUrl == url) Color(0xFF38BDF8) else Color(0xFFCCD6E5),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 6. Save & Apply Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val finalUrl = if (tempUrl.isNotBlank() && !tempUrl.startsWith("http://") && !tempUrl.startsWith("https://")) {
                                "https://$tempUrl"
                            } else {
                                tempUrl
                            }
                            if (settings != null && onUpdateSettings != null) {
                                onUpdateSettings(
                                    settings.copy(
                                        autoLoadTargetOnFocus = tempAutoLoad,
                                        autoLoadTargetUrl = finalUrl,
                                        bidirectionalBridgeEnabled = tempBridgeEnabled,
                                        bridgeApplyToAllWebsites = tempBridgeApplyToAll
                                    )
                                )
                            }
                            Toast.makeText(context, "Bridge & Target settings applied", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("Done", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Horizontally scrollable row of dark gray squircle action cards.
 * User swipes horizontally to reveal all actions.
 */
@Composable
fun HorizontalActionList(
    actions: List<ControlActionItem>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        actions.forEach { action ->
            ControlActionCard(action = action)
        }
    }
}

/**
 * Individual Apple-inspired dark gray squircle card.
 * Minimalist white outline icon positioned upper/center, centered text underneath.
 */
@Composable
fun ControlActionCard(
    action: ControlActionItem,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = action.onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF161E2C), // Dark gray squircle
        border = BorderStroke(1.dp, Color(0xFF233044)),
        modifier = modifier
            .width(84.dp)
            .height(96.dp)
            .clip(RoundedCornerShape(18.dp))
            .testTag("control_action_card_${action.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Upper/Center: Icon
            Icon(
                imageVector = action.icon,
                contentDescription = action.contentDescription,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )

            // Bottom: Text centered underneath
            Text(
                text = action.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.1).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
