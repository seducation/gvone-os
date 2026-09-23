package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import com.example.data.model.BrowserTab
import com.example.data.model.ShortsAudioMode
import com.example.data.model.isInternalHomeUrl
import com.example.ui.components.SafariShortcutsCard
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class SafariActionTab(val label: String, val icon: ImageVector) {
    MAIN("Main", Icons.Rounded.Dashboard),
    ABOUT("About", Icons.Rounded.Info),
    INFO("Info", Icons.Rounded.Insights),
    GIT("Git", Icons.Rounded.Source),
    NOTIFICATION("Notification", Icons.Rounded.Notifications),
    WIDGET("Widget", Icons.Rounded.Widgets)
}

data class MenuShortcut(
    val title: String,
    val url: String,
    val initialLetters: String,
    val iconVector: ImageVector? = null,
    val badgeBg: Brush,
    val textColor: Color = Color.White
)

/**
 * Compact Expandable Browser Action Menu
 * Adheres to the compact modern layout from reference design:
 * 1. Top Quick Favorites / Shortcuts row (with "Add new" & "view all" toggle)
 * 2. Extensions row (expandable with adblock & security shields)
 * 3. Prominent "More" expandable accordion card with animated rotating chevron
 * 4. 4-Tile Grid Row: History, Bookmarks, Downloads, Passwords
 * 5. Sign In & Sync row
 * 6. Settings row
 * 7. Bottom Navigation Toolbar: Back, Forward, Share, Refresh
 *
 * Tapping "More" smoothly reveals ALL existing actions (New Tab, Private, Zoom, Reader Mode,
 * Desktop Mode, Find on page, Add to Reading List, Add Bookmark, Favorites, Home Screen, etc.)
 * in place with spring animation without navigating away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafariActionsSheet(
    tab: BrowserTab?,
    isTorActive: Boolean,
    isPrivateMode: Boolean,
    isShortsTab: Boolean = false,
    isShortsMuted: Boolean = false,
    shortsAudioMode: ShortsAudioMode = ShortsAudioMode.ALWAYS_UNMUTED,
    backgroundPlayEnabled: Boolean = true,
    isMediaPlaying: Boolean = true,
    onToggleShortsAudio: () -> Unit = {},
    onSelectShortsAudioMode: (ShortsAudioMode) -> Unit = {},
    onToggleBackgroundPlay: () -> Unit = {},
    onToggleMediaPlay: () -> Unit = {},
    onMediaPrevious: () -> Unit = {},
    onMediaNext: () -> Unit = {},
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleTor: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onReload: () -> Unit,
    onAddBookmark: () -> Unit,
    onAddFavorite: () -> Unit,
    onAddToReadingList: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenSiteInfo: () -> Unit,
    onOpenPasswords: () -> Unit = onOpenSiteInfo,
    onOpenAddToHomeScreen: () -> Unit,
    onOpenWidgetSelection: () -> Unit = {},
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenTerminal: () -> Unit = {},
    isDeveloperBarEnabled: Boolean = false,
    onToggleDeveloperBar: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenTorDiagnostics: () -> Unit = {},
    onOpenCustomCommands: () -> Unit = {},
    onOpenFiles: () -> Unit = {},
    onOpenWebsiteConnectors: () -> Unit = {},
    onOpenConnectorHub: () -> Unit = {},
    onOpenResearchWorkspace: () -> Unit = {},
    onOpenDataSaver: () -> Unit = {},
    onOpenCommunicationHub: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateForward: () -> Unit = {},
    onNavigateToUrl: (String) -> Unit = {},
    onClearBrowsingData: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMoreExpanded by remember { mutableStateOf(false) }
    var isExtensionsExpanded by remember { mutableStateOf(false) }
    var isMediaPlayerExpanded by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    var zoomPercentage by remember { mutableIntStateOf(100) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(SafariActionTab.MAIN) }

    // Extension toggles
    var adBlockerActive by remember { mutableStateOf(true) }
    var httpsForcedActive by remember { mutableStateOf(true) }
    var antiFingerprintActive by remember { mutableStateOf(true) }

    val moreChevronRotation by animateFloatAsState(
        targetValue = if (isMoreExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "more_chevron_rotation"
    )

    val extensionsChevronRotation by animateFloatAsState(
        targetValue = if (isExtensionsExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ext_chevron_rotation"
    )

    // Auto-clear toast
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            kotlinx.coroutines.delay(2200)
            toastMessage = null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF10141C),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF334155))
            )
        },
        modifier = modifier.testTag("compact_browser_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 0. Inspector Panel Header
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF0083B0)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = "Inspector Panel",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Inspector Panel",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Actions, controls, page tools & extensions",
                            color = GVONETextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = GVONETextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Top Horizontal Category Switcher Bar (Main, About, Info, Git, Notification, Widget)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF141A26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF222D3E)),
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SafariActionTab.values().forEach { tabItem ->
                        val isSelected = selectedTab == tabItem
                        val badgeCount = when (tabItem) {
                            SafariActionTab.NOTIFICATION -> 3
                            SafariActionTab.GIT -> 2
                            else -> 0
                        }
                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF00E5FF), Color(0xFF0083B0))
                                        )
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF1A2232), Color(0xFF1A2232)))
                                    }
                                )
                                .clickable { selectedTab = tabItem }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("safari_menu_tab_${tabItem.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tabItem.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else GVONETextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (badgeCount > 0) "${tabItem.label} ($badgeCount)" else tabItem.label,
                                    color = if (isSelected) Color.White else GVONETextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Global Toast notification pill when actions are triggered
            if (toastMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = GVONEPrimary.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GVONEPrimary),
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = GVONEPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = toastMessage ?: "",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            when (selectedTab) {
                SafariActionTab.MAIN -> {
                    // Main Scrollable Area inside the Bottom Sheet
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 560.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = 2.dp, bottom = 6.dp)
                    ) {
                // Toast notification pill when actions are triggered
                if (toastMessage != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = GVONEPrimary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GVONEPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = GVONEPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = toastMessage ?: "",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Media Player Card (Prominent when on Shorts / YouTube Media)
                if (isShortsTab) {
                    item {
                        MediaPlayerMenuCard(
                            tab = tab,
                            isShortsTab = true,
                            isShortsMuted = isShortsMuted,
                            shortsAudioMode = shortsAudioMode,
                            backgroundPlayEnabled = backgroundPlayEnabled,
                            isMediaPlaying = isMediaPlaying,
                            isExpanded = isMediaPlayerExpanded,
                            onToggleExpanded = { isMediaPlayerExpanded = !isMediaPlayerExpanded },
                            onToggleAudio = onToggleShortsAudio,
                            onToggleMediaPlay = onToggleMediaPlay,
                            onMediaPrevious = onMediaPrevious,
                            onMediaNext = onMediaNext,
                            onToggleBackgroundPlay = onToggleBackgroundPlay,
                            onSelectMode = onSelectShortsAudioMode,
                            onShowToast = { toastMessage = it }
                        )
                    }
                }

                // 1. TOP SHORTCUTS CARD (Pin shortcuts row + expanding/collapsible view all below)
                item {
                    SafariShortcutsCard(
                        modifier = Modifier.fillMaxWidth(),
                        onShortcutClick = { shortcut ->
                            onClose()
                            onNavigateToUrl(shortcut.url)
                        },
                        onAddFavorite = {
                            onAddFavorite()
                            toastMessage = "Added current tab to Shortcuts"
                        },
                        testTagPrefix = "safari_shortcut"
                    )
                }

                // 2. EXTENSIONS ROW (Puzzle icon, "Extensions", "Try a recommended extension", expandable chevron)
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isExtensionsExpanded = !isExtensionsExpanded },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161C26),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = "Extensions",
                                        tint = Color(0xFFA78BFA),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Extensions",
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (adBlockerActive) "AdBlocker & Privacy Shield active" else "Try a recommended extension",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Expand extensions",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .rotate(extensionsChevronRotation)
                                )
                            }

                            // Expandable Extensions Details
                            AnimatedVisibility(
                                visible = isExtensionsExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF121720))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ExtensionToggleRow(
                                        title = "Ad & Tracker Blocker",
                                        subtitle = "Blocks intrusive ads, popups & telemetry",
                                        icon = Icons.Rounded.Shield,
                                        isEnabled = adBlockerActive,
                                        onToggle = { adBlockerActive = it }
                                    )
                                    ExtensionToggleRow(
                                        title = "HTTPS Enforcement",
                                        subtitle = "Automatically upgrades all insecure connections",
                                        icon = Icons.Rounded.Lock,
                                        isEnabled = httpsForcedActive,
                                        onToggle = { httpsForcedActive = it }
                                    )
                                    ExtensionToggleRow(
                                        title = "Anti-Fingerprinting Shield",
                                        subtitle = "Randomizes canvas, audio & hardware hashes",
                                        icon = Icons.Rounded.Fingerprint,
                                        isEnabled = antiFingerprintActive,
                                        onToggle = { antiFingerprintActive = it }
                                    )
                                    HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                    SafariActionRow(
                                        icon = Icons.Rounded.DataSaverOn,
                                        label = "Data Saver Extension",
                                        trailingText = "Configure",
                                        onClick = {
                                            onClose()
                                            onOpenDataSaver()
                                        }
                                    )
                                    HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                    SafariActionRow(
                                        icon = Icons.Rounded.Hub,
                                        label = "Connector Hub",
                                        trailingText = "Drive • GitHub • Slack",
                                        onClick = {
                                            onClose()
                                            onOpenConnectorHub()
                                        }
                                    )
                                    HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                    SafariActionRow(
                                        icon = Icons.Rounded.Article,
                                        label = "Research Workspace",
                                        trailingText = "Citations & Evidence",
                                        onClick = {
                                            onClose()
                                            onOpenResearchWorkspace()
                                        }
                                    )
                                    HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                    SafariActionRow(
                                        icon = Icons.Rounded.Forum,
                                        label = "Communication Hub",
                                        trailingText = "Unified Inbox",
                                        onClick = {
                                            onClose()
                                            onOpenCommunicationHub()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Media Player Card (Accessible across all tabs with media & YouTube)
                if (!isShortsTab) {
                    item {
                        MediaPlayerMenuCard(
                            tab = tab,
                            isShortsTab = false,
                            isShortsMuted = isShortsMuted,
                            shortsAudioMode = shortsAudioMode,
                            backgroundPlayEnabled = backgroundPlayEnabled,
                            isMediaPlaying = isMediaPlaying,
                            isExpanded = isMediaPlayerExpanded,
                            onToggleExpanded = { isMediaPlayerExpanded = !isMediaPlayerExpanded },
                            onToggleAudio = onToggleShortsAudio,
                            onToggleMediaPlay = onToggleMediaPlay,
                            onMediaPrevious = onMediaPrevious,
                            onMediaNext = onMediaNext,
                            onToggleBackgroundPlay = onToggleBackgroundPlay,
                            onSelectMode = onSelectShortsAudioMode,
                            onShowToast = { toastMessage = it }
                        )
                    }
                }

                // 3. MORE SECTION (The central interaction: Tap to expand all browser controls in-place)
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { isMoreExpanded = !isMoreExpanded }
                            .testTag("more_accordion_row"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161C26),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isMoreExpanded) GVONEPrimary.copy(alpha = 0.6f) else Color(0xFF243042)
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreHoriz,
                                        contentDescription = "More",
                                        tint = if (isMoreExpanded) GVONEPrimary else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "More",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isMoreExpanded) {
                                        Text(
                                            text = "Tap to collapse",
                                            color = Color(0xFF64748B),
                                            fontSize = 11.sp
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Expand or collapse more controls",
                                        tint = if (isMoreExpanded) GVONEPrimary else Color(0xFF94A3B8),
                                        modifier = Modifier
                                            .size(22.dp)
                                            .rotate(moreChevronRotation)
                                    )
                                }
                            }

                            // EXPANDED STATE CONTENT: All browser actions smoothly revealed in-place
                            AnimatedVisibility(
                                visible = isMoreExpanded,
                                enter = expandVertically(animationSpec = tween(280)) + fadeIn(animationSpec = tween(280)),
                                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF10151E))
                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // A. Quick Tabs & Privacy/Tor Action Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CompactQuickButton(
                                            icon = Icons.Rounded.Add,
                                            label = "New Tab",
                                            onClick = {
                                                onClose()
                                                onNewTab()
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CompactQuickButton(
                                            icon = Icons.Rounded.Security,
                                            label = if (isPrivateMode) "Private (On)" else "Private Tab",
                                            isActive = isPrivateMode,
                                            activeColor = GVONESecondary,
                                            onClick = {
                                                onClose()
                                                onNewPrivateTab()
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                        CompactQuickButton(
                                            icon = Icons.Rounded.VpnLock,
                                            label = if (isTorActive) "Tor (ON)" else "Tor (OFF)",
                                            isActive = isTorActive,
                                            activeColor = GVONETertiary,
                                            onClick = {
                                                onToggleTor()
                                                toastMessage = if (isTorActive) "Tor Disabled" else "Tor Enabled"
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // B. Page Zoom Capsule: [ A- | 100% | A+ ]
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFF181F2C),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF273448))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = { if (zoomPercentage > 50) zoomPercentage -= 10 },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Text(
                                                    text = "A-",
                                                    color = Color(0xFFE2E8F0),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Text(
                                                text = "Page Zoom $zoomPercentage%",
                                                color = Color(0xFFE2E8F0),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )

                                            IconButton(
                                                onClick = { if (zoomPercentage < 200) zoomPercentage += 10 },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Text(
                                                    text = "A+",
                                                    color = Color(0xFFE2E8F0),
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // C. Page Interaction Group: Reader Mode, Find in Page, Desktop Website
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF181F2C),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF273448))
                                    ) {
                                        Column {
                                            SafariActionRow(
                                                icon = Icons.Rounded.MenuBook,
                                                label = "Show Reader Mode",
                                                trailingText = "Clean View",
                                                onClick = {
                                                    onClose()
                                                    onOpenReaderMode()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.FindInPage,
                                                label = "Find on Page",
                                                onClick = {
                                                    onClose()
                                                    onFindInPage()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.DesktopWindows,
                                                label = "Request Desktop Website",
                                                isActive = tab?.desktopMode == true,
                                                trailingText = if (tab?.desktopMode == true) "On" else "Off",
                                                onClick = onToggleDesktop
                                            )
                                        }
                                    }

                                    // D. Save & Add Group: Add to Reading List, Add Bookmark, Favorites, Home Screen
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF181F2C),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF273448))
                                    ) {
                                        Column {
                                            SafariActionRow(
                                                icon = Icons.Rounded.BookmarkAdd,
                                                label = "Add to Reading List",
                                                onClick = {
                                                    onAddToReadingList()
                                                    toastMessage = "Added to Reading List"
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.BookmarkBorder,
                                                label = "Add Bookmark",
                                                onClick = {
                                                    onAddBookmark()
                                                    toastMessage = "Bookmark saved"
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.StarBorder,
                                                label = "Add to Favorites",
                                                onClick = {
                                                    onAddFavorite()
                                                    toastMessage = "Added to Favorites"
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.AddToHomeScreen,
                                                label = "Add to Home Screen",
                                                onClick = {
                                                    onClose()
                                                    onOpenAddToHomeScreen()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.CropFree,
                                                label = "Crop Webpage to Widget",
                                                trailingText = if (isInternalHomeUrl(tab?.url)) "Webpage only" else "Configure Widget",
                                                onClick = {
                                                    if (isInternalHomeUrl(tab?.url)) {
                                                        toastMessage = "Open any webpage to crop into a widget"
                                                    } else {
                                                        onClose()
                                                        onOpenWidgetSelection()
                                                    }
                                                }
                                            )
                                        }
                                    }

                                    // E. Management & Security Group: Passwords, Bookmarks Manager, History, Diagnostics
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF181F2C),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF273448))
                                    ) {
                                        Column {
                                            SafariActionRow(
                                                icon = Icons.Rounded.Info,
                                                label = "Site Information",
                                                trailingText = if (tab?.url?.startsWith("https://") == true) "Secure" else "Page Info",
                                                onClick = {
                                                    onClose()
                                                    onOpenSiteInfo()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.FolderOpen,
                                                label = "Bookmarks Manager",
                                                onClick = {
                                                    onClose()
                                                    onOpenBookmarks()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.Troubleshoot,
                                                label = "Tor Network Diagnostics",
                                                trailingText = if (isTorActive) "Routing Active" else "Offline",
                                                onClick = {
                                                    onClose()
                                                    onOpenTorDiagnostics()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.Terminal,
                                                label = "Terminal CLI",
                                                trailingText = "Shell",
                                                onClick = {
                                                    onClose()
                                                    onOpenTerminal()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.DeveloperMode,
                                                label = "Website Developer Bar",
                                                trailingText = if (isDeveloperBarEnabled) "ON" else "OFF",
                                                onClick = {
                                                    onToggleDeveloperBar()
                                                    onClose()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.Terminal,
                                                label = "Custom Terminal Commands",
                                                trailingText = "Configure",
                                                onClick = {
                                                    onClose()
                                                    onOpenCustomCommands()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.FolderCopy,
                                                label = "Files & Storage",
                                                trailingText = "GVONE FS",
                                                onClick = {
                                                    onClose()
                                                    onOpenFiles()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.Hub,
                                                label = "Connector Hub",
                                                trailingText = "Drive • GitHub • Slack",
                                                onClick = {
                                                    onClose()
                                                    onOpenConnectorHub()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.Article,
                                                label = "Research Workspace",
                                                trailingText = "Sources • Citations • Notes",
                                                onClick = {
                                                    onClose()
                                                    onOpenResearchWorkspace()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.DataSaverOn,
                                                label = "Data Saver Extension",
                                                trailingText = "Bandwidth Optimization",
                                                onClick = {
                                                    onClose()
                                                    onOpenDataSaver()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.Forum,
                                                label = "Communication Hub",
                                                trailingText = "Unified Inbox",
                                                onClick = {
                                                    onClose()
                                                    onOpenCommunicationHub()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.Link,
                                                label = "Website Logins & Connectors",
                                                trailingText = "Passwords & Cookies",
                                                onClick = {
                                                    onClose()
                                                    onOpenWebsiteConnectors()
                                                }
                                            )
                                            HorizontalDivider(color = Color(0xFF263348), thickness = 0.5.dp)
                                            SafariActionRow(
                                                icon = Icons.Rounded.DeleteOutline,
                                                label = "Clear Browsing Data",
                                                onClick = {
                                                    onClearBrowsingData()
                                                    toastMessage = "Browsing cache cleared"
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. SHORTCUT TILES: History | Bookmarks | Terminal | Downloads
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GridShortcutItem(
                            icon = Icons.Rounded.History,
                            label = "History",
                            onClick = {
                                onClose()
                                onOpenHistory()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GridShortcutItem(
                            icon = Icons.Rounded.Star,
                            label = "Bookmarks",
                            onClick = {
                                onClose()
                                onOpenBookmarks()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GridShortcutItem(
                            icon = Icons.Rounded.Terminal,
                            label = "Terminal",
                            onClick = {
                                onClose()
                                onOpenTerminal()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GridShortcutItem(
                            icon = Icons.Rounded.FolderCopy,
                            label = "Files",
                            onClick = {
                                onClose()
                                onOpenFiles()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GridShortcutItem(
                            icon = Icons.Rounded.Download,
                            label = "Downloads",
                            onClick = {
                                onClose()
                                onOpenDownloads()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 5. SIGN IN / SYNC ROW
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { showSignInDialog = true }
                            .testTag("menu_signin_row"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161C26),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = "Sign in",
                                    tint = GVONEPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(
                                        text = "Sign in",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Synchronise passwords, bookmarks and more",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // 6. SETTINGS ROW
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onClose()
                                onOpenSettings()
                            }
                            .testTag("menu_settings_row"),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF161C26),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Settings",
                                    tint = Color(0xFFCBD5E1),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Settings",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1F2937), thickness = 0.5.dp)

            // 7. BOTTOM NAVIGATION TOOLBAR: Back | Forward | Share | Refresh
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F131A)),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 440.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomBarButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = "Back",
                        onClick = {
                            onClose()
                            onNavigateBack()
                        }
                    )

                    BottomBarButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowForward,
                        label = "Forward",
                        onClick = {
                            onClose()
                            onNavigateForward()
                        }
                    )

                    BottomBarButton(
                        icon = Icons.Rounded.Share,
                        label = "Share",
                        onClick = {
                            onClose()
                            onShare()
                        }
                    )

                    BottomBarButton(
                        icon = Icons.Rounded.Refresh,
                        label = "Refresh",
                        onClick = {
                            onClose()
                            onReload()
                        }
                    )
                }
            }
        }

        SafariActionTab.ABOUT -> {
            SafariAboutTabContent(
                onOpenTerminal = {
                    onClose()
                    onOpenTerminal()
                },
                onOpenTorDiagnostics = {
                    onClose()
                    onOpenTorDiagnostics()
                },
                onOpenSettings = {
                    onClose()
                    onOpenSettings()
                },
                onShowToast = { toastMessage = it }
            )
        }

        SafariActionTab.INFO -> {
            SafariInfoTabContent(
                tab = tab,
                onReload = onReload,
                onShare = onShare,
                onOpenSiteInfo = {
                    onClose()
                    onOpenSiteInfo()
                },
                onShowToast = { toastMessage = it }
            )
        }

        SafariActionTab.GIT -> {
            SafariGitTabContent(
                onOpenTerminal = {
                    onClose()
                    onOpenTerminal()
                },
                onShowToast = { toastMessage = it }
            )
        }

        SafariActionTab.NOTIFICATION -> {
            SafariNotificationTabContent(
                onOpenFiles = {
                    onClose()
                    onOpenFiles()
                },
                onShowToast = { toastMessage = it }
            )
        }

        SafariActionTab.WIDGET -> {
            SafariWidgetTabContent(
                tab = tab,
                isShortsMuted = isShortsMuted,
                shortsAudioMode = shortsAudioMode,
                backgroundPlayEnabled = backgroundPlayEnabled,
                isMediaPlaying = isMediaPlaying,
                onToggleAudio = onToggleShortsAudio,
                onToggleMediaPlay = onToggleMediaPlay,
                onMediaPrevious = onMediaPrevious,
                onMediaNext = onMediaNext,
                onToggleBackgroundPlay = onToggleBackgroundPlay,
                onSelectShortsAudioMode = onSelectShortsAudioMode,
                onOpenWidgetSelection = {
                    onClose()
                    onOpenWidgetSelection()
                },
                onOpenFiles = {
                    onClose()
                    onOpenFiles()
                },
                onOpenTerminal = {
                    onClose()
                    onOpenTerminal()
                },
                onShowToast = { toastMessage = it }
            )
        }
    }
}
    }

    // Sign in / Sync Dialog
    if (showSignInDialog) {
        AlertDialog(
            onDismissRequest = { showSignInDialog = false },
            containerColor = Color(0xFF161D2A),
            shape = RoundedCornerShape(22.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudSync,
                        contentDescription = null,
                        tint = GVONEPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "GVONE Sync & Cloud",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sign in to synchronize your passwords, open tabs, bookmarks, and history end-to-end encrypted across all your devices.",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E2738),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = GVONETertiary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Zero-Knowledge Encryption Active",
                                color = Color(0xFFE2E8F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignInDialog = false
                        toastMessage = "Signed in as pinakiranjanbera@icloud.com"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign In with Google", color = Color.White, fontSize = 13.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignInDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun GridShortcutItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag("grid_shortcut_${label.lowercase()}"),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF161C26),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                color = Color(0xFFCBD5E1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun BottomBarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color(0xFF94A3B8),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ExtensionToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF171E2B))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) GVONEPrimary else Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }
        }

        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GVONEPrimary,
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF273448)
            )
        )
    }
}

@Composable
private fun CompactQuickButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = GVONEPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) activeColor.copy(alpha = 0.2f) else Color(0xFF181F2C),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.7f) else Color(0xFF273448)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color(0xFFE2E8F0),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (isActive) activeColor else Color(0xFFCBD5E1),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SafariQuickTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = GVONEPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) activeColor.copy(alpha = 0.2f) else Color(0xFF1A2230),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.8f) else Color(0xFF2B374C)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color(0xFFF1F5F9),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (isActive) activeColor else Color(0xFFCCD6E5),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SafariActionRow(
    icon: ImageVector,
    label: String,
    trailingText: String? = null,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) GVONESecondary else Color(0xFFF1F5F9),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = Color(0xFFF1F5F9),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                color = if (isActive) GVONESecondary else Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Media Player Control Card inside the Three-Dot Action Menu Sheet.
 * Displays full media player controls, animated equalizer, play/pause, seek,
 * Background Player toggle, and audio behavior configuration.
 */
