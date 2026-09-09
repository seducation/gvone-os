package com.example.data.files

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Universal Unified File System for GVONE.
 * Provides a shared file layer across Browser Tabs, Terminal CLI,
 * AI Agents, and Connectors.
 */
class GVONEFileSystem(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "gvone_fs")
    private val prefs: SharedPreferences = context.getSharedPreferences("gvone_fs_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITES = "fs_favorites_set"
        private const val KEY_RECENT = "fs_recent_json"
        private const val KEY_CONFIG = "fs_storage_config_json"

        val DEFAULT_FOLDERS = listOf(
            "Documents",
            "Projects",
            "Images",
            "GVONE",
            "Downloads",
            "Cloud"
        )
    }

    init {
        initializeFileSystem()
    }

    /**
     * Initializes the root directory, standard folder hierarchy, and starter files.
     */
    fun initializeFileSystem() {
        try {
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }

            for (folderName in DEFAULT_FOLDERS) {
                val folder = File(rootDir, folderName)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
            }

            // Seed starter files if Documents is empty
            val docsFolder = File(rootDir, "Documents")
            val notesFile = File(docsFolder, "notes.md")
            if (!notesFile.exists()) {
                notesFile.writeText(
                    """
                    # GVONE Universal File System
                    
                    Welcome to the GVONE integrated file manager.
                    This workspace is unified across:
                    - **Browser Tabs**: Open Markdown, Text, Code, JSON, Images, and PDFs in native tabs.
                    - **Terminal CLI**: Access, edit, and manage these files using `ls`, `cat`, `mkdir`, and `touch`.
                    - **AI Agents**: Read project files for context and assistance.
                    - **Connectors**: Sync files with cloud services and web sessions.
                    
                    ## Quick Tips
                    - Tap any file to open it in an editor or viewer.
                    - Long-press for file options (Rename, Move, Copy, Share, Info).
                    - Use the **+ button** to create new files or folders.
                    """.trimIndent()
                )
            }

            val specsFile = File(docsFolder, "specs.txt")
            if (!specsFile.exists()) {
                specsFile.writeText(
                    """
                    GVONE System Specifications
                    OS: Android (ARM64 / x86_64)
                    Architecture: Unified Shared Storage Layer
                    Bridge: Bidirectional WebSocket/HTTP v2.4
                    Security: Encrypted Sandboxed File Root
                    Root: ${rootDir.absolutePath}
                    """.trimIndent()
                )
            }

            val projectsFolder = File(rootDir, "Projects")
            val mainKt = File(projectsFolder, "main.kt")
            if (!mainKt.exists()) {
                mainKt.writeText(
                    """
                    package com.example.project
                    
                    fun main() {
                        println("Hello from GVONE Project Runtime!")
                        val status = "Ready"
                        println("Status: ${'$'}status")
                    }
                    """.trimIndent()
                )
            }

            val readmeMd = File(projectsFolder, "README.md")
            if (!readmeMd.exists()) {
                readmeMd.writeText(
                    """
                    # Project Directory
                    
                    Place your development source code, web projects, and scripts here.
                    You can execute commands or inspect code via the GVONE Terminal.
                    """.trimIndent()
                )
            }

            val configJson = File(projectsFolder, "config.json")
            if (!configJson.exists()) {
                configJson.writeText(
                    """
                    {
                      "appName": "GVONE Workspace",
                      "version": "1.0.0",
                      "features": {
                        "unifiedFileSystem": true,
                        "terminalIntegration": true,
                        "tabViewer": true
                      },
                      "environment": "Development"
                    }
                    """.trimIndent()
                )
            }

            val gvoneFolder = File(rootDir, "GVONE")
            val archMd = File(gvoneFolder, "architecture.md")
            if (!archMd.exists()) {
                archMd.writeText(
                    """
                    # GVONE Architecture: Unified File Layer
                    
                    Unlike standard browsers that only support a download directory,
                    GVONE provides an operating-system-level shared file layer.
                    
                    ```
                    [ GVONE Universal File System (gvone_fs) ]
                                    │
                        ┌───────────┼───────────┐
                        ▼           ▼           ▼
                   [Browser]   [Terminal]   [Connectors]
                    ```
                    
                    This guarantees consistent file operations across mobile,
                    desktop, and future Linux/Flutter migrations.
                    """.trimIndent()
                )
            }

            val downloadsFolder = File(rootDir, "Downloads")
            val samplePdf = File(downloadsFolder, "document.pdf")
            if (!samplePdf.exists()) {
                createSamplePdf(samplePdf)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Creates a valid, real Android PDF document so the PDF viewer can immediately render it.
     */
    private fun createSamplePdf(file: File) {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 20f
                isAntiAlias = true
            }

            val titlePaint = Paint().apply {
                color = AndroidColor.rgb(16, 185, 129)
                textSize = 28f
                isFakeBoldText = true
                isAntiAlias = true
            }

            canvas.drawText("GVONE Document Reader", 50f, 80f, titlePaint)
            paint.textSize = 14f
            canvas.drawText("Native Portable Document Format (PDF) Support", 50f, 120f, paint)
            canvas.drawText("This file is rendered by the built-in GVONE PDF viewer in a tab.", 50f, 150f, paint)
            canvas.drawText("Pages can be browsed, zoomed, and exported easily.", 50f, 180f, paint)

            val linePaint = Paint().apply {
                color = AndroidColor.LTGRAY
                strokeWidth = 2f
            }
            canvas.drawLine(50f, 210f, 545f, 210f, linePaint)
            canvas.drawText("Universal File System • Browser • Terminal • Agent", 50f, 240f, paint)

            doc.finishPage(page)
            FileOutputStream(file).use { out ->
                doc.writeTo(out)
            }
            doc.close()
        } catch (_: Exception) {
            file.writeText("%PDF-1.4\n% Sample GVONE PDF Document")
        }
    }

    /**
     * Lists files based on active location and folder.
     */
    suspend fun listFiles(
        location: StorageLocation,
        currentFolder: String = "",
        searchQuery: String = "",
        showHidden: Boolean = false
    ): List<GVONEFileItem> = withContext(Dispatchers.IO) {
        val favorites = getFavoritePaths()

        val rawList = when (location) {
            StorageLocation.MY_FILES -> {
                val targetDir = if (currentFolder.isBlank()) rootDir else File(rootDir, currentFolder)
                scanDirectory(targetDir, showHidden)
            }
            StorageLocation.CLOUD -> {
                val cloudDir = File(rootDir, "Cloud")
                scanDirectory(cloudDir, showHidden)
            }
            StorageLocation.DOWNLOADS -> {
                val downloadsDir = File(rootDir, "Downloads")
                scanDirectory(downloadsDir, showHidden)
            }
            StorageLocation.FAVORITES -> {
                favorites.mapNotNull { relPath ->
                    val f = File(rootDir, relPath)
                    if (f.exists()) toFileItem(f, favorites) else null
                }
            }
            StorageLocation.RECENT -> {
                getRecentFiles()
            }
        }

        val filtered = if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase(Locale.ROOT)
            rawList.filter { it.name.lowercase(Locale.ROOT).contains(query) }
        } else {
            rawList
        }

        // Sort: Folders first, then alphabetically
        filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    private fun scanDirectory(dir: File, showHidden: Boolean): List<GVONEFileItem> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val favorites = getFavoritePaths()
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { file ->
            if (!showHidden && file.name.startsWith(".")) false else true
        }.map { toFileItem(it, favorites) }
    }

    fun toFileItem(file: File, favorites: Set<String> = getFavoritePaths()): GVONEFileItem {
        val relativePath = file.relativeTo(rootDir).path
        val parentPath = file.parentFile?.relativeTo(rootDir)?.path.orEmpty().let {
            if (it == ".") "" else it
        }
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
        val fileType = determineFileType(file.name, isDir)
        val size = if (isDir) computeDirectorySize(file) else file.length()
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        return GVONEFileItem(
            id = relativePath,
            name = file.name,
            path = relativePath,
            absolutePath = file.absolutePath,
            parentPath = parentPath,
            isDirectory = isDir,
            size = size,
            formattedSize = if (isDir) "${(file.list()?.size ?: 0)} items" else formatFileSize(size),
            lastModified = file.lastModified(),
            formattedDate = sdf.format(Date(file.lastModified())),
            extension = ext,
            fileType = fileType,
            isFavorite = favorites.contains(relativePath),
            mimeType = getMimeType(ext)
        )
    }

    fun getFileItem(relativePath: String): GVONEFileItem? {
        val file = getFile(relativePath)
        return if (file.exists()) toFileItem(file) else null
    }

    fun determineFileType(fileName: String, isDirectory: Boolean): FileType {
        if (isDirectory) return FileType.FOLDER
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "md", "markdown" -> FileType.MARKDOWN
            "txt", "log", "conf", "cfg", "env" -> FileType.TEXT
            "json" -> FileType.JSON
            "kt", "java", "py", "js", "ts", "html", "htm", "css", "sh", "bash", "xml", "c", "cpp", "h", "rs", "go", "sql" -> FileType.CODE
            "pdf" -> FileType.PDF
            "png", "jpg", "jpeg", "webp", "gif", "svg", "bmp", "ico" -> FileType.IMAGE
            "zip", "tar", "gz", "rar", "7z" -> FileType.ARCHIVE
            else -> FileType.UNKNOWN
        }
    }

    fun getMimeType(ext: String): String {
        return when (ext.lowercase(Locale.ROOT)) {
            "md", "markdown" -> "text/markdown"
            "txt", "log" -> "text/plain"
            "json" -> "application/json"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "py" -> "text/x-python"
            "js" -> "application/javascript"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun computeDirectorySize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) computeDirectorySize(f) else f.length()
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    /**
     * Creates a new file inside the designated parent folder.
     */
    suspend fun createFile(
        parentFolder: String,
        name: String,
        extension: String,
        initialContent: String = ""
    ): GVONEFileItem = withContext(Dispatchers.IO) {
        val targetDir = if (parentFolder.isBlank()) rootDir else File(rootDir, parentFolder)
        if (!targetDir.exists()) targetDir.mkdirs()

        val fullName = if (extension.isNotBlank() && !name.endsWith(".$extension", ignoreCase = true)) {
            "$name.$extension"
        } else {
            name
        }

        var candidate = File(targetDir, fullName)
        var count = 1
        while (candidate.exists()) {
            val base = fullName.substringBeforeLast('.')
            val ext = fullName.substringAfterLast('.', "")
            val newName = if (ext.isNotEmpty()) "$base ($count).$ext" else "$base ($count)"
            candidate = File(targetDir, newName)
            count++
        }

        candidate.writeText(initialContent)
        val item = toFileItem(candidate, getFavoritePaths())
        recordRecent(item)
        item
    }

    /**
     * Creates a new folder inside parent folder.
     */
    suspend fun createFolder(
        parentFolder: String,
        name: String
    ): GVONEFileItem = withContext(Dispatchers.IO) {
        val targetDir = if (parentFolder.isBlank()) rootDir else File(rootDir, parentFolder)
        if (!targetDir.exists()) targetDir.mkdirs()

        var candidate = File(targetDir, name)
        var count = 1
        while (candidate.exists()) {
            candidate = File(targetDir, "$name ($count)")
            count++
        }

        candidate.mkdirs()
        toFileItem(candidate, getFavoritePaths())
    }

    /**
     * Imports a file selected from system storage via Uri.
     */
    suspend fun importFromUri(
        uri: Uri,
        targetFolder: String
    ): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (targetFolder.isBlank()) File(rootDir, "Downloads") else File(rootDir, targetFolder)
            if (!targetDir.exists()) targetDir.mkdirs()

            val fileName = queryFileName(uri) ?: "imported_file_${System.currentTimeMillis()}"
            val targetFile = File(targetDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val item = toFileItem(targetFile, getFavoritePaths())
            recordRecent(item)
            item
        } catch (_: Exception) {
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (colIndex != -1) {
                            name = cursor.getString(colIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    /**
     * Reads text content of a file.
     */
    suspend fun readFileContent(relativePath: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, relativePath)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    /**
     * Writes text content to an existing or new file.
     */
    suspend fun writeFileContent(relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, relativePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            recordRecent(toFileItem(file, getFavoritePaths()))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes a file or directory recursively.
     */
    suspend fun deleteItem(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, relativePath)
            if (file.exists()) {
                val success = file.deleteRecursively()
                if (success) {
                    removeFavorite(relativePath)
                }
                success
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Renames a file or directory.
     */
    suspend fun renameItem(relativePath: String, newName: String): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val source = File(rootDir, relativePath)
            if (!source.exists()) return@withContext null
            val dest = File(source.parentFile, newName)
            if (dest.exists()) return@withContext null
            val success = source.renameTo(dest)
            if (success) {
                val wasFav = isFavorite(relativePath)
                if (wasFav) {
                    removeFavorite(relativePath)
                    addFavorite(dest.relativeTo(rootDir).path)
                }
                toFileItem(dest, getFavoritePaths())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Moves a file or directory to a target folder.
     */
    suspend fun moveItem(sourcePath: String, targetFolder: String): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val source = File(rootDir, sourcePath)
            if (!source.exists()) return@withContext null
            val targetDir = if (targetFolder.isBlank()) rootDir else File(rootDir, targetFolder)
            if (!targetDir.exists()) targetDir.mkdirs()

            val dest = File(targetDir, source.name)
            if (dest.exists()) return@withContext null

            val success = source.renameTo(dest)
            if (success) {
                toFileItem(dest, getFavoritePaths())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies a file or folder to a target folder.
     */
    suspend fun copyItem(sourcePath: String, targetFolder: String): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val source = File(rootDir, sourcePath)
            if (!source.exists()) return@withContext null
            val targetDir = if (targetFolder.isBlank()) rootDir else File(rootDir, targetFolder)
            if (!targetDir.exists()) targetDir.mkdirs()

            var dest = File(targetDir, source.name)
            var count = 1
            while (dest.exists()) {
                val base = source.name.substringBeforeLast('.')
                val ext = source.name.substringAfterLast('.', "")
                val newName = if (ext.isNotEmpty()) "$base (copy $count).$ext" else "$base (copy $count)"
                dest = File(targetDir, newName)
                count++
            }

            source.copyRecursively(dest, overwrite = false)
            toFileItem(dest, getFavoritePaths())
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // FAVORITES & RECENT MANAGEMENT
    // =========================================================================

    fun getFavoritePaths(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun isFavorite(relativePath: String): Boolean {
        return getFavoritePaths().contains(relativePath)
    }

    fun toggleFavorite(relativePath: String): Boolean {
        val current = getFavoritePaths().toMutableSet()
        val newState = if (current.contains(relativePath)) {
            current.remove(relativePath)
            false
        } else {
            current.add(relativePath)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return newState
    }

    private fun addFavorite(path: String) {
        val current = getFavoritePaths().toMutableSet()
        current.add(path)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    private fun removeFavorite(path: String) {
        val current = getFavoritePaths().toMutableSet()
        current.remove(path)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    fun recordRecent(item: GVONEFileItem) {
        try {
            val raw = prefs.getString(KEY_RECENT, "[]")
            val arr = JSONArray(raw)
            val updated = JSONArray()
            updated.put(item.path)

            for (i in 0 until arr.length()) {
                val p = arr.getString(i)
                if (p != item.path && updated.length() < 25) {
                    updated.put(p)
                }
            }
            prefs.edit().putString(KEY_RECENT, updated.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getRecentFiles(): List<GVONEFileItem> {
        val favorites = getFavoritePaths()
        return try {
            val raw = prefs.getString(KEY_RECENT, "[]")
            val arr = JSONArray(raw)
            val list = mutableListOf<GVONEFileItem>()
            for (i in 0 until arr.length()) {
                val path = arr.getString(i)
                val file = File(rootDir, path)
                if (file.exists()) {
                    list.add(toFileItem(file, favorites))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearRecentFiles() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    /**
     * Inspects complete metadata for "Get Info" dialog.
     */
    suspend fun getFileMetadata(relativePath: String): FileMetadata = withContext(Dispatchers.IO) {
        val file = File(rootDir, relativePath)
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' hh:mm:ss a", Locale.US)
        val ext = file.extension.lowercase(Locale.ROOT)
        val isDir = file.isDirectory
        val sizeBytes = if (isDir) computeDirectorySize(file) else file.length()
        val perm = StringBuilder().apply {
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
        }.toString()

        val md5 = if (!isDir && file.length() < 10 * 1024 * 1024) {
            calculateMD5(file)
        } else {
            "N/A (Directory or Large File)"
        }

        FileMetadata(
            name = file.name,
            path = relativePath,
            absolutePath = file.absolutePath,
            parentFolder = file.parentFile?.name ?: "Root",
            sizeFormatted = if (isDir) "${file.list()?.size ?: 0} items (${formatFileSize(sizeBytes)})" else formatFileSize(sizeBytes),
            bytes = sizeBytes,
            createdFormatted = sdf.format(Date(file.lastModified())),
            modifiedFormatted = sdf.format(Date(file.lastModified())),
            mimeType = if (isDir) "directory/folder" else getMimeType(ext),
            permissions = "POSIX: $perm (0755)",
            isFavorite = isFavorite(relativePath),
            md5Checksum = md5
        )
    }

    private fun calculateMD5(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Storage summary metrics for Settings → Files & Storage.
     */
    suspend fun getStorageUsageSummary(): StorageUsageSummary = withContext(Dispatchers.IO) {
        var totalCount = 0
        var totalFolders = 0
        var totalBytes = 0L

        fun scan(f: File) {
            if (f.isDirectory) {
                totalFolders++
                f.listFiles()?.forEach { scan(it) }
            } else {
                totalCount++
                totalBytes += f.length()
            }
        }

        scan(rootDir)

        val docsDir = File(rootDir, "Documents")
        val projectsDir = File(rootDir, "Projects")
        val imagesDir = File(rootDir, "Images")
        val downloadsDir = File(rootDir, "Downloads")

        StorageUsageSummary(
            totalFilesCount = totalCount,
            totalFoldersCount = totalFolders,
            totalSizeBytes = totalBytes,
            formattedTotalSize = formatFileSize(totalBytes),
            documentsSize = formatFileSize(computeDirectorySize(docsDir)),
            projectsSize = formatFileSize(computeDirectorySize(projectsDir)),
            imagesSize = formatFileSize(computeDirectorySize(imagesDir)),
            downloadsSize = formatFileSize(computeDirectorySize(downloadsDir))
        )
    }

    /**
     * Exports all user files into a compressed ZIP archive.
     */
    suspend fun exportAllAsZip(outputZip: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipOutputStream(FileOutputStream(outputZip)).use { zipOut ->
                fun addFile(fileToZip: File, parentName: String) {
                    if (fileToZip.isDirectory) {
                        val folderName = if (parentName.isEmpty()) fileToZip.name else "$parentName/${fileToZip.name}"
                        zipOut.putNextEntry(ZipEntry("$folderName/"))
                        zipOut.closeEntry()
                        fileToZip.listFiles()?.forEach { child ->
                            addFile(child, folderName)
                        }
                    } else {
                        FileInputStream(fileToZip).use { fis ->
                            val zipEntry = ZipEntry(if (parentName.isEmpty()) fileToZip.name else "$parentName/${fileToZip.name}")
                            zipOut.putNextEntry(zipEntry)
                            fis.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }
                }

                rootDir.listFiles()?.forEach { file ->
                    addFile(file, "")
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getFile(relativePath: String): File {
        return File(rootDir, relativePath)
    }

    fun getRootDir(): File = rootDir
}
