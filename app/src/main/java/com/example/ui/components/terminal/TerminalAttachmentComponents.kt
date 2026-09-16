package com.example.ui.components.terminal

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.terminal.AttachmentType
import com.example.data.terminal.TerminalAttachment

private val TermSurfaceColor = Color(0xFF0D1117)
private val TermBorderColor = Color(0xFF1E2636)
private val TermPromptCyan = Color(0xFF38BDF8)
private val TermPromptGreen = Color(0xFF4ADE80)
private val TermPromptAmber = Color(0xFFF59E0B)
private val TermPromptPurple = Color(0xFFA855F7)
private val TermTextPrimary = Color(0xFFE6EDF3)
private val TermTextSecondary = Color(0xFF8B949E)

/**
 * Returns the appropriate icon vector for a given attachment type.
 */
fun getIconForAttachmentType(type: AttachmentType): ImageVector {
    return when (type) {
        AttachmentType.PHOTO -> Icons.Rounded.Image
        AttachmentType.DOCUMENT -> Icons.Rounded.Description
        AttachmentType.WEB_PAGE -> Icons.Rounded.Language
        AttachmentType.CODE_SNIPPET -> Icons.Rounded.Code
        AttachmentType.AUDIO -> Icons.Rounded.Mic
        AttachmentType.OTHER -> Icons.Rounded.AttachFile
    }
}

/**
 * Returns the primary accent color for a given attachment type.
 */
fun getColorForAttachmentType(type: AttachmentType): Color {
    return when (type) {
        AttachmentType.PHOTO -> Color(0xFFEC4899)      // Vibrant Pink / Rose
        AttachmentType.DOCUMENT -> Color(0xFF38BDF8)   // Sky Blue
        AttachmentType.WEB_PAGE -> Color(0xFFF59E0B)   // Amber / Gold
        AttachmentType.CODE_SNIPPET -> Color(0xFF10B981)// Emerald Green
        AttachmentType.AUDIO -> Color(0xFFA855F7)      // Purple
        AttachmentType.OTHER -> Color(0xFF94A3B8)      // Slate Gray
    }
}

/**
 * Horizontal suggestion chips bar for attachments in the terminal.
 * Shows each attached item as an interactive chip with select/deselect status,
 * an "Attachment Panel" launch chip, and an "+ Attach" button.
 */
