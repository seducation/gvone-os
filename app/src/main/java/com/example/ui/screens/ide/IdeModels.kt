package com.example.ui.screens.ide

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.files.GVONEFileItem
import com.example.data.files.GitFileStatus

/**
 * Represents an open file tab in the VS Code editor group.
 */
data class IdeTab(
    val id: String,
    val fileItem: GVONEFileItem,
    val content: String,
    val originalContent: String,
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1
) {
    val isDirty: Boolean
        get() = content != originalContent
}

/**
 * Activity Bar views on the left vertical rail of VS Code.
 */
enum class IdeActivityTab(
    val label: String,
    val icon: ImageVector,
    val description: String
) {
    EXPLORER("Explorer", Icons.Rounded.FolderOpen, "Files and project workspace tree"),
    SEARCH("Search", Icons.Rounded.Search, "Global search and replace in workspace"),
    SOURCE_CONTROL("Source Control", Icons.Rounded.Source, "Git staging, diffs, and commits"),
    EXTENSIONS("Extensions", Icons.Rounded.Extension, "Install and configure editor extensions"),
    AI_ASSISTANT("Grok Bots", Icons.Rounded.AutoAwesome, "Grok-style multi-bot AI Copilots")
}

/**
 * Bottom dockable panel tabs in VS Code.
 */
enum class IdeBottomTab(
    val label: String,
    val icon: ImageVector
) {
    TERMINAL("Terminal", Icons.Rounded.Terminal),
    OUTPUT("Output", Icons.Rounded.Notes),
    PROBLEMS("Problems", Icons.Rounded.WarningAmber),
    AI_REVIEW("Bot Chat", Icons.Rounded.Psychology)
}

/**
 * Executable command for the Quick Command Palette (Ctrl+P / >).
 */
data class IdeCommand(
    val id: String,
    val title: String,
    val category: String,
    val shortcut: String? = null,
    val icon: ImageVector = Icons.Rounded.PlayArrow,
    val action: () -> Unit
)

/**
 * Search hit across project files.
 */
data class IdeSearchResult(
    val file: GVONEFileItem,
    val lineNumber: Int,
    val lineContent: String,
    val matchStart: Int = 0,
    val matchEnd: Int = 0
)

/**
 * Diagnostic or lint issue shown in the Problems panel.
 */
enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

data class IdeDiagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val fileRelativePath: String,
    val line: Int,
    val column: Int
)

/**
 * Node in the tree view for the Explorer sidebar.
 */
data class IdeTreeNode(
    val item: GVONEFileItem,
    val level: Int,
    val isExpanded: Boolean = false,
    val children: List<IdeTreeNode> = emptyList(),
    val isLoaded: Boolean = false
)

/**
 * Extension for the IDE Extensions Manager (Prettier, ESLint, GitLens, etc.).
 */
data class IdeExtension(
    val id: String,
    val name: String,
    val version: String,
    val publisher: String,
    val description: String,
    val icon: ImageVector,
    val isEnabled: Boolean = true,
    val category: String = "Formatter"
)

/**
 * Grok-style Multi-Bot Personas for AI Coding Assistance.
 */
enum class GrokBotPersona(
    val id: String,
    val displayName: String,
    val tagline: String,
    val systemRole: String,
    val avatarColorHex: Long,
    val icon: ImageVector
) {
    GROK_CODER(
        "grok_coder",
        "Grok Coder",
        "Witty, high-IQ algorithmic wizard",
        "You are Grok Coder, an unfiltered, hyper-intelligent, witty AI coding prodigy. You write optimal algorithms, point out inefficiencies directly, and explain code with clarity and humor.",
        0xFF00E5FF,
        Icons.Rounded.Terminal
    ),
    CODE_ARCHITECT(
        "code_architect",
        "Code Architect",
        "System design & clean patterns",
        "You are Code Architect, a senior principal software engineer. You specialize in system architecture, design patterns, modular code, and maintainable structure.",
        0xFFA855F7,
        Icons.Rounded.AccountTree
    ),
    BUG_HUNTER(
        "bug_hunter",
        "Bug Hunter",
        "Security auditor & edge-case sleuth",
        "You are Bug Hunter, an obsessive security researcher and bug auditor. You identify null pointer risks, race conditions, memory leaks, and input vulnerabilities.",
        0xFFEF4444,
        Icons.Rounded.Security
    ),
    TEST_SPECIALIST(
        "test_specialist",
        "Test & Refactor",
        "TDD master & clean refactorings",
        "You are Test Specialist, an expert in Test-Driven Development (TDD). You generate comprehensive unit test suites, mock cases, and elegant code refactorings.",
        0xFF10B981,
        Icons.Rounded.Verified
    )
}

/**
 * Individual message in the multi-bot chat stream.
 */
data class BotChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val senderId: String, // "user" or GrokBotPersona.id
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val extractedCode: String? = null
)
