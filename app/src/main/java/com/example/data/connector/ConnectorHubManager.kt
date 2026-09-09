package com.example.data.connector

import android.content.Context
import com.example.data.files.GVONEFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ConnectorHubManager(
    private val context: Context,
    private val fileSystem: GVONEFileSystem
) {
    private val prefs = context.getSharedPreferences("gvone_connector_hub_prefs", Context.MODE_PRIVATE)

    private val _connectors = MutableStateFlow<List<ConnectorAccount>>(emptyList())
    val connectors: StateFlow<List<ConnectorAccount>> = _connectors.asStateFlow()

    private val _driveFiles = MutableStateFlow<List<DriveFileItem>>(emptyList())
    val driveFiles: StateFlow<List<DriveFileItem>> = _driveFiles.asStateFlow()

    private val _gitHubRepos = MutableStateFlow<List<GitHubRepoItem>>(emptyList())
    val gitHubRepos: StateFlow<List<GitHubRepoItem>> = _gitHubRepos.asStateFlow()

    private val _slackChannels = MutableStateFlow<List<SlackChannelItem>>(emptyList())
    val slackChannels: StateFlow<List<SlackChannelItem>> = _slackChannels.asStateFlow()

    private val _notionPages = MutableStateFlow<List<NotionPageItem>>(emptyList())
    val notionPages: StateFlow<List<NotionPageItem>> = _notionPages.asStateFlow()

    private val _dropboxFiles = MutableStateFlow<List<DropboxFileItem>>(emptyList())
    val dropboxFiles: StateFlow<List<DropboxFileItem>> = _dropboxFiles.asStateFlow()

    private val _actionStatusMessage = MutableStateFlow<String?>(null)
    val actionStatusMessage: StateFlow<String?> = _actionStatusMessage.asStateFlow()

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        val initialConnectors = listOf(
            ConnectorAccount(
                id = "conn_google_drive",
                type = ConnectorType.GOOGLE_DRIVE,
                accountName = "Google Drive (Research & Archives)",
                emailOrHandle = "educationrb76@gmail.com",
                organization = "Personal & Academic Research",
                status = ConnectionStatus.CONNECTED,
                connectedAt = "Sep 4, 2026",
                lastSyncedTime = "5 mins ago",
                storageOrItemCountText = "14.2 GB of 100 GB used",
                allowAiDataAccess = true,
                indexedItemCount = 38,
                permissions = listOf(
                    ConnectorPermission("p_drive_read", "Read Drive Files", "Access documents, spreadsheets, presentations, and PDFs", isGranted = true, isCritical = true),
                    ConnectorPermission("p_drive_write", "Create & Edit Files", "Save research papers, notes, and browser exports to Drive", isGranted = true),
                    ConnectorPermission("p_drive_ai", "AI Semantic Indexing", "Allow Gemini AI to read Drive documents for research synthesis", isGranted = true)
                )
            ),
            ConnectorAccount(
                id = "conn_github",
                type = ConnectorType.GITHUB,
                accountName = "GitHub (Code & Repositories)",
                emailOrHandle = "@developer-hub",
                organization = "GVONE Open Systems",
                status = ConnectionStatus.CONNECTED,
                connectedAt = "Aug 28, 2026",
                lastSyncedTime = "Just now",
                storageOrItemCountText = "24 repositories indexed",
                allowAiDataAccess = true,
                indexedItemCount = 24,
                permissions = listOf(
                    ConnectorPermission("p_gh_repo", "Repository Access", "Read public and private repositories, commits, and releases", isGranted = true, isCritical = true),
                    ConnectorPermission("p_gh_issues", "Issues & Pull Requests", "Read and manage PR discussions, issues, and milestones", isGranted = true),
                    ConnectorPermission("p_gh_clone", "Local Clone to GVONE FS", "Allow pulling git repositories into /gvone_fs/Projects/", isGranted = true),
                    ConnectorPermission("p_gh_ai", "AI Code Search & Inspection", "Feed codebase symbols & architecture context to Gemini AI", isGranted = true)
                )
            ),
            ConnectorAccount(
                id = "conn_slack",
                type = ConnectorType.SLACK,
                accountName = "Slack (Engineering & Research)",
                emailOrHandle = "alex.researcher@gvone.internal",
                organization = "GVONE Research Core",
                status = ConnectionStatus.CONNECTED,
                connectedAt = "Sep 1, 2026",
                lastSyncedTime = "12 mins ago",
                storageOrItemCountText = "6 channels • 142 messages today",
                allowAiDataAccess = true,
                indexedItemCount = 86,
                permissions = listOf(
                    ConnectorPermission("p_slack_read", "Channel & Message History", "Read messages in authorized public and private channels", isGranted = true, isCritical = true),
                    ConnectorPermission("p_slack_post", "Post Messages & Replies", "Send messages, highlights, and quick replies to Slack channels", isGranted = true),
                    ConnectorPermission("p_slack_ai", "AI Team Knowledge Retrieval", "Let Gemini AI retrieve recent decisions, meeting notes, and links from Slack", isGranted = true)
                )
            ),
            ConnectorAccount(
                id = "conn_notion",
                type = ConnectorType.NOTION,
                accountName = "Notion (Knowledge Base)",
                emailOrHandle = "alex.notion@workspace.so",
                organization = "Global Knowledge Hub",
                status = ConnectionStatus.CONNECTED,
                connectedAt = "Sep 3, 2026",
                lastSyncedTime = "1 hour ago",
                storageOrItemCountText = "19 databases • 312 pages",
                allowAiDataAccess = true,
                indexedItemCount = 112,
                permissions = listOf(
                    ConnectorPermission("p_notion_pages", "Read Pages & Databases", "Search and index Notion knowledge bases and roadmaps", isGranted = true, isCritical = true),
                    ConnectorPermission("p_notion_clip", "Web Clipper & Exporter", "Append web clippings, citations, and summaries directly to Notion", isGranted = true),
                    ConnectorPermission("p_notion_ai", "AI Knowledge Grounding", "Expose Notion documentation directly to the Gemini AI Agent", isGranted = true)
                )
            ),
            ConnectorAccount(
                id = "conn_dropbox",
                type = ConnectorType.DROPBOX,
                accountName = "Dropbox (Cloud Vault)",
                emailOrHandle = "educationrb76@gmail.com",
                organization = "Personal Vault",
                status = ConnectionStatus.DISCONNECTED,
                connectedAt = "Not connected",
                lastSyncedTime = "Never",
                storageOrItemCountText = "0 items",
                allowAiDataAccess = false,
                indexedItemCount = 0,
                permissions = listOf(
                    ConnectorPermission("p_dropbox_read", "Files & Media Read", "Read cloud documents, backups, and media", isGranted = false),
                    ConnectorPermission("p_dropbox_sync", "Local Storage Sync", "Sync cloud folders to /gvone_fs/Cloud/", isGranted = false)
                )
            )
        )

        _connectors.value = initialConnectors

        // Populate mock live items
        _driveFiles.value = listOf(
            DriveFileItem("df_1", "Quantum Computing & Neural Nets.pdf", "application/pdf", "4.8 MB", "Sep 7, 2026", isSyncedLocally = true, localFilePath = "Cloud/GoogleDrive/Quantum Computing & Neural Nets.pdf"),
            DriveFileItem("df_2", "Autonomous Browser Agent Architecture.gdoc", "application/vnd.google-apps.document", "210 KB", "Yesterday", isSyncedLocally = false),
            DriveFileItem("df_3", "Literature Review - Web Scraping Ethics.gdoc", "application/vnd.google-apps.document", "145 KB", "Sep 5, 2026", isSyncedLocally = true, localFilePath = "Cloud/GoogleDrive/Literature Review.md"),
            DriveFileItem("df_4", "Tor Network Circuit Telemetry Dataset.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "12.4 MB", "Sep 2, 2026", isSyncedLocally = false)
        )

        _gitHubRepos.value = listOf(
            GitHubRepoItem("gh_1", "gvone-browser", "gvone/gvone-browser", isPrivate = true, "High-performance Android browser with built-in Tor & CLI", "Kotlin", 1420, 184, 12, "https://github.com/gvone/gvone-browser.git", isClonedLocally = true),
            GitHubRepoItem("gh_2", "gemini-research-workspace", "gvone/gemini-research-workspace", isPrivate = false, "Academic research workspace with automatic citations & evidence maps", "TypeScript", 530, 48, 4, "https://github.com/gvone/gemini-research-workspace.git", isClonedLocally = false),
            GitHubRepoItem("gh_3", "privacy-shield-core", "gvone/privacy-shield-core", isPrivate = false, "Ad-blocking, tracker suppression and anti-fingerprinting rules", "Rust", 890, 92, 7, "https://github.com/gvone/privacy-shield-core.git", isClonedLocally = true)
        )

        _slackChannels.value = listOf(
            SlackChannelItem("sl_1", "research-workspace", isPrivate = false, 18, "Discussions on paper citations, LLM reasoning and source analysis", 3, "10:42 AM"),
            SlackChannelItem("sl_2", "engineering-core", isPrivate = false, 42, "Android WebView, Tor SOCKS5, and Memory optimization", 0, "Yesterday"),
            SlackChannelItem("sl_3", "ai-connectors", isPrivate = true, 8, "API integrations: Drive, Notion, Slack, GitHub", 1, "09:15 AM"),
            SlackChannelItem("sl_4", "announcements", isPrivate = false, 120, "GVONE Project milestones and releases", 0, "Sep 4")
        )

        _notionPages.value = listOf(
            NotionPageItem("nt_1", "Research Workspace Product Requirements", "📑", "GVONE Specs", "Sep 7, 2026", "Comprehensive guide on multi-source synthesis, MLA/APA citations, and evidence mapping."),
            NotionPageItem("nt_2", "AI Knowledge Grounding Protocol", "🧠", "System Architecture", "Sep 6, 2026", "How Gemini AI queries connected Drive, Slack, GitHub and Notion databases dynamically."),
            NotionPageItem("nt_3", "Browser Extension Ecosystem Roadmap", "🧩", "Roadmap 2026", "Sep 2, 2026", "Data Saver mode, privacy shields, web widgets and custom terminal utilities.")
        )

        _dropboxFiles.value = listOf(
            DropboxFileItem("db_1", "Datasets_Archive_2026.zip", "/Research/Datasets_Archive_2026.zip", "420 MB", false, "Aug 30, 2026"),
            DropboxFileItem("db_2", "Conference_Presentations", "/Talks/Conference_Presentations", "18 Items", true, "Sep 1, 2026")
        )
    }

    fun toggleAiDataAccess(connectorId: String, enabled: Boolean) {
        _connectors.value = _connectors.value.map { conn ->
            if (conn.id == connectorId) {
                conn.copy(allowAiDataAccess = enabled)
            } else conn
        }
        _actionStatusMessage.value = "AI data access ${if (enabled) "enabled" else "disabled"} for ${getConnectorName(connectorId)}"
    }

    fun togglePermission(connectorId: String, permissionId: String, granted: Boolean) {
        _connectors.value = _connectors.value.map { conn ->
            if (conn.id == connectorId) {
                val updatedPerms = conn.permissions.map { perm ->
                    if (perm.id == permissionId) perm.copy(isGranted = granted) else perm
                }
                conn.copy(permissions = updatedPerms)
            } else conn
        }
    }

    fun connectService(type: ConnectorType) {
        val now = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date())
        _connectors.value = _connectors.value.map { conn ->
            if (conn.type == type) {
                conn.copy(
                    status = ConnectionStatus.CONNECTED,
                    connectedAt = now,
                    lastSyncedTime = "Just now",
                    allowAiDataAccess = true,
                    permissions = conn.permissions.map { it.copy(isGranted = true) }
                )
            } else conn
        }
        _actionStatusMessage.value = "Connected to ${type.displayName} successfully"
    }

    fun disconnectService(connectorId: String) {
        _connectors.value = _connectors.value.map { conn ->
            if (conn.id == connectorId) {
                conn.copy(
                    status = ConnectionStatus.DISCONNECTED,
                    allowAiDataAccess = false,
                    permissions = conn.permissions.map { it.copy(isGranted = false) }
                )
            } else conn
        }
        _actionStatusMessage.value = "Disconnected ${getConnectorName(connectorId)}"
    }

    suspend fun syncDriveFileToLocal(fileId: String): Boolean = withContext(Dispatchers.IO) {
        val driveFile = _driveFiles.value.find { it.id == fileId } ?: return@withContext false
        val destFolder = "Cloud/GoogleDrive"
        fileSystem.createFile(destFolder, driveFile.name, "", "# Synced from Google Drive: ${driveFile.name}\n\nLast Modified: ${driveFile.modifiedTime}\nMIME: ${driveFile.mimeType}\n\nContents synced to GVONE unified file storage.")
        _driveFiles.value = _driveFiles.value.map {
            if (it.id == fileId) it.copy(isSyncedLocally = true, localFilePath = "$destFolder/${driveFile.name}") else it
        }
        _actionStatusMessage.value = "Synced ${driveFile.name} to /gvone_fs/$destFolder/"
        true
    }

    suspend fun cloneGitHubRepo(repoId: String): Boolean = withContext(Dispatchers.IO) {
        val repo = _gitHubRepos.value.find { it.id == repoId } ?: return@withContext false
        val destFolder = "Projects/${repo.name}"
        fileSystem.createFile(destFolder, "README.md", "", "# ${repo.fullName}\n\n${repo.description}\n\n- Primary Language: ${repo.language}\n- Stars: ⭐ ${repo.stars}\n- Open Issues: ${repo.openIssues}\n\nCloned into GVONE Unified Workspace.")
        _gitHubRepos.value = _gitHubRepos.value.map {
            if (it.id == repoId) it.copy(isClonedLocally = true) else it
        }
        _actionStatusMessage.value = "Cloned ${repo.fullName} to /gvone_fs/$destFolder"
        true
    }

    suspend fun postSlackMessage(channelId: String, messageText: String): Boolean = withContext(Dispatchers.IO) {
        val channel = _slackChannels.value.find { it.id == channelId } ?: return@withContext false
        _actionStatusMessage.value = "Posted message to #${channel.name}"
        true
    }

    suspend fun clipPageToNotion(title: String, url: String, excerpt: String): Boolean = withContext(Dispatchers.IO) {
        val newPage = NotionPageItem(
            id = "nt_clip_${System.currentTimeMillis()}",
            title = title,
            emoji = "🔗",
            databaseTitle = "Research Web Clippings",
            lastEditedTime = "Just now",
            excerpt = excerpt
        )
        _notionPages.value = listOf(newPage) + _notionPages.value
        _actionStatusMessage.value = "Clipped '$title' to Notion Database"
        true
    }

    /**
     * AI KNOWLEDGE BRIDGE:
     * Queries connected accounts for context that Gemini AI can ground its answers on.
     * Only searches connectors where allowAiDataAccess == true.
     */
    fun queryConnectedDataForAi(query: String): List<ConnectedKnowledgeSnippet> {
        val results = mutableListOf<ConnectedKnowledgeSnippet>()
        val queryLower = query.lowercase(Locale.ROOT)

        val connectedTypes = _connectors.value
            .filter { it.status == ConnectionStatus.CONNECTED && it.allowAiDataAccess }
            .map { it.type }
            .toSet()

        if (ConnectorType.GOOGLE_DRIVE in connectedTypes) {
            _driveFiles.value.filter { it.name.lowercase(Locale.ROOT).contains(queryLower) || queryLower.contains("drive") || queryLower.contains("paper") || queryLower.contains("doc") }
                .forEach { file ->
                    results.add(
                        ConnectedKnowledgeSnippet(
                            id = file.id,
                            connectorType = ConnectorType.GOOGLE_DRIVE,
                            accountName = "Google Drive (Research)",
                            itemTitle = file.name,
                            contentSnippet = "Document in Google Drive: ${file.name} (${file.sizeFormatted}). Modified ${file.modifiedTime}.",
                            category = "Document",
                            referenceUrl = "https://drive.google.com/open?id=${file.id}",
                            lastUpdated = file.modifiedTime
                        )
                    )
                }
        }

        if (ConnectorType.GITHUB in connectedTypes) {
            _gitHubRepos.value.filter { it.name.lowercase(Locale.ROOT).contains(queryLower) || it.description.lowercase(Locale.ROOT).contains(queryLower) || queryLower.contains("code") || queryLower.contains("github") || queryLower.contains("repo") }
                .forEach { repo ->
                    results.add(
                        ConnectedKnowledgeSnippet(
                            id = repo.id,
                            connectorType = ConnectorType.GITHUB,
                            accountName = "GitHub",
                            itemTitle = repo.fullName,
                            contentSnippet = "Repository: ${repo.fullName} (${repo.language}, ⭐ ${repo.stars}). ${repo.description}. Open Issues: ${repo.openIssues}.",
                            category = "Source Code",
                            referenceUrl = repo.cloneUrl,
                            lastUpdated = "Recently active"
                        )
                    )
                }
        }

        if (ConnectorType.NOTION in connectedTypes) {
            _notionPages.value.filter { it.title.lowercase(Locale.ROOT).contains(queryLower) || it.excerpt.lowercase(Locale.ROOT).contains(queryLower) || queryLower.contains("notion") || queryLower.contains("notes") }
                .forEach { page ->
                    results.add(
                        ConnectedKnowledgeSnippet(
                            id = page.id,
                            connectorType = ConnectorType.NOTION,
                            accountName = "Notion Knowledge Base",
                            itemTitle = page.title,
                            contentSnippet = "Notion [${page.databaseTitle}]: ${page.excerpt}",
                            category = "Wiki Page",
                            referenceUrl = "https://notion.so/${page.id}",
                            lastUpdated = page.lastEditedTime
                        )
                    )
                }
        }

        if (ConnectorType.SLACK in connectedTypes) {
            _slackChannels.value.filter { it.name.lowercase(Locale.ROOT).contains(queryLower) || it.topic.lowercase(Locale.ROOT).contains(queryLower) || queryLower.contains("slack") || queryLower.contains("team") }
                .forEach { channel ->
                    results.add(
                        ConnectedKnowledgeSnippet(
                            id = channel.id,
                            connectorType = ConnectorType.SLACK,
                            accountName = "Slack Workspace",
                            itemTitle = "#${channel.name}",
                            contentSnippet = "Channel #${channel.name} (${channel.memberCount} members): ${channel.topic}.",
                            category = "Chat History",
                            referenceUrl = "slack://channel?id=${channel.id}",
                            lastUpdated = channel.lastActivityTime
                        )
                    )
                }
        }

        return results
    }

    fun clearStatusMessage() {
        _actionStatusMessage.value = null
    }

    private fun getConnectorName(id: String): String {
        return _connectors.value.find { it.id == id }?.accountName ?: "Service"
    }
}
