package com.example.data.repository

import android.content.Context
import androidx.room.Room
import com.example.data.command.CommandEngine
import com.example.data.local.GVONEDatabase
import com.example.data.model.BookmarkEntry
import com.example.data.model.BrowserTab
import com.example.data.model.CustomCommandEntity
import com.example.data.model.DownloadItem
import com.example.data.model.HistoryEntry
import com.example.data.model.SitePermission
import com.example.data.model.TabGroup
import kotlinx.coroutines.flow.Flow

class BrowserRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        GVONEDatabase::class.java,
        "gvone_browser.db"
    ).fallbackToDestructiveMigration().build()

    private val historyDao = db.historyDao()
    private val bookmarkDao = db.bookmarkDao()
    private val downloadDao = db.downloadDao()
    private val sitePermissionDao = db.sitePermissionDao()
    private val tabSessionDao = db.tabSessionDao()
    private val tabGroupDao = db.tabGroupDao()
    private val customCommandDao = db.customCommandDao()

    // Custom Commands
    val customCommands: Flow<List<CustomCommandEntity>> = customCommandDao.getAllCommands()
    val enabledCustomCommands: Flow<List<CustomCommandEntity>> = customCommandDao.getEnabledCommands()

    suspend fun saveCustomCommand(command: CustomCommandEntity) = customCommandDao.insertCommand(command)
    suspend fun saveCustomCommands(commands: List<CustomCommandEntity>) = customCommandDao.insertCommands(commands)
    suspend fun updateCustomCommand(command: CustomCommandEntity) = customCommandDao.updateCommand(command)
    suspend fun deleteCustomCommand(id: String) = customCommandDao.deleteById(id)
    suspend fun setCommandEnabled(id: String, enabled: Boolean) = customCommandDao.setEnabled(id, enabled)
    suspend fun setCommandPinned(id: String, pinned: Boolean) = customCommandDao.setPinned(id, pinned)

    suspend fun seedDefaultCommandsIfEmpty() {
        try {
            // Seed initial built-in commands so they are queryable and customizable in Room
            customCommandDao.insertCommands(CommandEngine.BUILT_IN_COMMANDS)
        } catch (_: Exception) {}
    }

    // History
    val history: Flow<List<HistoryEntry>> = historyDao.getAllHistory()
    fun searchHistory(query: String) = historyDao.searchHistory(query)
    suspend fun addHistory(entry: HistoryEntry) = historyDao.insertHistory(entry)
    suspend fun deleteHistory(entry: HistoryEntry) = historyDao.deleteHistory(entry)
    suspend fun deleteHistoryById(id: Long) = historyDao.deleteById(id)
    suspend fun deleteHistorySince(sinceTimestamp: Long) = historyDao.deleteSince(sinceTimestamp)
    suspend fun deleteHistoryByDomain(domain: String) = historyDao.deleteByDomain(domain)
    suspend fun clearAllHistory() = historyDao.clearAll()

    // Bookmarks
    val bookmarks: Flow<List<BookmarkEntry>> = bookmarkDao.getAllBookmarks()
    fun searchBookmarks(query: String) = bookmarkDao.searchBookmarks(query)
    suspend fun isBookmarked(url: String): Boolean = bookmarkDao.getStandardBookmarkByUrl(url) != null
    suspend fun isInReadingList(url: String): Boolean = bookmarkDao.getReadingListByUrl(url) != null
    suspend fun addBookmark(bookmark: BookmarkEntry) = bookmarkDao.insertBookmark(bookmark)
    suspend fun removeBookmark(bookmark: BookmarkEntry) = bookmarkDao.deleteBookmark(bookmark)
    suspend fun removeBookmarkByUrl(url: String) = bookmarkDao.deleteByUrl(url)
    suspend fun removeBookmarkByUrlAndType(url: String, isReadingList: Boolean) = bookmarkDao.deleteByUrlAndType(url, isReadingList)

    // Downloads
    val downloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()
    suspend fun addOrUpdateDownload(item: DownloadItem) = downloadDao.insertDownload(item)
    suspend fun updateDownload(item: DownloadItem) = downloadDao.updateDownload(item)
    suspend fun deleteDownload(id: String) = downloadDao.deleteById(id)

    // Site Permissions
    suspend fun getSitePermission(domain: String) = sitePermissionDao.getPermissionForDomain(domain)
    suspend fun saveSitePermission(permission: SitePermission) = sitePermissionDao.savePermission(permission)

    // Tab Session Restore
    val savedTabs: Flow<List<BrowserTab>> = tabSessionDao.getAllTabs()
    fun getTabsByEnvironment(envId: String): Flow<List<BrowserTab>> = tabSessionDao.getTabsByEnvironment(envId)
    suspend fun saveTab(tab: BrowserTab) = tabSessionDao.insertOrUpdateTab(tab)
    suspend fun saveTabs(tabs: List<BrowserTab>) = tabSessionDao.insertOrUpdateTabs(tabs)
    suspend fun deleteTab(id: String) = tabSessionDao.deleteById(id)
    suspend fun deleteTabsByEnvironment(envId: String) = tabSessionDao.deleteByEnvironment(envId)
    suspend fun clearTabs() = tabSessionDao.clearAllTabs()

    // Tab Groups
    val tabGroups: Flow<List<TabGroup>> = tabGroupDao.getAllGroups()
    fun getGroupsByEnvironment(envId: String): Flow<List<TabGroup>> = tabGroupDao.getGroupsByEnvironment(envId)
    suspend fun saveGroup(group: TabGroup) = tabGroupDao.insertOrUpdateGroup(group)
    suspend fun saveGroups(groups: List<TabGroup>) = tabGroupDao.insertOrUpdateGroups(groups)
    suspend fun deleteGroup(id: String) = tabGroupDao.deleteById(id)
    suspend fun deleteGroupsByEnvironment(envId: String) = tabGroupDao.deleteByEnvironment(envId)
    suspend fun clearGroups() = tabGroupDao.clearAllGroups()
}
