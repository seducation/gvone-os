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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GVONEFileBrowserSheet(
    fileSystem: GVONEFileSystem,
    onOpenFileInTab: (GVONEFileItem, inNewTab: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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

    // Import file launcher
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val imported = fileSystem.importFromUri(uri, currentFolder)
                if (imported != null) {
                    Toast.makeText(context, "Imported: ${imported.name}", Toast.LENGTH_SHORT).show()
                    loadFiles(fileSystem, activeLocation, currentFolder, searchQuery) { fileItems = it }
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
            isLoading = false
        }
    }

    LaunchedEffect(activeLocation, currentFolder, searchQuery) {
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
            // 1. TOP HEADER & SEARCH
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
                        Text(
                            text = "Files",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "GVONE Universal File Manager",
                            color = Color(0xFF64748B),
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

            // Breadcrumb navigation if navigated inside a subfolder
            if (currentFolder.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val parent = currentFolder.substringBeforeLast('/', "")
                            currentFolder = parent
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "My Files / $currentFolder",
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

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
                    items(fileItems, key = { it.path }) { item ->
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
                                } else {
                                    onOpenFileInTab(item, false)
                                    onDismiss()
                                }
                            },
                            onLongClick = {
                                contextMenuItem = item
                                showContextMenu = true
                            }
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // MODALS & ACTIONS (Context Menu, Create, Rename, Move, Info, Preview)
    // =========================================================================

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

    // 2. Create File / Folder Dialog
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
    onLongClick: () -> Unit
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
 * Dialog for "Create File / Folder / Import".
 */
@Composable
private fun CreateFileDialog(
    currentFolder: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, ext: String, type: FileType, content: String) -> Unit,
    onImport: () -> Unit
) {
    var selectedType by remember { mutableStateOf(FileType.TEXT) }
    var fileName by remember { mutableStateOf("") }
    var customExtension by remember { mutableStateOf("txt") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select Type", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)

                // Type selector chips: Text, Markdown, JSON, Code, Folder
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        TypeSelectionChip(label = "📄 Text (.txt)", isSelected = selectedType == FileType.TEXT) {
                            selectedType = FileType.TEXT
                            customExtension = "txt"
                        }
                    }
                    item {
                        TypeSelectionChip(label = "📝 Markdown (.md)", isSelected = selectedType == FileType.MARKDOWN) {
                            selectedType = FileType.MARKDOWN
                            customExtension = "md"
                        }
                    }
                    item {
                        TypeSelectionChip(label = "📋 JSON (.json)", isSelected = selectedType == FileType.JSON) {
                            selectedType = FileType.JSON
                            customExtension = "json"
                        }
                    }
                    item {
                        TypeSelectionChip(label = "💻 Code (.kt)", isSelected = selectedType == FileType.CODE) {
                            selectedType = FileType.CODE
                            customExtension = "kt"
                        }
                    }
                    item {
                        TypeSelectionChip(label = "📁 Folder", isSelected = selectedType == FileType.FOLDER) {
                            selectedType = FileType.FOLDER
                            customExtension = ""
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Name input
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text(if (selectedType == FileType.FOLDER) "Folder Name" else "File Name") },
                    placeholder = { Text(if (selectedType == FileType.FOLDER) "MyProject" else "notes") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Custom extension input if file
                if (selectedType != FileType.FOLDER) {
                    OutlinedTextField(
                        value = customExtension,
                        onValueChange = { customExtension = it },
                        label = { Text("File Extension") },
                        placeholder = { Text("md, txt, json, kt, py, html, etc.") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        val initialContent = when (selectedType) {
                            FileType.MARKDOWN -> "# ${fileName}\n\nStart writing markdown here."
                            FileType.JSON -> "{\n  \"title\": \"${fileName}\"\n}"
                            FileType.CODE -> "// ${fileName}.${customExtension}\n\nfun main() {\n    println(\"Hello World\")\n}"
                            else -> ""
                        }
                        onCreate(fileName.trim(), customExtension.trim(), selectedType, initialContent)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                enabled = fileName.isNotBlank()
            ) {
                Text("Create")
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
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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
