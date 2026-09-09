package com.example.agent.browser

import android.webkit.CookieManager
import android.webkit.WebView
import com.example.data.model.BrowserTab
import com.example.data.model.START_PAGE_URL
import com.example.data.repository.BrowserRepository
import com.example.ui.viewmodel.BrowserViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Concrete implementation of BrowserController connected to the live GVONE Browser
 * runtime, ViewModels, and Chromium WebViews.
 */
class BrowserControllerImpl(
    private val viewModel: BrowserViewModel,
    private val repository: BrowserRepository
) : BrowserController {

    private val _browserEvents = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 128)
    override val browserEvents: Flow<BrowserEvent> = _browserEvents.asSharedFlow()

    private val _browserState = MutableStateFlow(
        BrowserStateSnapshot(
            currentTabId = null,
            currentUrl = null,
            currentTitle = null,
            totalTabsCount = 0,
            isPrivateMode = false,
            isTorActive = false,
            isLoading = false
        )
    )
    override val browserState: StateFlow<BrowserStateSnapshot> = _browserState.asStateFlow()

    fun startObserving() {
        // Sync browser state continuously from ViewModel once fully constructed
        viewModel.tabs.onEach { tabs ->
            val curr = viewModel.currentTab.value
            val isTor = viewModel.settings.value.torEnabled
            _browserState.value = BrowserStateSnapshot(
                currentTabId = curr?.id,
                currentUrl = curr?.url,
                currentTitle = curr?.title,
                totalTabsCount = tabs.size,
                isPrivateMode = viewModel.isPrivateMode.value,
                isTorActive = isTor,
                isLoading = curr?.isLoading ?: false
            )
        }.launchIn(viewModel.viewModelScope)
    }

    suspend fun emitEvent(event: BrowserEvent) {
        _browserEvents.emit(event)
    }

    override fun getSupportedCapabilities(): List<CapabilityDescriptor> = listOf(
        CapabilityDescriptor(
            name = "Navigation",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Full URL loading, history back/forward traversal, reload, and stopLoading supported."
        ),
        CapabilityDescriptor(
            name = "Tab Management",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Create, close, switch, list, and group tabs with private mode separation."
        ),
        CapabilityDescriptor(
            name = "Page Content Extraction",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Asynchronous DOM text extraction, selection extraction, and JavaScript evaluation."
        ),
        CapabilityDescriptor(
            name = "Find In Page",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Integrated with WebView findAllAsync and match counter."
        ),
        CapabilityDescriptor(
            name = "History & Bookmarks Data",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Backed by persistent SQLite/Room database."
        ),
        CapabilityDescriptor(
            name = "Downloads",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Monitored and managed through Android DownloadManager."
        ),
        CapabilityDescriptor(
            name = "Cookie Management",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Direct integration with Android Chromium CookieManager."
        ),
        CapabilityDescriptor(
            name = "Site Permissions",
            status = BrowserCapabilityStatus.AVAILABLE,
            details = "Per-origin site permissions (camera, microphone, location, notifications) stored in Room."
        ),
        CapabilityDescriptor(
            name = "Raw TCP Socket Injection",
            status = BrowserCapabilityStatus.UNAVAILABLE,
            details = "Raw unauthenticated socket hijacking is disabled for Android sandbox security."
        )
    )

    override suspend fun openUrl(url: String, inNewTab: Boolean): Boolean = withContext(Dispatchers.Main) {
        if (inNewTab) {
            val tabId = viewModel.createNewTab(url = url, isPrivate = viewModel.isPrivateMode.value)
            _browserEvents.emit(BrowserEvent.TabCreated(tabId, viewModel.isPrivateMode.value))
        } else {
            viewModel.loadUrlInCurrentTab(url)
        }
        val activeId = viewModel.currentTabId.value
        _browserEvents.emit(BrowserEvent.UrlChanged(activeId, url))
        true
    }

    override suspend fun goBack(): Boolean = withContext(Dispatchers.Main) {
        val wv = viewModel.getActiveWebView()
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
            true
        } else false
    }

    override suspend fun goForward(): Boolean = withContext(Dispatchers.Main) {
        val wv = viewModel.getActiveWebView()
        if (wv != null && wv.canGoForward()) {
            wv.goForward()
            true
        } else false
    }

    override suspend fun reload(): Boolean = withContext(Dispatchers.Main) {
        val wv = viewModel.getActiveWebView()
        if (wv != null) {
            wv.reload()
            true
        } else {
            viewModel.currentTab.value?.url?.let { viewModel.loadUrlInCurrentTab(it) }
            true
        }
    }

    override suspend fun stopLoading(): Boolean = withContext(Dispatchers.Main) {
        val wv = viewModel.getActiveWebView()
        wv?.stopLoading()
        true
    }

    override suspend fun createTab(url: String?, isPrivate: Boolean, groupId: String?): String = withContext(Dispatchers.Main) {
        val tabId = viewModel.createNewTab(
            url = url ?: START_PAGE_URL,
            isPrivate = isPrivate,
            groupId = groupId
        )
        _browserEvents.emit(BrowserEvent.TabCreated(tabId, isPrivate))
        tabId
    }

    override suspend fun closeTab(tabId: String): Boolean = withContext(Dispatchers.Main) {
        viewModel.closeTab(tabId)
        _browserEvents.emit(BrowserEvent.TabClosed(tabId))
        true
    }

    override suspend fun switchTab(tabId: String): Boolean = withContext(Dispatchers.Main) {
        val prev = viewModel.currentTabId.value
        viewModel.selectTab(tabId)
        _browserEvents.emit(BrowserEvent.TabChanged(prev, tabId))
        true
    }

    override suspend fun getTabs(): List<BrowserTabInfo> = withContext(Dispatchers.Default) {
        viewModel.tabs.value.map { tab ->
            BrowserTabInfo(
                id = tab.id,
                title = tab.title,
                url = tab.url,
                isPrivate = tab.isPrivate,
                isPinned = tab.isPinned,
                isMuted = tab.isMuted,
                desktopMode = tab.desktopMode,
                isLoading = tab.isLoading,
                tabGroupId = tab.tabGroupId
            )
        }
    }

    override suspend fun getCurrentTab(): BrowserTabInfo? = withContext(Dispatchers.Default) {
        val tab = viewModel.currentTab.value ?: return@withContext null
        BrowserTabInfo(
            id = tab.id,
            title = tab.title,
            url = tab.url,
            isPrivate = tab.isPrivate,
            isPinned = tab.isPinned,
            isMuted = tab.isMuted,
            desktopMode = tab.desktopMode,
            isLoading = tab.isLoading,
            tabGroupId = tab.tabGroupId
        )
    }

    override suspend fun getCurrentUrl(): String? = withContext(Dispatchers.Default) {
        viewModel.currentTab.value?.url
    }

    override suspend fun getTitle(): String? = withContext(Dispatchers.Default) {
        viewModel.currentTab.value?.title
    }

    override suspend fun getPageText(): String? = withContext(Dispatchers.Main) {
        val wv = viewModel.getActiveWebView() ?: return@withContext null
        val deferred = CompletableDeferred<String?>()
        val script = "(function() { return document.body ? document.body.innerText : ''; })();"
        wv.evaluateJavascript(script) { result ->
            val unquoted = result?.removeSurrounding("\"")
                ?.replace("\\n", "\n")
                ?.replace("\\t", "\t")
                ?.replace("\\\"", "\"")
            deferred.complete(unquoted)
        }
        withTimeoutOrNull(2000L) { deferred.await() }
    }

    override suspend fun getSelectedText(): String? = withContext(Dispatchers.Main) {
        val wv = viewModel.getActiveWebView() ?: return@withContext null
        val deferred = CompletableDeferred<String?>()
        val script = "(function() { return window.getSelection ? window.getSelection().toString() : ''; })();"
        wv.evaluateJavascript(script) { result ->
            val unquoted = result?.removeSurrounding("\"")
            deferred.complete(unquoted)
        }
        withTimeoutOrNull(1500L) { deferred.await() }
    }

    override suspend fun findInPage(query: String): FindResult = withContext(Dispatchers.Main) {
        viewModel.setFindQuery(query)
        val wv = viewModel.getActiveWebView()
        wv?.findAllAsync(query)
        FindResult(
            query = query,
            matchCount = viewModel.findMatchCount.value,
            currentIndex = viewModel.findCurrentIndex.value
        )
    }

    override suspend fun executeScript(javascript: String): String? = withContext(Dispatchers.Main) {
        val wv = viewModel.getActiveWebView() ?: return@withContext null
        val deferred = CompletableDeferred<String?>()
        wv.evaluateJavascript(javascript) { result ->
            deferred.complete(result)
        }
        withTimeoutOrNull(3000L) { deferred.await() }
    }

    override suspend fun getHistory(query: String?, limit: Int): List<HistoryEntryInfo> = withContext(Dispatchers.IO) {
        val list = if (query.isNullOrBlank()) {
            repository.history.first().take(limit)
        } else {
            repository.searchHistory(query).first().take(limit)
        }
        list.map {
            HistoryEntryInfo(
                id = it.id,
                title = it.title,
                url = it.url,
                timestamp = it.timestamp
            )
        }
    }

    override suspend fun getBookmarks(): List<BookmarkEntryInfo> = withContext(Dispatchers.IO) {
        repository.bookmarks.first().map {
            BookmarkEntryInfo(
                id = it.id,
                title = it.title,
                url = it.url,
                folder = it.folder,
                isReadingList = it.isReadingList
            )
        }
    }

    override suspend fun getDownloads(): List<DownloadItemInfo> = withContext(Dispatchers.IO) {
        repository.downloads.first().map {
            DownloadItemInfo(
                id = it.id,
                fileName = it.fileName,
                url = it.url,
                status = it.status.name,
                timestamp = it.timestamp
            )
        }
    }

    override suspend fun getCookies(url: String): String? = withContext(Dispatchers.IO) {
        try {
            CookieManager.getInstance().getCookie(url)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getPermissions(domain: String): SitePermissionInfo? = withContext(Dispatchers.IO) {
        val perm = repository.getSitePermission(domain) ?: return@withContext null
        SitePermissionInfo(
            domain = perm.domain,
            cameraAllowed = perm.cameraAllowed,
            micAllowed = perm.micAllowed,
            locationAllowed = perm.locationAllowed,
            notificationsAllowed = perm.notificationsAllowed
        )
    }
}
