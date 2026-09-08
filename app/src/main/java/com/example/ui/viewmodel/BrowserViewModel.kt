package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MainActivity
import com.example.data.ai.GVONEAIService
import com.example.data.command.CommandEngine
import com.example.data.download.BrowserDownloadManager
import com.example.data.environment.EnvironmentManager
import com.example.data.model.*
import com.example.data.repository.BrowserRepository
import com.example.data.sync.*
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import com.example.data.tor.*
import com.example.ui.contextmenu.LinkContextMenuData
import com.example.ui.contextmenu.PagePreviewData
import com.example.data.webwidget.WebWidgetSelectionDraft
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.UUID

sealed interface ActiveSheet {
    object None : ActiveSheet
    object TabOverview : ActiveSheet
    object ControlCentre : ActiveSheet
    object SafariPageMenu : ActiveSheet
    object SafariActions : ActiveSheet
    object Settings : ActiveSheet
    object History : ActiveSheet
    object Bookmarks : ActiveSheet
    object Downloads : ActiveSheet
    object ReaderMode : ActiveSheet
    object AddToHomeScreen : ActiveSheet
    object SavedPasswords : ActiveSheet
    object SiteInfo : ActiveSheet
    object FindInPage : ActiveSheet
    object AISearchResult : ActiveSheet
    object TorDiagnostics : ActiveSheet
    object WebWidgetSelection : ActiveSheet
    object WebWidgetConfig : ActiveSheet
    object CustomCommands : ActiveSheet
    object Terminal : ActiveSheet
    object WebsiteConnector : ActiveSheet
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("gvone_settings_prefs", Context.MODE_PRIVATE)
    val repository = BrowserRepository(application)
    val torManager = TorManager()
    val downloadManager = BrowserDownloadManager(application, repository)
    val aiService = GVONEAIService(torManager)
    val terminalRepository = com.example.data.terminal.TerminalRepository(application)
    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    fun appendTerminalLine(text: String, type: TerminalLineType = TerminalLineType.OUTPUT) {
        appendTerminalLines(listOf(TerminalLine(text = text, type = type)))
    }

    fun appendTerminalLines(lines: List<TerminalLine>) {
        val current = _terminalLines.value.toMutableList()
        current.addAll(lines)
        while (current.size > 200) {
            current.removeAt(0)
        }
        _terminalLines.value = current
        terminalRepository.saveSessionLines(current)
    }

    fun clearTerminalLines() {
        _terminalLines.value = emptyList()
        terminalRepository.clearSavedSessionLines()
    }

    // Bridge for Browser <-> GVONE Search/Chat Web App Communication
    val webAppBridge = GVONEWebAppBridge(
        onStateChanged = { state ->
            val activeTab = currentTab.value
            val currentUrl = activeTab?.url.orEmpty()
            val host = try { java.net.URI(currentUrl).host.orEmpty().ifEmpty { currentUrl } } catch (_: Exception) { currentUrl }
            when (state) {
                WebAppConnectionState.READY -> {
                    appendTerminalLine(
                        "[BRIDGE] Connection SUCCESSFUL: Handshake verified with $host. Bidirectional InputRouter bridge ACTIVE.",
                        TerminalLineType.SUCCESS
                    )
                }
                WebAppConnectionState.CONNECTING -> {
                    appendTerminalLine(
                        "[BRIDGE] Initializing handshake with $host...",
                        TerminalLineType.INFO
                    )
                }
                WebAppConnectionState.PROCESSING -> {
                    appendTerminalLine(
                        "[BRIDGE] Web app is processing request...",
                        TerminalLineType.INFO
                    )
                }
                WebAppConnectionState.COMPLETED -> {
                    appendTerminalLine(
                        "[BRIDGE] Web app completed request processing.",
                        TerminalLineType.SUCCESS
                    )
                }
                WebAppConnectionState.UNAVAILABLE -> {
                    appendTerminalLine(
                        "[BRIDGE] Connection FAILED or UNAVAILABLE for $host.",
                        TerminalLineType.ERROR
                    )
                }
                WebAppConnectionState.IDLE -> {}
            }
        },
        onInputDelivered = { text, success ->
            if (success) {
                appendTerminalLine(
                    "[BRIDGE] Input delivered successfully to Web App: \"$text\"",
                    TerminalLineType.SUCCESS
                )
            } else {
                appendTerminalLine(
                    "[BRIDGE] Delivery FAILED for: \"$text\" (Web App DOM element not found or not responsive)",
                    TerminalLineType.ERROR
                )
            }
        }
    )
    val webAppConnectionState: StateFlow<WebAppConnectionState> = webAppBridge.connectionState

    // Map of active WebViews by tabId
    private val activeWebViews = mutableMapOf<String, WeakReference<WebView>>()

    fun registerWebView(tabId: String, webView: WebView) {
        activeWebViews[tabId] = WeakReference(webView)
    }

    fun unregisterWebView(tabId: String) {
        activeWebViews.remove(tabId)
    }

    fun getActiveWebView(tabId: String? = null): WebView? {
        val targetId = tabId ?: _currentTabId.value
        return activeWebViews[targetId]?.get()
    }

    // Tab State
    private val _allTabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String>("")
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    private val _isPrivateMode = MutableStateFlow(false)
    val isPrivateMode: StateFlow<Boolean> = _isPrivateMode.asStateFlow()

    private val _tabSortOption = MutableStateFlow(TabSortOption.DEFAULT)
    val tabSortOption: StateFlow<TabSortOption> = _tabSortOption.asStateFlow()

    // Tab Groups State
    private val _allTabGroups = MutableStateFlow<List<TabGroup>>(emptyList())
    private val _tabGroups = MutableStateFlow<List<TabGroup>>(emptyList())
    val tabGroups: StateFlow<List<TabGroup>> = _tabGroups.asStateFlow()

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId: StateFlow<String?> = _activeGroupId.asStateFlow()

    // Long-press Context Menu & Preview State
    private val _contextMenuData = MutableStateFlow<LinkContextMenuData?>(null)
    val contextMenuData: StateFlow<LinkContextMenuData?> = _contextMenuData.asStateFlow()

    private val _pagePreviewData = MutableStateFlow<PagePreviewData?>(null)
    val pagePreviewData: StateFlow<PagePreviewData?> = _pagePreviewData.asStateFlow()

    private val _groupPickerUrl = MutableStateFlow<String?>(null)
    val groupPickerUrl: StateFlow<String?> = _groupPickerUrl.asStateFlow()

    // Navigation and Active Sheet State
    private val _activeSheet = MutableStateFlow<ActiveSheet>(ActiveSheet.None)
    val activeSheet: StateFlow<ActiveSheet> = _activeSheet.asStateFlow()

    // Search & AI State
    private val _addressBarInput = MutableStateFlow("")
    val addressBarInput: StateFlow<String> = _addressBarInput.asStateFlow()

    private val _aiSearchResult = MutableStateFlow<GVONEAISearchResult?>(null)
    val aiSearchResult: StateFlow<GVONEAISearchResult?> = _aiSearchResult.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Find in page state
    private val _findQuery = MutableStateFlow("")
    val findQuery: StateFlow<String> = _findQuery.asStateFlow()
    private val _findMatchCount = MutableStateFlow(0)
    val findMatchCount: StateFlow<Int> = _findMatchCount.asStateFlow()
    private val _findCurrentIndex = MutableStateFlow(0)
    val findCurrentIndex: StateFlow<Int> = _findCurrentIndex.asStateFlow()

