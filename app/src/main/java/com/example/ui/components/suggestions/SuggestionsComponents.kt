package com.example.ui.components.suggestions

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.data.model.DefaultSuggestions
import com.example.data.model.Suggestion
import com.example.ui.theme.*

/**
 * SuggestionsButton:
 * Bottom-right circular bulb/sparkle button that acts as the entry point for Suggestions.
 * Has a subtle active glow state when Suggestions UI is open.
 */
@Composable
fun SuggestionsButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    val transition = updateTransition(targetState = isActive, label = "BulbActiveTransition")
    
    val glowColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 200, easing = FastOutSlowInEasing) },
        label = "GlowColor"
    ) { active ->
        if (active) Color(0xFFF59E0B) else Color(0x33FFFFFF)
    }

    val iconColor by transition.animateColor(
        transitionSpec = { tween(durationMillis = 200, easing = FastOutSlowInEasing) },
        label = "IconColor"
    ) { active ->
        if (active) Color(0xFFFBBF24) else Color(0xFFE2E8F0)
    }

    val buttonScale by transition.animateFloat(
        transitionSpec = {
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        },
        label = "ButtonScale"
    ) { active ->
        if (active) 1.05f else 1.0f
    }

    val backgroundBrush = if (isActive) {
        Brush.radialGradient(
            colors = listOf(
                Color(0xFF2E2412),
                Color(0xFF1C1710),
                Color(0xFF0F1117)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xE61E293B),
                Color(0xF00F172A)
            )
        )
    }

    Surface(
        modifier = modifier
            .size(52.dp)
            .scale(buttonScale)
            .shadow(
                elevation = if (isActive) 12.dp else 4.dp,
                shape = CircleShape,
                spotColor = if (isActive) Color(0x88F59E0B) else Color.Black
            )
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                brush = if (isActive) {
                    Brush.sweepGradient(
                        listOf(
                            Color(0xFFF59E0B),
                            Color(0xFF38BDF8),
                            Color(0xFFF59E0B)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color(0x44FFFFFF),
                            Color(0x15FFFFFF)
                        )
                    )
                },
                shape = CircleShape
            )
            .testTag("suggestions_button")
            .testTag("bulb_button"),
        shape = CircleShape,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Subtle active pulsing aura behind icon
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x44F59E0B),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Icon(
                imageVector = if (isActive) Icons.Rounded.Lightbulb else Icons.Rounded.AutoAwesome,
                contentDescription = "Proactive Suggestions",
                tint = iconColor,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

/**
 * SuggestionsPanel:
 * Independent reusable suggestions container that renders at the top of the home content area.
 * Follows the glassmorphism design language of GVONE:
 * - dark/glassmorphism surface
 * - rounded corners
 * - subtle border
 * - soft shadow
 * - compact horizontal suggestion chips/cards
 * - sparkle/lightbulb visual language
 */
@Composable
fun SuggestionsPanel(
    suggestions: List<Suggestion>,
    onSuggestionClick: (Suggestion) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = remember(suggestions) {
        listOf("All") + suggestions.map { it.category }.distinct()
    }

    val filteredSuggestions = remember(suggestions, selectedCategory) {
        if (selectedCategory == "All") {
            suggestions
        } else {
            suggestions.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = Color(0x33F59E0B)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0x55F59E0B),
                        Color(0x3338BDF8),
                        Color(0x15FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("suggestions_panel"),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF20F1420)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xE6141B2B),
                            Color(0xF50D111A)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            // Header Row: Sparkle/Bulb visual language + Title + Close Button
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
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0x22F59E0B))
                            .border(1.dp, Color(0x44F59E0B), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SUGGESTIONS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
                                    fontFamily = FontFamily.Monospace
                                ),
                                color = Color(0xFFF59E0B)
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x3338BDF8))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    text = "AGENTIC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF7DD3FC)
                                )
                            }
                        }
                        Text(
                            text = "Contextual workflows & neural commands",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Dismiss 'X' Button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .testTag("suggestions_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Suggestions",
                        tint = Color(0xFFCBD5E1),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Category Filter Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                categories.forEach { category ->
                    val isSelected = category == selectedCategory
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) Color(0x33F59E0B) else Color(0x14FFFFFF)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) Color(0x88F59E0B) else Color(0x18FFFFFF),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedCategory = category }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = category,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFFFBBF24) else Color(0xFF94A3B8)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compact Horizontal Suggestion Chips / Cards
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
            ) {
                items(filteredSuggestions, key = { it.id }) { suggestion ->
                    SuggestionCard(
                        suggestion = suggestion,
                        onClick = { onSuggestionClick(suggestion) }
                    )
                }
            }
        }
    }
}

