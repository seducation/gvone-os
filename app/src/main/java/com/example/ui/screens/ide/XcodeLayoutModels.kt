package com.example.ui.screens.ide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Workspace display mode for the unified Xcode-style IDE browser window.
 * The central workspace dynamically displays either the full web browsing engine,
 * the monospace code editor, bot chat, or native viewers for all file formats (PDF, Image, Markdown, Media, Data).
 */
enum class XcodeWorkspaceMode(
    val label: String,
    val icon: ImageVector,
    val tag: String = "",
    val description: String = ""
) {
    CODE_EDITOR("Xcode Editor", Icons.Rounded.Code, "Xcode", "Monospace IDE code studio & syntax"),
    BROWSER("Website Browser", Icons.Rounded.Public, "Website", "Live web engine & DevTools drawer"),
    BOT_CHAT("AI Copilot", Icons.Rounded.AutoAwesome, "Copilot", "Grok & Claude AI assistants"),
    PDF_VIEWER("PDF Document", Icons.Rounded.PictureAsPdf, "PDF", "Multi-page document reader"),
    IMAGE_VIEWER("Image Viewer", Icons.Rounded.Image, "Image", "Visual asset inspector & zoom"),
    MARKDOWN("Markdown & Docs", Icons.Rounded.Description, "Markdown", "Live Markdown preview & editor"),
    MEDIA_PLAYER("Media Player", Icons.Rounded.PlayCircle, "Media", "Audio & video playback engine"),
    DATA_INSPECTOR("JSON & Data", Icons.Rounded.DataObject, "Data", "Tree viewer, syntax & beautifier")
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
    EXTENSIONS(4, "Extensions", Icons.Rounded.Extension, 0xFFF59E0B),
    MCP(5, "MCP", Icons.Rounded.Hub, 0xFFEC4899),
    SETTINGS(6, "Settings", Icons.Rounded.Settings, 0xFF64748B)
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
    PROBLEMS("Problems", Icons.Rounded.Warning, "Diagnostics, lint errors, and extension problems"),
    CONSOLE("Debug Console", Icons.Rounded.Terminal, "Unified web console logs, IDE build & execution output"),
    DOM_INSPECT("DOM & Elements", Icons.Rounded.Code, "Visual outlines, inspect element, and tag tree"),
    EXTENSIONS("Side Panels", Icons.Rounded.Extension, "Dedicated interactive side panels for active extensions"),
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
