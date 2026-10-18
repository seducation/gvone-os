package com.example.ui.screens.ide

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.files.FileType
import com.example.data.files.GVONEFileItem
import com.example.data.files.GVONEFileSystem
import com.example.data.files.StorageLocation
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Universal VS Code-style IDE Screen for GVONE Browser.
 * Features multi-tab editor, line numbers, syntax highlighting, activity bar,
 * collapsible sidebar, dockable terminal/output panel, status bar, and command palette.
 */
@Composable
fun GVONEIdeScreen(
    viewModel: BrowserViewModel,
    fileSystem: GVONEFileSystem,
    onCloseIde: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val terminalLines by viewModel.terminalLines.collectAsState()
    val pendingFile by viewModel.pendingIdeFile.collectAsState()

    // Workspace & Editor Tabs state
    var openTabs by remember { mutableStateOf<List<IdeTab>>(emptyList()) }
    var activeTabId by remember { mutableStateOf<String?>(null) }
    val activeTab = remember(openTabs, activeTabId) {
        openTabs.find { it.id == activeTabId } ?: openTabs.firstOrNull()
    }

    // Sidebar & Bottom Panel layout state
    var activeActivityTab by remember { mutableStateOf(IdeActivityTab.EXPLORER) }
    var isSidebarOpen by remember { mutableStateOf(true) }
    var activeBottomTab by remember { mutableStateOf(IdeBottomTab.TERMINAL) }
    var isBottomPanelOpen by remember { mutableStateOf(false) }
    var isBottomPanelMaximized by remember { mutableStateOf(false) }

    // Quick Command Palette Modal state
    var isCommandPaletteOpen by remember { mutableStateOf(false) }
    var commandPaletteQuery by remember { mutableStateOf("") }

    // Text editor buffer for active tab
    var editorTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }

    // Synchronize editor buffer when active tab switches
    LaunchedEffect(activeTab?.id) {
        if (activeTab != null) {
            editorTextFieldValue = TextFieldValue(activeTab.content)
        }
    }

    // Helper: Open a file in an editor tab
    val openFileInTab: (GVONEFileItem) -> Unit = { item ->
        coroutineScope.launch {
            val existing = openTabs.find { it.id == item.path }
            if (existing != null) {
                activeTabId = existing.id
            } else {
                val content = fileSystem.readFileContent(item.path)
                val newTab = IdeTab(
                    id = item.path,
                    fileItem = item,
                    content = content,
                    originalContent = content
                )
                openTabs = openTabs + newTab
                activeTabId = newTab.id
            }
        }
    }

    // Helper: Close an editor tab
    val closeTab: (String) -> Unit = { tabId ->
        val remaining = openTabs.filterNot { it.id == tabId }
        openTabs = remaining
        if (activeTabId == tabId) {
            activeTabId = remaining.lastOrNull()?.id
        }
    }

    // Helper: Save current active file
    val saveActiveFile: () -> Unit = {
        activeTab?.let { tab ->
            val updatedContent = editorTextFieldValue.text
            coroutineScope.launch {
                val success = fileSystem.writeFileContent(tab.fileItem.path, updatedContent)
                if (success) {
                    openTabs = openTabs.map {
                        if (it.id == tab.id) it.copy(content = updatedContent, originalContent = updatedContent)
                        else it
                    }
                    Toast.makeText(context, "Saved ${tab.fileItem.name}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Helper: Format active document (indentation clean up)
    val formatActiveDocument: () -> Unit = {
        val currentText = editorTextFieldValue.text
        if (currentText.isNotBlank()) {
            val lines = currentText.lines()
            var indentLevel = 0
            val formatted = lines.joinToString("\n") { rawLine ->
                val trimmed = rawLine.trim()
                if (trimmed.startsWith("}") || trimmed.startsWith(")") || trimmed.startsWith("]")) {
                    indentLevel = (indentLevel - 1).coerceAtLeast(0)
                }
                val indented = "    ".repeat(indentLevel) + trimmed
                if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[")) {
                    indentLevel++
                }
                indented
            }
            editorTextFieldValue = TextFieldValue(formatted)
            activeTab?.let { tab ->
                openTabs = openTabs.map {
                    if (it.id == tab.id) it.copy(content = formatted) else it
                }
            }
            Toast.makeText(context, "Formatted document", Toast.LENGTH_SHORT).show()
        }
    }

    // Initial load: Open starter project file if tabs are empty
    LaunchedEffect(Unit) {
        if (openTabs.isEmpty()) {
            val starterItem = fileSystem.getFileItem("Projects/KotlinCalculator/src/Main.kt")
                ?: fileSystem.getFileItem("Documents/notes.md")
            if (starterItem != null) {
                openFileInTab(starterItem)
            }
        }
    }

    // Open pending file triggered from Navigator Panel (Tabs / Files / Chats)
    LaunchedEffect(pendingFile) {
        pendingFile?.let { file ->
            openFileInTab(file)
            viewModel.clearPendingIdeFile()
        }
    }

    // Handle back button behavior
    BackHandler {
        when {
            isCommandPaletteOpen -> isCommandPaletteOpen = false
            isBottomPanelMaximized -> isBottomPanelMaximized = false
            isBottomPanelOpen -> isBottomPanelOpen = false
            else -> onCloseIde()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("gvone_ide_screen"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ================= TOP TITLE & MENU BAR =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Brand & Breadcrumbs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Code,
                                    contentDescription = "Code Studio",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Code Studio",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (activeTab != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "›",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeTab.fileItem.path,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Center Search / Command Palette Bar
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isCommandPaletteOpen = true }
                            .testTag("ide_top_command_palette_trigger"),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GVONE Code Studio (Ctrl+P)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Right Actions (Format, Save, Terminal Toggle, Close)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = formatActiveDocument,
                            enabled = activeTab != null,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.FormatAlignLeft,
                                contentDescription = "Format Document",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = saveActiveFile,
                            enabled = activeTab != null,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("ide_save_button")
                        ) {
                            Icon(
                                Icons.Rounded.Save,
                                contentDescription = "Save File",
                                tint = if (activeTab?.isDirty == true) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = { isBottomPanelOpen = !isBottomPanelOpen },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("ide_toggle_panel_button")
                        ) {
                            Icon(
                                Icons.Rounded.Dock,
                                contentDescription = "Toggle Terminal Panel",
                                tint = if (isBottomPanelOpen) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onCloseIde,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("ide_close_button")
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Close IDE",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ================= WORKSPACE BODY (Sidebar + Editor Group) =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Activity Bar + Collapsible Sidebar
                IdeSidebar(
                    activeActivityTab = activeActivityTab,
                    onSelectActivityTab = { activeActivityTab = it },
                    isSidebarOpen = isSidebarOpen,
                    onToggleSidebar = { isSidebarOpen = !isSidebarOpen },
                    fileSystem = fileSystem,
                    activeTab = activeTab,
                    openTabs = openTabs,
                    onOpenFile = openFileInTab,
                    onApplyAiCodeToEditor = { aiCode ->
                        editorTextFieldValue = TextFieldValue(aiCode)
                        activeTab?.let { tab ->
                            openTabs = openTabs.map {
                                if (it.id == tab.id) it.copy(content = aiCode) else it
                            }
                        }
                    },
                    onOpenCommandPalette = { isCommandPaletteOpen = true },
                    onFormatDocument = formatActiveDocument,
                    onLivePreview = { fileItem ->
                        viewModel.createNewTab(url = "file://${fileItem.path}", isPrivate = false)
                        viewModel.closeSheet()
                    },
                    aiService = viewModel.aiService,
                    coroutineScope = coroutineScope
                )

                // Editor Group Column
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Tab Bar Strip
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            openTabs.forEach { tab ->
                                val isActive = tab.id == activeTabId
                                val fileType = tab.fileItem.fileType
                                val typeIcon = when (fileType) {
                                    FileType.CODE -> Icons.Rounded.Code
                                    FileType.JSON -> Icons.Rounded.DataArray
                                    FileType.MARKDOWN -> Icons.Rounded.Article
                                    else -> Icons.Rounded.Description
                                }

                                Surface(
                                    color = if (isActive) MaterialTheme.colorScheme.surface
                                    else Color.Transparent,
                                    border = if (isActive) BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ) else null,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .clickable { activeTabId = tab.id }
                                        .testTag("ide_tab_${tab.fileItem.name}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = typeIcon,
                                            contentDescription = null,
                                            tint = if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = tab.fileItem.name,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                            ),
                                            color = if (isActive) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Dirty dot indicator or Close tab button
                                        if (tab.isDirty) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.primary,
                                                        CircleShape
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }

                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Close tab",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .clickable { closeTab(tab.id) }
                                        )
                                    }
                                }
                            }

                            // Add New Tab button
                            IconButton(
                                onClick = {
                                    val newFileItem = GVONEFileItem(
                                        id = "untitled_${System.currentTimeMillis()}.txt",
                                        name = "Untitled-${openTabs.size + 1}.txt",
                                        path = "Untitled-${openTabs.size + 1}.txt",
                                        absolutePath = "",
                                        parentPath = "",
                                        isDirectory = false,
                                        size = 0,
                                        formattedSize = "0 B",
                                        lastModified = System.currentTimeMillis(),
                                        formattedDate = "",
                                        extension = "txt",
                                        fileType = FileType.TEXT
                                    )
                                    openFileInTab(newFileItem)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = "New Tab",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Main Editor Buffer Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (activeTab == null) {
                            // VS Code Welcome Empty State
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Rounded.Terminal,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "GVONE Code Studio",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Select a file from Explorer or use Ctrl+P to search",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { isCommandPaletteOpen = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Command Palette")
                                    }
                                }
                            }
                        } else {
                            // Active Code Editor Buffer with Line Numbers
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                val text = editorTextFieldValue.text
                                val lineCount = text.lines().size.coerceAtLeast(1)

                                // Line Numbers Gutter
                                Surface(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .fillMaxHeight(),
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp, end = 8.dp),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        for (i in 1..lineCount) {
                                            Text(
                                                text = "$i",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    lineHeight = 18.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }

                                VerticalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                    thickness = 1.dp
                                )

                                // Monospace Code Text Field
                                BasicTextField(
                                    value = editorTextFieldValue,
                                    onValueChange = { newValue ->
                                        editorTextFieldValue = newValue
                                        openTabs = openTabs.map {
                                            if (it.id == activeTab.id) it.copy(content = newValue.text)
                                            else it
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 40.dp)
                                        .testTag("ide_code_editor_buffer")
                                )
                            }
                        }
                    }

                    // Dockable Bottom Panel (Terminal / Output / AI Review)
                    IdeBottomPanel(
                        activeBottomTab = activeBottomTab,
                        onSelectBottomTab = { activeBottomTab = it },
                        isPanelOpen = isBottomPanelOpen,
                        onClosePanel = { isBottomPanelOpen = false },
                        isMaximized = isBottomPanelMaximized,
                        onToggleMaximize = { isBottomPanelMaximized = !isBottomPanelMaximized },
                        terminalLines = terminalLines,
                        onExecuteTerminalCommand = { cmd ->
                            viewModel.terminalCommandExecutor.executeCommand(cmd)
                        },
                        onClearTerminal = { viewModel.clearTerminalLines() },
                        activeTab = activeTab,
                        aiService = viewModel.aiService,
                        coroutineScope = coroutineScope
                    )
                }
            }

            // ================= STATUS BAR (VS Code Bottom Rail) =================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .testTag("ide_status_bar"),
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left items: Git Branch, Problems
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.CloudSync,
                            contentDescription = "Drive Cloud Sync",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Drive: main (Synced)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = "Errors",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            Icons.Rounded.WarningAmber,
                            contentDescription = "Warnings",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Right items: Ln/Col, Spaces, Encoding, Language
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val currentText = editorTextFieldValue.text
                        val selection = editorTextFieldValue.selection.start
                        val lineIndex = currentText.substring(0, selection.coerceAtMost(currentText.length)).lines().size
                        val colIndex = (selection - currentText.substring(0, selection.coerceAtMost(currentText.length)).lastIndexOf('\n')).coerceAtLeast(1)

                        Text(
                            text = "Ln $lineIndex, Col $colIndex",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "Spaces: 4",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = "UTF-8",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        val langMode = when (activeTab?.fileItem?.extension?.lowercase()) {
                            "kt", "kts" -> "Kotlin"
                            "py" -> "Python"
                            "js", "ts" -> "JavaScript"
                            "html", "htm" -> "HTML"
                            "json" -> "JSON"
                            "md" -> "Markdown"
                            "sh" -> "Shell"
                            else -> "Plain Text"
                        }

                        Text(
                            text = langMode,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }

    // ================= COMMAND PALETTE MODAL (Ctrl+P / >) =================
    if (isCommandPaletteOpen) {
        Dialog(onDismissRequest = { isCommandPaletteOpen = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .testTag("ide_command_palette_modal"),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Search bar
                    OutlinedTextField(
                        value = commandPaletteQuery,
                        onValueChange = { commandPaletteQuery = it },
                        placeholder = { Text("Type '>' for commands, or file name...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (commandPaletteQuery.isNotEmpty()) {
                                IconButton(onClick = { commandPaletteQuery = "" }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ide_command_palette_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val isCommandMode = commandPaletteQuery.startsWith(">")
                    val query = commandPaletteQuery.removePrefix(">").trim()

                    val allCommands = remember {
                        listOf(
                            IdeCommand("save", "File: Save Active File", "File", "Ctrl+S", Icons.Rounded.Save) {
                                saveActiveFile()
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("format", "Format: Format Document", "Editor", "Shift+Alt+F", Icons.Rounded.FormatAlignLeft) {
                                formatActiveDocument()
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("terminal", "View: Toggle Terminal Panel", "View", "Ctrl+`", Icons.Rounded.Terminal) {
                                isBottomPanelOpen = !isBottomPanelOpen
                                activeBottomTab = IdeBottomTab.TERMINAL
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("drive_sync", "Drive: Sync Project to Google Drive", "Drive", "Ctrl+Shift+D", Icons.Rounded.CloudSync) {
                                activeActivityTab = IdeActivityTab.SOURCE_CONTROL
                                isSidebarOpen = true
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("git_status", "Drive: Open Drive & Git Control", "Drive", "Ctrl+Shift+G", Icons.Rounded.CloudSync) {
                                activeActivityTab = IdeActivityTab.SOURCE_CONTROL
                                isSidebarOpen = true
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("ai_review", "AI: Run Code Audit & Review", "Copilot", "Ctrl+Shift+I", Icons.Rounded.AutoAwesome) {
                                isBottomPanelOpen = true
                                activeBottomTab = IdeBottomTab.AI_REVIEW
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("clear_terminal", "Terminal: Clear Terminal Buffer", "Terminal", null, Icons.Rounded.DeleteOutline) {
                                viewModel.clearTerminalLines()
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("close_tab", "View: Close Active Tab", "View", "Ctrl+W", Icons.Rounded.Close) {
                                activeTab?.id?.let { closeTab(it) }
                                isCommandPaletteOpen = false
                            },
                            IdeCommand("close_ide", "View: Return to Web Browser", "Navigation", "Esc", Icons.Rounded.ExitToApp) {
                                isCommandPaletteOpen = false
                                onCloseIde()
                            }
                        )
                    }

                    if (isCommandMode) {
                        val filteredCommands = allCommands.filter {
                            query.isEmpty() || it.title.contains(query, ignoreCase = true)
                        }

                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(filteredCommands) { cmd ->
                                Surface(
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { cmd.action() }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = cmd.icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = cmd.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        cmd.shortcut?.let { sc ->
                                            Text(
                                                text = sc,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // File quick jump search
                        var workspaceFiles by remember { mutableStateOf<List<GVONEFileItem>>(emptyList()) }
                        LaunchedEffect(Unit) {
                            val files = fileSystem.listFiles(StorageLocation.PROJECTS, "") +
                                    fileSystem.listFiles(StorageLocation.MY_FILES, "Documents")
                            workspaceFiles = files.filterNot { it.isDirectory }
                        }

                        val filteredFiles = workspaceFiles.filter {
                            query.isEmpty() || it.name.contains(query, ignoreCase = true)
                        }

                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(filteredFiles) { file ->
                                Surface(
                                    color = Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            openFileInTab(file)
                                            isCommandPaletteOpen = false
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = file.name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            Text(
                                                text = file.path,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
