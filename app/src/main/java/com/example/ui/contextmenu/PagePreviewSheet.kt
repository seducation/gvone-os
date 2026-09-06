package com.example.ui.contextmenu

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.BookmarkEntry
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagePreviewSheet(
    data: PagePreviewData,
    savedBookmarks: List<BookmarkEntry> = emptyList(),
    onClose: () -> Unit,
    onOpenInTab: (String) -> Unit,
    onShare: (url: String, title: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentUrl by remember(data.url) { mutableStateOf(data.url) }
    var currentTitle by remember(data.url) { mutableStateOf(data.title.ifBlank { data.url }) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var showBookmarksList by remember { mutableStateOf(false) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Check if currently loaded preview URL is in bookmarks
    val isCurrentUrlBookmarked = remember(currentUrl, savedBookmarks) {
        savedBookmarks.any { it.url.equals(currentUrl, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0B101B),
        scrimColor = Color.Black.copy(alpha = 0.70f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x35FFFFFF))
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .testTag("page_preview_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = GVONEPrimary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Page Title and URL
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTitle,
                        color = GVONETextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentUrl,
                        color = GVONETextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 1. Share icon
                IconButton(
                    onClick = {
                        onShare(currentUrl, currentTitle)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("preview_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = GVONETextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 2. Bookmarks icon next to share
                IconButton(
                    onClick = {
                        showBookmarksList = !showBookmarksList
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("preview_bookmark_toggle_button")
                ) {
                    val bookmarkIcon = if (isCurrentUrlBookmarked || showBookmarksList) {
                        Icons.Rounded.Bookmark
                    } else {
                        Icons.Rounded.BookmarkBorder
                    }
                    val bookmarkTint = if (showBookmarksList) {
                        GVONESecondary
                    } else if (isCurrentUrlBookmarked) {
                        GVONEPrimary
                    } else {
                        GVONETextPrimary
                    }

                    Icon(
                        imageVector = bookmarkIcon,
                        contentDescription = "Saved bookmarks",
                        tint = bookmarkTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3. Open in full tab button
                IconButton(
                    onClick = {
                        onClose()
                        onOpenInTab(currentUrl)
                    },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("preview_open_tab_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = "Open in tab",
                        tint = GVONEPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 4. Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("preview_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close preview",
                        tint = GVONETextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Progress bar
            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = GVONEPrimary,
                    trackColor = Color(0x20FFFFFF)
                )
            } else {
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Collapsible / Dropdown list of Saved Bookmarks
            AnimatedVisibility(
                visible = showBookmarksList,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = Color(0xFF131924),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .testTag("preview_bookmarks_container")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Saved Bookmarks (${savedBookmarks.size})",
                                color = GVONETextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tap to preview",
                                color = GVONESecondary,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (savedBookmarks.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No saved bookmarks yet",
                                    color = GVONETextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(savedBookmarks, key = { it.id }) { bookmark ->
                                    val isSelected = bookmark.url.equals(currentUrl, ignoreCase = true)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                // Switch preview to selected bookmark
                                                currentUrl = bookmark.url
                                                currentTitle = bookmark.title.ifBlank { bookmark.url }
                                                showBookmarksList = false
                                                webViewInstance?.loadUrl(bookmark.url)
                                            }
                                            .testTag("preview_bookmark_item_${bookmark.id}"),
                                        color = if (isSelected) Color(0xFF1E2B3E) else Color(0xFF0F1520)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) GVONESecondary.copy(alpha = 0.25f)
                                                        else Color(0x15FFFFFF)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Bookmark,
                                                    contentDescription = null,
                                                    tint = if (isSelected) GVONESecondary else GVONEPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = bookmark.title.ifBlank { bookmark.url },
                                                    color = if (isSelected) GVONESecondary else GVONETextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = bookmark.url,
                                                    color = GVONETextSecondary,
                                                    fontSize = 10.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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

            // Preview WebView Container with isolated touch & nested scrolling
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF141923))
            ) {
                AndroidView(
                    factory = { ctx ->
                        NestedScrollWebView(ctx).apply {
                            isVerticalScrollBarEnabled = true
                            isHorizontalScrollBarEnabled = false
                            overScrollMode = WebView.OVER_SCROLL_IF_CONTENT_SCROLLS

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    loadProgress = newProgress
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    if (!title.isNullOrBlank()) {
                                        currentTitle = title
                                    }
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    loadProgress = 10
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                    }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loadProgress = 100
                                    if (!url.isNullOrBlank()) {
                                        currentUrl = url
                                    }
                                }
                            }
                            loadUrl(currentUrl)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { webView ->
                        webView.stopLoading()
                        webView.destroy()
                        if (webViewInstance == webView) {
                            webViewInstance = null
                        }
                    }
                )
            }
        }
    }
}
