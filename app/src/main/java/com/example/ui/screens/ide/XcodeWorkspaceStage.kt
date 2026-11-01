package com.example.ui.screens.ide

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.agent.BotAssistantStatus
import com.example.data.model.BrowserSettings
import com.example.data.model.BrowserTab
import com.example.ui.components.WebsiteDeveloperBar
import com.example.ui.screens.files.GVONEFileViewerScreen
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Dedicated central stage for non-browser Xcode Workspace modes:
 * Code Editor, Bot Chat, PDF Viewer, Image Viewer, Markdown Docs, JSON Data Inspector, Media Player.
 * Keeps BrowserApp method bytecode lightweight and modular.
 */
@Composable
fun XcodeWorkspaceStage(
    workspaceMode: XcodeWorkspaceMode,
    settings: BrowserSettings,
    effectiveAddressBarHeight: Dp,
    currentTab: BrowserTab?,
    tabs: List<BrowserTab>,
    currentTabId: String?,
    ideOpenTabs: List<IdeTab>,
    ideActiveTabId: String?,
    onSelectIdeTab: (String) -> Unit,
    onCloseIdeTab: (String) -> Unit,
    onNewFileTab: () -> Unit,
    onSaveFileContent: (String, String) -> Unit,
    activeBotPersona: GrokBotPersona,
    botMessages: List<BotChatMessage>,
    botAssistantStatus: BotAssistantStatus,
    botAssistantActiveTask: String?,
    viewModel: BrowserViewModel,
    mainScope: CoroutineScope,
    onAddressClick: () -> Unit,
    onSelectBrowserTabFromDevBar: (String) -> Unit,
    onNewBrowserTabFromDevBar: () -> Unit,
    onSelectIdeTabFromDevBar: (String) -> Unit,
    onNewIdeTabFromDevBar: () -> Unit,
    onOpenCodeFileFromDevBar: (String) -> Unit,
    onSelectBotPersonaFromDevBar: (GrokBotPersona) -> Unit,
    onSelectWorkspaceFileFromDevBar: (String, XcodeWorkspaceMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    if (workspaceMode == XcodeWorkspaceMode.CODE_EDITOR) {
        // Unified Central Workspace in CODE EDITOR mode (Xcode style)
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = if (settings.addressBarBottom) effectiveAddressBarHeight else 0.dp)
        ) {
            val activeEditorTab = remember(ideOpenTabs, ideActiveTabId) {
                ideOpenTabs.find { it.id == ideActiveTabId }
            }
            WebsiteDeveloperBar(
                tab = currentTab ?: tabs.firstOrNull() ?: BrowserTab(id = "code", title = "Xcode Code Editor", url = "xcode://editor"),
                activeWebView = null,
                isTorActive = settings.torEnabled,
                onToggleOff = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxWidth(),
                onOpenIde = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                workspaceMode = XcodeWorkspaceMode.CODE_EDITOR,
                onToggleWorkspaceMode = { viewModel.setWorkspaceMode(it) },
                activeFileName = activeEditorTab?.fileItem?.name,
                isFileDirty = activeEditorTab?.isDirty == true,
                onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
                onOpenConsole = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenNavigator = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onSaveFile = {
                    activeEditorTab?.let { tabItem ->
                        onSaveFileContent(tabItem.fileItem.path, tabItem.content)
                    }
                },
                onFormatCode = {
                    Toast.makeText(context, "Formatted code with Prettier", Toast.LENGTH_SHORT).show()
                },
                onAddressClick = onAddressClick,
                openTabs = tabs,
                currentTabId = currentTabId,
                onSelectBrowserTab = onSelectBrowserTabFromDevBar,
                onNewBrowserTab = onNewBrowserTabFromDevBar,
                ideTabs = ideOpenTabs,
                activeIdeTabId = ideActiveTabId,
                onSelectIdeTab = onSelectIdeTabFromDevBar,
                onNewIdeTab = onNewIdeTabFromDevBar,
                onOpenCodeFile = onOpenCodeFileFromDevBar,
                activeBotPersona = activeBotPersona,
                onSelectBotPersona = onSelectBotPersonaFromDevBar,
                onSelectWorkspaceFile = onSelectWorkspaceFileFromDevBar
            )

            XcodeWorkspaceEditor(
                openTabs = ideOpenTabs,
                activeTabId = ideActiveTabId,
                onSelectTab = onSelectIdeTab,
                onCloseTab = onCloseIdeTab,
                onNewFileTab = onNewFileTab,
                fileSystem = viewModel.fileSystem,
                coroutineScope = mainScope,
                onSaveFile = onSaveFileContent,
                onFormatDocument = {
                    Toast.makeText(context, "Formatted code with Prettier", Toast.LENGTH_SHORT).show()
                },
                onOpenNavigatorFiles = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onSwitchToBrowser = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (workspaceMode == XcodeWorkspaceMode.BOT_CHAT) {
        // Dedicated, full-featured Bot Chat tab in the central workspace stage
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = if (settings.addressBarBottom) effectiveAddressBarHeight else 0.dp)
        ) {
            WebsiteDeveloperBar(
                tab = currentTab ?: tabs.firstOrNull() ?: BrowserTab(id = "bot", title = "AI Copilot", url = "xcode://bots"),
                activeWebView = null,
                isTorActive = settings.torEnabled,
                onToggleOff = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxWidth(),
                onOpenIde = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.CODE_EDITOR) },
                workspaceMode = XcodeWorkspaceMode.BOT_CHAT,
                onToggleWorkspaceMode = { viewModel.setWorkspaceMode(it) },
                activeFileName = "Bot Chat: ${activeBotPersona.displayName}",
                isFileDirty = false,
                onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
                onOpenConsole = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenNavigator = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onAddressClick = onAddressClick,
                openTabs = tabs,
                currentTabId = currentTabId,
                onSelectBrowserTab = onSelectBrowserTabFromDevBar,
                onNewBrowserTab = onNewBrowserTabFromDevBar,
                ideTabs = ideOpenTabs,
                activeIdeTabId = ideActiveTabId,
                onSelectIdeTab = onSelectIdeTabFromDevBar,
                onNewIdeTab = onNewIdeTabFromDevBar,
                onOpenCodeFile = onOpenCodeFileFromDevBar,
                activeBotPersona = activeBotPersona,
                onSelectBotPersona = onSelectBotPersonaFromDevBar,
                onSelectWorkspaceFile = onSelectWorkspaceFileFromDevBar
            )

            // Dedicated Bot Chat Workspace Stage
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("workspace_bot_chat_stage"),
                color = Color(0xFF0D121F)
            ) {
                val activeEditorTab = remember(ideOpenTabs, ideActiveTabId) {
                    ideOpenTabs.find { it.id == ideActiveTabId }
                }
                IdeGrokBotsView(
                    activeTab = activeEditorTab,
                    openTabs = ideOpenTabs,
                    messages = botMessages,
                    selectedPersona = activeBotPersona,
                    isThinking = botAssistantStatus == BotAssistantStatus.WORKING,
                    assistantStatus = botAssistantStatus,
                    activeTask = botAssistantActiveTask,
                    onSendMessage = { message, ctx -> viewModel.sendBotMessage(message, ctx) },
                    onContextChanged = { viewModel.observeBotContext(it, "ide") },
                    onPersonaChange = { viewModel.setActiveBotPersona(it) },
                    onApplyCodeToEditor = { code ->
                        activeEditorTab?.let { tabItem ->
                            onSaveFileContent(tabItem.fileItem.path, code)
                        }
                    },
                    onCreateFileFromCode = { filename, code ->
                        mainScope.launch {
                            viewModel.fileSystem.createFile(viewModel.terminalTargetDirectory.value, filename, code)
                            onNewFileTab()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else if (workspaceMode == XcodeWorkspaceMode.PDF_VIEWER) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = if (settings.addressBarBottom) effectiveAddressBarHeight else 0.dp)
        ) {
            WebsiteDeveloperBar(
                tab = currentTab ?: tabs.firstOrNull() ?: BrowserTab(id = "pdf", title = "PDF Document", url = "xcode://pdf"),
                activeWebView = null,
                isTorActive = settings.torEnabled,
                onToggleOff = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxWidth(),
                workspaceMode = XcodeWorkspaceMode.PDF_VIEWER,
                onToggleWorkspaceMode = { viewModel.setWorkspaceMode(it) },
                activeFileName = "document.pdf",
                isFileDirty = false,
                onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
                onOpenConsole = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenNavigator = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onAddressClick = onAddressClick,
                openTabs = tabs,
                currentTabId = currentTabId,
                onSelectBrowserTab = onSelectBrowserTabFromDevBar,
                onNewBrowserTab = onNewBrowserTabFromDevBar,
                ideTabs = ideOpenTabs,
                activeIdeTabId = ideActiveTabId,
                onSelectIdeTab = onSelectIdeTabFromDevBar,
                onNewIdeTab = onNewIdeTabFromDevBar,
                onOpenCodeFile = onOpenCodeFileFromDevBar,
                activeBotPersona = activeBotPersona,
                onSelectBotPersona = onSelectBotPersonaFromDevBar,
                onSelectWorkspaceFile = onSelectWorkspaceFileFromDevBar
            )
            GVONEFileViewerScreen(
                fileRelativePath = "Downloads/document.pdf",
                fileSystem = viewModel.fileSystem,
                onCloseTab = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                onOpenTerminalWithCommand = { viewModel.openSheet(ActiveSheet.Terminal) },
                isDeveloperBarEnabled = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (workspaceMode == XcodeWorkspaceMode.IMAGE_VIEWER) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = if (settings.addressBarBottom) effectiveAddressBarHeight else 0.dp)
        ) {
            WebsiteDeveloperBar(
                tab = currentTab ?: tabs.firstOrNull() ?: BrowserTab(id = "image", title = "Image Viewer", url = "xcode://image"),
                activeWebView = null,
                isTorActive = settings.torEnabled,
                onToggleOff = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxWidth(),
                workspaceMode = XcodeWorkspaceMode.IMAGE_VIEWER,
                onToggleWorkspaceMode = { viewModel.setWorkspaceMode(it) },
                activeFileName = "preview.png",
                isFileDirty = false,
                onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
                onOpenConsole = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenNavigator = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onAddressClick = onAddressClick,
                openTabs = tabs,
                currentTabId = currentTabId,
                onSelectBrowserTab = onSelectBrowserTabFromDevBar,
                onNewBrowserTab = onNewBrowserTabFromDevBar,
                ideTabs = ideOpenTabs,
                activeIdeTabId = ideActiveTabId,
                onSelectIdeTab = onSelectIdeTabFromDevBar,
                onNewIdeTab = onNewIdeTabFromDevBar,
                onOpenCodeFile = onOpenCodeFileFromDevBar,
                activeBotPersona = activeBotPersona,
                onSelectBotPersona = onSelectBotPersonaFromDevBar,
                onSelectWorkspaceFile = onSelectWorkspaceFileFromDevBar
            )
            GVONEFileViewerScreen(
                fileRelativePath = "Images/preview.png",
                fileSystem = viewModel.fileSystem,
                onCloseTab = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                onOpenTerminalWithCommand = { viewModel.openSheet(ActiveSheet.Terminal) },
                isDeveloperBarEnabled = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (workspaceMode == XcodeWorkspaceMode.MARKDOWN) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = if (settings.addressBarBottom) effectiveAddressBarHeight else 0.dp)
        ) {
            WebsiteDeveloperBar(
                tab = currentTab ?: tabs.firstOrNull() ?: BrowserTab(id = "markdown", title = "Markdown & Docs", url = "xcode://markdown"),
                activeWebView = null,
                isTorActive = settings.torEnabled,
                onToggleOff = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxWidth(),
                workspaceMode = XcodeWorkspaceMode.MARKDOWN,
                onToggleWorkspaceMode = { viewModel.setWorkspaceMode(it) },
                activeFileName = "architecture.md",
                isFileDirty = false,
                onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
                onOpenConsole = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenNavigator = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onAddressClick = onAddressClick,
                openTabs = tabs,
                currentTabId = currentTabId,
                onSelectBrowserTab = onSelectBrowserTabFromDevBar,
                onNewBrowserTab = onNewBrowserTabFromDevBar,
                ideTabs = ideOpenTabs,
                activeIdeTabId = ideActiveTabId,
                onSelectIdeTab = onSelectIdeTabFromDevBar,
                onNewIdeTab = onNewIdeTabFromDevBar,
                onOpenCodeFile = onOpenCodeFileFromDevBar,
                activeBotPersona = activeBotPersona,
                onSelectBotPersona = onSelectBotPersonaFromDevBar,
                onSelectWorkspaceFile = onSelectWorkspaceFileFromDevBar
            )
            GVONEFileViewerScreen(
                fileRelativePath = "GVONE/architecture.md",
                fileSystem = viewModel.fileSystem,
                onCloseTab = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                onOpenTerminalWithCommand = { viewModel.openSheet(ActiveSheet.Terminal) },
                isDeveloperBarEnabled = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (workspaceMode == XcodeWorkspaceMode.DATA_INSPECTOR) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = if (settings.addressBarBottom) effectiveAddressBarHeight else 0.dp)
        ) {
            WebsiteDeveloperBar(
                tab = currentTab ?: tabs.firstOrNull() ?: BrowserTab(id = "data", title = "JSON & Data", url = "xcode://data"),
                activeWebView = null,
                isTorActive = settings.torEnabled,
                onToggleOff = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxWidth(),
                workspaceMode = XcodeWorkspaceMode.DATA_INSPECTOR,
                onToggleWorkspaceMode = { viewModel.setWorkspaceMode(it) },
                activeFileName = "package.json",
                isFileDirty = false,
                onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
                onOpenConsole = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenNavigator = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onAddressClick = onAddressClick,
                openTabs = tabs,
                currentTabId = currentTabId,
                onSelectBrowserTab = onSelectBrowserTabFromDevBar,
                onNewBrowserTab = onNewBrowserTabFromDevBar,
                ideTabs = ideOpenTabs,
                activeIdeTabId = ideActiveTabId,
                onSelectIdeTab = onSelectIdeTabFromDevBar,
                onNewIdeTab = onNewIdeTabFromDevBar,
                onOpenCodeFile = onOpenCodeFileFromDevBar,
                activeBotPersona = activeBotPersona,
                onSelectBotPersona = onSelectBotPersonaFromDevBar,
                onSelectWorkspaceFile = onSelectWorkspaceFileFromDevBar
            )
            GVONEFileViewerScreen(
                fileRelativePath = "Projects/ReactMobileUI/package.json",
                fileSystem = viewModel.fileSystem,
                onCloseTab = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                onOpenTerminalWithCommand = { viewModel.openSheet(ActiveSheet.Terminal) },
                isDeveloperBarEnabled = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else if (workspaceMode == XcodeWorkspaceMode.MEDIA_PLAYER) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(bottom = if (settings.addressBarBottom) effectiveAddressBarHeight else 0.dp)
        ) {
            WebsiteDeveloperBar(
                tab = currentTab ?: tabs.firstOrNull() ?: BrowserTab(id = "media", title = "Media Player", url = "xcode://media"),
                activeWebView = null,
                isTorActive = settings.torEnabled,
                onToggleOff = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                modifier = Modifier.fillMaxWidth(),
                workspaceMode = XcodeWorkspaceMode.MEDIA_PLAYER,
                onToggleWorkspaceMode = { viewModel.setWorkspaceMode(it) },
                activeFileName = "sample_track.wav",
                isFileDirty = false,
                onOpenInspector = { viewModel.openSheet(ActiveSheet.Inspector) },
                onOpenConsole = { viewModel.openSheet(ActiveSheet.Terminal) },
                onOpenNavigator = { viewModel.openSheet(ActiveSheet.TabOverview) },
                onAddressClick = onAddressClick,
                openTabs = tabs,
                currentTabId = currentTabId,
                onSelectBrowserTab = onSelectBrowserTabFromDevBar,
                onNewBrowserTab = onNewBrowserTabFromDevBar,
                ideTabs = ideOpenTabs,
                activeIdeTabId = ideActiveTabId,
                onSelectIdeTab = onSelectIdeTabFromDevBar,
                onNewIdeTab = onNewIdeTabFromDevBar,
                onOpenCodeFile = onOpenCodeFileFromDevBar,
                activeBotPersona = activeBotPersona,
                onSelectBotPersona = onSelectBotPersonaFromDevBar,
                onSelectWorkspaceFile = onSelectWorkspaceFileFromDevBar
            )
            GVONEFileViewerScreen(
                fileRelativePath = "Audio/sample_track.wav",
                fileSystem = viewModel.fileSystem,
                onCloseTab = { viewModel.setWorkspaceMode(XcodeWorkspaceMode.BROWSER) },
                onOpenTerminalWithCommand = { viewModel.openSheet(ActiveSheet.Terminal) },
                isDeveloperBarEnabled = false,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
