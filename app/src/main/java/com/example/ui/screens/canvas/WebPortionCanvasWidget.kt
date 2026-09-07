package com.example.ui.screens.canvas

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.CanvasObject
import com.example.data.model.WebWidgetInteraction
import com.example.data.model.WebWidgetType
import com.example.data.webwidget.WebWidgetExtractionService

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPortionCanvasWidget(
    widget: CanvasObject.WebPortionWidgetObject,
    isEditMode: Boolean,
    onNavigate: (String) -> Unit,
    onUpdateWidget: (CanvasObject) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isRefreshing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Decode snapshot bitmap if available
    val snapshotBitmap = remember(widget.snapshotBase64) {
        widget.snapshotBase64?.let { WebWidgetExtractionService.base64ToBitmap(it) }
    }

    // Relative timestamp
    val timeAgo = remember(widget.lastRefreshedAt) {
        val diffMs = System.currentTimeMillis() - widget.lastRefreshedAt
        val minutes = (diffMs / 60000).toInt()
        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            else -> "${minutes / 60}h ago"
        }
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !isEditMode) {
                when (widget.interactionMode) {
                    WebWidgetInteraction.OPEN_ORIGINAL -> onNavigate(widget.sourceUrl)
                    WebWidgetInteraction.OPEN_BACKGROUND -> onNavigate(widget.sourceUrl)
                    WebWidgetInteraction.INTERACT_IN_WIDGET -> {
                        // Let child handle interactions
                    }
                }
            }
            .testTag("web_portion_widget_${widget.id}"),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF131B2B),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF223046))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and Source
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    Column {
                        Text(
                            text = widget.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = widget.siteName.ifBlank { "Live Web" },
                            fontSize = 9.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Controls & Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Type Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (widget.widgetType) {
                            WebWidgetType.LIVE_DOM -> Color(0xFF10B981).copy(alpha = 0.2f)
                            WebWidgetType.LIVE_URL -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                            WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            when (widget.widgetType) {
                                WebWidgetType.LIVE_DOM -> Color(0xFF10B981)
                                WebWidgetType.LIVE_URL -> Color(0xFF3B82F6)
                                WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B)
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (widget.widgetType) {
                                            WebWidgetType.LIVE_DOM -> Color(0xFF10B981)
                                            WebWidgetType.LIVE_URL -> Color(0xFF3B82F6)
                                            WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B)
                                        }
                                    )
                            )
                            Text(
                                text = widget.widgetType.displayName,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (widget.widgetType) {
                                    WebWidgetType.LIVE_DOM -> Color(0xFF10B981)
                                    WebWidgetType.LIVE_URL -> Color(0xFF3B82F6)
                                    WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B)
                                }
                            )
                        }
                    }

                    // Open link button
                    IconButton(
                        onClick = { onNavigate(widget.sourceUrl) },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Open Webpage",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Menu dropdown
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Options",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Open Webpage", fontSize = 12.sp, color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.OpenInBrowser, null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onNavigate(widget.sourceUrl)
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Refresh Content", fontSize = 12.sp, color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Refresh, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    isRefreshing = true
                                    onUpdateWidget(widget.copy(lastRefreshedAt = System.currentTimeMillis()))
                                }
                            )

                            val nextType = when (widget.widgetType) {
                                WebWidgetType.LIVE_DOM -> WebWidgetType.SNAPSHOT
                                WebWidgetType.SNAPSHOT -> WebWidgetType.LIVE_URL
                                WebWidgetType.LIVE_URL -> WebWidgetType.LIVE_DOM
                            }

                            DropdownMenuItem(
                                text = { Text("Switch to ${nextType.displayName}", fontSize = 12.sp, color = Color.White) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.SwapHoriz, null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                                },
                                onClick = {
                                    showMenu = false
                                    onUpdateWidget(widget.copy(widgetType = nextType))
                                }
                            )
                        }
                    }
                }
            }

            // Widget Body Display
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF0A0F1D)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    // LIVE_DOM mode with extracted HTML
                    widget.widgetType == WebWidgetType.LIVE_DOM && !widget.extractedHtml.isNullOrBlank() -> {
                        val preparedHtml = remember(widget.extractedHtml, widget.sourceUrl) {
                            WebWidgetExtractionService.prepareDomWidgetHtml(
                                originalHtml = widget.extractedHtml,
                                baseUrl = widget.sourceUrl,
                                isDark = true
                            )
                        }

                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(0xFF0A0F1D.toInt())
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.textZoom = 85

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString()
                                            if (targetUrl != null) {
                                                onNavigate(targetUrl)
                                            }
                                            return true
                                        }
                                    }

                                    if (widget.interactionMode == WebWidgetInteraction.OPEN_ORIGINAL) {
                                        setOnTouchListener { _, event ->
                                            if (event.action == MotionEvent.ACTION_UP && !isEditMode) {
                                                onNavigate(widget.sourceUrl)
                                            }
                                            true
                                        }
                                    }

                                    loadDataWithBaseURL(
                                        widget.sourceUrl,
                                        preparedHtml,
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            },
                            update = { wv ->
                                wv.loadDataWithBaseURL(
                                    widget.sourceUrl,
                                    preparedHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // LIVE_URL mode (embedded clipped webview)
                    widget.widgetType == WebWidgetType.LIVE_URL -> {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    setBackgroundColor(0xFF0A0F1D.toInt())
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true

                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // Scroll to the saved position
                                            scrollTo(widget.cropBounds.scrollXPx, widget.cropBounds.scrollYPx)
                                        }

                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val targetUrl = request?.url?.toString()
                                            if (targetUrl != null && widget.interactionMode != WebWidgetInteraction.INTERACT_IN_WIDGET) {
                                                onNavigate(targetUrl)
                                                return true
                                            }
                                            return false
                                        }
                                    }

                                    if (widget.interactionMode == WebWidgetInteraction.OPEN_ORIGINAL) {
                                        setOnTouchListener { _, event ->
                                            if (event.action == MotionEvent.ACTION_UP && !isEditMode) {
                                                onNavigate(widget.sourceUrl)
                                            }
                                            true
                                        }
                                    }

                                    loadUrl(widget.sourceUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // SNAPSHOT or fallback mode
                    snapshotBitmap != null -> {
                        Image(
                            bitmap = snapshotBitmap.asImageBitmap(),
                            contentDescription = widget.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Empty state fallback
                    else -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DashboardCustomize,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = widget.title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap to open original webpage",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Footer Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A).copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timeAgo,
                    fontSize = 9.sp,
                    color = Color(0xFF64748B)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!widget.isLiveValid) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = "Warning",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "DOM Changed",
                            fontSize = 9.sp,
                            color = Color(0xFFF59E0B)
                        )
                    } else {
                        Text(
                            text = "GVONE Live",
                            fontSize = 9.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}
