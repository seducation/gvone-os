package com.example.ui.screens.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.files.*
import kotlinx.coroutines.launch

/**
 * Dedicated Universal File Browser Interface for GVONE.
 */
@Composable
fun ReadmePreview(fileSystem: GVONEFileSystem, readmeItem: GVONEFileItem) {
    var content by remember { mutableStateOf("") }
    LaunchedEffect(readmeItem) {
        content = fileSystem.readFileContent(readmeItem.path)
    }
    Surface(
        color = Color(0xFF141C2B),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF222F43)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("README.md", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (content.length > 200) content.take(200) + "..." else content,
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GVONEFileBrowserSheet(
    fileSystem: GVONEFileSystem,
    onOpenFileInTab: (GVONEFileItem, inNewTab: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectFile: ((GVONEFileItem) -> Unit)? = null,
    selectionModeLabel: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var activeLocation by remember { mutableStateOf(StorageLocation.MY_FILES) }
    var currentFolder by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var fileItems by remember { mutableStateOf<List<GVONEFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Multi-select state
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<String>() }

    // Context item for actions
    var contextMenuItem by remember { mutableStateOf<GVONEFileItem?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Project & Git Resolution
    val projectName = remember(currentFolder, activeLocation) {
        if (currentFolder.isNotEmpty()) {
            val segments = currentFolder.trim('/').split('/')
            if (segments.firstOrNull() == "Projects" && segments.size > 1) {
                segments[1]
            } else {
                segments.last()
            }
        } else if (activeLocation == StorageLocation.PROJECTS) {
            "Projects"
        } else if (activeLocation == StorageLocation.MY_FILES) {
            "gvone-workspace"
        } else {
            activeLocation.displayName.lowercase().replace(" ", "-")
        }
    }

    val projectPath = remember(currentFolder, activeLocation, projectName) {
        val root = fileSystem.gitManager.resolveProjectRoot(currentFolder)
        if (root != null) {
            root
        } else if (activeLocation == StorageLocation.PROJECTS && projectName != "Projects") {
            "Projects/$projectName"
        } else if (currentFolder.isNotEmpty()) {
            currentFolder
        } else {
            "Projects/DefaultProject"
        }
    }

    // Git Branch & Repository state
    var currentBranch by remember { mutableStateOf("main") }
    var showBranchDialog by remember { mutableStateOf(false) }
    var showRepoMenu by remember { mutableStateOf(false) }
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showProjectSwitcherDialog by remember { mutableStateOf(false) }
    var allProjects by remember { mutableStateOf<List<ProjectRepoInfo>>(emptyList()) }
    var showCommitHistoryDialog by remember { mutableStateOf(false) }
    var showCommitChangesDialog by remember { mutableStateOf(false) }
    var showGitHubActionModal by remember { mutableStateOf<String?>(null) }
    var projectCommits by remember { mutableStateOf<List<ProjectCommit>>(emptyList()) }
    var projectBranches by remember { mutableStateOf<List<String>>(listOf("main")) }
    var uncommittedFiles by remember { mutableStateOf<List<java.io.File>>(emptyList()) }

    val latestCommit = projectCommits.firstOrNull()
    val latestCommitHash = latestCommit?.shortHash ?: "init"
    val totalCommitCount = projectCommits.size
    val latestCommitMessage = latestCommit?.message ?: "Initial project workspace created"
    val latestCommitTime = latestCommit?.formattedDate ?: "Just now"

    // Import file launcher
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val imported = fileSystem.importFromUri(uri, currentFolder)
                if (imported != null) {
                    Toast.makeText(context, "Imported: ${imported.name}", Toast.LENGTH_SHORT).show()
                    fileItems = fileSystem.listFiles(activeLocation, currentFolder, searchQuery)
                    uncommittedFiles = fileSystem.gitManager.calculateUncommittedChanges(projectPath)
                } else {
                    Toast.makeText(context, "Failed to import file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun refreshList() {
        coroutineScope.launch {
            isLoading = true
            fileItems = fileSystem.listFiles(activeLocation, currentFolder, searchQuery)
            allProjects = fileSystem.gitManager.listProjects()
            val repoInfo = fileSystem.gitManager.getProjectRepoInfo(projectPath)
            projectCommits = repoInfo.commits
            projectBranches = repoInfo.branches
            currentBranch = repoInfo.currentBranch
            uncommittedFiles = fileSystem.gitManager.calculateUncommittedChanges(projectPath)
            isLoading = false
        }
    }

    LaunchedEffect(activeLocation, currentFolder, searchQuery, projectName, projectPath) {
        refreshList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0D121D),
        scrimColor = Color(0x88000000),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF334155))
            )
        },
        modifier = modifier.testTag("gvone_files_browser_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // =========================================================================
            // 0. GITHUB-LIKE PROJECT HEADER & GIT TELEMETRY
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderSpecial,
                            contentDescription = "Repository",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showProjectSwitcherDialog = true }
                            .padding(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = projectName,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.UnfoldMore,
                                contentDescription = "Switch project",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp).padding(start = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0x2238BDF8),
                                border = BorderStroke(1.dp, Color(0x4438BDF8))
                            ) {
                                Text(
                                    text = "Public",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { showBranchDialog = true }
                                .padding(vertical = 1.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountTree,
                                contentDescription = "Branch",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentBranch,
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                Box {
                    IconButton(
                        onClick = { showRepoMenu = true },
                        modifier = Modifier.testTag("btn_project_repo_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Project options",
                            tint = Color(0xFF94A3B8)
                        )
                    }

                    DropdownMenu(
                        expanded = showRepoMenu,
                        onDismissRequest = { showRepoMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF162032))
                            .border(BorderStroke(1.dp, Color(0xFF283955)), RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Switch Project / Workspace", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showRepoMenu = false
                                showProjectSwitcherDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("New Project / Repository", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showRepoMenu = false
                                showNewProjectDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Commit History", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.History, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showRepoMenu = false
                                showCommitHistoryDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Switch Branch ($currentBranch)", color = Color.White, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.AccountTree, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showRepoMenu = false
                                showBranchDialog = true
                            }
                        )
                        HorizontalDivider(color = Color(0xFF283955))
                        DropdownMenuItem(
                            text = { Text("Copy Repository URL", color = Color(0xFFCBD5E1), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showRepoMenu = false
                                clipboardManager.setText(AnnotatedString("https://github.com/gvone/$projectName.git"))
                                Toast.makeText(context, "Copied repository clone URL", Toast.LENGTH_SHORT).show()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh Repository", color = Color(0xFFCBD5E1), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showRepoMenu = false
                                refreshList()
                                Toast.makeText(context, "Repository refreshed", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // =========================================================================
            // HORIZONTAL CHIP GROUP: LATEST COMMIT HASH & TOTAL COMMIT COUNT
            // =========================================================================
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("project_git_chips_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chip 1: Latest Commit Hash (Clickable to copy)
                item {
                    Surface(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(latestCommitHash))
                            Toast.makeText(
                                context,
                                "Commit hash $latestCommitHash copied to clipboard",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF162032),
                        border = BorderStroke(1.dp, Color(0xFF283955)),
                        modifier = Modifier.testTag("chip_latest_commit_hash")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Commit,
                                contentDescription = "Commit Hash",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = latestCommitHash,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy commit hash",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                // Chip 2: Total Commit Count
                item {
                    Surface(
                        onClick = {
                            showCommitHistoryDialog = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF162032),
                        border = BorderStroke(1.dp, Color(0xFF283955)),
                        modifier = Modifier.testTag("chip_total_commit_count")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = "Total Commits",
                                tint = Color(0xFFA78BFA),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$totalCommitCount commits",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }

                // Chip 3: Branch selector chip
                item {
                    Surface(
                        onClick = { showBranchDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF162032),
                        border = BorderStroke(1.dp, Color(0xFF283955)),
                        modifier = Modifier.testTag("chip_branch_selector")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccountTree,
                                contentDescription = "Branches",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentBranch,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFE2E8F0)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Rounded.ArrowDropDown,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Chip 4: Activity Time
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF162032),
                        border = BorderStroke(1.dp, Color(0xFF283955)),
                        modifier = Modifier.testTag("chip_activity_time")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = "Recent Activity",
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Updated $latestCommitTime",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Chip 5: Uncommitted Changes (Clickable to open Commit Dialog)
                if (uncommittedFiles.isNotEmpty()) {
                    item {
                        Surface(
                            onClick = { showCommitChangesDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x2210B981),
                            border = BorderStroke(1.dp, Color(0xFF10B981)),
                            modifier = Modifier.testTag("chip_uncommitted_changes")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Commit,
                                    contentDescription = "Commit changes",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Commit (${uncommittedFiles.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF34D399)
                                )
                            }
                        }
                    }
                }
            }

            // Latest commit message banner (GitHub mobile style)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp)
                    .clickable {
                        showCommitHistoryDialog = true
                    },
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF131B29),
                border = BorderStroke(1.dp, Color(0xFF222E42))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "G",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = latestCommitMessage,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF1F5F9),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "workspace-agent committed $latestCommitTime",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Verified",
                                fontSize = 10.sp,
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF1E293B),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = latestCommitHash,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF38BDF8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =========================================================================
            // 1. TOP HEADER & SEARCH (Updated)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.FolderCopy,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (onSelectFile != null) "Select File" else "Files",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (onSelectFile != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x3338BDF8),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8))
                                ) {
                                    Text(
                                        text = selectionModeLabel ?: "ATTACH",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (onSelectFile != null) "Tap any file to select as ${selectionModeLabel?.lowercase() ?: "attachment"}" else "GVONE Universal File Manager",
                            color = if (onSelectFile != null) Color(0xFF38BDF8) else Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Multi-select toggle
                    IconButton(
                        onClick = {
                            isMultiSelectMode = !isMultiSelectMode
                            if (!isMultiSelectMode) selectedItemIds.clear()
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isMultiSelectMode) Icons.Rounded.CheckCircle else Icons.Rounded.Checklist,
                            contentDescription = "Multi Select",
                            tint = if (isMultiSelectMode) Color(0xFF38BDF8) else Color(0xFF94A3B8)
                        )
                    }

                    // Add / Create File + button
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("files_create_add_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar: ⌕ Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("files_search_input"),
                shape = RoundedCornerShape(12.dp),
                placeholder = { Text("Search files, folders, code...", color = Color(0xFF64748B), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF141C2B),
                    unfocusedContainerColor = Color(0xFF141C2B),
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF222F43),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 2. LOCATIONS PILLS (My Files, Cloud, Downloads, Favorites, Recent)
            // =========================================================================
            Text(
                text = "LOCATIONS",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    LocationChip(
                        icon = Icons.Rounded.PhoneAndroid,
                        label = "My Files",
                        isSelected = activeLocation == StorageLocation.MY_FILES,
                        onClick = {
                            activeLocation = StorageLocation.MY_FILES
                            currentFolder = ""
                        }
                    )
                }
                item {
                    LocationChip(
                        icon = Icons.Rounded.FolderSpecial,
                        label = "Projects",
                        isSelected = activeLocation == StorageLocation.PROJECTS,
                        onClick = {
                            activeLocation = StorageLocation.PROJECTS
                            currentFolder = ""
                        }
                    )
                }
                item {
                    LocationChip(
                        icon = Icons.Rounded.CloudQueue,
                        label = "Cloud",
                        isSelected = activeLocation == StorageLocation.CLOUD,
                        onClick = {
                            activeLocation = StorageLocation.CLOUD
                            currentFolder = ""
                        }
                    )
                }
                item {
                    LocationChip(
                        icon = Icons.Rounded.Download,
                        label = "Downloads",
                        isSelected = activeLocation == StorageLocation.DOWNLOADS,
                        onClick = {
                            activeLocation = StorageLocation.DOWNLOADS
                            currentFolder = ""
                        }
                    )
                }
                item {
                    LocationChip(
                        icon = Icons.Rounded.Star,
                        label = "Favorites",
                        isSelected = activeLocation == StorageLocation.FAVORITES,
                        onClick = {
                            activeLocation = StorageLocation.FAVORITES
                            currentFolder = ""
                        }
                    )
                }
                item {
                    LocationChip(
                        icon = Icons.Rounded.Schedule,
                        label = "Recent",
                        isSelected = activeLocation == StorageLocation.RECENT,
                        onClick = {
                            activeLocation = StorageLocation.RECENT
                            currentFolder = ""
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // =========================================================================
            // 3. FOLDERS SHORTCUTS (Documents, Projects, Images, GVONE)
            // =========================================================================
            if (activeLocation == StorageLocation.MY_FILES && currentFolder.isEmpty()) {
                Text(
                    text = "FOLDERS",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FolderShortcutCard(name = "Documents", icon = Icons.Rounded.Folder, modifier = Modifier.weight(1f)) {
                        currentFolder = "Documents"
                    }
                    FolderShortcutCard(name = "Projects", icon = Icons.Rounded.FolderSpecial, modifier = Modifier.weight(1f)) {
                        currentFolder = "Projects"
                    }
                    FolderShortcutCard(name = "Images", icon = Icons.Rounded.FolderOpen, modifier = Modifier.weight(1f)) {
                        currentFolder = "Images"
                    }
                    FolderShortcutCard(name = "GVONE", icon = Icons.Rounded.FolderShared, modifier = Modifier.weight(1f)) {
                        currentFolder = "GVONE"
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Interactive Breadcrumb navigation bar
            val folderSegments = remember(currentFolder) {
                if (currentFolder.isEmpty()) emptyList() else currentFolder.split('/').filter { it.isNotEmpty() }
            }

            Surface(
                color = Color(0xFF131B2A),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF1E2B3E)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentFolder.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                val parent = currentFolder.substringBeforeLast('/', "")
                                currentFolder = parent
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back one level",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    LazyRow(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Surface(
                                onClick = { currentFolder = "" },
                                shape = RoundedCornerShape(6.dp),
                                color = if (currentFolder.isEmpty()) Color(0xFF1E3A5F) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (currentFolder.isEmpty()) Color(0xFF38BDF8) else Color(0xFF222F43))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (activeLocation == StorageLocation.PROJECTS) Icons.Rounded.FolderSpecial else Icons.Rounded.Home,
                                        contentDescription = null,
                                        tint = if (currentFolder.isEmpty()) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (activeLocation == StorageLocation.PROJECTS) "Projects" else "Root",
                                        color = if (currentFolder.isEmpty()) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        items(folderSegments.indices.toList()) { index ->
                            val segmentName = folderSegments[index]
                            val segmentPath = folderSegments.take(index + 1).joinToString("/")
                            val isLast = index == folderSegments.size - 1
                            val isProjectRoot = (index == 0 && segmentName == "Projects") || (index == 1 && folderSegments.firstOrNull() == "Projects")

                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(12.dp)
                            )

                            Surface(
                                onClick = { currentFolder = segmentPath },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isLast) Color(0x3338BDF8) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isLast) Color(0xFF38BDF8) else Color(0xFF222F43))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isProjectRoot && index == 1) Icons.Rounded.RocketLaunch else Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = if (isLast) Color(0xFF38BDF8) else if (isProjectRoot) Color(0xFFA78BFA) else Color(0xFFF59E0B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = segmentName,
                                        color = if (isLast) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // =========================================================================
            // 4. MULTI-SELECT ACTION BAR
            // =========================================================================
            if (isMultiSelectMode && selectedItemIds.isNotEmpty()) {
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedItemIds.size} selected",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Attach Selected (if selection callback provided)
                            if (onSelectFile != null) {
                                Button(
                                    onClick = {
                                        val selectedFiles = fileItems.filter { selectedItemIds.contains(it.path) }
                                        val target = selectedFiles.firstOrNull { !it.isDirectory }
                                        if (target != null) {
                                            onSelectFile(target)
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, "Select at least one file to ${selectionModeLabel?.lowercase() ?: "attach"}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp).testTag("files_attach_selected_btn")
                                ) {
                                    Icon(
                                        imageVector = if (selectionModeLabel != null) Icons.Rounded.CheckCircle else Icons.Rounded.AttachFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (selectionModeLabel == "RESEARCH SOURCE") "Import" else "Attach",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Batch Delete
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        for (id in selectedItemIds) {
                                            fileSystem.deleteItem(id)
                                        }
                                        selectedItemIds.clear()
                                        refreshList()
                                        Toast.makeText(context, "Deleted selected files", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Delete", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 5. FILES & FOLDERS LIST
            // =========================================================================
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8))
                }
            } else if (fileItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.FolderOff, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No files or folders found", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showCreateDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create File or Folder", fontSize = 12.sp)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // README.md section
                    val readmeItem = fileItems.find { it.name.equals("README.md", ignoreCase = true) }
                    if (readmeItem != null) {
                        item {
                            ReadmePreview(fileSystem = fileSystem, readmeItem = readmeItem)
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    items(fileItems.filter { !it.name.equals("README.md", ignoreCase = true) }, key = { it.path }) { item ->
                        val isSelected = selectedItemIds.contains(item.path)
                        FileItemRow(
                            item = item,
                            isMultiSelect = isMultiSelectMode,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) selectedItemIds.remove(item.path) else selectedItemIds.add(item.path)
                            },
                            onClick = {
                                if (isMultiSelectMode) {
                                    if (isSelected) selectedItemIds.remove(item.path) else selectedItemIds.add(item.path)
                                } else if (item.isDirectory) {
                                    currentFolder = item.path
                                } else if (onSelectFile != null) {
                                    onSelectFile(item)
                                    onDismiss()
                                } else {
                                    onOpenFileInTab(item, false)
                                    onDismiss()
                                }
                            },
                            onLongClick = {
                                contextMenuItem = item
                                showContextMenu = true
                            },
                            onSelectFile = onSelectFile?.let { selectFn ->
                                {
                                    selectFn(item)
                                    onDismiss()
                                }
                            },
                            selectionModeLabel = selectionModeLabel
                        )
                    }
                }
            }
        }
    }


    // =========================================================================
    // MODALS & ACTIONS (Context Menu, Create, Rename, Move, Info, Preview)
    // =========================================================================

    // 0. Branch Selector Dialog
    if (showBranchDialog) {
        GitHubBranchDialog(
            projectName = projectName,
            currentBranch = currentBranch,
            branches = projectBranches,
            onDismiss = { showBranchDialog = false },
            onSelectBranch = { branch ->
                coroutineScope.launch {
                    fileSystem.gitManager.switchBranch(projectPath, branch)
                    currentBranch = branch
                    refreshList()
                    showBranchDialog = false
                    Toast.makeText(context, "Switched to branch $branch", Toast.LENGTH_SHORT).show()
                }
            },
            onCreateBranch = { newBranch ->
                coroutineScope.launch {
                    fileSystem.gitManager.createBranch(projectPath, newBranch, switchTo = true)
                    currentBranch = newBranch
                    refreshList()
                    showBranchDialog = false
                    Toast.makeText(context, "Created & switched to $newBranch", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 1. Long Press Context Menu
    if (showContextMenu && contextMenuItem != null) {
        val item = contextMenuItem!!
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (item.isDirectory) Icons.Rounded.Folder else Icons.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = item.fileType.color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Open
                    ActionSheetButton(icon = Icons.Rounded.Visibility, label = "Open") {
                        showContextMenu = false
                        if (item.isDirectory) {
                            currentFolder = item.path
                        } else {
                            onOpenFileInTab(item, false)
                            onDismiss()
                        }
                    }

                    // Open in new tab
                    if (!item.isDirectory) {
                        ActionSheetButton(icon = Icons.Rounded.OpenInBrowser, label = "Open in new tab") {
                            showContextMenu = false
                            onOpenFileInTab(item, true)
                            onDismiss()
                        }

                        // Preview
                        ActionSheetButton(icon = Icons.Rounded.Preview, label = "Quick Preview") {
                            showContextMenu = false
                            showPreviewDialog = true
                        }
                    }

                    // Rename
                    ActionSheetButton(icon = Icons.Rounded.DriveFileRenameOutline, label = "Rename") {
                        showContextMenu = false
                        showRenameDialog = true
                    }

                    // Move
                    ActionSheetButton(icon = Icons.Rounded.DriveFileMove, label = "Move") {
                        showContextMenu = false
                        showMoveDialog = true
                    }

                    // Copy
                    ActionSheetButton(icon = Icons.Rounded.ContentCopy, label = "Copy") {
                        coroutineScope.launch {
                            fileSystem.copyItem(item.path, currentFolder)
                            refreshList()
                            Toast.makeText(context, "Copied ${item.name}", Toast.LENGTH_SHORT).show()
                        }
                        showContextMenu = false
                    }

                    // Share
                    if (!item.isDirectory) {
                        ActionSheetButton(icon = Icons.Rounded.Share, label = "Share") {
                            showContextMenu = false
                            try {
                                val f = fileSystem.getFile(item.path)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = item.mimeType
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
                            } catch (_: Exception) {
                                Toast.makeText(context, "Sharing unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                    // Attach as File to Terminal / Prompts
                    if (!item.isDirectory) {
                        ActionSheetButton(
                            icon = if (selectionModeLabel != null) Icons.Rounded.CheckCircle else Icons.Rounded.AttachFile,
                            label = if (selectionModeLabel == "RESEARCH SOURCE") "Import as Research Source" else "Attach to Terminal & Suggestions"
                        ) {
                            showContextMenu = false
                            if (onSelectFile != null) {
                                onSelectFile(item)
                            } else {
                                onOpenFileInTab(item, false)
                            }
                            onDismiss()
                        }
                    }

                    // Add to Favorites / Remove
                    ActionSheetButton(
                        icon = if (item.isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                        label = if (item.isFavorite) "Remove from Favorites" else "Add to Favorites"
                    ) {
                        coroutineScope.launch {
                            fileSystem.toggleFavorite(item.path)
                            refreshList()
                        }
                        showContextMenu = false
                    }

                    // Delete
                    ActionSheetButton(icon = Icons.Rounded.DeleteOutline, label = "Delete", isDestructive = true) {
                        coroutineScope.launch {
                            fileSystem.deleteItem(item.path)
                            refreshList()
                            Toast.makeText(context, "Deleted ${item.name}", Toast.LENGTH_SHORT).show()
                        }
                        showContextMenu = false
                    }

                    // Get Info
                    ActionSheetButton(icon = Icons.Rounded.Info, label = "Get Info") {
                        showContextMenu = false
                        showInfoDialog = true
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showContextMenu = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF141C2B)
        )
    }

    // 2. Create File / Folder / Project Dialog
    if (showCreateDialog) {
        CreateFileDialog(
            currentFolder = currentFolder,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, ext, type, content ->
                coroutineScope.launch {
                    if (type == FileType.FOLDER) {
                        fileSystem.createFolder(currentFolder, name)
                    } else {
                        val file = fileSystem.createFile(currentFolder, name, ext, content)
                        onOpenFileInTab(file, false)
                        onDismiss()
                    }
                    refreshList()
                }
                showCreateDialog = false
            },
            onCreateProject = { repoName, template, description ->
                coroutineScope.launch {
                    val targetPath = if (currentFolder.isEmpty() || currentFolder == "Projects") {
                        "Projects/$repoName"
                    } else {
                        "$currentFolder/$repoName"
                    }
                    fileSystem.createProject(repoName, template, description)
                    activeLocation = StorageLocation.PROJECTS
                    currentFolder = "Projects/$repoName"
                    refreshList()
                    showCreateDialog = false
                    Toast.makeText(context, "Created project: $repoName (${template.displayName})", Toast.LENGTH_SHORT).show()
                }
            },
            onImport = {
                showCreateDialog = false
                fileImportLauncher.launch("*/*")
            }
        )
    }

    // 3. Rename Dialog
    if (showRenameDialog && contextMenuItem != null) {
        var newName by remember { mutableStateOf(contextMenuItem!!.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            coroutineScope.launch {
                                fileSystem.renameItem(contextMenuItem!!.path, newName.trim())
                                refreshList()
                            }
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF141C2B)
        )
    }

    // 4. Move Dialog
    if (showMoveDialog && contextMenuItem != null) {
        MoveFolderDialog(
            item = contextMenuItem!!,
            onDismiss = { showMoveDialog = false },
            onMove = { targetFolder ->
                coroutineScope.launch {
                    fileSystem.moveItem(contextMenuItem!!.path, targetFolder)
                    refreshList()
                    Toast.makeText(context, "Moved to $targetFolder", Toast.LENGTH_SHORT).show()
                }
                showMoveDialog = false
            }
        )
    }

    // 5. Quick Preview Dialog
    if (showPreviewDialog && contextMenuItem != null) {
        var previewContent by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(contextMenuItem) {
            previewContent = fileSystem.readFileContent(contextMenuItem!!.path)
        }

        AlertDialog(
            onDismissRequest = { showPreviewDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Preview, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(contextMenuItem!!.name, color = Color.White, fontSize = 16.sp)
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .background(Color(0xFF090D16), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = previewContent ?: "Loading preview...",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPreviewDialog = false
                        onOpenFileInTab(contextMenuItem!!, false)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Open Editor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreviewDialog = false }) {
                    Text("Close", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF141C2B)
        )
    }

    // 6. Detailed Get Info Dialog
    if (showInfoDialog && contextMenuItem != null) {
        FileInfoModalDialog(
            relativePath = contextMenuItem!!.path,
            fileSystem = fileSystem,
            onDismiss = { showInfoDialog = false }
        )
    }

    // 7. GitHub Style New Project / Repository Dialog
    if (showNewProjectDialog) {
        GitHubNewProjectDialog(
            onDismiss = { showNewProjectDialog = false },
            onCreateProject = { repoName, template, description, isPrivate, initReadme, initGitignore ->
                coroutineScope.launch {
                    fileSystem.createProject(repoName, template, description)
                    activeLocation = StorageLocation.PROJECTS
                    currentFolder = "Projects/$repoName"
                    refreshList()
                    showNewProjectDialog = false
                    Toast.makeText(context, "Created project: $repoName (${template.displayName})", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 7.5 Project Switcher Dialog
    if (showProjectSwitcherDialog) {
        ProjectSwitcherDialog(
            currentProjectPath = projectPath,
            projects = allProjects,
            onDismiss = { showProjectSwitcherDialog = false },
            onSelectProject = { selectedRepo ->
                activeLocation = StorageLocation.PROJECTS
                currentFolder = selectedRepo.projectPath
                showProjectSwitcherDialog = false
                refreshList()
                Toast.makeText(context, "Switched to ${selectedRepo.projectName}", Toast.LENGTH_SHORT).show()
            },
            onCreateNewProject = {
                showProjectSwitcherDialog = false
                showNewProjectDialog = true
            }
        )
    }

    // 8. GitHub Style Commit History Dialog
    if (showCommitHistoryDialog) {
        GitHubCommitHistoryDialog(
            projectName = projectName,
            branch = currentBranch,
            commits = projectCommits,
            onDismiss = { showCommitHistoryDialog = false }
        )
    }

    // 9. Project Commit Changes Dialog
    if (showCommitChangesDialog) {
        ProjectCommitChangesDialog(
            projectName = projectName,
            currentBranch = currentBranch,
            uncommittedFiles = uncommittedFiles,
            onDismiss = { showCommitChangesDialog = false },
            onCommit = { message, description, branch ->
                coroutineScope.launch {
                    val result = fileSystem.gitManager.commitChanges(projectPath, message, description, branch)
                    refreshList()
                    showCommitChangesDialog = false
                    Toast.makeText(context, "Committed ${result.shortHash}: $message", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

private suspend fun loadFiles(
    fs: GVONEFileSystem,
    loc: StorageLocation,
    folder: String,
    search: String,
    onLoaded: (List<GVONEFileItem>) -> Unit
) {
    val list = fs.listFiles(loc, folder, search)
    onLoaded(list)
}

@Composable
private fun LocationChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF0369A1) else Color(0xFF141C2B),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF222F43)),
        modifier = Modifier.height(36.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FolderShortcutCard(
    name: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF141C2B),
        border = BorderStroke(1.dp, Color(0xFF222F43)),
        modifier = modifier.height(64.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = name,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FileItemRow(
    item: GVONEFileItem,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSelectFile: (() -> Unit)? = null,
    selectionModeLabel: String? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF1E3A5F) else Color(0xFF121824),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isMultiSelect) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // File icon with background circle
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = item.fileType.color.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, item.fileType.color.copy(alpha = 0.35f)),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (item.fileType) {
                                FileType.FOLDER -> Icons.Rounded.Folder
                                FileType.MARKDOWN -> Icons.Rounded.Description
                                FileType.TEXT -> Icons.Rounded.Article
                                FileType.JSON -> Icons.Rounded.DataObject
                                FileType.CODE -> Icons.Rounded.Code
                                FileType.PDF -> Icons.Rounded.PictureAsPdf
                                FileType.IMAGE -> Icons.Rounded.Image
                                FileType.ARCHIVE -> Icons.Rounded.FolderZip
                                else -> Icons.Rounded.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = item.fileType.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.gitStatus != GitFileStatus.UNMODIFIED) {
                            Spacer(modifier = Modifier.width(6.dp))
                            val (badgeText, badgeColor) = when (item.gitStatus) {
                                GitFileStatus.MODIFIED -> "M" to Color(0xFFF59E0B)
                                GitFileStatus.UNTRACKED -> "U" to Color(0xFF10B981)
                                GitFileStatus.DELETED -> "D" to Color(0xFFEF4444)
                                else -> "" to Color.Transparent
                            }
                            if (badgeText.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = badgeColor.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, badgeColor)
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (item.isFavorite) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Rounded.Star, contentDescription = "Favorite", tint = Color(0xFFFBBF24), modifier = Modifier.size(13.dp))
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = item.formattedSize, color = Color(0xFF64748B), fontSize = 11.sp)
                        Text(text = "•", color = Color(0xFF475569), fontSize = 11.sp)
                        Text(text = item.formattedDate, color = Color(0xFF64748B), fontSize = 11.sp)
                    }
                }
            }

            // Actions: Attach chip + More options
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onSelectFile != null && !item.isDirectory) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0x2A38BDF8),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                        modifier = Modifier
                            .clickable { onSelectFile() }
                            .testTag("file_select_attach_${item.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (selectionModeLabel != null) Icons.Rounded.CheckCircle else Icons.Rounded.AttachFile,
                                contentDescription = if (selectionModeLabel != null) "Select" else "Attach",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (selectionModeLabel == "RESEARCH SOURCE") "Import" else "Select",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // More options button (for accessibility and touch convenience)
                IconButton(
                    onClick = onLongClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionSheetButton(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) Color(0xFFEF4444) else Color(0xFF38BDF8),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = if (isDestructive) Color(0xFFEF4444) else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Dialog for "Create File / Subfolder / Project / Import".
 */
@Composable
private fun CreateFileDialog(
    currentFolder: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, ext: String, type: FileType, content: String) -> Unit,
    onCreateProject: (name: String, template: ProjectTemplate, description: String) -> Unit,
    onImport: () -> Unit
) {
    var creationMode by remember { mutableStateOf(0) } // 0: File, 1: Subfolder, 2: Project
    var fileName by remember { mutableStateOf("") }
    var customExtension by remember { mutableStateOf("kt") }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplate.KOTLIN_APP) }
    var projectDescription by remember { mutableStateOf("") }

    val filePresets = listOf(
        "kt" to "Kotlin",
        "py" to "Python",
        "js" to "JavaScript",
        "ts" to "TypeScript",
        "html" to "HTML",
        "css" to "CSS",
        "json" to "JSON",
        "md" to "Markdown",
        "sql" to "SQL",
        "sh" to "Shell",
        "env" to "Env",
        "txt" to "Text"
    )

    val folderPresets = listOf("src", "components", "utils", "routes", "models", "assets", "services", "tests", "docs")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = when (creationMode) {
                        0 -> "Create New File"
                        1 -> "Create Subfolder"
                        else -> "Create Software Project"
                    },
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (currentFolder.isNotEmpty()) {
                    Text(
                        text = "in $currentFolder",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Triple(0, "📄 File", Color(0xFF38BDF8)),
                        Triple(1, "📁 Folder", Color(0xFFF59E0B)),
                        Triple(2, "🚀 Project", Color(0xFFA78BFA))
                    ).forEach { (mode, label, tint) ->
                        val isSelected = creationMode == mode
                        Surface(
                            onClick = { creationMode = mode },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, tint.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                when (creationMode) {
                    0 -> {
                        // FILE CREATION
                        Text("Quick Extension Presets", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(filePresets) { (ext, label) ->
                                val isSelected = customExtension == ext
                                Surface(
                                    onClick = { customExtension = ext },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155))
                                ) {
                                    Text(
                                        text = ".$ext ($label)",
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text("File Name (without extension)") },
                            placeholder = { Text("App, index, server, query, etc.") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = customExtension,
                            onValueChange = { customExtension = it },
                            label = { Text("Extension") },
                            placeholder = { Text("kt, py, js, html, etc.") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    1 -> {
                        // SUBFOLDER CREATION
                        Text("Suggested Folder Names", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(folderPresets) { folder ->
                                val isSelected = fileName == folder
                                Surface(
                                    onClick = { fileName = folder },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Color(0xFFD97706) else Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFF59E0B) else Color(0xFF334155))
                                ) {
                                    Text(
                                        text = "$folder/",
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text("Folder Name") },
                            placeholder = { Text("e.g. src, components, utils") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFF59E0B),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    2 -> {
                        // SOFTWARE PROJECT CREATION
                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it.replace(" ", "-") },
                            label = { Text("Project / Repository Name *") },
                            placeholder = { Text("e.g. KotlinApp, WebFrontend") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFA78BFA),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Select Architecture Template", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ProjectTemplate.values().forEach { template ->
                                val isSelected = selectedTemplate == template
                                Surface(
                                    onClick = { selectedTemplate = template },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) Color(0x33A78BFA) else Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFA78BFA) else Color(0xFF334155)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (template) {
                                                ProjectTemplate.KOTLIN_APP -> Icons.Rounded.Code
                                                ProjectTemplate.WEB_HTML -> Icons.Rounded.Language
                                                ProjectTemplate.PYTHON_SCRIPT -> Icons.Rounded.Terminal
                                                ProjectTemplate.NODE_JS -> Icons.Rounded.DataObject
                                                ProjectTemplate.BLANK -> Icons.Rounded.Folder
                                            },
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFA78BFA) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = template.displayName,
                                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = template.description,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFFA78BFA),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = projectDescription,
                            onValueChange = { projectDescription = it },
                            label = { Text("Project Description (optional)") },
                            placeholder = { Text("Description for README and git repo") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFA78BFA),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Import option
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Icon(Icons.Rounded.UploadFile, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Or Import Existing File from Device", color = Color(0xFFE2E8F0), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        when (creationMode) {
                            0 -> {
                                val cleanExt = customExtension.trim().removePrefix(".")
                                val initialContent = when (cleanExt.lowercase()) {
                                    "kt" -> "// $fileName.kt\n\nfun main() {\n    println(\"Hello GVONE!\")\n}\n"
                                    "py" -> "# $fileName.py\n\ndef main():\n    print(\"Hello GVONE!\")\n\nif __name__ == '__main__':\n    main()\n"
                                    "js" -> "// $fileName.js\n\nconsole.log('Hello GVONE!');\n"
                                    "ts" -> "// $fileName.ts\n\ninterface Config {\n    name: string;\n}\n\nconsole.log('Hello GVONE!');\n"
                                    "html" -> "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <title>$fileName</title>\n</head>\n<body>\n    <h1>Hello GVONE!</h1>\n</body>\n</html>\n"
                                    "css" -> "/* $fileName.css */\nbody {\n    margin: 0;\n    font-family: sans-serif;\n}\n"
                                    "json" -> "{\n  \"name\": \"$fileName\",\n  \"version\": \"1.0.0\"\n}\n"
                                    "md" -> "# $fileName\n\nDocumentation and notes.\n"
                                    "sql" -> "-- $fileName.sql\nCREATE TABLE IF NOT EXISTS items (\n    id INTEGER PRIMARY KEY,\n    name TEXT NOT NULL\n);\n"
                                    "sh" -> "#!/bin/bash\n# $fileName.sh\n\necho \"Running $fileName...\"\n"
                                    "env" -> "# $fileName.env\nAPI_KEY=\nENVIRONMENT=development\n"
                                    else -> ""
                                }
                                val fileType = when (cleanExt.lowercase()) {
                                    "kt", "java", "py", "js", "ts", "cpp", "c", "cs", "go", "rs", "php", "rb", "swift", "html", "css", "xml", "sql", "sh" -> FileType.CODE
                                    "json" -> FileType.JSON
                                    "md" -> FileType.MARKDOWN
                                    else -> FileType.TEXT
                                }
                                onCreate(fileName.trim(), cleanExt, fileType, initialContent)
                            }
                            1 -> {
                                onCreate(fileName.trim(), "", FileType.FOLDER, "")
                            }
                            2 -> {
                                onCreateProject(fileName.trim(), selectedTemplate, projectDescription.trim())
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (creationMode) {
                        0 -> Color(0xFF0284C7)
                        1 -> Color(0xFFD97706)
                        else -> Color(0xFF7C3AED)
                    }
                ),
                enabled = fileName.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = when (creationMode) {
                        0 -> Icons.Rounded.Add
                        1 -> Icons.Rounded.CreateNewFolder
                        else -> Icons.Rounded.RocketLaunch
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (creationMode) {
                        0 -> "Create File"
                        1 -> "Create Folder"
                        else -> "Initialize Project"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF141C2B)
    )
}

@Composable
private fun TypeSelectionChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)),
        modifier = Modifier.height(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(
                text = label,
                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun MoveFolderDialog(
    item: GVONEFileItem,
    onDismiss: () -> Unit,
    onMove: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Folder", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (folder in GVONEFileSystem.DEFAULT_FOLDERS) {
                    Surface(
                        onClick = { onMove(folder) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(folder, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF141C2B)
    )
}

/**
 * GitHub Mobile style "Create a new project / repository" dialog with template presets.
 */
@Composable
fun GitHubNewProjectDialog(
    onDismiss: () -> Unit,
    onCreateProject: (
        repoName: String,
        template: ProjectTemplate,
        description: String,
        isPrivate: Boolean,
        initReadme: Boolean,
        initGitignore: Boolean
    ) -> Unit
) {
    var repoName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedTemplate by remember { mutableStateOf(ProjectTemplate.KOTLIN_APP) }
    var isPrivate by remember { mutableStateOf(false) }
    var initReadme by remember { mutableStateOf(true) }
    var initGitignore by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF238636)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "New Project / Repository",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Initialize project folder, subfolders, files & git tracking.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Project Name *",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = repoName,
                    onValueChange = { repoName = it.replace(" ", "-") },
                    singleLine = true,
                    placeholder = { Text("e.g. MyMobileApp", color = Color(0xFF64748B), fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_repo_name_input")
                )

                Text(
                    text = "Project Template",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(ProjectTemplate.entries.toList()) { template ->
                        val isSelected = selectedTemplate == template
                        Surface(
                            onClick = { selectedTemplate = template },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0x3338BDF8) else Color(0xFF0F172A),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B))
                        ) {
                            Text(
                                text = template.displayName,
                                color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "Description (optional)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    maxLines = 2,
                    placeholder = { Text("Short description of this project", color = Color(0xFF64748B), fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Public vs Private
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPrivate = false }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = !isPrivate,
                                onClick = { isPrivate = false },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Public", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Anyone on the local workspace can inspect and branch.", color = Color(0xFF64748B), fontSize = 10.sp)
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isPrivate = true }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = isPrivate,
                                onClick = { isPrivate = true },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF38BDF8))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Private", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("Restricted to current session credentials.", color = Color(0xFF64748B), fontSize = 10.sp)
                            }
                        }
                    }
                }

                Text(
                    text = "Include defaults:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Add README.md checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { initReadme = !initReadme }
                ) {
                    Checkbox(
                        checked = initReadme,
                        onCheckedChange = { initReadme = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Add README.md", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Include template description and architecture overview.", color = Color(0xFF64748B), fontSize = 10.sp)
                    }
                }

                // Add .gitignore checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { initGitignore = !initGitignore }
                ) {
                    Checkbox(
                        checked = initGitignore,
                        onCheckedChange = { initGitignore = it },
                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("Add .gitignore", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Filter out compiled artifacts and build outputs.", color = Color(0xFF64748B), fontSize = 10.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (repoName.isNotBlank()) {
                        onCreateProject(repoName.trim(), selectedTemplate, description.trim(), isPrivate, initReadme, initGitignore)
                    }
                },
                enabled = repoName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_create_repo_btn")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create project", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF131926)
    )
}

/**
 * Real Git Commit History Dialog showing all commits for a project.
 */
@Composable
fun GitHubCommitHistoryDialog(
    projectName: String,
    branch: String,
    commits: List<ProjectCommit>,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Commit History", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("$projectName • $branch (${commits.size} commits)", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            }
        },
        text = {
            if (commits.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No commits found on $branch", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    commits.forEach { commit ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF1E293B)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(commit.hash))
                                    Toast.makeText(context, "Commit ${commit.shortHash} copied", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E293B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Commit, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = commit.message,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (commit.description.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = commit.description,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${commit.author} • ${commit.formattedDate}",
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                        if (commit.changedFiles.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• ${commit.changedFiles.size} files",
                                                color = Color(0xFF34D399),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E293B)
                                ) {
                                    Text(
                                        text = commit.shortHash,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF38BDF8))
            }
        },
        containerColor = Color(0xFF131926)
    )
}

/**
 * Branch Switching and Creation Dialog.
 */
@Composable
fun GitHubBranchDialog(
    projectName: String,
    currentBranch: String,
    branches: List<String>,
    onDismiss: () -> Unit,
    onSelectBranch: (String) -> Unit,
    onCreateBranch: (String) -> Unit
) {
    var newBranchName by remember { mutableStateOf("") }
    var isCreatingNew by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161F30),
        titleContentColor = Color.White,
        textContentColor = Color(0xFF94A3B8),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AccountTree,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Branches", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("$projectName", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isCreatingNew) {
                    Text(
                        text = "Select active branch:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    branches.forEach { branch ->
                        val isCurrent = branch == currentBranch
                        Surface(
                            onClick = { onSelectBranch(branch) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrent) Color(0x3338BDF8) else Color(0xFF0F172A),
                            border = BorderStroke(
                                1.dp,
                                if (isCurrent) Color(0xFF38BDF8) else Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.AccountTree,
                                        contentDescription = null,
                                        tint = if (isCurrent) Color(0xFF38BDF8) else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = branch,
                                        fontSize = 13.sp,
                                        color = if (isCurrent) Color.White else Color(0xFFCBD5E1),
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Current",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = { isCreatingNew = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create new branch", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "New branch name:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it.replace(" ", "-") },
                        singleLine = true,
                        placeholder = { Text("e.g. feature/new-module", color = Color(0xFF64748B), fontSize = 13.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (newBranchName.isNotBlank()) {
                                    onCreateBranch(newBranchName.trim())
                                }
                            },
                            enabled = newBranchName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Create & Switch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { isCreatingNew = false },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Back", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        }
    )
}

/**
 * Commit Changes Dialog for writing commits to the project repository.
 */
@Composable
fun ProjectCommitChangesDialog(
    projectName: String,
    currentBranch: String,
    uncommittedFiles: List<java.io.File>,
    onDismiss: () -> Unit,
    onCommit: (message: String, description: String, branch: String) -> Unit
) {
    var commitMessage by remember { mutableStateOf("") }
    var commitDescription by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16A34A)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Commit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Commit Changes",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Project: $projectName • Branch: $currentBranch",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Changed Files (${uncommittedFiles.size})",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        uncommittedFiles.forEach { file ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0x33F59E0B),
                                    border = BorderStroke(1.dp, Color(0xFFF59E0B))
                                ) {
                                    Text(
                                        text = if (file.exists()) "M" else "U",
                                        color = Color(0xFFF59E0B),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = file.name,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Commit message *",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = { commitMessage = it },
                    singleLine = true,
                    placeholder = { Text("e.g. Add calculation logic and test files", color = Color(0xFF64748B), fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Extended description (optional)",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = commitDescription,
                    onValueChange = { commitDescription = it },
                    maxLines = 2,
                    placeholder = { Text("Add details about this version update", color = Color(0xFF64748B), fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (commitMessage.isNotBlank()) {
                        onCommit(commitMessage.trim(), commitDescription.trim(), currentBranch)
                    }
                },
                enabled = commitMessage.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Commit to $currentBranch", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF131926)
    )
}

/**
 * Fast Project & Workspace Switcher Dialog.
 * Allows the user to switch between any software project, see active branches & commits, or create new repositories.
 */
@Composable
fun ProjectSwitcherDialog(
    currentProjectPath: String,
    projects: List<ProjectRepoInfo>,
    onDismiss: () -> Unit,
    onSelectProject: (ProjectRepoInfo) -> Unit,
    onCreateNewProject: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredProjects = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) projects
        else projects.filter {
            it.projectName.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0284C7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Projects & Repositories",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${projects.size} workspace repositories",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter projects...", color = Color(0xFF64748B), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick New Project Action Card
                Surface(
                    onClick = onCreateNewProject,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x2210B981),
                    border = BorderStroke(1.dp, Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AddCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create New Project", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Kotlin, Web, Python, Node.js with Git VCS", color = Color(0xFF6EE7B7), fontSize = 11.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                    }
                }

                // Project List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredProjects.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) "No matching projects found" else "No projects created yet",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(filteredProjects) { project ->
                            val isCurrent = project.projectPath == currentProjectPath || 
                                           currentProjectPath.startsWith(project.projectPath)

                            Surface(
                                onClick = { onSelectProject(project) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) Color(0xFF1E2F48) else Color(0xFF0F172A),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) Color(0xFF38BDF8) else Color(0xFF222F43)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.FolderSpecial,
                                                contentDescription = null,
                                                tint = if (isCurrent) Color(0xFF38BDF8) else Color(0xFFA78BFA),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = project.projectName,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (isCurrent) {
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = Color(0xFF0284C7)
                                            ) {
                                                Text(
                                                    text = "ACTIVE",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (project.description.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = project.description,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Branch tag
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF1E293B),
                                            border = BorderStroke(1.dp, Color(0xFF334155))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.AccountTree,
                                                    contentDescription = null,
                                                    tint = Color(0xFF38BDF8),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = project.currentBranch,
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        // Commits tag
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF1E293B),
                                            border = BorderStroke(1.dp, Color(0xFF334155))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Commit,
                                                    contentDescription = null,
                                                    tint = Color(0xFF34D399),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = "${project.commits.size} commits",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        // Path tag
                                        Text(
                                            text = project.projectPath,
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp,
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF131926)
    )
}
