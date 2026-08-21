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
    onOpenPasswords: () -> Unit,
    onOpenAddToHomeScreen: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTorDiagnostics: () -> Unit = {},
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
                    .padding(top = 10.dp, bottom = 8.dp)
                    .width(40.dp)
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
                .navigationBarsPadding()
        ) {
            // Main Scrollable Area inside the Bottom Sheet
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
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
                                }
                            }
                        }
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
                                            label = if (isTorActive) "Tor Shield (On)" else "Tor Shield",
                                            isActive = isTorActive,
                                            activeColor = GVONETertiary,
                                            onClick = {
                                                onToggleTor()
                                                toastMessage = if (isTorActive) "Tor Disabled" else "Connecting Tor..."
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
                                                icon = Icons.Rounded.VpnKey,
                                                label = "Passwords & Autofill",
                                                trailingText = "iCloud Keychain",
                                                onClick = {
                                                    onClose()
                                                    onOpenPasswords()
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

                // 4. 4-GRID SHORTCUT TILES: History | Bookmarks | Downloads | Passwords
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
                            icon = Icons.Rounded.Download,
                            label = "Downloads",
                            onClick = {
                                onClose()
                                onOpenDownloads()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GridShortcutItem(
                            icon = Icons.Rounded.VpnKey,
                            label = "Passwords",
                            onClick = {
                                onClose()
                                onOpenPasswords()
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F131A))
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
