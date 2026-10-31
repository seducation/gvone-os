package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.ui.screens.ide.XcodeWorkspaceMode
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Model for console logs captured from website execution.
 */
data class DevConsoleLog(
    val message: String,
    val level: ConsoleMessage.MessageLevel = ConsoleMessage.MessageLevel.LOG,
    val sourceId: String? = null,
    val lineNumber: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DevBarTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    CONSOLE("Console", Icons.Rounded.Terminal),
    DOM_INSPECT("DOM", Icons.Rounded.Code),
    NETWORK("Network", Icons.Rounded.Lan),
    STORAGE("Storage", Icons.Rounded.Storage),
    DEVICE("Device", Icons.Rounded.Devices)
}

/**
 * Developer Bar placed directly on top of the active website.
 * Can be toggled on/off, expanded/collapsed, and equipped with live DevTools.
 */
@Composable
fun WebsiteDeveloperBar(
    tab: BrowserTab,
    activeWebView: WebView?,
    isTorActive: Boolean,
    onToggleOff: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenIde: (() -> Unit)? = null,
    workspaceMode: XcodeWorkspaceMode = XcodeWorkspaceMode.BROWSER,
    onToggleWorkspaceMode: ((XcodeWorkspaceMode) -> Unit)? = null,
    activeFileName: String? = null,
    isFileDirty: Boolean = false,
    onOpenInspector: (() -> Unit)? = null,
    onOpenConsole: (() -> Unit)? = null,
    onOpenNavigator: (() -> Unit)? = null,
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    onGoBack: (() -> Unit)? = null,
    onGoForward: (() -> Unit)? = null,
    onReload: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onSaveFile: (() -> Unit)? = null,
    onFormatCode: (() -> Unit)? = null,
    onAddressClick: (() -> Unit)? = null,
    onOpenProblems: (() -> Unit)? = null,
    problemCount: Int = 0,
    onZoomIn: (() -> Unit)? = null,
    onZoomOut: (() -> Unit)? = null,
    onResetZoom: (() -> Unit)? = null,
    onRotate: (() -> Unit)? = null,
    onTogglePreviewMode: (() -> Unit)? = null,
    isPreviewMode: Boolean = false,
    onTogglePlayPause: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    onBeautifyData: (() -> Unit)? = null,
    onMinifyData: (() -> Unit)? = null,
    onCopyData: (() -> Unit)? = null,
    onShareFile: (() -> Unit)? = null,
    onSearchDoc: (() -> Unit)? = null,
    onInvertCanvas: (() -> Unit)? = null,
    onOpenMcp: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(DevBarTab.CONSOLE) }

    // Visual Outlines state
    var outlinesEnabled by remember { mutableStateOf(false) }
    var clickInspectorEnabled by remember { mutableStateOf(false) }

    // Console logs & JS evaluation state
    val consoleLogs = remember { mutableStateListOf<DevConsoleLog>() }
    var jsInput by remember { mutableStateOf("document.title") }
    var jsEvaluationOutput by remember { mutableStateOf<String?>(null) }
    var logFilter by remember { mutableStateOf<ConsoleMessage.MessageLevel?>(null) }

    // Page metadata
    var pageTitle by remember(tab.title) { mutableStateOf(tab.title.ifBlank { "Untitled Page" }) }
    var pageUrl by remember(tab.url) { mutableStateOf(tab.url) }

    // Host domain display
    val hostDomain = remember(tab.url) {
        try {
            val uri = URI(tab.url)
            uri.host ?: tab.url
        } catch (_: Exception) {
            tab.url
        }
    }

    val isHttps = tab.url.startsWith("https://", ignoreCase = true)

    // Execute JavaScript helper
    val executeJs = { code: String, onResult: ((String) -> Unit)? ->
        if (activeWebView != null) {
            activeWebView.evaluateJavascript(code) { result ->
                val cleanResult = result?.trim() ?: "undefined"
                onResult?.invoke(cleanResult)
            }
        } else {
            Toast.makeText(context, "WebView not ready", Toast.LENGTH_SHORT).show()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("website_developer_bar"),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column {
            // ================= TOP UNIVERSAL BAR (Browser + IDE Unified) =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val isCodeMode = (workspaceMode == XcodeWorkspaceMode.CODE_EDITOR)

                var isCategoryDropdownOpen by remember { mutableStateOf(false) }

                // 1. LEFT CONTROLS: Category Dropdown Menu & Active Category Tools
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Category Dropdown Trigger (Replaces top-left sidebar button)
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF161F32),
                            border = BorderStroke(1.dp, Color(0xFF2C3E55)),
                            modifier = Modifier
                                .height(30.dp)
                                .clickable { isCategoryDropdownOpen = true }
                                .testTag("dev_bar_category_dropdown_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 7.dp)
                            ) {
                                Icon(
                                    imageVector = workspaceMode.icon,
                                    contentDescription = "Workspace Mode",
                                    tint = when (workspaceMode) {
                                        XcodeWorkspaceMode.CODE_EDITOR -> Color(0xFFA855F7)
                                        XcodeWorkspaceMode.BROWSER -> Color(0xFF38BDF8)
                                        XcodeWorkspaceMode.BOT_CHAT -> Color(0xFF34D399)
                                        XcodeWorkspaceMode.PDF_VIEWER -> Color(0xFFEF4444)
                                        XcodeWorkspaceMode.IMAGE_VIEWER -> Color(0xFFEC4899)
                                        XcodeWorkspaceMode.MARKDOWN -> Color(0xFF38BDF8)
                                        XcodeWorkspaceMode.DATA_INSPECTOR -> Color(0xFF10B981)
                                        XcodeWorkspaceMode.MEDIA_PLAYER -> Color(0xFFF97316)
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = workspaceMode.tag.ifEmpty { workspaceMode.label },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.Rounded.ArrowDropDown,
                                    contentDescription = "Open Categories",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isCategoryDropdownOpen,
                            onDismissRequest = { isCategoryDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(12.dp))
                                .widthIn(min = 230.dp)
                        ) {
                            Text(
                                text = "DEVELOPMENT WORKSPACE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )

                            // 1. Xcode Code Editor
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Xcode Code Editor", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Monospace IDE, save, format & diagnostics", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Code, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.CODE_EDITOR) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.CODE_EDITOR)
                                }
                            )

                            // 2. Website Browser
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Website Browser", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Live web runtime, DOM & DevTools", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Public, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.BROWSER) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.BROWSER)
                                }
                            )

                            // 3. AI Copilot & Bots
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("AI Copilot & Bots", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Grok & Claude AI assistants", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.BOT_CHAT) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.BOT_CHAT)
                                }
                            )

                            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "FILE FORMATS & VIEWERS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )

                            // 4. PDF Document Viewer
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("PDF Document", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Multi-page PDF reader & zoom", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.PDF_VIEWER) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.PDF_VIEWER)
                                }
                            )

                            // 5. Image Viewer
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Image Viewer", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("PNG, JPG, WebP, SVG, GIF, BMP", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Image, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.IMAGE_VIEWER) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.IMAGE_VIEWER)
                                }
                            )

                            // 6. Markdown & Documents
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Markdown & Docs", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Live formatted preview & editor", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Description, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.MARKDOWN) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.MARKDOWN)
                                }
                            )

                            // 7. Media Player (Audio & Video)
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Media Player", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Audio (MP3/WAV) & Video (MP4/WEBM)", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.MEDIA_PLAYER) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.MEDIA_PLAYER)
                                }
                            )

                            // 8. JSON & Data Inspector
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("JSON & Data Inspector", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Syntax tree, beautifier & schema", fontSize = 10.sp, color = Color(0xFF10B981))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.DataObject, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (workspaceMode == XcodeWorkspaceMode.DATA_INSPECTOR) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    }
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.DATA_INSPECTOR)
                                }
                            )

                            HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 4.dp))

                            Text(
                                text = "SYSTEM PANELS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )

                            // 9. Files Navigator
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("File Navigator & Storage", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Browse projects, files & tabs", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.FolderSpecial, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onOpenNavigator?.invoke()
                                }
                            )

                            // 10. Terminal Shell
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Linux Terminal & CLI", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Interactive bash/sh shell & scripts", fontSize = 10.sp, color = Color(0xFF34D399))
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    isCategoryDropdownOpen = false
                                    onOpenConsole?.invoke()
                                }
                            )

                            // 11. MCP & Context Protocol
                            if (onOpenMcp != null) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("MCP & AI Context Protocol", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            Text("Model context server & tool bridges", fontSize = 10.sp, color = Color(0xFF38BDF8))
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Hub, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        isCategoryDropdownOpen = false
                                        onOpenMcp.invoke()
                                    }
                                )
                            }

                            // 12. Browser & IDE Settings
                            if (onOpenSettings != null) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Settings & Preferences", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            Text("DevTools, Tor proxy, search & layout", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        isCategoryDropdownOpen = false
                                        onOpenSettings.invoke()
                                    }
                                )
                            }
                        }
                    }

                    // Mode-Specific Left Tools
                    when (workspaceMode) {
                        XcodeWorkspaceMode.CODE_EDITOR -> {
                            // Save button
                            IconButton(
                                onClick = { onSaveFile?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_save_file_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Save,
                                    contentDescription = "Save File",
                                    tint = if (isFileDirty) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Format button
                            if (onFormatCode != null) {
                                IconButton(
                                    onClick = onFormatCode,
                                    modifier = Modifier
                                        .size(30.dp)
                                        .testTag("dev_bar_format_code_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FormatPaint,
                                        contentDescription = "Format Code",
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Problems & Diagnostics button with error count badge
                            IconButton(
                                onClick = { onOpenProblems?.invoke() ?: onOpenInspector?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_problems_btn")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (problemCount > 0) {
                                            Badge(
                                                containerColor = Color(0xFFEF4444),
                                                contentColor = Color.White
                                            ) {
                                                Text("$problemCount", fontSize = 8.sp)
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = "Diagnostics",
                                        tint = if (problemCount > 0) Color(0xFFF87171) else Color(0xFFFBBF24),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }

                        XcodeWorkspaceMode.BROWSER -> {
                            // Back button
                            IconButton(
                                onClick = { onGoBack?.invoke() ?: run { if (activeWebView?.canGoBack() == true) activeWebView.goBack() } },
                                enabled = canGoBack || activeWebView?.canGoBack() == true,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (canGoBack || activeWebView?.canGoBack() == true) Color(0xFFE2E8F0) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Forward button
                            IconButton(
                                onClick = { onGoForward?.invoke() ?: run { if (activeWebView?.canGoForward() == true) activeWebView.goForward() } },
                                enabled = canGoForward || activeWebView?.canGoForward() == true,
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_forward_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (canGoForward || activeWebView?.canGoForward() == true) Color(0xFFE2E8F0) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Reload button
                            IconButton(
                                onClick = { onReload?.invoke() ?: activeWebView?.reload() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_reload_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reload",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Visual Outlines button
                            IconButton(
                                onClick = {
                                    outlinesEnabled = !outlinesEnabled
                                    val script = if (outlinesEnabled) {
                                        "(function(){var s=document.createElement('style');s.id='__gvone_dev_outlines__';s.innerHTML='* { outline: 1px dashed rgba(56,189,248,0.7) !important; }';document.head.appendChild(s);return 'Outlines Active';})()"
                                    } else {
                                        "(function(){var s=document.getElementById('__gvone_dev_outlines__');if(s)s.remove();return 'Outlines Removed';})()"
                                    }
                                    executeJs(script, null)
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_outlines_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Layers,
                                    contentDescription = "DOM Outlines",
                                    tint = if (outlinesEnabled) Color(0xFF38BDF8) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        XcodeWorkspaceMode.BOT_CHAT -> {
                            IconButton(
                                onClick = { Toast.makeText(context, "New chat started", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_new_bot_chat_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddComment,
                                    contentDescription = "New Bot Chat",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        XcodeWorkspaceMode.PDF_VIEWER -> {
                            IconButton(
                                onClick = { onZoomIn?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_pdf_zoom_in_btn")
                            ) {
                                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onZoomOut?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_pdf_zoom_out_btn")
                            ) {
                                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onResetZoom?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_pdf_fit_btn")
                            ) {
                                Icon(Icons.Rounded.FitScreen, contentDescription = "Fit Width", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.IMAGE_VIEWER -> {
                            IconButton(
                                onClick = { onZoomIn?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_img_zoom_in_btn")
                            ) {
                                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onZoomOut?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_img_zoom_out_btn")
                            ) {
                                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onResetZoom?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_img_reset_btn")
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = "Reset Zoom", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onRotate?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_img_rotate_btn")
                            ) {
                                Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate 90°", tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.MARKDOWN -> {
                            IconButton(
                                onClick = { onTogglePreviewMode?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_md_toggle_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPreviewMode) Icons.Rounded.Edit else Icons.Rounded.Preview,
                                    contentDescription = if (isPreviewMode) "Edit Mode" else "Preview Mode",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onSaveFile?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_md_save_btn")
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = "Save Markdown", tint = if (isFileDirty) Color(0xFFF59E0B) else Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.DATA_INSPECTOR -> {
                            IconButton(
                                onClick = { onBeautifyData?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_data_beautify_btn")
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = "Beautify", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onMinifyData?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_data_minify_btn")
                            ) {
                                Icon(Icons.Rounded.Compress, contentDescription = "Minify", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.MEDIA_PLAYER -> {
                            IconButton(
                                onClick = { onTogglePlayPause?.invoke() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_media_play_btn")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                // 2. CENTER CAPSULE: Target Scheme / Active File Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0F1523),
                    border = BorderStroke(
                        1.dp,
                        when (workspaceMode) {
                            XcodeWorkspaceMode.CODE_EDITOR -> Color(0xFFA855F7).copy(alpha = 0.4f)
                            XcodeWorkspaceMode.BOT_CHAT -> Color(0xFF10B981).copy(alpha = 0.4f)
                            XcodeWorkspaceMode.PDF_VIEWER -> Color(0xFFEF4444).copy(alpha = 0.4f)
                            XcodeWorkspaceMode.IMAGE_VIEWER -> Color(0xFFEC4899).copy(alpha = 0.4f)
                            XcodeWorkspaceMode.MARKDOWN -> Color(0xFF38BDF8).copy(alpha = 0.4f)
                            XcodeWorkspaceMode.DATA_INSPECTOR -> Color(0xFF10B981).copy(alpha = 0.4f)
                            XcodeWorkspaceMode.MEDIA_PLAYER -> Color(0xFFF97316).copy(alpha = 0.4f)
                            else -> Color(0xFF2C384D)
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .height(34.dp)
                        .clickable { onAddressClick?.invoke() ?: run { isExpanded = !isExpanded } }
                        .testTag("dev_bar_scheme_pill")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (workspaceMode) {
                                XcodeWorkspaceMode.CODE_EDITOR -> Color(0xFFA855F7).copy(alpha = 0.2f)
                                XcodeWorkspaceMode.BOT_CHAT -> Color(0xFF10B981).copy(alpha = 0.2f)
                                XcodeWorkspaceMode.PDF_VIEWER -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                XcodeWorkspaceMode.IMAGE_VIEWER -> Color(0xFFEC4899).copy(alpha = 0.2f)
                                XcodeWorkspaceMode.MARKDOWN -> Color(0xFF38BDF8).copy(alpha = 0.2f)
                                XcodeWorkspaceMode.DATA_INSPECTOR -> Color(0xFF10B981).copy(alpha = 0.2f)
                                XcodeWorkspaceMode.MEDIA_PLAYER -> Color(0xFFF97316).copy(alpha = 0.2f)
                                else -> Color(0xFF38BDF8).copy(alpha = 0.2f)
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (workspaceMode) {
                                        XcodeWorkspaceMode.CODE_EDITOR -> Icons.Rounded.Code
                                        XcodeWorkspaceMode.BOT_CHAT -> Icons.Rounded.AutoAwesome
                                        XcodeWorkspaceMode.PDF_VIEWER -> Icons.Rounded.PictureAsPdf
                                        XcodeWorkspaceMode.IMAGE_VIEWER -> Icons.Rounded.Image
                                        XcodeWorkspaceMode.MARKDOWN -> Icons.Rounded.Description
                                        XcodeWorkspaceMode.DATA_INSPECTOR -> Icons.Rounded.DataObject
                                        XcodeWorkspaceMode.MEDIA_PLAYER -> Icons.Rounded.PlayCircle
                                        else -> if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Public
                                    },
                                    contentDescription = null,
                                    tint = when (workspaceMode) {
                                        XcodeWorkspaceMode.CODE_EDITOR -> Color(0xFFA855F7)
                                        XcodeWorkspaceMode.BOT_CHAT -> Color(0xFF34D399)
                                        XcodeWorkspaceMode.PDF_VIEWER -> Color(0xFFEF4444)
                                        XcodeWorkspaceMode.IMAGE_VIEWER -> Color(0xFFEC4899)
                                        XcodeWorkspaceMode.MARKDOWN -> Color(0xFF38BDF8)
                                        XcodeWorkspaceMode.DATA_INSPECTOR -> Color(0xFF10B981)
                                        XcodeWorkspaceMode.MEDIA_PLAYER -> Color(0xFFF97316)
                                        else -> if (isHttps) Color(0xFF4ADE80) else Color(0xFF38BDF8)
                                    },
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (workspaceMode) {
                                        XcodeWorkspaceMode.CODE_EDITOR -> (activeFileName ?: "Xcode Editor")
                                        XcodeWorkspaceMode.BOT_CHAT -> (activeFileName ?: "Bot Chat Copilot")
                                        XcodeWorkspaceMode.PDF_VIEWER -> (activeFileName ?: "document.pdf")
                                        XcodeWorkspaceMode.IMAGE_VIEWER -> (activeFileName ?: "preview.png")
                                        XcodeWorkspaceMode.MARKDOWN -> (activeFileName ?: "architecture.md")
                                        XcodeWorkspaceMode.DATA_INSPECTOR -> (activeFileName ?: "package.json")
                                        XcodeWorkspaceMode.MEDIA_PLAYER -> (activeFileName ?: "sample_track.wav")
                                        else -> (activeFileName ?: tab.title.takeIf { it.isNotBlank() } ?: "Web Workspace")
                                    },
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isFileDirty) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF59E0B))
                                    )
                                }
                                if (isTorActive) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFC084FC))
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. RIGHT CONTROLS: Mode Action Buttons, Inspector, Console, Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    when (workspaceMode) {
                        XcodeWorkspaceMode.CODE_EDITOR -> {
                            // Run & Preview in Web Browser
                            IconButton(
                                onClick = {
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.BROWSER)
                                    Toast.makeText(context, "Running & Previewing in Web Browser", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_run_action_btn")
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Run in Browser", tint = Color(0xFF34D399), modifier = Modifier.size(17.dp))
                            }
                        }

                        XcodeWorkspaceMode.BROWSER -> {
                            // Reload / Run web app
                            IconButton(
                                onClick = {
                                    onReload?.invoke() ?: run {
                                        activeWebView?.clearCache(true)
                                        activeWebView?.reload()
                                    }
                                    Toast.makeText(context, "Reloading Web App", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_run_action_btn")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Reload Web App", tint = Color(0xFF38BDF8), modifier = Modifier.size(17.dp))
                            }
                        }

                        XcodeWorkspaceMode.BOT_CHAT -> {
                            // Apply to Code
                            IconButton(
                                onClick = {
                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.CODE_EDITOR)
                                },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("dev_bar_apply_code_btn")
                            ) {
                                Icon(Icons.Rounded.Code, contentDescription = "Switch to Code", tint = Color(0xFFA855F7), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.PDF_VIEWER -> {
                            IconButton(
                                onClick = { onSearchDoc?.invoke() ?: Toast.makeText(context, "Searching document...", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search PDF", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onShareFile?.invoke() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share PDF", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.IMAGE_VIEWER -> {
                            IconButton(
                                onClick = { onInvertCanvas?.invoke() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Contrast, contentDescription = "Invert Canvas", tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onShareFile?.invoke() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share Image", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.MARKDOWN -> {
                            IconButton(
                                onClick = { onShareFile?.invoke() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = "Share Document", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.DATA_INSPECTOR -> {
                            IconButton(
                                onClick = { onCopyData?.invoke() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy JSON", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            }
                        }

                        XcodeWorkspaceMode.MEDIA_PLAYER -> {
                            IconButton(
                                onClick = { Toast.makeText(context, "Background Audio Active", Toast.LENGTH_SHORT).show() },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Rounded.Headphones, contentDescription = "Background Audio", tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Open Xcode Inspector Panel
                    if (onOpenInspector != null) {
                        IconButton(
                            onClick = onOpenInspector,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("dev_bar_inspector_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = "Inspector Panel",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Open Xcode Debug Console / Terminal
                    if (onOpenConsole != null && workspaceMode != XcodeWorkspaceMode.IMAGE_VIEWER && workspaceMode != XcodeWorkspaceMode.PDF_VIEWER && workspaceMode != XcodeWorkspaceMode.MEDIA_PLAYER) {
                        IconButton(
                            onClick = onOpenConsole,
                            modifier = Modifier
                                .size(30.dp)
                                .testTag("dev_bar_console_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = "Debug Console",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Expand / Collapse Live DevTools Drawer
                    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "chevron")
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("dev_bar_expand_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse Tools" else "Expand Tools",
                            tint = if (isExpanded) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(17.dp)
                                .rotate(rotation)
                        )
                    }

                    // DEV ON/OFF Toggle
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF064E3B),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier
                            .clickable { onToggleOff() }
                            .testTag("dev_bar_toggle_off_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color(0xFF34D399), CircleShape)
                            )
                            Text(
                                text = "DEV",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF34D399)
                            )
                        }
                    }
                }
            }

            // ================= EXPANDABLE DEV DRAWER =================
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .background(Color(0xFF06090F))
                ) {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    // Category Tabs inside drawer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0D121D))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DevBarTab.values().forEach { tabType ->
                            val isSelected = selectedTab == tabType
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, Color(0xFF38BDF8)) else BorderStroke(0.5.dp, Color(0xFF1E293B)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { selectedTab = tabType }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = tabType.icon,
                                        contentDescription = tabType.label,
                                        tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = tabType.label,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        when (selectedTab) {
                            DevBarTab.CONSOLE -> {
                                DevConsolePanel(
                            consoleLogs = consoleLogs,
                            jsInput = jsInput,
                            onJsInputChange = { jsInput = it },
                            jsOutput = jsEvaluationOutput,
                            onExecuteJs = { snippet ->
                                executeJs(snippet) { result ->
                                    jsEvaluationOutput = result
                                    consoleLogs.add(
                                        DevConsoleLog(
                                            message = "▶ $snippet\n◀ $result",
                                            level = ConsoleMessage.MessageLevel.LOG
                                        )
                                    )
                                }
                            },
                            onClearLogs = {
                                consoleLogs.clear()
                                jsEvaluationOutput = null
                            }
                        )
                    }
                    DevBarTab.DOM_INSPECT -> {
                        DevDomInspectPanel(
                            tab = tab,
                            outlinesEnabled = outlinesEnabled,
                            onToggleOutlines = {
                                outlinesEnabled = !outlinesEnabled
                                val script = if (outlinesEnabled) {
                                    "(function(){var s=document.createElement('style');s.id='__gvone_dev_outlines__';s.innerHTML='* { outline: 1px dashed rgba(56,189,248,0.7) !important; }';document.head.appendChild(s);return 'Outlines Active';})()"
                                } else {
                                    "(function(){var s=document.getElementById('__gvone_dev_outlines__');if(s)s.remove();return 'Outlines Removed';})()"
                                }
                                executeJs(script) {
                                    Toast.makeText(context, if (outlinesEnabled) "Visual DOM Outlines Enabled" else "Visual Outlines Disabled", Toast.LENGTH_SHORT).show()
                                }
                            },
                            clickInspectorEnabled = clickInspectorEnabled,
                            onToggleClickInspector = {
                                clickInspectorEnabled = !clickInspectorEnabled
                                val script = if (clickInspectorEnabled) {
                                    """
                                    (function(){
                                        window.__gvone_inspect_fn__ = function(e){
                                            e.preventDefault();
                                            e.stopPropagation();
                                            var el = e.target;
                                            var info = '<' + el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + '> ' + Math.round(el.offsetWidth) + 'x' + Math.round(el.offsetHeight) + 'px';
                                            alert('Element: ' + info);
                                        };
                                        window.addEventListener('click', window.__gvone_inspect_fn__, true);
                                        return 'Inspector On';
                                    })()
                                    """.trimIndent()
                                } else {
                                    """
                                    (function(){
                                        if (window.__gvone_inspect_fn__) {
                                            window.removeEventListener('click', window.__gvone_inspect_fn__, true);
                                            window.__gvone_inspect_fn__ = null;
                                        }
                                        return 'Inspector Off';
                                    })()
                                    """.trimIndent()
                                }
                                executeJs(script) {
                                    Toast.makeText(context, if (clickInspectorEnabled) "Click Inspector ON: Tap any element to view tag" else "Click Inspector OFF", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onCopyOuterHtml = {
                                executeJs("document.documentElement.outerHTML") { html ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("HTML", html.removePrefix("\"").removeSuffix("\"").replace("\\n", "\n").replace("\\\"", "\"")))
                                    Toast.makeText(context, "Page HTML copied to clipboard!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onInspectMetadata = {
                                val script = """
                                JSON.stringify({
                                    title: document.title,
                                    elements: document.getElementsByTagName('*').length,
                                    scripts: document.scripts.length,
                                    links: document.links.length,
                                    images: document.images.length,
                                    viewport: window.innerWidth + 'x' + window.innerHeight
                                })
                                """.trimIndent()
                                executeJs(script) { data ->
                                    Toast.makeText(context, "Page Stats: $data", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                    DevBarTab.NETWORK -> {
                        DevNetworkPanel(
                            tab = tab,
                            isTorActive = isTorActive,
                            onHardReload = {
                                activeWebView?.clearCache(true)
                                activeWebView?.reload()
                                Toast.makeText(context, "Hard Reload (Cache Bypassed)", Toast.LENGTH_SHORT).show()
                            },
                            onClearCache = {
                                activeWebView?.clearCache(true)
                                Toast.makeText(context, "WebView Cache Cleared", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    DevBarTab.STORAGE -> {
                        DevStoragePanel(
                            hostDomain = hostDomain,
                            onInspectCookies = {
                                executeJs("document.cookie") { cookies ->
                                    val cleanCookies = cookies.trim().removePrefix("\"").removeSuffix("\"")
                                    Toast.makeText(context, if (cleanCookies.isBlank()) "No cookies found" else "Cookies: $cleanCookies", Toast.LENGTH_LONG).show()
                                }
                            },
                            onClearStorage = {
                                executeJs("localStorage.clear(); sessionStorage.clear(); 'Storage Cleared';") {
                                    Toast.makeText(context, "LocalStorage & SessionStorage cleared!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    DevBarTab.DEVICE -> {
                        DevDevicePanel(
                            activeWebView = activeWebView,
                            onSetZoom = { zoomPercent ->
                                activeWebView?.setInitialScale(zoomPercent)
                                Toast.makeText(context, "Scale set to $zoomPercent%", Toast.LENGTH_SHORT).show()
                            },
                            onToggleDarkMode = {
                                val script = """
                                (function(){
                                    var id = '__gvone_dark_filter__';
                                    var existing = document.getElementById(id);
                                    if (existing) {
                                        existing.remove();
                                        return 'Default Theme';
                                    } else {
                                        var s = document.createElement('style');
                                        s.id = id;
                                        s.innerHTML = 'html { filter: invert(0.92) hue-rotate(180deg) !important; } img, video { filter: invert(1.08) hue-rotate(180deg) !important; }';
                                        document.head.appendChild(s);
                                        return 'Inverted Dark Mode';
                                    }
                                })()
                                """.trimIndent()
                                executeJs(script) { res ->
                                    Toast.makeText(context, res, Toast.LENGTH_SHORT).show()
                                }
                            }
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
private fun DevConsolePanel(
    consoleLogs: List<DevConsoleLog>,
    jsInput: String,
    onJsInputChange: (String) -> Unit,
    jsOutput: String?,
    onExecuteJs: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Quick Snippets Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val snippets = listOf(
                "document.title",
                "location.href",
                "document.cookie",
                "navigator.userAgent",
                "performance.now()",
                "document.querySelectorAll('*').length",
                "document.body.innerText.length"
            )
            snippets.forEach { snippet ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF131C2E),
                    border = BorderStroke(0.5.dp, Color(0xFF233554)),
                    modifier = Modifier.clickable {
                        onJsInputChange(snippet)
                        onExecuteJs(snippet)
                    }
                ) {
                    Text(
                        text = snippet,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFF93C5FD),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Interactive JS input bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TextField(
                value = jsInput,
                onValueChange = onJsInputChange,
                placeholder = { Text("eval JS snippet...", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFFE2E8F0)
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedIndicatorColor = Color(0xFF38BDF8),
                    unfocusedIndicatorColor = Color(0xFF1E293B)
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag("dev_bar_js_input")
            )

            Button(
                onClick = { onExecuteJs(jsInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(42.dp)
                    .testTag("dev_bar_js_run_btn")
            ) {
                Text("RUN", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(
                onClick = onClearLogs,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = "Clear Logs",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Live Log Output
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF04060A),
            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (consoleLogs.isEmpty() && jsOutput == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Console is ready. Type JS or tap a snippet above to inspect website.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        color = Color(0xFF475569)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(consoleLogs.reversed()) { log ->
                        val color = when (log.level) {
                            ConsoleMessage.MessageLevel.ERROR -> Color(0xFFF87171)
                            ConsoleMessage.MessageLevel.WARNING -> Color(0xFFFBBF24)
                            else -> Color(0xFF93C5FD)
                        }
                        Text(
                            text = log.message,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DevDomInspectPanel(
    tab: BrowserTab,
    outlinesEnabled: Boolean,
    onToggleOutlines: () -> Unit,
    clickInspectorEnabled: Boolean,
    onToggleClickInspector: () -> Unit,
    onCopyOuterHtml: () -> Unit,
    onInspectMetadata: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Toggle Outlines
            Button(
                onClick = onToggleOutlines,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (outlinesEnabled) Color(0xFF0369A1) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (outlinesEnabled) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (outlinesEnabled) "Hide Outlines" else "Show Outlines",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
            }

            // Click Inspector
            Button(
                onClick = onToggleClickInspector,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (clickInspectorEnabled) Color(0xFF7C3AED) else Color(0xFF1E293B)
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (clickInspectorEnabled) "Stop Inspect" else "Click Inspect",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onCopyOuterHtml,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Full HTML", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }

            Button(
                onClick = onInspectMetadata,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Analytics, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Page Element Stats", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }
        }

        // Details card
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF090D14),
            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 2.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("TITLE: ${tab.title}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFCBD5E1), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("URL: ${tab.url}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF38BDF8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("TAB ID: ${tab.id}", fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun DevNetworkPanel(
    tab: BrowserTab,
    isTorActive: Boolean,
    onHardReload: () -> Unit,
    onClearCache: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onHardReload,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bypass Cache Reload", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }

            Button(
                onClick = onClearCache,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear HTTP Cache", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF090D14),
            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("NETWORK PROTOCOL: ${if (tab.url.startsWith("https")) "HTTPS (TLS/SSL Secured)" else "HTTP (Unencrypted)"}", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = if (tab.url.startsWith("https")) Color(0xFF4ADE80) else Color(0xFFFBBF24))
                Text("TOR PROXY STATUS: ${if (isTorActive) "Routing through SOCKS5 (127.0.0.1:9050)" else "Direct ClearNet Routing"}", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = if (isTorActive) Color(0xFFC084FC) else Color(0xFF94A3B8))
                Text("PAGE READY STATE: COMPLETED", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun DevStoragePanel(
    hostDomain: String,
    onInspectCookies: () -> Unit,
    onClearStorage: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onInspectCookies,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Cookie, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Read Cookies", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }

            Button(
                onClick = onClearStorage,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB91C1C)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear LocalStorage", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF090D14),
            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ORIGIN DOMAIN: $hostDomain", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF38BDF8))
                Text("STORAGE ENGINES: LocalStorage, SessionStorage, IndexedDB, Cookies", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF94A3B8))
                Text("Tap buttons above to inspect cookies or wipe origin web storage.", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun DevDevicePanel(
    activeWebView: WebView?,
    onSetZoom: (Int) -> Unit,
    onToggleDarkMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = onToggleDarkMode,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.DarkMode, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Force Dark CSS", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp)
            }

            listOf(80, 100, 125).forEach { zoom ->
                OutlinedButton(
                    onClick = { onSetZoom(zoom) },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                    border = BorderStroke(0.5.dp, Color(0xFF233554))
                ) {
                    Text("$zoom%", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = Color(0xFF090D14),
            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("USER AGENT: Android WebView Mobile / Desktop compatible", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFCBD5E1))
                Text("VIEWPORT RESIZE: Zoom levels and custom scale controls active.", fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, color = Color(0xFF64748B))
            }
        }
    }
}
