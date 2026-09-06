package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.TabGroup
import com.example.ui.theme.*

val GROUP_PALETTE_COLORS = listOf(
    "#3B82F6", // Blue
    "#10B981", // Emerald
    "#8B5CF6", // Purple
    "#F59E0B", // Amber
    "#EC4899", // Pink
    "#06B6D4"  // Cyan
)

fun parseHexColor(hex: String?, fallback: Color = Color(0xFF3B82F6)): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}

@Composable
fun TabFolderCard(
    group: TabGroup,
    tabsInGroup: List<BrowserTab>,
    isActiveGroup: Boolean,
    isDropTarget: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onAddTab: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = parseHexColor(group.colorHex, Color(0xFF3B82F6))
    val borderColor = when {
        isDropTarget -> GVONEPrimary
        isActiveGroup -> accentColor
        else -> Color(0xFF263042)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isDropTarget || isActiveGroup) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .testTag("tab_folder_card_${group.id}"),
        color = Color(0xFF141923),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Folder Icon + Title + Active Badge + Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = group.name,
                                color = GVONETextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isActiveGroup) {
                                Surface(
                                    color = accentColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, accentColor)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        color = accentColor,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${tabsInGroup.size} ${if (tabsInGroup.size == 1) "tab" else "tabs"}",
                            color = GVONETextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Options Menu button
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Folder Options",
                            tint = GVONETextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF1B2230))
                            .border(1.dp, Color(0xFF303A4E), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open Folder", color = GVONETextPrimary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = GVONEPrimary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Tab Here", color = GVONETextPrimary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = GVONESecondary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onAddTab()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = GVONETextPrimary, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Edit, contentDescription = null, tint = GVONETertiary, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onRename()
                            }
                        )
                        Divider(color = Color(0xFF263042), thickness = 0.5.dp)
                        DropdownMenuItem(
                            text = { Text("Delete Group", color = Color(0xFFEF4444), fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Preview Chips / Summary Row
            if (tabsInGroup.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B2230).copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isDropTarget) "Drop to add here" else "Empty folder — tap to open or add tabs",
                        color = if (isDropTarget) GVONEPrimary else GVONETextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewList = tabsInGroup.take(3)
                    previewList.forEach { tab ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1B2230),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF2C384D)),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (tab.isPrivate) Icons.Rounded.VpnLock else Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = tab.title.ifBlank { "Tab" },
                                    color = GVONETextPrimary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (tabsInGroup.size > 3) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1B2230),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF2C384D))
                        ) {
                            Text(
                                text = "+${tabsInGroup.size - 3}",
                                color = GVONETextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateGroupDialog(
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (name: String, colorHex: String) -> Unit
) {
    var groupName by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(GROUP_PALETTE_COLORS[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Tab Group",
                color = GVONETextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Organize related tabs together into a folder.",
                    color = GVONETextSecondary,
                    fontSize = 13.sp
                )

                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Group Name (e.g. Work, Study, Travel)", color = GVONETextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = GVONETextPrimary,
                        unfocusedTextColor = GVONETextPrimary,
                        focusedBorderColor = GVONEPrimary,
                        unfocusedBorderColor = Color(0xFF333E52)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Choose Color",
                    color = GVONETextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GROUP_PALETTE_COLORS.forEach { hex ->
                        val color = parseHexColor(hex)
                        val isSelected = selectedColor == hex

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isNotBlank()) {
                        onConfirm(groupName.trim(), selectedColor)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GVONETextSecondary)
            }
        },
        containerColor = Color(0xFF161F2E),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun RenameGroupDialog(
    group: TabGroup,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    var newName by remember { mutableStateOf(group.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rename Tab Group",
                color = GVONETextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("Group Name", color = GVONETextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GVONETextPrimary,
                    unfocusedTextColor = GVONETextPrimary,
                    focusedBorderColor = GVONEPrimary,
                    unfocusedBorderColor = Color(0xFF333E52)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newName.isNotBlank()) {
                        onConfirm(newName.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GVONETextSecondary)
            }
        },
        containerColor = Color(0xFF161F2E),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun DeleteGroupDialog(
    group: TabGroup,
    tabCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (closeTabs: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete \"${group.name}\"?",
                color = GVONETextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Text(
                text = if (tabCount > 0) {
                    "This group contains $tabCount tabs. You can either close them or keep them as ungrouped tabs."
                } else {
                    "Are you sure you want to delete this empty tab group?"
                },
                color = GVONETextSecondary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (tabCount > 0) {
                    Button(
                        onClick = { onConfirm(false) }, // Keep tabs
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263042)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Keep Tabs (Move to Ungrouped)", color = GVONETextPrimary)
                    }
                }

                Button(
                    onClick = { onConfirm(true) }, // Close tabs
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (tabCount > 0) "Delete Folder & Close Tabs" else "Delete Folder",
                        color = Color.White
                    )
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            }
        },
        containerColor = Color(0xFF161F2E),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
fun MoveToGroupDialog(
    tabGroups: List<TabGroup>,
    currentGroupId: String?,
    onDismiss: () -> Unit,
    onSelectGroup: (groupId: String?) -> Unit,
    onCreateNewGroup: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Move to Tab Group",
                color = GVONETextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Option: Ungrouped Tabs
                Surface(
                    onClick = { onSelectGroup(null) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (currentGroupId == null) GVONEPrimary.copy(alpha = 0.2f) else Color(0xFF1B2230),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (currentGroupId == null) GVONEPrimary else Color(0xFF2C384D)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            tint = GVONESecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "🌐 Ungrouped Tabs",
                            color = GVONETextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Existing Groups
                tabGroups.forEach { group ->
                    val isCurrent = currentGroupId == group.id
                    val accentColor = parseHexColor(group.colorHex, Color(0xFF3B82F6))

                    Surface(
                        onClick = { onSelectGroup(group.id) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) accentColor.copy(alpha = 0.2f) else Color(0xFF1B2230),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = if (isCurrent) accentColor else Color(0xFF2C384D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = group.name,
                                color = GVONETextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Create New Group option
                OutlinedButton(
                    onClick = onCreateNewGroup,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GVONEPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GVONEPrimary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Tab Group...", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = GVONETextSecondary)
            }
        },
        containerColor = Color(0xFF161F2E),
        shape = RoundedCornerShape(18.dp)
    )
}
