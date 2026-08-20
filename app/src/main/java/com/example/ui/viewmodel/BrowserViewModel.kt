package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GVONEAIService
import com.example.data.download.BrowserDownloadManager
import com.example.data.model.*
import com.example.data.repository.BrowserRepository
import com.example.data.tor.TorConnectionState
import com.example.data.tor.TorManager
import com.example.data.tor.TorTestResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
}

class BrowserViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("gvone_settings_prefs", Context.MODE_PRIVATE)
    val repository = BrowserRepository(application)
    val torManager = TorManager()
    val downloadManager = BrowserDownloadManager(application, repository)
    val aiService = GVONEAIService(torManager)

    // Tab State
    private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _currentTabId = MutableStateFlow<String>("")
    val currentTabId: StateFlow<String> = _currentTabId.asStateFlow()

    private val _isPrivateMode = MutableStateFlow(false)
    val isPrivateMode: StateFlow<Boolean> = _isPrivateMode.asStateFlow()

    private val _tabSortOption = MutableStateFlow(TabSortOption.DEFAULT)
    val tabSortOption: StateFlow<TabSortOption> = _tabSortOption.asStateFlow()

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

    val currentTab: StateFlow<BrowserTab?> = combine(_tabs, _currentTabId) { tabsList, currentId ->
        tabsList.find { it.id == currentId } ?: tabsList.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Initialize default home tab running the integrated web app (URL hidden from UI)
        val initialTabs = listOf(
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Home",
                url = HOME_WEB_APP_URL,
                faviconUrl = null,
                isPrivate = false
            ),
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Physicists Reveal a Quantum Geometry",
                url = "https://www.quantamagazine.org",
                faviconUrl = "https://www.quantamagazine.org/favicon.ico",
                isPrivate = false
            ),
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "DuckDuckGo — Privacy, Simplified.",
                url = "https://duckduckgo.com",
                faviconUrl = "https://duckduckgo.com/favicon.ico",
                isPrivate = false
            ),
            BrowserTab(
                id = UUID.randomUUID().toString(),
                title = "Apple",
                url = "https://www.apple.com",
                faviconUrl = "https://www.apple.com/favicon.ico",
                isPrivate = false
            )
        )
        _tabs.value = initialTabs
        _currentTabId.value = initialTabs[0].id
        _addressBarInput.value = ""

        // If Tor was persistent and enabled across restarts, connect immediately
        if (_settings.value.torEnabled) {
            viewModelScope.launch {
                torManager.connect(host = _settings.value.torProxyHost, port = _settings.value.torProxyPort)
            }
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

        return BrowserSettings(
            searchEngine = engine,
            trackingProtection = tracking,
            blockPopups = popups,
            forceHttps = https,
            torEnabled = torEnabled,
            torProxyHost = torHost,
            torProxyPort = torPort,
            addressBarBottom = addressBottom,
            aiSearchAutoTrigger = aiAuto
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
            .apply()
    }

    fun openSheet(sheet: ActiveSheet) {
        _activeSheet.value = sheet
    }

    fun closeSheet() {
        _activeSheet.value = ActiveSheet.None
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

    fun selectTab(tabId: String) {
        val tab = _tabs.value.find { it.id == tabId }
        if (tab != null) {
            _currentTabId.value = tab.id
            _isPrivateMode.value = tab.isPrivate
            _addressBarInput.value = tab.url
            closeSheet()
        }
    }

    fun createNewTab(url: String = HOME_WEB_APP_URL, isPrivate: Boolean = _isPrivateMode.value) {
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            title = if (isInternalHomeUrl(url)) "Home" else "New Tab",
            url = url,
            isPrivate = isPrivate
        )
        _tabs.value = _tabs.value + newTab
        _currentTabId.value = newTab.id
        _addressBarInput.value = if (isInternalHomeUrl(url)) "" else url
        closeSheet()
    }

    fun closeTab(tabId: String) {
        val currentList = _tabs.value
        val index = currentList.indexOfFirst { it.id == tabId }
        if (index == -1) return

        val newList = currentList.filter { it.id != tabId }
        _tabs.value = newList

        if (newList.isEmpty()) {
            createNewTab(isPrivate = _isPrivateMode.value)
        } else if (_currentTabId.value == tabId) {
            val nextIndex = (index - 1).coerceAtLeast(0)
            val nextTab = newList.getOrNull(nextIndex) ?: newList.first()
            _currentTabId.value = nextTab.id
            _isPrivateMode.value = nextTab.isPrivate
        }
    }

    fun closeAllTabs(isPrivateOnly: Boolean = false) {
        if (isPrivateOnly) {
            val remaining = _tabs.value.filter { !it.isPrivate }
            _tabs.value = remaining
            if (remaining.isEmpty()) {
                createNewTab(isPrivate = false)
            } else {
                _currentTabId.value = remaining.first().id
                _isPrivateMode.value = false
            }
        } else {
            _tabs.value = emptyList()
            createNewTab(isPrivate = false)
        }
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

    fun updateCurrentTabState(
        title: String? = null,
        url: String? = null,
        faviconUrl: String? = null,
        isLoading: Boolean? = null,
        progress: Int? = null
    ) {
        val currentId = _currentTabId.value
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == currentId) {
                var updated = tab
                title?.let { updated = updated.copy(title = it) }
                url?.let {
                    updated = updated.copy(url = it)
                    if (!isInternalHomeUrl(it)) {
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

        val isQuestion = trimmed.endsWith("?") || 
            trimmed.startsWith("what", ignoreCase = true) ||
            trimmed.startsWith("who", ignoreCase = true) ||
            trimmed.startsWith("how", ignoreCase = true) ||
            trimmed.startsWith("why", ignoreCase = true) ||
            trimmed.startsWith("explain", ignoreCase = true)

        if (_settings.value.searchEngine == SearchEngineType.GVONE && isQuestion && _settings.value.aiSearchAutoTrigger) {
            performAISearch(trimmed)
            return
        }

        val destinationUrl = resolveUrlOrSearch(trimmed)
        loadUrlInCurrentTab(destinationUrl)
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
}
