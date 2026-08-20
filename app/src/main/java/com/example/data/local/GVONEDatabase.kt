package com.example.data.local

import androidx.room.*
import com.example.data.model.BookmarkEntry
import com.example.data.model.BrowserTab
import com.example.data.model.DownloadItem
import com.example.data.model.HistoryEntry
import com.example.data.model.SitePermission
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history_entries WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntry): Long

    @Delete
    suspend fun deleteHistory(entry: HistoryEntry)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_entries WHERE timestamp >= :sinceTimestamp")
    suspend fun deleteSince(sinceTimestamp: Long)

    @Query("DELETE FROM history_entries WHERE url LIKE '%' || :domain || '%'")
    suspend fun deleteByDomain(domain: String)

    @Query("DELETE FROM history_entries")
    suspend fun clearAll()
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntry>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): BookmarkEntry?

    @Query("SELECT * FROM bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%'")
    fun searchBookmarks(query: String): Flow<List<BookmarkEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntry): Long

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntry)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntry)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(item: DownloadItem)

    @Update
    suspend fun updateDownload(item: DownloadItem)

    @Delete
    suspend fun deleteDownload(item: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface SitePermissionDao {
    @Query("SELECT * FROM site_permissions WHERE domain = :domain")
    suspend fun getPermissionForDomain(domain: String): SitePermission?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePermission(permission: SitePermission)

    @Query("DELETE FROM site_permissions WHERE domain = :domain")
    suspend fun deletePermission(domain: String)
}

@Dao
interface TabSessionDao {
    @Query("SELECT * FROM browser_tabs ORDER BY lastAccessedAt DESC")
    fun getAllTabs(): Flow<List<BrowserTab>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTab(tab: BrowserTab)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTabs(tabs: List<BrowserTab>)

    @Delete
    suspend fun deleteTab(tab: BrowserTab)

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM browser_tabs")
    suspend fun clearAllTabs()
}

@Database(
    entities = [
        HistoryEntry::class,
        BookmarkEntry::class,
        DownloadItem::class,
        SitePermission::class,
        BrowserTab::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GVONEDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao
    abstract fun sitePermissionDao(): SitePermissionDao
    abstract fun tabSessionDao(): TabSessionDao
}
