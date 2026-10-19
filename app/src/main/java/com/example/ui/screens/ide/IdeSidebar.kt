package com.example.ui.screens.ide

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.rounded.Send
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
import com.example.data.ai.GVONEAIService
import com.example.data.files.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity Bar & Collapsible Sidebar for the VS Code-style IDE.
 */
@Composable
fun IdeSidebar(
    activeActivityTab: IdeActivityTab,
    onSelectActivityTab: (IdeActivityTab) -> Unit,
    isSidebarOpen: Boolean,
    onToggleSidebar: () -> Unit,
    fileSystem: GVONEFileSystem,
    activeTab: IdeTab?,
    openTabs: List<IdeTab> = emptyList(),
    onOpenFile: (GVONEFileItem) -> Unit,
    onApplyAiCodeToEditor: (String) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onFormatDocument: () -> Unit = {},
    onLivePreview: (GVONEFileItem) -> Unit = {},
    aiService: GVONEAIService,
    coroutineScope: CoroutineScope,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Explorer tree nodes
    var workspaceNodes by remember { mutableStateOf<List<IdeTreeNode>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }

    // Dialogs for file/folder creation
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newItemName by remember { mutableStateOf("") }
    var selectedTargetFolder by remember { mutableStateOf("Projects") }

    // Global Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<IdeSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Git / Source Control state
    var gitBranch by remember { mutableStateOf("main") }
    var gitChangedFiles by remember { mutableStateOf<List<GVONEFileItem>>(emptyList()) }
    var gitCommitMessage by remember { mutableStateOf("") }
    var isCommitting by remember { mutableStateOf(false) }

    // Extensions state
    val extensions = remember {
        mutableStateListOf(
            IdeExtension("prettier", "Prettier - Code Formatter", "v3.2.0", "Prettier Team", "Opinionated code formatter for Kotlin, Python, JS, HTML, CSS, JSON", Icons.Rounded.FormatPaint, true, "Formatters"),
            IdeExtension("eslint", "ESLint & Code Linter", "v8.56.0", "OpenJS Foundation", "Identifies syntax errors, code smells, and TODO/FIXME markers", Icons.Rounded.Rule, true, "Linters"),
            IdeExtension("gitlens", "GitLens - Git supercharged", "v14.0.0", "GitKraken", "Supercharge Git with blame annotations, commit history, and diff navigation", Icons.Rounded.AccountTree, true, "Git"),
            IdeExtension("live_server", "Live Preview Web Server", "v0.4.0", "Microsoft", "Instant local live preview server for HTML, CSS, and JS web projects", Icons.Rounded.Preview, true, "Preview"),
            IdeExtension("language_tools", "Language Tools Pack", "v2.1.0", "GVONE Team", "Enhanced autocomplete snippets and syntax support for Kotlin, Python, JS", Icons.Rounded.Code, true, "Languages")
        )
    }

    // AI Assistant state
    var aiPrompt by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }

    // Load project directory tree
    val refreshFiles: () -> Unit = {
        isLoadingFiles = true
        coroutineScope.launch {
            try {
                val projects = fileSystem.listFiles(StorageLocation.PROJECTS, "")
                val docs = fileSystem.listFiles(StorageLocation.MY_FILES, "Documents")
                val combined = (projects + docs).distinctBy { it.path }

                val rootNodes = combined.map { item ->
                    IdeTreeNode(
                        item = item,
                        level = 0,
                        isExpanded = item.name == "KotlinCalculator" || item.name == "src",
                        children = emptyList()
                    )
                }
                workspaceNodes = rootNodes
            } catch (_: Exception) {
            } finally {
                isLoadingFiles = false
            }
        }
    }

    // Load Git status
    val refreshGit: () -> Unit = {
        coroutineScope.launch {
            try {
                val repoInfo = fileSystem.gitManager.getProjectRepoInfo("Projects")
                gitBranch = repoInfo.currentBranch
                val uncommittedFiles = fileSystem.gitManager.calculateUncommittedChanges("Projects")
                val modified = uncommittedFiles.mapNotNull { file ->
                    val relPath = file.relativeTo(fileSystem.getRootDir()).path
                    fileSystem.getFileItem(relPath)
                }
                gitChangedFiles = modified
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshFiles()
        refreshGit()
    }

    Row(modifier = modifier.fillMaxHeight()) {
        // ================= ACTIVITY BAR (VS Code Leftmost Vertical Rail) =================
        Surface(
            modifier = Modifier
                .width(50.dp)
                .fillMaxHeight()
                .testTag("ide_activity_bar"),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top activity icons
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IdeActivityTab.values().forEach { tab ->
                        val isSelected = isSidebarOpen && activeActivityTab == tab
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    if (activeActivityTab == tab && isSidebarOpen) {
                                        onToggleSidebar()
                                    } else {
                                        onSelectActivityTab(tab)
                                        if (!isSidebarOpen) onToggleSidebar()
                                    }
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(3.dp)
                                        .height(24.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primary,
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                // Bottom activity actions (Command Palette & Toggle)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onOpenCommandPalette,
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("ide_activity_cmd_palette")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = "Command Palette",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleSidebar,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isSidebarOpen) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
                            contentDescription = "Toggle Sidebar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ================= PRIMARY SIDEBAR CONTENT =================
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = expandHorizontally() + fadeIn(),
            exit = shrinkHorizontally() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .width(260.dp)
                    .fillMaxHeight()
                    .testTag("ide_primary_sidebar"),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Sidebar Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeActivityTab.label.uppercase(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Action icons based on active tab
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (activeActivityTab) {
                                IdeActivityTab.EXPLORER -> {
                                    IconButton(
                                        onClick = {
                                            newItemName = ""
                                            showNewFileDialog = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.NoteAdd,
                                            contentDescription = "New File",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            newItemName = ""
                                            showNewFolderDialog = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.CreateNewFolder,
                                            contentDescription = "New Folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { refreshFiles() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Refresh,
                                            contentDescription = "Refresh",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IdeActivityTab.SOURCE_CONTROL -> {
                                    IconButton(
                                        onClick = { refreshGit() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Refresh,
                                            contentDescription = "Refresh Git",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IdeActivityTab.SEARCH -> {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            searchResults = emptyList()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.ClearAll,
                                            contentDescription = "Clear Search",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IdeActivityTab.AI_ASSISTANT -> {
                                    IconButton(
                                        onClick = {
                                            aiPrompt = ""
                                            aiResponse = ""
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.DeleteSweep,
                                            contentDescription = "Clear Chat",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IdeActivityTab.EXTENSIONS -> {
                                    IconButton(
                                        onClick = {
                                            Toast.makeText(context, "Extensions Marketplace up to date", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Refresh,
                                            contentDescription = "Refresh Extensions",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 1.dp
                    )

                    // Sidebar Tab Content Body
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (activeActivityTab) {
                            IdeActivityTab.EXPLORER -> {
                                ExplorerView(
                                    nodes = workspaceNodes,
                                    activeTab = activeTab,
                                    isLoading = isLoadingFiles,
                                    onOpenFile = onOpenFile,
                                    onDeleteFile = { item ->
                                        coroutineScope.launch {
                                            fileSystem.deleteItem(item.path)
                                            refreshFiles()
                                        }
                                    }
                                )
                            }
                            IdeActivityTab.SEARCH -> {
                                SearchView(
                                    searchQuery = searchQuery,
                                    onSearchQueryChanged = { query ->
                                        searchQuery = query
                                        if (query.length >= 2) {
                                            isSearching = true
                                            coroutineScope.launch {
                                                try {
                                                    val results = mutableListOf<IdeSearchResult>()
                                                    val allFiles = fileSystem.listFiles(StorageLocation.PROJECTS, "") +
                                                            fileSystem.listFiles(StorageLocation.MY_FILES, "Documents")
                                                    for (file in allFiles) {
                                                        if (!file.isDirectory) {
                                                            val content = fileSystem.readFileContent(file.path)
                                                            content.lines().forEachIndexed { index, line ->
                                                                if (line.contains(query, ignoreCase = true)) {
                                                                    results.add(
                                                                        IdeSearchResult(
                                                                            file = file,
                                                                            lineNumber = index + 1,
                                                                            lineContent = line.trim()
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                    searchResults = results
                                                } finally {
                                                    isSearching = false
                                                }
                                            }
                                        } else {
                                            searchResults = emptyList()
                                        }
                                    },
                                    searchResults = searchResults,
                                    isSearching = isSearching,
                                    onSelectResult = { res ->
                                        onOpenFile(res.file)
                                    }
                                )
                            }
                            IdeActivityTab.SOURCE_CONTROL -> {
                                SourceControlView(
                                    branch = gitBranch,
                                    changedFiles = gitChangedFiles,
                                    commitMessage = gitCommitMessage,
                                    onCommitMessageChange = { gitCommitMessage = it },
                                    isCommitting = isCommitting,
                                    onCommit = {
                                        if (gitCommitMessage.isBlank()) {
                                            Toast.makeText(context, "Please enter a commit message", Toast.LENGTH_SHORT).show()
                                            return@SourceControlView
                                        }
                                        isCommitting = true
                                        coroutineScope.launch {
                                            try {
                                                fileSystem.gitManager.commitChanges(
                                                    projectPath = "Projects",
                                                    message = gitCommitMessage
                                                )
                                                gitCommitMessage = ""
                                                refreshGit()
                                                Toast.makeText(context, "Committed to $gitBranch", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Commit recorded", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                isCommitting = false
                                            }
                                        }
                                    },
                                    onOpenFile = onOpenFile
                                )
                            }
                            IdeActivityTab.AI_ASSISTANT -> {
                                IdeGrokBotsView(
                                    activeTab = activeTab,
                                    openTabs = openTabs,
                                    aiService = aiService,
                                    coroutineScope = coroutineScope,
                                    onApplyCodeToEditor = onApplyAiCodeToEditor,
                                    onCreateFileFromCode = { filename, code ->
                                        coroutineScope.launch {
                                            val dir = "Projects"
                                            fileSystem.createFile(dir, filename, code)
                                            refreshFiles()
                                            val item = fileSystem.getFileItem("$dir/$filename")
                                            if (item != null) onOpenFile(item)
                                            Toast.makeText(context, "Created $filename in Projects", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                            IdeActivityTab.EXTENSIONS -> {
                                IdeExtensionsView(
                                    extensions = extensions,
                                    onToggleExtension = { extId ->
                                        val idx = extensions.indexOfFirst { it.id == extId }
                                        if (idx != -1) {
                                            val current = extensions[idx]
                                            extensions[idx] = current.copy(isEnabled = !current.isEnabled)
                                        }
                                    },
                                    onRunPrettier = {
                                        onFormatDocument()
                                        Toast.makeText(context, "Prettier format executed", Toast.LENGTH_SHORT).show()
                                    },
                                    onInspectLinter = {
                                        onOpenCommandPalette()
                                    },
                                    onOpenGitLens = {
                                        onSelectActivityTab(IdeActivityTab.SOURCE_CONTROL)
                                    },
                                    onLaunchLivePreview = {
                                        if (activeTab != null) {
                                            onLivePreview(activeTab.fileItem)
                                        } else {
                                            Toast.makeText(context, "Open a web file to live preview", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    activeTab = activeTab
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File") },
            text = {
                Column {
                    Text(
                        "Target directory: $selectedTargetFolder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newItemName,
                        onValueChange = { newItemName = it },
                        placeholder = { Text("e.g. App.kt, styles.css, script.py") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newItemName.trim()
                        if (name.isNotEmpty()) {
                            coroutineScope.launch {
                                val relPath = if (selectedTargetFolder.isEmpty()) name else "$selectedTargetFolder/$name"
                                fileSystem.writeFileContent(relPath, "// Created in GVONE Code Studio\n")
                                refreshFiles()
                                val created = fileSystem.getFileItem(relPath)
                                if (created != null) {
                                    onOpenFile(created)
                                }
                                showNewFileDialog = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    placeholder = { Text("e.g. components, utils, tests") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newItemName.trim()
                        if (name.isNotEmpty()) {
                            coroutineScope.launch {
                                val relPath = if (selectedTargetFolder.isEmpty()) name else "$selectedTargetFolder/$name"
                                fileSystem.createFolder(selectedTargetFolder, name)
                                refreshFiles()
                                showNewFolderDialog = false
                            }
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * File Explorer Tree View
 */
@Composable
private fun ExplorerView(
    nodes: List<IdeTreeNode>,
    activeTab: IdeTab?,
    isLoading: Boolean,
    onOpenFile: (GVONEFileItem) -> Unit,
    onDeleteFile: (GVONEFileItem) -> Unit
) {
    if (isLoading && nodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
        }
        return
    }

    if (nodes.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No workspace files found.\nTap + to create a file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ide_explorer_tree"),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(nodes, key = { it.item.path }) { node ->
            ExplorerItemRow(
                item = node.item,
                isSelected = activeTab?.id == node.item.path,
                onOpenFile = onOpenFile,
                onDelete = { onDeleteFile(node.item) }
            )
        }
    }
}

@Composable
private fun ExplorerItemRow(
    item: GVONEFileItem,
    isSelected: Boolean,
    onOpenFile: (GVONEFileItem) -> Unit,
    onDelete: () -> Unit
) {
    val fileIcon = when (item.fileType) {
        FileType.FOLDER -> Icons.Rounded.Folder
        FileType.CODE -> Icons.Rounded.Code
        FileType.JSON -> Icons.Rounded.DataArray
        FileType.MARKDOWN -> Icons.Rounded.Article
        FileType.IMAGE -> Icons.Rounded.Image
        else -> Icons.Rounded.Description
    }

    val iconColor = when (item.fileType) {
        FileType.FOLDER -> Color(0xFFF59E0B) // Amber
        FileType.CODE -> Color(0xFFA78BFA) // Kotlin purple
        FileType.JSON -> Color(0xFF10B981) // Green
        FileType.MARKDOWN -> Color(0xFF38BDF8) // Light blue
        FileType.IMAGE -> Color(0xFFEC4899) // Pink
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenFile(item) }
            .testTag("ide_tree_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = fileIcon,
                contentDescription = item.fileType.title,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (item.isDirectory) FontFamily.Default else FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            if (item.gitStatus == GitFileStatus.MODIFIED) {
                Text(
                    text = "M",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFEAB308),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * Global Search View
 */
@Composable
private fun SearchView(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    searchResults: List<IdeSearchResult>,
    isSearching: Boolean,
    onSelectResult: (IdeSearchResult) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Search files in workspace", fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ide_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (isSearching) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Searching workspace...", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text(
                text = "${searchResults.size} results found",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(searchResults) { res ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectResult(res) }
                        .padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = res.file.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = ":${res.lineNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = res.lineContent,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Git Source Control View
 */
@Composable
private fun SourceControlView(
    branch: String,
    changedFiles: List<GVONEFileItem>,
    commitMessage: String,
    onCommitMessageChange: (String) -> Unit,
    isCommitting: Boolean,
    onCommit: () -> Unit,
    onOpenFile: (GVONEFileItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Branch Badge
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.AltRoute,
                    contentDescription = "Branch",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = branch,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Commit Message Input
        OutlinedTextField(
            value = commitMessage,
            onValueChange = onCommitMessageChange,
            placeholder = { Text("Message (Ctrl+Enter to commit)", fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
                .testTag("ide_git_commit_msg")
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onCommit,
            enabled = !isCommitting && commitMessage.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ide_git_commit_button"),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isCommitting) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Commit Changes")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "CHANGES (${changedFiles.size})",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        if (changedFiles.isEmpty()) {
            Text(
                text = "No unstaged changes in working tree.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(changedFiles) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenFile(file) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "M",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFEAB308),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * AI Copilot / Assistant View
 */
@Composable
private fun AiAssistantView(
    activeTab: IdeTab?,
    prompt: String,
    onPromptChange: (String) -> Unit,
    response: String,
    isGenerating: Boolean,
    onSendPrompt: (String) -> Unit,
    onApplyCode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Quick Action Chips
        val chips = listOf(
            "Explain Code",
            "Find Bugs",
            "Optimize",
            "Generate Tests",
            "Add Comments"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips.forEach { chipText ->
                SuggestionChip(
                    onClick = { onSendPrompt(chipText) },
                    label = { Text(chipText, fontSize = 11.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // AI Prompt Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                placeholder = { Text("Ask Copilot...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ide_ai_prompt_input")
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = { onSendPrompt(prompt) },
                enabled = !isGenerating && prompt.isNotBlank(),
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // AI Response Area
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            if (isGenerating) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Thinking...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (response.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "AI Copilot is ready.\nAsk a question or select a quick action above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "AI SUGGESTION",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (response.contains("```")) {
                            TextButton(
                                onClick = {
                                    val extractedCode = response.substringAfter("```")
                                        .substringAfter("\n")
                                        .substringBefore("```")
                                    onApplyCode(extractedCode)
                                }
                            ) {
                                Text("Apply Code", fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        item {
                            Text(
                                text = response,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
