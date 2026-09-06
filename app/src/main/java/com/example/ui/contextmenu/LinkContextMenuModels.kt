package com.example.ui.contextmenu

/**
 * Element classification for context menu detection
 */
enum class ContextMenuTargetType {
    LINK,
    IMAGE,
    IMAGE_LINK,
    VIDEO,
    AUDIO,
    DOCUMENT;

    val isMedia: Boolean
        get() = this == IMAGE || this == IMAGE_LINK || this == VIDEO || this == AUDIO

    val isImage: Boolean
        get() = this == IMAGE || this == IMAGE_LINK

    val isVideo: Boolean
        get() = this == VIDEO
}

/**
 * Rich payload representing the element long-pressed by the user
 */
data class LinkContextMenuData(
    val url: String,
    val title: String = "",
    val text: String = "",
    val srcUrl: String? = null,
    val targetType: ContextMenuTargetType = ContextMenuTargetType.LINK,
    val mimeType: String? = null,
    val faviconUrl: String? = null,
    val isBookmarked: Boolean = false,
    val isInReadingList: Boolean = false,
    val activeTabGroupId: String? = null
) {
    /**
     * Primary label displayed in the context menu header preview
     */
    val displayTitle: String
        get() {
            if (text.isNotBlank()) return text.trim()
            if (title.isNotBlank()) return title.trim()
            val clean = url.substringAfter("://").substringBefore("?")
            val lastSegment = clean.substringAfterLast("/", "")
            if (lastSegment.isNotBlank() && lastSegment.length > 2) return lastSegment
            return clean
        }

    /**
     * Secondary address displayed in the context menu header preview (clean domain or url)
     */
    val displaySubtitle: String
        get() {
            return try {
                val host = java.net.URI(url).host
                if (!host.isNullOrBlank()) host.removePrefix("www.") else url
            } catch (_: Exception) {
                url
            }
        }

    /**
     * The primary media asset URL if available (e.g. image or video stream), otherwise fallback to the url
     */
    val mediaUrl: String
        get() = srcUrl?.ifBlank { null } ?: url
}

/**
 * Payload for the lightweight in-page live preview
 */
data class PagePreviewData(
    val url: String,
    val title: String = "",
    val faviconUrl: String? = null
)
