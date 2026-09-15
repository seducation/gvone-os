package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.MenuShortcut
import com.example.ui.theme.*

/**
 * Returns default primary pinned shortcuts matching Safari Actions / Three-Dot menu
 */
fun getDefaultPrimaryShortcuts(): List<MenuShortcut> = listOf(
    MenuShortcut(
        title = "Flipkart Lite",
        url = "https://www.flipkart.com",
        initialLetters = "f",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFFFD200), Color(0xFF2874F0))),
        textColor = Color(0xFF2874F0)
    ),
    MenuShortcut(
        title = "Amazon India",
        url = "https://www.amazon.in",
        initialLetters = "a",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFFF9900), Color(0xFFFF6600))),
        textColor = Color.White
    ),
    MenuShortcut(
        title = "ESPNcricinfo",
        url = "https://www.espncricinfo.com",
        initialLetters = "E",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF00A3E0), Color(0xFF0072CE))),
        textColor = Color.White
    ),
    MenuShortcut(
        title = "The Financial...",
        url = "https://www.financialexpress.com",
        initialLetters = "FE",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))),
        textColor = Color.White
    )
)

/**
 * Returns default secondary and popular shortcuts shown when expanding "view all"
 */
fun getDefaultExtraShortcuts(): List<MenuShortcut> = listOf(
    MenuShortcut(
        title = "Google",
        url = "https://google.com",
        initialLetters = "G",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF34A853)))
    ),
    MenuShortcut(
        title = "YouTube",
        url = "https://youtube.com",
        initialLetters = "YT",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFCC0000)))
    ),
    MenuShortcut(
        title = "AI Companion",
        url = "https://charassist-c4uzg7hb.manus.space",
        initialLetters = "AI",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFFA855F7)))
    ),
    MenuShortcut(
        title = "DuckDuckGo",
        url = "https://duckduckgo.com",
        initialLetters = "DDG",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFDE5833), Color(0xFFE27457)))
    ),
    MenuShortcut(
        title = "Wikipedia",
        url = "https://en.wikipedia.org",
        initialLetters = "W",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF475569)))
    ),
    MenuShortcut(
        title = "GitHub",
        url = "https://github.com",
        initialLetters = "GH",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF6B21A8), Color(0xFF9333EA)))
    ),
    MenuShortcut(
        title = "Reddit",
        url = "https://reddit.com",
        initialLetters = "R",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFFF4500), Color(0xFFFF5722)))
    ),
    MenuShortcut(
        title = "X (Twitter)",
        url = "https://x.com",
        initialLetters = "X",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF334155)))
    ),
    MenuShortcut(
        title = "Netflix",
        url = "https://netflix.com",
        initialLetters = "N",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF221F1F)))
    ),
    MenuShortcut(
        title = "Spotify",
        url = "https://spotify.com",
        initialLetters = "S",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF191414)))
    ),
    MenuShortcut(
        title = "Twitch",
        url = "https://twitch.tv",
        initialLetters = "T",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF9146FF), Color(0xFF6441A5)))
    ),
    MenuShortcut(
        title = "LinkedIn",
        url = "https://linkedin.com",
        initialLetters = "in",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF0A66C2), Color(0xFF004182)))
    ),
    MenuShortcut(
        title = "Stack Overflow",
        url = "https://stackoverflow.com",
        initialLetters = "SO",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFF48024), Color(0xFFBC5800)))
    )
)

