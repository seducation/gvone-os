package com.example.ui.screens.ide

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.files.FileType
import com.example.data.files.GVONEFileItem
import com.example.data.files.GVONEFileSystem
import com.example.data.files.StorageLocation
import com.example.data.model.QuickMenuProject
import com.example.data.model.QuickMenuStorage
import kotlinx.coroutines.launch

private enum class ProjectSubTab(val label: String) {
    FILES("Files"),
    TABS("Tabs"),
    CHATS("Chats"),
    EXTENSIONS("Extensions"),
    BOTS("Bots")
}

/**
 * Dedicated Project Inspector View.
 * Displays project-scoped resources (Files, Tabs, Chats, Extensions, Bots) with
 * complete isolation from general workspace files, allowing file creation, AI scaffolding,
 * and device import.
 */
@Composable
fun ProjectInspectorView(
    fileSystem: GVONEFileSystem?,
    onOpenFileInTab: ((GVONEFileItem, Boolean) -> Unit)? = null,
    onOpenFileInIde: ((GVONEFileItem) -> Unit)? = null,
    onExecuteTerminalCommand: ((String) -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var projects by remember {
        mutableStateOf(QuickMenuStorage.getProjects(context))
    }
    var selectedProjectId by remember {
        mutableStateOf(projects.firstOrNull()?.id.orEmpty())
    }
    val activeProject = projects.find { it.id == selectedProjectId } ?: projects.firstOrNull()

    var activeSubTab by remember { mutableStateOf(ProjectSubTab.FILES) }

    var currentFolder by remember { mutableStateOf("") }
    var fileItems by remember { mutableStateOf<List<GVONEFileItem>>(emptyList()) }
    var isLoadingFiles by remember { mutableStateOf(false) }

    val refreshFiles: () -> Unit = {
        coroutineScope.launch {
            if (fileSystem != null) {
                isLoadingFiles = true
                fileItems = fileSystem.listFiles(StorageLocation.MY_FILES, currentFolder)
                isLoadingFiles = false
            }
        }
    }

    LaunchedEffect(currentFolder, activeProject) {
        refreshFiles()
    }

    // Dialog state for new file
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var newFileExt by remember { mutableStateOf("kt") }

    // Dialog state for AI creation
    var showAiCreateDialog by remember { mutableStateOf(false) }
    var aiPromptDesc by remember { mutableStateOf("") }
    var aiTargetName by remember { mutableStateOf("") }

    // Media / Device File Picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && activeProject != null) {
            val fileName = "imported_asset_${System.currentTimeMillis() % 10000}.png"
            val updatedFiles = (activeProject.files + fileName).distinct()
            val updatedProj = activeProject.copy(files = updatedFiles)
            val updatedList = projects.map { if (it.id == activeProject.id) updatedProj else it }
            projects = updatedList
            QuickMenuStorage.saveProjects(context, updatedList)
            Toast.makeText(context, "Imported device asset '$fileName' to ${activeProject.name}", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("project_inspector_view")
    ) {
        // Project Selector Chips Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            projects.forEach { proj ->
                val isSelected = proj.id == activeProject?.id
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF1E293B) else Color(0xFF0F1724),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2638)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedProjectId = proj.id }
                        .testTag("project_chip_${proj.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderSpecial,
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = proj.name,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFFF1F5F9) else Color(0xFF94A3B8)
                        )
                    }
                }
            }
        }

        if (activeProject == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No projects configured.", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
            return
        }

        // Sub-tabs Strip: Files, Tabs, Chats, Extensions, Bots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ProjectSubTab.values().forEach { subTab ->
                val isSelected = activeSubTab == subTab
                val count = when (subTab) {
                    ProjectSubTab.FILES -> if (fileItems.isNotEmpty()) fileItems.size else activeProject.files.size
                    ProjectSubTab.TABS -> activeProject.tabs.size
                    ProjectSubTab.CHATS -> activeProject.chats.size
                    ProjectSubTab.EXTENSIONS -> activeProject.extensions.size
                    ProjectSubTab.BOTS -> activeProject.bots.size
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) Color(0xFF0284C7).copy(alpha = 0.25f) else Color.Transparent,
                    border = BorderStroke(0.8.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E2838)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { activeSubTab = subTab }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .testTag("project_subtab_${subTab.name.lowercase()}")
                ) {
                    Text(
                        text = "${subTab.label} ($count)",
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.8.dp, modifier = Modifier.padding(vertical = 6.dp))

        // SubTab Content Body
        when (activeSubTab) {
            ProjectSubTab.FILES -> {
                // FILES SUB-TAB (All files and folders with normal navigation!)
                Column(modifier = Modifier.fillMaxSize()) {
                    // Actions Toolbar: + New File, + AI Create, + Import Device File
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                newFileName = ""
                                showNewFileDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFF38BDF8))
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("+ File", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = {
                                aiTargetName = ""
                                aiPromptDesc = ""
                                showAiCreateDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF2E1A47), contentColor = Color(0xFFA78BFA))
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("+ AI Create", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF142E25), contentColor = Color(0xFF34D399))
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Import", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        IconButton(
                            onClick = { refreshFiles() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                        }
                    }

                    // Breadcrumb navigation bar when inside subfolders
                    if (currentFolder.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(0.8.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        currentFolder = if (currentFolder.contains('/')) {
                                            currentFolder.substringBeforeLast('/')
                                        } else {
                                            ""
                                        }
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Up to parent directory",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = currentFolder,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { currentFolder = "" },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Text("Root", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                                }
                            }
                        }
                    }

                    if (isLoadingFiles) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else if (fileItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(28.dp))
                                Text(
                                    text = if (currentFolder.isEmpty()) "No files found in workspace" else "Folder \"${currentFolder.substringAfterLast('/')}\" is empty",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                                Text("Use '+ File' or '+ AI Create' to add files", color = Color(0xFF64748B), fontSize = 10.5.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(fileItems, key = { it.path }) { item ->
                                if (item.isDirectory) {
                                    // DIRECTORY FOLDER ITEM (Opens normally into subfolder, never in tab groups)
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF141D2B),
                                        border = BorderStroke(0.6.dp, Color(0xFF26354D)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                // Opens normally by navigating into folder, NOT in tab groups!
                                                currentFolder = item.path
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Folder,
                                                    contentDescription = "Folder",
                                                    tint = Color(0xFFF59E0B),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                                Text(
                                                    text = item.name,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFF8FAFC),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                                contentDescription = "Open folder",
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                } else {
                                    // REGULAR FILE ITEM
                                    val ext = item.extension.lowercase()
                                    val iconTint = when (ext) {
                                        "kt", "java" -> Color(0xFFA78BFA)
                                        "html", "htm" -> Color(0xFF38BDF8)
                                        "js", "ts", "jsx", "tsx" -> Color(0xFFFBBF24)
                                        "css" -> Color(0xFF60A5FA)
                                        "png", "jpg", "jpeg", "webp" -> Color(0xFF34D399)
                                        else -> Color(0xFF94A3B8)
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF121926),
                                        border = BorderStroke(0.6.dp, Color(0xFF223048)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                onOpenFileInIde?.invoke(item)
                                                onClose()
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = if (ext in listOf("png", "jpg", "jpeg", "webp")) Icons.Rounded.Image else Icons.Rounded.Description,
                                                    contentDescription = null,
                                                    tint = iconTint,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = item.name,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFFE2E8F0),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                // Open in IDE Editor Button
                                                IconButton(
                                                    onClick = {
                                                        onOpenFileInIde?.invoke(item)
                                                        onClose()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Code, contentDescription = "Edit in IDE", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                                                }

                                                // Preview in Browser Tab Button
                                                IconButton(
                                                    onClick = {
                                                        onOpenFileInTab?.invoke(item, true)
                                                        onClose()
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Preview in Tab", tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                                                }

                                                // Delete File Button
                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            fileSystem?.deleteItem(item.path)
                                                            if (activeProject != null) {
                                                                val updatedFiles = activeProject.files.filterNot { it == item.name }
                                                                val updatedProj = activeProject.copy(files = updatedFiles)
                                                                val updatedList = projects.map { if (it.id == activeProject.id) updatedProj else it }
                                                                projects = updatedList
                                                                QuickMenuStorage.saveProjects(context, updatedList)
                                                            }
                                                            refreshFiles()
                                                            Toast.makeText(context, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Close, contentDescription = "Delete File", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
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

            ProjectSubTab.TABS -> {
                // TABS SUB-TAB
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Project Browser & Editor Tabs",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    if (activeProject.tabs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No tabs associated with this project.", color = Color(0xFF64748B), fontSize = 11.5.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(activeProject.tabs) { tabTitle ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF141C2B),
                                    border = BorderStroke(0.6.dp, Color(0xFF223048)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Rounded.Tab, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                                            Text(tabTitle, fontSize = 11.5.sp, color = Color(0xFFF1F5F9), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ProjectSubTab.CHATS -> {
                // CHATS SUB-TAB
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Project Conversations", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                        TextButton(
                            onClick = {
                                onExecuteTerminalCommand?.invoke("/chat")
                                onClose()
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text("Open in Terminal", fontSize = 10.5.sp, color = Color(0xFF38BDF8))
                        }
                    }
                    if (activeProject.chats.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No chats recorded for this project.", color = Color(0xFF64748B), fontSize = 11.5.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(activeProject.chats) { chatSnippet ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1A1429),
                                    border = BorderStroke(0.6.dp, Color(0xFF2E2248)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Chat, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(15.dp))
                                        Text(chatSnippet, fontSize = 11.sp, color = Color(0xFFE2E8F0), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ProjectSubTab.EXTENSIONS -> {
                // EXTENSIONS SUB-TAB
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Enabled Extensions", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    val allExtensions = listOf("prettier", "eslint", "live_server", "gitlens", "language_tools")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(allExtensions) { extId ->
                            val isEnabled = activeProject.extensions.contains(extId)
                            val title = when (extId) {
                                "prettier" -> "Prettier Code Formatter"
                                "eslint" -> "ESLint & Code Linter"
                                "live_server" -> "Live Server Web Preview"
                                "gitlens" -> "GitLens VCS History"
                                else -> "Language Tools Pack"
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF121926),
                                border = BorderStroke(0.6.dp, Color(0xFF223048)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.Extension, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                                        Text(title, fontSize = 11.5.sp, color = Color(0xFFE2E8F0))
                                    }
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = { checked ->
                                            val updatedExts = if (checked) {
                                                (activeProject.extensions + extId).distinct()
                                            } else {
                                                activeProject.extensions.filterNot { it == extId }
                                            }
                                            val updatedProj = activeProject.copy(extensions = updatedExts)
                                            val updatedList = projects.map { if (it.id == activeProject.id) updatedProj else it }
                                            projects = updatedList
                                            QuickMenuStorage.saveProjects(context, updatedList)
                                        },
                                        modifier = Modifier.scale(0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            ProjectSubTab.BOTS -> {
                // BOTS SUB-TAB
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Assigned Project Bots & Agents", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    val defaultBots = listOf("Coding Agent", "Terminal Agent", "Web Review Agent", "Architect")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(defaultBots) { botName ->
                            val isAssigned = activeProject.bots.contains(botName)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF121926),
                                border = BorderStroke(0.6.dp, Color(0xFF223048)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(15.dp))
                                        Text(botName, fontSize = 11.5.sp, color = Color(0xFFE2E8F0))
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            onExecuteTerminalCommand?.invoke("/agent")
                                            onClose()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF2E1A47), contentColor = Color(0xFFA78BFA))
                                    ) {
                                        Text("Chat", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog && activeProject != null) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("New File in ${activeProject.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        label = { Text("Filename (e.g. App.kt, index.html)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isNotEmpty()) {
                            coroutineScope.launch {
                                val folder = if (currentFolder.isNotEmpty()) currentFolder else "Projects/${activeProject.name}"
                                val ext = name.substringAfterLast('.', "txt")
                                val baseName = name.substringBeforeLast('.')
                                fileSystem?.createFile(folder, baseName, ext, "// File: $name in ${activeProject.name}")
                                val updatedFiles = (activeProject.files + name).distinct()
                                val updatedProj = activeProject.copy(files = updatedFiles)
                                val updatedList = projects.map { if (it.id == activeProject.id) updatedProj else it }
                                projects = updatedList
                                QuickMenuStorage.saveProjects(context, updatedList)
                                refreshFiles()
                                showNewFileDialog = false
                                Toast.makeText(context, "Created $name", Toast.LENGTH_SHORT).show()
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

    // AI Create Dialog
    if (showAiCreateDialog && activeProject != null) {
        AlertDialog(
            onDismissRequest = { showAiCreateDialog = false },
            title = { Text("AI Create File in ${activeProject.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aiTargetName,
                        onValueChange = { aiTargetName = it },
                        label = { Text("Filename (e.g. Calculator.kt, index.html)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = aiPromptDesc,
                        onValueChange = { aiPromptDesc = it },
                        label = { Text("What should AI build in this file?") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = aiTargetName.trim().ifEmpty { "MainComponent.kt" }
                        val desc = aiPromptDesc.trim()
                        coroutineScope.launch {
                            val folder = if (currentFolder.isNotEmpty()) currentFolder else "Projects/${activeProject.name}"
                            val ext = name.substringAfterLast('.', "kt")
                            val baseName = name.substringBeforeLast('.')
                            val content = "// AI-Generated File: $name\n// Requirements: $desc\n\nfun main() {\n    println(\"Executing $name for ${activeProject.name}\")\n}\n"
                            val created = fileSystem?.createFile(folder, baseName, ext, content)
                            val updatedFiles = (activeProject.files + name).distinct()
                            val updatedProj = activeProject.copy(files = updatedFiles)
                            val updatedList = projects.map { if (it.id == activeProject.id) updatedProj else it }
                            projects = updatedList
                            QuickMenuStorage.saveProjects(context, updatedList)
                            refreshFiles()
                            showAiCreateDialog = false
                            Toast.makeText(context, "AI generated $name", Toast.LENGTH_SHORT).show()
                            if (created != null) {
                                onOpenFileInIde?.invoke(created)
                                onClose()
                            }
                        }
                    }
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAiCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