/**
 * SuggestionCard:
 * Compact horizontal card component matching the glassmorphic aesthetics of GVONE.
 */
@Composable
fun SuggestionCard(
    suggestion: Suggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconVector = remember(suggestion.iconName) {
        DefaultSuggestions.getIconForName(suggestion.iconName)
    }

    val categoryColor = when (suggestion.category.lowercase()) {
        "missions" -> Color(0xFFF59E0B)
        "swarm" -> Color(0xFF818CF8)
        "agents" -> Color(0xFF38BDF8)
        "workflows" -> Color(0xFF10B981)
        "signals" -> Color(0xFFA855F7)
        "feeds" -> Color(0xFFEC4899)
        else -> Color(0xFF38BDF8)
    }

    Surface(
        modifier = modifier
            .width(210.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x33FFFFFF),
                        Color(0x12FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .testTag("suggestion_card_${suggestion.id}")
            .testTag("suggestion_chip_${suggestion.id}"),
        shape = RoundedCornerShape(14.dp),
        color = Color(0x331E293B)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x441E293B),
                            Color(0x660F172A)
                        )
                    )
                )
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(categoryColor.copy(alpha = 0.2f))
                        .border(1.dp, categoryColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = suggestion.category.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = categoryColor
                    )
                }

                // Small Action Icon
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFFF1F5F9),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = suggestion.description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                ),
                color = Color(0xFF94A3B8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * SuggestionChip:
 * Compact chip variant for tight mobile screens or secondary suggestions.
 */
@Composable
fun SuggestionChip(
    suggestion: Suggestion,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconVector = remember(suggestion.iconName) {
        DefaultSuggestions.getIconForName(suggestion.iconName)
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = Color(0x33FFFFFF),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .testTag("suggestion_chip_${suggestion.id}"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0x401E293B)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = suggestion.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF1F5F9)
            )
        }
    }
}

/**
 * Types of items that can be selected in the terminal chips bar.
 * Supports Photos, Files, Researches, Websites, as well as Prompts and Commands.
 */
enum class SelectedItemType {
    PHOTO,
    FILE,
    RESEARCH,
    WEBSITE,
    PROMPT,
    COMMAND
}

/**
 * QuickPrompt data model for horizontal suggestion chips above the address bar.
 * Also represents selected items (Photos, Files, Researches, Websites, Prompts, Commands).
 */
data class QuickPrompt(
    val id: String,
    val title: String,
    val promptText: String,
    val icon: ImageVector = Icons.Rounded.AutoAwesome,
    val category: String = "Prompt",
    val type: SelectedItemType = when (category.lowercase()) {
        "photo", "photos", "image", "gallery" -> SelectedItemType.PHOTO
        "file", "files", "document", "pdf" -> SelectedItemType.FILE
        "research", "canvas", "notes", "synthesis", "paper" -> SelectedItemType.RESEARCH
        "website", "web", "webpage", "url", "tab" -> SelectedItemType.WEBSITE
        "command", "terminal" -> SelectedItemType.COMMAND
        else -> SelectedItemType.PROMPT
    },
    val uriOrUrl: String? = null
)

