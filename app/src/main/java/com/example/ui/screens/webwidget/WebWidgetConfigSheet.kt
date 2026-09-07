package com.example.ui.screens.webwidget

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.data.webwidget.WebWidgetSelectionDraft
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebWidgetConfigSheet(
    draft: WebWidgetSelectionDraft,
    environments: List<Environment>,
    onAddWidget: (CanvasObject.WebPortionWidgetObject, String) -> Unit,
    onBackToSelection: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf(draft.title) }
    var selectedType by remember { mutableStateOf(draft.widgetType) }
    var selectedSize by remember { mutableStateOf(Pair(2f, 1.6f)) } // width, height in grid units
    var refreshInterval by remember { mutableStateOf(draft.refreshIntervalMinutes) }
    var interactionMode by remember { mutableStateOf(draft.interactionMode) }
    var targetEnvironmentId by remember { mutableStateOf(draft.targetEnvironmentId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF334155))
        },
        modifier = modifier.testTag("web_widget_config_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DashboardCustomize,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Configure Web Widget",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = draft.siteName.ifBlank { draft.sourceUrl },
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 1. LIVE PREVIEW CARD
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Widget Preview",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF182234),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D52))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Widget Top Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                Text(
                                    text = title.ifBlank { "Web Widget" },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 160.dp)
                                )
                            }

                            // Widget Type Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (selectedType) {
                                    WebWidgetType.LIVE_DOM -> Color(0xFF10B981).copy(alpha = 0.2f)
                                    WebWidgetType.LIVE_URL -> Color(0xFF3B82F6).copy(alpha = 0.2f)
                                    WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B).copy(alpha = 0.2f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    when (selectedType) {
                                        WebWidgetType.LIVE_DOM -> Color(0xFF10B981)
                                        WebWidgetType.LIVE_URL -> Color(0xFF3B82F6)
                                        WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B)
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (selectedType) {
                                                    WebWidgetType.LIVE_DOM -> Color(0xFF10B981)
                                                    WebWidgetType.LIVE_URL -> Color(0xFF3B82F6)
                                                    WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B)
                                                }
                                            )
                                    )
                                    Text(
                                        text = selectedType.displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = when (selectedType) {
                                            WebWidgetType.LIVE_DOM -> Color(0xFF10B981)
                                            WebWidgetType.LIVE_URL -> Color(0xFF3B82F6)
                                            WebWidgetType.SNAPSHOT -> Color(0xFFF59E0B)
                                        }
                                    )
                                }
                            }
                        }

                        // Widget Content Body Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (draft.snapshotBitmap != null) {
                                Image(
                                    bitmap = draft.snapshotBitmap.asImageBitmap(),
                                    contentDescription = "Widget Preview Content",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Widgets,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Live Content Preview",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        // Bottom status line
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Origin: ${draft.siteName}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "Refreshes: ${if (refreshInterval == 0) "Manual" else "Every ${refreshInterval}m"}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            // 2. WIDGET TITLE
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Widget Name",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedContainerColor = Color(0xFF182234),
                        unfocusedContainerColor = Color(0xFF182234)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("widget_title_input")
                )
            }

            // 3. WIDGET TYPE SELECTOR
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Widget Engine Mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )

                val types = listOf(
                    Triple(
                        WebWidgetType.LIVE_DOM,
                        "Live DOM Element",
                        "Extracts live updating HTML element & styles directly from page DOM"
                    ),
                    Triple(
                        WebWidgetType.LIVE_URL,
                        "Live Web Window",
                        "Embedded sandboxed web view cropped to exact coordinates"
                    ),
                    Triple(
                        WebWidgetType.SNAPSHOT,
                        "Visual Snapshot",
                        "High-res visual capture, perfect for bot-protected or DRM sites"
                    )
                )

                types.forEach { (type, name, desc) ->
                    val isSelected = selectedType == type
                    Surface(
                        onClick = { selectedType = type },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color(0xFF131B2B),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF223046)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF38BDF8),
                                    unselectedColor = Color(0xFF64748B)
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                )
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            // 4. CANVAS SIZE RECOMMENDATIONS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Canvas Grid Size",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )

                val sizes = listOf(
                    Triple(Pair(1f, 1f), "1 × 1", "Compact"),
                    Triple(Pair(2f, 1.4f), "2 × 1", "Standard Wide"),
                    Triple(Pair(2f, 2f), "2 × 2", "Square Card"),
                    Triple(Pair(4f, 1.8f), "4 × 2", "Full Width Hero")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sizes.forEach { (dim, label, sub) ->
                        val isSelected = selectedSize == dim
                        Surface(
                            onClick = { selectedSize = dim },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF182234),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF273549)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color.White
                                )
                                Text(
                                    text = sub,
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            // 5. REFRESH SETTINGS
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Auto-Refresh Interval",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )

                val intervals = listOf(
                    Pair(0, "Manual"),
                    Pair(5, "5 min"),
                    Pair(15, "15 min"),
                    Pair(30, "30 min"),
                    Pair(60, "1 hr")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    intervals.forEach { (mins, lbl) ->
                        val isSelected = refreshInterval == mins
                        Surface(
                            onClick = { refreshInterval = mins },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF182234),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF38BDF8) else Color(0xFF273549)
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lbl,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFFCBD5E1)
                                )
                            }
                        }
                    }
                }
            }

            // 6. INTERACTION BEHAVIOR
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "On-Click Behavior",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )

                val behaviors = listOf(
                    Pair(WebWidgetInteraction.OPEN_ORIGINAL, "Open Original Webpage in Browser"),
                    Pair(WebWidgetInteraction.INTERACT_IN_WIDGET, "Interact Directly Inside Widget"),
                    Pair(WebWidgetInteraction.OPEN_BACKGROUND, "Open Webpage in Background Tab")
                )

                behaviors.forEach { (mode, lbl) ->
                    val isSelected = interactionMode == mode
                    Surface(
                        onClick = { interactionMode = mode },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color(0xFF131B2B),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color(0xFF223046)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { interactionMode = mode },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF38BDF8),
                                    unselectedColor = Color(0xFF64748B)
                                )
                            )
                            Text(
                                text = lbl,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // 7. TARGET ENVIRONMENT SELECTOR
            if (environments.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Place on Environment",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        environments.forEach { env ->
                            val isSelected = targetEnvironmentId == env.id
                            Surface(
                                onClick = { targetEnvironmentId = env.id },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF182234),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF273549)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = env.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF38BDF8) else Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBackToSelection,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Back to Crop", fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val widgetObject = CanvasObject.WebPortionWidgetObject(
                            id = UUID.randomUUID().toString(),
                            type = CanvasObjectType.WEB_PORTION,
                            x = 0f,
                            y = 0f,
                            width = selectedSize.first,
                            height = selectedSize.second,
                            zIndex = 0,
                            title = title.ifBlank { "Web Widget" },
                            sourceUrl = draft.sourceUrl,
                            siteName = draft.siteName,
                            faviconUrl = draft.faviconUrl,
                            widgetType = selectedType,
                            domSelector = draft.domSelector,
                            domTagName = draft.domTagName,
                            extractedHtml = draft.extractedHtml,
                            snapshotBase64 = draft.snapshotBase64,
                            cropBounds = draft.cropBounds,
                            refreshIntervalMinutes = refreshInterval,
                            lastRefreshedAt = System.currentTimeMillis(),
                            interactionMode = interactionMode,
                            isLiveValid = true
                        )
                        onAddWidget(widgetObject, targetEnvironmentId)
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .weight(1.8f)
                        .testTag("add_widget_to_start_page_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Add to Start Page",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}
