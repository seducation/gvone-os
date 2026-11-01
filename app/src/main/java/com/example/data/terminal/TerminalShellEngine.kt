package com.example.data.terminal

import android.content.Context
import android.webkit.CookieManager
import com.example.agent.sandbox.AgentPersona
import com.example.agent.sandbox.SandboxAgentEngine
import com.example.data.files.FileType
import com.example.data.files.GVONEFileItem
import com.example.data.files.GVONEFileSystem
import com.example.data.files.StorageLocation
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*

class TerminalShellEngine(
    private val context: Context,
    private val viewModel: BrowserViewModel,
    private val fileSystem: GVONEFileSystem,
    val agentEngine: SandboxAgentEngine
) {
    // Current working directory inside the sandbox filesystem
    private val _currentDirectory = kotlinx.coroutines.flow.MutableStateFlow("Documents")
    val currentDirectoryFlow: kotlinx.coroutines.flow.StateFlow<String> = _currentDirectory

    var currentDirectory: String
        get() = _currentDirectory.value
        private set(value) { _currentDirectory.value = value }

    val promptPath: String
        get() = if (currentDirectory.isBlank() || currentDirectory == "/") "~" else "~/$currentDirectory"

    /**
     * Resolves a relative or absolute path against the current sandbox directory.
     */
    fun resolvePath(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed == "/" || trimmed == "~" || trimmed.isBlank()) return ""
        val clean = if (trimmed.startsWith("~/")) trimmed.removePrefix("~/") else trimmed
        if (clean.startsWith("/")) return clean.removePrefix("/")
        return if (currentDirectory.isBlank() || currentDirectory == "/") clean else "$currentDirectory/$clean"
    }

    /**
     * Changes current directory in sandbox.
     */
    suspend fun changeDirectory(arg: String): TerminalLine {
        val trimmed = arg.trim()
        when {
            trimmed.isBlank() || trimmed == "~" -> {
                currentDirectory = ""
                return TerminalLine("Changed directory to root (~)", TerminalLineType.OUTPUT)
            }
            trimmed == ".." -> {
                if (currentDirectory.contains("/")) {
                    currentDirectory = currentDirectory.substringBeforeLast('/')
                } else {
                    currentDirectory = ""
                }
                return TerminalLine("Changed directory to $promptPath", TerminalLineType.OUTPUT)
            }
            trimmed == "/" -> {
                currentDirectory = ""
                return TerminalLine("Changed directory to /", TerminalLineType.OUTPUT)
            }
            else -> {
                val target = resolvePath(trimmed)
                val items = fileSystem.listFiles(StorageLocation.MY_FILES, target)
                // If it exists or is root/default folder, permit navigation
                currentDirectory = target
                return TerminalLine("Changed directory to $promptPath", TerminalLineType.SUCCESS)
            }
        }
    }

    /**
     * Lists files in sandbox directory.
     */
    suspend fun listFiles(arg: String): List<TerminalLine> {
        val target = if (arg.isNotBlank()) resolvePath(arg) else currentDirectory
        val items = fileSystem.listFiles(StorageLocation.MY_FILES, target)
        val lines = mutableListOf<TerminalLine>()

        val displayPath = if (target.isBlank()) "/" else "/$target"
        lines.add(TerminalLine("total ${items.size} items in $displayPath", TerminalLineType.SYSTEM))

        if (items.isEmpty()) {
            lines.add(TerminalLine("  (empty directory)", TerminalLineType.INFO))
            return lines
        }

        // Sort directories first, then alphabetically
        val sorted = items.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        val sdf = SimpleDateFormat("MMM dd HH:mm", Locale.US)

        for (item in sorted) {
            val dateStr = sdf.format(Date(item.lastModified))
            val perms = if (item.isDirectory) "drwxr-xr-x" else "-rw-r--r--"
            val sizeStr = if (item.isDirectory) "<DIR>".padEnd(8) else item.formattedSize.padEnd(8)
            val icon = if (item.isDirectory) "📁" else when (item.fileType) {
                FileType.MARKDOWN -> "📝"
                FileType.CODE -> "💻"
                FileType.JSON -> "⚙"
                FileType.IMAGE -> "🖼"
                FileType.PDF -> "📕"
                else -> "📄"
            }
            val type = if (item.isDirectory) TerminalLineType.INFO else when (item.fileType) {
                FileType.CODE -> TerminalLineType.SUCCESS
                FileType.MARKDOWN -> TerminalLineType.OUTPUT
                else -> TerminalLineType.OUTPUT
            }
            lines.add(
                TerminalLine(
                    text = "%s  %s  %s  %s %s".format(perms, sizeStr, dateStr, icon, item.name),
                    type = type
                )
            )
        }
        return lines
    }

    /**
     * Reads file content.
     */
    suspend fun catFile(filename: String): List<TerminalLine> {
        val path = resolvePath(filename)
        return try {
            val content = fileSystem.readFileContent(path)
            listOf(
                TerminalLine("--- $filename ---", TerminalLineType.SYSTEM),
                TerminalLine(content, TerminalLineType.OUTPUT)
            )
        } catch (e: Exception) {
            listOf(TerminalLine("cat: $filename: No such file or could not read", TerminalLineType.ERROR))
        }
    }

    /**
     * Creates an empty file in sandbox.
     */
    suspend fun touchFile(filename: String): TerminalLine {
        if (filename.isBlank()) return TerminalLine("touch: missing file operand", TerminalLineType.ERROR)
        val path = resolvePath(filename)
        val parent = if (path.contains("/")) path.substringBeforeLast('/') else ""
        val name = if (path.contains("/")) path.substringAfterLast('/') else path
        val created = fileSystem.createFile(parent, name, "")
        return if (created != null) {
            TerminalLine("Created empty file: $name in ${if (parent.isBlank()) "/" else "/$parent"}", TerminalLineType.SUCCESS)
        } else {
            TerminalLine("touch: cannot create file '$filename'", TerminalLineType.ERROR)
        }
    }

    /**
     * Creates a directory in sandbox.
     */
    suspend fun makeDirectory(dirname: String): TerminalLine {
        if (dirname.isBlank()) return TerminalLine("mkdir: missing operand", TerminalLineType.ERROR)
        val path = resolvePath(dirname)
        val parent = if (path.contains("/")) path.substringBeforeLast('/') else ""
        val name = if (path.contains("/")) path.substringAfterLast('/') else path
        val created = fileSystem.createFolder(parent, name)
        return if (created != null) {
            TerminalLine("Created directory: $name in ${if (parent.isBlank()) "/" else "/$parent"}", TerminalLineType.SUCCESS)
        } else {
            TerminalLine("mkdir: cannot create directory '$dirname'", TerminalLineType.ERROR)
        }
    }

    /**
     * Removes an item in sandbox.
     */
    suspend fun removeFile(arg: String): TerminalLine {
        val clean = arg.replace("-r", "").replace("-f", "").trim()
        if (clean.isBlank()) return TerminalLine("rm: missing operand", TerminalLineType.ERROR)
        val path = resolvePath(clean)
        val ok = fileSystem.deleteItem(path)
        return if (ok) {
            TerminalLine("Removed: $clean", TerminalLineType.SUCCESS)
        } else {
            TerminalLine("rm: cannot remove '$clean': No such file or directory", TerminalLineType.ERROR)
        }
    }

    /**
     * Writes or appends content to file.
     */
    suspend fun writeToFile(targetFile: String, content: String, append: Boolean): TerminalLine {
        val path = resolvePath(targetFile)
        val existing = if (append) {
            try { fileSystem.readFileContent(path) } catch (_: Exception) { "" }
        } else ""
        val newContent = if (append && existing.isNotBlank()) "$existing\n$content" else content
        val ok = fileSystem.writeFileContent(path, newContent)
        return if (ok) {
            TerminalLine("Wrote ${content.length} characters to $targetFile", TerminalLineType.SUCCESS)
        } else {
            TerminalLine("echo: failed to write to $targetFile", TerminalLineType.ERROR)
        }
    }

    /**
     * Generates a visual ASCII tree of the sandbox directory structure.
     */
    suspend fun generateTree(arg: String = ""): List<TerminalLine> {
        val target = if (arg.isNotBlank()) resolvePath(arg) else ""
        val lines = mutableListOf<TerminalLine>()
        lines.add(TerminalLine("gvone-fs://${if (target.isBlank()) "" else target}", TerminalLineType.SYSTEM))

        suspend fun buildBranch(dir: String, prefix: String) {
            val items = fileSystem.listFiles(StorageLocation.MY_FILES, dir).sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            items.forEachIndexed { idx, item ->
                val isLast = idx == items.size - 1
                val branch = if (isLast) "└── " else "├── "
                val icon = if (item.isDirectory) "📁" else "📄"
                val lineText = "$prefix$branch$icon ${item.name}"
                lines.add(TerminalLine(lineText, if (item.isDirectory) TerminalLineType.INFO else TerminalLineType.OUTPUT))
                if (item.isDirectory) {
                    val nextPrefix = prefix + (if (isLast) "    " else "│   ")
                    buildBranch(item.path, nextPrefix)
                }
            }
        }

        buildBranch(target, "")
        return lines
    }

    /**
     * Shows sandbox storage usage summary.
     */
    suspend fun getDiskUsage(): List<TerminalLine> {
        val summary = fileSystem.getStorageUsageSummary()
        return listOf(
            TerminalLine("── GVONE SANDBOX STORAGE USAGE ──", TerminalLineType.SYSTEM),
            TerminalLine("Total Files:     ${summary.totalFilesCount}", TerminalLineType.INFO),
            TerminalLine("Total Folders:   ${summary.totalFoldersCount}", TerminalLineType.INFO),
            TerminalLine("Disk Allocated:  ${summary.formattedTotalSize}", TerminalLineType.SUCCESS),
            TerminalLine("Documents:       ${summary.documentsSize}", TerminalLineType.OUTPUT),
            TerminalLine("Projects:        ${summary.projectsSize}", TerminalLineType.OUTPUT),
            TerminalLine("Downloads:       ${summary.downloadsSize}", TerminalLineType.OUTPUT)
        )
    }

    /**
     * Lists Tab Groups with IDs, colors, and tabs count.
     */
    fun listTabGroups(): List<TerminalLine> {
        val groups = viewModel.tabGroups.value
        val allTabs = viewModel.tabs.value
        val lines = mutableListOf<TerminalLine>()

        lines.add(TerminalLine("── TAB GROUPS (${groups.size} groups, ${allTabs.size} total tabs) ──", TerminalLineType.SYSTEM))
        if (groups.isEmpty()) {
            lines.add(TerminalLine("No tab groups exist. Use 'creategroup <name>' or '/sandbox' to create one.", TerminalLineType.INFO))
            return lines
        }

        groups.forEachIndexed { index, group ->
            val tabsInGroup = allTabs.filter { it.tabGroupId == group.id }
            val isActive = group.id == viewModel.activeGroupId.value
            val mark = if (isActive) "★ ACTIVE" else ""
            lines.add(
                TerminalLine(
                    "[%d] 📂 %s (%s) [%d tabs] %s".format(
                        index,
                        group.name,
                        group.colorHex ?: "#3B82F6",
                        tabsInGroup.size,
                        mark
                    ),
                    if (isActive) TerminalLineType.SUCCESS else TerminalLineType.INFO
                )
            )
            tabsInGroup.take(5).forEach { tab ->
                val isCurrent = tab.id == viewModel.currentTab.value?.id
                val tabMark = if (isCurrent) " ➔ " else "   - "
                lines.add(
                    TerminalLine(
                        "%s%s (%s)".format(tabMark, tab.title.take(30), tab.url),
                        if (isCurrent) TerminalLineType.SUCCESS else TerminalLineType.OUTPUT
                    )
                )
            }
            if (tabsInGroup.size > 5) {
                lines.add(TerminalLine("     ... and ${tabsInGroup.size - 5} more tabs", TerminalLineType.OUTPUT))
            }
        }
        val ungrouped = allTabs.filter { it.tabGroupId == null }
        if (ungrouped.isNotEmpty()) {
            lines.add(TerminalLine("Ungrouped Tabs: ${ungrouped.size} tabs", TerminalLineType.WARNING))
        }
        return lines
    }

    /**
     * Creates or focuses the Sandbox Tab Group.
     */
    suspend fun focusSandboxTabGroup(): List<TerminalLine> = withContext(Dispatchers.Main) {
        val groups = viewModel.tabGroups.value
        val existing = groups.find { it.name.equals("Sandbox", ignoreCase = true) }
        val groupId = existing?.id ?: viewModel.createTabGroup("Sandbox", colorHex = "#10B981")
        val currentTab = viewModel.currentTab.value
        if (currentTab != null) {
            viewModel.moveTabToGroup(currentTab.id, groupId)
        }
        agentEngine.activeSandboxGroupId = groupId
        listOf(
            TerminalLine("[SANDBOX] Tab Group focused: \"Sandbox\" (ID: ${groupId.take(8)}...)", TerminalLineType.SUCCESS),
            TerminalLine("[SANDBOX] Current tab linked into Sandbox group. Sandbox filesystem directory: ~$currentDirectory", TerminalLineType.INFO)
        )
    }

    /**
     * Inspects cookies for the active domain.
     */
    fun inspectCookies(): List<TerminalLine> {
        val url = viewModel.currentTab.value?.url.orEmpty()
        if (url.isBlank() || url.startsWith("gvone://")) {
            return listOf(TerminalLine("No cookies: Current page is an internal start page.", TerminalLineType.INFO))
        }
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)
        return if (cookies.isNullOrBlank()) {
            listOf(TerminalLine("No cookies found for $url", TerminalLineType.INFO))
        } else {
            val pairs = cookies.split(";").map { it.trim() }
            val lines = mutableListOf<TerminalLine>()
            lines.add(TerminalLine("── COOKIES FOR $url (${pairs.size} entries) ──", TerminalLineType.SYSTEM))
            pairs.take(15).forEach { c ->
                lines.add(TerminalLine("  ● $c", TerminalLineType.OUTPUT))
            }
            if (pairs.size > 15) {
                lines.add(TerminalLine("  ... and ${pairs.size - 15} more cookies", TerminalLineType.OUTPUT))
            }
            lines
        }
    }
}
