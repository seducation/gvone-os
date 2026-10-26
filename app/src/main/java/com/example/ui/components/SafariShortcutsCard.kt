package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ShortcutStorage
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
        textColor = Color(0xFF2874F0),
        category = "Shopping"
    ),
    MenuShortcut(
        title = "Amazon India",
        url = "https://www.amazon.in",
        initialLetters = "a",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFFF9900), Color(0xFFFF6600))),
        textColor = Color.White,
        category = "Shopping"
    ),
    MenuShortcut(
        title = "ESPNcricinfo",
        url = "https://www.espncricinfo.com",
        initialLetters = "E",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF00A3E0), Color(0xFF0072CE))),
        textColor = Color.White,
        category = "News"
    ),
    MenuShortcut(
        title = "The Financial...",
        url = "https://www.financialexpress.com",
        initialLetters = "FE",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF3B82F6))),
        textColor = Color.White,
        category = "News"
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
        badgeBg = Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF34A853))),
        category = "Search"
    ),
    MenuShortcut(
        title = "YouTube",
        url = "https://youtube.com",
        initialLetters = "YT",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFFCC0000))),
        category = "Media"
    ),
    MenuShortcut(
        title = "DuckDuckGo",
        url = "https://duckduckgo.com",
        initialLetters = "DDG",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFDE5833), Color(0xFFE27457))),
        category = "Search"
    ),
    MenuShortcut(
        title = "Wikipedia",
        url = "https://en.wikipedia.org",
        initialLetters = "W",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF475569))),
        category = "News"
    ),
    MenuShortcut(
        title = "GitHub",
        url = "https://github.com",
        initialLetters = "GH",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF6B21A8), Color(0xFF9333EA))),
        category = "Dev"
    ),
    MenuShortcut(
        title = "Reddit",
        url = "https://reddit.com",
        initialLetters = "R",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFFF4500), Color(0xFFFF5722))),
        category = "Social"
    ),
    MenuShortcut(
        title = "X (Twitter)",
        url = "https://x.com",
        initialLetters = "X",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF0F172A), Color(0xFF334155))),
        category = "Social"
    ),
    MenuShortcut(
        title = "Netflix",
        url = "https://netflix.com",
        initialLetters = "N",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF221F1F))),
        category = "Media"
    ),
    MenuShortcut(
        title = "Spotify",
        url = "https://spotify.com",
        initialLetters = "S",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF191414))),
        category = "Media"
    ),
    MenuShortcut(
        title = "Twitch",
        url = "https://twitch.tv",
        initialLetters = "T",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF9146FF), Color(0xFF6441A5))),
        category = "Media"
    ),
    MenuShortcut(
        title = "LinkedIn",
        url = "https://linkedin.com",
        initialLetters = "in",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF0A66C2), Color(0xFF004182))),
        category = "Social"
    ),
    MenuShortcut(
        title = "Stack Overflow",
        url = "https://stackoverflow.com",
        initialLetters = "SO",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFF48024), Color(0xFFBC5800))),
        category = "Dev"
    ),
    MenuShortcut(
        title = "ChatGPT",
        url = "https://chatgpt.com",
        initialLetters = "AI",
        badgeBg = Brush.linearGradient(listOf(Color(0xFF10A37F), Color(0xFF0D8A6A))),
        category = "Dev"
    ),
    MenuShortcut(
        title = "Claude",
        url = "https://claude.ai",
        initialLetters = "C",
        badgeBg = Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFB45309))),
        category = "Dev"
    )
)

/**
 * Quick Action item definition for rapid access to browser utilities
 */
data class QuickActionItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color
)