object DefaultQuickPrompts {
    val items = listOf(
        QuickPrompt(
            id = "attach_photo",
            title = "Attach Photo",
            promptText = "/photos",
            icon = Icons.Rounded.AddPhotoAlternate,
            category = "Photo"
        ),
        QuickPrompt(
            id = "attach_file",
            title = "Attach File",
            promptText = "/files",
            icon = Icons.Rounded.Folder,
            category = "File"
        ),
        QuickPrompt(
            id = "attach_research",
            title = "Attach Research",
            promptText = "/research",
            icon = Icons.Rounded.Science,
            category = "Research"
        ),
        QuickPrompt(
            id = "attach_web",
            title = "Attach Web Context",
            promptText = "/web",
            icon = Icons.Rounded.Language,
            category = "Website"
        ),
        QuickPrompt(
            id = "pin_terminal",
            title = "Pin Terminal",
            promptText = "/pin",
            icon = Icons.Rounded.PushPin,
            category = "Terminal"
        ),
        QuickPrompt(
            id = "mission_templates",
            title = "Add Mission Templates",
            promptText = "Add mission templates for autonomous execution",
            icon = Icons.Rounded.Assignment,
            category = "Missions"
        ),
        QuickPrompt(
            id = "swarm_visualize",
            title = "Visualize Swarm Members",
            promptText = "Visualize all active swarm members and agents",
            icon = Icons.Rounded.Hub,
            category = "Swarm"
        ),
        QuickPrompt(
            id = "explore_agents",
            title = "Explore Agent Swarm",
            promptText = "Explore available subagents and adapter capabilities",
            icon = Icons.Rounded.SmartToy,
            category = "Agents"
        ),
        QuickPrompt(
            id = "feed_signals",
            title = "Analyze Feed Signals",
            promptText = "Analyze current feed signals and telemetry",
            icon = Icons.Rounded.RssFeed,
            category = "Signals"
        ),
        QuickPrompt(
            id = "nodal_workflow",
            title = "Run Nodal Workflow",
            promptText = "Execute primary nodal pipeline workflow",
            icon = Icons.Rounded.AccountTree,
            category = "Workflows"
        ),
        QuickPrompt(
            id = "diagnostics",
            title = "System Diagnostics",
            promptText = "Run Central Nervous System health check and diagnostics",
            icon = Icons.Rounded.Psychology,
            category = "CNS"
        ),
        QuickPrompt(
            id = "youtube_search",
            title = "Search YouTube",
            promptText = "/yt ",
            icon = Icons.Rounded.PlayCircle,
            category = "Search"
        ),
        QuickPrompt(
            id = "research_canvas",
            title = "Open Research Canvas",
            promptText = "Open multi-agent research workspace canvas",
            icon = Icons.Rounded.Science,
            category = "Research"
        ),
        QuickPrompt(
            id = "pin_terminal",
            title = "Pin Terminal",
            promptText = "/pin",
            icon = Icons.Rounded.PushPin,
            category = "Terminal"
        )
    )
}

/**
 * SuggestionChipsBar:
 * Floating row of quick-prompt chips matching the address bar theme,
 * with the bulb icon placed on the left of the persistent chips.
 * When items are selected by the user, they appear prominently near the bulb icon
 * with a close button ('X') so the user can easily remove them.
 */
