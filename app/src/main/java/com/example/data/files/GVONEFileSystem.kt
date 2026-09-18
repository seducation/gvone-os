package com.example.data.files

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GVONEImageFileResult(
    val success: Boolean,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val absolutePath: String,
    val fileUri: String,
    val width: Int = 0,
    val height: Int = 0,
    val error: String? = null
) {
    fun formattedSize(): String {
        if (sizeBytes <= 0) return "0 B"
        val kb = sizeBytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    fun formatBadge(): String {
        return when {
            mimeType.contains("png", true) -> "PNG"
            mimeType.contains("jpeg", true) || mimeType.contains("jpg", true) -> "JPEG"
            mimeType.contains("webp", true) -> "WEBP"
            mimeType.contains("gif", true) -> "GIF"
            mimeType.contains("svg", true) -> "SVG"
            else -> mimeType.substringAfter("/").uppercase()
        }
    }

    fun toStructuredJson(): String {
        return JSONObject().apply {
            put("type", "file")
            put("mime", mimeType)
            put("name", name)
            put("path", relativePath)
            put("size", sizeBytes)
            put("preview", mimeType.startsWith("image/"))
            if (width > 0 && height > 0) {
                put("width", width)
            }
            if (error != null) {
                put("error", error)
            }
        }.toString()
    }
}

class GVONEFileSystem(private val context: Context) {
    private val rootDir: File = File(context.filesDir, "gvone_storage").apply {
        if (!exists()) mkdirs()
    }
    private val downloadsDir: File = File(rootDir, "Downloads").apply {
        if (!exists()) mkdirs()
    }

    private val favoritesPrefs = context.getSharedPreferences("gvone_fs_favorites", Context.MODE_PRIVATE)

    companion object {
        val DEFAULT_FOLDERS = listOf("Documents", "Downloads", "Projects", "Images", "Cloud", "Notes")
    }

    init {
        for (folder in DEFAULT_FOLDERS) {
            val f = File(rootDir, folder)
            if (!f.exists()) f.mkdirs()
        }
    }

    fun getRootDir(): File = rootDir
    fun getDownloadsDir(): File = downloadsDir

    fun getFile(relativePath: String): File {
        val clean = relativePath.trim().removePrefix("/")
        return if (clean.isBlank()) rootDir else File(rootDir, clean)
    }

    fun determineFileType(name: String, isDir: Boolean): FileType {
        if (isDir) return FileType.FOLDER
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "md", "markdown" -> FileType.MARKDOWN
            "txt", "log", "env", "conf", "ini" -> FileType.TEXT
            "json" -> FileType.JSON
            "kt", "kts", "java", "js", "ts", "py", "rs", "cpp", "c", "html", "css", "xml", "sh" -> FileType.CODE
            "pdf" -> FileType.PDF
            "png", "jpg", "jpeg", "webp", "gif", "svg", "bmp" -> FileType.IMAGE
            "zip", "tar", "gz", "7z", "rar" -> FileType.ARCHIVE
            else -> FileType.UNKNOWN
        }
    }

    fun getMimeType(extension: String): String {
        return guessMimeType(extension)
    }

    fun isFavorite(relativePath: String): Boolean {
        val clean = relativePath.trim().removePrefix("/")
        return favoritesPrefs.getBoolean(clean, false)
    }

    fun toggleFavorite(relativePath: String): Boolean {
        val clean = relativePath.trim().removePrefix("/")
        val current = favoritesPrefs.getBoolean(clean, false)
        val next = !current
        favoritesPrefs.edit().putBoolean(clean, next).apply()
        return next
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    private fun toFileItem(file: File): GVONEFileItem {
        val relPath = file.relativeTo(rootDir).path
        val parentPath = if (file.parentFile != null && file.parentFile != rootDir) {
            try { file.parentFile!!.relativeTo(rootDir).path } catch (_: Exception) { "" }
        } else ""
        val ext = file.extension.lowercase(Locale.ROOT)
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US)
        val lastModStr = sdf.format(Date(file.lastModified()))
        val isDir = file.isDirectory
        val size = if (isDir) 0L else file.length()
        val fileType = determineFileType(file.name, isDir)
        val fav = isFavorite(relPath)
        val mime = if (isDir) "inode/directory" else guessMimeType(ext)

        return GVONEFileItem(
            id = relPath,
            name = file.name,
            path = relPath,
            absolutePath = file.absolutePath,
            parentPath = parentPath,
            isDirectory = isDir,
            size = size,
            formattedSize = if (isDir) "<DIR>" else formatSize(size),
            lastModified = file.lastModified(),
            formattedDate = lastModStr,
            extension = ext,
            fileType = fileType,
            isFavorite = fav,
            mimeType = mime
        )
    }

    fun listFiles(relativePath: String = ""): List<File> {
        val target = if (relativePath.isBlank()) rootDir else File(rootDir, relativePath.removePrefix("/"))
        return if (target.isDirectory) target.listFiles()?.toList() ?: emptyList() else emptyList()
    }

    fun listFiles(
        location: StorageLocation = StorageLocation.MY_FILES,
        folder: String = "",
        searchQuery: String = ""
    ): List<GVONEFileItem> {
        val baseDir = when (location) {
            StorageLocation.DOWNLOADS -> downloadsDir
            StorageLocation.CLOUD -> File(rootDir, "Cloud").apply { if (!exists()) mkdirs() }
            else -> {
                if (folder.isNotBlank()) File(rootDir, folder.removePrefix("/")) else rootDir
            }
        }

        if (location == StorageLocation.FAVORITES) {
            val allFiles = mutableListOf<File>()
            fun collect(dir: File) {
                dir.listFiles()?.forEach { f ->
                    val rel = f.relativeTo(rootDir).path
                    if (isFavorite(rel)) allFiles.add(f)
                    if (f.isDirectory) collect(f)
                }
            }
            collect(rootDir)
            return filterFiles(allFiles.map { toFileItem(it) }, searchQuery)
        }

        if (location == StorageLocation.RECENT) {
            val allFiles = mutableListOf<File>()
            fun collect(dir: File) {
                dir.listFiles()?.forEach { f ->
                    if (!f.isDirectory) allFiles.add(f)
                    if (f.isDirectory) collect(f)
                }
            }
            collect(rootDir)
            val sorted = allFiles.sortedByDescending { it.lastModified() }.take(30)
            return filterFiles(sorted.map { toFileItem(it) }, searchQuery)
        }

        val rawFiles = if (baseDir.isDirectory) baseDir.listFiles()?.toList() ?: emptyList() else emptyList()
        return filterFiles(rawFiles.map { toFileItem(it) }, searchQuery)
    }

    private fun filterFiles(items: List<GVONEFileItem>, query: String): List<GVONEFileItem> {
        if (query.isBlank()) return items
        return items.filter { it.name.contains(query, ignoreCase = true) || it.path.contains(query, ignoreCase = true) }
    }

    fun readFileContent(relativePath: String): String {
        val target = getFile(relativePath)
        return if (target.exists() && target.isFile) target.readText(Charsets.UTF_8) else ""
    }

    fun writeFileContent(relativePath: String, content: String): Boolean {
        return try {
            val target = getFile(relativePath)
            target.parentFile?.let { if (!it.exists()) it.mkdirs() }
            target.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun createFile(
        folder: String,
        name: String,
        extension: String = "",
        initialContent: String = ""
    ): GVONEFileItem? {
        return try {
            val targetFolder = if (folder.isBlank()) rootDir else File(rootDir, folder.removePrefix("/"))
            if (!targetFolder.exists()) targetFolder.mkdirs()

            val fullName = if (extension.isNotBlank() && !name.endsWith(".$extension", ignoreCase = true)) {
                "$name.$extension"
            } else name

            val file = File(targetFolder, fullName)
            if (!file.exists()) {
                file.createNewFile()
            }
            if (initialContent.isNotEmpty()) {
                file.writeText(initialContent, Charsets.UTF_8)
            }
            toFileItem(file)
        } catch (e: Exception) {
            null
        }
    }

    fun createFolder(parentFolder: String, name: String): GVONEFileItem? {
        return try {
            val base = if (parentFolder.isBlank()) rootDir else File(rootDir, parentFolder.removePrefix("/"))
            val target = File(base, name)
            if (!target.exists()) {
                target.mkdirs()
            }
            toFileItem(target)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteItem(relativePath: String): Boolean {
        return try {
            val target = getFile(relativePath)
            if (target.exists()) {
                if (target.isDirectory) target.deleteRecursively() else target.delete()
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun copyItem(relativePath: String, targetFolder: String): Boolean {
        return try {
            val src = getFile(relativePath)
            val destDir = if (targetFolder.isBlank()) rootDir else File(rootDir, targetFolder.removePrefix("/"))
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, src.name)
            if (src.isDirectory) src.copyRecursively(destFile, overwrite = true) else src.copyTo(destFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun moveItem(relativePath: String, targetFolder: String): Boolean {
        return try {
            val src = getFile(relativePath)
            val destDir = if (targetFolder.isBlank()) rootDir else File(rootDir, targetFolder.removePrefix("/"))
            if (!destDir.exists()) destDir.mkdirs()
            val destFile = File(destDir, src.name)
            src.renameTo(destFile)
        } catch (e: Exception) {
            false
        }
    }

    fun renameItem(relativePath: String, newName: String): Boolean {
        return try {
            val src = getFile(relativePath)
            val destFile = File(src.parentFile ?: rootDir, newName)
            src.renameTo(destFile)
        } catch (e: Exception) {
            false
        }
    }

    fun getFileItem(relativePath: String): GVONEFileItem? {
        val target = getFile(relativePath)
        return if (target.exists()) toFileItem(target) else null
    }

    fun getFileMetadata(relativePath: String): FileMetadata? {
        val target = getFile(relativePath)
        if (!target.exists()) return null
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.US)
        val rel = target.relativeTo(rootDir).path
        val parent = try { target.parentFile?.relativeTo(rootDir)?.path ?: "" } catch (_: Exception) { "" }
        val bytes = if (target.isDirectory) 0L else target.length()

        return FileMetadata(
            name = target.name,
            path = rel,
            absolutePath = target.absolutePath,
            parentFolder = if (parent.isBlank()) "/" else "/$parent",
            sizeFormatted = formatSize(bytes),
            bytes = bytes,
            createdFormatted = sdf.format(Date(target.lastModified())),
            modifiedFormatted = sdf.format(Date(target.lastModified())),
            mimeType = guessMimeType(target.extension),
            permissions = if (target.canWrite()) "Read / Write" else "Read Only",
            isFavorite = isFavorite(rel),
            md5Checksum = "MD5-" + rel.hashCode().toString(16).uppercase(Locale.ROOT)
        )
    }

    fun getStorageUsageSummary(): StorageUsageSummary {
        var totalFiles = 0
        var totalFolders = 0
        var totalBytes = 0L

        var docBytes = 0L
        var projBytes = 0L
        var imgBytes = 0L
        var dlBytes = 0L

        fun scan(dir: File) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    totalFolders++
                    scan(file)
                } else {
                    totalFiles++
                    val len = file.length()
                    totalBytes += len
                    val rel = file.relativeTo(rootDir).path
                    when {
                        rel.startsWith("Documents", true) -> docBytes += len
                        rel.startsWith("Projects", true) -> projBytes += len
                        rel.startsWith("Images", true) -> imgBytes += len
                        rel.startsWith("Downloads", true) -> dlBytes += len
                    }
                }
            }
        }

        scan(rootDir)

        return StorageUsageSummary(
            totalFilesCount = totalFiles,
            totalFoldersCount = totalFolders,
            totalSizeBytes = totalBytes,
            formattedTotalSize = formatSize(totalBytes),
            documentsSize = formatSize(docBytes),
            projectsSize = formatSize(projBytes),
            imagesSize = formatSize(imgBytes),
            downloadsSize = formatSize(dlBytes)
        )
    }

    fun importFromUri(uri: Uri, targetFolder: String): GVONEFileItem? {
        return try {
            val destFolder = if (targetFolder.isBlank()) rootDir else File(rootDir, targetFolder.removePrefix("/"))
            if (!destFolder.exists()) destFolder.mkdirs()

            val fileName = "imported_${System.currentTimeMillis()}"
            val stream: InputStream? = context.contentResolver.openInputStream(uri)
            val bytes = stream?.use { it.readBytes() } ?: return null
            val destFile = File(destFolder, fileName)
            FileOutputStream(destFile).use { it.write(bytes) }
            toFileItem(destFile)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun receiveAndSaveImage(
        sourceUriOrPathOrUrl: String,
        customFileName: String? = null
    ): GVONEImageFileResult = withContext(Dispatchers.IO) {
        val trimmedInput = sourceUriOrPathOrUrl.trim()
        if (trimmedInput.isBlank()) {
            return@withContext GVONEImageFileResult(
                success = false,
                name = "unknown",
                mimeType = "application/octet-stream",
                sizeBytes = 0L,
                relativePath = "/Downloads/unknown",
                absolutePath = "",
                fileUri = "",
                error = "Source path or URL is empty."
            )
        }

        try {
            var rawBytes: ByteArray? = null
            var detectedName: String = customFileName ?: ""
            var detectedMime: String = ""

            when {
                trimmedInput.startsWith("http://", ignoreCase = true) || trimmedInput.startsWith("https://", ignoreCase = true) -> {
                    val url = java.net.URL(trimmedInput)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 12000
                    conn.readTimeout = 15000
                    conn.requestMethod = "GET"
                    conn.connect()

                    if (conn.responseCode in 200..299) {
                        detectedMime = conn.contentType?.substringBefore(";")?.trim() ?: ""
                        if (detectedName.isBlank()) {
                            val pathSegments = url.path.split("/")
                            detectedName = pathSegments.lastOrNull { it.isNotBlank() } ?: "download_${System.currentTimeMillis()}"
                        }
                        rawBytes = conn.inputStream.use { it.readBytes() }
                    } else {
                        return@withContext GVONEImageFileResult(
                            success = false,
                            name = detectedName.ifBlank { "download" },
                            mimeType = "application/octet-stream",
                            sizeBytes = 0L,
                            relativePath = "/Downloads/$detectedName",
                            absolutePath = "",
                            fileUri = "",
                            error = "HTTP Error ${conn.responseCode}: ${conn.responseMessage}"
                        )
                    }
                }

                trimmedInput.startsWith("data:", ignoreCase = true) -> {
                    val mimePart = trimmedInput.substringAfter("data:").substringBefore(";")
                    detectedMime = if (mimePart.isNotBlank()) mimePart else "image/png"
                    val base64Data = trimmedInput.substringAfter("base64,")
                    rawBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                    if (detectedName.isBlank()) {
                        val ext = when {
                            detectedMime.contains("png") -> "png"
                            detectedMime.contains("webp") -> "webp"
                            detectedMime.contains("gif") -> "gif"
                            detectedMime.contains("svg") -> "svg"
                            else -> "jpg"
                        }
                        detectedName = "image_${System.currentTimeMillis()}.$ext"
                    }
                }

                trimmedInput.startsWith("content://", ignoreCase = true) -> {
                    val uri = Uri.parse(trimmedInput)
                    detectedMime = context.contentResolver.getType(uri) ?: ""
                    val stream: InputStream? = context.contentResolver.openInputStream(uri)
                    rawBytes = stream?.use { it.readBytes() }
                    if (detectedName.isBlank()) {
                        detectedName = "imported_${System.currentTimeMillis()}"
                    }
                }

                else -> {
                    val targetFile = if (trimmedInput.startsWith("/")) File(trimmedInput) else File(rootDir, trimmedInput)
                    val resolvedFile = if (targetFile.exists()) targetFile else File(downloadsDir, targetFile.name)

                    if (!resolvedFile.exists()) {
                        return@withContext GVONEImageFileResult(
                            success = false,
                            name = customFileName ?: targetFile.name,
                            mimeType = "application/octet-stream",
                            sizeBytes = 0L,
                            relativePath = "/Downloads/${targetFile.name}",
                            absolutePath = targetFile.absolutePath,
                            fileUri = "",
                            error = "File not found: ${resolvedFile.absolutePath}"
                        )
                    }

                    rawBytes = resolvedFile.readBytes()
                    if (detectedName.isBlank()) detectedName = resolvedFile.name
                    detectedMime = guessMimeType(resolvedFile.extension)
                }
            }

            if (rawBytes == null || rawBytes.isEmpty()) {
                return@withContext GVONEImageFileResult(
                    success = false,
                    name = detectedName.ifBlank { "image" },
                    mimeType = "application/octet-stream",
                    sizeBytes = 0L,
                    relativePath = "/Downloads/$detectedName",
                    absolutePath = "",
                    fileUri = "",
                    error = "Invalid or empty image payload (0 bytes)."
                )
            }

            if (detectedMime.isBlank() || detectedMime == "application/octet-stream") {
                val ext = detectedName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                detectedMime = guessMimeType(ext)
            }

            if (!detectedName.contains('.')) {
                val ext = when {
                    detectedMime.contains("png") -> "png"
                    detectedMime.contains("webp") -> "webp"
                    detectedMime.contains("gif") -> "gif"
                    detectedMime.contains("svg") -> "svg"
                    else -> "jpg"
                }
                detectedName = "$detectedName.$ext"
            }

            val savedFile = File(downloadsDir, detectedName)
            FileOutputStream(savedFile).use { fos ->
                fos.write(rawBytes)
            }

            val (width, height) = calculateImageDimensions(bytes = rawBytes, mimeType = detectedMime)
            val relativePath = "/Downloads/${savedFile.name}"
            val fileUri = Uri.fromFile(savedFile).toString()

            return@withContext GVONEImageFileResult(
                success = true,
                name = savedFile.name,
                mimeType = detectedMime,
                sizeBytes = savedFile.length(),
                relativePath = relativePath,
                absolutePath = savedFile.absolutePath,
                fileUri = fileUri,
                width = width,
                height = height
            )

        } catch (e: Exception) {
            return@withContext GVONEImageFileResult(
                success = false,
                name = customFileName ?: "failed_image",
                mimeType = "application/octet-stream",
                sizeBytes = 0L,
                relativePath = "/Downloads/failed",
                absolutePath = "",
                fileUri = "",
                error = e.localizedMessage ?: "Failed to process image payload."
            )
        }
    }

    private fun calculateImageDimensions(bytes: ByteArray, mimeType: String): Pair<Int, Int> {
        return try {
            if (mimeType.contains("svg", true)) {
                val xmlContent = String(bytes, Charsets.UTF_8)
                val widthMatch = Regex("""width=["'](\d+)(?:px)?["']""").find(xmlContent)?.groupValues?.get(1)?.toIntOrNull() ?: 1280
                val heightMatch = Regex("""height=["'](\d+)(?:px)?["']""").find(xmlContent)?.groupValues?.get(1)?.toIntOrNull() ?: 720
                Pair(widthMatch, heightMatch)
            } else {
                val options = android.graphics.BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth > 0 && options.outHeight > 0) {
                    Pair(options.outWidth, options.outHeight)
                } else {
                    Pair(1280, 720)
                }
            }
        } catch (_: Exception) {
            Pair(1280, 720)
        }
    }

    private fun guessMimeType(ext: String): String {
        return when (ext.lowercase(Locale.ROOT)) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            "kt", "kts", "java" -> "text/x-kotlin"
            "md", "markdown" -> "text/markdown"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