/**
 * Pinned Shortcuts Card with inline expanding/collapsing "view all" section.
 * - Shows pinned shortcuts horizontally at top with "+ Add new"
 * - On clicking "view all", smoothly expands an inline panel directly below the pin shortcuts
 *   showing all shortcuts in a responsive grid (no separate page)
 * - On clicking "show less", collapses back inline.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SafariShortcutsCard(
    modifier: Modifier = Modifier,
    onShortcutClick: (MenuShortcut) -> Unit,
    onAddFavorite: () -> Unit = {},
    customShortcuts: List<MenuShortcut> = emptyList(),
    onAddCustomShortcut: ((MenuShortcut) -> Unit)? = null,
    onRemoveShortcut: ((MenuShortcut) -> Unit)? = null,
    testTagPrefix: String = "overview_shortcut",
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    var internalExpanded by rememberSaveable { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = { newVal ->
        if (expanded != null) {
            onExpandedChange?.invoke(newVal)
        } else {
            internalExpanded = newVal
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutForOptions by remember { mutableStateOf<MenuShortcut?>(null) }
    var shortcutToDelete by remember { mutableStateOf<MenuShortcut?>(null) }

    val primary = remember { getDefaultPrimaryShortcuts() }
    val extra = remember { getDefaultExtraShortcuts() }

    // User-managed pinned URLs set
    var pinnedUrls by remember {
        mutableStateOf(primary.map { it.url }.toSet())
    }

    // Full list of all available shortcuts (primary, extra, and custom)
    val allShortcuts = remember(primary, extra, customShortcuts) {
        val combined = primary + extra + customShortcuts
        combined.distinctBy { it.url }
    }

    // Pinned shortcuts shown in the top row
    val pinnedShortcuts = remember(allShortcuts, pinnedUrls) {
        val filtered = allShortcuts.filter { pinnedUrls.contains(it.url) }
        if (filtered.isNotEmpty()) filtered else primary
    }

    var cardDragY by remember { mutableFloatStateOf(0f) }
    var handleDragY by remember { mutableFloatStateOf(0f) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(isExpanded) {
                detectVerticalDragGestures(
                    onDragStart = { cardDragY = 0f },
                    onDragEnd = {
                        if (!isExpanded && cardDragY > 20f) setExpanded(true)
                        else if (isExpanded && cardDragY < -20f) setExpanded(false)
                        cardDragY = 0f
                    },
                    onDragCancel = { cardDragY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        cardDragY += dragAmount
                        if (!isExpanded && cardDragY > 25f) {
                            setExpanded(true)
                            change.consume()
                        } else if (isExpanded && cardDragY < -25f) {
                            setExpanded(false)
                            change.consume()
                        }
                    }
                )
            }
            .testTag("${testTagPrefix}_card"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161C26),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // -----------------------------------------------------------------
            // 1. PIN SHORTCUTS ROW (Horizontal carousel of pinned shortcuts + Add new)
            // -----------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(2.dp))

                pinnedShortcuts.forEach { shortcut ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onShortcutClick(shortcut) },
                                onLongClick = {
                                    shortcutForOptions = shortcut
                                }
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .widthIn(min = 58.dp, max = 74.dp)
                            .testTag("${testTagPrefix}_item_${shortcut.title.lowercase().replace(" ", "_")}")
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(shortcut.badgeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortcut.initialLetters,
                                    color = shortcut.textColor,
                                    fontSize = if (shortcut.initialLetters.length > 2) 11.sp else if (shortcut.initialLetters.length > 1) 13.sp else 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Pin badge on pinned shortcut tile
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF38BDF8), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(9.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = shortcut.title,
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // "+ Add new" shortcut button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (onAddCustomShortcut != null) {
                                showAddDialog = true
                            } else {
                                onAddFavorite()
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .widthIn(min = 58.dp, max = 74.dp)
                        .testTag("${testTagPrefix}_add_new")
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF263244)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add new shortcut",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = "Add new",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))
            }

            // -----------------------------------------------------------------
            // 2. PULL TO EXPAND / COLLAPSE HANDLE (Drag & tap interactive handle)
            // -----------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { setExpanded(!isExpanded) }
                    .pointerInput(isExpanded) {
                        detectVerticalDragGestures(
                            onDragStart = { handleDragY = 0f },
                            onDragEnd = {
                                if (!isExpanded && handleDragY > 15f) setExpanded(true)
                                else if (isExpanded && handleDragY < -15f) setExpanded(false)
                                handleDragY = 0f
                            },
                            onDragCancel = { handleDragY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                handleDragY += dragAmount
                                if (!isExpanded && handleDragY > 20f) {
                                    setExpanded(true)
                                    change.consume()
                                } else if (isExpanded && handleDragY < -20f) {
                                    setExpanded(false)
                                    change.consume()
                                }
                            }
                        )
                    }
                    .padding(vertical = 4.dp)
                    .testTag("${testTagPrefix}_toggle_expand"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF475569))
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Pull down to expand",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // -----------------------------------------------------------------
            // 3. EXPANDING & COLLAPSIBLE ALL SHORTCUTS PANEL (4-column grid with titles)
            // -----------------------------------------------------------------
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut(animationSpec = tween(160)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTagPrefix}_expanded_container")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        color = Color(0xFF243042).copy(alpha = 0.7f),
                        thickness = 1.dp
                    )

                    // 4-column grid displaying all shortcuts
                    val chunkedShortcuts = remember(allShortcuts) {
                        allShortcuts.chunked(4)
                    }

                    chunkedShortcuts.forEach { rowShortcuts ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Top
                        ) {
                            rowShortcuts.forEach { shortcut ->
                                val isShortcutPinned = pinnedUrls.contains(shortcut.url)
                                ShortcutGridTile(
                                    shortcut = shortcut,
                                    isPinned = isShortcutPinned,
                                    onClick = { onShortcutClick(shortcut) },
                                    onLongClick = {
                                        shortcutForOptions = shortcut
                                    },
                                    testTagPrefix = testTagPrefix
                                )
                            }
                            // Fill remaining columns in the last row to maintain spacing
                            repeat(4 - rowShortcuts.size) {
                                Spacer(modifier = Modifier.width(74.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------
    // 5. SHORTCUT OPTIONS / PIN TOGGLE DIALOG (on long press)
    // -----------------------------------------------------------------
    if (shortcutForOptions != null) {
        val target = shortcutForOptions!!
        val isCurrentlyPinned = pinnedUrls.contains(target.url)
        val isCustom = customShortcuts.any { it.url == target.url }

        AlertDialog(
            onDismissRequest = { shortcutForOptions = null },
            containerColor = Color(0xFF141A24),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(target.badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = target.initialLetters,
                            color = target.textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = target.title,
                        color = GVONETextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = target.url,
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Pin / Unpin Option
                    Surface(
                        onClick = {
                            pinnedUrls = if (isCurrentlyPinned) {
                                pinnedUrls - target.url
                            } else {
                                pinnedUrls + target.url
                            }
                            shortcutForOptions = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E2838),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = if (isCurrentlyPinned) Color(0xFFF59E0B) else Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isCurrentlyPinned) "Unpin from Pin Shortcuts" else "Pin to Pin Shortcuts",
                                color = GVONETextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Open Shortcut Option
                    Surface(
                        onClick = {
                            shortcutForOptions = null
                            onShortcutClick(target)
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E2838),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Open website",
                                color = GVONETextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Delete Option (if custom shortcut)
                    if (isCustom && onRemoveShortcut != null) {
                        Surface(
                            onClick = {
                                shortcutToDelete = target
                                shortcutForOptions = null
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF2A1B22),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Delete custom shortcut",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { shortcutForOptions = null }) {
                    Text("Close", color = GVONETextSecondary)
                }
            }
        )
    }

    // -----------------------------------------------------------------
    // 6. ADD SHORTCUT DIALOG
    // -----------------------------------------------------------------
    if (showAddDialog && onAddCustomShortcut != null) {
        var newTitle by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }

        val presetSuggestions = listOf(
            Triple("YouTube", "https://youtube.com", Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFF990000)))),
            Triple("Google", "https://google.com", Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF34A853)))),
            Triple("X (Twitter)", "https://x.com", Brush.linearGradient(listOf(Color(0xFF14171A), Color(0xFF657786)))),
            Triple("Netflix", "https://netflix.com", Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF221F1F)))),
            Triple("Spotify", "https://spotify.com", Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF191414)))),
            Triple("Twitch", "https://twitch.tv", Brush.linearGradient(listOf(Color(0xFF9146FF), Color(0xFF6441A5))))
        )

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF141A24),
            title = {
                Text(
                    text = "Add Shortcut",
                    color = GVONETextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Quick Presets",
                        color = GVONETextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Presets Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presetSuggestions.forEach { (name, url, brush) ->
                            Surface(
                                onClick = {
                                    newTitle = name
                                    newUrl = url
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E2838),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D52))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(brush)
                                    )
                                    Text(
                                        text = name,
                                        color = GVONETextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title", color = GVONETextSecondary) },
                        placeholder = { Text("e.g. YouTube", color = GVONETextSecondary.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GVONETextPrimary,
                            unfocusedTextColor = GVONETextPrimary,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF333E52)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newUrl,
                        onValueChange = { newUrl = it },
                        label = { Text("Website URL", color = GVONETextSecondary) },
                        placeholder = { Text("https://...", color = GVONETextSecondary.copy(alpha = 0.6f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GVONETextPrimary,
                            unfocusedTextColor = GVONETextPrimary,
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF333E52)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val formattedUrl = if (newUrl.startsWith("http://") || newUrl.startsWith("https://")) {
                            newUrl
                        } else {
                            "https://$newUrl"
                        }
                        val title = newTitle.ifBlank {
                            formattedUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").take(12)
                        }
                        val initials = if (title.length >= 2) title.take(2).uppercase() else title.take(1).uppercase()

                        // Nice gradient based on title hash
                        val colors = when (kotlin.math.abs(title.hashCode()) % 4) {
                            0 -> listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                            1 -> listOf(Color(0xFFA855F7), Color(0xFF7E22CE))
                            2 -> listOf(Color(0xFF10B981), Color(0xFF047857))
                            else -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                        }

                        val createdShortcut = MenuShortcut(
                            title = title,
                            url = formattedUrl,
                            initialLetters = initials,
                            badgeBg = Brush.linearGradient(colors),
                            textColor = Color.White
                        )
                        // Add to custom shortcuts and auto-pin
                        onAddCustomShortcut(createdShortcut)
                        pinnedUrls = pinnedUrls + createdShortcut.url
                        showAddDialog = false
                    },
                    enabled = newUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary)
                ) {
                    Text("Add Shortcut")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            }
        )
    }

    // -----------------------------------------------------------------
    // 7. DELETE CONFIRMATION DIALOG
    // -----------------------------------------------------------------
    if (shortcutToDelete != null && onRemoveShortcut != null) {
        val target = shortcutToDelete!!
        AlertDialog(
            onDismissRequest = { shortcutToDelete = null },
            containerColor = Color(0xFF141A24),
            title = { Text("Remove Shortcut", color = GVONETextPrimary) },
            text = { Text("Do you want to remove \"${target.title}\" from shortcuts?", color = GVONETextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        pinnedUrls = pinnedUrls - target.url
                        onRemoveShortcut(target)
                        shortcutToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Remove", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { shortcutToDelete = null }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            }
        )
    }
}

/**
 * Grid tile used in the expanded "All Shortcuts" view
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShortcutGridTile(
    shortcut: MenuShortcut,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    testTagPrefix: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .testTag("${testTagPrefix}_item_${shortcut.title.lowercase().replace(" ", "_")}")
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(shortcut.badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortcut.initialLetters,
                    color = shortcut.textColor,
                    fontSize = if (shortcut.initialLetters.length > 2) 11.sp else if (shortcut.initialLetters.length > 1) 13.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isPinned) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0F172A))
                        .border(1.dp, Color(0xFF38BDF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        Text(
            text = shortcut.title,
            color = Color(0xFFCBD5E1),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

