package com.example.data.connector

/**
 * Supported 3rd party integration services in GVONE Connector Hub.
 */
enum class ConnectorType(
    val displayName: String,
    val brandColorHex: Long,
    val description: String,
    val defaultIconName: String
) {
    GOOGLE_DRIVE(
        displayName = "Google Drive",
        brandColorHex = 0xFF4285F4,
        description = "Sync docs, spreadsheets, slides and research archives",
        defaultIconName = "drive"
    ),
    GITHUB(
        displayName = "GitHub",
        brandColorHex = 0xFF24292E,
        description = "Repositories, codebases, pull requests and issue tracking",
        defaultIconName = "github"
    ),
    SLACK(
        displayName = "Slack",
        brandColorHex = 0xFF4A154B,
        description = "Team workspaces, channels, direct messages and announcements",
        defaultIconName = "slack"
    ),
    NOTION(
        displayName = "Notion",
        brandColorHex = 0xFF000000,
        description = "Workspace pages, research databases, roadmaps and notes",
        defaultIconName = "notion"
    ),
    DROPBOX(
        displayName = "Dropbox",
        brandColorHex = 0xFF0061FE,
        description = "Cloud storage, media folders, document backups and downloads",
        defaultIconName = "dropbox"
    )
}

enum class ConnectionStatus {
    CONNECTED,
    SYNCING,
    DISCONNECTED,
    ACTION_REQUIRED
}

data class ConnectorPermission(
    val id: String,
    val title: String,
    val description: String,
    val isGranted: Boolean = true,
    val isCritical: Boolean = false
)

data class ConnectorAccount(
    val id: String,
    val type: ConnectorType,
    val accountName: String,
    val emailOrHandle: String,
    val organization: String,
    val status: ConnectionStatus = ConnectionStatus.CONNECTED,
    val connectedAt: String,
    val lastSyncedTime: String,
    val storageOrItemCountText: String,
    val allowAiDataAccess: Boolean = true,
    val permissions: List<ConnectorPermission> = emptyList(),
    val indexedItemCount: Int = 0,
    val syncFrequency: String = "Hourly"
)

data class DriveFileItem(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeFormatted: String,
    val modifiedTime: String,
    val isSyncedLocally: Boolean = false,
    val localFilePath: String? = null
)

data class GitHubRepoItem(
    val id: String,
    val name: String,
    val fullName: String,
    val isPrivate: Boolean,
    val description: String,
    val language: String,
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
    val cloneUrl: String,
    val isClonedLocally: Boolean = false
)

data class SlackChannelItem(
    val id: String,
    val name: String,
    val isPrivate: Boolean,
    val memberCount: Int,
    val topic: String,
    val unreadCount: Int = 0,
    val lastActivityTime: String
)

data class NotionPageItem(
    val id: String,
    val title: String,
    val emoji: String = "📄",
    val databaseTitle: String,
    val lastEditedTime: String,
    val excerpt: String
)

data class DropboxFileItem(
    val id: String,
    val name: String,
    val path: String,
    val sizeFormatted: String,
    val isFolder: Boolean,
    val modifiedTime: String
)

/**
 * Knowledge snippet indexed from a connected account and exposed directly to Gemini AI.
 */
data class ConnectedKnowledgeSnippet(
    val id: String,
    val connectorType: ConnectorType,
    val accountName: String,
    val itemTitle: String,
    val contentSnippet: String,
    val category: String,
    val referenceUrl: String,
    val lastUpdated: String
)
