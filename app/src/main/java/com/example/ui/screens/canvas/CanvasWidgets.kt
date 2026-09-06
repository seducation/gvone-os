package com.example.ui.screens.canvas

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment as AndroidEnv
import android.os.StatFs
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

fun getIconForName(iconName: String): ImageVector {
    return when (iconName) {
        "Person" -> Icons.Rounded.Person
        "School" -> Icons.Rounded.School
        "Work" -> Icons.Rounded.Work
        "Code" -> Icons.Rounded.Code
        "SportsEsports" -> Icons.Rounded.SportsEsports
        "Search" -> Icons.Rounded.Search
        "Globe" -> Icons.Rounded.Public
        "Video" -> Icons.Rounded.PlayCircle
        "Book" -> Icons.Rounded.MenuBook
        "Forum" -> Icons.Rounded.Forum
        "Folder" -> Icons.Rounded.Folder
        "Spa" -> Icons.Rounded.Spa
        "Devices" -> Icons.Rounded.Devices
        "TrendingUp" -> Icons.Rounded.TrendingUp
        "AutoAwesome" -> Icons.Rounded.AutoAwesome
        "Music" -> Icons.Rounded.MusicNote
        "Shopping" -> Icons.Rounded.ShoppingCart
        "Mail" -> Icons.Rounded.Mail
        "Shield" -> Icons.Rounded.Shield
        "Settings" -> Icons.Rounded.Settings
        "Star" -> Icons.Rounded.Star
        else -> Icons.Rounded.Language
    }
}

fun parseColorSafe(hex: String, fallback: Color = Color(0xFF3B82F6)): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

