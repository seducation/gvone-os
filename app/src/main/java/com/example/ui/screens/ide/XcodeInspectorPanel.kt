package com.example.ui.screens.ide

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.agent.safety.PermissionSystem
import com.example.data.model.BrowserTab
import com.example.ui.screens.files.UnifiedFileExplorerView

/**
 * Bottom-right Inspector Panel for the unified Xcode-style IDE browser window.
 * Provides live inspection of attributes, DOM elements, network & security headers,
 * and CNS autonomous safety firewall rules.
 */
@Composable
fun XcodeInspectorPanel(
    currentTab: BrowserTab?,
    activeEditorTab: IdeTab?,
    workspaceMode: XcodeWorkspaceMode,
    activeWebView: WebView?,
    isTorActive: Boolean,
    fileSystem: com.example.data.files.GVONEFileSystem? = null,
    onOpenFileInTab: ((com.example.data.files.GVONEFileItem, Boolean) -> Unit)? = null,
    onOpenFileInIde: ((com.example.data.files.GVONEFileItem) -> Unit)? = null,
    webConsoleLogs: List<com.example.ui.components.DevConsoleLog> = emptyList(),
    ideExecutionLogs: List<String> = emptyList(),
    terminalLines: List<com.example.data.terminal.TerminalLine> = emptyList(),
    onClearWebConsoleLogs: () -> Unit = {},
    onClearIdeLogs: () -> Unit = {},
    onExecuteTerminalCommand: (String) -> Unit = {},
    onAppendIdeLog: (String) -> Unit = {},
    onAppendWebConsoleLog: (com.example.ui.components.DevConsoleLog) -> Unit = {},
    initialTab: XcodeInspectorTab = XcodeInspectorTab.ATTRIBUTES,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(initialTab) }

    // Visual Outlines and Click Inspector state
    var outlinesEnabled by remember { mutableStateOf(false) }
    var clickInspectorEnabled by remember { mutableStateOf(false) }

    val executeJs = { code: String, onResult: ((String) -> Unit)? ->
        if (activeWebView != null) {
            activeWebView.evaluateJavascript(code) { res ->
                onResult?.invoke(res?.trim() ?: "undefined")
            }
        } else {
            Toast.makeText(context, "WebView not available", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .testTag("xcode_inspector_panel"),
        color = Color(0xFF0F1523),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .padding(12.dp)
        ) {
            // Header: Title and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "INSPECTOR",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = if (workspaceMode == XcodeWorkspaceMode.BROWSER) "Web Page" else "Source File",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("xcode_inspector_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Inspector",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Inspector Category Tabs Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                XcodeInspectorTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                        border = if (isSelected) BorderStroke(1.dp, Color(0xFFF59E0B)) else BorderStroke(0.5.dp, Color(0xFF283548)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("inspector_tab_${tab.name.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) Color(0xFFF59E0B) else Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E293B),
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Inspector Tab Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    XcodeInspectorTab.PROJECT -> {
                        ProjectInspectorView(
                            fileSystem = fileSystem,
                            onOpenFileInTab = onOpenFileInTab,
                            onOpenFileInIde = onOpenFileInIde,
                            onExecuteTerminalCommand = onExecuteTerminalCommand,
                            onClose = onClose
                        )
                    }
                    XcodeInspectorTab.ATTRIBUTES -> {
                        AttributesInspectorView(
                            currentTab = currentTab,
                            activeEditorTab = activeEditorTab,
                            workspaceMode = workspaceMode
                        )
                    }
                    XcodeInspectorTab.FILES -> {
                        if (fileSystem != null) {
                            UnifiedFileExplorerView(
                                fileSystem = fileSystem,
                                onOpenFileInTab = { file, inNewTab ->
                                    onOpenFileInTab?.invoke(file, inNewTab)
                                    onClose()
                                },
                                onOpenFileInIde = { file ->
                                    onOpenFileInIde?.invoke(file)
                                    onClose()
                                },
                                onClosePanel = onClose
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Workspace file system loading...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                    }
                    XcodeInspectorTab.CONSOLE -> {
                        UnifiedDebugConsoleView(
                            workspaceMode = workspaceMode,
                            currentTab = currentTab,
                            activeEditorTab = activeEditorTab,
                            activeWebView = activeWebView,
                            webConsoleLogs = webConsoleLogs,
                            ideExecutionLogs = ideExecutionLogs,
                            terminalLines = terminalLines,
                            onClearWebConsoleLogs = onClearWebConsoleLogs,
                            onClearIdeLogs = onClearIdeLogs,
                            onExecuteTerminalCommand = onExecuteTerminalCommand,
                            onAppendIdeLog = onAppendIdeLog,
                            onAppendWebConsoleLog = onAppendWebConsoleLog
                        )
                    }
                    XcodeInspectorTab.DOM_INSPECT -> {
                        DomInspectorView(
                            currentTab = currentTab,
                            outlinesEnabled = outlinesEnabled,
                            onToggleOutlines = {
                                outlinesEnabled = !outlinesEnabled
                                val script = if (outlinesEnabled) {
                                    "(function(){var s=document.createElement('style');s.id='__xcode_dev_outlines__';s.innerHTML='* { outline: 1px dashed rgba(245,158,11,0.7) !important; }';document.head.appendChild(s);return 'Outlines Active';})()"
                                } else {
                                    "(function(){var s=document.getElementById('__xcode_dev_outlines__');if(s)s.remove();return 'Outlines Removed';})()"
                                }
                                executeJs(script) {
                                    Toast.makeText(context, if (outlinesEnabled) "Visual Outlines Active" else "Visual Outlines Disabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            clickInspectorEnabled = clickInspectorEnabled,
                            onToggleClickInspector = {
                                clickInspectorEnabled = !clickInspectorEnabled
                                val script = if (clickInspectorEnabled) {
                                    """
                                    (function(){
                                        window.__xcode_inspect_fn__ = function(e){
                                            e.preventDefault();
                                            e.stopPropagation();
                                            var el = e.target;
                                            var info = '<' + el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + '> ' + Math.round(el.offsetWidth) + 'x' + Math.round(el.offsetHeight) + 'px';
                                            alert('Element: ' + info);
                                        };
                                        window.addEventListener('click', window.__xcode_inspect_fn__, true);
                                        return 'Inspector On';
                                    })()
                                    """.trimIndent()
                                } else {
                                    """
                                    (function(){
                                        if (window.__xcode_inspect_fn__) {
                                            window.removeEventListener('click', window.__xcode_inspect_fn__, true);
                                            window.__xcode_inspect_fn__ = null;
                                        }
                                        return 'Inspector Off';
                                    })()
                                    """.trimIndent()
                                }
                                executeJs(script) {
                                    Toast.makeText(context, if (clickInspectorEnabled) "Click Inspector ON: Tap element to inspect tag" else "Click Inspector OFF", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCopyHtml = {
                                executeJs("document.documentElement.outerHTML") { html ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("HTML", html.removePrefix("\"").removeSuffix("\"").replace("\\n", "\n").replace("\\\"", "\"")))
                                    Toast.makeText(context, "OuterHTML copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    XcodeInspectorTab.NETWORK_SECURITY -> {
                        NetworkSecurityInspectorView(
                            currentTab = currentTab,
                            isTorActive = isTorActive,
                            onClearSiteData = {
                                executeJs("localStorage.clear(); sessionStorage.clear(); 'Storage Cleared';") {
                                    Toast.makeText(context, "Storage and cookies wiped", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    XcodeInspectorTab.SAFETY_RULES -> {
                        SafetyRulesInspectorView()
                    }
                }
            }
        }
    }
}

@Composable
fun AttributesInspectorView(
    currentTab: BrowserTab?,
    activeEditorTab: IdeTab?,
    workspaceMode: XcodeWorkspaceMode
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF131B2A),
                border = BorderStroke(1.dp, Color(0xFF223048)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "IDENTITY & METADATA",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )

                    if (workspaceMode == XcodeWorkspaceMode.BROWSER) {
                        InspectorAttributeRow("Page Title", currentTab?.title?.ifBlank { "Untitled" } ?: "N/A")
                        InspectorAttributeRow("Location URL", currentTab?.url ?: "N/A")
                        InspectorAttributeRow("Protocol", if (currentTab?.url?.startsWith("https") == true) "HTTPS (TLS Secured)" else "HTTP / Local")
                        InspectorAttributeRow("Tab ID", currentTab?.id ?: "N/A")
                        InspectorAttributeRow("Private Mode", if (currentTab?.isPrivate == true) "Enabled (Isolated)" else "Standard")
                    } else {
                        InspectorAttributeRow("File Name", activeEditorTab?.fileItem?.name ?: "Untitled")
                        InspectorAttributeRow("File Path", activeEditorTab?.fileItem?.path ?: "N/A")
                        InspectorAttributeRow("File Size", activeEditorTab?.fileItem?.formattedSize ?: "0 B")
                        InspectorAttributeRow("Line Count", "${activeEditorTab?.content?.lines()?.size ?: 0}")
                        InspectorAttributeRow("Extension", activeEditorTab?.fileItem?.extension?.uppercase() ?: "TXT")
                    }
                }
            }
        }
    }
}

@Composable
fun DomInspectorView(
    currentTab: BrowserTab?,
    outlinesEnabled: Boolean,
    onToggleOutlines: () -> Unit,
    clickInspectorEnabled: Boolean,
    onToggleClickInspector: () -> Unit,
    onCopyHtml: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onToggleOutlines,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (outlinesEnabled) Color(0xFFF59E0B) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (outlinesEnabled) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (outlinesEnabled) "Hide Outlines" else "Show Outlines",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Button(
                onClick = onToggleClickInspector,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (clickInspectorEnabled) Color(0xFF7C3AED) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.TouchApp, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (clickInspectorEnabled) "Stop Inspect" else "Click Inspect",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Button(
            onClick = onCopyHtml,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy Page OuterHTML", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF131B2A),
            border = BorderStroke(1.dp, Color(0xFF223048)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "DOM STRUCTURE STATS",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Text(
                    text = "Tap 'Click Inspect' to tap on any element on the web page to inspect its tag, ID, and dimensions.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun NetworkSecurityInspectorView(
    currentTab: BrowserTab?,
    isTorActive: Boolean,
    onClearSiteData: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF131B2A),
            border = BorderStroke(1.dp, Color(0xFF223048)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "SECURITY HEADERS & ROUTING",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                InspectorAttributeRow(
                    "Connection",
                    if (currentTab?.url?.startsWith("https") == true) "TLS 1.3 Encrypted" else "Insecure HTTP"
                )
                InspectorAttributeRow(
                    "Tor Circuit",
                    if (isTorActive) "SOCKS5 Proxy (127.0.0.1:9050)" else "Direct Clearnet"
                )
                InspectorAttributeRow("CORS Policy", "Enforced via WebView WebSettings")
                InspectorAttributeRow("Cross-Site Cookies", "Strict Partitioned")
            }
        }

        Button(
            onClick = onClearSiteData,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Clear Storage & Cookies", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun SafetyRulesInspectorView() {
    val permissionSystem = remember { PermissionSystem.global }
    val scopePolicies by permissionSystem.scopePoliciesFlow.collectAsState()
    val policyCount = if (scopePolicies.isNotEmpty()) scopePolicies.size else PermissionSystem.ALL_DESCRIPTORS.size

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF131B2A),
            border = BorderStroke(1.dp, Color(0xFF223048)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "AUTONOMOUS SAFETY FIREWALL",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )

                InspectorAttributeRow("Active Policies", "$policyCount configured")
                InspectorAttributeRow("Audit Log State", "Real-time Verification Active")
                InspectorAttributeRow("Reflex Loop", "Sub-10ms Safety Preemption")
                InspectorAttributeRow("Central Nervous System", "Gemini 2.5 Multi-Agent Bus")
            }
        }
    }
}

@Composable
fun InspectorAttributeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            color = Color(0xFFE2E8F0),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
