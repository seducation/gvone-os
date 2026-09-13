package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.connector.WebsiteAccessReport
import com.example.data.model.SitePermission
import com.example.data.tor.TorConnectionState
import com.example.ui.components.CustomCommandManagerSheet
import com.example.ui.components.FloatingAddressBar
import com.example.ui.components.GVONEWebView
import com.example.ui.components.PermissionPromptDialog
import com.example.ui.components.ShortsAudioPill
import com.example.ui.components.WebsiteDeveloperBar
import com.example.ui.components.DeveloperBarToggleChip
import com.example.ui.contextmenu.LinkContextMenuBottomSheet
import com.example.ui.contextmenu.PagePreviewSheet
import com.example.ui.contextmenu.TabGroupPickerSheet
import com.example.ui.screens.*
import com.example.ui.screens.canvas.EnvironmentStartPageCanvas
import com.example.ui.screens.communication.CommunicationHubScreen
import com.example.ui.screens.connectors.ConnectorHubScreen
import com.example.ui.screens.connectors.WebsiteConnectorsManagerScreen
import com.example.ui.screens.extensions.DataSaverExtensionSheet
import com.example.ui.screens.files.GVONEFileBrowserSheet
import com.example.ui.screens.files.GVONEFileViewerScreen
import com.example.ui.screens.research.ResearchWorkspaceScreen
import com.example.ui.screens.webwidget.WebWidgetConfigSheet
import com.example.ui.screens.webwidget.WebWidgetSelectionOverlay
import com.example.ui.theme.GVONEBrowserTheme
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel

