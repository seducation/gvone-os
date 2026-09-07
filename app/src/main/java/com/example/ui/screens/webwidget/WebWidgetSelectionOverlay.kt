package com.example.ui.screens.webwidget

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.WebWidgetCropBounds
import com.example.data.webwidget.DetectedDomElement
import com.example.data.webwidget.WebWidgetExtractionService
import com.example.data.webwidget.WebWidgetSelectionDraft
import kotlinx.coroutines.delay
import java.net.URI

enum class DragHandle {
    NONE,
    BODY,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

@Composable
fun WebWidgetSelectionOverlay(
    currentTab: BrowserTab?,
    activeWebView: WebView?,
    currentEnvironmentId: String,
    onConfirm: (WebWidgetSelectionDraft) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // Box constraints state
    var overlayWidth by remember { mutableStateOf(1080f) }
    var overlayHeight by remember { mutableStateOf(1920f) }

    // Initial crop rectangle centered on screen
    var cropLeft by remember { mutableStateOf(80f) }
    var cropTop by remember { mutableStateOf(300f) }
    var cropWidth by remember { mutableStateOf(700f) }
    var cropHeight by remember { mutableStateOf(500f) }

    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }
    var detectedElement by remember { mutableStateOf<DetectedDomElement?>(null) }
    var isInspecting by remember { mutableStateOf(false) }

