package com.example.data.communication

enum class CommServiceType(
    val displayName: String,
    val brandColorHex: Long,
    val badgeLabel: String
) {
    GMAIL("Gmail", 0xFFEA4335, "Email"),
    SLACK("Slack", 0xFF4A154B, "Team Chat"),
    DISCORD("Discord", 0xFF5865F2, "Community"),
    TEAMS("Microsoft Teams", 0xFF6264A7, "Enterprise")
}

data class CommMessage(
    val id: String,
    val serviceType: CommServiceType,
    val senderName: String,
    val senderHandleOrEmail: String,
    val recipientOrChannel: String,
    val subjectOrTopic: String,
    val snippet: String,
    val body: String,
    val timestamp: String,
    val isUnread: Boolean = false,
    val isStarred: Boolean = false,
    val isMention: Boolean = false,
    val isUrgent: Boolean = false,
    val threadCount: Int = 1,
    val attachments: List<String> = emptyList()
)

data class CommNotification(
    val id: String,
    val serviceType: CommServiceType,
    val title: String,
    val message: String,
    val timestamp: String,
    val isRead: Boolean = false,
    val priorityScore: Int = 50, // 0 - 100
    val deepLinkAction: String = ""
)

data class QuickComposePayload(
    val serviceType: CommServiceType,
    val recipientOrChannel: String,
    val subject: String = "",
    val messageText: String
)

data class CommAiTriageDigest(
    val urgentActionItems: List<String>,
    val executiveSummary: String,
    val keyThreadsToWatch: List<String>,
    val generatedAt: String
)
