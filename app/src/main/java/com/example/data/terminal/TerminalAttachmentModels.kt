package com.example.data.terminal

import java.util.UUID

/**
 * Types of attachments supported in GVONE Terminal for AI and Agent workflows.
 */
enum class AttachmentType {
    PHOTO,
    DOCUMENT,
    WEB_PAGE,
    CODE_SNIPPET,
    AUDIO,
    OTHER
}

/**
 * Represents an item attached in the Terminal to be sent to AI or Autonomous Agents.
 * Each item has an [isSelectedForSending] flag enabling granular select/deselect
 * from both suggestion chips and the attachment management panel.
 */
data class TerminalAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: AttachmentType = AttachmentType.PHOTO,
    val uriString: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val contentSummary: String = "",
    val isSelectedForSending: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun formattedSize(): String {
        if (sizeBytes <= 0L) {
            return when (type) {
                AttachmentType.WEB_PAGE -> "Web Tab"
                AttachmentType.CODE_SNIPPET -> "${contentSummary.length} chars"
                else -> "Attachment"
            }
        }
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", sizeBytes / (1024f * 1024f))
        }
    }

    fun toPromptContext(): String {
        val typeLabel = when (type) {
            AttachmentType.PHOTO -> "Photo/Image"
            AttachmentType.DOCUMENT -> "Document"
            AttachmentType.WEB_PAGE -> "Web Page"
            AttachmentType.CODE_SNIPPET -> "Code/Snippet"
            AttachmentType.AUDIO -> "Audio Clip"
            AttachmentType.OTHER -> "File"
        }
        val detail = if (contentSummary.isNotBlank()) " | Summary: $contentSummary" else ""
        val uri = if (uriString != null) " | URI: $uriString" else ""
        val mime = mimeType ?: "unknown"
        return "[$typeLabel: $name ($mime)$detail$uri]"
    }
}
