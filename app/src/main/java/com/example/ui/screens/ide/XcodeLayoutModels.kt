package com.example.ui.screens.ide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Workspace display mode for the unified Xcode-style IDE browser window.
 * The central workspace dynamically displays either the full web browsing engine
 * or the monospace code editor without separate application screens.
 */
enum class XcodeWorkspaceMode(val label: String, val icon: ImageVector) {
    BROWSER("Web Browser", Icons.Rounded.Public),
    CODE_EDITOR("Code Studio", Icons.Rounded.Code),
    BOT_CHAT("Bot Chat", Icons.Rounded.AutoAwesome)
}

/**
 * Tabs available within the bottom-left Navigator Panel.
 * The separate activity bar has been removed and integrated directly into this navigator.
 */
enum class XcodeNavigatorCategory(
    val id: Int,
    val title: String,
    val icon: ImageVector,
    val badgeColorHex: Long
) {
    TABS(0, "Tabs", Icons.Rounded.Public, 0xFF00E5FF),
    CHATS(1, "Chats", Icons.Rounded.Terminal, 0xFF38BDF8),
    FILES(2, "Files", Icons.Rounded.InsertDriveFile, 0xFF10B981),
    BOTS(3, "Bots", Icons.Rounded.AutoAwesome, 0xFFA855F7),
    EXTENSIONS(4, "Extensions", Icons.Rounded.Extension, 0xFFF59E0B)
}

/**
 * Tabs within the bottom-right Inspector Panel for inspecting active workspace content.
 */
enum class XcodeInspectorTab(
    val title: String,
    val icon: ImageVector,
    val description: String
) {
    PROJECT("Project", Icons.Rounded.FolderSpecial, "Active project files, tabs, chats, extensions and bots"),
    ATTRIBUTES("Attributes", Icons.Rounded.Info, "Identity, title, URL, size and metadata"),
    CONSOLE("Debug Console", Icons.Rounded.Terminal, "Unified web console logs, IDE build & execution output"),
    DOM_INSPECT("DOM & Elements", Icons.Rounded.Code, "Visual outlines, inspect element, and tag tree"),
    NETWORK_SECURITY("Security", Icons.Rounded.Security, "HTTPS TLS status, Tor proxy, and cookies"),
    SAFETY_RULES("CNS Rules", Icons.Rounded.Shield, "Autonomous agent firewall and permission checks")
}

/**
 * Filter modes for the Xcode-style Debug Console output.
 */
enum class XcodeConsoleFilter(val label: String) {
    ALL("All Output"),
    TARGET("Target"),
    ERRORS("Errors"),
    WARNINGS("Warnings")
}
