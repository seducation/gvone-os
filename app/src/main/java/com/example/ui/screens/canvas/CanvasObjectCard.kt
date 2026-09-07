package com.example.ui.screens.canvas

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CanvasObject
import com.example.data.model.CanvasWidgetType

@Composable
fun CanvasObjectCard(
    obj: CanvasObject,
    isEditMode: Boolean,
    isTorActive: Boolean,
    onNavigate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onUpdate: (CanvasObject) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onConfigure: (CanvasObject) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .testTag("canvas_object_${obj.id}")
            .then(
                if (isEditMode) {
                    Modifier
                        .border(
                            1.5.dp,
                            Color(0xFF38BDF8).copy(alpha = 0.65f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(2.dp)
                } else Modifier
            )
    ) {
        // Content rendering based on polymorphic object type
        when (obj) {
            is CanvasObject.LinkObject -> {
                LinkCanvasCard(
                    link = obj,
                    onNavigate = { if (!isEditMode) onNavigate(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is CanvasObject.FolderObject -> {
                FolderCanvasCard(
                    folder = obj,
                    onNavigate = { if (!isEditMode) onNavigate(it) },
                    modifier = Modifier.fillMaxSize()
                )
            }
            is CanvasObject.NoteObject -> {
                NoteCanvasCard(
                    note = obj,
                    onUpdateNote = onUpdate,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is CanvasObject.WidgetObject -> {
                when (obj.widgetType) {
                    CanvasWidgetType.CLOCK -> ClockCanvasWidget(obj, Modifier.fillMaxSize())
                    CanvasWidgetType.DATE_CALENDAR -> CalendarCanvasWidget(obj, Modifier.fillMaxSize())
                    CanvasWidgetType.WEATHER -> WeatherCanvasWidget(obj, Modifier.fillMaxSize())
                    CanvasWidgetType.SEARCH -> SearchCanvasWidget(obj, onNavigate = { if (!isEditMode) onNavigate(it) }, Modifier.fillMaxSize())
                    CanvasWidgetType.SYSTEM_INFO -> SystemInfoCanvasWidget(obj, isTorActive, Modifier.fillMaxSize())
                    CanvasWidgetType.RSS_FEED -> RssFeedCanvasWidget(obj, onNavigate = { if (!isEditMode) onNavigate(it) }, Modifier.fillMaxSize())
                    CanvasWidgetType.QUICK_TOOLS -> QuickToolsCanvasWidget(obj, onNavigate = { if (!isEditMode) onNavigate(it) }, Modifier.fillMaxSize())
                    CanvasWidgetType.NOTES_WIDGET -> {
                        NoteCanvasCard(
                            note = CanvasObject.NoteObject(
                                id = obj.id,
                                title = obj.config["title"] ?: "Sticky Note",
                                content = obj.config["content"] ?: "",
                                colorHex = obj.config["color"] ?: "#38BDF8"
                            ),
                            onUpdateNote = { updatedNote ->
                                onUpdate(
                                    obj.copy(
                                        config = obj.config + mapOf(
                                            "title" to updatedNote.title,
                                            "content" to updatedNote.content,
                                            "color" to updatedNote.colorHex
                                        )
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            is CanvasObject.WebPortionWidgetObject -> {
                WebPortionCanvasWidget(
                    widget = obj,
                    isEditMode = isEditMode,
                    onNavigate = onNavigate,
                    onUpdateWidget = onUpdate,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Customization Overlay Controls (Visible only in Edit Mode)
        if (isEditMode) {
            // Delete button (Top-Right red badge)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .clickable { onDelete(obj.id) }
                    .testTag("delete_object_${obj.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Edit / Configure button (Top-Left badge)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (-4).dp, y = (-6).dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFF38BDF8), CircleShape)
                    .clickable { onConfigure(obj) }
                    .testTag("config_object_${obj.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Configure",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Bottom controls: Move Up/Down + Resize pill
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Move order buttons
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                        .clickable { onMoveUp() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropUp,
                        contentDescription = "Move Up",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A).copy(alpha = 0.9f))
                        .clickable { onMoveDown() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = "Move Down",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Cycle size pill
                Surface(
                    onClick = {
                        val nextWidth = when (obj.width) {
                            1f -> 2f
                            2f -> 4f
                            else -> 1f
                        }
                        val updated = when (obj) {
                            is CanvasObject.LinkObject -> obj.copy(width = nextWidth)
                            is CanvasObject.FolderObject -> obj.copy(width = nextWidth)
                            is CanvasObject.NoteObject -> obj.copy(width = nextWidth)
                            is CanvasObject.WidgetObject -> obj.copy(width = nextWidth)
                            is CanvasObject.WebPortionWidgetObject -> obj.copy(width = nextWidth)
                        }
                        onUpdate(updated)
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.9f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AspectRatio,
                            contentDescription = "Resize",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${obj.width.toInt()}x",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
