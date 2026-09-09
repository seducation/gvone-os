package com.example.data.files

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Storage locations for GVONE Universal File System.
 */
enum class StorageLocation(val displayName: String, val folderName: String) {
    MY_FILES("My Files", ""),
    CLOUD("Cloud", "Cloud"),
    DOWNLOADS("Downloads", "Downloads"),
    FAVORITES("Favorites", ""),
    RECENT("Recent", "")
}

/**
 * File category type for styling, viewer routing, and icons.
 */
enum class FileType(
    val title: String,
    val defaultExtension: String,
    val color: Color
) {
    FOLDER("Folder", "", Color(0xFFF59E0B)),
    MARKDOWN("Markdown", "md", Color(0xFF38BDF8)),
    TEXT("Text File", "txt", Color(0xFF94A3B8)),
    JSON("JSON", "json", Color(0xFF10B981)),
    CODE("Code File", "kt", Color(0xFFA78BFA)),
    PDF("PDF Document", "pdf", Color(0xFFEF4444)),
    IMAGE("Image", "png", Color(0xFFEC4899)),
    ARCHIVE("Archive", "zip", Color(0xFFEAB308)),
    UNKNOWN("File", "", Color(0xFF64748B))
}

/**
 * Metadata representation of a file or directory in GVONE File System.
 */
data class GVONEFileItem(
    val id: String,
    val name: String,
    val path: String, // Relative path from gvone_fs root, e.g. "Projects/main.kt"
    val absolutePath: String, // Absolute path on the device
    val parentPath: String, // Relative parent folder, e.g. "Projects"
    val isDirectory: Boolean,
    val size: Long,
    val formattedSize: String,
    val lastModified: Long,
    val formattedDate: String,
    val extension: String,
    val fileType: FileType,
    val isFavorite: Boolean = false,
    val mimeType: String = "application/octet-stream"
)

/**
 * Behavior when opening a file.
 */
enum class FileOpenBehavior(val displayName: String) {
    TAB_VIEWER("Open in GVONE Tab"),
    SYSTEM_DEFAULT("Open with External App")
}

/**
 * Association of file extension to viewer and behavior.
 */
data class FileAssociation(
    val extension: String,
    val label: String,
    val behavior: FileOpenBehavior = FileOpenBehavior.TAB_VIEWER
)

/**
 * Configuration for Files & Storage Settings.
 */
data class FilesStorageConfig(
    val defaultDownloadLocation: String = "Downloads",
    val defaultOpenBehavior: FileOpenBehavior = FileOpenBehavior.TAB_VIEWER,
    val showHiddenFiles: Boolean = false,
    val autoOrganization: Boolean = true,
    val cloudSyncEnabled: Boolean = true,
    val associations: List<FileAssociation> = listOf(
        FileAssociation("md", "Markdown Viewer / Editor"),
        FileAssociation("txt", "Text Editor"),
        FileAssociation("json", "JSON Editor"),
        FileAssociation("kt", "Code Editor"),
        FileAssociation("pdf", "PDF Reader"),
        FileAssociation("png", "Image Viewer"),
        FileAssociation("jpg", "Image Viewer")
    )
)

/**
 * Detailed metadata inspection dialog model.
 */
data class FileMetadata(
    val name: String,
    val path: String,
    val absolutePath: String,
    val parentFolder: String,
    val sizeFormatted: String,
    val bytes: Long,
    val createdFormatted: String,
    val modifiedFormatted: String,
    val mimeType: String,
    val permissions: String,
    val isFavorite: Boolean,
    val md5Checksum: String
)

/**
 * Summary of storage used by GVONE File System.
 */
data class StorageUsageSummary(
    val totalFilesCount: Int,
    val totalFoldersCount: Int,
    val totalSizeBytes: Long,
    val formattedTotalSize: String,
    val documentsSize: String,
    val projectsSize: String,
    val imagesSize: String,
    val downloadsSize: String
)
