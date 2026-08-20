package com.example.data.repository

import android.content.Context
import androidx.room.Room
import com.example.data.local.GVONEDatabase
import com.example.data.model.BookmarkEntry
import com.example.data.model.BrowserTab
import com.example.data.model.DownloadItem
import com.example.data.model.HistoryEntry
import com.example.data.model.SitePermission
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
    suspend fun isBookmarked(url: String): Boolean = bookmarkDao.getBookmarkByUrl(url) != null
    suspend fun addBookmark(bookmark: BookmarkEntry) = bookmarkDao.insertBookmark(bookmark)
    suspend fun removeBookmark(bookmark: BookmarkEntry) = bookmarkDao.deleteBookmark(bookmark)
    suspend fun removeBookmarkByUrl(url: String) = bookmarkDao.deleteByUrl(url)

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
    suspend fun saveTab(tab: BrowserTab) = tabSessionDao.insertOrUpdateTab(tab)
    suspend fun saveTabs(tabs: List<BrowserTab>) = tabSessionDao.insertOrUpdateTabs(tabs)
    suspend fun deleteTab(id: String) = tabSessionDao.deleteById(id)
    suspend fun clearTabs() = tabSessionDao.clearAllTabs()
}
