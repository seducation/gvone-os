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

enum class ProblemSeverity(val label: String, val colorHex: Long) {
    ERROR("Error", 0xFFEF4444),
    WARNING("Warning", 0xFFF59E0B),
    INFO("Info", 0xFF38BDF8)
}

data class ExtensionProblem(
    val id: String,
    val extensionId: String,
    val extensionName: String,
    val file: String,
    val line: Int,
    val column: Int = 1,
    val message: String,
    val ruleId: String? = null,
    val severity: ProblemSeverity = ProblemSeverity.ERROR,
    val quickFixAvailable: Boolean = true,
    val quickFixActionLabel: String = "Quick Fix"
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
    val category: String = "Formatter",
    val problems: List<ExtensionProblem> = emptyList(),
    val hasSidePanel: Boolean = true,
    val sidePanelTitle: String? = null
)

/**
 * Specialized task template for a domain-specific Bot Persona.
 */
data class BotSpecializedTask(
    val id: String,
    val title: String,
    val description: String,
    val promptTemplate: String,
    val targetAgent: String
)

/**
 * Grok-style Multi-Bot Personas for AI Coding Assistance.
 * Acting as high-level Advisory Copilots directing underlying CNS Actuator Agents.
 */
enum class GrokBotPersona(
    val id: String,
    val displayName: String,
    val tagline: String,
    val systemRole: String,
    val avatarColorHex: Long,
    val icon: ImageVector,
    val actuatorAgents: List<String> = emptyList(),
    val specializedTasks: List<BotSpecializedTask> = emptyList()
) {
    GROK_CODER(
        "grok_coder",
        "Grok Coder",
        "Witty, high-IQ algorithmic wizard",
        "You are Grok Coder, an unfiltered, hyper-intelligent, witty AI coding prodigy. You write optimal algorithms, point out inefficiencies directly, and explain code with clarity and humor.",
        0xFF00E5FF,
        Icons.Rounded.Terminal,
        actuatorAgents = listOf("CodingAgent", "FileAgent"),
        specializedTasks = listOf(
            BotSpecializedTask(
                id = "refactor_clean",
                title = "Refactor Clean Code",
                description = "Refactor file for readability and performance",
                promptTemplate = "Refactor the active file: optimize logic, simplify complex expressions, and apply modern Kotlin clean code principles.",
                targetAgent = "CodingAgent"
            ),
            BotSpecializedTask(
                id = "optimize_perf",
                title = "Optimize Algorithm",
                description = "Eliminate performance bottlenecks",
                promptTemplate = "Analyze algorithm performance in the active file: identify O(N^2) loops, allocations, and optimize for peak speed.",
                targetAgent = "CodingAgent"
            ),
            BotSpecializedTask(
                id = "scaffold_feature",
                title = "Scaffold Feature",
                description = "Generate boilerplate and components",
                promptTemplate = "Scaffold a new feature structure for this project with clean interfaces and idiomatic implementations.",
                targetAgent = "FileAgent"
            )
        )
    ),
    CODE_ARCHITECT(
        "code_architect",
        "Code Architect",
        "System design & clean patterns",
        "You are Code Architect, a senior principal software engineer. You specialize in system architecture, design patterns, modular code, and maintainable structure.",
        0xFFA855F7,
        Icons.Rounded.AccountTree,
        actuatorAgents = listOf("FileAgent", "WorkspaceAgent", "CodingAgent"),
        specializedTasks = listOf(
            BotSpecializedTask(
                id = "arch_audit",
                title = "Architecture Audit",
                description = "Evaluate modularity & dependency graph",
                promptTemplate = "Perform an architecture audit of this workspace: map module dependencies, detect circular coupling, and recommend boundary improvements.",
                targetAgent = "WorkspaceAgent"
            ),
            BotSpecializedTask(
                id = "plan_module",
                title = "Plan Module Design",
                description = "Design domain and data layer boundaries",
                promptTemplate = "Design a modular architecture plan for the target feature including state flows, repository boundaries, and domain models.",
                targetAgent = "FileAgent"
            ),
            BotSpecializedTask(
                id = "design_schema",
                title = "Design API & Schema",
                description = "Draft clean contracts and schemas",
                promptTemplate = "Draft robust data models, JSON schemas, and API contracts for this system.",
                targetAgent = "CodingAgent"
            )
        )
    ),
    BUG_HUNTER(
        "bug_hunter",
        "Bug Hunter",
        "Security auditor & edge-case sleuth",
        "You are Bug Hunter, an obsessive security researcher and bug auditor. You identify null pointer risks, race conditions, memory leaks, and input vulnerabilities.",
        0xFFEF4444,
        Icons.Rounded.Security,
        actuatorAgents = listOf("CodingAgent", "CommandAgent", "FileAgent"),
        specializedTasks = listOf(
            BotSpecializedTask(
                id = "sec_leak_audit",
                title = "Security & Leak Audit",
                description = "Check for memory leaks & security flaws",
                promptTemplate = "Audit the codebase for security risks, leaked keys/tokens, memory leaks in coroutines/listeners, and unsafe inputs.",
                targetAgent = "CodingAgent"
            ),
            BotSpecializedTask(
                id = "null_safety_scan",
                title = "Null & Race Scan",
                description = "Detect null pointer and threading races",
                promptTemplate = "Perform a strict null-safety and concurrency audit: identify potential NPEs, race conditions, and unhandled exception paths.",
                targetAgent = "CodingAgent"
            ),
            BotSpecializedTask(
                id = "lint_command_audit",
                title = "Run Static Code Audit",
                description = "Run static analysis & compiler checks",
                promptTemplate = "Run static code analysis and compiler checks on the workspace and report all warnings.",
                targetAgent = "CommandAgent"
            )
        )
    ),
    TEST_SPECIALIST(
        "test_specialist",
        "Test Specialist",
        "TDD master & clean refactorings",
        "You are Test Specialist, an expert in Test-Driven Development (TDD). You generate comprehensive unit test suites, mock cases, and elegant code refactorings.",
        0xFF10B981,
        Icons.Rounded.Verified,
        actuatorAgents = listOf("CodingAgent", "BrowserAgent", "SearchAgent"),
        specializedTasks = listOf(
            BotSpecializedTask(
                id = "gen_unit_tests",
                title = "Generate Unit Tests",
                description = "Create comprehensive test suites",
                promptTemplate = "Generate comprehensive JUnit/Mockk unit tests covering all happy paths and edge cases for the active component.",
                targetAgent = "CodingAgent"
            ),
            BotSpecializedTask(
                id = "gen_mock_fixtures",
                title = "Generate Mock Fixtures",
                description = "Produce sample data & stubs",
                promptTemplate = "Create mock test data fixtures and repository stubs for testing scenarios.",
                targetAgent = "FileAgent"
            ),
            BotSpecializedTask(
                id = "verify_endpoints",
                title = "Verify API Endpoints",
                description = "Test web APIs and responses",
                promptTemplate = "Verify web API endpoints, test response schemas, and validate status codes.",
                targetAgent = "BrowserAgent"
            )
        )
    ),
    WEB_SCRAPER(
        "web_scraper",
        "Web Scraper",
        "DOM extractor & website content analyzer",
        "You are Web Scraper Bot, an expert web crawling and DOM extraction agent. You extract JSON data, HTML structure, meta tags, and interactive elements from web pages.",
        0xFFF59E0B,
        Icons.Rounded.Language,
        actuatorAgents = listOf("BrowserAgent", "SearchAgent"),
        specializedTasks = listOf(
            BotSpecializedTask(
                id = "extract_dom",
                title = "Extract DOM Structure",
                description = "Parse page hierarchy and tables",
                promptTemplate = "Inspect active web page DOM: extract structured JSON from tables, article elements, and links.",
                targetAgent = "BrowserAgent"
            ),
            BotSpecializedTask(
                id = "crawl_search",
                title = "Deep Web Intelligence",
                description = "Gather intelligence from multiple sites",
                promptTemplate = "Crawl web search results, retrieve relevant documentation, and synthesize findings.",
                targetAgent = "SearchAgent"
            )
        )
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