@Composable
fun TerminalAttachmentChipsRow(
    attachments: List<TerminalAttachment>,
    onToggleSelection: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onOpenPanel: () -> Unit,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (attachments.isEmpty()) return

    val selectedCount = attachments.count { it.isSelectedForSending }
    val totalCount = attachments.size

    Surface(
        color = Color(0xFF0A0E17),
        border = BorderStroke(0.5.dp, TermBorderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Attachment Panel Trigger Chip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selectedCount > 0) Color(0xFF1E293B) else Color(0xFF151922),
                border = BorderStroke(
                    1.dp,
                    if (selectedCount > 0) TermPromptCyan.copy(alpha = 0.5f) else Color(0xFF2D3748)
                ),
                modifier = Modifier
                    .clickable { onOpenPanel() }
                    .testTag("terminal_attachment_panel_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = "Open Attachment Panel",
                        tint = if (selectedCount > 0) TermPromptCyan else TermTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Panel ($selectedCount/$totalCount)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedCount > 0) TermPromptCyan else TermTextSecondary
                    )
                }
            }

            // Quick "+ Add" Chip
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF161B22),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                modifier = Modifier
                    .clickable { onAddMore() }
                    .testTag("terminal_attachment_add_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Add attachment",
                        tint = TermPromptGreen,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Add",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TermTextPrimary
                    )
                }
            }

            // Individual Attachment Chips with Select/Deselect Toggle
            attachments.forEach { item ->
                val isSelected = item.isSelectedForSending
                val accentColor = getColorForAttachmentType(item.type)
                val icon = getIconForAttachmentType(item.type)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF111C2B) else Color(0xFF131720),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) accentColor.copy(alpha = 0.7f) else Color(0xFF263040)
                    ),
                    modifier = Modifier
                        .clickable { onToggleSelection(item.id) }
                        .testTag("attachment_chip_${item.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Selection state indicator (Checkmark vs Open Circle)
                        Icon(
                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                            contentDescription = if (isSelected) "Selected for sending" else "Deselected",
                            tint = if (isSelected) accentColor else Color(0xFF64748B),
                            modifier = Modifier
                                .size(13.dp)
                                .testTag("attachment_toggle_${item.id}")
                        )

                        // Type icon
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) accentColor else Color(0xFF64748B),
                            modifier = Modifier.size(13.dp)
                        )

                        // Name label
                        Text(
                            text = item.name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) TermTextPrimary else TermTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 110.dp)
                        )

                        // Remove '✕' button
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { onRemoveAttachment(item.id) }
                                .testTag("attachment_remove_${item.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove attachment",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Comprehensive Attachment Panel Sheet / Dialog.
 * Allows viewing all attached items, selecting or deselecting them for sending
 * to AI or Agent, previewing details, selecting all, deselecting all, and adding more.
 */
@Composable
fun TerminalAttachmentPanelDialog(
    attachments: List<TerminalAttachment>,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onClearAll: () -> Unit,
    onAddPhoto: () -> Unit,
    onAddDocument: () -> Unit,
    onAddWebTab: () -> Unit,
    onAddSnippet: () -> Unit,
    onAddSample: () -> Unit,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(vertical = 12.dp)
                .testTag("attachment_panel"),
            shape = RoundedCornerShape(20.dp),
            color = TermSurfaceColor,
            border = BorderStroke(1.dp, TermBorderColor),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Panel Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0x2238BDF8))
                                .border(1.dp, Color(0x5538BDF8), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                tint = TermPromptCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "TERMINAL ATTACHMENTS",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = TermPromptCyan
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0x334ADE80))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    val sel = attachments.count { it.isSelectedForSending }
                                    Text(
                                        text = "$sel/${attachments.size} FOR SENDING",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TermPromptGreen
                                    )
                                }
                            }
                            Text(
                                text = "Select or deselect items to include for AI/Agent",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = TermTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x1AFFFFFF))
                            .testTag("attachment_panel_close")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close panel",
                            tint = TermTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bulk Selection Actions Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161B22),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier
                            .clickable { onSelectAll() }
                            .testTag("attachment_panel_select_all")
                    ) {
                        Text(
                            text = "Select All",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TermPromptCyan,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF161B22),
                        border = BorderStroke(1.dp, Color(0xFF30363D)),
                        modifier = Modifier
                            .clickable { onDeselectAll() }
                            .testTag("attachment_panel_deselect_all")
                    ) {
                        Text(
                            text = "Deselect All",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TermTextSecondary,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (attachments.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0x22F85149),
                            border = BorderStroke(1.dp, Color(0x44F85149)),
                            modifier = Modifier
                                .clickable { onClearAll() }
                                .testTag("attachment_panel_clear_all")
                        ) {
                            Text(
                                text = "Clear All",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFF85149),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = TermBorderColor, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Item List
                if (attachments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Color(0xFF090D14), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
                                contentDescription = null,
                                tint = Color(0xFF4B5563),
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "No items attached yet",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = "Use the buttons below to attach photos, files, or web tabs",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.5.sp,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(attachments, key = { it.id }) { item ->
                            val isSelected = item.isSelectedForSending
                            val accentColor = getColorForAttachmentType(item.type)
                            val icon = getIconForAttachmentType(item.type)

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF131C28) else Color(0xFF0E131C),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) accentColor.copy(alpha = 0.6f) else Color(0xFF1E2636)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleSelection(item.id) }
                                    .testTag("attachment_panel_item_${item.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Checkbox for Select/Deselect
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { onToggleSelection(item.id) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = accentColor,
                                            uncheckedColor = Color(0xFF4B5563),
                                            checkmarkColor = Color.Black
                                        ),
                                        modifier = Modifier.size(24.dp)
                                    )

                                    // Type Icon Badge
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accentColor.copy(alpha = 0.15f))
                                            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = accentColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    // Details Column
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = item.name,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) TermTextPrimary else Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = item.type.name.lowercase().replaceFirstChar { it.uppercase() },
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = accentColor
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 10.sp,
                                                color = Color(0xFF4B5563)
                                            )
                                            Text(
                                                text = item.formattedSize(),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = TermTextSecondary
                                            )
                                            if (isSelected) {
                                                Text(
                                                    text = "[WILL SEND]",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TermPromptGreen
                                                )
                                            } else {
                                                Text(
                                                    text = "[DESELECTED]",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = Color(0xFF6B7280)
                                                )
                                            }
                                        }

                                        if (item.contentSummary.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = item.contentSummary,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = Color(0xFF8B949E),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    // Remove button
                                    IconButton(
                                        onClick = { onRemoveAttachment(item.id) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = TermBorderColor, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Attachment Source Buttons
                Text(
                    text = "ADD ATTACHMENT SOURCE",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B7280)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SourceButton(
                        icon = Icons.Rounded.Image,
                        label = "Photo",
                        accentColor = Color(0xFFEC4899),
                        onClick = onAddPhoto,
                        tag = "attach_source_photo"
                    )
                    SourceButton(
                        icon = Icons.Rounded.Description,
                        label = "Document",
                        accentColor = Color(0xFF38BDF8),
                        onClick = onAddDocument,
                        tag = "attach_source_doc"
                    )
                    SourceButton(
                        icon = Icons.Rounded.Language,
                        label = "Web Tab",
                        accentColor = Color(0xFFF59E0B),
                        onClick = onAddWebTab,
                        tag = "attach_source_web"
                    )
                    SourceButton(
                        icon = Icons.Rounded.Code,
                        label = "Snippet",
                        accentColor = Color(0xFF10B981),
                        onClick = onAddSnippet,
                        tag = "attach_source_snippet"
                    )
                    SourceButton(
                        icon = Icons.Rounded.FlashOn,
                        label = "Quick Sample",
                        accentColor = TermPromptCyan,
                        onClick = onAddSample,
                        tag = "attach_source_sample"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Done Button
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("attachment_panel_done_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF238636)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Done (${attachments.count { it.isSelectedForSending }} selected)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceButton(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF161B22),
        border = BorderStroke(1.dp, Color(0xFF30363D)),
        modifier = Modifier
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TermTextPrimary
            )
        }
    }
}

/**
 * Dialog to input a code/text snippet to attach.
 */
@Composable
fun SnippetInputDialog(
    onConfirm: (title: String, content: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = TermSurfaceColor,
            border = BorderStroke(1.dp, TermBorderColor)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "ATTACH CODE OR TEXT SNIPPET",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TermPromptCyan
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Snippet Title / Filename") },
                    placeholder = { Text("e.g. prompt.txt, query.sql, logic.kt") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Snippet Content") },
                    placeholder = { Text("Paste code or text notes here...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TermTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalTitle = title.ifBlank { "snippet_${System.currentTimeMillis() % 1000}.txt" }
                            if (content.isNotBlank()) {
                                onConfirm(finalTitle, content)
                            }
                        },
                        enabled = content.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TermPromptCyan)
                    ) {
                        Text("Attach Snippet", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Bottom Sheet / Dialog to pick attachment type for Terminal:
 * Photos, Documents, Web Tab, Code Snippets, or Quick Samples.
 */
@Composable
fun TerminalAttachmentPickerMenu(
    hasExistingAttachments: Boolean,
    onPickPhoto: () -> Unit,
    onPickDocument: () -> Unit,
    onAttachWebPage: () -> Unit,
    onAttachSnippet: () -> Unit,
    onAddSample: () -> Unit,
    onOpenPanel: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("terminal_attachment_picker_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F141C),
            border = BorderStroke(1.dp, TermBorderColor),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = TermPromptCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.AttachFile,
                                    contentDescription = null,
                                    tint = TermPromptCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "ATTACH TO TERMINAL",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TermTextPrimary
                            )
                            Text(
                                text = "Select photos & context for AI or Agent",
                                fontSize = 11.sp,
                                color = TermTextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TermTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = TermBorderColor, thickness = 0.5.dp)

                // Attachment Options Grid / List
                AttachmentOptionRow(
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "Photo / Gallery",
                    subtitle = "Pick images from camera or gallery",
                    accentColor = Color(0xFFEC4899),
                    tag = "picker_option_photo",
                    onClick = {
                        onDismiss()
                        onPickPhoto()
                    }
                )

                AttachmentOptionRow(
                    icon = Icons.Rounded.Description,
                    title = "Document / File",
                    subtitle = "Attach PDF, text, JSON, or code files",
                    accentColor = Color(0xFF38BDF8),
                    tag = "picker_option_doc",
                    onClick = {
                        onDismiss()
                        onPickDocument()
                    }
                )

                AttachmentOptionRow(
                    icon = Icons.Rounded.Language,
                    title = "Active Web Tab",
                    subtitle = "Attach current browser page URL & title",
                    accentColor = Color(0xFFF59E0B),
                    tag = "picker_option_web",
                    onClick = {
                        onDismiss()
                        onAttachWebPage()
                    }
                )

                AttachmentOptionRow(
                    icon = Icons.Rounded.Code,
                    title = "Code / Text Snippet",
                    subtitle = "Paste or type a snippet directly",
                    accentColor = Color(0xFF10B981),
                    tag = "picker_option_snippet",
                    onClick = {
                        onDismiss()
                        onAttachSnippet()
                    }
                )

                AttachmentOptionRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = "Quick Sample Photo & Doc",
                    subtitle = "Instantly load demo image and Markdown file",
                    accentColor = Color(0xFFA855F7),
                    tag = "picker_option_sample",
                    onClick = {
                        onDismiss()
                        onAddSample()
                    }
                )

                if (hasExistingAttachments) {
                    HorizontalDivider(color = TermBorderColor, thickness = 0.5.dp)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenPanel()
                            }
                            .testTag("picker_open_panel_button"),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF161E2E),
                        border = BorderStroke(1.dp, TermPromptCyan.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = TermPromptCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Open Full Attachment Management Panel",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TermPromptCyan
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF141923),
        border = BorderStroke(0.8.dp, Color(0xFF212836))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TermTextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = TermTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TermTextSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
