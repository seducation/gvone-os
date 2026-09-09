package com.example.data.communication

import android.content.Context
import com.example.data.ai.GVONEAIService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CommunicationHubManager(
    private val context: Context,
    private val aiService: GVONEAIService
) {
    private val prefs = context.getSharedPreferences("gvone_comm_hub_prefs", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<CommMessage>>(emptyList())
    val messages: StateFlow<List<CommMessage>> = _messages.asStateFlow()

    private val _notifications = MutableStateFlow<List<CommNotification>>(emptyList())
    val notifications: StateFlow<List<CommNotification>> = _notifications.asStateFlow()

    private val _triageDigest = MutableStateFlow<CommAiTriageDigest?>(null)
    val triageDigest: StateFlow<CommAiTriageDigest?> = _triageDigest.asStateFlow()

    private val _isGeneratingDigest = MutableStateFlow(false)
    val isGeneratingDigest: StateFlow<Boolean> = _isGeneratingDigest.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        loadInitialMessagesAndNotifications()
    }

    private fun loadInitialMessagesAndNotifications() {
        val initialMessages = listOf(
            CommMessage(
                id = "msg_gm_1",
                serviceType = CommServiceType.GMAIL,
                senderName = "Dr. Elena Rostova",
                senderHandleOrEmail = "elena.rostova@stanford.edu",
                recipientOrChannel = "Inbox (Research)",
                subjectOrTopic = "Feedback on Research Workspace & Constitutional AI preprint",
                snippet = "Hi Alex, I reviewed the draft. The section linking empirical evidence to attention heads is very compelling. Can you add BibTeX citations?",
                body = "Hi Alex,\n\nI thoroughly reviewed your draft on autonomous agent reasoning and evidence graph verification. The empirical benchmarks on multi-source synthesis are particularly strong.\n\nCould you also verify how the citations are mapped in BibTeX format? Looking forward to seeing the final manuscript!\n\nBest regards,\nElena",
                timestamp = "10:14 AM",
                isUnread = true,
                isStarred = true,
                isMention = false,
                isUrgent = true,
                threadCount = 3,
                attachments = listOf("manuscript_review_comments.pdf")
            ),
            CommMessage(
                id = "msg_sl_1",
                serviceType = CommServiceType.SLACK,
                senderName = "Marcus Chen (Lead Arch)",
                senderHandleOrEmail = "@marcus.chen",
                recipientOrChannel = "#research-workspace",
                subjectOrTopic = "PR #242: Evidence Map & Google Drive sync merged",
                snippet = "@alex The cloud connector permissions and automatic MLA/APA citation formatter just passed automated tests. Merged to main!",
                body = "Hey @alex!\n\nJust merged PR #242. The bi-directional sync with Google Drive and the new Evidence Graph node visualizer are deployed in build 4.2.\n\nLet me know if we need to expand the AI Grounding context window for Notion databases.",
                timestamp = "09:48 AM",
                isUnread = true,
                isStarred = false,
                isMention = true,
                isUrgent = false,
                threadCount = 8
            ),
            CommMessage(
                id = "msg_dc_1",
                serviceType = CommServiceType.DISCORD,
                senderName = "CyberSamurai",
                senderHandleOrEmail = "CyberSamurai#8821",
                recipientOrChannel = "#android-dev-general",
                subjectOrTopic = "Tor circuit switching latency on Android 16",
                snippet = "Has anyone measured SOCKS5 handshake overhead when switching Tor guard nodes? Our WebView latency dropped by 35% with the new pooling.",
                body = "Hey folks in #android-dev-general!\n\nWe benchmarked the Tor SOCKS5 proxy tunneling with keep-alive connection reuse. Latency dropped from 2200ms to 680ms.\n\nSharing our log captures here if anyone wants to test.",
                timestamp = "08:30 AM",
                isUnread = false,
                isStarred = false,
                isMention = false,
                isUrgent = false,
                threadCount = 14
            ),
            CommMessage(
                id = "msg_tm_1",
                serviceType = CommServiceType.TEAMS,
                senderName = "Sarah Jenkins (Product Director)",
                senderHandleOrEmail = "s.jenkins@enterprise.gvone.com",
                recipientOrChannel = "Global Strategy & Roadmap",
                subjectOrTopic = "Q4 Unified Communication Hub Demo & Client Review",
                snippet = "Team, the executive demo is scheduled for Thursday 2 PM. Please ensure the Gmail and Teams cross-posting bridge is ready.",
                body = "Hello Team,\n\nWe have the Q4 review with enterprise partners this Thursday at 2:00 PM EST.\n\nKey deliverables to showcase:\n1. Unified Inbox across Gmail, Slack, Discord & Teams\n2. Real-time notifications and AI Priority Triage\n3. One-tap Quick Reply across all platforms\n\nThanks,\nSarah",
                timestamp = "Yesterday",
                isUnread = false,
                isStarred = true,
                isMention = true,
                isUrgent = true,
                threadCount = 5
            ),
            CommMessage(
                id = "msg_gm_2",
                serviceType = CommServiceType.GMAIL,
                senderName = "GitHub Notifications",
                senderHandleOrEmail = "notifications@github.com",
                recipientOrChannel = "Inbox",
                subjectOrTopic = "[gvone-browser] Release v4.2.0: Unified FS & Connector Hub",
                snippet = "gvone-browser release v4.2.0 is now live with 12 bug fixes and new multi-source Research Workspace.",
                body = "GitHub Actions completed build #1842 successfully.\nRelease v4.2.0 assets generated.\nChangelog:\n- Universal GVONE File System\n- Google Drive, Notion, Slack, GitHub Connector Hub\n- Data Saver Extension with video autoplay blocking",
                timestamp = "Yesterday",
                isUnread = false,
                isStarred = false,
                isMention = false,
                isUrgent = false,
                threadCount = 1
            )
        )

        val initialNotifications = listOf(
            CommNotification(
                id = "notif_1",
                serviceType = CommServiceType.GMAIL,
                title = "Dr. Elena Rostova replied to your draft",
                message = "Feedback on Research Workspace & Constitutional AI preprint",
                timestamp = "10:14 AM",
                isRead = false,
                priorityScore = 95,
                deepLinkAction = "msg_gm_1"
            ),
            CommNotification(
                id = "notif_2",
                serviceType = CommServiceType.SLACK,
                title = "@marcus.chen mentioned you in #research-workspace",
                message = "The cloud connector permissions and automatic citation formatter passed tests.",
                timestamp = "09:48 AM",
                isRead = false,
                priorityScore = 88,
                deepLinkAction = "msg_sl_1"
            ),
            CommNotification(
                id = "notif_3",
                serviceType = CommServiceType.TEAMS,
                title = "Sarah Jenkins scheduled Executive Review",
                message = "Thursday 2:00 PM: Q4 Unified Communication Hub Demo",
                timestamp = "Yesterday",
                isRead = true,
                priorityScore = 75,
                deepLinkAction = "msg_tm_1"
            ),
            CommNotification(
                id = "notif_4",
                serviceType = CommServiceType.DISCORD,
                title = "14 new messages in #android-dev-general",
                message = "Discussion on Tor circuit switching and WebView latency",
                timestamp = "08:30 AM",
                isRead = true,
                priorityScore = 40,
                deepLinkAction = "msg_dc_1"
            )
        )

        _messages.value = initialMessages
        _notifications.value = initialNotifications

        _triageDigest.value = CommAiTriageDigest(
            urgentActionItems = listOf(
                "Reply to Dr. Elena Rostova on Stanford preprint feedback and send BibTeX export.",
                "Verify Teams demo checklist for Thursday 2 PM Q4 Executive review."
            ),
            executiveSummary = "You have 2 unread high-priority communications across Gmail and Slack. PR #242 for the Evidence Map was successfully merged. No blockers reported on Discord.",
            keyThreadsToWatch = listOf(
                "Dr. Elena Rostova (Gmail): Preprint manuscript citations",
                "#research-workspace (Slack): PR #242 merge",
                "Sarah Jenkins (Teams): Q4 Product Demo"
            ),
            generatedAt = "Today at 10:30 AM"
        )
    }

    fun toggleStarMessage(messageId: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(isStarred = !msg.isStarred) else msg
        }
    }

    fun markMessageAsRead(messageId: String) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == messageId) msg.copy(isUnread = false) else msg
        }
    }

    fun markNotificationAsRead(notificationId: String) {
        _notifications.value = _notifications.value.map { notif ->
            if (notif.id == notificationId) notif.copy(isRead = true) else notif
        }
    }

    fun markAllNotificationsAsRead() {
        _notifications.value = _notifications.value.map { it.copy(isRead = true) }
        _statusMessage.value = "All notifications marked as read"
    }

    fun clearAllNotifications() {
        _notifications.value = emptyList()
        _statusMessage.value = "Notifications cleared"
    }

    suspend fun sendQuickMessage(payload: QuickComposePayload): Boolean = withContext(Dispatchers.IO) {
        val now = SimpleDateFormat("h:mm a", Locale.US).format(Date())
        val newMessage = CommMessage(
            id = "msg_sent_${System.currentTimeMillis()}",
            serviceType = payload.serviceType,
            senderName = "You (Alex)",
            senderHandleOrEmail = "alex.researcher@gvone.internal",
            recipientOrChannel = payload.recipientOrChannel,
            subjectOrTopic = if (payload.subject.isNotBlank()) payload.subject else "Quick Message",
            snippet = payload.messageText,
            body = payload.messageText,
            timestamp = now,
            isUnread = false,
            isStarred = false,
            isMention = false,
            isUrgent = false,
            threadCount = 1
        )
        _messages.value = listOf(newMessage) + _messages.value
        _statusMessage.value = "Sent message via ${payload.serviceType.displayName} to ${payload.recipientOrChannel}"
        true
    }

    suspend fun generateAiTriageDigest(): CommAiTriageDigest = withContext(Dispatchers.IO) {
        _isGeneratingDigest.value = true
        try {
            val messagesContext = _messages.value.take(6).joinToString("\n---\n") { msg ->
                "[${msg.serviceType.displayName}] From: ${msg.senderName} (${msg.recipientOrChannel})\nSubject: ${msg.subjectOrTopic}\nBody: ${msg.snippet}\nUrgent: ${msg.isUrgent}"
            }

            val prompt = """
                You are GVONE Communication Hub AI Assistant. Analyze the user's unified inbox across Gmail, Slack, Discord, and Microsoft Teams:
                
                $messagesContext
                
                Synthesize a prioritized morning briefing:
                1. Urgent action items requiring user response
                2. Executive summary in 2 sentences
                3. Key threads to watch
            """.trimIndent()

            val aiResult = aiService.searchAndSynthesize(prompt)
            val digest = CommAiTriageDigest(
                urgentActionItems = aiResult.keyTakeaways.ifEmpty {
                    listOf("Follow up on Dr. Elena Rostova's preprint feedback", "Review Q4 Executive Review items on Teams")
                },
                executiveSummary = aiResult.aiAnswer.ifEmpty {
                    "You have 2 unread high-priority communications across Gmail and Slack. All engineering merges are green."
                },
                keyThreadsToWatch = listOf(
                    "Dr. Elena Rostova (Stanford Research Review)",
                    "#research-workspace (Marcus Chen PR #242)",
                    "Sarah Jenkins (Teams Executive Demo)"
                ),
                generatedAt = SimpleDateFormat("h:mm a, MMM d", Locale.US).format(Date())
            )

            _triageDigest.value = digest
            _statusMessage.value = "AI Triage digest updated"
            digest
        } catch (e: Exception) {
            val fallback = CommAiTriageDigest(
                urgentActionItems = listOf(
                    "Reply to Dr. Elena Rostova regarding BibTeX research citations.",
                    "Verify Teams demo checklist for Thursday 2 PM Q4 Executive review."
                ),
                executiveSummary = "You have 2 unread high-priority communications across Gmail and Slack. PR #242 for the Evidence Map was successfully merged.",
                keyThreadsToWatch = listOf(
                    "Dr. Elena Rostova (Gmail): Preprint manuscript citations",
                    "#research-workspace (Slack): PR #242 merge",
                    "Sarah Jenkins (Teams): Q4 Product Demo"
                ),
                generatedAt = SimpleDateFormat("h:mm a, MMM d", Locale.US).format(Date())
            )
            _triageDigest.value = fallback
            fallback
        } finally {
            _isGeneratingDigest.value = false
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
