package com.example.data.files

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
                put("height", height)
            }
            if (error != null) {
                put("error", error)
            }
        }.toString()
    }
}

class GVONEFileSystem(private val context: Context) {
    private val rootDir: File = File(context.filesDir, "gvone_storage").apply { if (!exists()) mkdirs() }
    private val downloadsDir: File = File(rootDir, "Downloads").apply { if (!exists()) mkdirs() }

    fun getRootDir(): File = rootDir
    fun getDownloadsDir(): File = downloadsDir

    fun listFiles(relativePath: String = ""): List<File> {
        val target = if (relativePath.isBlank()) rootDir else File(rootDir, relativePath)
        return if (target.isDirectory) target.listFiles()?.toList() ?: emptyList() else emptyList()
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
                val ext = detectedName.substringAfterLast('.', "").lowercase()
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
        return when (ext.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }
}
