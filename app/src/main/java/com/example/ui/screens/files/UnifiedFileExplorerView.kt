package com.example.ui.screens.files

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.launch
import java.io.File

/**
 * Unified File & Directory Explorer View.
 * Provides identical, cohesive file & directory operations across:
 * 1. Bottom-right three-dots "Files & Storage" sheet
 * 2. Xcode Inspector Panel "Files" tab
 * 3. IDE primary sidebar "Explorer"
 * 4. Tab Overview / Navigator "Files & Workspace"
 *
 * Core capabilities:
 * - Clicking a folder opens that subfolder and updates path breadcrumbs.
 * - Clicking a back button ("← ..") navigates up to the parent directory.
 * - Clicking a file opens it in a live tab with path header; image files render with full ImageFileViewer.
 * - Quick in-place preview dialog for images and documents.
 */
@Composable
fun UnifiedFileExplorerView(
    fileSystem: GVONEFileSystem,
    onOpenFileInTab: (GVONEFileItem, inNewTab: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initialFolder: String = "",
    initialLocation: StorageLocation = StorageLocation.MY_FILES,
    onOpenFileInIde: ((GVONEFileItem) -> Unit)? = null,
    onClosePanel: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    var currentFolder by remember { mutableStateOf(initialFolder) }
    var activeLocation by remember { mutableStateOf(initialLocation) }
    var fileItems by remember { mutableStateOf<List<GVONEFileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var previewFile by remember { mutableStateOf<GVONEFileItem?>(null) }

    fun refresh() {
        coroutineScope.launch {
            isLoading = true
            fileItems = fileSystem.listFiles(activeLocation, currentFolder, searchQuery)
            isLoading = false
        }
    }

    LaunchedEffect(activeLocation, currentFolder, searchQuery) {
        refresh()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("unified_file_explorer_view")
    ) {
        // ================= HEADER & BREADCRUMBS =================
        Surface(
            color = Color(0xFF0F1522),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Storage Location Selector Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val locations = listOf(
                        Triple(StorageLocation.MY_FILES, "My Files", Icons.Rounded.FolderShared),
                        Triple(StorageLocation.PROJECTS, "Projects", Icons.Rounded.Source),
                        Triple(StorageLocation.DOWNLOADS, "Downloads", Icons.Rounded.Download),
                        Triple(StorageLocation.FAVORITES, "Favorites", Icons.Rounded.Star)
                    )

                    locations.forEach { (loc, label, icon) ->
                        val isSelected = activeLocation == loc
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF263348)
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    activeLocation = loc
                                    currentFolder = ""
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // Subfolder Navigation & Path Breadcrumbs Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Back to parent directory button
                        if (currentFolder.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    val parent = if (currentFolder.contains('/')) {
                                        currentFolder.substringBeforeLast('/')
                                    } else {
                                        ""
                                    }
                                    currentFolder = parent
                                },
                                modifier = Modifier
                                    .size(28.dp)
                                    .testTag("btn_explorer_parent_dir")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back to parent folder",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Root chip
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (currentFolder.isEmpty()) Color(0xFF1E293B) else Color(0xFF141A28),
                            border = BorderStroke(0.5.dp, Color(0xFF263348)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { currentFolder = "" }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "root",
                                color = if (currentFolder.isEmpty()) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Path segments
                        if (currentFolder.isNotEmpty()) {
                            val parts = currentFolder.split('/')
                            var cumulative = ""
                            parts.forEachIndexed { index, part ->
                                cumulative = if (cumulative.isEmpty()) part else "$cumulative/$part"
                                val target = cumulative
                                val isLast = index == parts.size - 1

                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isLast) Color(0xFF0284C7).copy(alpha = 0.2f) else Color(0xFF141A28),
                                    border = BorderStroke(0.5.dp, if (isLast) Color(0xFF0284C7) else Color(0xFF263348)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { currentFolder = target }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = part,
                                        color = if (isLast) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Refresh Button
                    IconButton(
                        onClick = { refresh() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Files",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

        // ================= FILE & FOLDER LIST =================
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = if (currentFolder.isEmpty()) "No files found in this location" else "Folder \"${currentFolder.substringAfterLast('/')}\" is empty",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (currentFolder.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    val parent = if (currentFolder.contains('/')) currentFolder.substringBeforeLast('/') else ""
                                    currentFolder = parent
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Go up to parent directory", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(fileItems, key = { it.path }) { item ->
                        UnifiedFileItemRow(
                            item = item,
                            onClick = {
                                if (item.isDirectory) {
                                    // Clicking a folder opens that subfolder!
                                    currentFolder = item.path
                                } else {
                                    // Clicking a file opens it in a live tab with full viewer
                                    onOpenFileInTab(item, false)
                                    onClosePanel?.invoke()
                                }
                            },
                            onPreview = { previewFile = item },
                            onOpenInNewTab = {
                                if (!item.isDirectory) {
                                    onOpenFileInTab(item, true)
                                    onClosePanel?.invoke()
                                }
                            },
                            onOpenInIde = if (onOpenFileInIde != null && !item.isDirectory) {
                                {
                                    onOpenFileInIde(item)
                                    onClosePanel?.invoke()
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }

    // Quick In-Place Preview Dialog
    previewFile?.let { item ->
        UnifiedFilePreviewDialog(
            item = item,
            fileSystem = fileSystem,
            onDismiss = { previewFile = null },
            onOpenInTab = {
                onOpenFileInTab(item, false)
                previewFile = null
                onClosePanel?.invoke()
            }
        )
    }
}

/**
 * Unified item row representing either a folder (opens subfolder) or a file (opens in tab/viewer).
 */
@Composable
fun UnifiedFileItemRow(
    item: GVONEFileItem,
    onClick: () -> Unit,
    onPreview: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onOpenInIde: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isImage = item.fileType == FileType.IMAGE ||
            item.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp")

    val icon: ImageVector = when {
        item.isDirectory -> Icons.Rounded.Folder
        isImage -> Icons.Rounded.Image
        item.fileType == FileType.CODE -> Icons.Rounded.Code
        item.fileType == FileType.JSON -> Icons.Rounded.DataArray
        item.fileType == FileType.MARKDOWN -> Icons.Rounded.Article
        item.fileType == FileType.PDF -> Icons.Rounded.PictureAsPdf
        item.extension == "html" || item.extension == "htm" -> Icons.Rounded.Language
        else -> Icons.Rounded.InsertDriveFile
    }

    val iconTint: Color = when {
        item.isDirectory -> Color(0xFFF59E0B) // Amber for folder
        isImage -> Color(0xFFEC4899)          // Pink for image
        item.extension == "html" -> Color(0xFF10B981) // Green for web
        item.fileType == FileType.CODE -> Color(0xFFA78BFA) // Violet for code
        item.fileType == FileType.MARKDOWN -> Color(0xFF38BDF8) // Sky blue
        item.fileType == FileType.PDF -> Color(0xFFEF4444) // Red for PDF
        else -> Color(0xFF94A3B8)
    }

    Surface(
        color = Color(0xFF121826),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, Color(0xFF1E283A)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag("unified_file_item_${item.name}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Leading Icon
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = iconTint.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = item.name,
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // File Name & Path
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = if (item.isDirectory) FontFamily.Default else FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isImage) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFEC4899).copy(alpha = 0.2f),
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = item.extension.uppercase(),
                                    color = Color(0xFFF472B6),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = item.path,
                        color = Color(0xFF64748B),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right side: Size or Subfolder chevron & Quick Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (item.isDirectory) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Open subfolder",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = item.formattedSize,
                        color = Color(0xFF64748B),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = onPreview,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = "Preview",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Unified Preview Dialog capable of displaying both image bitmaps and source text cleanly.
 */
@Composable
fun UnifiedFilePreviewDialog(
    item: GVONEFileItem,
    fileSystem: GVONEFileSystem,
    onDismiss: () -> Unit,
    onOpenInTab: () -> Unit
) {
    val file = remember(item.path) { fileSystem.getFile(item.path) }
    val isImage = item.fileType == FileType.IMAGE ||
            item.extension.lowercase() in listOf("png", "jpg", "jpeg", "webp", "gif", "svg", "bmp")

    var contentText by remember { mutableStateOf<String?>(null) }
    var imageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.path) {
        isLoading = true
        if (isImage) {
            try {
                imageBitmap = BitmapFactory.decodeFile(file.absolutePath)
            } catch (_: Exception) {}
        } else {
            try {
                contentText = fileSystem.readFileContent(item.path)
            } catch (_: Exception) {
                contentText = "Unable to read file content."
            }
        }
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1522),
        shape = RoundedCornerShape(16.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Text(
                            text = item.formattedSize,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = item.path,
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF090D16),
                border = BorderStroke(1.dp, Color(0xFF1E283A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp, max = 340.dp)
            ) {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                    }
                } else if (isImage) {
                    val bmp = imageBitmap
                    if (bmp != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = item.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${bmp.width} × ${bmp.height} px  •  ${file.length() / 1024} KB",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Unable to render image bitmap", color = Color(0xFF64748B), fontSize = 12.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = contentText ?: "Empty file",
                            color = Color(0xFFE2E8F0),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenInTab,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open in Tab", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8), fontSize = 12.sp)
            }
        }
    )
}