/**
 * Dynamic & Scrollable Shortcut Quick Panel for Navigator Panel and Safari Actions.
 *
 * Features:
 * 1. Horizontally scrollable row of pinned shortcuts with quick-action badges and "+ Add" button.
 * 2. Horizontally scrollable category filter pills ("All", "Pinned", "Dev & AI", "Social", "News", "Shopping", "Quick Tools").
 * 3. Real-time dynamic search filter inside expanded mode.
 * 4. Vertically scrollable expanded grid with responsive constraints (max height 260dp) preventing screen overflows.
 * 5. Pull-to-expand / drag-to-collapse gesture handle with animated chevron.
 * 6. Quick Action Tools row (New Tab, Incognito, Bookmarks, History, Downloads, Terminal, AI Hub).
 * 7. Dynamic CRUD: Add custom shortcuts with presets, Edit existing shortcuts, Pin/Unpin, and Delete with local persistence via ShortcutStorage.
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
    onQuickActionClick: ((String) -> Unit)? = null,
    testTagPrefix: String = "overview_shortcut",
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var internalExpanded by rememberSaveable { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    val setExpanded: (Boolean) -> Unit = { newVal ->
        if (expanded != null) {
            onExpandedChange?.invoke(newVal)
        } else {
            internalExpanded = newVal
        }
    }

    // Persisted custom shortcuts
    var persistedCustomShortcuts by remember {
        mutableStateOf(ShortcutStorage.getCustomShortcuts(context))
    }

    val primary = remember { getDefaultPrimaryShortcuts() }
    val extra = remember { getDefaultExtraShortcuts() }

    // User-managed pinned URLs set with SharedPreferences persistence
    var pinnedUrls by remember {
        mutableStateOf(ShortcutStorage.getPinnedUrls(context, primary.map { it.url }.toSet()))
    }

    // Full list of all available shortcuts (primary + extra + custom + persistedCustom)
    val allShortcuts = remember(primary, extra, customShortcuts, persistedCustomShortcuts) {
        val combined = primary + extra + customShortcuts + persistedCustomShortcuts
        combined.distinctBy { it.url }
    }

    // Pinned shortcuts shown in top row
    val pinnedShortcuts = remember(allShortcuts, pinnedUrls) {
        val filtered = allShortcuts.filter { pinnedUrls.contains(it.url) }
        if (filtered.isNotEmpty()) filtered else primary
    }

    // Dialog & Options states
    var showAddDialog by remember { mutableStateOf(false) }
    var shortcutForOptions by remember { mutableStateOf<MenuShortcut?>(null) }
    var shortcutToEdit by remember { mutableStateOf<MenuShortcut?>(null) }
    var shortcutToDelete by remember { mutableStateOf<MenuShortcut?>(null) }

    // Filter & Search states
    var selectedCategory by remember { mutableStateOf("All") }
    var filterQuery by remember { mutableStateOf("") }
    var showQuickToolsOnly by remember { mutableStateOf(false) }

    // Gesture drag tracking
    var cardDragY by remember { mutableFloatStateOf(0f) }
    var handleDragY by remember { mutableFloatStateOf(0f) }

    // System Quick Actions
    val quickActions = remember {
        listOf(
            QuickActionItem("new_tab", "New Tab", Icons.Rounded.Add, Color(0xFF38BDF8)),
            QuickActionItem("private_tab", "Incognito", Icons.Rounded.Security, Color(0xFFA78BFA)),
            QuickActionItem("ai_hub", "AI Hub", Icons.Rounded.AutoAwesome, Color(0xFFF472B6)),
            QuickActionItem("terminal", "Terminal", Icons.Rounded.Terminal, Color(0xFF34D399)),
            QuickActionItem("bookmarks", "Bookmarks", Icons.Rounded.BookmarkBorder, Color(0xFFFBBF24)),
            QuickActionItem("history", "History", Icons.Rounded.History, Color(0xFF60A5FA)),
            QuickActionItem("downloads", "Downloads", Icons.Rounded.Download, Color(0xFF818CF8)),
            QuickActionItem("drive", "Cloud Drive", Icons.Rounded.CloudSync, Color(0xFF2DD4BF))
        )
    }

    // Filtered shortcuts based on category and search query
    val filteredShortcuts = remember(allShortcuts, selectedCategory, filterQuery, pinnedUrls) {
        allShortcuts.filter { shortcut ->
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Pinned" -> pinnedUrls.contains(shortcut.url)
                "Dev & AI" -> shortcut.category.equals("Dev", ignoreCase = true) || shortcut.title.contains("Git", ignoreCase = true) || shortcut.title.contains("AI", ignoreCase = true) || shortcut.title.contains("Stack", ignoreCase = true)
                "Social" -> shortcut.category.equals("Social", ignoreCase = true) || shortcut.title.contains("Reddit", ignoreCase = true) || shortcut.title.contains("X", ignoreCase = true) || shortcut.title.contains("LinkedIn", ignoreCase = true)
                "Media" -> shortcut.category.equals("Media", ignoreCase = true) || shortcut.title.contains("YouTube", ignoreCase = true) || shortcut.title.contains("Netflix", ignoreCase = true) || shortcut.title.contains("Spotify", ignoreCase = true) || shortcut.title.contains("Twitch", ignoreCase = true)
                "News" -> shortcut.category.equals("News", ignoreCase = true) || shortcut.title.contains("Financial", ignoreCase = true) || shortcut.title.contains("ESPN", ignoreCase = true) || shortcut.title.contains("Wikipedia", ignoreCase = true)
                "Shopping" -> shortcut.category.equals("Shopping", ignoreCase = true) || shortcut.title.contains("Flipkart", ignoreCase = true) || shortcut.title.contains("Amazon", ignoreCase = true)
                else -> true
            }

            val matchesQuery = if (filterQuery.isBlank()) true else {
                shortcut.title.contains(filterQuery, ignoreCase = true) || shortcut.url.contains(filterQuery, ignoreCase = true)
            }

            matchesCategory && matchesQuery
        }
    }

    val categories = remember(allShortcuts, pinnedShortcuts, quickActions) {
        listOf(
            "All" to allShortcuts.size,
            "Pinned" to pinnedShortcuts.size,
            "Quick Tools" to quickActions.size,
            "Dev & AI" to allShortcuts.count { it.category == "Dev" || it.title.contains("AI") },
            "Social" to allShortcuts.count { it.category == "Social" },
            "Media" to allShortcuts.count { it.category == "Media" },
            "News" to allShortcuts.count { it.category == "News" },
            "Shopping" to allShortcuts.count { it.category == "Shopping" }
        )
    }

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
        border = BorderStroke(1.dp, Color(0xFF243042)),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // -----------------------------------------------------------------
            // 1. PIN SHORTCUTS ROW (Horizontally Scrollable Carousel)
            // -----------------------------------------------------------------
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                    onLongClick = { shortcutForOptions = shortcut }
                                )
                                .padding(horizontal = 3.dp, vertical = 3.dp)
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

                            Spacer(modifier = Modifier.height(4.dp))

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
                            .clickable { showAddDialog = true }
                            .padding(horizontal = 3.dp, vertical = 3.dp)
                            .widthIn(min = 58.dp, max = 74.dp)
                            .testTag("${testTagPrefix}_add_new")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF263244))
                                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add new shortcut",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
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
            // 2. PULL / TAP TO EXPAND HANDLE (View all & Pull to expand)
            // -----------------------------------------------------------------
            val handleChevronRotation by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "handle_chevron"
            )

            Surface(
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
                color = Color.Transparent
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF475569))
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Show less" else "View all • Pull to expand",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "View all",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(handleChevronRotation)
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // 3. EXPANDING DYNAMIC & SCROLLABLE ALL SHORTCUTS PANEL
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
                        .padding(top = 2.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(
                        color = Color(0xFF243042).copy(alpha = 0.7f),
                        thickness = 1.dp
                    )

                    // A. DYNAMIC HORIZONTALLY SCROLLABLE CATEGORY PILLS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        categories.forEach { (catName, count) ->
                            val isSelected = selectedCategory == catName
                            Surface(
                                onClick = { selectedCategory = catName },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E2838),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF2D3B4E)
                                ),
                                modifier = Modifier.heightIn(min = 30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = catName,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(if (isSelected) Color(0x33FFFFFF) else Color(0xFF2B374A))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = "$count",
                                            fontSize = 9.5.sp,
                                            color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // B. LIVE SEARCH BAR IN SHORTCUT QUICK PANEL
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF10141D),
                        border = BorderStroke(0.8.dp, Color(0xFF263244))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search shortcuts",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                if (filterQuery.isEmpty()) {
                                    Text(
                                        text = "Filter ${filteredShortcuts.size} shortcuts...",
                                        color = Color(0xFF64748B),
                                        fontSize = 12.sp
                                    )
                                }
                                androidx.compose.foundation.text.BasicTextField(
                                    value = filterQuery,
                                    onValueChange = { filterQuery = it },
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = GVONETextPrimary,
                                        fontSize = 12.sp
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (filterQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .clickable { filterQuery = "" }
                                )
                            }
                        }
                    }

                    // C. SCROLLABLE VERTICAL CONTAINER FOR SHORTCUTS GRID
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (selectedCategory == "Quick Tools") {
                            val chunkedTools = remember(quickActions) { quickActions.chunked(4) }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                chunkedTools.forEach { rowTools ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        rowTools.forEach { action ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        onQuickActionClick?.invoke(action.id) ?: run {
                                                            Toast.makeText(context, action.title, Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                    .padding(horizontal = 3.dp, vertical = 3.dp)
                                                    .widthIn(min = 58.dp, max = 74.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(action.color.copy(alpha = 0.2f))
                                                        .border(1.dp, action.color.copy(alpha = 0.5f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = action.icon,
                                                        contentDescription = action.title,
                                                        tint = action.color,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = action.title,
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                        repeat(4 - rowTools.size) {
                                            Spacer(modifier = Modifier.width(74.dp))
                                        }
                                    }
                                }
                            }
                        } else if (filteredShortcuts.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SearchOff,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "No shortcuts found for \"$filterQuery\"",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        } else {
                            val chunkedShortcuts = remember(filteredShortcuts) {
                                filteredShortcuts.chunked(4)
                            }

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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
                                                onLongClick = { shortcutForOptions = shortcut },
                                                testTagPrefix = testTagPrefix
                                            )
                                        }
                                        repeat(4 - rowShortcuts.size) {
                                            Spacer(modifier = Modifier.width(74.dp))
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

    // -----------------------------------------------------------------
    // 4. SHORTCUT OPTIONS / PIN TOGGLE DIALOG (on long press)
    // -----------------------------------------------------------------
    if (shortcutForOptions != null) {
        val target = shortcutForOptions!!
        val isCurrentlyPinned = pinnedUrls.contains(target.url)
        val isCustom = (customShortcuts + persistedCustomShortcuts).any { it.url == target.url }

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
                            val nextPinned = if (isCurrentlyPinned) {
                                pinnedUrls - target.url
                            } else {
                                pinnedUrls + target.url
                            }
                            pinnedUrls = nextPinned
                            ShortcutStorage.savePinnedUrls(context, nextPinned)
                            val actionStr = if (isCurrentlyPinned) "Unpinned from top row" else "Pinned to top row"
                            Toast.makeText(context, actionStr, Toast.LENGTH_SHORT).show()
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

                    // Edit Option
                    Surface(
                        onClick = {
                            shortcutToEdit = target
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
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Edit shortcut",
                                color = GVONETextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Delete Option (if custom or extra shortcut)
                    if (isCustom || onRemoveShortcut != null) {
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
                                    text = "Delete shortcut",
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
    // 5. ADD SHORTCUT DIALOG
    // -----------------------------------------------------------------
    if (showAddDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newUrl by remember { mutableStateOf("") }
        var selectedNewCategory by remember { mutableStateOf("Dev") }

        val presetSuggestions = listOf(
            Triple("ChatGPT", "https://chatgpt.com", Brush.linearGradient(listOf(Color(0xFF10A37F), Color(0xFF0D8A6A)))),
            Triple("Claude", "https://claude.ai", Brush.linearGradient(listOf(Color(0xFFD97706), Color(0xFFB45309)))),
            Triple("Perplexity", "https://perplexity.ai", Brush.linearGradient(listOf(Color(0xFF20B2AA), Color(0xFF008080)))),
            Triple("YouTube", "https://youtube.com", Brush.linearGradient(listOf(Color(0xFFFF0000), Color(0xFF990000)))),
            Triple("Google", "https://google.com", Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF34A853)))),
            Triple("GitHub", "https://github.com", Brush.linearGradient(listOf(Color(0xFF6B21A8), Color(0xFF9333EA)))),
            Triple("X (Twitter)", "https://x.com", Brush.linearGradient(listOf(Color(0xFF14171A), Color(0xFF657786)))),
            Triple("Spotify", "https://spotify.com", Brush.linearGradient(listOf(Color(0xFF1DB954), Color(0xFF191414))))
        )

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = Color(0xFF141A24),
            title = {
                Text(
                    text = "Add Shortcut to Quick Panel",
                    color = GVONETextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Quick Presets",
                        color = GVONETextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Presets Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presetSuggestions.forEach { (name, url, brush) ->
                            Surface(
                                onClick = {
                                    newTitle = name
                                    newUrl = url
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E2838),
                                border = BorderStroke(1.dp, Color(0xFF2E3D52))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
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
                        placeholder = { Text("e.g. Claude AI", color = GVONETextSecondary.copy(alpha = 0.6f)) },
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

                    // Category Selector Pills
                    Text(
                        text = "Category",
                        color = GVONETextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Dev", "Social", "Media", "News", "Shopping", "General").forEach { cat ->
                            val isSelected = selectedNewCategory == cat
                            Surface(
                                onClick = { selectedNewCategory = cat },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E2838),
                                border = BorderStroke(0.6.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF2E3D52))
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
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
                            textColor = Color.White,
                            category = selectedNewCategory
                        )

                        // Save to callback and local persistent storage
                        onAddCustomShortcut?.invoke(createdShortcut)
                        val updatedPersisted = persistedCustomShortcuts + createdShortcut
                        persistedCustomShortcuts = updatedPersisted
                        ShortcutStorage.saveCustomShortcuts(context, updatedPersisted)

                        val nextPinned = pinnedUrls + createdShortcut.url
                        pinnedUrls = nextPinned
                        ShortcutStorage.savePinnedUrls(context, nextPinned)

                        showAddDialog = false
                        Toast.makeText(context, "Added shortcut: $title", Toast.LENGTH_SHORT).show()
                    },
                    enabled = newUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary)
                ) {
                    Text("Add to Panel")
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
    // 6. EDIT SHORTCUT DIALOG
    // -----------------------------------------------------------------
    shortcutToEdit?.let { target ->
        var editTitle by remember { mutableStateOf(target.title) }
        var editUrl by remember { mutableStateOf(target.url) }
        var editCategory by remember { mutableStateOf(target.category) }

        AlertDialog(
            onDismissRequest = { shortcutToEdit = null },
            containerColor = Color(0xFF141A24),
            title = {
                Text(
                    text = "Edit Shortcut",
                    color = GVONETextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title", color = GVONETextSecondary) },
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
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("URL", color = GVONETextSecondary) },
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
                        text = "Category",
                        color = GVONETextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Dev", "Social", "Media", "News", "Shopping", "General").forEach { cat ->
                            val isSelected = editCategory == cat
                            Surface(
                                onClick = { editCategory = cat },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E2838),
                                border = BorderStroke(0.6.dp, if (isSelected) Color(0xFF38BDF8) else Color(0xFF2E3D52))
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val initials = if (editTitle.length >= 2) editTitle.take(2).uppercase() else editTitle.take(1).uppercase()
                        val updated = target.copy(
                            title = editTitle,
                            url = editUrl,
                            initialLetters = initials,
                            category = editCategory
                        )

                        // Update in persisted list
                        val nextPersisted = persistedCustomShortcuts.map {
                            if (it.url == target.url) updated else it
                        }
                        persistedCustomShortcuts = nextPersisted
                        ShortcutStorage.saveCustomShortcuts(context, nextPersisted)

                        if (pinnedUrls.contains(target.url) && target.url != editUrl) {
                            val nextPinned = (pinnedUrls - target.url) + editUrl
                            pinnedUrls = nextPinned
                            ShortcutStorage.savePinnedUrls(context, nextPinned)
                        }

                        shortcutToEdit = null
                        Toast.makeText(context, "Shortcut updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary)
                ) {
                    Text("Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { shortcutToEdit = null }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            }
        )
    }

    // -----------------------------------------------------------------
    // 7. DELETE CONFIRMATION DIALOG
    // -----------------------------------------------------------------
    if (shortcutToDelete != null) {
        val target = shortcutToDelete!!
        AlertDialog(
            onDismissRequest = { shortcutToDelete = null },
            containerColor = Color(0xFF141A24),
            title = { Text("Remove Shortcut", color = GVONETextPrimary) },
            text = { Text("Do you want to remove \"${target.title}\" from the shortcut panel?", color = GVONETextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        val nextPinned = pinnedUrls - target.url
                        pinnedUrls = nextPinned
                        ShortcutStorage.savePinnedUrls(context, nextPinned)

                        val nextPersisted = persistedCustomShortcuts.filter { it.url != target.url }
                        persistedCustomShortcuts = nextPersisted
                        ShortcutStorage.saveCustomShortcuts(context, nextPersisted)

                        onRemoveShortcut?.invoke(target)
                        shortcutToDelete = null
                        Toast.makeText(context, "Shortcut removed", Toast.LENGTH_SHORT).show()
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

        Spacer(modifier = Modifier.height(4.dp))

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
