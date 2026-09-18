package com.example.data.terminal

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.example.agent.cns.CentralNervousSystem
import com.example.agent.sandbox.SandboxAgentEngine
import com.example.data.files.GVONEFileSystem
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalCommandExecutor {
    private var context: Context? = null
    private var fileSystem: GVONEFileSystem? = null
    private var scope: CoroutineScope? = null
    private var viewModel: BrowserViewModel? = null
    private var shellEngine: TerminalShellEngine? = null
    private var agentEngine: SandboxAgentEngine? = null

    constructor(
        context: Context,
        fileSystem: GVONEFileSystem,
        scope: CoroutineScope
    ) {
        this.context = context
        this.fileSystem = fileSystem
        this.scope = scope
    }

    constructor(
        viewModel: BrowserViewModel,
        shellEngine: TerminalShellEngine,
        agentEngine: SandboxAgentEngine
    ) {
        this.viewModel = viewModel
        this.shellEngine = shellEngine
        this.agentEngine = agentEngine
        this.context = viewModel.getApplication()
        this.fileSystem = viewModel.fileSystem
        this.scope = viewModel.viewModelScope
    }

    fun isCommand(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        return trimmed.startsWith("/") ||
                trimmed.startsWith("help", ignoreCase = true) ||
                trimmed.startsWith("clear", ignoreCase = true) ||
                trimmed.startsWith("ls", ignoreCase = true) ||
                trimmed.startsWith("cd ", ignoreCase = true) ||
                trimmed.startsWith("cat ", ignoreCase = true) ||
                trimmed.startsWith("touch ", ignoreCase = true) ||
                trimmed.startsWith("mkdir ", ignoreCase = true) ||
                trimmed.startsWith("agent ", ignoreCase = true) ||
                trimmed.startsWith("cns ", ignoreCase = true) ||
                trimmed.startsWith("shell ", ignoreCase = true) ||
                trimmed.startsWith("tor ", ignoreCase = true) ||
                trimmed.startsWith("bridge ", ignoreCase = true) ||
                trimmed.startsWith("cookies", ignoreCase = true) ||
                trimmed.startsWith("groups", ignoreCase = true)
    }

    fun executeCommand(
        rawInput: String,
        origin: CommandOrigin = CommandOrigin.TERMINAL,
        onOpenAgentDashboard: (() -> Unit)? = null
    ) {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return

        val vm = viewModel
        if (vm != null) {
            val append = { line: TerminalLine -> vm.appendTerminalLine(line.text, line.type) }
            val clear = { vm.clearTerminalLines() }
            execute(trimmed, append, clear, origin, onOpenAgentDashboard)
        } else {
            execute(trimmed, { _ -> }, {}, origin, onOpenAgentDashboard)
        }
    }

    fun execute(
        input: String,
        appendLine: (TerminalLine) -> Unit,
        clearLines: () -> Unit,
        origin: CommandOrigin = CommandOrigin.TERMINAL,
        onOpenAgentDashboard: (() -> Unit)? = null
    ) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        val promptPrefix = if (origin == CommandOrigin.ADDRESS_BAR) "gvone@addressbar:~$ " else "gvone@terminal:~$ "
        appendLine(TerminalLine(text = "$promptPrefix$trimmed", type = TerminalLineType.COMMAND))

        val parts = trimmed.split(Regex("\\s+"))
        val command = parts.firstOrNull()?.lowercase() ?: ""
        val args = parts.drop(1)

        val activeScope = scope ?: CoroutineScope(Dispatchers.Main)
        val fs = fileSystem

        when {
            command == "help" || command == "/help" -> {
                appendLine(
                    TerminalLine(
                        text = """
                            GVONE Multimodal Terminal & Unified Command Shell:
                              /agent <goal>                       Orchestrate autonomous agent mission
                              /agent ui | /cns | /dashboard       Open Central Nervous System Agent Dashboard
                              /receive <url|path|data> [filename] Receive, save, and display image preview
                              /download <url> [filename]          Download and render binary image file
                              /sandbox                            Focus Sandbox Tab Group and link workspace
                              ls [path]                           List local GVONE sandboxed files
                              cat <file>                          Inspect file content
                              touch <file>                        Create new empty file
                              mkdir <dir>                         Create directory
                              cd <dir>                            Change working directory
                              cookies                             Inspect active domain session cookies
                              groups                              List active Tab Groups and tabs
                              clear                               Clear terminal screen
                              help                                Display this help menu
                        """.trimIndent(),
                        type = TerminalLineType.INFO
                    )
                )
            }

            command == "clear" || command == "/clear" -> {
                clearLines()
            }

            command == "/agent" || command == "agent" -> {
                val goal = args.joinToString(" ").trim()
                if (goal.isEmpty() || goal.equals("ui", ignoreCase = true) || goal.equals("dashboard", ignoreCase = true)) {
                    appendLine(TerminalLine(text = "[CNS] Opening Central Nervous System Agent Dashboard...", type = TerminalLineType.SUCCESS))
                    if (onOpenAgentDashboard != null) {
                        onOpenAgentDashboard.invoke()
                    } else {
                        viewModel?.openAgentDashboard()
                    }
                } else {
                    appendLine(TerminalLine(text = "[AGENT MISSION] Initializing agent goal: \"$goal\"...", type = TerminalLineType.SYSTEM))
                    activeScope.launch(Dispatchers.Main) {
                        CentralNervousSystem.global.orchestrateGoal(goal)
                        if (onOpenAgentDashboard != null) {
                            onOpenAgentDashboard.invoke()
                        } else {
                            viewModel?.openAgentDashboard()
                        }
                    }
                }
            }

            command == "/cns" || command == "/dashboard" -> {
                appendLine(TerminalLine(text = "[CNS] Opening Central Nervous System Agent Dashboard...", type = TerminalLineType.SUCCESS))
                if (onOpenAgentDashboard != null) {
                    onOpenAgentDashboard.invoke()
                } else {
                    viewModel?.openAgentDashboard()
                }
            }

            command == "/sandbox" || command == "sandbox" -> {
                activeScope.launch(Dispatchers.Main) {
                    shellEngine?.focusSandboxTabGroup()?.forEach { appendLine(it) }
                }
            }

            command == "groups" -> {
                shellEngine?.listTabGroups()?.forEach { appendLine(it) }
            }

            command == "cookies" -> {
                shellEngine?.inspectCookies()?.forEach { appendLine(it) }
            }

            command == "ls" -> {
                if (shellEngine != null) {
                    val relPath = args.firstOrNull() ?: ""
                    activeScope.launch(Dispatchers.IO) {
                        val lines = shellEngine?.listFiles(relPath) ?: emptyList()
                        withContext(Dispatchers.Main) {
                            lines.forEach { appendLine(it) }
                        }
                    }
                } else if (fs != null) {
                    val relPath = args.firstOrNull() ?: ""
                    val files = fs.listFiles(relPath)
                    if (files.isEmpty()) {
                        appendLine(TerminalLine(text = "Directory is empty.", type = TerminalLineType.SYSTEM))
                    } else {
                        val formatted = files.joinToString("\n") { file ->
                            val prefix = if (file.isDirectory) "[DIR] " else "      "
                            "$prefix${file.name}"
                        }
                        appendLine(TerminalLine(text = formatted, type = TerminalLineType.OUTPUT))
                    }
                }
            }

            command == "cd" -> {
                val targetDir = args.firstOrNull() ?: ""
                activeScope.launch(Dispatchers.IO) {
                    val result = shellEngine?.changeDirectory(targetDir)
                    if (result != null) {
                        withContext(Dispatchers.Main) { appendLine(result) }
                    }
                }
            }

            command == "cat" -> {
                val file = args.firstOrNull() ?: ""
                activeScope.launch(Dispatchers.IO) {
                    val results = shellEngine?.catFile(file) ?: emptyList()
                    withContext(Dispatchers.Main) {
                        results.forEach { appendLine(it) }
                    }
                }
            }

            command == "touch" -> {
                val file = args.firstOrNull() ?: ""
                activeScope.launch(Dispatchers.IO) {
                    val result = shellEngine?.touchFile(file)
                    if (result != null) {
                        withContext(Dispatchers.Main) { appendLine(result) }
                    }
                }
            }

            command == "mkdir" -> {
                val dir = args.firstOrNull() ?: ""
                activeScope.launch(Dispatchers.IO) {
                    val result = shellEngine?.makeDirectory(dir)
                    if (result != null) {
                        withContext(Dispatchers.Main) { appendLine(result) }
                    }
                }
            }

            command == "/receive" || command == "/download" || command == "receive" || command == "download" -> {
                val source = args.firstOrNull()
                if (source == null) {
                    appendLine(
                        TerminalLine(
                            text = "Usage: /receive <url|path|data_uri> [custom_filename]",
                            type = TerminalLineType.WARNING
                        )
                    )
                    return
                }

                val customName = if (args.size > 1) args.subList(1, args.size).joinToString("_") else null

                appendLine(
                    TerminalLine(
                        text = "[System] Downloading and processing image payload from: $source ...",
                        type = TerminalLineType.INFO
                    )
                )

                if (fs != null) {
                    activeScope.launch(Dispatchers.IO) {
                        val result = fs.receiveAndSaveImage(
                            sourceUriOrPathOrUrl = source,
                            customFileName = customName
                        )

                        withContext(Dispatchers.Main) {
                            if (result.success) {
                                appendLine(
                                    TerminalLine(
                                        text = "Successfully saved ${result.name} (${result.formattedSize()})",
                                        type = TerminalLineType.IMAGE_PREVIEW,
                                        imageUri = result.fileUri,
                                        fileUri = result.fileUri,
                                        fileName = result.name,
                                        fileMimeType = result.mimeType,
                                        fileSize = result.sizeBytes,
                                        imageWidth = result.width,
                                        imageHeight = result.height
                                    )
                                )
                                appendLine(
                                    TerminalLine(
                                        text = result.toStructuredJson(),
                                        type = TerminalLineType.SYSTEM
                                    )
                                )
                            } else {
                                appendLine(
                                    TerminalLine(
                                        text = "[Error] Failed to receive image: ${result.error}",
                                        type = TerminalLineType.ERROR
                                    )
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // If user enters an image URL directly without command prefix
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    val ext = trimmed.substringAfterLast('.', "").lowercase()
                    if (ext in listOf("png", "jpg", "jpeg", "webp", "gif", "svg") && fs != null) {
                        appendLine(TerminalLine(text = "[Auto-Detect] Image URL detected. Receiving image payload...", type = TerminalLineType.INFO))
                        activeScope.launch(Dispatchers.IO) {
                            val result = fs.receiveAndSaveImage(trimmed)
                            withContext(Dispatchers.Main) {
                                if (result.success) {
                                    appendLine(
                                        TerminalLine(
                                            text = "Saved ${result.name} to ~/Downloads/",
                                            type = TerminalLineType.IMAGE_PREVIEW,
                                            imageUri = result.fileUri,
                                            fileUri = result.fileUri,
                                            fileName = result.name,
                                            fileMimeType = result.mimeType,
                                            fileSize = result.sizeBytes,
                                            imageWidth = result.width,
                                            imageHeight = result.height
                                        )
                                    )
                                } else {
                                    appendLine(TerminalLine(text = "Failed to load image URL: ${result.error}", type = TerminalLineType.ERROR))
                                }
                            }
                        }
                        return
                    }
                }

                appendLine(
                    TerminalLine(
                        text = "gvone: command not found: $command. Type 'help' for available commands.",
                        type = TerminalLineType.ERROR
                    )
                )
            }
        }
    }
}