@Composable
fun SuggestionChipsBar(
    prompts: List<QuickPrompt> = DefaultQuickPrompts.items,
    selectedItems: List<QuickPrompt> = emptyList(),
    onRemoveSelectedItem: (QuickPrompt) -> Unit = {},
    isSuggestivePopupOpen: Boolean = false,
    showPinButton: Boolean = false,
    isTerminalPinned: Boolean = false,
    onTogglePinTerminal: () -> Unit = {},
    onToggleBulb: () -> Unit = {},
    onSelectPrompt: (String) -> Unit,
    onSelectQuickPrompt: ((QuickPrompt) -> Unit)? = null,
    onAttachPhoto: (() -> Unit)? = null,
    onAttachFile: (() -> Unit)? = null,
    onAttachResearch: (() -> Unit)? = null,
    onAttachWebsite: (() -> Unit)? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Automatically scroll to front when a new selected item is added
    LaunchedEffect(selectedItems.size) {
        if (selectedItems.isNotEmpty()) {
            scrollState.animateScrollTo(0)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("floating_suggestion_chips_bar")
            .testTag("suggestion_chips_bar")
            .testTag("suggestion_chips_ui"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bulb icon on the LEFT of persistence chips - opens the Attachments panel
        Surface(
            modifier = Modifier
                .size(36.dp)
                .shadow(elevation = 10.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.6f))
                .clip(CircleShape)
                .clickable { onToggleBulb() }
                .testTag("bulb_button_left")
                .testTag("lightbulb_icon")
                .testTag("bulb_button")
                .testTag("attachments_button"),
            shape = CircleShape,
            color = if (isSuggestivePopupOpen) Color(0xFF2D2312) else Color(0xEB131A24),
            border = BorderStroke(
                1.dp,
                if (isSuggestivePopupOpen) Color(0xFFFBBF24) else Color(0x33FFFFFF)
            )
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = "Attachments",
                    tint = if (isSuggestivePopupOpen) Color(0xFFFBBF24) else Color(0xFFE2E8F0),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Dedicated Pin Button on top of address bar for fixing terminal in half screen
        if (showPinButton) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                modifier = Modifier
                    .height(34.dp)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(17.dp), spotColor = Color.Black.copy(alpha = 0.4f))
                    .clip(RoundedCornerShape(17.dp))
                    .clickable { onTogglePinTerminal() }
                    .testTag("pin_half_screen_top_button")
                    .testTag("pin_half_screen_button")
                    .testTag("terminal_pin_half_screen_top_btn")
                    .testTag("address_bar_pin_terminal_toggle"),
                shape = RoundedCornerShape(17.dp),
                color = if (isTerminalPinned) Color(0x3338BDF8) else Color(0xEB131A24),
                border = BorderStroke(
                    1.dp,
                    if (isTerminalPinned) Color(0xFF38BDF8) else Color(0x33FFFFFF)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = if (isTerminalPinned) "Unfix from half screen" else "Fix in half of screen",
                        tint = if (isTerminalPinned) Color(0xFF38BDF8) else Color(0xFFE2E8F0),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (isTerminalPinned) "Fixed 50%" else "Fix in half of screen",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isTerminalPinned) Color(0xFF38BDF8) else Color(0xFFE2E8F0)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Horizontal scrollable floating chips containing:
        // 1. Selected items (right near the bulb icon, with close button)
        // 2. Quick attachment chips (Photos, Files, Research, Web)
        // 3. Regular suggestion chips
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selected item(s) chosen by user - shown right near the bulb icon like photos
            selectedItems.forEach { item ->
                SelectedItemChip(
                    item = item,
                    onClose = { onRemoveSelectedItem(item) }
                )
            }

            // Quick Attachment action chips directly in horizontal suggestion chips
            if (onAttachPhoto != null && selectedItems.none { it.type == SelectedItemType.PHOTO }) {
                QuickAttachmentActionChip(
                    label = "+ Photo",
                    icon = Icons.Rounded.AddPhotoAlternate,
                    accentColor = Color(0xFF34D399),
                    onClick = onAttachPhoto,
                    testTag = "suggestion_chip_attach_photo"
                )
            }
            if (onAttachFile != null && selectedItems.none { it.type == SelectedItemType.FILE }) {
                QuickAttachmentActionChip(
                    label = "+ File",
                    icon = Icons.Rounded.Folder,
                    accentColor = Color(0xFFA78BFA),
                    onClick = onAttachFile,
                    testTag = "suggestion_chip_attach_file"
                )
            }
            if (onAttachResearch != null && selectedItems.none { it.type == SelectedItemType.RESEARCH }) {
                QuickAttachmentActionChip(
                    label = "+ Research",
                    icon = Icons.Rounded.Science,
                    accentColor = Color(0xFFFBBF24),
                    onClick = onAttachResearch,
                    testTag = "suggestion_chip_attach_research"
                )
            }
            if (onAttachWebsite != null && selectedItems.none { it.type == SelectedItemType.WEBSITE }) {
                QuickAttachmentActionChip(
                    label = "+ Web",
                    icon = Icons.Rounded.Language,
                    accentColor = Color(0xFF38BDF8),
                    onClick = onAttachWebsite,
                    testTag = "suggestion_chip_attach_web"
                )
            }

            // Available suggestion chips
            prompts.forEach { prompt ->
                val isSelected = selectedItems.any { it.id == prompt.id }
                QuickPromptChip(
                    prompt = prompt,
                    isSelected = isSelected,
                    onClick = {
                        onSelectPrompt(prompt.promptText)
                        onSelectQuickPrompt?.invoke(prompt)
                    }
                )
            }
        }
    }
}

/**
 * QuickAttachmentActionChip:
 * Compact button in the horizontal suggestion bar allowing users to quickly attach items like photos, files, research, or web.
 */
@Composable
fun QuickAttachmentActionChip(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = ""
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(17.dp), spotColor = accentColor.copy(alpha = 0.25f))
            .clip(RoundedCornerShape(17.dp))
            .clickable { onClick() }
            .testTag(testTag)
            .testTag("quick_attach_chip_${label.filter { it.isLetterOrDigit() }.lowercase()}"),
        shape = RoundedCornerShape(17.dp),
        color = Color(0xEB131A24),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
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
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF1F5F9)
            )
        }
    }
}

/**
 * SelectedItemChip:
 * Highlighted chip representing an item selected by the user, displayed right near the bulb icon.
 * Includes a close ("X") button to remove the selection.
 */
