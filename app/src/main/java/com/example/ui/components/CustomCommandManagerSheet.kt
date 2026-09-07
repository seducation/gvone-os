package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.command.CommandEngine
import com.example.data.model.*
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomCommandManagerSheet(
    commands: List<CustomCommandEntity>,
    onSaveCommand: (CustomCommandEntity) -> Unit,
    onDeleteCommand: (String) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onInstallPack: (CommandEngine.CommandPack) -> Unit,
    onImportCommands: (String) -> Unit,
    onTestExecuteCommand: (String) -> Unit,
    onGenerateWithAI: (String, (CustomCommandEntity) -> Unit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String>("ALL") }

    // Dialog States
    var editingCommand by remember { mutableStateOf<CustomCommandEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showPacksDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }
    var showAIDialog by remember { mutableStateOf(false) }
    var testingCommand by remember { mutableStateOf<CustomCommandEntity?>(null) }

    val filteredCommands = remember(commands, searchQuery, selectedCategoryFilter) {
        commands.filter { cmd ->
            val matchesCategory = when (selectedCategoryFilter) {
                "ALL" -> true
                "PINNED" -> cmd.isPinned
                "SEARCH" -> cmd.type == CommandType.SEARCH
                "AI" -> cmd.type == CommandType.AI
                "PAGE" -> cmd.type == CommandType.PAGE_ACTION
                "BROWSER" -> cmd.type == CommandType.BROWSER_ACTION
                "AUTOMATION" -> cmd.type == CommandType.AUTOMATION
                "CUSTOM" -> !cmd.isBuiltIn
                else -> true
            }

            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                cmd.command.lowercase().contains(q) ||
                        cmd.name.lowercase().contains(q) ||
                        cmd.description.lowercase().contains(q) ||
                        cmd.aliasesRaw.lowercase().contains(q)
            }

            matchesCategory && matchesSearch
        }.sortedWith(
            compareByDescending<CustomCommandEntity> { it.isPinned }
                .thenBy { it.category.ordinal }
                .thenBy { it.command }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0C1017),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color(0xFF334155),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        },
        modifier = modifier
            .fillMaxHeight(0.92f)
            .testTag("custom_command_manager_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GVONEPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GVONEPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = GVONEPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Command Manager",
                            color = Color(0xFFF1F5F9),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Terminal Address Bar Shortcuts & Workflows",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("close_command_manager_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            // Action Buttons Row: [+ New], [AI Assistant], [Packs], [Export/Import]
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Button(
                        onClick = { isCreatingNew = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GVONEPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("create_new_command_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Command", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                item {
                    FilledTonalButton(
                        onClick = { showAIDialog = true },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = GVONESecondary
                        ),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("ai_command_gen_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = GVONESecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("AI Create", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { showPacksDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE2E8F0)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("command_packs_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = GVONETertiary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Packs", fontSize = 13.sp)
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { showImportExportDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFE2E8F0)
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("import_export_commands_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Import/Export", fontSize = 13.sp)
                    }
                }
            }

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter commands, aliases, keywords...", color = Color(0xFF64748B), fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("command_search_filter_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF131A26),
                    unfocusedContainerColor = Color(0xFF131A26),
                    focusedBorderColor = GVONEPrimary,
                    unfocusedBorderColor = Color(0xFF243042),
                    focusedTextColor = Color(0xFFF1F5F9),
                    unfocusedTextColor = Color(0xFFF1F5F9)
                ),
                singleLine = true
            )

            // Category Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val filters = listOf(
                    "ALL" to "All (${commands.size})",
                    "PINNED" to "Starred",
                    "SEARCH" to "Search",
                    "AI" to "GVONE AI",
                    "PAGE" to "Page",
                    "BROWSER" to "Browser",
                    "AUTOMATION" to "Automation",
                    "CUSTOM" to "Custom"
                )

                items(filters) { (key, label) ->
                    val isSelected = selectedCategoryFilter == key
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = key },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GVONEPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = GVONEPrimary,
                            containerColor = Color(0xFF131B26),
                            labelColor = Color(0xFF94A3B8)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) GVONEPrimary.copy(alpha = 0.6f) else Color(0xFF222F3E)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            // Commands List
            if (filteredCommands.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching commands found", color = Color(0xFF64748B), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { isCreatingNew = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary, contentColor = Color.Black),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Create '${searchQuery.trim().ifEmpty { "/newcmd" }}'")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("custom_commands_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredCommands, key = { it.id }) { cmd ->
                        CommandCard(
                            command = cmd,
                            onToggleEnabled = { enabled -> onToggleEnabled(cmd.id, enabled) },
                            onTogglePinned = { pinned -> onTogglePinned(cmd.id, pinned) },
                            onTest = { testingCommand = cmd },
                            onEdit = { editingCommand = cmd },
                            onDuplicate = {
                                val duplicate = cmd.copy(
                                    id = "cmd_${UUID.randomUUID().toString().take(8)}",
                                    command = "${cmd.command}_copy",
                                    name = "${cmd.name} (Copy)",
                                    isBuiltIn = false,
                                    createdAt = System.currentTimeMillis()
                                )
                                onSaveCommand(duplicate)
                                Toast.makeText(context, "Duplicated to ${duplicate.command}", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = {
                                onDeleteCommand(cmd.id)
                                Toast.makeText(context, "Deleted ${cmd.command}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // Create / Edit Dialog
    if (isCreatingNew || editingCommand != null) {
        val targetCommand = editingCommand
        CommandBuilderDialog(
            initialCommand = targetCommand,
            existingCommands = commands,
            onSave = { saved ->
                onSaveCommand(saved)
                isCreatingNew = false
                editingCommand = null
                Toast.makeText(context, "Saved ${saved.command}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                isCreatingNew = false
                editingCommand = null
            }
        )
    }

    // Test Command Dialog
    testingCommand?.let { cmd ->
        TestCommandDialog(
            command = cmd,
            onRun = { fullInput ->
                testingCommand = null
                onClose()
                onTestExecuteCommand(fullInput)
            },
            onDismiss = { testingCommand = null }
        )
    }

    // AI Generator Dialog
    if (showAIDialog) {
        AICommandGeneratorDialog(
            onGenerate = { prompt, callback ->
                onGenerateWithAI(prompt, callback)
            },
            onSave = { generated ->
                onSaveCommand(generated)
                showAIDialog = false
                Toast.makeText(context, "Created ${generated.command} with AI", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showAIDialog = false }
        )
    }

    // Pre-made Command Packs Dialog
    if (showPacksDialog) {
        CommandPacksDialog(
            packs = CommandEngine.PRESET_COMMAND_PACKS,
            onInstall = { pack ->
                onInstallPack(pack)
                showPacksDialog = false
                Toast.makeText(context, "Installed ${pack.name}", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showPacksDialog = false }
        )
    }

    // Import / Export Dialog
    if (showImportExportDialog) {
        ImportExportDialog(
            commands = commands,
            onImport = { json ->
                onImportCommands(json)
                showImportExportDialog = false
                Toast.makeText(context, "Imported commands successfully", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showImportExportDialog = false }
        )
    }
}

@Composable
fun CommandCard(
    command: CustomCommandEntity,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePinned: (Boolean) -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = if (command.isPinned) GVONEPrimary.copy(alpha = 0.5f) else Color(0xFF1E293B),
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("command_card_${command.command.removePrefix("/")}"),
        colors = CardDefaults.cardColors(
            containerColor = if (command.isEnabled) Color(0xFF111722) else Color(0xFF0D121A)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row: Trigger badge, Aliases, Type badge, Star, Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Command Trigger Badge
                    Surface(
                        color = if (command.isEnabled) GVONEPrimary.copy(alpha = 0.15f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (command.isEnabled) GVONEPrimary.copy(alpha = 0.5f) else Color(0xFF334155)
                        )
                    ) {
                        Text(
                            text = command.command,
                            color = if (command.isEnabled) GVONEPrimary else Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Type Badge
                    val (typeColor, typeLabel) = when (command.type) {
                        CommandType.SEARCH -> GVONEPrimary to "Search"
                        CommandType.AI -> GVONESecondary to "GVONE AI"
                        CommandType.URL -> Color(0xFF38BDF8) to "URL"
                        CommandType.PAGE_ACTION -> Color(0xFFA855F7) to "Page"
                        CommandType.BROWSER_ACTION -> Color(0xFFF59E0B) to "Browser"
                        CommandType.AUTOMATION -> Color(0xFF10B981) to "JS Auto"
                    }

                    Surface(
                        color = typeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            color = typeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (command.isBuiltIn) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFF334155).copy(alpha = 0.4f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Built-in",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Controls: Pin / Favorite + Enable Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onTogglePinned(!command.isPinned) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (command.isPinned) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Pin Command",
                            tint = if (command.isPinned) Color(0xFFFBBF24) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Switch(
                        checked = command.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = GVONEPrimary,
                            uncheckedThumbColor = Color(0xFF64748B),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Name & Description
            Text(
                text = command.name,
                color = if (command.isEnabled) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            if (command.description.isNotBlank()) {
                Text(
                    text = command.description,
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Aliases Preview
            val aliases = command.getAliasesList()
            if (aliases.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Aliases: ", color = Color(0xFF64748B), fontSize = 11.sp)
                    aliases.forEach { alias ->
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = alias,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Template preview in monospace snippet box
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = Color(0xFF090D13),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1A2333)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = command.template.replace("\n", " ").take(100) + if (command.template.length > 100) "..." else "",
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }

            // Action Buttons Row: [Test], [Edit], [Duplicate], [Delete]
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Test button
                OutlinedButton(
                    onClick = onTest,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GVONEPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GVONEPrimary.copy(alpha = 0.4f)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Edit button
                FilledTonalButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0xFF1E293B),
                        contentColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Duplicate button
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (!command.isBuiltIn) {
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandBuilderDialog(
    initialCommand: CustomCommandEntity?,
    existingCommands: List<CustomCommandEntity>,
    onSave: (CustomCommandEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var commandTrigger by remember { mutableStateOf(initialCommand?.command ?: "/") }
    var name by remember { mutableStateOf(initialCommand?.name ?: "") }
    var description by remember { mutableStateOf(initialCommand?.description ?: "") }
    var commandType by remember { mutableStateOf(initialCommand?.type ?: CommandType.SEARCH) }
    var template by remember {
        mutableStateOf(
            initialCommand?.template
                ?: "https://www.youtube.com/results?search_query={query}"
        )
    }
    var aliasesInput by remember { mutableStateOf(initialCommand?.aliasesRaw ?: "") }
    var targetProvider by remember { mutableStateOf(initialCommand?.targetProvider ?: "") }
    var isPinned by remember { mutableStateOf(initialCommand?.isPinned ?: false) }
    var isAdvancedMode by remember { mutableStateOf(false) }

    // Conflict detection live check
    val tempDraft = CustomCommandEntity(
        id = initialCommand?.id ?: "draft",
        command = if (commandTrigger.startsWith("/")) commandTrigger else "/$commandTrigger",
        name = name,
        description = description,
        type = commandType,
        template = template,
        aliasesRaw = aliasesInput,
        isBuiltIn = initialCommand?.isBuiltIn ?: false
    )
    val conflicts = remember(tempDraft.command, aliasesInput) {
        CommandEngine.checkConflicts(tempDraft, existingCommands)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .testTag("command_builder_dialog"),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1521))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (initialCommand == null) "Create Custom Command" else "Edit Command",
                            color = Color(0xFFF1F5F9),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // Conflict warning banner
                if (conflicts.isNotEmpty()) {
                    item {
                        Surface(
                            color = Color(0xFF7C2D12).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFF97316),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = conflicts.first(),
                                    color = Color(0xFFFDBA74),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Command Trigger field
                item {
                    OutlinedTextField(
                        value = commandTrigger,
                        onValueChange = {
                            val clean = if (!it.startsWith("/")) "/$it" else it
                            commandTrigger = clean
                        },
                        label = { Text("Command Trigger (e.g. /yt, /wiki, /g)") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Terminal, null, tint = GVONEPrimary)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("builder_trigger_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF243042),
                            focusedContainerColor = Color(0xFF161E2E),
                            unfocusedContainerColor = Color(0xFF161E2E)
                        )
                    )
                }

                // Name & Description
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (e.g. YouTube Search)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("builder_name_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF243042),
                            focusedContainerColor = Color(0xFF161E2E),
                            unfocusedContainerColor = Color(0xFF161E2E)
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF243042),
                            focusedContainerColor = Color(0xFF161E2E),
                            unfocusedContainerColor = Color(0xFF161E2E)
                        )
                    )
                }

                // Command Type Dropdown / Segmented Row
                item {
                    Text("Command Type", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(CommandType.entries) { type ->
                            val isSelected = commandType == type
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    commandType = type
                                    // Update template default if needed
                                    if (template.isBlank() || template.startsWith("http")) {
                                        template = when (type) {
                                            CommandType.SEARCH -> "https://www.google.com/search?q={query}"
                                            CommandType.URL -> "https://example.com/{query}"
                                            CommandType.AI -> "Explain this in MBBS-level clarity: {query}"
                                            CommandType.BROWSER_ACTION -> "new_tab"
                                            CommandType.PAGE_ACTION -> "summarize_page"
                                            CommandType.AUTOMATION -> "window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'});"
                                        }
                                    }
                                },
                                label = { Text(type.displayName, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GVONEPrimary,
                                    selectedLabelColor = Color.Black,
                                    containerColor = Color(0xFF161E2E),
                                    labelColor = Color(0xFF94A3B8)
                                )
                            )
                        }
                    }
                }

                // Target Provider (if Search)
                if (commandType == CommandType.SEARCH) {
                    item {
                        OutlinedTextField(
                            value = targetProvider,
                            onValueChange = { targetProvider = it },
                            label = { Text("Provider Name (e.g. YouTube, Wikipedia, Google)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GVONEPrimary,
                                unfocusedBorderColor = Color(0xFF243042),
                                focusedContainerColor = Color(0xFF161E2E),
                                unfocusedContainerColor = Color(0xFF161E2E)
                            )
                        )
                    }
                }

                // Template / Action field with Variable Chips
                item {
                    Text(
                        text = when (commandType) {
                            CommandType.URL -> "URL Template (use {query}, {url}, {domain})"
                            CommandType.SEARCH -> "Search URL Template ({query} required)"
                            CommandType.AI -> "AI Prompt Template (use {query}, {url}, {title})"
                            CommandType.BROWSER_ACTION -> "Browser Action Identifier"
                            CommandType.PAGE_ACTION -> "Page Action (summarize_page, translate_page, extract_info, reader_mode)"
                            CommandType.AUTOMATION -> "JavaScript Code (Safe DOM & Navigation only)"
                        },
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = template,
                        onValueChange = { template = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("builder_template_input"),
                        minLines = if (commandType == CommandType.AUTOMATION || commandType == CommandType.AI) 3 else 1,
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF243042),
                            focusedContainerColor = Color(0xFF161E2E),
                            unfocusedContainerColor = Color(0xFF161E2E)
                        )
                    )
                }

                // Variable insertion chips
                item {
                    Text("Insert Variable:", color = Color(0xFF64748B), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val variables = listOf("{query}", "{url}", "{title}", "{domain}", "{selection}", "{clipboard}")
                        items(variables) { variable ->
                            SuggestionChip(
                                onClick = { template += variable },
                                label = { Text(variable, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = Color(0xFF161E2E),
                                    labelColor = GVONEPrimary
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = Color(0xFF243042)
                                )
                            )
                        }
                    }
                }

                // Aliases
                item {
                    OutlinedTextField(
                        value = aliasesInput,
                        onValueChange = { aliasesInput = it },
                        label = { Text("Aliases (comma-separated, e.g. /youtube, /you)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF243042),
                            focusedContainerColor = Color(0xFF161E2E),
                            unfocusedContainerColor = Color(0xFF161E2E)
                        )
                    )
                }

                // Pin toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Star / Pin to top", color = Color(0xFFF1F5F9), fontSize = 13.sp)
                        Switch(
                            checked = isPinned,
                            onCheckedChange = { isPinned = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = GVONEPrimary
                            )
                        )
                    }
                }

                // Save & Cancel Buttons
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = Color(0xFF94A3B8))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val cleanCommand = if (commandTrigger.startsWith("/")) commandTrigger else "/$commandTrigger"
                                val finalName = name.ifBlank { cleanCommand }
                                val result = CustomCommandEntity(
                                    id = initialCommand?.id ?: "cmd_${cleanCommand.removePrefix("/").lowercase()}",
                                    command = cleanCommand,
                                    name = finalName,
                                    description = description,
                                    type = commandType,
                                    template = template.trim(),
                                    aliasesRaw = aliasesInput.trim(),
                                    category = when (commandType) {
                                        CommandType.SEARCH -> CommandCategory.SEARCH
                                        CommandType.AI -> CommandCategory.GVONE
                                        CommandType.BROWSER_ACTION -> CommandCategory.BROWSER
                                        CommandType.PAGE_ACTION -> CommandCategory.PAGE
                                        CommandType.AUTOMATION -> CommandCategory.AUTOMATION
                                        CommandType.URL -> CommandCategory.CUSTOM
                                    },
                                    targetProvider = targetProvider.takeIf { it.isNotBlank() },
                                    isEnabled = true,
                                    isPinned = isPinned,
                                    isBuiltIn = initialCommand?.isBuiltIn ?: false,
                                    createdAt = initialCommand?.createdAt ?: System.currentTimeMillis()
                                )
                                onSave(result)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GVONEPrimary,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("builder_save_btn")
                        ) {
                            Text("Save Command", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TestCommandDialog(
    command: CustomCommandEntity,
    onRun: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var queryArg by remember { mutableStateOf("test query") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111722))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Test Command: ${command.command}",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter sample arguments or query to test execution",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = queryArg,
                    onValueChange = { queryArg = it },
                    label = { Text("Query argument") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GVONEPrimary,
                        unfocusedBorderColor = Color(0xFF243042),
                        focusedContainerColor = Color(0xFF161E2E),
                        unfocusedContainerColor = Color(0xFF161E2E)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                val preview = "${command.command} ${queryArg.trim()}"
                Surface(
                    color = Color(0xFF090D13),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Full input: $preview",
                        color = GVONEPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onRun(preview) },
                        colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary, contentColor = Color.Black)
                    ) {
                        Text("Execute Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun AICommandGeneratorDialog(
    onGenerate: (String, (CustomCommandEntity) -> Unit) -> Unit,
    onSave: (CustomCommandEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var nlPrompt by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedCommand by remember { mutableStateOf<CustomCommandEntity?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111722))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = GVONESecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Natural Language Command Creator",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Describe what command you want in plain English. GVONE AI will synthesize the template and aliases automatically.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                OutlinedTextField(
                    value = nlPrompt,
                    onValueChange = { nlPrompt = it },
                    placeholder = { Text("e.g. Create a command /wiki that searches Wikipedia for articles", color = Color(0xFF64748B), fontSize = 13.sp) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GVONESecondary,
                        unfocusedBorderColor = Color(0xFF243042),
                        focusedContainerColor = Color(0xFF161E2E),
                        unfocusedContainerColor = Color(0xFF161E2E)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick suggestions chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val suggestions = listOf(
                        "Create /wiki for Wikipedia",
                        "Create /reddit for Reddit posts",
                        "Create /gh for GitHub code",
                        "Create /med for PubMed papers"
                    )
                    items(suggestions) { text ->
                        SuggestionChip(
                            onClick = { nlPrompt = text },
                            label = { Text(text, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isGenerating) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = GVONESecondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Synthesizing command with GVONE AI...", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }

                generatedCommand?.let { cmd ->
                    Surface(
                        color = Color(0xFF0C1017),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GVONESecondary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = cmd.command,
                                    color = GVONESecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cmd.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = cmd.description,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cmd.template,
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (generatedCommand == null) {
                        Button(
                            onClick = {
                                if (nlPrompt.isNotBlank()) {
                                    isGenerating = true
                                    onGenerate(nlPrompt) { result ->
                                        generatedCommand = result
                                        isGenerating = false
                                    }
                                }
                            },
                            enabled = nlPrompt.isNotBlank() && !isGenerating,
                            colors = ButtonDefaults.buttonColors(containerColor = GVONESecondary, contentColor = Color.Black)
                        ) {
                            Text("Generate")
                        }
                    } else {
                        Button(
                            onClick = {
                                generatedCommand?.let { onSave(it) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary, contentColor = Color.Black)
                        ) {
                            Text("Approve & Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommandPacksDialog(
    packs: List<CommandEngine.CommandPack>,
    onInstall: (CommandEngine.CommandPack) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111722))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preset Command Packs",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = Color(0xFF94A3B8))
                    }
                }

                Text(
                    text = "One-tap packs to instantly supercharge your address bar workflows:",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(packs) { pack ->
                        Surface(
                            color = Color(0xFF161E2E),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = pack.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = pack.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        pack.commands.forEach { cmd ->
                                            Surface(
                                                color = Color(0xFF0F1521),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = cmd.command,
                                                    color = GVONEPrimary,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { onInstall(pack) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = GVONEPrimary,
                                            contentColor = Color.Black
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Install", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun ImportExportDialog(
    commands: List<CustomCommandEntity>,
    onImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var isExportTab by remember { mutableStateOf(true) }
    var importText by remember { mutableStateOf("") }

    val exportedJson = remember(commands) {
        CommandEngine.exportCommandsToJson(commands)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111722))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Import & Export Commands",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = Color(0xFF94A3B8))
                    }
                }

                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isExportTab,
                        onClick = { isExportTab = true },
                        label = { Text("Export JSON (${commands.size})") }
                    )
                    FilterChip(
                        selected = !isExportTab,
                        onClick = { isExportTab = false },
                        label = { Text("Import JSON") }
                    )
                }

                if (isExportTab) {
                    Text(
                        text = "Copy the JSON representation to backup or share your command pack:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = exportedJson,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF94A3B8),
                            unfocusedTextColor = Color(0xFF94A3B8),
                            focusedContainerColor = Color(0xFF090D13),
                            unfocusedContainerColor = Color(0xFF090D13)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(exportedJson))
                                Toast.makeText(context, "JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy JSON")
                        }
                    }
                } else {
                    Text(
                        text = "Paste a valid command pack JSON array to import:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("Paste JSON here...", color = Color(0xFF64748B), fontSize = 11.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF243042),
                            focusedContainerColor = Color(0xFF090D13),
                            unfocusedContainerColor = Color(0xFF090D13)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                if (importText.isNotBlank()) {
                                    onImport(importText.trim())
                                }
                            },
                            enabled = importText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary, contentColor = Color.Black)
                        ) {
                            Text("Validate & Import")
                        }
                    }
                }
            }
        }
    }
}
