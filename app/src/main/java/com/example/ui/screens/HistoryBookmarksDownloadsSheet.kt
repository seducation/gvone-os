package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookmarkEntry
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.model.HistoryEntry
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySheet(
    historyList: List<HistoryEntry>,
    onOpenUrl: (String) -> Unit,
    onDeleteEntry: (HistoryEntry) -> Unit,
    onClearAll: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = historyList.filter {
        it.title.contains(searchQuery, true) || it.url.contains(searchQuery, true)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0D121C),
        modifier = modifier.testTag("history_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Browsing History", color = GVONETextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("Clear All", color = GVONEAccentRed, fontSize = 13.sp)
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search history", color = GVONETextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = GVONETextSecondary) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF161D2B),
                    unfocusedContainerColor = Color(0xFF161D2B),
                    focusedTextColor = GVONETextPrimary,
                    unfocusedTextColor = GVONETextPrimary
                ),
                singleLine = true
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No browsing history found", color = GVONETextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.id }) { entry ->
                        HistoryItemRow(
                            entry = entry,
                            onClick = { onOpenUrl(entry.url) },
                            onDelete = { onDeleteEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    entry: HistoryEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateStr = remember(entry.timestamp) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick() },
        color = Color(0xFF141B26)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Language, contentDescription = null, tint = GVONEPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(entry.title.ifBlank { entry.url }, color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$dateStr • ${entry.url}", color = GVONETextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Delete", tint = GVONETextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksSheet(
    bookmarks: List<BookmarkEntry>,
    onOpenUrl: (String) -> Unit,
    onDeleteBookmark: (BookmarkEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0D121C),
        modifier = modifier.testTag("bookmarks_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Bookmarks", color = GVONETextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            if (bookmarks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No bookmarks saved yet", color = GVONETextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpenUrl(bookmark.url) },
                            color = Color(0xFF141B26)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Bookmark, contentDescription = null, tint = GVONESecondary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(bookmark.title, color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(bookmark.url, color = GVONETextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                IconButton(onClick = { onDeleteBookmark(bookmark) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = GVONETextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    downloads: List<DownloadItem>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0D121C),
        modifier = modifier.testTag("downloads_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text("Downloads", color = GVONETextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No downloads yet", color = GVONETextSecondary, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(downloads, key = { it.id }) { item ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                            color = Color(0xFF141B26)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (item.status == DownloadStatus.COMPLETED) Icons.Rounded.CheckCircle else Icons.Rounded.Download,
                                        contentDescription = null,
                                        tint = if (item.status == DownloadStatus.COMPLETED) GVONETertiary else GVONEPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(item.fileName, color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(item.status.name, color = GVONETextSecondary, fontSize = 11.sp)
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
