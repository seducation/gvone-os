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
    val currentTabId by viewModel.currentTabId.collectAsStateWithLifecycle()
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

    val isTorActive = settings.torEnabled && torStatus.state == TorConnectionState.CONNECTED

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
            GVONEWebView(
                tab = currentTab!!,
                isTorActive = settings.torEnabled,
                torConnectionState = torStatus.state,
                torLastError = torStatus.lastError,
                onRetryTor = { viewModel.retryTorConnection() },
                onOpenSettings = { viewModel.openSheet(ActiveSheet.Settings) },
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
                onStartDownload = { url, userAgent, contentDisposition, mimeType ->
                    // Trigger download via DownloadManager
                },
                modifier = Modifier.fillMaxSize()
            )
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
                onTabOverviewClick = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onActionsMenuClick = { viewModel.openSheet(ActiveSheet.SafariActions) },
                onNavigate = { input -> viewModel.navigateTo(input) },
                onReload = {
                    currentTab?.url?.let { viewModel.loadUrlInCurrentTab(it) }
                },
                onSwipeNextTab = { viewModel.switchToNextTab() },
                onSwipePrevTab = { viewModel.switchToPreviousTab() },
                modifier = Modifier.align(Alignment.BottomCenter)
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
                currentTabId = currentTabId,
                isPrivateMode = isPrivateMode,
                onTabSelected = { tabId -> viewModel.selectTab(tabId) },
                onTabClose = { tabId -> viewModel.closeTab(tabId) },
                onNewTab = { viewModel.createNewTab() },
                onTogglePrivate = { isPrivate -> viewModel.setPrivateMode(isPrivate) },
                onSortTabs = { sortOption -> viewModel.sortTabs(sortOption) },
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
                onSettingsChanged = { viewModel.updateSettings(it) },
                onClearBrowsingData = { viewModel.clearBrowsingData() },
                onBack = { viewModel.closeSheet() }
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