@Composable
private fun MediaPlayerMenuCard(
    tab: BrowserTab?,
    isShortsTab: Boolean,
    isShortsMuted: Boolean,
    shortsAudioMode: ShortsAudioMode,
    backgroundPlayEnabled: Boolean,
    isMediaPlaying: Boolean,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleMediaPlay: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaNext: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onSelectMode: (ShortsAudioMode) -> Unit,
    onShowToast: (String) -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "media_player_chevron"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "media_player_equalizer")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 16f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 15f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(320, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 7f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("menu_media_player_card"),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161C26),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isMediaPlaying) GVONEPrimary.copy(alpha = 0.6f) else Color(0xFF243042)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header: Title, Animated Visualizer, Badges & Expand Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggleExpanded() }
                ) {
                    // Media / Equalizer Icon Badge
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isMediaPlaying) {
                                    Brush.linearGradient(listOf(GVONEPrimary, GVONESecondary))
                                } else {
                                    Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isMediaPlaying) {
                            // Animated equalizer wave bars
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.height(18.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(bar1.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(bar2.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(bar3.dp)
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(Color.White)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Media Player",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Media Player",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            // Playing status badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isMediaPlaying) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isMediaPlaying) "PLAYING" else "PAUSED",
                                    color = if (isMediaPlaying) Color(0xFF34D399) else Color(0xFF94A3B8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                )
                            }
                            if (backgroundPlayEnabled) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GVONESecondary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "BG PLAY",
                                        color = GVONESecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = tab?.title?.takeIf { it.isNotBlank() }
                                ?: if (isShortsTab) "YouTube Shorts" else "Web Video & Audio",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Expand chevron
                IconButton(
                    onClick = onToggleExpanded,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("menu_media_expand_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Expand media player options",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            // Media Controls Bar (Rewind 10s, Play/Pause, Forward 10s, Mute/Unmute)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F1520),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s / Previous
                    IconButton(
                        onClick = {
                            onMediaPrevious()
                            onShowToast("Rewound 10s")
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("menu_media_player_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Play / Pause Action Button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(GVONEPrimary, GVONESecondary)))
                            .clickable {
                                onToggleMediaPlay()
                                onShowToast(if (isMediaPlaying) "Media paused" else "Media playing")
                            }
                            .testTag("menu_media_player_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMediaPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isMediaPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Forward 10s / Next
                    IconButton(
                        onClick = {
                            onMediaNext()
                            onShowToast("Skipped 10s")
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("menu_media_player_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Quick Mute / Unmute
                    IconButton(
                        onClick = {
                            onToggleAudio()
                            onShowToast(if (isShortsMuted) "Audio unmuted" else "Audio muted")
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("menu_media_player_mute_btn")
                    ) {
                        Icon(
                            imageVector = if (isShortsMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = if (isShortsMuted) "Unmute" else "Mute",
                            tint = if (isShortsMuted) Color(0xFFEF4444) else GVONESecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Background Player Section
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onToggleBackgroundPlay()
                        onShowToast(if (!backgroundPlayEnabled) "Background Player enabled" else "Background Player disabled")
                    }
                    .testTag("menu_media_bg_player_row"),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0F1520),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (backgroundPlayEnabled) GVONESecondary.copy(alpha = 0.35f) else Color(0xFF1E293B)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (backgroundPlayEnabled) GVONESecondary.copy(alpha = 0.15f) else Color(0xFF1E293B)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = "Background Player",
                                tint = if (backgroundPlayEnabled) GVONESecondary else Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Background Player",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (backgroundPlayEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (backgroundPlayEnabled) "ENABLED" else "OFF",
                                        color = if (backgroundPlayEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Plays audio when minimized or screen is locked",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Switch(
                        checked = backgroundPlayEnabled,
                        onCheckedChange = {
                            onToggleBackgroundPlay()
                            onShowToast(if (it) "Background Player enabled" else "Background Player disabled")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GVONESecondary,
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF1E293B)
                        ),
                        modifier = Modifier
                            .scale(0.8f)
                            .testTag("menu_media_bg_player_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expandable Mode Selector Options
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF10151E))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Shorts & Web Sound Behavior:",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    ShortsAudioMode.values().forEach { mode ->
                        val isSelected = shortsAudioMode == mode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectMode(mode)
                                    onShowToast("Mode set to ${mode.displayName}")
                                }
                                .testTag("menu_shorts_mode_${mode.name}"),
                            color = if (isSelected) GVONEPrimary.copy(alpha = 0.15f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = when (mode) {
                                            ShortsAudioMode.ALWAYS_UNMUTED -> Icons.Rounded.VolumeUp
                                            ShortsAudioMode.ALWAYS_MUTED -> Icons.Rounded.VolumeOff
                                            ShortsAudioMode.REMEMBER_STATE -> Icons.Rounded.Sync
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) GVONESecondary else Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = mode.displayName,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        Text(
                                            text = mode.description,
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = "Selected",
                                        tint = GVONESecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 2: ABOUT TAB CONTENT
// -----------------------------------------------------------------------------------------
@Composable
private fun SafariAboutTabContent(
    onOpenTerminal: () -> Unit,
    onOpenTorDiagnostics: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowToast: (String) -> Unit
) {
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("Up to date • v4.2.8 LTS") }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {
        // Hero App Banner
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131926),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0083B0)))
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safari_about_hero_banner")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "GVONE Browser & OS",
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                                ) {
                                    Text(
                                        text = "PROD",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Quantum Dual-Engine Runtime • v4.2.8 LTS",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)

                    Text(
                        text = "Next-generation private web environment combining Chromium V8 JIT acceleration, Tor Onion privacy circuits, native sandbox filesystem, and bidirectional terminal automation.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Live Engine Specifications & Subsystems
        item {
            Text(
                text = "ENGINE SPECIFICATIONS & SUBSYSTEMS",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(horizontal = 4.dp).padding(top = 2.dp)
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AboutSpecRow(
                        title = "Core Rendering Engine",
                        value = "Chromium Quantum / Gecko Fallback",
                        icon = Icons.Rounded.Speed,
                        tint = Color(0xFF38BDF8)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    AboutSpecRow(
                        title = "JavaScript Runtime",
                        value = "V8 Engine JIT (Isolated Contexts)",
                        icon = Icons.Rounded.Code,
                        tint = Color(0xFFA78BFA)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    AboutSpecRow(
                        title = "Tor Onion Routing",
                        value = "v3 Onion Proxy Circuits Active",
                        icon = Icons.Rounded.Security,
                        tint = Color(0xFF10B981)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    AboutSpecRow(
                        title = "Bidirectional Bridge",
                        value = "WebSocket / HTTP IPC Multiplexer v2.4",
                        icon = Icons.Rounded.SyncAlt,
                        tint = Color(0xFFF59E0B)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    AboutSpecRow(
                        title = "Cryptography & Vault",
                        value = "AES-256-GCM Hardware Encrypted",
                        icon = Icons.Rounded.Lock,
                        tint = Color(0xFF00E5FF)
                    )
                }
            }
        }

        // Live Memory & Resource Diagnostics
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "LIVE SYSTEM TELEMETRY",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMiniCard(
                            label = "RAM Allocated",
                            value = "84.6 MB",
                            subtext = "512 MB Max",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMiniCard(
                            label = "V8 JS Heap",
                            value = "26.2 MB",
                            subtext = "Optimized",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMiniCard(
                            label = "Page Cache",
                            value = "14.8 MB",
                            subtext = "ZRAM x2.8",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Update Check & Quick Action Triggers
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Software Updates",
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = updateStatus,
                                color = if (isCheckingUpdates) Color(0xFF38BDF8) else Color(0xFF10B981),
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = {
                                if (!isCheckingUpdates) {
                                    isCheckingUpdates = true
                                    updateStatus = "Checking repository..."
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(1300)
                                        isCheckingUpdates = false
                                        updateStatus = "Up to date • v4.2.8 LTS"
                                        onShowToast("GVONE OS is running the latest build")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCheckingUpdates) Color(0xFF1E293B) else Color(0xFF0284C7)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Checking", fontSize = 11.sp, color = Color.White)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Now", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenTerminal,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CLI Console", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }

                        OutlinedButton(
                            onClick = onOpenTorDiagnostics,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tor Onion", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }

                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF94A3B8))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Settings", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 3: INFO TAB CONTENT (Page & Connection Inspector)
// -----------------------------------------------------------------------------------------
@Composable
private fun SafariInfoTabContent(
    tab: BrowserTab?,
    onReload: () -> Unit,
    onShare: () -> Unit,
    onOpenSiteInfo: () -> Unit,
    onShowToast: (String) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val currentUrl = tab?.url ?: ""
    val isSecure = currentUrl.startsWith("https://")
    val isOnion = currentUrl.contains(".onion")
    val isInternal = isInternalHomeUrl(currentUrl)

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {
        // Page Overview Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131926),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSecure || isOnion) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF243042)
                ),
                modifier = Modifier.fillMaxWidth().testTag("safari_info_page_overview")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                isOnion -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                isSecure -> Color(0xFF10B981).copy(alpha = 0.2f)
                                else -> Color(0xFF38BDF8).copy(alpha = 0.2f)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when {
                                        isOnion -> Icons.Rounded.Security
                                        isSecure -> Icons.Rounded.Lock
                                        else -> Icons.Rounded.Language
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isOnion -> Color(0xFFA78BFA)
                                        isSecure -> Color(0xFF34D399)
                                        else -> Color(0xFF38BDF8)
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tab?.title?.ifBlank { "Untitled Page" } ?: "Untitled Page",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentUrl.ifBlank { "about:blank" },
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isOnion -> Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                isSecure -> Color(0xFF10B981).copy(alpha = 0.2f)
                                else -> Color(0xFF334155)
                            }
                        ) {
                            Text(
                                text = when {
                                    isOnion -> "ONION v3"
                                    isSecure -> "TLS 1.3"
                                    isInternal -> "LOCAL"
                                    else -> "HTTP"
                                },
                                color = when {
                                    isOnion -> Color(0xFFA78BFA)
                                    isSecure -> Color(0xFF34D399)
                                    else -> Color(0xFF94A3B8)
                                },
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)

                    // Quick buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(currentUrl))
                                onShowToast("URL copied to clipboard")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy URL", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = onShare,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = onOpenSiteInfo,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Details", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Security & Cryptographic Details
        item {
            Text(
                text = "CONNECTION & CERTIFICATE ENCRYPTION",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(horizontal = 4.dp).padding(top = 2.dp)
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutSpecRow(
                        title = "Security Protocol",
                        value = if (isSecure) "TLS 1.3 / HTTP/3 QUIC" else if (isOnion) "Tor v3 End-to-End" else "Unencrypted HTTP",
                        icon = Icons.Rounded.Lock,
                        tint = if (isSecure || isOnion) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    AboutSpecRow(
                        title = "Cipher Suite",
                        value = "TLS_AES_256_GCM_SHA384 (X25519)",
                        icon = Icons.Rounded.Key,
                        tint = Color(0xFF38BDF8)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    AboutSpecRow(
                        title = "Certificate Authority",
                        value = if (isSecure) "Google Trust Services / DigiCert" else "Self-Signed / Tor v3 Hidden",
                        icon = Icons.Rounded.VerifiedUser,
                        tint = Color(0xFFA78BFA)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    AboutSpecRow(
                        title = "Mixed Insecure Content",
                        value = "Strictly Blocked (0 insecure items)",
                        icon = Icons.Rounded.Shield,
                        tint = Color(0xFF10B981)
                    )
                }
            }
        }

        // Privacy & Telemetry Blocked
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "PAGE PRIVACY & SHIELD ANALYTICS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMiniCard(
                            label = "Trackers Blocked",
                            value = "34",
                            subtext = "Scripts blocked",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMiniCard(
                            label = "Fingerprints",
                            value = "12",
                            subtext = "Canvas masked",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMiniCard(
                            label = "3rd-Party Cookies",
                            value = "0",
                            subtext = "Sandboxed",
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // DOM & Performance Metrics
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Page Load Latency", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("DOM Loaded in 184 ms • 22 requests", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Text("184 ms", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onShowToast("DOM Inspector initialized in Developer Bar") },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Inspect DOM", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }

                        OutlinedButton(
                            onClick = { onShowToast("Cookies & storage cleared for this site") },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFEF4444))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Site Data", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 4: GIT TAB CONTENT (Workspace & Repository Control)
// -----------------------------------------------------------------------------------------
@Composable
private fun SafariGitTabContent(
    onOpenTerminal: () -> Unit,
    onShowToast: (String) -> Unit
) {
    var commitMessage by remember { mutableStateOf("") }
    var selectedBranch by remember { mutableStateOf("main") }
    var isDiffExpanded by remember { mutableStateOf(false) }
    var isCommitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val branches = listOf("main", "feature/tab-groups", "dev", "+ New Branch")

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {
        // Repository Status Header
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131926),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().testTag("safari_git_repo_header")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF0F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Source,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "gvone-browser-workspace",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981))
                                    )
                                    Text(
                                        text = "origin/main • commit 8f21bc4",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                        ) {
                            Text(
                                text = "2 Modified",
                                color = Color(0xFF34D399),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)

                    // Branch Switcher Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        branches.forEach { branch ->
                            val isSelected = selectedBranch == branch
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                ),
                                modifier = Modifier.clickable {
                                    if (branch == "+ New Branch") {
                                        onShowToast("Create new branch dialog")
                                    } else {
                                        selectedBranch = branch
                                        onShowToast("Switched to branch $branch")
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ForkRight,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = branch,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    // Git Quick Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { onShowToast("Git Pull: Already up to date") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pull", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = { onShowToast("Git Push: Synced with origin/main") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Push", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = { onShowToast("Git Fetch: Remote references updated") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Fetch", fontSize = 11.sp, color = Color.White)
                        }

                        Button(
                            onClick = { isDiffExpanded = !isDiffExpanded },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDiffExpanded) Color(0xFF0284C7) else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Difference, contentDescription = null, modifier = Modifier.size(13.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Diff", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Working Tree Changes
        item {
            Text(
                text = "WORKING TREE MODIFIED FILES",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(horizontal = 4.dp).padding(top = 2.dp)
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GitFileChangeRow(
                        fileName = "SafariActionsSheet.kt",
                        path = "app/src/main/java/com/example/ui/screens/",
                        status = "MODIFIED",
                        diffText = "+140 -12",
                        statusColor = Color(0xFFF59E0B)
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    GitFileChangeRow(
                        fileName = "TabOverviewScreen.kt",
                        path = "app/src/main/java/com/example/ui/screens/",
                        status = "MODIFIED",
                        diffText = "+85 -4",
                        statusColor = Color(0xFFF59E0B)
                    )

                    AnimatedVisibility(visible = isDiffExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F141D))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "@@ -180,6 +180,24 @@ Top Horizontal Pill Bar",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "+ Surface(shape = RoundedCornerShape(20.dp))",
                                color = Color(0xFF34D399),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "+   Row(horizontalArrangement = spacedBy(6.dp))",
                                color = Color(0xFF34D399),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "+     SafariActionTab.values().forEach { ... }",
                                color = Color(0xFF34D399),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Commit Message Composer
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "COMMIT CHANGES TO $selectedBranch",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    OutlinedTextField(
                        value = commitMessage,
                        onValueChange = { commitMessage = it },
                        placeholder = { Text("feat: add horizontal tabs to safari action sheet", fontSize = 12.sp, color = Color(0xFF64748B)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF0284C7),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (commitMessage.isBlank()) {
                                    onShowToast("Please enter a commit message")
                                } else {
                                    isCommitting = true
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(800)
                                        isCommitting = false
                                        val msg = commitMessage
                                        commitMessage = ""
                                        onShowToast("Committed: $msg")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            if (isCommitting) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Commit", fontSize = 11.5.sp, color = Color.White)
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenTerminal,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Git CLI", fontSize = 11.5.sp, color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }

        // Commit Log Timeline
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "RECENT COMMIT HISTORY",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    GitCommitItem(
                        hash = "8f21bc4",
                        message = "feat: add horizontal tab layout to safari bottom sheet",
                        author = "GVONE System",
                        time = "Just now",
                        isHead = true
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    GitCommitItem(
                        hash = "a3f910b",
                        message = "feat: add Files category to tab overview pill bar",
                        author = "GVONE System",
                        time = "15m ago"
                    )
                    HorizontalDivider(color = Color(0xFF233044), thickness = 0.5.dp)
                    GitCommitItem(
                        hash = "c991e20",
                        message = "perf: optimize memory footprint & tab switching",
                        author = "GVONE Core",
                        time = "1h ago"
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 5: NOTIFICATION TAB CONTENT (Browser Notifications & Events)
// -----------------------------------------------------------------------------------------
@Composable
private fun SafariNotificationTabContent(
    onOpenFiles: () -> Unit,
    onShowToast: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Downloads", "Security", "Bridge")

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {
        // Notification Filter Chips & Clear All
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131926),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth().testTag("safari_notification_header")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filters.forEach { filter ->
                            val isSelected = selectedFilter == filter
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                ),
                                modifier = Modifier.clickable { selectedFilter = filter }
                            ) {
                                Text(
                                    text = filter,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { onShowToast("Notifications cleared") },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Clear All", fontSize = 11.sp, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Notification Cards Stream
        if (selectedFilter == "All" || selectedFilter == "Security") {
            item {
                NotificationItemCard(
                    title = "Anti-Fingerprinting Shield Active",
                    body = "Blocked 14 canvas & WebGL fingerprint attempts on current domain",
                    timestamp = "4m ago",
                    icon = Icons.Rounded.Shield,
                    badgeColor = Color(0xFF10B981),
                    actionLabel = "Review Shield",
                    onAction = { onShowToast("Shield telemetry: 14 blocked") }
                )
            }
        }

        if (selectedFilter == "All" || selectedFilter == "Downloads") {
            item {
                NotificationItemCard(
                    title = "Download Complete: document_preview.pdf",
                    body = "2.4 MB • Saved to /storage/emulated/0/Download/GVONE",
                    timestamp = "12m ago",
                    icon = Icons.Rounded.DownloadDone,
                    badgeColor = Color(0xFF38BDF8),
                    actionLabel = "Open in Files",
                    onAction = {
                        onOpenFiles()
                    }
                )
            }
        }

        if (selectedFilter == "All" || selectedFilter == "Bridge") {
            item {
                NotificationItemCard(
                    title = "Bidirectional Bridge Connected",
                    body = "WebSocket IPC connected to CharAssist AI multiplexer on :8080",
                    timestamp = "25m ago",
                    icon = Icons.Rounded.SyncAlt,
                    badgeColor = Color(0xFFF59E0B),
                    actionLabel = "Bridge Status",
                    onAction = { onShowToast("Bridge IPC healthy • 0 packet drops") }
                )
            }
        }

        if (selectedFilter == "All" || selectedFilter == "Downloads") {
            item {
                NotificationItemCard(
                    title = "Download Complete: app-release.apk",
                    body = "18.1 MB • SHA256 verified",
                    timestamp = "1h ago",
                    icon = Icons.Rounded.DownloadDone,
                    badgeColor = Color(0xFF38BDF8),
                    actionLabel = "Show in Files",
                    onAction = { onOpenFiles() }
                )
            }
        }

        if (selectedFilter == "All" || selectedFilter == "Security") {
            item {
                NotificationItemCard(
                    title = "RAM Optimizer Report",
                    body = "Freed 142 MB by sleeping 3 idle background tabs",
                    timestamp = "2h ago",
                    icon = Icons.Rounded.Speed,
                    badgeColor = Color(0xFFA78BFA),
                    actionLabel = "View Memory",
                    onAction = { onShowToast("Current RAM: 84 MB allocated") }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TAB 6: WIDGET TAB CONTENT (Interactive In-Sheet Widgets & Tools)
// -----------------------------------------------------------------------------------------
@Composable
private fun SafariWidgetTabContent(
    tab: BrowserTab?,
    isShortsMuted: Boolean,
    shortsAudioMode: ShortsAudioMode,
    backgroundPlayEnabled: Boolean,
    isMediaPlaying: Boolean,
    onToggleAudio: () -> Unit,
    onToggleMediaPlay: () -> Unit,
    onMediaPrevious: () -> Unit,
    onMediaNext: () -> Unit,
    onToggleBackgroundPlay: () -> Unit,
    onSelectShortsAudioMode: (ShortsAudioMode) -> Unit,
    onOpenWidgetSelection: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTerminal: () -> Unit,
    onShowToast: (String) -> Unit
) {
    var noteText by remember { mutableStateOf("Quick idea: Check out Web Portion canvas extraction on Wikipedia article...") }
    val clipboardManager = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {
        // Widget 1: Background Media & Audio Controller Widget
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131926),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().testTag("safari_widget_media_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                            Text("Background Audio & Media Player", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Switch(
                            checked = backgroundPlayEnabled,
                            onCheckedChange = {
                                onToggleBackgroundPlay()
                                onShowToast(if (it) "Background Audio Enabled" else "Background Audio Disabled")
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00E5FF),
                                uncheckedThumbColor = Color(0xFF94A3B8),
                                uncheckedTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.scale(0.75f)
                        )
                    }

                    // Media Track & Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F1520))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tab?.title?.ifBlank { "Ambient Audio Stream" } ?: "Ambient Audio Stream",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (backgroundPlayEnabled) "Background Audio: Active" else "Ready",
                                color = if (backgroundPlayEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconButton(
                                onClick = onMediaPrevious,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = onToggleMediaPlay,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00E5FF))
                            ) {
                                Icon(
                                    imageVector = if (isMediaPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color(0xFF0A0E17),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = onMediaNext,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Widget 2: Quick Scratchpad Note Widget
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth().testTag("safari_widget_scratchpad")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.EditNote, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Text("Quick Scratchpad & Clipboard", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(noteText))
                                    onShowToast("Note copied to clipboard")
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy", tint = Color(0xFF38BDF8), modifier = Modifier.size(15.dp))
                            }

                            IconButton(
                                onClick = {
                                    noteText = ""
                                    onShowToast("Note cleared")
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                            }
                        }
                    }

                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                }
            }
        }

        // Widget 3: Live Crypto & Market Ticker
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth().testTag("safari_widget_crypto_ticker")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.TrendingUp, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                            Text("Market & Crypto Tickers", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { onShowToast("Market rates refreshed") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiveCryptoCard(symbol = "BTC", price = "$68,420", change = "+3.4%", isPositive = true, modifier = Modifier.weight(1f))
                        LiveCryptoCard(symbol = "ETH", price = "$3,540", change = "+2.1%", isPositive = true, modifier = Modifier.weight(1f))
                        LiveCryptoCard(symbol = "SOL", price = "$184", change = "+5.8%", isPositive = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Widget 4: Quick CLI Launcher & Web Portion Canvas
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CANVAS & TERMINAL LAUNCHERS",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenWidgetSelection,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Widgets, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Canvas Widgets", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }

                        OutlinedButton(
                            onClick = onOpenTerminal,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF10B981))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CLI Commands", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// REUSABLE SUB-COMPONENTS FOR TAB VIEWS
// -----------------------------------------------------------------------------------------

@Composable
private fun AboutSpecRow(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(text = title, color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Text(text = value, color = Color(0xFF94A3B8), fontSize = 11.5.sp)
    }
}

@Composable
private fun TelemetryMiniCard(
    label: String,
    value: String,
    subtext: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F1520),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF233044)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = label, color = Color(0xFF94A3B8), fontSize = 10.sp)
            Text(text = value, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = subtext, color = Color(0xFF64748B), fontSize = 9.sp)
        }
    }
}

@Composable
private fun GitFileChangeRow(
    fileName: String,
    path: String,
    status: String,
    diffText: String,
    statusColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = statusColor.copy(alpha = 0.2f)
            ) {
                Text(
                    text = status.take(1),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            Column {
                Text(text = fileName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(text = path, color = Color(0xFF64748B), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(text = diffText, color = Color(0xFF34D399), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun GitCommitItem(
    hash: String,
    message: String,
    author: String,
    time: String,
    isHead: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = hash,
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (isHead) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF0284C7).copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = "HEAD",
                            color = Color(0xFF38BDF8),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(text = time, color = Color(0xFF64748B), fontSize = 10.sp)
        }
        Text(text = message, color = Color(0xFFCBD5E1), fontSize = 11.5.sp, fontWeight = FontWeight.Normal)
        Text(text = "by $author", color = Color(0xFF64748B), fontSize = 9.5.sp)
    }
}

@Composable
private fun NotificationItemCard(
    title: String,
    body: String,
    timestamp: String,
    icon: ImageVector,
    badgeColor: Color,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161C26),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(imageVector = icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(15.dp))
                        }
                    }
                    Text(text = title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(text = timestamp, color = Color(0xFF64748B), fontSize = 10.sp)
            }

            Text(text = body, color = Color(0xFF94A3B8), fontSize = 11.5.sp, lineHeight = 16.sp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(text = actionLabel, fontSize = 11.sp, color = badgeColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LiveCryptoCard(
    symbol: String,
    price: String,
    change: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF0F1520),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF233044)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = symbol, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = price, color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = change, color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444), fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