    // Inspect webpage DOM when crop changes (debounced)
    LaunchedEffect(cropLeft, cropTop, cropWidth, cropHeight, activeWebView) {
        if (activeWebView == null) return@LaunchedEffect
        delay(250) // debounce inspection while user is dragging
        isInspecting = true
        WebWidgetExtractionService.inspectElementAt(
            webView = activeWebView,
            cropX = cropLeft,
            cropY = cropTop,
            cropW = cropWidth,
            cropH = cropHeight
        ) { detected ->
            detectedElement = detected
            isInspecting = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("web_widget_selection_overlay")
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        LaunchedEffect(widthPx, heightPx) {
            if (widthPx > 0 && heightPx > 0) {
                overlayWidth = widthPx
                overlayHeight = heightPx
                // Initialize default centered crop
                if (cropWidth == 700f && cropHeight == 500f) {
                    cropWidth = (widthPx * 0.85f).coerceAtLeast(300f)
                    cropHeight = (heightPx * 0.35f).coerceAtLeast(200f)
                    cropLeft = (widthPx - cropWidth) / 2f
                    cropTop = (heightPx - cropHeight) / 2.5f
                }
            }
        }

        val handleSizePx = with(density) { 36.dp.toPx() }

        // 1. DIMMED BACKGROUND MASK
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val r = Rect(cropLeft, cropTop, cropLeft + cropWidth, cropTop + cropHeight)
                            val tl = Rect(cropLeft - handleSizePx, cropTop - handleSizePx, cropLeft + handleSizePx, cropTop + handleSizePx)
                            val tr = Rect(cropLeft + cropWidth - handleSizePx, cropTop - handleSizePx, cropLeft + cropWidth + handleSizePx, cropTop + handleSizePx)
                            val bl = Rect(cropLeft - handleSizePx, cropTop + cropHeight - handleSizePx, cropLeft + handleSizePx, cropTop + cropHeight + handleSizePx)
                            val br = Rect(cropLeft + cropWidth - handleSizePx, cropTop + cropHeight - handleSizePx, cropLeft + cropWidth + handleSizePx, cropTop + cropHeight + handleSizePx)

                            activeHandle = when {
                                tl.contains(offset) -> DragHandle.TOP_LEFT
                                tr.contains(offset) -> DragHandle.TOP_RIGHT
                                bl.contains(offset) -> DragHandle.BOTTOM_LEFT
                                br.contains(offset) -> DragHandle.BOTTOM_RIGHT
                                r.contains(offset) -> DragHandle.BODY
                                else -> DragHandle.NONE
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val minSize = 140f
                            when (activeHandle) {
                                DragHandle.BODY -> {
                                    val newLeft = (cropLeft + dragAmount.x).coerceIn(0f, overlayWidth - cropWidth)
                                    val newTop = (cropTop + dragAmount.y).coerceIn(60f, overlayHeight - cropHeight - 120f)
                                    cropLeft = newLeft
                                    cropTop = newTop
                                }
                                DragHandle.TOP_LEFT -> {
                                    val newLeft = (cropLeft + dragAmount.x).coerceIn(0f, cropLeft + cropWidth - minSize)
                                    val newTop = (cropTop + dragAmount.y).coerceIn(60f, cropTop + cropHeight - minSize)
                                    cropWidth += (cropLeft - newLeft)
                                    cropHeight += (cropTop - newTop)
                                    cropLeft = newLeft
                                    cropTop = newTop
                                }
                                DragHandle.TOP_RIGHT -> {
                                    val newRight = (cropLeft + cropWidth + dragAmount.x).coerceIn(cropLeft + minSize, overlayWidth)
                                    val newTop = (cropTop + dragAmount.y).coerceIn(60f, cropTop + cropHeight - minSize)
                                    cropWidth = newRight - cropLeft
                                    cropHeight += (cropTop - newTop)
                                    cropTop = newTop
                                }
                                DragHandle.BOTTOM_LEFT -> {
                                    val newLeft = (cropLeft + dragAmount.x).coerceIn(0f, cropLeft + cropWidth - minSize)
                                    val newBottom = (cropTop + cropHeight + dragAmount.y).coerceIn(cropTop + minSize, overlayHeight - 120f)
                                    cropWidth += (cropLeft - newLeft)
                                    cropHeight = newBottom - cropTop
                                    cropLeft = newLeft
                                }
                                DragHandle.BOTTOM_RIGHT -> {
                                    val newRight = (cropLeft + cropWidth + dragAmount.x).coerceIn(cropLeft + minSize, overlayWidth)
                                    val newBottom = (cropTop + cropHeight + dragAmount.y).coerceIn(cropTop + minSize, overlayHeight - 120f)
                                    cropWidth = newRight - cropLeft
                                    cropHeight = newBottom - cropTop
                                }
                                DragHandle.NONE -> {}
                            }
                        },
                        onDragEnd = {
                            activeHandle = DragHandle.NONE
                        },
                        onDragCancel = {
                            activeHandle = DragHandle.NONE
                        }
                    )
                }
        ) {
            // Draw dimmed mask outside crop box
            val dimColor = Color(0xAA000000)
            // Top rect
            drawRect(dimColor, Offset.Zero, Size(size.width, cropTop))
            // Bottom rect
            drawRect(dimColor, Offset(0f, cropTop + cropHeight), Size(size.width, size.height - (cropTop + cropHeight)))
            // Left rect
            drawRect(dimColor, Offset(0f, cropTop), Size(cropLeft, cropHeight))
            // Right rect
            drawRect(dimColor, Offset(cropLeft + cropWidth, cropTop), Size(size.width - (cropLeft + cropWidth), cropHeight))

            // Draw crop box border
            drawRect(
                color = Color(0xFF38BDF8),
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropWidth, cropHeight),
                style = Stroke(width = 2.dp.toPx())
            )

            // Draw subtle grid lines inside crop box (rule of thirds)
            val thirdW = cropWidth / 3f
            val thirdH = cropHeight / 3f
            val gridColor = Color(0xFF38BDF8).copy(alpha = 0.25f)

            drawLine(gridColor, Offset(cropLeft + thirdW, cropTop), Offset(cropLeft + thirdW, cropTop + cropHeight), strokeWidth = 1f)
            drawLine(gridColor, Offset(cropLeft + thirdW * 2f, cropTop), Offset(cropLeft + thirdW * 2f, cropTop + cropHeight), strokeWidth = 1f)
            drawLine(gridColor, Offset(cropLeft, cropTop + thirdH), Offset(cropLeft + cropWidth, cropTop + thirdH), strokeWidth = 1f)
            drawLine(gridColor, Offset(cropLeft, cropTop + thirdH * 2f), Offset(cropLeft + cropWidth, cropTop + thirdH * 2f), strokeWidth = 1f)

            // Detected element highlight boundary if detected
            detectedElement?.let { el ->
                if (el.rectWidth > 10f && el.rectHeight > 10f) {
                    drawRect(
                        color = Color(0xFF10B981).copy(alpha = 0.6f),
                        topLeft = Offset(el.rectLeft, el.rectTop),
                        size = Size(el.rectWidth, el.rectHeight),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }

        // 2. CORNER HANDLES OVERLAY
        val cornerHandlePositions = listOf(
            Offset(cropLeft, cropTop),
            Offset(cropLeft + cropWidth, cropTop),
            Offset(cropLeft, cropTop + cropHeight),
            Offset(cropLeft + cropWidth, cropTop + cropHeight)
        )

        cornerHandlePositions.forEach { pos ->
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { (pos.x - 12.dp.toPx()).toDp() },
                        y = with(density) { (pos.y - 12.dp.toPx()).toDp() }
                    )
                    .size(24.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFF38BDF8))
                    .border(2.dp, Color.White, CircleShape)
            )
        }

        // 3. DIMENSION & DETECTION BADGE (Pinned directly above the crop box)
        val badgeY = (cropTop - with(density) { 36.dp.toPx() }).coerceAtLeast(70f)
        Surface(
            modifier = Modifier
                .offset(
                    x = with(density) { cropLeft.toDp() },
                    y = with(density) { badgeY.toDp() }
                )
                .shadow(6.dp, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.92f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CropFree,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${cropWidth.toInt()} × ${cropHeight.toInt()} px",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                if (isInspecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        color = Color(0xFF38BDF8),
                        strokeWidth = 1.5.dp
                    )
                } else if (detectedElement != null) {
                    val el = detectedElement!!
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF10B981))
                    ) {
                        Text(
                            text = "<${el.tagName.lowercase()}> ${el.heading.take(18).ifBlank { el.selector.take(18) }}",
                            color = Color(0xFF10B981),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // 4. TOP ACTION BAR
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.94f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DashboardCustomize,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Crop Webpage to Widget",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Select any section to turn into a live dashboard widget",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp
                        )
                    }
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 5. BOTTOM FLOATING CONTROLS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Snap to Element Pill (if detected element has meaningful bounds)
            AnimatedVisibility(
                visible = detectedElement != null && detectedElement?.rectWidth ?: 0f > 50f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                detectedElement?.let { el ->
                    Button(
                        onClick = {
                            cropLeft = el.rectLeft.coerceIn(0f, overlayWidth - 100f)
                            cropTop = el.rectTop.coerceIn(60f, overlayHeight - 100f)
                            cropWidth = el.rectWidth.coerceIn(100f, overlayWidth - cropLeft)
                            cropHeight = el.rectHeight.coerceIn(60f, overlayHeight - cropTop - 100f)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B).copy(alpha = 0.95f),
                            contentColor = Color(0xFF10B981)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FitScreen,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Snap to <${el.tagName.lowercase()}> element",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Main Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", fontSize = 14.sp)
                }

                Button(
                    onClick = {
                        val webView = activeWebView
                        val pageUrl = currentTab?.url ?: "https://"
                        val pageTitle = currentTab?.title ?: "Web Widget"
                        val domain = try {
                            URI(pageUrl).host?.removePrefix("www.") ?: pageTitle
                        } catch (_: Exception) {
                            pageTitle
                        }

                        // Capture high-resolution portion bitmap
                        val bitmap = if (webView != null) {
                            WebWidgetExtractionService.capturePortionBitmap(
                                webView = webView,
                                cropX = cropLeft.toInt(),
                                cropY = cropTop.toInt(),
                                cropWidth = cropWidth.toInt(),
                                cropHeight = cropHeight.toInt()
                            )
                        } else null

                        val base64 = bitmap?.let { WebWidgetExtractionService.bitmapToBase64(it) }

                        val cropBounds = WebWidgetCropBounds(
                            xPercent = cropLeft / overlayWidth,
                            yPercent = cropTop / overlayHeight,
                            widthPercent = cropWidth / overlayWidth,
                            heightPercent = cropHeight / overlayHeight,
                            scrollXPx = activeWebView?.scrollX ?: 0,
                            scrollYPx = activeWebView?.scrollY ?: 0,
                            widthPx = cropWidth.toInt(),
                            heightPx = cropHeight.toInt()
                        )

                        val el = detectedElement
                        val suggestedType = el?.suggestedType ?: com.example.data.model.WebWidgetType.SNAPSHOT

                        val draftTitle = if (!el?.heading.isNullOrBlank()) {
                            el!!.heading
                        } else {
                            "$domain Widget"
                        }

                        val draft = WebWidgetSelectionDraft(
                            title = draftTitle,
                            sourceUrl = pageUrl,
                            siteName = domain,
                            faviconUrl = currentTab?.faviconUrl,
                            widgetType = suggestedType,
                            domSelector = el?.selector,
                            domTagName = el?.tagName,
                            extractedHtml = el?.html,
                            snapshotBitmap = bitmap,
                            snapshotBase64 = base64,
                            cropBounds = cropBounds,
                            detectedElement = el,
                            targetEnvironmentId = currentEnvironmentId
                        )

                        onConfirm(draft)
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1.8f)
                        .testTag("preview_and_add_widget_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Preview & Add",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
