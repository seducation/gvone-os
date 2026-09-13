package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

const val START_PAGE_URL = "gvone://newtab"
const val HOME_WEB_APP_URL = "https://rssgroupfeed-jaelvwfd.manus.space"
const val ADDRESS_BAR_TARGET_URL = "https://charassist-c4uzg7hb.manus.space"

fun isInternalHomeUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return true
    return url == START_PAGE_URL || url == "about:blank" || url == "chrome://newtab" || url == "gvone://newtab"
}

enum class TabSortOption {
    DEFAULT,
    BY_TITLE,
    BY_WEBSITE
}

@Entity(tableName = "tab_groups")
data class TabGroup(
    @PrimaryKey val id: String,
    val name: String,
    val order: Int = 0,
    val colorHex: String? = null,
    val environmentId: String = "personal",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_tabs")
data class BrowserTab(
    @PrimaryKey val id: String,
    val title: String = "New Tab",
    val url: String = "gvone://newtab",
    val faviconUrl: String? = null,
    val isPrivate: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val desktopMode: Boolean = false,
    val zoomLevel: Float = 1.0f,
    val isLoading: Boolean = false,
    val progress: Int = 100,
    val tabGroupId: String? = null,
    val environmentId: String = "personal",
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_entries")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val faviconUrl: String? = null
)

@Entity(tableName = "bookmarks")
data class BookmarkEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String = "Favorites",
    val timestamp: Long = System.currentTimeMillis(),
    val faviconUrl: String? = null,
    val isReadingList: Boolean = false,
    val isUnread: Boolean = true
)

data class SavedPasswordEntry(
    val id: String,
    val website: String,
    val username: String,
    val passwordMasked: String = "••••••••••••",
    val lastUsed: String = "Today"
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String,
    val fileName: String,
    val url: String,
    val destinationPath: String? = null,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val mimeType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
    CANCELLED
}

@Entity(tableName = "site_permissions")
data class SitePermission(
    @PrimaryKey val domain: String,
    val cameraAllowed: Boolean? = null,
    val micAllowed: Boolean? = null,
    val locationAllowed: Boolean? = null,
    val notificationsAllowed: Boolean? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class SearchEngineType(val displayName: String, val searchUrl: String) {
    GVONE("GVONE AI Search", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    CUSTOM("Custom", "")
}

enum class ShortsAudioMode(val displayName: String, val description: String) {
    ALWAYS_UNMUTED("Always Unmuted", "Automatically unmutes audio on every Short when scrolling"),
    ALWAYS_MUTED("Always Muted", "Keeps audio muted on every Short when scrolling until manually unmuted"),
    REMEMBER_STATE("Remember Last Choice", "Preserves whether you last muted or unmuted Shorts")
}

data class BrowserSettings(
    val searchEngine: SearchEngineType = SearchEngineType.GVONE,
    val customSearchUrl: String = "",
    val trackingProtection: Boolean = true,
    val blockPopups: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val doNotTrack: Boolean = true,
    val forceHttps: Boolean = true,
    val torEnabled: Boolean = false,
    val torProxyHost: String = "127.0.0.1",
    val torProxyPort: Int = 9050,
    val desktopSiteDefault: Boolean = false,
    val addressBarBottom: Boolean = true,
    val showQuickShortcuts: Boolean = true,
    val aiSearchAutoTrigger: Boolean = true,
    val autoLoadTargetOnFocus: Boolean = false,
    val autoLoadTargetUrl: String = ADDRESS_BAR_TARGET_URL,
    val bidirectionalBridgeEnabled: Boolean = true,
    val bridgeApplyToAllWebsites: Boolean = false,
    val terminalAutoAppearOnAddressBar: Boolean = false,
    val terminalHeightFraction: Float = 0.85f,
    val terminalPinnedToScreen: Boolean = false,
    val terminalSwappedPosition: Boolean = false,
    val shortsAudioMode: ShortsAudioMode = ShortsAudioMode.ALWAYS_UNMUTED,
    val backgroundPlayEnabled: Boolean = true,
    val developerBarEnabled: Boolean = false
)

data class SourceCard(
    val title: String,
    val domain: String,
    val url: String,
    val snippet: String,
    val faviconUrl: String? = null
)

data class GVONEAISearchResult(
    val query: String,
    val aiAnswer: String,
    val keyTakeaways: List<String> = emptyList(),
    val sources: List<SourceCard> = emptyList(),
    val followUpQuestions: List<String> = emptyList()
)
