package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
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
import com.example.data.command.CommandEngine
import com.example.data.files.FileType
import com.example.data.files.GVONEFileItem
import com.example.data.model.*
import com.example.data.research.ResearchSource
import com.example.ui.components.suggestions.DefaultQuickPrompts
import com.example.ui.components.suggestions.QuickPrompt
import com.example.ui.theme.*

enum class CommandPopupTab(val label: String, val testTag: String) {
    ALL("All", "tab_all"),
    ATTACHMENTS("Attachments", "tab_attachments"),
    SUGGESTIONS("Suggestions", "tab_suggestions"),
    PIN_ATTACHMENTS("Pin Tabs", "tab_pin_attachments");

    companion object {
        val TERMINAL = ATTACHMENTS
    }
}

data class PinAttachmentOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val badge: String,
    val isPinned: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun CommandAutocompletePopup(
    suggestions: List<CommandSuggestion>,
    onSelectSuggestion: (CommandSuggestion, Boolean) -> Unit, // Boolean: executeImmediately
    onOpenCommandManager: () -> Unit,
    modifier: Modifier = Modifier,
    prompts: List<QuickPrompt> = DefaultQuickPrompts.items,
    onSelectPrompt: ((String) -> Unit)? = null,
    isTerminalPinned: Boolean = false,
    onTogglePinTerminal: (() -> Unit)? = null,
    onAttachPhotos: (() -> Unit)? = null,
    onAttachCamera: (() -> Unit)? = null,
    onAttachFiles: (() -> Unit)? = null,
    onPinCurrentTab: (() -> Unit)? = null,
    currentTabUrl: String? = null,
    onWebsiteClick: (() -> Unit)? = null,
    onPinConnector: (() -> Unit)? = null,
    onPinResearchCanvas: (() -> Unit)? = null,
    onAttachResearch: (() -> Unit)? = null,
    availableFiles: List<GVONEFileItem> = emptyList(),
    availableResearch: List<ResearchSource> = emptyList(),
    onSelectFileItem: ((fileName: String, filePath: String) -> Unit)? = null,
    onSelectResearchItem: ((title: String, notes: String?) -> Unit)? = null,
    onDismiss: (() -> Unit)? = null
) {
    // If no suggestions, no prompts, no attachments, and no pin options are available, return
    if (suggestions.isEmpty() && prompts.isEmpty() && onTogglePinTerminal == null && availableFiles.isEmpty() && availableResearch.isEmpty()) return

    var selectedTab by remember { mutableStateOf(CommandPopupTab.ALL) }

    // Build the list of pin attachment options
    val pinAttachmentOptions = remember(
        isTerminalPinned,
        currentTabUrl,
        onTogglePinTerminal,
        onAttachPhotos,
        onAttachCamera,
        onAttachFiles,
        onPinCurrentTab,
        onPinConnector,
        onPinResearchCanvas,
        onAttachResearch
    ) {
        val list = mutableListOf<PinAttachmentOption>()

        // 1. Pin Terminal
        list.add(
            PinAttachmentOption(
                id = "pin_terminal",
                title = "Pin Terminal (50% Split View)",
                subtitle = if (isTerminalPinned) "Terminal is pinned to 50% split view" else "Fix terminal in 50% split view on screen",
                icon = Icons.Rounded.PushPin,
                badge = if (isTerminalPinned) "PINNED" else "SPLIT",
                isPinned = isTerminalPinned,
                onClick = { onTogglePinTerminal?.invoke() }
            )
        )

        // 2. Pin Current Webpage
        list.add(
            PinAttachmentOption(
                id = "pin_current_page",
                title = "Pin Current Webpage",
                subtitle = currentTabUrl?.ifBlank { null } ?: "Attach live webpage context to prompt",
                icon = Icons.Rounded.Link,
                badge = "WEB CONTEXT",
                isPinned = false,
                onClick = { onPinCurrentTab?.invoke() }
            )
        )

        // 3. Attach Photos & Wallpapers
        if (onAttachPhotos != null) {
            list.add(
                PinAttachmentOption(
                    id = "attach_photos",
                    title = "Attach Photos & Wallpapers",
                    subtitle = "Pick images and wallpapers from gallery",
                    icon = Icons.Rounded.Image,
                    badge = "GALLERY",
                    isPinned = false,
                    onClick = onAttachPhotos
                )
            )
        }

        // 4. Capture Camera Scanner
        if (onAttachCamera != null) {
            list.add(
                PinAttachmentOption(
                    id = "capture_camera",
                    title = "Capture Camera Scanner",
                    subtitle = "Snap photo or scan document for agent",
                    icon = Icons.Rounded.PhotoCamera,
                    badge = "CAMERA",
                    isPinned = false,
                    onClick = onAttachCamera
                )
            )
        }

        // 5. Attach Universal Files
        if (onAttachFiles != null) {
            list.add(
                PinAttachmentOption(
                    id = "attach_files",
                    title = "Attach Universal Files",
                    subtitle = "Attach PDFs, local docs & GVONE file system",
                    icon = Icons.Rounded.Folder,
                    badge = "FILES",
                    isPinned = false,
                    onClick = onAttachFiles
                )
            )
        }

        // 6. Pin Website Connector
        if (onPinConnector != null) {
            list.add(
                PinAttachmentOption(
                    id = "pin_connector",
                    title = "Pin Website Connector",
                    subtitle = "Attach website credentials & session bridge",
                    icon = Icons.Rounded.Hub,
                    badge = "CONNECTOR",
                    isPinned = false,
                    onClick = onPinConnector
                )
            )
        }

        // 7. Attach Research Canvas
        if (onAttachResearch != null || onPinResearchCanvas != null) {
            list.add(
                PinAttachmentOption(
                    id = "pin_research",
                    title = "Attach Research Canvas",
                    subtitle = "Attach multi-agent research notes & canvas",
                    icon = Icons.Rounded.Science,
                    badge = "RESEARCH",
                    isPinned = false,
                    onClick = { (onAttachResearch ?: onPinResearchCanvas)?.invoke() }
                )
            )
        }

        list
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.75f))
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF8131A24),
                        Color(0xFD0B0F15)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GVONEPrimary.copy(alpha = 0.45f),
                        Color(0x3338BDF8),
                        Color(0x22FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("command_autocomplete_popup")
            .testTag("attachments_panel_popup"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
        ) {
            // Top drag-handle pill indicator
            Box(
                modifier = Modifier
                    .padding(top = 2.dp, bottom = 8.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF64748B))
                    .align(Alignment.CenterHorizontally)
                    .testTag("popup_drag_handle")
            )

            // Header: Attachments + Manage button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .testTag("popup_header_title")
                        .testTag("attachments_panel_title")
                ) {
                    // Triple-icon cluster
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0x22FFFFFF))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = null,
                            tint = GVONEPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.AddPhotoAlternate,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(13.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column {
                        Text(
                            text = "ATTACHMENTS",
                            color = Color(0xFFF1F5F9),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Attachments • Actions • Quick Select",
                            color = Color(0xFF94A3B8),
                            fontSize = 9.5.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onOpenCommandManager() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.Transparent
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Manage",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (onDismiss != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Horizontal scrollable dark gray squircle cards: Photos, Camera, Files, Website, Connectors, Research, Pin
            HorizontalActionCardsRow(
                onPhotosClick = { onAttachPhotos?.invoke() },
                onCameraClick = { onAttachCamera?.invoke() },
                onFilesClick = { onAttachFiles?.invoke() },
                onWebsiteClick = { onWebsiteClick?.invoke() ?: onPinCurrentTab?.invoke() },
                onConnectorsClick = onPinConnector,
                onResearchClick = { (onAttachResearch ?: onPinResearchCanvas)?.invoke() },
                onPinTerminalClick = onTogglePinTerminal,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Segmented Tabs: [ All ] [ Attachments ] [ Suggestions ] [ Pin Tabs ]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommandPopupTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val (tabColor, tabIcon) = when (tab) {
                        CommandPopupTab.ALL -> Color(0xFFE2E8F0) to Icons.Rounded.Apps
                        CommandPopupTab.ATTACHMENTS -> GVONEPrimary to Icons.Rounded.AttachFile
                        CommandPopupTab.SUGGESTIONS -> Color(0xFFFBBF24) to Icons.Rounded.Lightbulb
                        CommandPopupTab.PIN_ATTACHMENTS -> Color(0xFF38BDF8) to Icons.Rounded.PushPin
                    }

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedTab = tab }
                            .testTag(tab.testTag)
                            .testTag(if (tab == CommandPopupTab.ATTACHMENTS) "tab_action_command" else tab.testTag),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) tabColor.copy(alpha = 0.20f) else Color(0x201E293B),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) tabColor.copy(alpha = 0.65f) else Color(0x20FFFFFF)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = tabIcon,
                                contentDescription = null,
                                tint = if (isSelected) tabColor else Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = tab.label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = Color(0xFF1E293B),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            // Scrollable Content Area: Max height to preserve screen space
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. ATTACHMENTS SECTION (Files, Research, and Commands)
                if (selectedTab == CommandPopupTab.ALL || selectedTab == CommandPopupTab.ATTACHMENTS) {
                    // --- FILES SECTION ---
                    SectionHeader(
                        icon = Icons.Rounded.Folder,
                        title = "FILES",
                        accentColor = Color(0xFFA78BFA),
                        count = availableFiles.size
                    )

                    if (availableFiles.isNotEmpty()) {
                        val displayFiles = if (selectedTab == CommandPopupTab.ALL) availableFiles.take(3) else availableFiles.take(6)
                        displayFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectFileItem?.invoke(file.name, file.path) ?: onAttachFiles?.invoke()
                                        onDismiss?.invoke()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("popup_file_item_${file.name}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = file.fileType.color.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, file.fileType.color.copy(alpha = 0.35f)),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when (file.fileType) {
                                                FileType.FOLDER -> Icons.Rounded.Folder
                                                FileType.PDF -> Icons.Rounded.PictureAsPdf
                                                FileType.CODE -> Icons.Rounded.Code
                                                FileType.MARKDOWN, FileType.TEXT -> Icons.Rounded.Description
                                                FileType.IMAGE -> Icons.Rounded.Image
                                                else -> Icons.Rounded.InsertDriveFile
                                            },
                                            contentDescription = null,
                                            tint = file.fileType.color,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${file.formattedSize} • ${file.formattedDate}",
                                        color = Color(0xFF64748B),
                                        fontSize = 11.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x22818CF8),
                                    border = BorderStroke(0.5.dp, Color(0xFFA78BFA)),
                                    modifier = Modifier.clickable {
                                        onSelectFileItem?.invoke(file.name, file.path) ?: onAttachFiles?.invoke()
                                        onDismiss?.invoke()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(12.dp))
                                        Text("Select", color = Color(0xFFA78BFA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Browse all files link
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAttachFiles?.invoke()
                                onDismiss?.invoke()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("popup_browse_files_link"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Browse all files in GVONE Storage...", color = Color(0xFFA78BFA), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // --- RESEARCH SECTION ---
                    SectionHeader(
                        icon = Icons.Rounded.Science,
                        title = "RESEARCH",
                        accentColor = Color(0xFFFBBF24),
                        count = availableResearch.size
                    )

                    if (availableResearch.isNotEmpty()) {
                        val displayResearch = if (selectedTab == CommandPopupTab.ALL) availableResearch.take(2) else availableResearch.take(4)
                        displayResearch.forEach { research ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectResearchItem?.invoke(research.title, research.url) ?: onAttachResearch?.invoke()
                                        onDismiss?.invoke()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("popup_research_item_${research.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color(0x22FBBF24),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, Color(0x55FBBF24)),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.Science,
                                            contentDescription = null,
                                            tint = Color(0xFFFBBF24),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = research.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = research.author?.ifBlank { null } ?: research.domain?.ifBlank { null } ?: "Research Canvas Source",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0x22FBBF24),
                                    border = BorderStroke(0.5.dp, Color(0xFFFBBF24)),
                                    modifier = Modifier.clickable {
                                        onSelectResearchItem?.invoke(research.title, research.url) ?: onAttachResearch?.invoke()
                                        onDismiss?.invoke()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.AttachFile, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(12.dp))
                                        Text("Select", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Attach Research Canvas button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectResearchItem?.invoke("Research Workspace Canvas", "research://workspace") ?: (onAttachResearch ?: onPinResearchCanvas)?.invoke()
                                onDismiss?.invoke()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("popup_attach_research_canvas_link"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Attach Research Workspace Canvas", color = Color(0xFFFBBF24), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // --- COMMAND SUGGESTIONS SECTION ---
                    SectionHeader(
                        icon = Icons.Rounded.Terminal,
                        title = "TERMINAL COMMANDS",
                        accentColor = GVONEPrimary,
                        count = suggestions.size
                    )

                    if (suggestions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "No matching terminal commands. Type / for all commands.",
                                color = Color(0xFF64748B),
                                fontSize = 11.5.sp
                            )
                        }
                    } else {
                        val displayCommands = if (selectedTab == CommandPopupTab.ALL) suggestions.take(4) else suggestions.take(8)
                        displayCommands.forEachIndexed { index, suggestion ->
                            val cmd = suggestion.command
                            val isQueryEmpty = suggestion.queryArgument.isEmpty()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectSuggestion(suggestion, !isQueryEmpty)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.5.dp)
                                    .testTag("command_suggestion_${cmd.command.removePrefix("/")}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Monospace command pill
                                Surface(
                                    color = GVONEPrimary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, GVONEPrimary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = suggestion.matchedTrigger,
                                        color = GVONEPrimary,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Details: Name + Action Preview
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cmd.name,
                                            color = Color(0xFFF1F5F9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        val (typeColor, typeLabel) = when (cmd.type) {
                                            CommandType.SEARCH -> GVONEPrimary to "Search"
                                            CommandType.AI -> GVONESecondary to "GVONE AI"
                                            CommandType.URL -> Color(0xFF38BDF8) to "URL"
                                            CommandType.PAGE_ACTION -> Color(0xFFA855F7) to "Page"
                                            CommandType.BROWSER_ACTION -> Color(0xFFF59E0B) to "Action"
                                            CommandType.AUTOMATION -> Color(0xFF10B981) to "JS"
                                        }

                                        Surface(
                                            color = typeColor.copy(alpha = 0.12f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = typeLabel,
                                                color = typeColor,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = suggestion.displayPreview,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Trailing Action Icon: Run (arrow) or Fill (insert)
                                IconButton(
                                    onClick = {
                                        onSelectSuggestion(suggestion, true)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (!isQueryEmpty) Icons.Rounded.ArrowForward else Icons.Rounded.NorthWest,
                                        contentDescription = if (!isQueryEmpty) "Execute" else "Autocomplete",
                                        tint = if (!isQueryEmpty) GVONEPrimary else Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (index < displayCommands.size - 1) {
                                HorizontalDivider(
                                    color = Color(0xFF17202D),
                                    thickness = 0.5.dp,
                                    modifier = Modifier.padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }

                // 2. SUGGESTIONS SECTION
                if (selectedTab == CommandPopupTab.ALL || selectedTab == CommandPopupTab.SUGGESTIONS) {
                    if (selectedTab == CommandPopupTab.ALL) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        SectionHeader(
                            icon = Icons.Rounded.Lightbulb,
                            title = "SUGGESTIONS",
                            accentColor = Color(0xFFFBBF24),
                            count = prompts.size
                        )
                    }

                    val displayPrompts = if (selectedTab == CommandPopupTab.ALL) prompts.take(4) else prompts
                    displayPrompts.forEachIndexed { index, prompt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectPrompt?.invoke(prompt.promptText)
                                }
                                .padding(horizontal = 14.dp, vertical = 6.5.dp)
                                .testTag("popup_suggestion_item_${prompt.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0x22FBBF24),
                                border = BorderStroke(1.dp, Color(0x44FBBF24)),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = prompt.icon,
                                        contentDescription = null,
                                        tint = Color(0xFFFBBF24),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = prompt.title,
                                        color = Color(0xFFF1F5F9),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Surface(
                                        color = Color(0x22FBBF24),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = prompt.category,
                                            color = Color(0xFFFBBF24),
                                            fontSize = 9.5.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = prompt.promptText,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = {
                                    onSelectPrompt?.invoke(prompt.promptText)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowForward,
                                    contentDescription = "Run Suggestion",
                                    tint = Color(0xFFFBBF24),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        if (index < displayPrompts.size - 1) {
                            HorizontalDivider(
                                color = Color(0xFF17202D),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }

                // 3. PIN ATTACHMENTS SECTION
                if (selectedTab == CommandPopupTab.ALL || selectedTab == CommandPopupTab.PIN_ATTACHMENTS) {
                    if (selectedTab == CommandPopupTab.ALL) {
                        Spacer(modifier = Modifier.height(4.dp))
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        SectionHeader(
                            icon = Icons.Rounded.PushPin,
                            title = "PIN TABS",
                            accentColor = Color(0xFF38BDF8),
                            count = pinAttachmentOptions.size
                        )
                    }

                    val displayPins = if (selectedTab == CommandPopupTab.ALL) pinAttachmentOptions.take(4) else pinAttachmentOptions
                    displayPins.forEachIndexed { index, option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { option.onClick() }
                                .padding(horizontal = 14.dp, vertical = 6.5.dp)
                                .testTag(
                                    if (option.id == "pin_terminal") "popup_pin_terminal_item"
                                    else "popup_pin_attachment_${option.id}"
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (option.isPinned) Color(0x3338BDF8) else Color(0x1838BDF8),
                                border = BorderStroke(
                                    1.dp,
                                    if (option.isPinned) Color(0xFF38BDF8) else Color(0x3338BDF8)
                                ),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null,
                                        tint = if (option.isPinned) Color(0xFF38BDF8) else Color(0xFF7DD3FC),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = option.title,
                                        color = Color(0xFFF1F5F9),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Surface(
                                        color = if (option.isPinned) Color(0x3310B981) else Color(0x2238BDF8),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = option.badge,
                                            color = if (option.isPinned) Color(0xFF34D399) else Color(0xFF38BDF8),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = option.subtitle,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (option.isPinned) Color(0x2238BDF8) else Color(0x15FFFFFF),
                                border = BorderStroke(
                                    0.5.dp,
                                    if (option.isPinned) Color(0xFF38BDF8) else Color(0x33FFFFFF)
                                ),
                                modifier = Modifier.clickable { option.onClick() }
                            ) {
                                Text(
                                    text = if (option.isPinned) "Active" else "Pin",
                                    color = if (option.isPinned) Color(0xFF38BDF8) else Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (index < displayPins.size - 1) {
                            HorizontalDivider(
                                color = Color(0xFF17202D),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = title,
                color = accentColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Surface(
            color = accentColor.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = "$count",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}
