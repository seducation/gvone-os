package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents an individual control action card inside the bottom action sheet.
 */
data class ControlActionItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val contentDescription: String,
    val badge: String? = null,
    val onClick: () -> Unit
)

/**
 * Reusable Registry for Address Bar Control actions.
 * Allows adding or reordering actions cleanly without altering UI code.
 */
object ControlActionRegistry {
    const val ACTION_PHOTOS = "photos"
    const val ACTION_CAMERA = "camera"
    const val ACTION_AVATAR = "avatar"
    const val ACTION_CONNECTOR = "connector"
    const val ACTION_CONNECTOR_HUB = "connector_hub"
    const val ACTION_RESEARCH = "research_workspace"
    const val ACTION_DATA_SAVER = "data_saver"
    const val ACTION_COMMUNICATION = "comm_hub"
    const val ACTION_FILES = "files"
    const val ACTION_TERMINAL = "terminal"
    const val ACTION_BRIDGE = "bridge"

    /**
     * Builds the default action list in the order specified by GVONE design:
     * 1. Photos
     * 2. Camera
     * 3. Avatar
     * 4. Connector (Website Access & Account Connector)
     * 5. Files (Universal File System)
     * 6. Terminal (CLI)
     * 7. Bridge
     *
     * When extended callbacks (Connector Hub, Research, Data Saver, Comms Hub) are provided,
     * they are appended cleanly to provide rich browser functionality.
     */
    fun buildDefaultActions(
        onPhotos: () -> Unit,
        onCamera: () -> Unit,
        onAvatar: () -> Unit,
        onConnector: () -> Unit,
        onFiles: () -> Unit = {},
        onTerminal: () -> Unit,
        onBridge: () -> Unit,
        onConnectorHub: (() -> Unit)? = null,
        onResearch: (() -> Unit)? = null,
        onDataSaver: (() -> Unit)? = null,
        onCommunicationHub: (() -> Unit)? = null
    ): List<ControlActionItem> {
        val list = mutableListOf(
            ControlActionItem(
                id = ACTION_PHOTOS,
                title = "Photos",
                icon = Icons.Outlined.Image,
                contentDescription = "Access Photos and Wallpapers",
                onClick = onPhotos
            ),
            ControlActionItem(
                id = ACTION_CAMERA,
                title = "Camera",
                icon = Icons.Outlined.PhotoCamera,
                contentDescription = "Open Camera and Scanner",
                onClick = onCamera
            ),
            ControlActionItem(
                id = ACTION_AVATAR,
                title = "Avatar",
                icon = Icons.Outlined.AccountCircle,
                contentDescription = "Switch Profile and Personas",
                onClick = onAvatar
            ),
            ControlActionItem(
                id = ACTION_CONNECTOR,
                title = "Connector",
                icon = Icons.Outlined.Link,
                contentDescription = "Website Access & Account Connector",
                onClick = onConnector
            ),
            ControlActionItem(
                id = ACTION_FILES,
                title = "Files",
                icon = Icons.Outlined.Folder,
                contentDescription = "Universal GVONE File System",
                onClick = onFiles
            ),
            ControlActionItem(
                id = ACTION_TERMINAL,
                title = "Terminal",
                icon = Icons.Outlined.Terminal,
                contentDescription = "Open Terminal CLI Shell",
                onClick = onTerminal
            ),
            ControlActionItem(
                id = ACTION_BRIDGE,
                title = "Bridge",
                icon = Icons.Outlined.Tune,
                contentDescription = "Configure Bridge & Address Bar",
                onClick = onBridge
            )
        )

        if (onConnectorHub != null) {
            list.add(
                ControlActionItem(
                    id = ACTION_CONNECTOR_HUB,
                    title = "Hub",
                    icon = Icons.Outlined.Hub,
                    contentDescription = "Connector Hub (Drive, GitHub, Slack, Notion)",
                    onClick = onConnectorHub
                )
            )
        }
        if (onResearch != null) {
            list.add(
                ControlActionItem(
                    id = ACTION_RESEARCH,
                    title = "Research",
                    icon = Icons.Outlined.Article,
                    contentDescription = "Research Workspace & Evidence Map",
                    onClick = onResearch
                )
            )
        }
        if (onDataSaver != null) {
            list.add(
                ControlActionItem(
                    id = ACTION_DATA_SAVER,
                    title = "Saver",
                    icon = Icons.Outlined.DataSaverOn,
                    contentDescription = "Data Saver Extension",
                    onClick = onDataSaver
                )
            )
        }
        if (onCommunicationHub != null) {
            list.add(
                ControlActionItem(
                    id = ACTION_COMMUNICATION,
                    title = "Comms",
                    icon = Icons.Outlined.Forum,
                    contentDescription = "Unified Communication Hub",
                    onClick = onCommunicationHub
                )
            )
        }

        return list
    }
}
