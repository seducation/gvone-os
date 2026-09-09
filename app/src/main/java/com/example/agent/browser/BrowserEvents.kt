package com.example.agent.browser

/**
 * Standard browser events emitted by the Kotlin Browser and received by the Agent System.
 */
sealed interface BrowserEvent {
    data class PageLoading(val tabId: String, val url: String, val progress: Int) : BrowserEvent
    data class PageLoaded(val tabId: String, val url: String, val title: String) : BrowserEvent
    data class PageFailed(val tabId: String, val url: String, val error: String) : BrowserEvent
    data class UrlChanged(val tabId: String, val newUrl: String) : BrowserEvent
    data class TitleChanged(val tabId: String, val newTitle: String) : BrowserEvent
    data class TabCreated(val tabId: String, val isPrivate: Boolean) : BrowserEvent
    data class TabClosed(val tabId: String) : BrowserEvent
    data class TabChanged(val previousTabId: String?, val activeTabId: String) : BrowserEvent
    data class TextSelected(val tabId: String, val selectedText: String) : BrowserEvent
    data class DownloadStarted(val downloadId: String, val fileName: String, val url: String) : BrowserEvent
    data class DownloadCompleted(val downloadId: String, val fileName: String) : BrowserEvent
    data class PermissionRequested(val domain: String, val permissionType: String) : BrowserEvent
    data class HistoryChanged(val url: String, val title: String) : BrowserEvent
}

/**
 * High-level immutable snapshot of current browser state.
 */
data class BrowserStateSnapshot(
    val currentTabId: String?,
    val currentUrl: String?,
    val currentTitle: String?,
    val totalTabsCount: Int,
    val isPrivateMode: Boolean,
    val isTorActive: Boolean,
    val isLoading: Boolean
)