    // Custom Commands State
    val customCommands: StateFlow<List<CustomCommandEntity>> = repository.customCommands.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        CommandEngine.BUILT_IN_COMMANDS
    )

    // Custom Environment Start Page System
    val environmentManager = EnvironmentManager(application.applicationContext)
    val environments: StateFlow<List<Environment>> = environmentManager.environments
    val currentEnvironment: StateFlow<Environment> = environmentManager.currentEnvironment
    private val _isCanvasEditMode = MutableStateFlow(false)
    val isCanvasEditMode: StateFlow<Boolean> = _isCanvasEditMode.asStateFlow()

    // Web Portion Widget State
    private val _webWidgetDraft = MutableStateFlow<WebWidgetSelectionDraft?>(null)
    val webWidgetDraft: StateFlow<WebWidgetSelectionDraft?> = _webWidgetDraft.asStateFlow()

    fun setWebWidgetDraft(draft: WebWidgetSelectionDraft) {
        _webWidgetDraft.value = draft
        _activeSheet.value = ActiveSheet.WebWidgetConfig
    }

    fun addWebPortionWidget(widget: CanvasObject.WebPortionWidgetObject, targetEnvironmentId: String) {
        environmentManager.addObjectToEnvironment(widget, targetEnvironmentId)
        _webWidgetDraft.value = null
        _activeSheet.value = ActiveSheet.None
    }

    fun toggleCanvasEditMode() {
        _isCanvasEditMode.value = !_isCanvasEditMode.value
    }

    fun setCanvasEditMode(enabled: Boolean) {
        _isCanvasEditMode.value = enabled
    }

    // Website Access & Account Connector State
    val websiteAccessConnectorService = com.example.data.connector.WebsiteAccessConnectorService(application.applicationContext)
    private val _websiteAccessContext = MutableStateFlow<com.example.data.connector.WebsiteAccessContext?>(null)
    val websiteAccessContext: StateFlow<com.example.data.connector.WebsiteAccessContext?> = _websiteAccessContext.asStateFlow()

    private val _websiteAccessReport = MutableStateFlow<com.example.data.connector.WebsiteAccessReport?>(null)
    val websiteAccessReport: StateFlow<com.example.data.connector.WebsiteAccessReport?> = _websiteAccessReport.asStateFlow()

    fun openWebsiteConnector(context: com.example.data.connector.WebsiteAccessContext? = null) {
        val targetContext = context ?: currentTab.value?.let { tab ->
            com.example.data.connector.WebsiteAccessContext(
                currentTabId = tab.id,
                currentUrl = tab.url,
                currentDomain = com.example.data.connector.WebsiteAccessConnectorService.extractDomain(tab.url),
                currentEnvironmentId = currentEnvironment.value.id,
                currentEnvironmentName = currentEnvironment.value.name
            )
        } ?: com.example.data.connector.WebsiteAccessContext(
            currentTabId = "default",
            currentUrl = "gvone://newtab",
            currentDomain = "Start Page",
            currentEnvironmentId = currentEnvironment.value.id,
            currentEnvironmentName = currentEnvironment.value.name
        )
        _websiteAccessContext.value = targetContext
        _activeSheet.value = ActiveSheet.WebsiteConnector
        refreshWebsiteConnectorReport()
    }

    fun refreshWebsiteConnectorReport() {
        val ctx = _websiteAccessContext.value ?: return
        viewModelScope.launch {
            val perm = repository.getSitePermission(ctx.currentDomain)
            val rep = websiteAccessConnectorService.inspectWebsite(ctx, perm)
            _websiteAccessReport.value = rep
        }
    }

    fun clearCookiesForCurrentSite() {
        val ctx = _websiteAccessContext.value ?: return
        viewModelScope.launch {
            websiteAccessConnectorService.clearCookiesForDomain(ctx.currentUrl, ctx.currentDomain)
            refreshWebsiteConnectorReport()
        }
    }

    fun clearSiteDataForCurrentSite() {
        val ctx = _websiteAccessContext.value ?: return
        viewModelScope.launch {
            websiteAccessConnectorService.clearSiteDataForOrigin(ctx.currentDomain)
            refreshWebsiteConnectorReport()
        }
    }

    fun switchEnvironment(id: String) {
        val oldEnvId = environmentManager.activeEnvironmentId.value
        if (oldEnvId == id && _tabs.value.isNotEmpty()) return

        // 1. Save active tab & group for old environment
        prefs.edit()
            .putString("last_active_tab_$oldEnvId", _currentTabId.value)
            .putString("last_active_group_$oldEnvId", _activeGroupId.value)
            .apply()

        // 2. Switch environment in EnvironmentManager
        environmentManager.switchEnvironment(id)
        val targetEnv = environmentManager.currentEnvironment.value

        // 3. Load & sync tabs and groups for targetEnv
        activateEnvironmentTabsAndGroups(targetEnv)
    }

    fun createEnvironment(
        name: String,
        icon: String,
        theme: String = "dark",
        preset: String = "aurora",
        initialLinkUrl: String? = null,
        initialLinkTitle: String? = null
    ): Environment {
        val newEnv = environmentManager.createEnvironment(name, icon, theme, preset, initialLinkUrl, initialLinkTitle)
        if (!newEnv.startPageUrl.isNullOrBlank()) {
            val linkTab = BrowserTab(
                id = UUID.randomUUID().toString(),
                title = initialLinkTitle?.ifBlank { null } ?: (if (newEnv.startPageUrl.contains("rssgroupfeed")) "RSS Group Feed" else name),
                url = newEnv.startPageUrl,
                isPrivate = false,
                tabGroupId = null,
                environmentId = newEnv.id
            )
            _allTabs.value = _allTabs.value + linkTab
            viewModelScope.launch {
                repository.saveTab(linkTab)
            }
        }
        switchEnvironment(newEnv.id)
        return newEnv
    }

    fun updateEnvironment(env: Environment) {
        environmentManager.updateEnvironment(env)
    }

    fun duplicateEnvironment(id: String) {
        val oldActiveId = environmentManager.activeEnvironmentId.value
        prefs.edit()
            .putString("last_active_tab_$oldActiveId", _currentTabId.value)
            .putString("last_active_group_$oldActiveId", _activeGroupId.value)
            .apply()

        environmentManager.duplicateEnvironment(id)
        val newEnv = environmentManager.currentEnvironment.value
        val originalTabs = _allTabs.value.filter { it.environmentId == id }
        val originalGroups = _allTabGroups.value.filter { it.environmentId == id }

        val groupIdMap = mutableMapOf<String, String>()
        val clonedGroups = originalGroups.map { g ->
            val newGId = UUID.randomUUID().toString()
            groupIdMap[g.id] = newGId
            g.copy(id = newGId, environmentId = newEnv.id)
        }
        val clonedTabs = originalTabs.map { t ->
            t.copy(
                id = UUID.randomUUID().toString(),
                environmentId = newEnv.id,
                tabGroupId = t.tabGroupId?.let { groupIdMap[it] }
            )
        }
        _allTabGroups.value = _allTabGroups.value + clonedGroups
        _allTabs.value = _allTabs.value + clonedTabs
        viewModelScope.launch {
            repository.saveGroups(clonedGroups)
            repository.saveTabs(clonedTabs)
        }
        activateEnvironmentTabsAndGroups(newEnv)
    }

    fun deleteEnvironment(id: String) {
        environmentManager.deleteEnvironment(id)
        viewModelScope.launch {
            repository.deleteTabsByEnvironment(id)
            repository.deleteGroupsByEnvironment(id)
        }
        _allTabs.value = _allTabs.value.filter { it.environmentId != id }
        _allTabGroups.value = _allTabGroups.value.filter { it.environmentId != id }
        activateEnvironmentTabsAndGroups(environmentManager.currentEnvironment.value)
    }

    private fun activateEnvironmentTabsAndGroups(env: Environment) {
        val envId = env.id
        var envTabs = _allTabs.value.filter { it.environmentId == envId }
        var envGroups = _allTabGroups.value.filter { it.environmentId == envId }

        if (envTabs.isEmpty()) {
            val startUrl = if (!env.startPageUrl.isNullOrBlank()) {
                env.startPageUrl
            } else {
                START_PAGE_URL
            }
            val startTitle = when {
                envId == "personal" || startUrl.contains("rssgroupfeed") -> "RSS Group Feed"
                isInternalHomeUrl(startUrl) -> "Start Page"
                else -> "${env.name} Start"
            }
            val initialTab = BrowserTab(
                id = UUID.randomUUID().toString(),
                title = startTitle,
                url = startUrl,
                isPrivate = false,
                tabGroupId = null,
                environmentId = envId
            )
            envTabs = listOf(initialTab)
            _allTabs.value = _allTabs.value + initialTab
            viewModelScope.launch {
                repository.saveTab(initialTab)
            }
        }

        if (envGroups.isEmpty()) {
            if (envId == "work") {
                val workGroup = TabGroup(
                    id = UUID.randomUUID().toString(),
                    name = "Work",
                    order = 0,
                    colorHex = "#3B82F6",
                    environmentId = "work"
                )
                envGroups = listOf(workGroup)
                _allTabGroups.value = _allTabGroups.value + workGroup
                viewModelScope.launch { repository.saveGroup(workGroup) }
            } else if (envId == "study") {
                val studyGroup = TabGroup(
                    id = UUID.randomUUID().toString(),
                    name = "Study",
                    order = 0,
                    colorHex = "#10B981",
                    environmentId = "study"
                )
                envGroups = listOf(studyGroup)
                _allTabGroups.value = _allTabGroups.value + studyGroup
                viewModelScope.launch { repository.saveGroup(studyGroup) }
            }
        }

        _tabs.value = envTabs
        _tabGroups.value = envGroups

        val savedTabId = prefs.getString("last_active_tab_$envId", null)
        val targetTab = envTabs.find { it.id == savedTabId } ?: envTabs.first()
        _currentTabId.value = targetTab.id
        _isPrivateMode.value = targetTab.isPrivate
        _activeGroupId.value = targetTab.tabGroupId
        _addressBarInput.value = if (isInternalHomeUrl(targetTab.url)) "" else targetTab.url

        persistTabsAndActiveState()
        persistGroups()
    }

    fun addCanvasObject(obj: CanvasObject) {
        environmentManager.addObject(obj)
    }

    fun updateCanvasObject(obj: CanvasObject) {
        environmentManager.updateObject(obj)
    }

    fun deleteCanvasObject(id: String) {
        environmentManager.removeObject(id)
    }

    fun reorderCanvasObjects(newObjects: List<CanvasObject>) {
        val current = environmentManager.currentEnvironment.value
        environmentManager.updateEnvironment(current.copy(objects = newObjects))
    }

    fun updateCanvasBackground(bg: EnvironmentBackground) {
        environmentManager.updateBackground(bg)
    }

    fun updateCanvasLayoutMode(mode: EnvironmentLayoutMode) {
        environmentManager.updateLayoutMode(mode)
    }

    fun isStartPage(url: String?): Boolean = isInternalHomeUrl(url)

    // Settings
    private val _settings = MutableStateFlow(loadPersistedSettings())
    val settings: StateFlow<BrowserSettings> = _settings.asStateFlow()

    // History and Bookmarks
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bookmarks = repository.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloads = repository.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val torStatus = torManager.torStatus
    val torTestResult: StateFlow<TorTestResult?> = torManager.testResult
    val isTorTesting: StateFlow<Boolean> = torManager.isTesting
    val diagnosticReport: StateFlow<TorDiagnosticReport?> = torManager.diagnosticReport
    val isDiagnosing: StateFlow<Boolean> = torManager.isDiagnosing

    val currentTab: StateFlow<BrowserTab?> = combine(_tabs, _currentTabId) { tabsList, currentId ->
        tabsList.find { it.id == currentId } ?: tabsList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        val savedTerminalLines = terminalRepository.getSavedSessionLines()
        if (savedTerminalLines.isNotEmpty()) {
            _terminalLines.value = savedTerminalLines
        } else {
            _terminalLines.value = listOf(
                TerminalLine("GVONE COMMAND & BRIDGE ENGINE v2.4", TerminalLineType.SYSTEM),
                TerminalLine("Connected to Address Bar & Bidirectional Bridge Runtime.", TerminalLineType.INFO),
                TerminalLine("Commands and Bridge status events will appear here in real time.", TerminalLineType.OUTPUT)
            )
        }

        val initialPersonalTabs = listOf(
            BrowserTab(
                id = "tab_rss_feed",
                title = "RSS Group Feed",
                url = "https://rssgroupfeed-jaelvwfd.manus.space",
                faviconUrl = null,
                isPrivate = false,
                tabGroupId = null,
                environmentId = "personal"
            ),
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "DuckDuckGo — Privacy, Simplified.",
                url = "https://duckduckgo.com",
                faviconUrl = "https://duckduckgo.com/favicon.ico",
                isPrivate = false,
                tabGroupId = null,
                environmentId = "personal"
            )
        )

        val initialWorkGroupId = UUID.randomUUID().toString()
        val defaultWorkGroup = TabGroup(
            id = initialWorkGroupId,
            name = "Work",
            order = 0,
            colorHex = "#3B82F6",
            environmentId = "work"
        )
        val defaultStudyGroup = TabGroup(
            id = UUID.randomUUID().toString(),
            name = "Study",
            order = 0,
            colorHex = "#10B981",
            environmentId = "study"
        )

        val initialWorkTabs = listOf(
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Physicists Reveal a Quantum Geometry",
                url = "https://www.quantamagazine.org",
                faviconUrl = "https://www.quantamagazine.org/favicon.ico",
                isPrivate = false,
                tabGroupId = initialWorkGroupId,
                environmentId = "work"
            ),
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Apple",
                url = "https://www.apple.com",
                faviconUrl = "https://www.apple.com/favicon.ico",
                isPrivate = false,
                tabGroupId = initialWorkGroupId,
                environmentId = "work"
            )
        )

        val initialStudyTabs = listOf(
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Wikipedia",
                url = "https://en.wikipedia.org",
                faviconUrl = "https://en.wikipedia.org/favicon.ico",
                isPrivate = false,
                tabGroupId = defaultStudyGroup.id,
                environmentId = "study"
            )
        )

        val allInitialTabs = initialPersonalTabs + initialWorkTabs + initialStudyTabs
        val allInitialGroups = listOf(defaultWorkGroup, defaultStudyGroup)

        _allTabGroups.value = allInitialGroups
        _allTabs.value = allInitialTabs

        val activeEnv = environmentManager.currentEnvironment.value
        val initialActiveTabs = allInitialTabs.filter { it.environmentId == activeEnv.id }.ifEmpty { initialPersonalTabs }
        val initialActiveGroups = allInitialGroups.filter { it.environmentId == activeEnv.id }
        _tabGroups.value = initialActiveGroups
        _tabs.value = initialActiveTabs
        _currentTabId.value = initialActiveTabs.first().id
        _activeGroupId.value = initialActiveTabs.first().tabGroupId
        _addressBarInput.value = if (isInternalHomeUrl(initialActiveTabs.first().url)) "" else initialActiveTabs.first().url

        // Restore tab groups and tabs from Room database asynchronously
        viewModelScope.launch {
            repository.tabGroups.take(1).collect { savedGroups ->
                val resolvedGroups = if (savedGroups.isNotEmpty()) {
                    savedGroups.map { if (it.environmentId.isBlank()) it.copy(environmentId = "personal") else it }
                } else {
                    repository.saveGroups(allInitialGroups)
                    allInitialGroups
                }
                _allTabGroups.value = resolvedGroups
                val curEnvId = environmentManager.activeEnvironmentId.value
                _tabGroups.value = resolvedGroups.filter { it.environmentId == curEnvId }
            }
        }

        viewModelScope.launch {
            repository.savedTabs.take(1).collect { savedTabsList ->
                val resolvedTabs = if (savedTabsList.isNotEmpty()) {
                    savedTabsList.map { if (it.environmentId.isBlank()) it.copy(environmentId = "personal") else it }
                } else {
                    repository.saveTabs(allInitialTabs)
                    allInitialTabs
                }
                _allTabs.value = resolvedTabs
                val curEnv = environmentManager.currentEnvironment.value
                activateEnvironmentTabsAndGroups(curEnv)
            }
        }

        // If Tor was persistent and enabled across restarts, connect immediately
        if (_settings.value.torEnabled) {
            viewModelScope.launch {
                torManager.connect(host = _settings.value.torProxyHost, port = _settings.value.torProxyPort)
            }
        }

        // Connect WebView context menu detection bridge to ViewModel
        webAppBridge.onContextMenuListener = { data ->
            triggerContextMenu(data)
        }

        // Seed default custom commands in Room
        viewModelScope.launch {
            repository.seedDefaultCommandsIfEmpty()
        }
    }

    private fun loadPersistedSettings(): BrowserSettings {
        val torEnabled = prefs.getBoolean("tor_enabled", false)
        val torHost = prefs.getString("tor_host", "127.0.0.1") ?: "127.0.0.1"
        val torPort = prefs.getInt("tor_port", 9050)
        val engineName = prefs.getString("search_engine", SearchEngineType.GVONE.name) ?: SearchEngineType.GVONE.name
        val engine = try { SearchEngineType.valueOf(engineName) } catch (e: Exception) { SearchEngineType.GVONE }
        val tracking = prefs.getBoolean("tracking_protection", true)
        val popups = prefs.getBoolean("block_popups", true)
        val https = prefs.getBoolean("force_https", true)
        val addressBottom = prefs.getBoolean("address_bar_bottom", true)
        val aiAuto = prefs.getBoolean("ai_search_auto_trigger", true)
        val autoLoad = prefs.getBoolean("auto_load_target_on_focus", false)
        val autoTargetUrl = prefs.getString("auto_load_target_url", ADDRESS_BAR_TARGET_URL) ?: ADDRESS_BAR_TARGET_URL
        val bridgeEnabled = prefs.getBoolean("bidirectional_bridge_enabled", true)
        val bridgeApplyAll = prefs.getBoolean("bridge_apply_all_websites", false)
        val termAutoAppear = prefs.getBoolean("terminal_auto_appear_address_bar", false)
        val shortsModeName = prefs.getString("shorts_audio_mode", ShortsAudioMode.ALWAYS_UNMUTED.name) ?: ShortsAudioMode.ALWAYS_UNMUTED.name
        val shortsMode = try { ShortsAudioMode.valueOf(shortsModeName) } catch (_: Exception) { ShortsAudioMode.ALWAYS_UNMUTED }
        val bgPlay = prefs.getBoolean("background_play_enabled", true)

        return BrowserSettings(
            searchEngine = engine,
            trackingProtection = tracking,
            blockPopups = popups,
            forceHttps = https,
            torEnabled = torEnabled,
            torProxyHost = torHost,
            torProxyPort = torPort,
            addressBarBottom = addressBottom,
            aiSearchAutoTrigger = aiAuto,
            autoLoadTargetOnFocus = autoLoad,
            autoLoadTargetUrl = autoTargetUrl,
            bidirectionalBridgeEnabled = bridgeEnabled,
            bridgeApplyToAllWebsites = bridgeApplyAll,
            terminalAutoAppearOnAddressBar = termAutoAppear,
            shortsAudioMode = shortsMode,
            backgroundPlayEnabled = bgPlay
        )
    }

    private fun persistSettings(s: BrowserSettings) {
        prefs.edit()
            .putBoolean("tor_enabled", s.torEnabled)
            .putString("tor_host", s.torProxyHost)
            .putInt("tor_port", s.torProxyPort)
            .putString("search_engine", s.searchEngine.name)
            .putBoolean("tracking_protection", s.trackingProtection)
            .putBoolean("block_popups", s.blockPopups)
            .putBoolean("force_https", s.forceHttps)
            .putBoolean("address_bar_bottom", s.addressBarBottom)
            .putBoolean("ai_search_auto_trigger", s.aiSearchAutoTrigger)
            .putBoolean("auto_load_target_on_focus", s.autoLoadTargetOnFocus)
            .putString("auto_load_target_url", s.autoLoadTargetUrl)
            .putBoolean("bidirectional_bridge_enabled", s.bidirectionalBridgeEnabled)
            .putBoolean("bridge_apply_all_websites", s.bridgeApplyToAllWebsites)
            .putBoolean("terminal_auto_appear_address_bar", s.terminalAutoAppearOnAddressBar)
            .putString("shorts_audio_mode", s.shortsAudioMode.name)
            .putBoolean("background_play_enabled", s.backgroundPlayEnabled)
            .apply()
    }

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
    }

    fun closeSheet() {
        _activeSheet.value = ActiveSheet.None
        _webWidgetDraft.value = null
    }

    fun setAddressBarInput(text: String) {
        _addressBarInput.value = text
    }

    fun setPrivateMode(isPrivate: Boolean) {
        _isPrivateMode.value = isPrivate
        val matchingTabs = _tabs.value.filter { it.isPrivate == isPrivate }
        if (matchingTabs.isNotEmpty()) {
            _currentTabId.value = matchingTabs.first().id
        } else {
            createNewTab(isPrivate = isPrivate)
        }
    }

    private fun persistTabsAndActiveState() {
        val currentEnvId = environmentManager.activeEnvironmentId.value
        val otherTabs = _allTabs.value.filter { it.environmentId != currentEnvId }
        val allUpdated = otherTabs + _tabs.value
        _allTabs.value = allUpdated
        viewModelScope.launch {
            repository.saveTabs(_tabs.value)
            prefs.edit()
                .putString("last_active_tab_id", _currentTabId.value)
                .putString("last_active_group_id", _activeGroupId.value)
                .putString("last_active_tab_$currentEnvId", _currentTabId.value)
                .putString("last_active_group_$currentEnvId", _activeGroupId.value)
                .apply()
        }
    }

    private fun persistGroups() {
        val currentEnvId = environmentManager.activeEnvironmentId.value
        val otherGroups = _allTabGroups.value.filter { it.environmentId != currentEnvId }
        val allUpdated = otherGroups + _tabGroups.value
        _allTabGroups.value = allUpdated
        viewModelScope.launch {
            repository.saveGroups(_tabGroups.value)
        }
    }

    fun selectTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId }
        if (tab != null) {
            _currentTabId.value = tab.id
            _isPrivateMode.value = tab.isPrivate
            _activeGroupId.value = tab.tabGroupId
            _addressBarInput.value = if (isInternalHomeUrl(tab.url)) "" else tab.url
            persistTabsAndActiveState()
            closeSheet()
        }
    }

    fun createNewTab(
        url: String? = null,
        isPrivate: Boolean = _isPrivateMode.value,
        groupId: String? = _activeGroupId.value,
        inBackground: Boolean = false
    ) {
        val currentEnv = environmentManager.currentEnvironment.value
        val defaultUrl = if (!currentEnv.startPageUrl.isNullOrBlank()) currentEnv.startPageUrl else START_PAGE_URL
        val targetUrl = url ?: defaultUrl
        val targetTitle = when {
            isInternalHomeUrl(targetUrl) -> "Start Page"
            targetUrl.contains("rssgroupfeed") -> "RSS Group Feed"
            targetUrl == currentEnv.startPageUrl -> "${currentEnv.name} Start"
            else -> "New Tab"
        }
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            title = targetTitle,
            url = targetUrl,
            isPrivate = isPrivate,
            tabGroupId = groupId,
            environmentId = currentEnv.id
        )
        _tabs.value = _tabs.value + newTab
        if (!inBackground) {
            _currentTabId.value = newTab.id
            _activeGroupId.value = groupId
            _addressBarInput.value = if (isInternalHomeUrl(targetUrl)) "" else targetUrl
            closeSheet()
        }
        persistTabsAndActiveState()
    }

    fun closeCurrentTab() {
        val currentId = _currentTabId.value
        if (currentId.isNotBlank()) {
            closeTab(currentId)
        }
    }

    fun closeTab(tabId: String) {
        val currentList = _tabs.value
        val index = currentList.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val targetTab = currentList[index]
        val newList = currentList.filter { it.id != tabId }
        _tabs.value = newList
        _allTabs.value = _allTabs.value.filter { it.id != tabId }
        viewModelScope.launch {
            repository.deleteTab(tabId)
        }

        if (newList.isEmpty()) {
            createNewTab(isPrivate = _isPrivateMode.value, groupId = targetTab.tabGroupId)
        } else if (_currentTabId.value == tabId) {
            val nextIndex = (index - 1).coerceAtLeast(0)
            val nextTab = newList.getOrNull(nextIndex) ?: newList.first()
            _currentTabId.value = nextTab.id
            _isPrivateMode.value = nextTab.isPrivate
            _activeGroupId.value = nextTab.tabGroupId
        }
        persistTabsAndActiveState()
    }

    fun closeAllTabs(isPrivateOnly: Boolean = false) {
        val currentEnvId = environmentManager.activeEnvironmentId.value
        if (isPrivateOnly) {
            val toDelete = _tabs.value.filter { it.isPrivate }
            viewModelScope.launch {
                toDelete.forEach { repository.deleteTab(it.id) }
            }
            val remaining = _tabs.value.filter { !it.isPrivate }
            _tabs.value = remaining
            _allTabs.value = _allTabs.value.filter { !(it.environmentId == currentEnvId && it.isPrivate) }
            if (remaining.isEmpty()) {
                createNewTab(isPrivate = false, groupId = null)
            } else {
                _currentTabId.value = remaining.first().id
                _isPrivateMode.value = false
                _activeGroupId.value = remaining.first().tabGroupId
            }
        } else {
            val toDelete = _tabs.value
            viewModelScope.launch {
                toDelete.forEach { repository.deleteTab(it.id) }
            }
            _tabs.value = emptyList()
            _allTabs.value = _allTabs.value.filter { it.environmentId != currentEnvId }
            createNewTab(isPrivate = false, groupId = null)
        }
        persistTabsAndActiveState()
    }

    // --- Tab Groups Operations ---

    fun createTabGroup(name: String, colorHex: String? = null, initialTabIds: List<String> = emptyList()): String {
        val cleanName = name.trim().ifBlank { "New Folder" }
        val newGroupId = UUID.randomUUID().toString()
        val currentEnvId = environmentManager.activeEnvironmentId.value
        val newGroup = TabGroup(
            id = newGroupId,
            name = cleanName,
            order = _tabGroups.value.size,
            colorHex = colorHex ?: "#3B82F6",
            environmentId = currentEnvId
        )
        val updatedGroups = _tabGroups.value + newGroup
        _tabGroups.value = updatedGroups

        if (initialTabIds.isNotEmpty()) {
            _tabs.value = _tabs.value.map { tab ->
                if (initialTabIds.contains(tab.id)) {
                    tab.copy(tabGroupId = newGroupId)
                } else tab
            }
        }
        _activeGroupId.value = newGroupId
        persistGroups()
        persistTabsAndActiveState()
        return newGroupId
    }

    fun renameTabGroup(groupId: String, newName: String) {
        val cleanName = newName.trim().ifBlank { "Folder" }
        _tabGroups.value = _tabGroups.value.map {
            if (it.id == groupId) it.copy(name = cleanName) else it
        }
        persistGroups()
    }

    fun deleteTabGroup(groupId: String, closeTabs: Boolean) {
        _tabGroups.value = _tabGroups.value.filter { it.id != groupId }
        _allTabGroups.value = _allTabGroups.value.filter { it.id != groupId }
        viewModelScope.launch {
            repository.deleteGroup(groupId)
        }

        if (closeTabs) {
            val tabsToClose = _tabs.value.filter { it.tabGroupId == groupId }
            viewModelScope.launch {
                tabsToClose.forEach { repository.deleteTab(it.id) }
            }
            val remainingTabs = _tabs.value.filter { it.tabGroupId != groupId }
            _tabs.value = remainingTabs
            _allTabs.value = _allTabs.value.filter { it.tabGroupId != groupId }
            if (remainingTabs.isEmpty()) {
                createNewTab(isPrivate = _isPrivateMode.value, groupId = null)
            } else if (_currentTabId.value in tabsToClose.map { it.id }) {
                val nextTab = remainingTabs.first()
                _currentTabId.value = nextTab.id
                _isPrivateMode.value = nextTab.isPrivate
                _activeGroupId.value = nextTab.tabGroupId
            }
        } else {
            // Keep tabs, move them to Ungrouped
            _tabs.value = _tabs.value.map {
                if (it.tabGroupId == groupId) it.copy(tabGroupId = null) else it
            }
        }

        if (_activeGroupId.value == groupId) {
            _activeGroupId.value = null
        }
        persistGroups()
        persistTabsAndActiveState()
    }

    fun moveTabToGroup(tabId: String, targetGroupId: String?) {
        _tabs.value = _tabs.value.map {
            if (it.id == tabId) it.copy(tabGroupId = targetGroupId) else it
        }
        if (_currentTabId.value == tabId) {
            _activeGroupId.value = targetGroupId
        }
        persistTabsAndActiveState()
    }

    fun moveTabsToGroup(tabIds: List<String>, targetGroupId: String?) {
        _tabs.value = _tabs.value.map {
            if (it.id in tabIds) it.copy(tabGroupId = targetGroupId) else it
        }
        if (_currentTabId.value in tabIds) {
            _activeGroupId.value = targetGroupId
        }
        persistTabsAndActiveState()
    }

    fun removeTabFromGroup(tabId: String) {
        moveTabToGroup(tabId, null)
    }

    fun closeTabsInGroup(groupId: String) {
        val tabsInGroup = _tabs.value.filter { it.tabGroupId == groupId }
        viewModelScope.launch {
            tabsInGroup.forEach { repository.deleteTab(it.id) }
        }
        val remaining = _tabs.value.filter { it.tabGroupId != groupId }
        _tabs.value = remaining
        _allTabs.value = _allTabs.value.filter { it.tabGroupId != groupId }
        if (remaining.isEmpty()) {
            createNewTab(isPrivate = _isPrivateMode.value, groupId = groupId)
        } else if (_currentTabId.value in tabsInGroup.map { it.id }) {
            val nextTab = remaining.first()
            _currentTabId.value = nextTab.id
            _isPrivateMode.value = nextTab.isPrivate
            _activeGroupId.value = nextTab.tabGroupId
        }
        persistTabsAndActiveState()
    }

    fun duplicateTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId } ?: return
        val newTab = tab.copy(
            id = UUID.randomUUID().toString(),
            title = tab.title,
            url = tab.url,
            environmentId = tab.environmentId,
            createdAt = System.currentTimeMillis(),
            lastAccessedAt = System.currentTimeMillis()
        )
        _tabs.value = _tabs.value + newTab
        persistTabsAndActiveState()
    }

    fun closeOtherTabs(tabId: String) {
        val targetTab = _tabs.value.find { it.id == tabId } ?: return
        val tabsToKeep = _tabs.value.filter { it.id == tabId || it.isPrivate != targetTab.isPrivate }
        val tabsToRemove = _tabs.value.filter { it !in tabsToKeep }
        viewModelScope.launch {
            tabsToRemove.forEach { repository.deleteTab(it.id) }
        }
        _tabs.value = tabsToKeep
        _currentTabId.value = targetTab.id
        _activeGroupId.value = targetTab.tabGroupId
        persistTabsAndActiveState()
    }

    fun closeTabsToRight(tabId: String) {
        val currentList = _tabs.value.filter { it.isPrivate == _isPrivateMode.value }
        val index = currentList.indexOfFirst { it.id == tabId }
        if (index == -1 || index >= currentList.size - 1) return

        val toRemove = currentList.subList(index + 1, currentList.size).map { it.id }.toSet()
        viewModelScope.launch {
            toRemove.forEach { repository.deleteTab(it) }
        }
        _tabs.value = _tabs.value.filter { it.id !in toRemove }
        if (_currentTabId.value in toRemove) {
            _currentTabId.value = tabId
        }
        persistTabsAndActiveState()
    }

    fun reorderGroups(reordered: List<TabGroup>) {
        val updated = reordered.mapIndexed { idx, group -> group.copy(order = idx) }
        _tabGroups.value = updated
        persistGroups()
    }

    fun reorderTabsInGroup(groupId: String?, fromIndex: Int, toIndex: Int) {
        val groupTabs = _tabs.value.filter { it.tabGroupId == groupId }.toMutableList()
        if (fromIndex in groupTabs.indices && toIndex in groupTabs.indices) {
            val moved = groupTabs.removeAt(fromIndex)
            groupTabs.add(toIndex, moved)
            val otherTabs = _tabs.value.filter { it.tabGroupId != groupId }
            _tabs.value = groupTabs + otherTabs
            persistTabsAndActiveState()
        }
    }

    fun setActiveGroup(groupId: String?) {
        _activeGroupId.value = groupId
        prefs.edit().putString("last_active_group_id", groupId).apply()
    }

    fun switchToNextTab() {
        val currentList = _tabs.value.filter { it.isPrivate == _isPrivateMode.value }
        if (currentList.size <= 1) return
        val currentIndex = currentList.indexOfFirst { it.id == _currentTabId.value }
        val nextIndex = (currentIndex + 1) % currentList.size
        _currentTabId.value = currentList[nextIndex].id
    }

    fun switchToPreviousTab() {
        val currentList = _tabs.value.filter { it.isPrivate == _isPrivateMode.value }
        if (currentList.size <= 1) return
        val currentIndex = currentList.indexOfFirst { it.id == _currentTabId.value }
        val prevIndex = if (currentIndex - 1 < 0) currentList.size - 1 else currentIndex - 1
        _currentTabId.value = currentList[prevIndex].id
    }

    fun sortTabs(option: TabSortOption) {
        _tabSortOption.value = option
        _tabs.value = when (option) {
            TabSortOption.BY_TITLE -> _tabs.value.sortedBy { it.title.lowercase() }
            TabSortOption.BY_WEBSITE -> _tabs.value.sortedBy { it.url.lowercase() }
            TabSortOption.DEFAULT -> _tabs.value.sortedBy { it.createdAt }
        }
    }

    fun updateTabState(
        tabId: String,
        title: String? = null,
        url: String? = null,
        faviconUrl: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null
    ) {
        val currentId = _currentTabId.value
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) {
                var updated = tab
                title?.let { updated = updated.copy(title = it) }
                url?.let {
                    updated = updated.copy(url = it)
                    if (tab.id == currentId && !isInternalHomeUrl(it)) {
                        _addressBarInput.value = it
                    }
                    if (!tab.isPrivate && !isInternalHomeUrl(it) && !it.startsWith("about:") && !it.startsWith("chrome:")) {
                        viewModelScope.launch {
                            repository.addHistory(
                                HistoryEntry(
                                    title = updated.title.ifBlank { it },
                                    url = it,
                                    faviconUrl = updated.faviconUrl
                                )
                            )
                        }
                    }
                }
                faviconUrl?.let { updated = updated.copy(faviconUrl = it) }
                isLoading?.let { updated = updated.copy(isLoading = it) }
                progress?.let { updated = updated.copy(progress = it) }
                updated
            } else {
                tab
            }
        }
        if (isLoading == false || url != null || title != null) {
            persistTabsAndActiveState()
        }
    }

    fun updateCurrentTabState(
        title: String? = null,
        url: String? = null,
        faviconUrl: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null
    ) {
        updateTabState(_currentTabId.value, title, url, faviconUrl, isLoading, progress)
    }

    fun toggleDesktopMode() {
        val currentId = _currentTabId.value
        _tabs.value = _tabs.value.map {
            if (it.id == currentId) it.copy(desktopMode = !it.desktopMode) else it
        }
    }

    fun addBookmark() {
        val current = currentTab.value ?: return
        if (isInternalHomeUrl(current.url)) return
        viewModelScope.launch {
            repository.addBookmark(
                BookmarkEntry(
                    title = current.title,
                    url = current.url,
                    folder = "Favorites",
                    faviconUrl = current.faviconUrl
                )
            )
        }
    }

    fun addToFavorites() {
        addBookmark()
    }

    fun addToReadingList() {
        val current = currentTab.value ?: return
        if (isInternalHomeUrl(current.url)) return
        viewModelScope.launch {
            repository.addBookmark(
                BookmarkEntry(
                    title = current.title,
                    url = current.url,
                    folder = "Reading List",
                    faviconUrl = current.faviconUrl,
                    isReadingList = true
                )
            )
        }
    }

    fun navigateTo(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return

        // Always log input to persistent command history and Terminal session
        terminalRepository.addCommandToHistory(trimmed)
        appendTerminalLine("gvone@addressbar:~$ $trimmed", TerminalLineType.COMMAND)

        // Built-in interactive bridge command check via address bar
        if (trimmed.equals("/bridge", ignoreCase = true) ||
            trimmed.equals("/bridge status", ignoreCase = true) ||
            trimmed.equals("bridge status", ignoreCase = true) ||
            trimmed.equals("bridge", ignoreCase = true)
        ) {
            val bridgeEnabled = _settings.value.bidirectionalBridgeEnabled
            val applyAll = _settings.value.bridgeApplyToAllWebsites
            val state = webAppBridge.connectionState.value
            val currentUrl = currentTab.value?.url.orEmpty()
            val host = try { java.net.URI(currentUrl).host.orEmpty().ifEmpty { currentUrl } } catch (_: Exception) { currentUrl }
            val isSuccess = state == WebAppConnectionState.READY

            appendTerminalLine("── GVONE BRIDGE DIAGNOSTIC STATUS ──", TerminalLineType.SYSTEM)
            appendTerminalLine(
                "● Bridge Connection: " + (if (isSuccess) "CONNECTED & ACTIVE (Ready)" else "STATUS: ${state.name} (Not Ready)"),
                if (isSuccess) TerminalLineType.SUCCESS else TerminalLineType.WARNING
            )
            appendTerminalLine("● Bridge Setting: " + (if (bridgeEnabled) "ENABLED" else "DISABLED"), if (bridgeEnabled) TerminalLineType.SUCCESS else TerminalLineType.WARNING)
            appendTerminalLine("● Bridge Scope: " + (if (applyAll) "Apply to All Websites (Universal)" else "Trusted GVONE Web Apps Only"), TerminalLineType.INFO)
            appendTerminalLine("● Current Target: $host", TerminalLineType.OUTPUT)
            appendTerminalLine(
                "● Result: " + (if (isSuccess) "Bridge is SUCCESSFUL. Address bar can send direct inputs." else "Bridge is NOT CONNECTED. Check if target page is open or supports GVONE bridge."),
                if (isSuccess) TerminalLineType.SUCCESS else TerminalLineType.ERROR
            )
            val msg = if (isSuccess) "Bridge is connected & successful" else "Bridge status: ${state.name}"
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Check if user entered a terminal command (e.g. /yt, /wiki, /cmd, or custom command alias)
        if (CommandEngine.isCommandCandidate(trimmed, customCommands.value)) {
            val activeTab = currentTab.value
            val currentUrl = activeTab?.url.orEmpty()
            val currentTitle = activeTab?.title.orEmpty()
            val domain = try { java.net.URI(currentUrl).host.orEmpty() } catch (_: Exception) { "" }
            val clipboard = try {
                val clipService = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                clipService?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
            } catch (_: Exception) { "" }

            val executionContext = CommandExecutionContext(
                currentUrl = currentUrl,
                currentTitle = currentTitle,
                currentDomain = domain,
                clipboardText = clipboard
            )

            val executionResult = CommandEngine.parseResult(trimmed, customCommands.value, executionContext)
            if (executionResult != null) {
                when (executionResult) {
                    is CommandExecutionResult.OpenUrl -> {
                        appendTerminalLine("[SUCCESS] Command executed -> Loading URL: ${executionResult.url}", TerminalLineType.SUCCESS)
                        loadUrlInCurrentTab(executionResult.url)
                        return
                    }
                    is CommandExecutionResult.ExecuteSearch -> {
                        appendTerminalLine("[SUCCESS] Search command executed -> ${executionResult.searchUrl}", TerminalLineType.SUCCESS)
                        loadUrlInCurrentTab(executionResult.searchUrl)
                        return
                    }
                    is CommandExecutionResult.SendAIPrompt -> {
                        appendTerminalLine("[INFO] Forwarding prompt to GVONE AI: \"${executionResult.prompt}\"", TerminalLineType.INFO)
                        performAISearch(executionResult.prompt)
                        return
                    }
                    is CommandExecutionResult.TriggerBrowserAction -> {
                        appendTerminalLine("[SUCCESS] Browser Action triggered: ${executionResult.action.name}", TerminalLineType.SUCCESS)
                        closeSheet()
                        when (executionResult.action) {
                            BrowserActionType.NEW_TAB -> createNewTab()
                            BrowserActionType.NEW_PRIVATE_TAB -> createNewTab(isPrivate = true)
                            BrowserActionType.RELOAD -> activeTab?.url?.let { loadUrlInCurrentTab(it) }
                            BrowserActionType.BACK -> getActiveWebView()?.goBack()
                            BrowserActionType.FORWARD -> getActiveWebView()?.goForward()
                            BrowserActionType.HISTORY -> openSheet(ActiveSheet.History)
                            BrowserActionType.BOOKMARKS -> openSheet(ActiveSheet.Bookmarks)
                            BrowserActionType.DOWNLOADS -> openSheet(ActiveSheet.Downloads)
                            BrowserActionType.CLOSE_TAB -> closeCurrentTab()
                            BrowserActionType.TAB_OVERVIEW -> openSheet(ActiveSheet.TabOverview)
                            BrowserActionType.SETTINGS -> openSheet(ActiveSheet.Settings)
                            BrowserActionType.COMMAND_MANAGER -> openSheet(ActiveSheet.CustomCommands)
                            BrowserActionType.DESKTOP_MODE -> toggleDesktopMode()
                            BrowserActionType.TOR_DIAGNOSTICS -> openSheet(ActiveSheet.TorDiagnostics)
                            BrowserActionType.FIND_IN_PAGE -> openSheet(ActiveSheet.FindInPage)
                            BrowserActionType.READER_MODE -> openSheet(ActiveSheet.ReaderMode)
                            BrowserActionType.TERMINAL -> openSheet(ActiveSheet.Terminal)
                            BrowserActionType.CLEAR_DATA -> {
                                clearBrowsingData()
                                Toast.makeText(getApplication(), "Browsing data cleared", Toast.LENGTH_SHORT).show()
                            }
                        }
                        return
                    }
                    is CommandExecutionResult.TriggerPageAction -> {
                        appendTerminalLine("[SUCCESS] Page Action triggered: ${executionResult.action}", TerminalLineType.SUCCESS)
                        closeSheet()
                        when (executionResult.action) {
                            "summarize_page" -> {
                                val activeWv = getActiveWebView()
                                activeWv?.evaluateJavascript("document.body.innerText.substring(0, 4000)") { text ->
                                    val cleanText = text?.trim('\"', ' ', '\n')?.replace("\\n", "\n")?.take(3500).orEmpty()
                                    val prompt = "Summarize this webpage clearly with an executive summary and 3-5 key points.\n\nTitle: ${activeTab?.title}\nURL: ${activeTab?.url}\n\nContent:\n$cleanText"
                                    performAISearch(prompt)
                                }
                            }
                            "translate_page" -> {
                                val targetUrl = "https://translate.google.com/translate?sl=auto&tl=en&u=" + java.net.URLEncoder.encode(currentUrl, "UTF-8")
                                loadUrlInCurrentTab(targetUrl)
                            }
                            "extract_info" -> {
                                val activeWv = getActiveWebView()
                                activeWv?.evaluateJavascript("document.body.innerText.substring(0, 4000)") { text ->
                                    val cleanText = text?.trim('\"', ' ', '\n')?.replace("\\n", "\n")?.take(3500).orEmpty()
                                    val prompt = "Extract all key structured data, dates, stats, contacts, and references from this webpage.\n\nTitle: ${activeTab?.title}\nURL: ${activeTab?.url}\n\nContent:\n$cleanText"
                                    performAISearch(prompt)
                                }
                            }
                            "reader_mode" -> {
                                openSheet(ActiveSheet.ReaderMode)
                            }
                            else -> {
                                // Fallback generic page action
                            }
                        }
                        return
                    }
                    is CommandExecutionResult.RunSafeJavaScript -> {
                        appendTerminalLine("[SUCCESS] JavaScript evaluated: ${executionResult.description}", TerminalLineType.SUCCESS)
                        closeSheet()
                        getActiveWebView()?.evaluateJavascript(executionResult.javascriptCode, null)
                        Toast.makeText(getApplication(), "Executed: ${executionResult.description}", Toast.LENGTH_SHORT).show()
                        return
                    }
                    is CommandExecutionResult.ShowMessage -> {
                        appendTerminalLine("[OUTPUT] ${executionResult.message}", TerminalLineType.OUTPUT)
                        Toast.makeText(getApplication(), executionResult.message, Toast.LENGTH_SHORT).show()
                        return
                    }
                }
            }
        }

        val activeTab = currentTab.value
        val routing = InputRouter.resolveRouting(
            input = trimmed,
            currentTabUrl = activeTab?.url,
            isWebAppReady = webAppBridge.connectionState.value == WebAppConnectionState.READY ||
                    webAppBridge.connectionState.value == WebAppConnectionState.PROCESSING,
            inputRouterEnabled = _settings.value.bidirectionalBridgeEnabled,
            bridgeApplyToAllWebsites = _settings.value.bridgeApplyToAllWebsites
        )

        when (routing) {
            InputDestination.DELIVER_TO_WEB_APP -> {
                appendTerminalLine("[BRIDGE] Routing address bar input to active Web App...", TerminalLineType.INFO)
                val activeWebView = getActiveWebView(activeTab?.id)
                val delivered = webAppBridge.deliverAddressBarInput(activeWebView, trimmed, action = "submit")
                if (!delivered) {
                    appendTerminalLine("[BRIDGE] Connection FAILED: Input delivery unconfirmed. Falling back to standard navigation.", TerminalLineType.ERROR)
                    // Safe fallback if active WebView was missing, detached, or on YouTube
                    if (com.example.data.sync.PageContextDetector.isYouTubeOrigin(activeTab?.url)) {
                        val encoded = java.net.URLEncoder.encode(trimmed, "UTF-8")
                        val ytUrl = if (activeTab?.url?.contains("m.youtube.com") == true) {
                            "https://m.youtube.com/results?search_query=$encoded"
                        } else {
                            "https://www.youtube.com/results?search_query=$encoded"
                        }
                        loadUrlInCurrentTab(ytUrl)
                    } else {
                        val destinationUrl = resolveUrlOrSearch(trimmed)
                        loadUrlInCurrentTab(destinationUrl)
                    }
                } else {
                    appendTerminalLine("[BRIDGE] Connection SUCCESSFUL: Input dispatched to Web App!", TerminalLineType.SUCCESS)
                    // Successfully delivered input directly to the Web App without page reload/navigation!
                    closeSheet()
                }
            }

            InputDestination.NAVIGATE_URL -> {
                val destinationUrl = InputRouter.formatNavigationUrl(trimmed)
                appendTerminalLine("[NAV] Loading URL: $destinationUrl", TerminalLineType.INFO)
                loadUrlInCurrentTab(destinationUrl)
            }

            InputDestination.UNIVERSAL_SEARCH, InputDestination.AI_SEARCH -> {
                val isQuestion = trimmed.endsWith("?") || 
                    trimmed.startsWith("what", ignoreCase = true) ||
                    trimmed.startsWith("who", ignoreCase = true) ||
                    trimmed.startsWith("how", ignoreCase = true) ||
                    trimmed.startsWith("why", ignoreCase = true) ||
                    trimmed.startsWith("explain", ignoreCase = true)

                if (_settings.value.searchEngine == SearchEngineType.GVONE && isQuestion && _settings.value.aiSearchAutoTrigger) {
                    appendTerminalLine("[AI SEARCH] Synthesizing answer for: \"$trimmed\"", TerminalLineType.INFO)
                    performAISearch(trimmed)
                } else {
                    val destinationUrl = resolveUrlOrSearch(trimmed)
                    appendTerminalLine("[SEARCH] Navigating to search: $destinationUrl", TerminalLineType.INFO)
                    loadUrlInCurrentTab(destinationUrl)
                }
            }
        }
    }

    fun performAISearch(query: String) {
        _isAiLoading.value = true
        _activeSheet.value = ActiveSheet.AISearchResult
        viewModelScope.launch {
            val result = aiService.searchAndSynthesize(query)
            _aiSearchResult.value = result
            _isAiLoading.value = false
        }
    }

    fun loadUrlInCurrentTab(url: String) {
        val currentId = _currentTabId.value
        _tabs.value = _tabs.value.map {
            if (it.id == currentId) it.copy(url = url, title = "Loading...") else it
        }
        _addressBarInput.value = url
        closeSheet()
    }

    // Custom Commands Management
    fun saveCustomCommand(command: CustomCommandEntity) {
        viewModelScope.launch {
            repository.saveCustomCommand(command)
        }
    }

    fun deleteCustomCommand(id: String) {
        viewModelScope.launch {
            repository.deleteCustomCommand(id)
        }
    }

    fun toggleCommandEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setCommandEnabled(id, enabled)
        }
    }

    fun toggleCommandPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            repository.setCommandPinned(id, pinned)
        }
    }

    fun installCommandPack(pack: CommandEngine.CommandPack) {
        viewModelScope.launch {
            repository.saveCustomCommands(pack.commands)
        }
    }

    fun importCommandsFromJson(json: String) {
        val parsed = CommandEngine.importCommandsFromJson(json)
        if (parsed.isNotEmpty()) {
            viewModelScope.launch {
                repository.saveCustomCommands(parsed)
            }
        }
    }

    fun generateCommandWithAI(prompt: String, callback: (CustomCommandEntity) -> Unit) {
        viewModelScope.launch {
            val systemPrompt = "You are an expert command syntax engineer for the GVONE universal terminal bar. Return ONLY valid JSON: {\"command\": \"/trigger\", \"name\": \"Title\", \"description\": \"Summary\", \"type\": \"SEARCH|URL|AI|BROWSER_ACTION|PAGE_ACTION|AUTOMATION\", \"template\": \"https://...{query}\", \"aliases\": \"/alias1, /alias2\"}"
            val response = try {
                aiService.askAI(prompt = "$systemPrompt\n\nUser request: $prompt")
            } catch (_: Exception) {
                null
            }

            val synthesized = if (!response.isNullOrBlank() && response.contains("{")) {
                try {
                    val jsonStart = response.indexOf("{")
                    val jsonEnd = response.lastIndexOf("}") + 1
                    val jsonStr = response.substring(jsonStart, jsonEnd)
                    val obj = org.json.JSONObject(jsonStr)
                    val cmdTrigger = obj.optString("command", "/cmd").let { if (it.startsWith("/")) it else "/$it" }
                    val cmdName = obj.optString("name", "Custom Command")
                    val desc = obj.optString("description", "")
                    val typeStr = obj.optString("type", "SEARCH")
                    val type = try { CommandType.valueOf(typeStr) } catch (_: Exception) { CommandType.SEARCH }
                    val tmpl = obj.optString("template", "https://www.google.com/search?q={query}")
                    val aliases = obj.optString("aliases", "")
                    CustomCommandEntity(
                        id = "ai_${UUID.randomUUID().toString().take(8)}",
                        command = cmdTrigger,
                        name = cmdName,
                        description = desc,
                        type = type,
                        template = tmpl,
                        aliasesRaw = aliases,
                        category = CommandCategory.CUSTOM,
                        isEnabled = true,
                        isPinned = false,
                        isBuiltIn = false
                    )
                } catch (_: Exception) {
                    fallbackSynthesizedCommand(prompt)
                }
            } else {
                fallbackSynthesizedCommand(prompt)
            }
            callback(synthesized)
        }
    }

    private fun fallbackSynthesizedCommand(prompt: String): CustomCommandEntity {
        val lower = prompt.lowercase()
        val trigger = when {
            lower.contains("wiki") -> "/wiki"
            lower.contains("reddit") -> "/reddit"
            lower.contains("github") || lower.contains("git") -> "/gh"
            lower.contains("twitter") || lower.contains(" x ") -> "/x"
            lower.contains("scholar") || lower.contains("paper") -> "/scholar"
            lower.contains("map") -> "/maps"
            lower.contains("translate") -> "/tr"
            else -> {
                val words = prompt.split(" ").filter { it.length in 3..8 }
                "/" + (words.firstOrNull()?.replace(Regex("[^a-zA-Z0-9]"), "") ?: "cmd")
            }
        }

        val (type, tmpl, name) = when {
            lower.contains("wiki") -> Triple(CommandType.SEARCH, "https://en.wikipedia.org/wiki/Special:Search?search={query}", "Wikipedia Search")
            lower.contains("reddit") -> Triple(CommandType.SEARCH, "https://www.reddit.com/search/?q={query}", "Reddit Search")
            lower.contains("github") || lower.contains("git") -> Triple(CommandType.SEARCH, "https://github.com/search?q={query}", "GitHub Code Search")
            lower.contains("scholar") || lower.contains("paper") -> Triple(CommandType.SEARCH, "https://scholar.google.com/scholar?q={query}", "Google Scholar")
            lower.contains("map") -> Triple(CommandType.SEARCH, "https://www.google.com/maps/search/{query}", "Google Maps")
            lower.contains("translate") -> Triple(CommandType.SEARCH, "https://translate.google.com/?sl=auto&tl=en&text={query}&op=translate", "Google Translate")
            lower.contains("summarize") -> Triple(CommandType.PAGE_ACTION, "summarize_page", "Summarize Webpage")
            else -> Triple(CommandType.SEARCH, "https://www.google.com/search?q={query}", "Quick Search")
        }

        return CustomCommandEntity(
            id = "ai_${UUID.randomUUID().toString().take(8)}",
            command = trigger,
            name = name,
            description = "Synthesized via GVONE AI: $prompt",
            type = type,
            template = tmpl,
            aliasesRaw = "$trigger${trigger.removePrefix("/")}",
            category = CommandCategory.CUSTOM,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = false
        )
    }

    fun resolveUrlOrSearch(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("gvone://")) {
            return trimmed
        }
        if (trimmed.contains(".") && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }
        // Use configured search engine
        val engine = _settings.value.searchEngine
        return if (engine == SearchEngineType.CUSTOM && _settings.value.customSearchUrl.isNotBlank()) {
            _settings.value.customSearchUrl + java.net.URLEncoder.encode(trimmed, "UTF-8")
        } else {
            engine.searchUrl + java.net.URLEncoder.encode(trimmed, "UTF-8")
        }
    }

    fun toggleTor() {
        val target = !_settings.value.torEnabled
        val updated = _settings.value.copy(torEnabled = target)
        _settings.value = updated
        persistSettings(updated)

        viewModelScope.launch {
            if (target) {
                torManager.connect(host = updated.torProxyHost, port = updated.torProxyPort)
            } else {
                torManager.disconnect()
            }
        }
    }

    fun disableTorAndReload() {
        val updated = _settings.value.copy(torEnabled = false)
        _settings.value = updated
        persistSettings(updated)
        torManager.disconnect()
        currentTab.value?.url?.let { loadUrlInCurrentTab(it) }
    }

    fun testTorConnection() {
        viewModelScope.launch {
            torManager.testTorConnection()
        }
    }

    fun retryTorConnection() {
        viewModelScope.launch {
            torManager.reconnect(host = _settings.value.torProxyHost, port = _settings.value.torProxyPort)
        }
    }

    fun runTorDiagnostics() {
        viewModelScope.launch {
            torManager.runDetailedDiagnostics(
                host = _settings.value.torProxyHost,
                port = _settings.value.torProxyPort
            )
        }
    }

    fun forceReapplyWebViewProxy() {
        torManager.configureWebViewProxy(_settings.value.torProxyHost, _settings.value.torProxyPort)
    }

    fun applyDiagnosticFix(fix: String) {
        when {
            fix.startsWith("SWITCH_PORT_") -> {
                val port = fix.removePrefix("SWITCH_PORT_").toIntOrNull() ?: 9050
                val updated = _settings.value.copy(torProxyPort = port, torEnabled = true)
                updateSettings(updated)
                runTorDiagnostics()
            }
            fix == "REAPPLY_PROXY" -> {
                forceReapplyWebViewProxy()
                runTorDiagnostics()
            }
            fix == "NEW_CIRCUIT" -> {
                viewModelScope.launch {
                    torManager.newIdentity()
                    torManager.testTorConnection()
                    runTorDiagnostics()
                }
            }
            fix == "DISABLE_TOR" -> {
                disableTorAndReload()
            }
        }
    }

    fun updateSettings(newSettings: BrowserSettings) {
        val oldTor = _settings.value.torEnabled
        val oldPort = _settings.value.torProxyPort
        val oldHost = _settings.value.torProxyHost
        _settings.value = newSettings
        persistSettings(newSettings)

        if (newSettings.torEnabled != oldTor || (newSettings.torEnabled && (newSettings.torProxyPort != oldPort || newSettings.torProxyHost != oldHost))) {
            viewModelScope.launch {
                if (newSettings.torEnabled) {
                    torManager.connect(host = newSettings.torProxyHost, port = newSettings.torProxyPort)
                } else {
                    torManager.disconnect()
                }
            }
        }
    }

    fun clearBrowsingData(cookies: Boolean = true, history: Boolean = true, cache: Boolean = true) {
        viewModelScope.launch {
            if (history) repository.clearAllHistory()
            if (cookies) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            }
        }
    }

    fun setFindQuery(query: String) {
        _findQuery.value = query
    }

    fun updateFindResults(activeMatchIndex: Int, numberOfMatches: Int) {
        _findCurrentIndex.value = activeMatchIndex
        _findMatchCount.value = numberOfMatches
    }

    // YouTube Shorts Audio Integration
    val shortsAudioStatus: StateFlow<ShortsAudioStatus?> = webAppBridge.shortsAudioState

    val isCurrentTabShorts: StateFlow<Boolean> = combine(
        currentTab,
        shortsAudioStatus
    ) { tab, status ->
        (status?.isShorts == true) || PageContextDetector.isYouTubeShorts(tab?.url)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isShortsMuted: StateFlow<Boolean> = combine(
        settings,
        shortsAudioStatus
    ) { s, status ->
        status?.isMuted ?: (s.shortsAudioMode == ShortsAudioMode.ALWAYS_MUTED)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setShortsAudioMode(mode: ShortsAudioMode) {
        val updated = _settings.value.copy(shortsAudioMode = mode)
        updateSettings(updated)
        getActiveWebView()?.evaluateJavascript(
            "if (window.__GVONE_SET_SHORTS_AUDIO_MODE__) window.__GVONE_SET_SHORTS_AUDIO_MODE__('${mode.name}');",
            null
        )
    }

    fun toggleShortsAudio() {
        getActiveWebView()?.evaluateJavascript(
            "if (window.__GVONE_TOGGLE_SHORTS_AUDIO__) window.__GVONE_TOGGLE_SHORTS_AUDIO__();",
            null
        )
    }

    val mediaPlayerStatus: StateFlow<com.example.data.sync.MediaPlayerStatus?> = webAppBridge.mediaPlayerState

    private val _isMediaPlaying = MutableStateFlow(true)
    val isMediaPlaying: StateFlow<Boolean> = _isMediaPlaying.asStateFlow()

    init {
        GVONEMediaPlaybackService.mediaActionListener = { action ->
            when (action) {
                MediaControlAction.TOGGLE_PLAY -> toggleMediaPlay()
                MediaControlAction.PREVIOUS -> mediaPrevious()
                MediaControlAction.NEXT -> mediaNext()
                MediaControlAction.STOP -> {
                    if (_isMediaPlaying.value) {
                        toggleMediaPlay()
                    }
                }
            }
        }

        viewModelScope.launch {
            mediaPlayerStatus.collect { status ->
                if (status != null) {
                    _isMediaPlaying.value = status.isPlaying
                    if (_settings.value.backgroundPlayEnabled) {
                        GVONEMediaPlaybackService.startOrUpdate(
                            getApplication(),
                            status.title.ifBlank { "Web Media Playback" },
                            status.isPlaying
                        )
                    }
                }
            }
        }
    }

    fun toggleBackgroundPlay() {
        val current = _settings.value.backgroundPlayEnabled
        val next = !current
        val updated = _settings.value.copy(backgroundPlayEnabled = next)
        updateSettings(updated)
        activeWebViews.values.forEach { ref ->
            ref.get()?.let { wv ->
                (wv as? com.example.ui.components.GVONEActionWebView)?.isBackgroundPlayEnabled = next
                webAppBridge.injectBackgroundPlayerScript(wv, next)
            }
        }
        if (!next) {
            GVONEMediaPlaybackService.stop(getApplication())
        } else {
            mediaPlayerStatus.value?.let { status ->
                if (status.isPlaying) {
                    GVONEMediaPlaybackService.startOrUpdate(
                        getApplication(),
                        status.title.ifBlank { "Web Media Playback" },
                        true
                    )
                }
            }
        }
    }

    fun toggleMediaPlay() {
        val current = _isMediaPlaying.value
        _isMediaPlaying.value = !current
        getActiveWebView()?.evaluateJavascript(
            "if (window.__GVONE_MEDIA_TOGGLE_PLAY__) window.__GVONE_MEDIA_TOGGLE_PLAY__();",
            null
        )
    }

    fun mediaPrevious() {
        getActiveWebView()?.evaluateJavascript(
            "if (window.__GVONE_MEDIA_PREV__) window.__GVONE_MEDIA_PREV__();",
            null
        )
    }

    fun mediaNext() {
        getActiveWebView()?.evaluateJavascript(
            "if (window.__GVONE_MEDIA_NEXT__) window.__GVONE_MEDIA_NEXT__();",
            null
        )
    }

    // --- Context Menu Operations ---

    fun triggerContextMenu(data: LinkContextMenuData) {
        viewModelScope.launch {
            val isBookmarked = repository.isBookmarked(data.url)
            val isInReadingList = repository.isInReadingList(data.url)
            val activeGroupId = currentTab.value?.tabGroupId ?: _activeGroupId.value
            _contextMenuData.value = data.copy(
                isBookmarked = isBookmarked,
                isInReadingList = isInReadingList,
                activeTabGroupId = activeGroupId
            )
        }
    }

    fun dismissContextMenu() {
        _contextMenuData.value = null
    }

    fun openInNewTabFromContextMenu(url: String, inBackground: Boolean = true, context: Context? = null) {
        createNewTab(url = url, inBackground = inBackground)
        if (inBackground && context != null) {
            Toast.makeText(context, "Tab opened in background", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInNewTabInGroupFromContextMenu(url: String, context: Context? = null) {
        val currentGroupId = currentTab.value?.tabGroupId ?: _activeGroupId.value
        if (currentGroupId != null) {
            createNewTab(url = url, groupId = currentGroupId, inBackground = true)
            if (context != null) {
                val groupName = _tabGroups.value.firstOrNull { it.id == currentGroupId }?.name ?: "Group"
                Toast.makeText(context, "Added tab to group '$groupName'", Toast.LENGTH_SHORT).show()
            }
        } else {
            if (_tabGroups.value.isNotEmpty()) {
                _groupPickerUrl.value = url
            } else {
                val domain = try {
                    java.net.URI(url).host?.removePrefix("www.") ?: "Group 1"
                } catch (_: Exception) {
                    "Group 1"
                }
                val newGroupId = createTabGroup(domain, "#3B82F6")
                val currentId = _currentTabId.value
                _tabs.value = _tabs.value.map {
                    if (it.id == currentId) it.copy(tabGroupId = newGroupId) else it
                }
                createNewTab(url = url, groupId = newGroupId, inBackground = true)
                if (context != null) {
                    Toast.makeText(context, "Created '$domain' group and added tab", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun selectGroupAndAddTab(groupId: String, url: String, context: Context? = null) {
        _groupPickerUrl.value = null
        createNewTab(url = url, groupId = groupId, inBackground = true)
        if (context != null) {
            val groupName = _tabGroups.value.firstOrNull { it.id == groupId }?.name ?: "Group"
            Toast.makeText(context, "Added tab to group '$groupName'", Toast.LENGTH_SHORT).show()
        }
    }

    fun createGroupAndAddTab(groupName: String, url: String, context: Context? = null) {
        _groupPickerUrl.value = null
        val newGroupId = createTabGroup(groupName, "#10B981")
        createNewTab(url = url, groupId = newGroupId, inBackground = true)
        if (context != null) {
            Toast.makeText(context, "Created '$groupName' group and added tab", Toast.LENGTH_SHORT).show()
        }
    }

    fun dismissGroupPicker() {
        _groupPickerUrl.value = null
    }

    fun openInIncognitoFromContextMenu(url: String, context: Context? = null) {
        createNewTab(url = url, isPrivate = true, groupId = null, inBackground = false)
        if (context != null) {
            Toast.makeText(context, "Opened in Incognito tab", Toast.LENGTH_SHORT).show()
        }
    }

    fun openInNewWindowFromContextMenu(url: String, context: Context) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            createNewTab(url = url, inBackground = false)
            Toast.makeText(context, "Multi-window unsupported; opened in new tab", Toast.LENGTH_SHORT).show()
        }
    }

    fun showPagePreview(url: String, title: String) {
        _pagePreviewData.value = PagePreviewData(url = url, title = title)
    }

    fun dismissPagePreview() {
        _pagePreviewData.value = null
    }

    fun copyLinkAddress(url: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Link Address", url))
        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun copyLinkText(text: String, context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Link Text", text))
        Toast.makeText(context, "Link text copied", Toast.LENGTH_SHORT).show()
    }

    fun downloadResource(url: String, mimeType: String?, context: Context) {
        viewModelScope.launch {
            try {
                downloadManager.startDownload(url = url, userAgent = null, contentDisposition = null, mimeType = mimeType)
                val fileName = url.substringAfterLast("/").substringBefore("?").ifBlank { "resource" }
                Toast.makeText(context, "Download started: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleBookmarkFromContextMenu(url: String, title: String, faviconUrl: String?, context: Context) {
        viewModelScope.launch {
            val already = repository.isBookmarked(url)
            if (already) {
                repository.removeBookmarkByUrlAndType(url, isReadingList = false)
                Toast.makeText(context, "Removed from bookmarks", Toast.LENGTH_SHORT).show()
            } else {
                repository.addBookmark(
                    BookmarkEntry(
                        title = title.ifBlank { url },
                        url = url,
                        folder = "Favorites",
                        faviconUrl = faviconUrl,
                        isReadingList = false
                    )
                )
                Toast.makeText(context, "Added to bookmarks", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun toggleReadingListFromContextMenu(url: String, title: String, faviconUrl: String?, context: Context) {
        viewModelScope.launch {
            val already = repository.isInReadingList(url)
            if (already) {
                repository.removeBookmarkByUrlAndType(url, isReadingList = true)
                Toast.makeText(context, "Removed from reading list", Toast.LENGTH_SHORT).show()
            } else {
                repository.addBookmark(
                    BookmarkEntry(
                        title = title.ifBlank { url },
                        url = url,
                        folder = "Reading List",
                        faviconUrl = faviconUrl,
                        isReadingList = true
                    )
                )
                Toast.makeText(context, "Added to reading list", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareLink(url: String, title: String?, context: Context) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_TEXT, url)
                if (!title.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TITLE, title)
                }
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, "Share link via")
            shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share link", Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun getSitePermission(domain: String): SitePermission? {
        return repository.getSitePermission(domain)
    }

    fun saveSitePermission(permission: SitePermission) {
        viewModelScope.launch {
            repository.saveSitePermission(permission)
        }
    }

    fun clearSiteDataForDomain(domain: String, url: String, context: Context) {
        viewModelScope.launch {
            try {
                val cookieManager = CookieManager.getInstance()
                val cookies = cookieManager.getCookie(url)
                if (!cookies.isNullOrBlank()) {
                    val parts = cookies.split(";")
                    for (part in parts) {
                        val cookieName = part.substringBefore("=").trim()
                        if (cookieName.isNotEmpty()) {
                            cookieManager.setCookie(url, "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                            cookieManager.setCookie(domain, "$cookieName=; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                        }
                    }
                    cookieManager.flush()
                }
                try {
                    android.webkit.WebStorage.getInstance().deleteOrigin(url)
                } catch (_: Exception) {}
                Toast.makeText(context, "Cookies and site data cleared for $domain", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error clearing site data", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deleteHistoryForDomain(domain: String, context: Context) {
        viewModelScope.launch {
            repository.deleteHistoryByDomain(domain)
            Toast.makeText(context, "History cleared for $domain", Toast.LENGTH_SHORT).show()
        }
    }
}
