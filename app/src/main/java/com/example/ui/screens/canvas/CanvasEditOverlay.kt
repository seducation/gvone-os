package com.example.ui.screens.canvas

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import java.util.UUID

@Composable
fun CanvasCustomizationDock(
    layoutMode: EnvironmentLayoutMode,
    onAddLink: () -> Unit,
    onAddWidget: () -> Unit,
    onAddFolder: () -> Unit,
    onAddNote: () -> Unit,
    onChangeBackground: () -> Unit,
    onToggleLayoutMode: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
        shadowElevation = 16.dp,
        modifier = modifier
            .testTag("canvas_customize_dock")
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DockActionButton(Icons.Rounded.AddLink, "Link", Color(0xFF38BDF8), onAddLink)
            DockActionButton(Icons.Rounded.Widgets, "Widget", Color(0xFFA855F7), onAddWidget)
            DockActionButton(Icons.Rounded.CreateNewFolder, "Folder", Color(0xFF10B981), onAddFolder)
            DockActionButton(Icons.Rounded.NoteAdd, "Note", Color(0xFFF59E0B), onAddNote)
            DockActionButton(Icons.Rounded.Palette, "Background", Color(0xFFEC4899), onChangeBackground)

            DockActionButton(
                icon = when (layoutMode) {
                    EnvironmentLayoutMode.GRID -> Icons.Rounded.GridView
                    EnvironmentLayoutMode.FREEFORM -> Icons.Rounded.PanTool
                    EnvironmentLayoutMode.AUTO -> Icons.Rounded.ViewAgenda
                },
                label = layoutMode.displayName,
                tint = Color(0xFF60A5FA),
                onClick = onToggleLayoutMode
            )

            VerticalDivider(
                modifier = Modifier
                    .height(26.dp)
                    .padding(horizontal = 4.dp),
                color = Color.White.copy(alpha = 0.15f)
            )

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("dock_done_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DockActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
            Text(text = label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// -------------------------------------------------------------
// ADD LINK DIALOG
// -------------------------------------------------------------
@Composable
fun AddLinkDialog(
    onDismiss: () -> Unit,
    onConfirm: (CanvasObject.LinkObject) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var selectedIcon by remember { mutableStateOf("Globe") }
    var selectedColor by remember { mutableStateOf("#3B82F6") }

    val iconOptions = listOf("Globe", "Video", "Book", "Forum", "Code", "Search", "Devices", "TrendingUp", "Shopping", "Music")
    val colorOptions = listOf("#3B82F6", "#10B981", "#F59E0B", "#EF4444", "#8B5CF6", "#EC4899", "#06B6D4")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Add Link to Canvas", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. YouTube)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL (https://...)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(iconOptions) { iconName ->
                        val isSelected = (iconName == selectedIcon)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.3f) else Color(0xFF1E293B))
                                .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = getIconForName(iconName), contentDescription = iconName, tint = if (isSelected) Color(0xFF38BDF8) else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Text("Accent Color", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colorOptions) { colorHex ->
                        val isSelected = (colorHex == selectedColor)
                        val color = parseColorSafe(colorHex)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (url.isNotBlank()) {
                        val finalTitle = title.ifBlank {
                            url.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")
                        }
                        onConfirm(
                            CanvasObject.LinkObject(
                                id = UUID.randomUUID().toString(),
                                title = finalTitle,
                                url = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url",
                                iconName = selectedIcon,
                                accentColorHex = selectedColor,
                                width = 1f,
                                height = 1f
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
            ) {
                Text("Add Link", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
}

// -------------------------------------------------------------
// ADD WIDGET DIALOG
// -------------------------------------------------------------
@Composable
fun AddWidgetDialog(
    onDismiss: () -> Unit,
    onConfirm: (CanvasObject.WidgetObject) -> Unit
) {
    val widgetTypes = CanvasWidgetType.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Select Widget", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(widgetTypes) { wType ->
                    Surface(
                        onClick = {
                            val defaultWidth = when (wType) {
                                CanvasWidgetType.SEARCH, CanvasWidgetType.RSS_FEED -> 4f
                                else -> 2f
                            }
                            val defaultHeight = when (wType) {
                                CanvasWidgetType.SEARCH -> 1.0f
                                CanvasWidgetType.DATE_CALENDAR -> 1.6f
                                CanvasWidgetType.RSS_FEED -> 1.6f
                                else -> 1.3f
                            }
                            onConfirm(
                                CanvasObject.WidgetObject(
                                    id = UUID.randomUUID().toString(),
                                    widgetType = wType,
                                    width = defaultWidth,
                                    height = defaultHeight
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFA855F7).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (wType) {
                                        CanvasWidgetType.CLOCK -> Icons.Rounded.Schedule
                                        CanvasWidgetType.DATE_CALENDAR -> Icons.Rounded.CalendarToday
                                        CanvasWidgetType.WEATHER -> Icons.Rounded.WbCloudy
                                        CanvasWidgetType.SEARCH -> Icons.Rounded.Search
                                        CanvasWidgetType.SYSTEM_INFO -> Icons.Rounded.Memory
                                        CanvasWidgetType.NOTES_WIDGET -> Icons.Rounded.EditNote
                                        CanvasWidgetType.RSS_FEED -> Icons.Rounded.RssFeed
                                        CanvasWidgetType.QUICK_TOOLS -> Icons.Rounded.Apps
                                    },
                                    contentDescription = null,
                                    tint = Color(0xFFC084FC),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = wType.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = wType.subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
}

// -------------------------------------------------------------
// ADD FOLDER DIALOG
// -------------------------------------------------------------
@Composable
fun AddFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (CanvasObject.FolderObject) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#8B5CF6") }
    val colorOptions = listOf("#8B5CF6", "#3B82F6", "#10B981", "#F59E0B", "#EC4899")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Create Folder", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF8B5CF6),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Folder Color", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colorOptions) { colorHex ->
                        val isSelected = (colorHex == selectedColor)
                        val color = parseColorSafe(colorHex)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        CanvasObject.FolderObject(
                            id = UUID.randomUUID().toString(),
                            title = title.ifBlank { "New Folder" },
                            accentColorHex = selectedColor,
                            width = 2f,
                            height = 1.3f,
                            items = listOf(
                                FolderItem(UUID.randomUUID().toString(), "Search", "https://duckduckgo.com", "Search", selectedColor),
                                FolderItem(UUID.randomUUID().toString(), "News", "https://news.ycombinator.com", "Forum", selectedColor)
                            )
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Text("Create", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
}

// -------------------------------------------------------------
// ADD NOTE DIALOG
// -------------------------------------------------------------
@Composable
fun AddNoteDialog(
    onDismiss: () -> Unit,
    onConfirm: (CanvasObject.NoteObject) -> Unit
) {
    var title by remember { mutableStateOf("Scratchpad") }
    var content by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#F59E0B") }
    val colors = listOf("#F59E0B", "#10B981", "#3B82F6", "#8B5CF6", "#EC4899", "#64748B")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("Add Note to Canvas", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = parseColorSafe(selectedColor),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note Content") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = parseColorSafe(selectedColor),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Note Color", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { colorHex ->
                        val isSelected = (colorHex == selectedColor)
                        val color = parseColorSafe(colorHex)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                .clickable { selectedColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        CanvasObject.NoteObject(
                            id = UUID.randomUUID().toString(),
                            title = title.ifBlank { "Quick Note" },
                            content = content,
                            colorHex = selectedColor,
                            width = 2f,
                            height = 1.4f
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = parseColorSafe(selectedColor))
            ) {
                Text("Add Note", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
}

// -------------------------------------------------------------
// BACKGROUND CUSTOMIZER SHEET
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundCustomizerSheet(
    currentBackground: EnvironmentBackground,
    onUpdateBackground: (EnvironmentBackground) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPreset by remember { mutableStateOf(currentBackground.presetId) }
    var blurRadius by remember { mutableFloatStateOf(currentBackground.blurRadius) }
    var opacity by remember { mutableFloatStateOf(currentBackground.opacity) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Canvas Background & Atmosphere",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text("Atmospheric Presets", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(EnvironmentPresets.BACKGROUND_PRESETS) { preset ->
                    val isSelected = (preset.id == selectedPreset)
                    val pColor = parseColorSafe(preset.primaryHex)
                    val sColor = parseColorSafe(preset.secondaryHex)

                    Surface(
                        onClick = {
                            selectedPreset = preset.id
                            onUpdateBackground(
                                currentBackground.copy(
                                    presetId = preset.id,
                                    type = preset.type,
                                    primaryColorHex = preset.primaryHex,
                                    secondaryColorHex = preset.secondaryHex,
                                    accentColorHex = preset.accentHex,
                                    blurRadius = blurRadius,
                                    opacity = opacity
                                )
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            2.dp,
                            if (isSelected) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.width(110.dp).height(74.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(pColor, sColor)))
                                .padding(8.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = preset.name,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Opacity slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Canvas Surface Opacity", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("${(opacity * 100).toInt()}%", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = opacity,
                    onValueChange = {
                        opacity = it
                        onUpdateBackground(currentBackground.copy(opacity = it))
                    },
                    valueRange = 0.4f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF38BDF8),
                        activeTrackColor = Color(0xFF38BDF8)
                    )
                )
            }

            // Blur radius slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Atmospheric Blur", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("${blurRadius.toInt()}dp", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Slider(
                    value = blurRadius,
                    onValueChange = {
                        blurRadius = it
                        onUpdateBackground(currentBackground.copy(blurRadius = it))
                    },
                    valueRange = 0f..25f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFA855F7),
                        activeTrackColor = Color(0xFFA855F7)
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// -------------------------------------------------------------
// ENVIRONMENT SWITCHER SHEET (Safari Profiles inspired)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvironmentSwitchSheet(
    environments: List<Environment>,
    activeEnvironmentId: String,
    onSelectEnvironment: (String) -> Unit,
    onCreateEnvironment: (name: String, icon: String, theme: String, preset: String, initialLinkUrl: String?, initialLinkTitle: String?) -> Unit,
    onDuplicateEnvironment: (String) -> Unit,
    onDeleteEnvironment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0B0F19),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Browser Environments",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Independent workspaces with custom canvas layouts",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF1E293B)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(environments) { env ->
                    val isActive = (env.id == activeEnvironmentId)
                    val icon = getIconForName(env.iconName)
                    val primaryColor = parseColorSafe(env.background.primaryColorHex)
                    val secondaryColor = parseColorSafe(env.background.secondaryColorHex)

                    Surface(
                        onClick = {
                            onSelectEnvironment(env.id)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isActive) Color(0xFF1E293B) else Color(0xFF131826).copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.2.dp,
                            if (isActive) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.06f)
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("env_item_${env.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Brush.linearGradient(listOf(primaryColor, secondaryColor)))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = env.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (isActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Active", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = if (!env.startPageUrl.isNullOrBlank()) {
                                        "Start page: ${env.startPageUrl}"
                                    } else {
                                        "${env.objects.size} items • ${env.background.presetId.replaceFirstChar { it.uppercase() }}"
                                    },
                                    color = if (!env.startPageUrl.isNullOrBlank()) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Duplicate button
                            IconButton(
                                onClick = { onDuplicateEnvironment(env.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                            }

                            // Delete button if more than 1
                            if (environments.size > 1) {
                                IconButton(
                                    onClick = { onDeleteEnvironment(env.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showCreateDialog) {
        CreateEnvironmentDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, icon, theme, preset, initialLinkUrl, initialLinkTitle ->
                onCreateEnvironment(name, icon, theme, preset, initialLinkUrl, initialLinkTitle)
                showCreateDialog = false
                onDismiss()
            }
        )
    }
}

// -------------------------------------------------------------
// CREATE ENVIRONMENT DIALOG WITH LINK SUPPORT
// -------------------------------------------------------------
@Composable
fun CreateEnvironmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, icon: String, theme: String, preset: String, initialLinkUrl: String?, initialLinkTitle: String?) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("Person") }
    var selectedPreset by remember { mutableStateOf("aurora") }
    var initialLinkUrl by remember { mutableStateOf("") }
    var initialLinkTitle by remember { mutableStateOf("") }

    val icons = listOf("Person", "School", "Work", "Code", "SportsEsports", "Spa", "Rocket", "Star", "Folder", "Lightbulb", "Palette", "Public")
    val linkPresets = listOf(
        Pair("RSS Group Feed", "https://rssgroupfeed-jaelvwfd.manus.space"),
        Pair("DuckDuckGo", "https://duckduckgo.com"),
        Pair("GitHub", "https://github.com"),
        Pair("Wikipedia", "https://en.wikipedia.org")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        title = { Text("New Environment", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Environment Name (e.g. Research)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Icon", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(icons) { iconName ->
                        val isSelected = (iconName == selectedIcon)
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.3f) else Color(0xFF1E293B))
                                .border(1.dp, if (isSelected) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { selectedIcon = iconName },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = getIconForName(iconName), contentDescription = iconName, tint = if (isSelected) Color(0xFF38BDF8) else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Text("Theme Palette", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(EnvironmentPresets.BACKGROUND_PRESETS.take(5)) { preset ->
                        val isSelected = (preset.id == selectedPreset)
                        Surface(
                            onClick = { selectedPreset = preset.id },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(2.dp, if (isSelected) Color(0xFF38BDF8) else Color.Transparent)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(parseColorSafe(preset.primaryHex), parseColorSafe(preset.secondaryHex))
                                        )
                                    )
                            )
                        }
                    }
                }

                // Initial Link Support
                HorizontalDivider(color = Color(0xFF1E293B), modifier = Modifier.padding(vertical = 2.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Link, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Text("Starting Link / Default Start Page", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }

                Text("Paste or pick any website URL to automatically open as this environment's start page:", color = Color(0xFF94A3B8), fontSize = 11.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(linkPresets) { preset ->
                        Surface(
                            onClick = {
                                initialLinkTitle = preset.first
                                initialLinkUrl = preset.second
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (initialLinkUrl == preset.second) Color(0xFF0284C7).copy(alpha = 0.3f) else Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (initialLinkUrl == preset.second) Color(0xFF38BDF8) else Color(0xFF334155))
                        ) {
                            Text(
                                text = preset.first,
                                color = if (initialLinkUrl == preset.second) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = initialLinkTitle,
                    onValueChange = { initialLinkTitle = it },
                    label = { Text("Link Title (e.g. RSS Feed)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = initialLinkUrl,
                    onValueChange = { initialLinkUrl = it },
                    label = { Text("Link URL (e.g. https://...)") },
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(
                            newName.trim(),
                            selectedIcon,
                            "dark",
                            selectedPreset,
                            initialLinkUrl.ifBlank { null },
                            initialLinkTitle.ifBlank { null }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
            ) {
                Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
}
