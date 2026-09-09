package com.example.agent.browser

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class BrowserTabInfo(
    val id: String,
    val title: String,
    val url: String,
    val isPrivate: Boolean,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val desktopMode: Boolean = false,
    val isLoading: Boolean = false,
    val tabGroupId: String? = null
)

data class HistoryEntryInfo(
    val id: Long,
    val title: String,
    val url: String,
    val timestamp: Long
)

data class BookmarkEntryInfo(
    val id: Long,
    val title: String,
    val url: String,
    val folder: String,
    val isReadingList: Boolean
)

data class DownloadItemInfo(
    val id: String,
    val fileName: String,
    val url: String,
    val status: String,
    val timestamp: Long
)

data class SitePermissionInfo(
    val domain: String,
    val cameraAllowed: Boolean?,
    val micAllowed: Boolean?,
    val locationAllowed: Boolean?,
    val notificationsAllowed: Boolean?
)

data class FindResult(
    val query: String,
    val matchCount: Int,
    val currentIndex: Int
)

enum class BrowserCapabilityStatus {
    AVAILABLE,
    PARTIALLY_AVAILABLE,
    UNAVAILABLE
}

data class CapabilityDescriptor(
    val name: String,
    val status: BrowserCapabilityStatus,
    val details: String
)

/**
 * Clean architectural abstraction representing the actual capabilities
 * of the GVONE Browser / Chromium engine.
 * Agents interact with the browser ONLY through this controller.
 */
interface BrowserController {
    // Capability Matrix Discovery
    fun getSupportedCapabilities(): List<CapabilityDescriptor>

    // Navigation
    suspend fun openUrl(url: String, inNewTab: Boolean = false): Boolean
    suspend fun goBack(): Boolean
    suspend fun goForward(): Boolean
    suspend fun reload(): Boolean
    suspend fun stopLoading(): Boolean

    // Tabs
    suspend fun createTab(url: String? = null, isPrivate: Boolean = false, groupId: String? = null): String
    suspend fun closeTab(tabId: String): Boolean
    suspend fun switchTab(tabId: String): Boolean
    suspend fun getTabs(): List<BrowserTabInfo>
    suspend fun getCurrentTab(): BrowserTabInfo?

    // Page inspection & actions
    suspend fun getCurrentUrl(): String?
    suspend fun getTitle(): String?
    suspend fun getPageText(): String?
    suspend fun getSelectedText(): String?
    suspend fun findInPage(query: String): FindResult
    suspend fun executeScript(javascript: String): String?

    // Browser Data
    suspend fun getHistory(query: String? = null, limit: Int = 50): List<HistoryEntryInfo>
    suspend fun getBookmarks(): List<BookmarkEntryInfo>
    suspend fun getDownloads(): List<DownloadItemInfo>
    suspend fun getCookies(url: String): String?
    suspend fun getPermissions(domain: String): SitePermissionInfo?

    // State & Event streams
    val browserEvents: Flow<BrowserEvent>
    val browserState: StateFlow<BrowserStateSnapshot>
}
