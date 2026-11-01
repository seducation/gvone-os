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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ViewSidebar
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
import com.example.ui.screens.ide.GrokBotPersona
import com.example.ui.screens.ide.IdeTab
import com.example.ui.screens.ide.XcodeWorkspaceMode
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Target items representation for the Xcode-style Target Selector Dropdown in UnifiedDeveloperBar.
 */
data class OtherWorkspaceFile(
    val title: String,
    val path: String,
    val mode: XcodeWorkspaceMode,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tintColor: Color
)

data class ProjectCodeFile(
    val name: String,
    val path: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tintColor: Color
)

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
 * Unified Developer Bar — context-adaptive toolbar that morphs its controls and
 * expandable drawer based on the active workspace mode (Browser, Code Editor, Bot Chat,
 * PDF, Image, Audio/Video, Markdown, JSON Inspector). Placed on top of the active content.
 */
@Composable
fun UnifiedDeveloperBar(
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
    openTabs: List<BrowserTab> = emptyList(),
    currentTabId: String? = null,
    onSelectBrowserTab: ((String) -> Unit)? = null,
    onNewBrowserTab: (() -> Unit)? = null,
    ideTabs: List<IdeTab> = emptyList(),
    activeIdeTabId: String? = null,
    onSelectIdeTab: ((String) -> Unit)? = null,
    onNewIdeTab: (() -> Unit)? = null,
    onOpenCodeFile: ((String) -> Unit)? = null,
    activeBotPersona: GrokBotPersona? = null,
    onSelectBotPersona: ((GrokBotPersona) -> Unit)? = null,
    onSelectWorkspaceFile: ((String, XcodeWorkspaceMode) -> Unit)? = null
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(DevBarTab.CONSOLE) }

    // Target Selector Dropdown State
    var isTargetDropdownOpen by remember { mutableStateOf(false) }
    var targetSearchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }

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

    // Target Dropdown Items Preparation
    val allWebTabs = remember(openTabs, tab) {
        if (openTabs.isNotEmpty()) openTabs else listOf(tab)
    }
    val filteredWebTabs = remember(allWebTabs, targetSearchQuery) {
        if (targetSearchQuery.isBlank()) allWebTabs
        else allWebTabs.filter {
            it.title.contains(targetSearchQuery, ignoreCase = true) ||
            it.url.contains(targetSearchQuery, ignoreCase = true)
        }
    }

    val projectCodeFiles = remember {
        listOf(
            ProjectCodeFile("script.js", "Projects/script.js", "JavaScript Studio", Icons.Rounded.Code, Color(0xFFFACC15)),
            ProjectCodeFile("index.html", "Projects/index.html", "Web Frontend Layout", Icons.Rounded.Language, Color(0xFFFB923C)),
            ProjectCodeFile("style.css", "Projects/style.css", "Design Stylesheet", Icons.Rounded.Brush, Color(0xFF38BDF8)),
            ProjectCodeFile("MainActivity.kt", "app/src/main/java/com/example/MainActivity.kt", "Android Kotlin Source", Icons.Rounded.Terminal, Color(0xFFA78BFA))
        )
    }
    val filteredIdeTabs = remember(ideTabs, targetSearchQuery) {
        if (targetSearchQuery.isBlank()) ideTabs
        else ideTabs.filter {
            it.fileItem.name.contains(targetSearchQuery, ignoreCase = true) ||
            it.fileItem.path.contains(targetSearchQuery, ignoreCase = true)
        }
    }
    val filteredProjectFiles = remember(projectCodeFiles, targetSearchQuery) {
        if (targetSearchQuery.isBlank()) projectCodeFiles
        else projectCodeFiles.filter {
            it.name.contains(targetSearchQuery, ignoreCase = true) ||
            it.subtitle.contains(targetSearchQuery, ignoreCase = true)
        }
    }

    val botPersonas = remember { GrokBotPersona.values().toList() }
    val filteredBots = remember(botPersonas, targetSearchQuery) {
        if (targetSearchQuery.isBlank()) botPersonas
        else botPersonas.filter {
            it.displayName.contains(targetSearchQuery, ignoreCase = true) ||
            it.tagline.contains(targetSearchQuery, ignoreCase = true)
        }
    }

    val otherFiles = remember {
        listOf(
            OtherWorkspaceFile("architecture.md", "GVONE/architecture.md", XcodeWorkspaceMode.MARKDOWN, "Markdown & Architecture Docs", Icons.Rounded.Description, Color(0xFF10B981)),
            OtherWorkspaceFile("document.pdf", "Downloads/document.pdf", XcodeWorkspaceMode.PDF_VIEWER, "PDF Multi-page Document", Icons.Rounded.PictureAsPdf, Color(0xFFEF4444)),
            OtherWorkspaceFile("preview.png", "Images/preview.png", XcodeWorkspaceMode.IMAGE_VIEWER, "Visual Asset & Image Viewer", Icons.Rounded.Image, Color(0xFFF59E0B)),
            OtherWorkspaceFile("package.json", "Projects/ReactMobileUI/package.json", XcodeWorkspaceMode.DATA_INSPECTOR, "JSON Tree & Data Inspector", Icons.Rounded.DataObject, Color(0xFF06B6D4)),
            OtherWorkspaceFile("sample_track.wav", "Audio/sample_track.wav", XcodeWorkspaceMode.MEDIA_PLAYER, "Audio & Media Player", Icons.Rounded.PlayCircle, Color(0xFFEC4899))
        )
    }
    val filteredOtherFiles = remember(otherFiles, targetSearchQuery) {
        if (targetSearchQuery.isBlank()) otherFiles
        else otherFiles.filter {
            it.title.contains(targetSearchQuery, ignoreCase = true) ||
            it.description.contains(targetSearchQuery, ignoreCase = true)
        }
    }

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
            .testTag("unified_developer_bar"),
        color = Color(0xFF111726),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column {
            // ================= TOP BAR (Restored Classic Developer Bar) =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. LEFT CONTROLS: Mode-Adaptive Navigation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    when (workspaceMode) {
                        XcodeWorkspaceMode.BROWSER -> {
                            IconButton(
                                onClick = { onGoBack?.invoke() ?: run { if (activeWebView?.canGoBack() == true) activeWebView.goBack() } },
                                enabled = canGoBack || activeWebView?.canGoBack() == true,
                                modifier = Modifier.size(30.dp).testTag("dev_bar_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = if (canGoBack || activeWebView?.canGoBack() == true) Color(0xFFE2E8F0) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onGoForward?.invoke() ?: run { if (activeWebView?.canGoForward() == true) activeWebView.goForward() } },
                                enabled = canGoForward || activeWebView?.canGoForward() == true,
                                modifier = Modifier.size(30.dp).testTag("dev_bar_forward_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Forward",
                                    tint = if (canGoForward || activeWebView?.canGoForward() == true) Color(0xFFE2E8F0) else Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onReload?.invoke() ?: activeWebView?.reload() },
                                modifier = Modifier.size(30.dp).testTag("dev_bar_reload_btn")
                            ) {
                                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Reload", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.CODE_EDITOR -> {
                            IconButton(onClick = { onOpenNavigator?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ViewSidebar, contentDescription = "Navigator", tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onSaveFile?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Save, contentDescription = "Save", tint = if (isFileDirty) Color(0xFF34D399) else Color(0xFF475569), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onFormatCode?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.FormatAlignLeft, contentDescription = "Format", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.BOT_CHAT -> {
                            IconButton(onClick = { onOpenNavigator?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.SmartToy, contentDescription = "Persona", tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onOpenConsole?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.History, contentDescription = "History", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.PDF_VIEWER -> {
                            IconButton(onClick = { /* Previous page */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous Page", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Next page */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next Page", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onOpenNavigator?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Toc, contentDescription = "Outline", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.IMAGE_VIEWER -> {
                            IconButton(onClick = { /* Zoom out */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Zoom in */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Fit to screen */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.FitScreen, contentDescription = "Fit", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.MEDIA_PLAYER -> {
                            IconButton(onClick = { /* Previous track */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Play/Pause */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Next track */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.MARKDOWN -> {
                            IconButton(onClick = { onOpenNavigator?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Toc, contentDescription = "TOC", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Toggle preview */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Visibility, contentDescription = "Preview", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.DATA_INSPECTOR -> {
                            IconButton(onClick = { /* Expand all */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.UnfoldMore, contentDescription = "Expand All", tint = Color(0xFF06B6D4), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Collapse all */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.UnfoldLess, contentDescription = "Collapse All", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Search */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search Keys", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // 2. CENTER CAPSULE: Target Selector Dropdown Anchor (Websites, Xcode, Bots, Files)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F1523),
                        border = BorderStroke(1.dp, if (isTargetDropdownOpen) Color(0xFF38BDF8) else Color(0xFF2C384D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                            .clickable { isTargetDropdownOpen = !isTargetDropdownOpen }
                            .testTag("dev_bar_scheme_pill")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Target Mode Icon
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (workspaceMode) {
                                    XcodeWorkspaceMode.BROWSER -> if (isHttps) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.2f)
                                    XcodeWorkspaceMode.CODE_EDITOR -> Color(0xFFA78BFA).copy(alpha = 0.2f)
                                    XcodeWorkspaceMode.BOT_CHAT -> Color(0xFF00E5FF).copy(alpha = 0.2f)
                                    XcodeWorkspaceMode.PDF_VIEWER -> Color(0xFFEF4444).copy(alpha = 0.2f)
                                    XcodeWorkspaceMode.IMAGE_VIEWER -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                    XcodeWorkspaceMode.MARKDOWN -> Color(0xFF10B981).copy(alpha = 0.2f)
                                    XcodeWorkspaceMode.DATA_INSPECTOR -> Color(0xFF06B6D4).copy(alpha = 0.2f)
                                    XcodeWorkspaceMode.MEDIA_PLAYER -> Color(0xFFEC4899).copy(alpha = 0.2f)
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = when (workspaceMode) {
                                            XcodeWorkspaceMode.BROWSER -> if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Public
                                            XcodeWorkspaceMode.CODE_EDITOR -> Icons.Rounded.Code
                                            XcodeWorkspaceMode.BOT_CHAT -> Icons.Rounded.AutoAwesome
                                            XcodeWorkspaceMode.PDF_VIEWER -> Icons.Rounded.PictureAsPdf
                                            XcodeWorkspaceMode.IMAGE_VIEWER -> Icons.Rounded.Image
                                            XcodeWorkspaceMode.MARKDOWN -> Icons.Rounded.Description
                                            XcodeWorkspaceMode.DATA_INSPECTOR -> Icons.Rounded.DataObject
                                            XcodeWorkspaceMode.MEDIA_PLAYER -> Icons.Rounded.PlayCircle
                                        },
                                        contentDescription = null,
                                        tint = when (workspaceMode) {
                                            XcodeWorkspaceMode.BROWSER -> if (isHttps) Color(0xFF34D399) else Color(0xFF38BDF8)
                                            XcodeWorkspaceMode.CODE_EDITOR -> Color(0xFFA78BFA)
                                            XcodeWorkspaceMode.BOT_CHAT -> Color(0xFF00E5FF)
                                            XcodeWorkspaceMode.PDF_VIEWER -> Color(0xFFEF4444)
                                            XcodeWorkspaceMode.IMAGE_VIEWER -> Color(0xFFF59E0B)
                                            XcodeWorkspaceMode.MARKDOWN -> Color(0xFF34D399)
                                            XcodeWorkspaceMode.DATA_INSPECTOR -> Color(0xFF06B6D4)
                                            XcodeWorkspaceMode.MEDIA_PLAYER -> Color(0xFFEC4899)
                                        },
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Target Label
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (workspaceMode) {
                                        XcodeWorkspaceMode.BROWSER -> hostDomain.ifBlank { tab.title.takeIf { it.isNotBlank() } ?: "Web DevTools" }
                                        XcodeWorkspaceMode.CODE_EDITOR -> activeFileName ?: "Xcode Editor"
                                        XcodeWorkspaceMode.BOT_CHAT -> activeBotPersona?.displayName ?: "AI Copilot"
                                        XcodeWorkspaceMode.PDF_VIEWER -> activeFileName ?: "document.pdf"
                                        XcodeWorkspaceMode.IMAGE_VIEWER -> activeFileName ?: "preview.png"
                                        XcodeWorkspaceMode.MARKDOWN -> activeFileName ?: "architecture.md"
                                        XcodeWorkspaceMode.DATA_INSPECTOR -> activeFileName ?: "package.json"
                                        XcodeWorkspaceMode.MEDIA_PLAYER -> activeFileName ?: "sample_track.wav"
                                    },
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
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

                            // Category Tag Badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E293B)
                            ) {
                                Text(
                                    text = when (workspaceMode) {
                                        XcodeWorkspaceMode.BROWSER -> "Website"
                                        XcodeWorkspaceMode.CODE_EDITOR -> "Xcode"
                                        XcodeWorkspaceMode.BOT_CHAT -> "Bot"
                                        else -> "File"
                                    },
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (workspaceMode) {
                                        XcodeWorkspaceMode.BROWSER -> Color(0xFF38BDF8)
                                        XcodeWorkspaceMode.CODE_EDITOR -> Color(0xFFA78BFA)
                                        XcodeWorkspaceMode.BOT_CHAT -> Color(0xFF00E5FF)
                                        else -> Color(0xFF10B981)
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            // Dropdown chevron indicator
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = "Open Target Selector",
                                tint = if (isTargetDropdownOpen) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // ================= TARGET SELECTOR DROPDOWN MENU =================
                    DropdownMenu(
                        expanded = isTargetDropdownOpen,
                        onDismissRequest = {
                            isTargetDropdownOpen = false
                            targetSearchQuery = ""
                        },
                        modifier = Modifier
                            .background(Color(0xFF0B101B))
                            .border(1.dp, Color(0xFF223048), RoundedCornerShape(12.dp))
                            .widthIn(min = 320.dp, max = 380.dp)
                            .heightIn(max = 520.dp)
                            .testTag("dev_bar_target_dropdown_menu")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            // Header: Target Selector
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderSpecial,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "TARGET SELECTOR",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        isTargetDropdownOpen = false
                                        targetSearchQuery = ""
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Category Filter Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val categories = listOf(
                                    "ALL" to "All",
                                    "WEBSITES" to "🌐 Websites",
                                    "XCODE" to "💻 Xcode",
                                    "BOTS" to "🤖 Bots",
                                    "FILES" to "📁 Files"
                                )
                                categories.forEach { (catKey, label) ->
                                    val isSelected = selectedCategoryFilter == catKey
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF161F30),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF2C384D)),
                                        modifier = Modifier.clickable { selectedCategoryFilter = catKey }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color(0xFF0F172A) else Color(0xFFCBD5E1),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                            // Scrollable Target Items (Websites, Xcode, Bots, Other Files)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 400.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                // 1. 🌐 WEBSITES SECTION
                                if (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "WEBSITES") {
                                    TargetSectionHeader(
                                        title = "WEBSITES & TABS",
                                        badge = "${filteredWebTabs.size}",
                                        actionLabel = "+ New Tab",
                                        onAction = {
                                            isTargetDropdownOpen = false
                                            onNewBrowserTab?.invoke() ?: onAddressClick?.invoke()
                                            onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.BROWSER)
                                        }
                                    )

                                    filteredWebTabs.forEach { tabItem ->
                                        val isCurrent = (tabItem.id == (currentTabId ?: tab.id)) && workspaceMode == XcodeWorkspaceMode.BROWSER
                                        TargetMenuItem(
                                            icon = if (tabItem.url.startsWith("https://")) Icons.Rounded.Lock else Icons.Rounded.Public,
                                            iconTint = if (tabItem.url.startsWith("https://")) Color(0xFF34D399) else Color(0xFF38BDF8),
                                            title = tabItem.title.ifBlank { "Untitled Tab" },
                                            subtitle = try { URI(tabItem.url).host ?: tabItem.url } catch (_: Exception) { tabItem.url },
                                            isActive = isCurrent,
                                            onClick = {
                                                isTargetDropdownOpen = false
                                                onSelectBrowserTab?.invoke(tabItem.id)
                                                onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.BROWSER)
                                            }
                                        )
                                    }
                                }

                                // 2. 💻 XCODE CODE STUDIO SECTION
                                if (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "XCODE") {
                                    TargetSectionHeader(
                                        title = "XCODE & CODE FILES",
                                        badge = "${filteredIdeTabs.size + filteredProjectFiles.size}",
                                        actionLabel = "+ New File",
                                        onAction = {
                                            isTargetDropdownOpen = false
                                            onNewIdeTab?.invoke()
                                            onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.CODE_EDITOR)
                                        }
                                    )

                                    // Open editor tabs
                                    filteredIdeTabs.forEach { editorTab ->
                                        val isCurrent = (editorTab.id == activeIdeTabId) && workspaceMode == XcodeWorkspaceMode.CODE_EDITOR
                                        TargetMenuItem(
                                            icon = Icons.Rounded.Code,
                                            iconTint = Color(0xFFA78BFA),
                                            title = editorTab.fileItem.name + (if (editorTab.isDirty) " •" else ""),
                                            subtitle = editorTab.fileItem.path,
                                            isActive = isCurrent,
                                            onClick = {
                                                isTargetDropdownOpen = false
                                                onSelectIdeTab?.invoke(editorTab.id)
                                                onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.CODE_EDITOR)
                                            }
                                        )
                                    }

                                    // Project files shortcuts
                                    filteredProjectFiles.forEach { pFile ->
                                        val isAlreadyOpen = filteredIdeTabs.any { it.fileItem.name.equals(pFile.name, ignoreCase = true) }
                                        if (!isAlreadyOpen) {
                                            TargetMenuItem(
                                                icon = pFile.icon,
                                                iconTint = pFile.tintColor,
                                                title = pFile.name,
                                                subtitle = pFile.subtitle,
                                                isActive = false,
                                                onClick = {
                                                    isTargetDropdownOpen = false
                                                    onOpenCodeFile?.invoke(pFile.name) ?: onNewIdeTab?.invoke()
                                                    onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.CODE_EDITOR)
                                                }
                                            )
                                        }
                                    }
                                }

                                // 3. 🤖 BOTS & AI COPILOTS SECTION
                                if (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "BOTS") {
                                    TargetSectionHeader(
                                        title = "BOTS & AI COPILOTS",
                                        badge = "${filteredBots.size}"
                                    )

                                    filteredBots.forEach { bot ->
                                        val isCurrent = (activeBotPersona == bot) && workspaceMode == XcodeWorkspaceMode.BOT_CHAT
                                        TargetMenuItem(
                                            icon = bot.icon,
                                            iconTint = Color(bot.avatarColorHex),
                                            title = bot.displayName,
                                            subtitle = bot.tagline,
                                            isActive = isCurrent,
                                            onClick = {
                                                isTargetDropdownOpen = false
                                                onSelectBotPersona?.invoke(bot)
                                                onToggleWorkspaceMode?.invoke(XcodeWorkspaceMode.BOT_CHAT)
                                            }
                                        )
                                    }
                                }

                                // 4. 📁 OTHER FILES & NATIVE VIEWERS SECTION
                                if (selectedCategoryFilter == "ALL" || selectedCategoryFilter == "FILES") {
                                    TargetSectionHeader(
                                        title = "OTHER FILES & VIEWERS",
                                        badge = "${filteredOtherFiles.size}"
                                    )

                                    filteredOtherFiles.forEach { f ->
                                        val isCurrent = workspaceMode == f.mode
                                        TargetMenuItem(
                                            icon = f.icon,
                                            iconTint = f.tintColor,
                                            title = f.title,
                                            subtitle = f.description,
                                            isActive = isCurrent,
                                            onClick = {
                                                isTargetDropdownOpen = false
                                                onSelectWorkspaceFile?.invoke(f.path, f.mode)
                                                onToggleWorkspaceMode?.invoke(f.mode)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. RIGHT CONTROLS: Mode-Adaptive Toolbar + Shared Expand/DEV
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // === Mode-Specific Tool Buttons ===
                    when (workspaceMode) {
                        XcodeWorkspaceMode.BROWSER -> {
                            // DOM Visual Outlines Toggle
                            IconButton(
                                onClick = {
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
                                modifier = Modifier.size(30.dp).testTag("dev_bar_outlines_btn")
                            ) {
                                Icon(Icons.Rounded.Layers, contentDescription = "Visual Outlines", tint = if (outlinesEnabled) Color(0xFF38BDF8) else Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }

                            // Click-to-Inspect Element Toggle
                            IconButton(
                                onClick = {
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
                                modifier = Modifier.size(30.dp).testTag("dev_bar_inspect_element_btn")
                            ) {
                                Icon(Icons.Rounded.AdsClick, contentDescription = "Inspect Element", tint = if (clickInspectorEnabled) Color(0xFF34D399) else Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }

                            // Console
                            IconButton(
                                onClick = {
                                    if (!isExpanded) { selectedTab = DevBarTab.CONSOLE; isExpanded = true }
                                    else if (selectedTab != DevBarTab.CONSOLE) selectedTab = DevBarTab.CONSOLE
                                    else onOpenConsole?.invoke()
                                },
                                modifier = Modifier.size(30.dp).testTag("dev_bar_console_btn")
                            ) {
                                Icon(Icons.Rounded.Terminal, contentDescription = "Dev Console", tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                            }

                            // Code Studio IDE
                            if (onOpenIde != null) {
                                IconButton(onClick = onOpenIde, modifier = Modifier.size(30.dp).testTag("dev_bar_ide_btn")) {
                                    Icon(Icons.Rounded.Code, contentDescription = "Code Studio IDE", tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        XcodeWorkspaceMode.CODE_EDITOR -> {
                            IconButton(onClick = { onOpenConsole?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Terminal, contentDescription = "Terminal", tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Run / Build */ Toast.makeText(context, "Build & Run", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Run", tint = Color(0xFF4ADE80), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Git status */ Toast.makeText(context, "Git: clean", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.AccountTree, contentDescription = "Git", tint = Color(0xFFFB923C), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.BOT_CHAT -> {
                            IconButton(onClick = { /* Clear chat */ Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Chat", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Export chat */ Toast.makeText(context, "Chat exported", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = "Export", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onOpenConsole?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Terminal, contentDescription = "Terminal", tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.PDF_VIEWER -> {
                            IconButton(onClick = { /* Zoom in */ Toast.makeText(context, "Zoom In", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Zoom out */ Toast.makeText(context, "Zoom Out", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Bookmark */ Toast.makeText(context, "Page bookmarked", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.BookmarkAdd, contentDescription = "Bookmark", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Search in PDF */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.IMAGE_VIEWER -> {
                            IconButton(onClick = { /* Rotate */ Toast.makeText(context, "Rotated 90°", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate", tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Crop */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Crop, contentDescription = "Crop", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* EXIF info */ Toast.makeText(context, "EXIF Metadata", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Info, contentDescription = "EXIF Info", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.MEDIA_PLAYER -> {
                            IconButton(onClick = { /* Volume */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.VolumeUp, contentDescription = "Volume", tint = Color(0xFFEC4899), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Loop */ Toast.makeText(context, "Loop toggled", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Loop, contentDescription = "Loop", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Speed */ Toast.makeText(context, "1.0x", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Speed, contentDescription = "Speed", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.MARKDOWN -> {
                            IconButton(onClick = { /* Toggle preview */ Toast.makeText(context, "Preview toggled", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Visibility, contentDescription = "Preview", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Export */ Toast.makeText(context, "Exported to PDF", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.FileDownload, contentDescription = "Export", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onFormatCode?.invoke() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.FormatAlignLeft, contentDescription = "Format", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                        XcodeWorkspaceMode.DATA_INSPECTOR -> {
                            IconButton(onClick = { /* Search keys */ }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = Color(0xFF06B6D4), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Copy JSON */ Toast.makeText(context, "JSON copied", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { /* Validate */ Toast.makeText(context, "Valid JSON ✓", Toast.LENGTH_SHORT).show() }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = "Validate", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // === SHARED: Expand / Collapse Live Drawer ===
                    val rotation by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f, label = "chevron")
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp).testTag("dev_bar_expand_toggle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse Tools" else "Expand Tools",
                            tint = if (isExpanded) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp).rotate(rotation)
                        )
                    }

                    // === SHARED: DEV ON/OFF Toggle Badge Pill ===
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF064E3B),
                        border = BorderStroke(1.dp, Color(0xFF10B981)),
                        modifier = Modifier.clickable { onToggleOff() }.testTag("dev_bar_toggle_off_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFF34D399), CircleShape))
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

            // ================= EXPANDABLE DEV DRAWER (Mode-Adaptive) =================
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

                    when (workspaceMode) {
                        // ======== BROWSER: Full multi-tab Web DevTools (unchanged) ========
                        XcodeWorkspaceMode.BROWSER -> {
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
                                            Icon(imageVector = tabType.icon, contentDescription = tabType.label, tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B), modifier = Modifier.size(14.dp))
                                            Text(text = tabType.label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color(0xFFE2E8F0) else Color(0xFF64748B))
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

                        // ======== CODE EDITOR: Problems / Output / Git ========
                        XcodeWorkspaceMode.CODE_EDITOR -> {
                            CodeEditorDrawerPanel(
                                activeFileName = activeFileName,
                                isFileDirty = isFileDirty
                            )
                        }

                        // ======== BOT CHAT: Prompt Library / Model Info ========
                        XcodeWorkspaceMode.BOT_CHAT -> {
                            BotChatDrawerPanel(activeBotPersona = activeBotPersona)
                        }

                        // ======== PDF VIEWER: Document Info / Thumbnails ========
                        XcodeWorkspaceMode.PDF_VIEWER -> {
                            FileInfoDrawerPanel(
                                fileName = activeFileName ?: "document.pdf",
                                fileType = "PDF Document",
                                accentColor = Color(0xFFEF4444),
                                icon = Icons.Rounded.PictureAsPdf,
                                details = listOf(
                                    "FORMAT" to "PDF (Portable Document Format)",
                                    "PAGES" to "Scanning...",
                                    "ENCRYPTION" to "None",
                                    "PRODUCER" to "GVONE Document Engine"
                                )
                            )
                        }

                        // ======== IMAGE VIEWER: EXIF / Dimensions / Histogram ========
                        XcodeWorkspaceMode.IMAGE_VIEWER -> {
                            FileInfoDrawerPanel(
                                fileName = activeFileName ?: "preview.png",
                                fileType = "Image Asset",
                                accentColor = Color(0xFFF59E0B),
                                icon = Icons.Rounded.Image,
                                details = listOf(
                                    "FORMAT" to "PNG (Portable Network Graphics)",
                                    "DIMENSIONS" to "Analyzing...",
                                    "COLOR SPACE" to "sRGB",
                                    "BIT DEPTH" to "32-bit RGBA",
                                    "DPI" to "72 ppi"
                                )
                            )
                        }

                        // ======== MEDIA PLAYER: Track Metadata / Waveform ========
                        XcodeWorkspaceMode.MEDIA_PLAYER -> {
                            FileInfoDrawerPanel(
                                fileName = activeFileName ?: "sample_track.wav",
                                fileType = "Audio Track",
                                accentColor = Color(0xFFEC4899),
                                icon = Icons.Rounded.PlayCircle,
                                details = listOf(
                                    "FORMAT" to "WAV (Waveform Audio)",
                                    "SAMPLE RATE" to "44.1 kHz",
                                    "BIT DEPTH" to "16-bit PCM",
                                    "CHANNELS" to "Stereo (2ch)",
                                    "DURATION" to "Loading..."
                                )
                            )
                        }

                        // ======== MARKDOWN: Live Preview / Outline ========
                        XcodeWorkspaceMode.MARKDOWN -> {
                            FileInfoDrawerPanel(
                                fileName = activeFileName ?: "architecture.md",
                                fileType = "Markdown Document",
                                accentColor = Color(0xFF10B981),
                                icon = Icons.Rounded.Description,
                                details = listOf(
                                    "FORMAT" to "Markdown (GFM)",
                                    "HEADINGS" to "Scanning...",
                                    "LINKS" to "Analyzing...",
                                    "CODE BLOCKS" to "Counting...",
                                    "RENDERER" to "GVONE Markdown Engine"
                                )
                            )
                        }

                        // ======== DATA INSPECTOR: Schema / Tree / Raw ========
                        XcodeWorkspaceMode.DATA_INSPECTOR -> {
                            FileInfoDrawerPanel(
                                fileName = activeFileName ?: "package.json",
                                fileType = "JSON Data",
                                accentColor = Color(0xFF06B6D4),
                                icon = Icons.Rounded.DataObject,
                                details = listOf(
                                    "FORMAT" to "JSON (JavaScript Object Notation)",
                                    "TOP-LEVEL KEYS" to "Parsing...",
                                    "DEPTH" to "Analyzing...",
                                    "ENCODING" to "UTF-8",
                                    "VALIDATOR" to "JSON Schema Draft 7"
                                )
                            )
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

@Composable
private fun TargetSectionHeader(
    title: String,
    badge: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF090E17))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
            if (badge != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = badge,
                        color = Color(0xFF38BDF8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                color = Color(0xFF38BDF8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TargetMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) Color(0xFF1A263D) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = iconTint.copy(alpha = 0.15f),
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (isActive) Color.White else Color(0xFFE2E8F0),
                    fontSize = 12.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isActive) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Active",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ========================= NEW MODE-ADAPTIVE DRAWER PANELS =========================

@Composable
private fun CodeEditorDrawerPanel(
    activeFileName: String?,
    isFileDirty: Boolean
) {
    val outputLines = remember {
        mutableStateListOf(
            "⟩ Kotlin compile daemon ready",
            "⟩ KSP annotation processor: OK",
            "⟩ compileDebugKotlin: UP-TO-DATE",
            "✓ Build successful"
        )
    }
    val drawerTabs = listOf("Problems", "Output", "Git Changes")
    var selectedDrawerTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        // Tab row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D121D))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tabIcons = listOf(Icons.Rounded.ErrorOutline, Icons.Rounded.Terminal, Icons.Rounded.AccountTree)
            val tabColors = listOf(Color(0xFFF87171), Color(0xFF34D399), Color(0xFFFB923C))
            drawerTabs.forEachIndexed { i, label ->
                val sel = selectedDrawerTab == i
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (sel) Color(0xFF1E293B) else Color.Transparent,
                    border = if (sel) BorderStroke(1.dp, tabColors[i]) else BorderStroke(0.5.dp, Color(0xFF1E293B)),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { selectedDrawerTab = i }.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(tabIcons[i], contentDescription = label, tint = if (sel) tabColors[i] else Color(0xFF64748B), modifier = Modifier.size(14.dp))
                        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) Color(0xFFE2E8F0) else Color(0xFF64748B))
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            when (selectedDrawerTab) {
                0 -> { // Problems
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF04060A), border = BorderStroke(0.5.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxSize()) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(28.dp))
                                Text("No problems detected", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF64748B))
                                if (activeFileName != null) Text("Active: $activeFileName${if (isFileDirty) " •" else ""}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
                1 -> { // Output
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF04060A), border = BorderStroke(0.5.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxSize()) {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(outputLines) { line ->
                                Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = if (line.startsWith("✓")) Color(0xFF4ADE80) else Color(0xFF64748B))
                            }
                        }
                    }
                }
                2 -> { // Git Changes
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF04060A), border = BorderStroke(0.5.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("BRANCH: main", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFFB923C))
                            Text("STATUS: 1 modified file", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFFFBBF24))
                            if (activeFileName != null) Text("M  $activeFileName", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF34D399))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotChatDrawerPanel(activeBotPersona: GrokBotPersona?) {
    val drawerTabs = listOf("Prompt Library", "Model Info")
    var selectedDrawerTab by remember { mutableStateOf(0) }

    val prompts = remember {
        listOf(
            "Explain this code step by step",
            "Find all bugs in this file",
            "Refactor for better readability",
            "Write unit tests for this function",
            "Generate JSDoc / KDoc documentation",
            "Optimize for performance",
            "Convert to Kotlin coroutines"
        )
    }

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D121D))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tabIcons = listOf(Icons.Rounded.AutoAwesome, Icons.Rounded.Info)
            val tabColor = Color(0xFF00E5FF)
            drawerTabs.forEachIndexed { i, label ->
                val sel = selectedDrawerTab == i
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (sel) Color(0xFF1E293B) else Color.Transparent,
                    border = if (sel) BorderStroke(1.dp, tabColor) else BorderStroke(0.5.dp, Color(0xFF1E293B)),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { selectedDrawerTab = i }.padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(tabIcons[i], contentDescription = label, tint = if (sel) tabColor else Color(0xFF64748B), modifier = Modifier.size(14.dp))
                        Text(label, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, color = if (sel) Color(0xFFE2E8F0) else Color(0xFF64748B))
                    }
                }
            }
        }
        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            when (selectedDrawerTab) {
                0 -> { // Prompt Library
                    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(prompts) { prompt ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF0F1523),
                                border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth().clickable { /* inject prompt */ }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                                    Text(prompt, fontSize = 11.sp, color = Color(0xFFCBD5E1), modifier = Modifier.weight(1f))
                                    Icon(Icons.Rounded.ArrowForward, contentDescription = "Use", tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
                1 -> { // Model Info
                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF04060A), border = BorderStroke(0.5.dp, Color(0xFF1E293B)), modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("ACTIVE PERSONA: ${activeBotPersona?.displayName ?: "None"}", fontFamily = FontFamily.Monospace, fontSize = 10.5.sp, color = Color(0xFF00E5FF))
                            Text("SPECIALIZATION: ${activeBotPersona?.tagline ?: "General AI"}", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Text("BACKEND: Firebase Gemini AI", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF64748B))
                            Text("CONTEXT: Workspace file + terminal CWD", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF64748B))
                            Text("ACTUATORS: @code @browser @file @cmd @search", fontFamily = FontFamily.Monospace, fontSize = 9.5.sp, color = Color(0xFF475569))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileInfoDrawerPanel(
    fileName: String,
    fileType: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    details: List<Pair<String, String>>
) {
    Column(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // File header card
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF0F1523),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = accentColor.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(fileName, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(fileType, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = accentColor)
                }
            }
        }
        // Detail rows
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFF04060A),
            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(details) { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.weight(0.45f)
                        )
                        Text(
                            text = value,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = Color(0xFFCBD5E1),
                            modifier = Modifier.weight(0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    HorizontalDivider(color = Color(0xFF1A2230), thickness = 0.5.dp)
                }
            }
        }
    }
}
