package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.tor.TorConnectionState
import com.example.ui.components.FloatingAddressBar
import com.example.ui.components.GVONEWebView
import com.example.ui.components.ShortsAudioPill
import com.example.ui.screens.*
import com.example.ui.theme.GVONEBrowserTheme
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GVONEBrowserTheme {
                val viewModel: BrowserViewModel = viewModel()
                BrowserApp(
                    viewModel = viewModel,
                    onShareUrl = { url ->
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, url)
                            type = "text/plain"
                        }
                        startActivity(Intent.createChooser(sendIntent, "Share Link"))
                    }
                )
            }
        }
    }
}

@Composable
fun BrowserApp(
    viewModel: BrowserViewModel,
    onShareUrl: (String) -> Unit
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val tabGroups by viewModel.tabGroups.collectAsStateWithLifecycle()
    val currentTabId by viewModel.currentTabId.collectAsStateWithLifecycle()
    val activeGroupId by viewModel.activeGroupId.collectAsStateWithLifecycle()
    val isPrivateMode by viewModel.isPrivateMode.collectAsStateWithLifecycle()
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val torStatus by viewModel.torStatus.collectAsStateWithLifecycle()
    val aiResult by viewModel.aiSearchResult.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val historyList by viewModel.history.collectAsStateWithLifecycle()
    val bookmarksList by viewModel.bookmarks.collectAsStateWithLifecycle()
    val downloadsList by viewModel.downloads.collectAsStateWithLifecycle()
    val torTestResult by viewModel.torTestResult.collectAsStateWithLifecycle()
    val isTorTesting by viewModel.isTorTesting.collectAsStateWithLifecycle()
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()
    val findIndex by viewModel.findCurrentIndex.collectAsStateWithLifecycle()
    val findCount by viewModel.findMatchCount.collectAsStateWithLifecycle()
    val diagnosticReport by viewModel.diagnosticReport.collectAsStateWithLifecycle()
    val isDiagnosing by viewModel.isDiagnosing.collectAsStateWithLifecycle()
    val isCurrentTabShorts by viewModel.isCurrentTabShorts.collectAsStateWithLifecycle()
    val isShortsMuted by viewModel.isShortsMuted.collectAsStateWithLifecycle()

    val isTorActive = settings.torEnabled && torStatus.state == TorConnectionState.CONNECTED

    val context = androidx.compose.ui.platform.LocalContext.current
    val launchOrbot = {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("org.torproject.android")
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.torproject.android"))
                try {
                    context.startActivity(marketIntent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://orbot.app/")))
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    // Address bar compactness state driven by web page scrolling
    var isAddressBarCompact by remember { mutableStateOf(false) }

    // Reset address bar to full expanded state when switching tabs
    LaunchedEffect(currentTabId) {
        isAddressBarCompact = false
    }

    // Handle back button presses gracefully
    BackHandler(enabled = activeSheet != ActiveSheet.None) {
        viewModel.closeSheet()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        // Main Browser View Area
        if (currentTab != null) {
            key(currentTab!!.id) {
                GVONEWebView(
                    tab = currentTab!!,
                    isTorActive = settings.torEnabled,
                    torConnectionState = torStatus.state,
                    torLastError = torStatus.lastError,
                    webAppBridge = viewModel.webAppBridge,
                    bridgeEnabled = settings.bidirectionalBridgeEnabled,
                    bridgeApplyToAll = settings.bridgeApplyToAllWebsites,
                    shortsAudioMode = settings.shortsAudioMode,
                    onRegisterWebView = { tabId, wv ->
                        viewModel.registerWebView(tabId, wv)
                    },
                    onRetryTor = { viewModel.retryTorConnection() },
                    onDisableTor = { viewModel.disableTorAndReload() },
                    onLaunchOrbot = launchOrbot,
                    onOpenSettings = { viewModel.openSheet(ActiveSheet.Settings) },
                    onOpenDiagnostics = { viewModel.openSheet(ActiveSheet.TorDiagnostics) },
                    onTitleChanged = { title ->
                        viewModel.updateCurrentTabState(title = title)
                    },
                    onUrlChanged = { url ->
                        viewModel.updateCurrentTabState(url = url)
                    },
                    onFaviconChanged = { favicon ->
                        viewModel.updateCurrentTabState(faviconUrl = favicon)
                    },
                    onProgressChanged = { progress ->
                        viewModel.updateCurrentTabState(progress = progress, isLoading = progress < 100)
                    },
                    onPageScroll = { scrollY, dy ->
                        if (scrollY <= 24) {
                            // Top of page: always restore full address bar
                            isAddressBarCompact = false
                        } else if (dy > 14) {
                            // Scrolling down into content: smoothly transform into compact pill
                            isAddressBarCompact = true
                        } else if (dy < -14) {
                            // Scrolling up toward top: smoothly expand back to full address bar
                            isAddressBarCompact = false
                        }
                    },
                    onStartDownload = { url, userAgent, contentDisposition, mimeType ->
                        // Trigger download via DownloadManager
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Find in Page Bar if active
        if (activeSheet == ActiveSheet.FindInPage) {
            FindInPageBar(
                query = findQuery,
                currentIndex = findIndex,
                matchCount = findCount,
                onQueryChange = { viewModel.setFindQuery(it) },
                onPrevious = { },
                onNext = { },
                onClose = { viewModel.closeSheet() },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Floating Bottom Address Bar Pill matching Safari Compact Design (Exactly 3 major controls)
        if (activeSheet == ActiveSheet.None || activeSheet == ActiveSheet.FindInPage) {
            FloatingAddressBar(
                currentTab = currentTab,
                tabCount = tabs.count { it.isPrivate == isPrivateMode },
                isPrivate = isPrivateMode,
                isTorActive = isTorActive,
                settings = settings,
                onUpdateSettings = { viewModel.updateSettings(it) },
                onTabOverviewClick = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onActionsMenuClick = { viewModel.openSheet(ActiveSheet.SafariActions) },
                onNavigate = { input ->
                    isAddressBarCompact = false
                    viewModel.navigateTo(input)
                },
                onReload = {
                    currentTab?.url?.let { viewModel.loadUrlInCurrentTab(it) }
                },
                onSwipeNextTab = { viewModel.switchToNextTab() },
                onSwipePrevTab = { viewModel.switchToPreviousTab() },
                isCompact = isAddressBarCompact,
                onExpand = { isAddressBarCompact = false },
                onContract = { isAddressBarCompact = true },
                onToggleCompact = { isAddressBarCompact = !isAddressBarCompact },
                modifier = Modifier.align(if (settings.addressBarBottom) Alignment.BottomCenter else Alignment.TopCenter)
            )
        }

        // Full Screen Tab Overview matching Screenshot 2
        AnimatedVisibility(
            visible = activeSheet == ActiveSheet.TabOverview,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 }
        ) {
            TabOverviewScreen(
                tabs = tabs,
                tabGroups = tabGroups,
                currentTabId = currentTabId,
                activeGroupId = activeGroupId,
                isPrivateMode = isPrivateMode,
                onTabSelected = { tabId -> viewModel.selectTab(tabId) },
                onTabClose = { tabId -> viewModel.closeTab(tabId) },
                onNewTab = { groupId -> viewModel.createNewTab(groupId = groupId) },
                onTogglePrivate = { isPrivate -> viewModel.setPrivateMode(isPrivate) },
                onSortTabs = { sortOption -> viewModel.sortTabs(sortOption) },
                onCreateGroup = { name, colorHex, tabIds -> viewModel.createTabGroup(name, colorHex, tabIds) },
                onRenameGroup = { groupId, newName -> viewModel.renameTabGroup(groupId, newName) },
                onDeleteGroup = { groupId, closeTabs -> viewModel.deleteTabGroup(groupId, closeTabs) },
                onMoveTabToGroup = { tabId, targetGroupId -> viewModel.moveTabToGroup(tabId, targetGroupId) },
                onMoveTabsToGroup = { tabIds, targetGroupId -> viewModel.moveTabsToGroup(tabIds, targetGroupId) },
                onCloseTabsInGroup = { groupId -> viewModel.closeTabsInGroup(groupId) },
                onDuplicateTab = { tabId -> viewModel.duplicateTab(tabId) },
                onCloseOtherTabs = { tabId -> viewModel.closeOtherTabs(tabId) },
                onCloseTabsToRight = { tabId -> viewModel.closeTabsToRight(tabId) },
                onCloseOverview = { viewModel.closeSheet() }
            )
        }

        // Control Centre Bottom Sheet matching Screenshot 1
        if (activeSheet == ActiveSheet.ControlCentre) {
            ControlCentreSheet(
                currentTab = currentTab,
                isTorActive = isTorActive,
                onNavigateBack = { /* WebView back handled */ },
                onNavigateForward = { /* WebView forward handled */ },
                onShare = { currentTab?.url?.let { onShareUrl(it) } },
                onRefresh = { currentTab?.url?.let { viewModel.loadUrlInCurrentTab(it) } },
                onOpenHistory = { viewModel.openSheet(ActiveSheet.History) },
                onOpenBookmarks = { viewModel.openSheet(ActiveSheet.Bookmarks) },
                onOpenDownloads = { viewModel.openSheet(ActiveSheet.Downloads) },
                onOpenSettings = { viewModel.openSheet(ActiveSheet.Settings) },
                onToggleDesktop = { viewModel.toggleDesktopMode() },
                onFindInPage = { viewModel.openSheet(ActiveSheet.FindInPage) },
                onToggleTor = { viewModel.toggleTor() },
                onNewPrivateTab = { viewModel.createNewTab(isPrivate = true) },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Settings Screen matching Screenshot 1 right panel
        if (activeSheet == ActiveSheet.Settings) {
            SettingsScreen(
                settings = settings,
                torStatus = torStatus,
                torTestResult = torTestResult,
                isTorTesting = isTorTesting,
                onToggleTor = { viewModel.toggleTor() },
                onTestTor = { viewModel.testTorConnection() },
                onRetryTor = { viewModel.retryTorConnection() },
                onOpenTorDiagnostics = { viewModel.openSheet(ActiveSheet.TorDiagnostics) },
                onSettingsChanged = { viewModel.updateSettings(it) },
                onClearBrowsingData = { viewModel.clearBrowsingData() },
                onBack = { viewModel.closeSheet() }
            )
        }

        // Tor Network Diagnostics Bottom Sheet
        if (activeSheet == ActiveSheet.TorDiagnostics) {
            TorDiagnosticsSheet(
                report = diagnosticReport,
                isDiagnosing = isDiagnosing,
                settings = settings,
                onRunDiagnostics = { viewModel.runTorDiagnostics() },
                onApplyFix = { fix -> viewModel.applyDiagnosticFix(fix) },
                onReapplyProxy = { viewModel.forceReapplyWebViewProxy() },
                onClose = { viewModel.closeSheet() }
            )
        }

        // AI Search Result Overlay (ChatGPT/Perplexity/Arc style)
        if (activeSheet == ActiveSheet.AISearchResult) {
            AISearchOverlay(
                result = aiResult,
                isLoading = isAiLoading,
                onOpenUrl = { url -> viewModel.loadUrlInCurrentTab(url) },
                onOpenInNewTab = { url -> viewModel.createNewTab(url) },
                onFollowUp = { prompt -> viewModel.performAISearch(prompt) },
                onClose = { viewModel.closeSheet() }
            )
        }

        // History Sheet
        if (activeSheet == ActiveSheet.History) {
            HistorySheet(
                historyList = historyList,
                onOpenUrl = { url -> viewModel.loadUrlInCurrentTab(url) },
                onDeleteEntry = { entry -> /* delete */ },
                onClearAll = { viewModel.clearBrowsingData(history = true) },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Bookmarks Sheet
        if (activeSheet == ActiveSheet.Bookmarks) {
            BookmarksSheet(
                bookmarks = bookmarksList,
                onOpenUrl = { url -> viewModel.loadUrlInCurrentTab(url) },
                onDeleteBookmark = { bookmark -> /* delete */ },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Downloads Sheet
        if (activeSheet == ActiveSheet.Downloads) {
            DownloadsSheet(
                downloads = downloadsList,
                onClose = { viewModel.closeSheet() }
            )
        }

        // Safari Actions Menu Sheet (Triggered by the Right Circular Button)
        if (activeSheet == ActiveSheet.SafariActions || activeSheet == ActiveSheet.SafariPageMenu || activeSheet == ActiveSheet.ControlCentre) {
            SafariActionsSheet(
                tab = currentTab,
                isTorActive = isTorActive,
                isPrivateMode = isPrivateMode,
                isShortsTab = isCurrentTabShorts,
                isShortsMuted = isShortsMuted,
                shortsAudioMode = settings.shortsAudioMode,
                onToggleShortsAudio = { viewModel.toggleShortsAudio() },
                onSelectShortsAudioMode = { mode -> viewModel.setShortsAudioMode(mode) },
                onNewTab = { viewModel.createNewTab() },
                onNewPrivateTab = { viewModel.createNewTab(isPrivate = true) },
                onToggleDesktop = { viewModel.toggleDesktopMode() },
                onToggleTor = { viewModel.toggleTor() },
                onFindInPage = { viewModel.openSheet(ActiveSheet.FindInPage) },
                onShare = { currentTab?.url?.let { onShareUrl(it) } },
                onReload = { currentTab?.url?.let { viewModel.loadUrlInCurrentTab(it) } },
                onAddBookmark = { viewModel.addBookmark() },
                onAddFavorite = { viewModel.addToFavorites() },
                onAddToReadingList = { viewModel.addToReadingList() },
                onOpenReaderMode = { viewModel.openSheet(ActiveSheet.ReaderMode) },
                onOpenPasswords = { viewModel.openSheet(ActiveSheet.SavedPasswords) },
                onOpenAddToHomeScreen = { viewModel.openSheet(ActiveSheet.AddToHomeScreen) },
                onOpenDownloads = { viewModel.openSheet(ActiveSheet.Downloads) },
                onOpenHistory = { viewModel.openSheet(ActiveSheet.History) },
                onOpenBookmarks = { viewModel.openSheet(ActiveSheet.Bookmarks) },
                onOpenSettings = { viewModel.openSheet(ActiveSheet.Settings) },
                onOpenTorDiagnostics = { viewModel.openSheet(ActiveSheet.TorDiagnostics) },
                onNavigateToUrl = { url -> viewModel.loadUrlInCurrentTab(url) },
                onClearBrowsingData = { viewModel.clearBrowsingData() },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Reader Mode Fullscreen View
        if (activeSheet == ActiveSheet.ReaderMode) {
            ReaderModeOverlay(
                tab = currentTab,
                onClose = { viewModel.closeSheet() },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Add to Home Screen Dialog
        if (activeSheet == ActiveSheet.AddToHomeScreen) {
            AddToHomeScreenDialog(
                tab = currentTab,
                onConfirm = { /* Added to Home Screen */ },
                onDismiss = { viewModel.closeSheet() }
            )
        }

        // Saved Passwords / iCloud Keychain Sheet
        if (activeSheet == ActiveSheet.SavedPasswords) {
            SavedPasswordsSheet(
                onClose = { viewModel.closeSheet() }
            )
        }

        // Site Security & SSL Info Dialog
        if (activeSheet == ActiveSheet.SiteInfo) {
            SiteInfoDialog(
                tab = currentTab,
                isTorActive = isTorActive,
                onDismiss = { viewModel.closeSheet() }
            )
        }
    }
}