@Composable
fun SelectedItemChip(
    item: QuickPrompt,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Style attributes based on item.type
    val (borderColors, bgColors, iconVector, iconTint, titleColor, closeBg, closeTint) = when (item.type) {
        SelectedItemType.PHOTO -> Tuple7(
            listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFF059669)),
            listOf(Color(0xEE0D2419), Color(0xF0081810)),
            Icons.Rounded.AddPhotoAlternate,
            Color(0xFF34D399),
            Color(0xFFD1FAE5),
            Color(0x3310B981),
            Color(0xFF6EE7B7)
        )
        SelectedItemType.FILE -> Tuple7(
            listOf(Color(0xFF818CF8), Color(0xFFA78BFA), Color(0xFF6366F1)),
            listOf(Color(0xEE1A1B35), Color(0xF0111224)),
            Icons.Rounded.Folder,
            Color(0xFFA78BFA),
            Color(0xFFEDE9FE),
            Color(0x33818CF8),
            Color(0xFFC4B5FD)
        )
        SelectedItemType.RESEARCH -> Tuple7(
            listOf(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFFD97706)),
            listOf(Color(0xEE2A1C08), Color(0xF01C1305)),
            Icons.Rounded.Science,
            Color(0xFFFBBF24),
            Color(0xFFFEF3C7),
            Color(0x33F59E0B),
            Color(0xFFFDE68A)
        )
        SelectedItemType.WEBSITE -> Tuple7(
            listOf(Color(0xFF0EA5E9), Color(0xFF38BDF8), Color(0xFF0284C7)),
            listOf(Color(0xEE0B1E2D), Color(0xF007141E)),
            Icons.Rounded.Language,
            Color(0xFF38BDF8),
            Color(0xFFE0F2FE),
            Color(0x330EA5E9),
            Color(0xFF7DD3FC)
        )
        else -> Tuple7(
            listOf(Color(0xFFF59E0B), Color(0xFFFBBF24), Color(0xFF38BDF8)),
            listOf(Color(0xEE2A1E0D), Color(0xF01C170E)),
            if (item.type == SelectedItemType.COMMAND) Icons.Rounded.Terminal else Icons.Rounded.CheckCircle,
            Color(0xFFFBBF24),
            Color(0xFFFEF3C7),
            Color(0x33F59E0B),
            Color(0xFFFDE68A)
        )
    }

    Surface(
        modifier = modifier
            .height(34.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(17.dp), spotColor = borderColors.first().copy(alpha = 0.4f))
            .clip(RoundedCornerShape(17.dp))
            .testTag("selected_item_chip")
            .testTag("selected_item_chip_${item.id}")
            .testTag("selected_chip_${item.id}")
            .testTag("selected_item_${item.type.name.lowercase()}"),
        shape = RoundedCornerShape(17.dp),
        color = bgColors.first(),
        border = BorderStroke(
            1.2.dp,
            Brush.horizontalGradient(borderColors)
        )
    ) {
        Row(
            modifier = Modifier
                .background(Brush.horizontalGradient(bgColors))
                .padding(start = 10.dp, end = 5.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Type-specific indicator icon
            Icon(
                imageVector = iconVector,
                contentDescription = "Selected ${item.type.name}",
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )

            // Selected item title
            Text(
                text = item.title,
                color = titleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("selected_item_title_${item.id}")
            )

            // Remove/Close button
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(closeBg)
                    .clickable { onClose() }
                    .testTag("remove_selected_item")
                    .testTag("close_selected_item")
                    .testTag("close_selected_item_${item.id}")
                    .testTag("remove_selected_item_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove ${item.title}",
                    tint = closeTint,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

private data class Tuple7<A, B, C, D, E, F, G>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E,
    val sixth: F,
    val seventh: G
)

/**
 * QuickPromptChip:
 * Sleek floating prompt chip matching the dark glassmorphic address bar theme.
 */
@Composable
fun QuickPromptChip(
    prompt: QuickPrompt,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(17.dp), spotColor = Color.Black.copy(alpha = 0.4f))
            .clip(RoundedCornerShape(17.dp))
            .clickable { onClick() }
            .testTag("quick_prompt_chip_${prompt.id}")
            .testTag("suggestion_chip_${prompt.id}"),
        shape = RoundedCornerShape(17.dp),
        color = if (isSelected) Color(0xFF241B0E) else Color(0xEB131A24),
        border = BorderStroke(
            1.dp,
            if (isSelected) {
                Brush.linearGradient(
                    listOf(
                        Color(0xFFF59E0B),
                        Color(0x88F59E0B)
                    )
                )
            } else {
                Brush.linearGradient(
                    listOf(
                        Color(0x33FFFFFF),
                        Color(0x1838BDF8)
                    )
                )
            }
        )
    ) {
        Row(
            modifier = Modifier
                .background(
                    if (isSelected) {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xEE2A1E0D),
                                Color(0xF01C170E)
                            )
                        )
                    } else {
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xEB131A24),
                                Color(0xF00D131C)
                            )
                        )
                    }
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.Check else prompt.icon,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = prompt.title,
                color = if (isSelected) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