// -------------------------------------------------------------
// 1. LINK OBJECT COMPONENT
// -------------------------------------------------------------
@Composable
fun LinkCanvasCard(
    link: CanvasObject.LinkObject,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = remember(link.accentColorHex) { parseColorSafe(link.accentColorHex, Color(0xFF3B82F6)) }
    val icon = remember(link.iconName) { getIconForName(link.iconName) }

    Surface(
        onClick = { onNavigate(link.url) },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF131826).copy(alpha = 0.72f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("link_${link.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(accentColor.copy(alpha = 0.35f), accentColor.copy(alpha = 0.10f))
                        )
                    )
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = link.title,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = link.title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// -------------------------------------------------------------
// 2. FOLDER OBJECT COMPONENT
// -------------------------------------------------------------
@Composable
fun FolderCanvasCard(
    folder: CanvasObject.FolderObject,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val accentColor = remember(folder.accentColorHex) { parseColorSafe(folder.accentColorHex, Color(0xFF8B5CF6)) }

    Surface(
        onClick = { isExpanded = true },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF131826).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("folder_${folder.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = folder.title,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${folder.items.size} bookmarks",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Open Folder",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Mini previews of items
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                folder.items.take(4).forEach { item ->
                    val itemColor = parseColorSafe(item.accentColorHex, accentColor)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.8f))
                            .border(0.8.dp, itemColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getIconForName(item.iconName),
                            contentDescription = item.title,
                            tint = itemColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    if (isExpanded) {
        AlertDialog(
            onDismissRequest = { isExpanded = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = accentColor)
                    }
                    Text(text = folder.title, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                if (folder.items.isEmpty()) {
                    Text("This folder is empty.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 280.dp)
                    ) {
                        items(folder.items) { item ->
                            val itemColor = parseColorSafe(item.accentColorHex, accentColor)
                            Surface(
                                onClick = {
                                    isExpanded = false
                                    onNavigate(item.url)
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.6f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
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
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(itemColor.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = getIconForName(item.iconName),
                                            contentDescription = null,
                                            tint = itemColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = item.url,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "Open",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isExpanded = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// 3. NOTE OBJECT COMPONENT
// -------------------------------------------------------------
@Composable
fun NoteCanvasCard(
    note: CanvasObject.NoteObject,
    onUpdateNote: (CanvasObject.NoteObject) -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedTitle by remember(note.title) { mutableStateOf(note.title) }
    var editedContent by remember(note.content) { mutableStateOf(note.content) }
    val noteColor = remember(note.colorHex) { parseColorSafe(note.colorHex, Color(0xFFF59E0B)) }

    Surface(
        onClick = { isEditing = true },
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF151926).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, noteColor.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("note_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(noteColor)
                    )
                    Text(
                        text = note.title.ifBlank { "Quick Note" },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit Note",
                    tint = noteColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = note.content.ifBlank { "Tap to write note on canvas..." },
                color = if (note.content.isBlank()) Color(0xFF64748B) else Color(0xFFCBD5E1),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (isEditing) {
        AlertDialog(
            onDismissRequest = { isEditing = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Text("Edit Note", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editedTitle,
                        onValueChange = { editedTitle = it },
                        label = { Text("Note Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = noteColor,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editedContent,
                        onValueChange = { editedContent = it },
                        label = { Text("Content") },
                        minLines = 4,
                        maxLines = 6,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = noteColor,
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isEditing = false
                        onUpdateNote(note.copy(title = editedTitle, content = editedContent))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = noteColor)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

// -------------------------------------------------------------
// 4. CLOCK WIDGET COMPONENT
// -------------------------------------------------------------
@Composable
fun ClockCanvasWidget(
    widget: CanvasObject.WidgetObject,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000)
        }
    }

    val timeFormat = remember { SimpleDateFormat("h:mm", Locale.getDefault()) }
    val secondsFormat = remember { SimpleDateFormat("ss", Locale.getDefault()) }
    val amPmFormat = remember { SimpleDateFormat("a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101524).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("widget_clock")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = timeFormat.format(currentTime),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = secondsFormat.format(currentTime),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Text(
                    text = amPmFormat.format(currentTime),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = dateFormat.format(currentTime),
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 5. CALENDAR WIDGET COMPONENT
// -------------------------------------------------------------
@Composable
fun CalendarCanvasWidget(
    widget: CanvasObject.WidgetObject,
    modifier: Modifier = Modifier
) {
    val calendar = remember { Calendar.getInstance() }
    val today = remember { Calendar.getInstance().get(Calendar.DAY_OF_MONTH) }
    val monthTitle = remember {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
    }

    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101524).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA855F7).copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("widget_calendar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = monthTitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFA855F7).copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Today $today", fontSize = 10.sp, color = Color(0xFFC084FC), fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day labels
            val dayHeaders = listOf("S", "M", "T", "W", "T", "F", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dayHeaders.forEach {
                    Text(
                        text = it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Simplified week days view around today
            val startDay = (today - 3).coerceAtLeast(1)
            val endDay = (startDay + 6).coerceAtMost(daysInMonth)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (d in startDay..endDay) {
                    val isToday = (d == today)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .padding(1.dp)
                            .clip(CircleShape)
                            .background(if (isToday) Color(0xFFA855F7) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = d.toString(),
                            fontSize = 11.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 6. WEATHER WIDGET COMPONENT
// -------------------------------------------------------------
@Composable
fun WeatherCanvasWidget(
    widget: CanvasObject.WidgetObject,
    modifier: Modifier = Modifier
) {
    val location = widget.config["location"] ?: "San Francisco"
    val temp = widget.config["temp"] ?: "68°F"
    val condition = widget.config["condition"] ?: "Partly Cloudy"

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101524).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("widget_weather")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = temp,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = condition,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8)
                )
                Text(
                    text = location,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0284C7).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WbCloudy,
                    contentDescription = "Weather",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 7. UNIVERSAL SEARCH WIDGET COMPONENT
// -------------------------------------------------------------
@Composable
fun SearchCanvasWidget(
    widget: CanvasObject.WidgetObject,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedEngine by remember { mutableStateOf("GVONE AI") }
    var showEngineMenu by remember { mutableStateOf(false) }

    val engines = listOf("GVONE AI", "Google", "DuckDuckGo", "Brave", "Bing")

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF121727).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("widget_search")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box {
                Surface(
                    onClick = { showEngineMenu = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedEngine,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showEngineMenu,
                    onDismissRequest = { showEngineMenu = false }
                ) {
                    engines.forEach { engine ->
                        DropdownMenuItem(
                            text = { Text(engine) },
                            onClick = {
                                selectedEngine = engine
                                showEngineMenu = false
                            }
                        )
                    }
                }
            }

            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                ),
                singleLine = true,
                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            val url = when (selectedEngine) {
                                "Google" -> "https://www.google.com/search?q=${searchQuery.trim()}"
                                "DuckDuckGo" -> "https://duckduckgo.com/?q=${searchQuery.trim()}"
                                "Brave" -> "https://search.brave.com/search?q=${searchQuery.trim()}"
                                "Bing" -> "https://www.bing.com/search?q=${searchQuery.trim()}"
                                else -> "https://duckduckgo.com/?q=${searchQuery.trim()}"
                            }
                            onNavigate(url)
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search the web or ask AI...",
                            color = Color(0xFF64748B),
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                },
                modifier = Modifier.weight(1f)
            )

            if (searchQuery.isNotBlank()) {
                IconButton(
                    onClick = {
                        val url = "https://duckduckgo.com/?q=${searchQuery.trim()}"
                        onNavigate(url)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "Search",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// 8. SYSTEM INFO WIDGET COMPONENT
// -------------------------------------------------------------
@Composable
fun SystemInfoCanvasWidget(
    widget: CanvasObject.WidgetObject,
    isTorActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val batteryPct = remember {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) (level * 100 / scale) else 85
    }

    val freeStorageGb = remember {
        try {
            val stat = StatFs(AndroidEnv.getDataDirectory().path)
            val bytesAvailable = stat.blockSizeLong * stat.availableBlocksLong
            bytesAvailable / (1024 * 1024 * 1024)
        } catch (e: Exception) {
            42L
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101524).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("widget_sysinfo")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "System Telemetry",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Battery
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                        Text(text = "$batteryPct%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(text = "Battery", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }

                // Storage
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Storage, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(14.dp))
                        Text(text = "${freeStorageGb}GB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(text = "Free Space", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }

                // Privacy Shield
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                        Text(text = if (isTorActive) "Tor ON" else "Shield", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(text = if (isTorActive) "Onion Proxy" else "Active", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 9. RSS / FEED WIDGET COMPONENT
// -------------------------------------------------------------
data class RssHeadline(val title: String, val source: String, val url: String, val time: String)

@Composable
fun RssFeedCanvasWidget(
    widget: CanvasObject.WidgetObject,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val headlines = remember {
        listOf(
            RssHeadline("Physicists Reveal a Quantum Geometry Outside Space and Time", "Quanta Magazine", "https://www.quantamagazine.org", "2h ago"),
            RssHeadline("AI Accelerators and Neural Silicon Architecture in 2026", "Ars Technica", "https://arstechnica.com", "4h ago"),
            RssHeadline("Breakthroughs in Commercial Fusion Energy Confinement", "Nature", "https://www.nature.com", "6h ago")
        )
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101524).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("widget_rss")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.RssFeed, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(16.dp))
                    Text("Live Scientific & Tech Feed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Text("RSS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF97316))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                headlines.forEach { item ->
                    Surface(
                        onClick = { onNavigate(item.url) },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.source} • ${item.time}",
                                    fontSize = 9.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 10. SAFARI QUICK TOOLS WIDGET COMPONENT
// -------------------------------------------------------------
@Composable
fun QuickToolsCanvasWidget(
    widget: CanvasObject.WidgetObject,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101524).copy(alpha = 0.75f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.2f)),
        modifier = modifier
            .fillMaxSize()
            .testTag("widget_quick_tools")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Safari Quick Tools", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickToolItem(Icons.Rounded.Bookmarks, "Bookmarks", Color(0xFF38BDF8)) {
                    onNavigate("https://duckduckgo.com")
                }
                QuickToolItem(Icons.Rounded.History, "History", Color(0xFFA855F7)) {
                    onNavigate("https://news.ycombinator.com")
                }
                QuickToolItem(Icons.Rounded.Download, "Downloads", Color(0xFF10B981)) {
                    onNavigate("https://github.com")
                }
                QuickToolItem(Icons.Rounded.VpnKey, "Onion Tor", Color(0xFFEC4899)) {
                    onNavigate("https://duckduckgo.com")
                }
            }
        }
    }
}

@Composable
private fun QuickToolItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.15f))
                .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 9.sp, color = Color(0xFF94A3B8))
    }
}
