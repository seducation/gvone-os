package com.example.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.data.model.BrowserSettings
import com.example.data.model.BrowserTab
import com.example.data.sync.MediaPlayerStatus
import com.example.ui.screens.ide.IdeTab
import com.example.ui.screens.ide.XcodeWorkspaceMode
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel

/**
 * Host composable for the unified Safari Actions & Xcode Inspector bottom sheet.
 * Consolidates the Main actions panel (settings, bookmarks, shortcuts, share, desktop mode)
 * with Xcode Inspector attribute tabs (identity, DOM, security) into a dedicated method to avoid
 * Java bytecode 64KB MethodTooLargeException in BrowserApp.
 */
@Composable
fun CombinedActionsInspectorSheet(
    activeSheet: ActiveSheet,
    currentTab: BrowserTab?,
    isTorActive: Boolean,
    isPrivateMode: Boolean,
    isCurrentTabShorts: Boolean,
    isShortsMuted: Boolean,
    settings: BrowserSettings,
    mediaPlayerStatus: MediaPlayerStatus?,
    ideOpenTabs: List<IdeTab>,
    ideActiveTabId: String?,
    workspaceMode: XcodeWorkspaceMode,
    viewModel: BrowserViewModel,
    onShareUrl: (String) -> Unit
) {
    if (activeSheet == ActiveSheet.SafariActions || activeSheet == ActiveSheet.SafariPageMenu || activeSheet == ActiveSheet.ControlCentre || activeSheet == ActiveSheet.Inspector) {
        val activeEditorTab = remember(ideOpenTabs, ideActiveTabId) {
            ideOpenTabs.find { it.id == ideActiveTabId }
        }
        val requestedTabStr = viewModel.inspectorRequestedTab.value
        val computedInitialTab = remember(activeSheet, requestedTabStr) {
            if (activeSheet == ActiveSheet.Inspector) {
                when (requestedTabStr?.uppercase()) {
                    "PROBLEMS" -> SafariActionTab.PROBLEMS
                    "EXTENSIONS", "SIDE_PANEL", "SIDEPANEL", "SIDE_PANELS" -> SafariActionTab.EXTENSIONS
                    "CONSOLE" -> SafariActionTab.CONSOLE
                    "DOM", "DOM_INSPECT" -> SafariActionTab.DOM_INSPECT
                    "SECURITY" -> SafariActionTab.SECURITY
                    else -> SafariActionTab.ATTRIBUTES
                }
            } else {
                SafariActionTab.MAIN
            }
        }
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
            onOpenFiles = { viewModel.openFiles(isSelectionMode = false) },
            fileSystem = viewModel.fileSystem,
            onOpenFileInTab = { file, inNewTab -> viewModel.openFileInTab(file, inNewTab) },
            onOpenWebsiteConnectors = { viewModel.openSheet(ActiveSheet.WebsiteConnectors) },
            onOpenConnectorHub = { viewModel.openConnectorHub() },
            onOpenResearchWorkspace = { viewModel.openResearchWorkspace() },
            onOpenDataSaver = { viewModel.openDataSaver() },
            onOpenCommunicationHub = { viewModel.openCommunicationHub() },
            onOpenIde = { viewModel.openIde() },
            onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
            onNavigateToUrl = { url -> viewModel.loadUrlInCurrentTab(url) },
            onClearBrowsingData = { viewModel.clearBrowsingData() },
            activeEditorTab = activeEditorTab,
            workspaceMode = workspaceMode,
            activeWebView = currentTab?.let { viewModel.getActiveWebView(it.id) },
            initialTab = computedInitialTab,
            onClose = { viewModel.closeSheet() }
        )
    }
}
