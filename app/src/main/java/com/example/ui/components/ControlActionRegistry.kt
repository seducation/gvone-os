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
    const val ACTION_TERMINAL = "terminal"
    const val ACTION_BRIDGE = "bridge"

    /**
     * Builds the default action list in the order specified by GVONE design:
     * 1. Photos
     * 2. Camera
     * 3. Avatar
     * 4. Connector
     * 5. Terminal (CLI)
     * 6. Bridge
     */
    fun buildDefaultActions(
        onPhotos: () -> Unit,
        onCamera: () -> Unit,
        onAvatar: () -> Unit,
        onConnector: () -> Unit,
        onTerminal: () -> Unit,
        onBridge: () -> Unit
    ): List<ControlActionItem> {
        return listOf(
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
    }
}