class MainActivity : ComponentActivity() {
    private val browserViewModel: BrowserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GVONEBrowserTheme {
                BrowserApp(
                    viewModel = browserViewModel,
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val isCtrlOrMeta = event.isCtrlPressed || event.isMetaPressed
            if (isCtrlOrMeta && event.keyCode == KeyEvent.KEYCODE_T) {
                browserViewModel.createNewTab()
                return true
            }
            if (isCtrlOrMeta && event.keyCode == KeyEvent.KEYCODE_W) {
                browserViewModel.closeCurrentTab()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
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
    val mediaPlayerStatus by viewModel.mediaPlayerStatus.collectAsStateWithLifecycle()
    val contextMenuData by viewModel.contextMenuData.collectAsStateWithLifecycle()
    val pagePreviewData by viewModel.pagePreviewData.collectAsStateWithLifecycle()
    val groupPickerUrl by viewModel.groupPickerUrl.collectAsStateWithLifecycle()

    val environments by viewModel.environments.collectAsStateWithLifecycle()
    val currentEnvironment by viewModel.currentEnvironment.collectAsStateWithLifecycle()
    val isCanvasEditMode by viewModel.isCanvasEditMode.collectAsStateWithLifecycle()
    val webWidgetDraft by viewModel.webWidgetDraft.collectAsStateWithLifecycle()
    val customCommands by viewModel.customCommands.collectAsStateWithLifecycle()
    val addressBarInput by viewModel.addressBarInput.collectAsStateWithLifecycle()

    val showSuggestions by viewModel.showSuggestions.collectAsStateWithLifecycle()
    val showCommandPalette by viewModel.showCommandPalette.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()

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

    var showAvatarDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Selected photo: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Camera permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Termux CLI full screen state
    var isTerminalFullScreen by remember { mutableStateOf(false) }
    LaunchedEffect(activeSheet) {
        if (activeSheet != ActiveSheet.Terminal) {
            isTerminalFullScreen = false
        }
    }

    // Reset address bar to full expanded state when switching tabs
    LaunchedEffect(currentTabId) {
        isAddressBarCompact = false
    }

    // Track address bar measured height for seamless docking of Terminal and overlays
    var addressBarHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val baseBarHeight = if (isAddressBarCompact) 50.dp else 76.dp

    val effectiveAddressBarHeight = if (settings.addressBarBottom) {
        if (addressBarHeightPx > 0) {
            with(density) { addressBarHeightPx.toDp() }
        } else {
            baseBarHeight + navBarBottomPadding + imeBottomPadding
        }
    } else {
        0.dp
    }

    // Handle back button presses gracefully
    BackHandler(enabled = activeSheet != ActiveSheet.None) {
        viewModel.closeSheet()
    }
    BackHandler(enabled = showSuggestions && activeSheet == ActiveSheet.None) {
        viewModel.closeSuggestions()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        // Main Browser View Area (All tabs kept alive for seamless background playback & instant tab switching)
        Box(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { tab ->
                val isCurrent = (tab.id == currentTabId)
                key(tab.id) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = if (isCurrent) 1f else 0f
                                translationX = if (isCurrent) 0f else 999999f
                            }
                    ) {
                        if (viewModel.isStartPage(tab.url)) {
                            EnvironmentStartPageCanvas(
                                environment = currentEnvironment,
                                allEnvironments = environments,
                                isPrivate = tab.isPrivate,
                                isTorActive = settings.torEnabled,
                                isEditMode = isCanvasEditMode,
                                onToggleEditMode = { viewModel.toggleCanvasEditMode() },
                                onSelectEnvironment = { viewModel.switchEnvironment(it) },
                                onCreateEnvironment = { name, icon, theme, preset, initialLinkUrl, initialLinkTitle ->
                                    viewModel.createEnvironment(name, icon, theme, preset, initialLinkUrl, initialLinkTitle)
                                },
                                onDuplicateEnvironment = { viewModel.duplicateEnvironment(it) },
                                onDeleteEnvironment = { viewModel.deleteEnvironment(it) },
                                onNavigate = { url ->
                                    viewModel.loadUrlInCurrentTab(url)
                                },
                                onAddObject = { viewModel.addCanvasObject(it) },
                                onUpdateObject = { viewModel.updateCanvasObject(it) },
                                onDeleteObject = { viewModel.deleteCanvasObject(it) },
                                onReorderObjects = { viewModel.reorderCanvasObjects(it) },
                                onUpdateBackground = { viewModel.updateCanvasBackground(it) },
                                onUpdateLayoutMode = { viewModel.updateCanvasLayoutMode(it) },
                                showSuggestions = showSuggestions,
                                suggestions = suggestions,
                                onExecuteSuggestion = { viewModel.executeSuggestion(it) },
                                onCloseSuggestions = { viewModel.closeSuggestions() },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (tab.url.startsWith("gvone-file://")) {
                            val filePath = tab.url.removePrefix("gvone-file://")
                            GVONEFileViewerScreen(
                                fileRelativePath = filePath,
                                fileSystem = viewModel.fileSystem,
                                onCloseTab = { viewModel.closeTab(tab.id) },
                                onOpenTerminalWithCommand = {
                                    viewModel.openSheet(ActiveSheet.Terminal)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (settings.developerBarEnabled) {
                                        WebsiteDeveloperBar(
                                            tab = tab,
                                            activeWebView = viewModel.getActiveWebView(tab.id),
                                            isTorActive = settings.torEnabled,
                                            onToggleOff = { viewModel.toggleDeveloperBar() },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    GVONEWebView(
                                        tab = tab,
                                        isTorActive = settings.torEnabled,
                                        torConnectionState = torStatus.state,
                                        torLastError = torStatus.lastError,
                                        webAppBridge = viewModel.webAppBridge,
                                        bridgeEnabled = settings.bidirectionalBridgeEnabled,
                                        bridgeApplyToAll = settings.bridgeApplyToAllWebsites,
                                        shortsAudioMode = settings.shortsAudioMode,
                                        backgroundPlayEnabled = settings.backgroundPlayEnabled,
                                        dataSaverManager = viewModel.dataSaverManager,
                                        onRegisterWebView = { tabId, wv ->
                                            viewModel.registerWebView(tabId, wv)
                                        },
                                        onRetryTor = { viewModel.retryTorConnection() },
                                        onDisableTor = { viewModel.disableTorAndReload() },
                                        onLaunchOrbot = launchOrbot,
                                        onOpenSettings = { viewModel.openSheet(ActiveSheet.Settings) },
                                        onOpenDiagnostics = { viewModel.openSheet(ActiveSheet.TorDiagnostics) },
                                        onTitleChanged = { title ->
                                            viewModel.updateTabState(tabId = tab.id, title = title)
                                        },
                                        onUrlChanged = { url ->
                                            viewModel.updateTabState(tabId = tab.id, url = url)
                                        },
                                        onFaviconChanged = { favicon ->
                                            viewModel.updateTabState(tabId = tab.id, faviconUrl = favicon)
                                        },
                                        onProgressChanged = { progress ->
                                            viewModel.updateTabState(tabId = tab.id, progress = progress, isLoading = progress < 100)
                                        },
                                        onContextMenuDetected = { data ->
                                            viewModel.triggerContextMenu(data)
                                        },
                                        onPageScroll = { scrollY, dy ->
                                            if (isCurrent) {
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
                                            }
                                        },
                                        onStartDownload = { url, userAgent, contentDisposition, mimeType ->
                                            viewModel.downloadResource(url, mimeType, context)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                }

                                if (!settings.developerBarEnabled && isCurrent) {
                                    DeveloperBarToggleChip(
                                        onClick = { viewModel.toggleDeveloperBar() },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 8.dp, end = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
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

        // Termux-Style CLI Drawer/Overlay (Displays docked on top of address bar or bottom bar, with fullscreen toggle)
        AnimatedVisibility(
            visible = activeSheet == ActiveSheet.Terminal,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            TerminalScreen(
                viewModel = viewModel,
                isAddressBarBottom = settings.addressBarBottom,
                addressBarBottomPadding = effectiveAddressBarHeight,
                isFullScreen = isTerminalFullScreen,
                onToggleFullScreen = { isTerminalFullScreen = it },
                onOpenAgentDashboard = { viewModel.openAgentDashboard() },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Floating Bottom Address Bar Pill matching Safari Compact Design (Visible when no modal or when Terminal is docked)
        if ((activeSheet == ActiveSheet.None || activeSheet == ActiveSheet.FindInPage || activeSheet == ActiveSheet.Terminal) && !isTerminalFullScreen) {
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
                customCommands = customCommands,
                onOpenCommandManager = { viewModel.openSheet(ActiveSheet.CustomCommands) },
                onOpenTerminal = { viewModel.openSheet(ActiveSheet.Terminal) },
                isTerminalOpen = activeSheet == ActiveSheet.Terminal,
                onCloseTerminal = { viewModel.closeSheet() },
                onOpenConnector = { context ->
                    viewModel.openWebsiteConnector(context)
                },
                onOpenPhotos = {
                    try {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    } catch (_: Exception) {}
                },
                onOpenCamera = {
                    try {
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    } catch (_: Exception) {}
                },
                onOpenAvatar = {
                    showAvatarDialog = true
                },
                onOpenFiles = {
                    viewModel.openSheet(ActiveSheet.Files)
                },
                onOpenConnectorHub = {
                    viewModel.openConnectorHub()
                },
                onOpenResearchWorkspace = {
                    viewModel.openResearchWorkspace()
                },
                onOpenDataSaver = {
                    viewModel.openDataSaver()
                },
                onOpenCommunicationHub = {
                    viewModel.openCommunicationHub()
                },
                currentEnvironmentId = currentEnvironment.id,
                currentEnvironmentName = currentEnvironment.name,
                addressBarInput = addressBarInput,
                onAddressBarInputChange = { viewModel.setAddressBarInput(it) },
                showSuggestions = showSuggestions,
                showCommandPalette = showCommandPalette,
                showSuggestionChips = showSuggestions,
                showTerminalCommands = showCommandPalette,
                onToggleSuggestions = { viewModel.toggleSuggestions() },
                onCloseSuggestions = { viewModel.closeSuggestions() },
                onOpenCommandPalette = { viewModel.openCommandPalette() },
                onCloseCommandPalette = { viewModel.closeCommandPalette() },
                onOpenSuggestionChips = { viewModel.openSuggestionChips() },
                onCloseSuggestionChips = { viewModel.closeSuggestionChips() },
                onToggleSuggestionChips = { viewModel.toggleSuggestionChips() },
                onOpenTerminalCommands = { viewModel.openTerminalCommands() },
                onCloseTerminalCommands = { viewModel.closeTerminalCommands() },
                modifier = Modifier
                    .align(if (settings.addressBarBottom) Alignment.BottomCenter else Alignment.TopCenter)
                    .onGloballyPositioned { coordinates ->
                        if (settings.addressBarBottom) {
                            addressBarHeightPx = coordinates.size.height
                        }
                    }
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
                environments = environments,
                currentEnvironment = currentEnvironment,
                onSelectEnvironment = { viewModel.switchEnvironment(it) },
                onCreateEnvironment = { name, icon, theme, preset, initialLinkUrl, initialLinkTitle ->
                    viewModel.createEnvironment(name, icon, theme, preset, initialLinkUrl, initialLinkTitle)
                },
                onDuplicateEnvironment = { viewModel.duplicateEnvironment(it) },
                onDeleteEnvironment = { viewModel.deleteEnvironment(it) },
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
                onOpenTerminal = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenDownloads = { viewModel.openSheet(ActiveSheet.Downloads) },
                onOpenSettings = { viewModel.openSheet(ActiveSheet.Settings) },
                onOpenPermissions = { viewModel.openPermissions() },
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
                onOpenCustomCommands = { viewModel.openSheet(ActiveSheet.CustomCommands) },
                onOpenFiles = { viewModel.openSheet(ActiveSheet.Files) },
                onOpenWebsiteConnectors = { viewModel.openSheet(ActiveSheet.WebsiteConnectors) },
                onOpenPermissions = { viewModel.openPermissions() },
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
                backgroundPlayEnabled = settings.backgroundPlayEnabled,
                isMediaPlaying = mediaPlayerStatus?.isPlaying ?: (isCurrentTabShorts && !isShortsMuted),
                onToggleShortsAudio = { viewModel.toggleShortsAudio() },
                onSelectShortsAudioMode = { mode -> viewModel.setShortsAudioMode(mode) },
                onToggleBackgroundPlay = { viewModel.toggleBackgroundPlay() },
                onToggleMediaPlay = { viewModel.toggleMediaPlay() },
                onMediaPrevious = { viewModel.mediaPrevious() },
                onMediaNext = { viewModel.mediaNext() },
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
                onOpenSiteInfo = { viewModel.openSheet(ActiveSheet.SiteInfo) },
                onOpenAddToHomeScreen = { viewModel.openSheet(ActiveSheet.AddToHomeScreen) },
                onOpenWidgetSelection = { viewModel.openSheet(ActiveSheet.WebWidgetSelection) },
                onOpenDownloads = { viewModel.openSheet(ActiveSheet.Downloads) },
                onOpenHistory = { viewModel.openSheet(ActiveSheet.History) },
                onOpenBookmarks = { viewModel.openSheet(ActiveSheet.Bookmarks) },
                onOpenTerminal = { viewModel.openSheet(ActiveSheet.Terminal) },
                isDeveloperBarEnabled = settings.developerBarEnabled,
                onToggleDeveloperBar = { viewModel.toggleDeveloperBar() },
                onOpenSettings = { viewModel.openSheet(ActiveSheet.Settings) },
                onOpenTorDiagnostics = { viewModel.openSheet(ActiveSheet.TorDiagnostics) },
                onOpenCustomCommands = { viewModel.openSheet(ActiveSheet.CustomCommands) },
                onOpenFiles = { viewModel.openSheet(ActiveSheet.Files) },
                onOpenWebsiteConnectors = { viewModel.openSheet(ActiveSheet.WebsiteConnectors) },
                onOpenConnectorHub = { viewModel.openConnectorHub() },
                onOpenResearchWorkspace = { viewModel.openResearchWorkspace() },
                onOpenDataSaver = { viewModel.openDataSaver() },
                onOpenCommunicationHub = { viewModel.openCommunicationHub() },
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

        // Chrome-style Site Information Bottom Sheet
        if (activeSheet == ActiveSheet.SiteInfo) {
            SiteInfoBottomSheet(
                tab = currentTab,
                isTorActive = isTorActive,
                history = historyList,
                settings = settings,
                onSavePermission = { permission ->
                    viewModel.saveSitePermission(permission)
                },
                onGetPermission = { domain ->
                    viewModel.getSitePermission(domain)
                },
                onClearSiteData = { domain, url ->
                    viewModel.clearSiteDataForDomain(domain, url, context)
                },
                onClearDomainHistory = { domain ->
                    viewModel.deleteHistoryForDomain(domain, context)
                },
                onDismiss = { viewModel.closeSheet() }
            )
        }

        // Chrome-like Long-Press Context Menu for Links & Media
        contextMenuData?.let { data ->
            LinkContextMenuBottomSheet(
                data = data,
                onDismiss = { viewModel.dismissContextMenu() },
                onOpenInNewTab = { url -> viewModel.openInNewTabFromContextMenu(url, inBackground = true, context = context) },
                onOpenInNewTabInGroup = { url -> viewModel.openInNewTabInGroupFromContextMenu(url, context = context) },
                onOpenInIncognito = { url -> viewModel.openInIncognitoFromContextMenu(url, context = context) },
                onOpenInNewWindow = { url -> viewModel.openInNewWindowFromContextMenu(url, context = context) },
                onPreviewPage = { url, title -> viewModel.showPagePreview(url, title) },
                onCopyLinkAddress = { url -> viewModel.copyLinkAddress(url, context = context) },
                onCopyLinkText = { text -> viewModel.copyLinkText(text, context = context) },
                onDownloadResource = { url, mimeType -> viewModel.downloadResource(url, mimeType, context = context) },
                onToggleBookmark = { url, title, faviconUrl -> viewModel.toggleBookmarkFromContextMenu(url, title, faviconUrl, context = context) },
                onToggleReadingList = { url, title, faviconUrl -> viewModel.toggleReadingListFromContextMenu(url, title, faviconUrl, context = context) },
                onShareLink = { url, title -> viewModel.shareLink(url, title, context = context) }
            )
        }

        // Web Portion Widget Selection Overlay
        if (activeSheet == ActiveSheet.WebWidgetSelection) {
            WebWidgetSelectionOverlay(
                currentTab = currentTab,
                activeWebView = viewModel.getActiveWebView(),
                currentEnvironmentId = currentEnvironment.id,
                onConfirm = { draft ->
                    viewModel.setWebWidgetDraft(draft)
                },
                onCancel = {
                    viewModel.closeSheet()
                }
            )
        }

        // Web Portion Widget Configuration Sheet
        if (activeSheet == ActiveSheet.WebWidgetConfig) {
            webWidgetDraft?.let { draft ->
                WebWidgetConfigSheet(
                    draft = draft,
                    environments = environments,
                    onAddWidget = { widget, targetEnvId ->
                        viewModel.addWebPortionWidget(widget, targetEnvId)
                    },
                    onBackToSelection = {
                        viewModel.openSheet(ActiveSheet.WebWidgetSelection)
                    },
                    onDismiss = {
                        viewModel.closeSheet()
                    }
                )
            }
        }

        // Lightweight Page Preview Bottom Sheet with Share & Saved Bookmarks Browser
        pagePreviewData?.let { preview ->
            PagePreviewSheet(
                data = preview,
                savedBookmarks = bookmarksList,
                onClose = { viewModel.dismissPagePreview() },
                onOpenInTab = { url ->
                    viewModel.dismissPagePreview()
                    viewModel.createNewTab(url = url, inBackground = false)
                },
                onShare = { url, title ->
                    viewModel.shareLink(url, title, context = context)
                }
            )
        }

        // Tab Group Picker Dialog/Sheet (When user selects "Open in new tab in group" with multiple existing groups)
        groupPickerUrl?.let { targetUrl ->
            TabGroupPickerSheet(
                groups = tabGroups,
                onDismiss = { viewModel.dismissGroupPicker() },
                onSelectGroup = { groupId -> viewModel.selectGroupAndAddTab(groupId, targetUrl, context = context) },
                onCreateGroupAndAdd = { groupName -> viewModel.createGroupAndAddTab(groupName, targetUrl, context = context) }
            )
        }

        // Custom Command Manager Sheet
        if (activeSheet == ActiveSheet.CustomCommands) {
            CustomCommandManagerSheet(
                commands = customCommands,
                onSaveCommand = { viewModel.saveCustomCommand(it) },
                onDeleteCommand = { viewModel.deleteCustomCommand(it) },
                onToggleEnabled = { id, enabled -> viewModel.toggleCommandEnabled(id, enabled) },
                onTogglePinned = { id, pinned -> viewModel.toggleCommandPinned(id, pinned) },
                onInstallPack = { viewModel.installCommandPack(it) },
                onImportCommands = { viewModel.importCommandsFromJson(it) },
                onTestExecuteCommand = { input ->
                    viewModel.closeSheet()
                    viewModel.navigateTo(input)
                },
                onGenerateWithAI = { prompt, callback ->
                    viewModel.generateCommandWithAI(prompt, callback)
                },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Universal GVONE File System & Storage Browser Sheet
        if (activeSheet == ActiveSheet.Files) {
            GVONEFileBrowserSheet(
                fileSystem = viewModel.fileSystem,
                onOpenFileInTab = { file, inNewTab ->
                    viewModel.openFileInTab(file, inNewTab)
                },
                onDismiss = { viewModel.closeSheet() }
            )
        }

        // Universal Website Accounts, Connectors, Passwords & Cookies Manager
        if (activeSheet == ActiveSheet.WebsiteConnectors) {
            WebsiteConnectorsManagerScreen(
                viewModel = viewModel,
                onOpenUrl = { url ->
                    viewModel.loadUrlInCurrentTab(url)
                },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Universal Connector Hub (Google Drive, GitHub, Slack, Notion, Dropbox)
        if (activeSheet == ActiveSheet.ConnectorHub) {
            ConnectorHubScreen(
                manager = viewModel.connectorHubManager,
                onOpenUrlInTab = { url ->
                    viewModel.loadUrlInCurrentTab(url)
                },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Research Workspace (Save webpage as source, Highlight -> note, Citation attached, Evidence Map)
        if (activeSheet == ActiveSheet.ResearchWorkspace) {
            ResearchWorkspaceScreen(
                manager = viewModel.researchWorkspaceManager,
                currentTabUrl = currentTab?.url.orEmpty(),
                currentTabTitle = currentTab?.title.orEmpty(),
                onOpenUrlInTab = { url ->
                    viewModel.loadUrlInCurrentTab(url)
                },
                onClose = { viewModel.closeSheet() }
            )
        }

        // Data Saver Extension Sheet
        if (activeSheet == ActiveSheet.DataSaver) {
            val currentDomain = remember(currentTab?.url) {
                com.example.data.connector.WebsiteAccessConnectorService.extractDomain(currentTab?.url.orEmpty())
            }
            DataSaverExtensionSheet(
                dataSaverManager = viewModel.dataSaverManager,
                currentDomain = currentDomain,
                onClose = { viewModel.closeSheet() }
            )
        }

        // Unified Communication Hub (Gmail, Slack, Discord, Teams, Unified Inbox, Notifications)
        if (activeSheet == ActiveSheet.CommunicationHub) {
            CommunicationHubScreen(
                manager = viewModel.communicationHubManager,
                onClose = { viewModel.closeSheet() }
            )
        }

        // GVONE Website Access & Account Connector Bottom Sheet
        if (activeSheet == ActiveSheet.WebsiteConnector) {
            val report by viewModel.websiteAccessReport.collectAsStateWithLifecycle()
            val accessContext by viewModel.websiteAccessContext.collectAsStateWithLifecycle()
            var currentPerm by remember { mutableStateOf<SitePermission?>(null) }

            LaunchedEffect(accessContext?.currentDomain) {
                accessContext?.currentDomain?.let { domain ->
                    currentPerm = viewModel.getSitePermission(domain)
                }
            }

            val effectiveReport = report ?: accessContext?.let { ctx ->
                WebsiteAccessReport(
                    context = ctx,
                    accountStatus = com.example.data.connector.AccountDetectionStatus.Unknown,
                    cookiesCount = 0,
                    cookieNames = emptyList(),
                    permissionsCount = 0,
                    grantedPermissions = emptyList(),
                    siteDataFormatted = "0 KB",
                    siteDataBytes = 0L,
                    isHttps = ctx.currentUrl.startsWith("https://", ignoreCase = true)
                )
            }

            effectiveReport?.let { rep ->
                WebsiteAccessConnectorSheet(
                    report = rep,
                    sitePermission = currentPerm,
                    onUpdatePermission = { updated ->
                        currentPerm = updated
                        viewModel.saveSitePermission(updated)
                        viewModel.refreshWebsiteConnectorReport()
                    },
                    onClearCookies = { viewModel.clearCookiesForCurrentSite() },
                    onClearSiteData = { viewModel.clearSiteDataForCurrentSite() },
                    onRefresh = { viewModel.refreshWebsiteConnectorReport() },
                    onClose = { viewModel.closeSheet() }
                )
            }
        }

        // Central Nervous System Multi-Agent UI Dashboard
        if (activeSheet == ActiveSheet.AgentDashboard) {
            AgentDashboardSheet(
                cns = com.example.agent.cns.CentralNervousSystem.global,
                viewModel = viewModel,
                onClose = { viewModel.closeSheet() }
            )
        }

        // Autonomous Agent Safety & Permissions Manager Sheet
        if (activeSheet == ActiveSheet.Permissions) {
            PermissionsManagerSheet(
                permissionSystem = com.example.agent.safety.PermissionSystem.global,
                onClose = { viewModel.closeSheet() }
            )
        }

        // Real-time Interactive Permission Request Prompt Dialog
        val activePermissionPrompt by com.example.agent.safety.PermissionSystem.global.activePromptFlow.collectAsStateWithLifecycle()
        if (activePermissionPrompt != null) {
            val prompt = activePermissionPrompt!!
            PermissionPromptDialog(
                request = prompt,
                onAllowOnce = {
                    com.example.agent.safety.PermissionSystem.global.respondToRequest(
                        prompt.id,
                        approved = true,
                        rememberPolicy = false
                    )
                },
                onAlwaysAllow = {
                    com.example.agent.safety.PermissionSystem.global.respondToRequest(
                        prompt.id,
                        approved = true,
                        rememberPolicy = true
                    )
                },
                onDeny = {
                    com.example.agent.safety.PermissionSystem.global.respondToRequest(
                        prompt.id,
                        approved = false,
                        rememberPolicy = false
                    )
                },
                onBlockAlways = {
                    com.example.agent.safety.PermissionSystem.global.respondToRequest(
                        prompt.id,
                        approved = false,
                        rememberPolicy = true
                    )
                },
                onDismiss = {
                    com.example.agent.safety.PermissionSystem.global.dismissPendingRequest(prompt.id)
                }
            )
        }

        // Browser Avatar & Persona Profile Dialog
        if (showAvatarDialog) {
            AlertDialog(
                onDismissRequest = { showAvatarDialog = false },
                title = {
                    Text(
                        text = "Browser Avatar & Persona",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Active Environment: ${currentEnvironment.name}",
                            color = Color(0xFF38BDF8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Persona: Default Web Identity\nSession: Isolated Container\nTab ID: ${currentTab?.id ?: "N/A"}",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAvatarDialog = false }) {
                        Text("Done", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF141C2B)
            )
        }
    }
}
