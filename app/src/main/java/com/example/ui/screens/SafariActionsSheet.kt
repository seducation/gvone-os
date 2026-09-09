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
import com.example.data.model.BrowserTab
import com.example.data.model.ShortsAudioMode
import com.example.data.model.isInternalHomeUrl
import com.example.ui.theme.*

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
    var isShortcutsExpanded by remember { mutableStateOf(false) }
    var isMediaPlayerExpanded by remember { mutableStateOf(false) }
    var showSignInDialog by remember { mutableStateOf(false) }
    var zoomPercentage by remember { mutableIntStateOf(100) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

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

    // Primary & secondary shortcuts matching visual reference
    val primaryShortcuts = remember {
        listOf(
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
    }

    val extraShortcuts = remember {
        listOf(
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
            )
        )
    }

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

                // 1. TOP SHORTCUTS CARD (Horizontal carousel of favorite sites + Add new)
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF161C26),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042))
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val shortcutsToDisplay = if (isShortcutsExpanded) (primaryShortcuts + extraShortcuts) else primaryShortcuts

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(2.dp))

                                shortcutsToDisplay.forEach { shortcut ->
                                    ShortcutTile(
                                        shortcut = shortcut,
                                        onClick = {
                                            onClose()
                                            onNavigateToUrl(shortcut.url)
                                        }
                                    )
                                }

                                // "Add new" button matching reference image (+ icon in circular container)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            onAddFavorite()
                                            toastMessage = "Added current tab to Shortcuts"
                                        }
                                        .padding(4.dp)
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
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Add new",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            // "view all" toggle button below shortcuts
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { isShortcutsExpanded = !isShortcutsExpanded }
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isShortcutsExpanded) "show less" else "view all",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
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
private fun ShortcutTile(
    shortcut: MenuShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .widthIn(min = 58.dp, max = 74.dp)
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
                fontSize = if (shortcut.initialLetters.length > 1) 14.sp else 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

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
