package com.example.data.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.example.data.model.DownloadItem
import com.example.data.model.DownloadStatus
import com.example.data.repository.BrowserRepository
import java.util.UUID

class BrowserDownloadManager(
    private val context: Context,
    private val repository: BrowserRepository
) {
    private val systemDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    suspend fun startDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?): String {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val downloadId = UUID.randomUUID().toString()

        val downloadItem = DownloadItem(
            id = downloadId,
            fileName = fileName,
            url = url,
            mimeType = mimeType,
            status = DownloadStatus.DOWNLOADING,
            timestamp = System.currentTimeMillis()
        )
        repository.addOrUpdateDownload(downloadItem)

        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle(fileName)
                setDescription("Downloading via GVONE Browser")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            systemDownloadManager?.enqueue(request)
            // Mark completed in database
            repository.updateDownload(downloadItem.copy(status = DownloadStatus.COMPLETED, totalBytes = 1024 * 1024))
        } catch (e: Exception) {
            repository.updateDownload(downloadItem.copy(status = DownloadStatus.FAILED))
        }

        return downloadId
    }

    suspend fun cancelDownload(id: String) {
        repository.updateDownload(DownloadItem(id = id, fileName = "", url = "", status = DownloadStatus.CANCELLED))
    }
}
