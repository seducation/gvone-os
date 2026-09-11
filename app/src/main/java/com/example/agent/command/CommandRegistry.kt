package com.example.agent.command

import com.example.agent.cns.CentralNervousSystem
import com.example.agent.config.SystemConfigManager
import com.example.agent.core.AgentStatus
import com.example.agent.core.CommandError
import com.example.agent.memory.ContextRouter
import com.example.agent.memory.MemoryScope
import com.example.agent.memory.MemoryStore
import com.example.agent.nodal.NodalEngine
import com.example.agent.registry.AgentRegistry
import com.example.agent.runtime.ExecutionType
import com.example.agent.runtime.InteractionType
import com.example.agent.runtime.RuntimeStateManager
import com.example.agent.task.TaskManager
import com.example.data.terminal.ConversationTreeManager
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Central Command Registry for GVONE OS.
 * Manages command registration, order-independent modifier parsing, execution dispatch,
 * and dynamic nodal integration.
 */
class CommandRegistry(
    private val runtimeState: RuntimeStateManager = RuntimeStateManager.global,
    private val taskManager: TaskManager = TaskManager.global,
    private val memoryStore: MemoryStore = MemoryStore.global,
    private val configManager: SystemConfigManager = SystemConfigManager.global,
    private val contextRouter: ContextRouter = ContextRouter.global,
    private val agentRegistry: AgentRegistry = AgentRegistry.global,
    private val nodalEngine: NodalEngine = NodalEngine.global,
    private val cns: CentralNervousSystem = CentralNervousSystem.global
) {
    private val commands = ConcurrentHashMap<String, CommandDefinition>()
    private val aliasMap = ConcurrentHashMap<String, String>()

    private val _registeredCommands = MutableStateFlow<List<CommandDefinition>>(emptyList())
    val registeredCommands: StateFlow<List<CommandDefinition>> = _registeredCommands.asStateFlow()

    init {
        registerBuiltInCommands()
    }

    fun register(definition: CommandDefinition) {
        val canonical = normalizeCommand(definition.command)
        commands[canonical] = definition
        definition.aliases.forEach { alias ->
            aliasMap[normalizeCommand(alias)] = canonical
        }
        _registeredCommands.value = commands.values.toList().sortedBy { it.command }
    }

    fun unregister(commandName: String) {
        val canonical = normalizeCommand(commandName)
        val def = commands.remove(canonical)
        def?.aliases?.forEach { aliasMap.remove(normalizeCommand(it)) }
        _registeredCommands.value = commands.values.toList().sortedBy { it.command }
    }

    fun getCommand(name: String): CommandDefinition? {
        val canonical = normalizeCommand(name)
        val target = aliasMap[canonical] ?: canonical
        return commands[target]
    }

    /**
     * Parses a raw terminal input string into a structured CommandInvocation.
     * Supports order-independent modifier parsing (e.g. /voice /on /agent, /agent /voice, /voice /agent /on).
     */
    fun parse(input: String): CommandInvocation? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val tokens = trimmed.split("\\s+".toRegex())
        if (tokens.isEmpty()) return null

        val firstToken = tokens.first()
        val normalizedFirst = normalizeCommand(firstToken)

        // Check if first token is a known command or starts with slash
        val isSlash = firstToken.startsWith("/")
        val resolvedCommand = getCommand(firstToken)?.command ?: if (isSlash) normalizedFirst else null

        if (resolvedCommand == null) {
            return null
        }

        val modifiers = mutableSetOf<String>()
        val positionalArgs = mutableListOf<String>()
        var argIndex = 1

        // Consume order-independent modifiers like /on, /off, /agent, /voice, /status, /list, -v, etc.
        while (argIndex < tokens.size) {
            val token = tokens[argIndex]
            if (token.startsWith("/") && token.length > 1) {
                modifiers.add(token.substring(1).lowercase())
                argIndex++
            } else if (token.startsWith("-") && token.length > 1) {
                modifiers.add(token.removePrefix("-").lowercase())
                argIndex++
            } else {
                break
            }
        }

        // Remaining tokens are arguments or query
        while (argIndex < tokens.size) {
            positionalArgs.add(tokens[argIndex])
            argIndex++
        }

        val queryArg = positionalArgs.joinToString(" ")

        return CommandInvocation(
            rawInput = trimmed,
            command = resolvedCommand,
            modifiers = modifiers,
            positionalArgs = positionalArgs,
            queryArg = queryArg
        )
    }

    /**
     * Executes a parsed invocation against registered handlers.
     */
    suspend fun execute(invocation: CommandInvocation): CommandResult {
        val definition = getCommand(invocation.command)
            ?: return CommandResult.error("Command '${invocation.command}' is not recognized. Type /help for available commands.")

        return try {
            definition.handler(invocation)
        } catch (e: Exception) {
            val err = if (e is CommandError) e else CommandError(invocation.command, e.message ?: e.toString())
            CommandResult.error("[COMMAND ERROR] ${err.messageText}\n-> Resolution: ${err.actionableResolution}")
        }
    }

    /**
     * Parse and execute raw input in one step.
     */
    suspend fun executeRaw(input: String): CommandResult? {
        val invocation = parse(input) ?: return null
        return execute(invocation)
    }

    private fun normalizeCommand(cmd: String): String {
        val trimmed = cmd.trim().lowercase()
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    private fun registerBuiltInCommands() {
        // 1. /voice
        register(
            CommandDefinition(
                command = "/voice",
                description = "Manage speech interaction, persistent voice mode (/on, /off), and voice-driven agent goals (/agent)",
                aliases = listOf("voice"),
                agentBinding = "VoiceAgent",
                executionMode = ExecutionType.CHAT,
                handler = { inv ->
                    handleVoiceCommand(inv)
                }
            )
        )

        // 2. /agent
        register(
            CommandDefinition(
                command = "/agent",
                description = "Execute autonomous multi-step agent goals, toggle agent mode (/on, /off), or inspect status",
                aliases = listOf("agent", "/agentic"),
                agentBinding = "CommandAgent",
                executionMode = ExecutionType.AGENT,
                handler = { inv ->
                    handleAgentCommand(inv)
                }
            )
        )

        // 3. /task
        register(
            CommandDefinition(
                command = "/task",
                description = "First-class task management: /task list, /task status <id>, /task pause, /task resume, /task stop",
                aliases = listOf("task", "/tasks"),
                handler = { inv ->
                    handleTaskCommand(inv)
                }
            )
        )

        // 4. /memory
        register(
            CommandDefinition(
                command = "/memory",
                description = "Scoped memory management: /memory list, /memory get <key>, /memory set <k> <v>, /memory search <q>, /memory clear",
                aliases = listOf("memory", "/mem"),
                handler = { inv ->
                    handleMemoryCommand(inv)
                }
            )
        )

        // 5. /context
        register(
            CommandDefinition(
                command = "/context",
                description = "Inspect and manage context isolation boundaries: /context show, /context reset",
                aliases = listOf("context", "/ctx"),
                handler = { inv ->
                    handleContextCommand(inv)
                }
            )
        )

        // 6. /config
        register(
            CommandDefinition(
                command = "/config",
                description = "Inspect and update system configuration: /config list, /config get <key>, /config set <key> <val>",
                aliases = listOf("config", "/cfg", "/settings"),
                handler = { inv ->
                    handleConfigCommand(inv)
                }
            )
        )

        // 7. /command or /commands
        register(
            CommandDefinition(
                command = "/command",
                description = "List all registered commands, syntax, and options",
                aliases = listOf("/commands", "commands", "command"),
                handler = {
                    val report = buildString {
                        appendLine("=== GVONE OS COMMAND REGISTRY ===")
                        commands.values.sortedBy { it.command }.forEach { cmd ->
                            appendLine("  ${cmd.command.padEnd(16)} : ${cmd.description}")
                        }
                    }.trim()
                    CommandResult.info(report)
                }
            )
        )

        // 8. /help
        register(
            CommandDefinition(
                command = "/help",
                description = "Display full manual and usage guide",
                aliases = listOf("help", "/?", "?"),
                handler = {
                    val manual = buildString {
                        appendLine("=== GVONE OS UNIFIED MANUAL ===")
                        appendLine("Command Syntax: <command> [modifiers...] [arguments...]")
                        appendLine("Supported Core Commands:")
                        appendLine("  /voice [/on|/off|/agent] [goal] - Voice interaction, persistent loop, or voice agent")
                        appendLine("  /agent [/on|/off|/voice] [goal] - Autonomous goal execution with tools & observations")
                        appendLine("  /task [list|status|pause|resume|stop|cancel] - First-class task lifecycle management")
                        appendLine("  /memory [list|get|set|search|clear] - Scoped, persistent key-value memory layer")
                        appendLine("  /context [show|reset]           - Inspect or isolate session/task execution context")
                        appendLine("  /config [list|get|set]          - Dynamic system & model configuration parameters")
                        appendLine("  /status                         - Global runtime status and active agent inspection")
                        appendLine("  /stop, /cancel                  - Terminate running task or voice loop")
                        appendLine("  /clear, /cls                    - Clear terminal output buffer")
                        appendLine("  /history                        - View recently executed command history")
                        appendLine("Order-independent modifiers are supported, e.g.: /voice /on /agent <goal> or /agent /voice")
                    }.trim()
                    CommandResult.info(manual)
                }
            )
        )

        // 9. /status
        register(
            CommandDefinition(
                command = "/status",
                description = "Inspect system mode, active task, and health of all registered agents",
                aliases = listOf("status"),
                handler = {
                    val mode = runtimeState.runtimeMode.value
                    val activeTask = taskManager.getActiveTask()
                    val agentNames = agentRegistry.registeredAgentNames.value

                    val statusMsg = buildString {
                        appendLine("=== GVONE OS RUNTIME STATUS ===")
                        appendLine("Interaction Mode: ${mode.interaction.displayName} (VoiceActive=${mode.isVoiceActive})")
                        appendLine("Execution Mode:   ${mode.execution.displayName} (Priority=${mode.priority.displayName})")
                        appendLine("Debug Logging:    ${if (mode.isDebugEnabled) "ENABLED" else "DISABLED"}")
                        appendLine("Active Task:      ${activeTask?.let { "'${it.goal}' [${it.status.displayName}] (${(it.progress * 100).toInt()}%)" } ?: "None"}")
                        appendLine("Registered Agents (${agentNames.size}): ${agentNames.joinToString(", ")}")
                        appendLine("Registered Tasks: ${taskManager.tasks.value.size}")
                    }.trim()
                    CommandResult.info(statusMsg)
                }
            )
        )

        // 10. /stop and /cancel
        register(
            CommandDefinition(
                command = "/stop",
                description = "Cancel active task, stop voice continuous loop, and return to idle chat",
                aliases = listOf("/cancel", "stop", "cancel"),
                handler = {
                    val hadActiveTask = taskManager.getActiveTask() != null
                    taskManager.cancelTask()
                    runtimeState.resetToTextChat()
                    val msg = if (hadActiveTask) {
                        "[STOP] Active task cancelled. Returned to default text chat."
                    } else {
                        "[STOP] System reset to default text chat."
                    }
                    CommandResult.success(msg)
                }
            )
        )

        // 11. /clear and /cls
        register(
            CommandDefinition(
                command = "/clear",
                description = "Clear terminal output buffer",
                aliases = listOf("/cls", "clear", "cls"),
                handler = {
                    CommandResult(
                        status = CommandStatus.SUCCESS,
                        message = "CLEAR_TERMINAL",
                        terminalLines = emptyList(),
                        openTerminal = true
                    )
                }
            )
        )

        // 12. /reset
        register(
            CommandDefinition(
                command = "/reset",
                description = "Reset runtime state, context router, and return to default text chat",
                aliases = listOf("reset"),
                handler = {
                    runtimeState.resetToTextChat()
                    taskManager.cancelTask()
                    contextRouter.resetAllScopes()
                    CommandResult.success("GVONE OS state and context boundaries have been reset to factory defaults.")
                }
            )
        )
    }

    private suspend fun handleVoiceCommand(inv: CommandInvocation): CommandResult {
        val hasOn = inv.isPersistentOn
        val hasOff = inv.isPersistentOff
        val hasAgent = inv.isAgenticRequested

        // 1. /voice /off -> disable persistent voice mode
        if (hasOff) {
            runtimeState.setTextMode()
            return CommandResult.success("[VOICE] Persistent voice mode DISABLED. Returned to text chat.")
        }

        // 2. /voice /on /agent <goal> or /voice /agent <goal> -> Voice-driven agentic execution
        if (hasAgent) {
            val goal = inv.queryArg.trim()
            if (hasOn) {
                runtimeState.activateVoiceAgentCompound(goal.ifBlank { null })
            } else {
                runtimeState.activateVoiceAgentCompound(goal.ifBlank { null })
            }

            return if (goal.isNotBlank()) {
                val task = taskManager.createTask(goal, "VoiceAgent")
                val orchRes = cns.orchestrateGoal(goal)
                if (orchRes.success) {
                    taskManager.completeTask(task.taskId, orchRes.synthesis)
                    CommandResult.success(
                        "[VOICE AGENT COMPLETED]\n${orchRes.synthesis}\n\nTask '$goal' has completed. You can continue conversation or start another task."
                    )
                } else {
                    taskManager.failTask(task.taskId, orchRes.synthesis)
                    CommandResult.error("[VOICE AGENT FAILED] ${orchRes.synthesis}")
                }
            } else {
                CommandResult.info(
                    "[VOICE AGENT ACTIVE] Voice-driven agentic mode engaged. Microphone listening for your goal."
                )
            }
        }

        // 3. /voice /on -> enable persistent voice mode (chat only, no agent task)
        if (hasOn) {
            runtimeState.activateVoiceOnly()
            return CommandResult.success(
                "[VOICE] Persistent voice mode ENABLED (Interaction: VOICE, Execution: CHAT).\n" +
                        "Continuous speech listening is active. Speak freely; this mode does not launch background agent tasks."
            )
        }

        // 4. /voice (without modifiers)
        val query = inv.queryArg.trim()
        if (query.isNotBlank()) {
            // Conversational voice interaction query
            runtimeState.activateVoiceOnly()
            return CommandResult.info(
                "[VOICE INTERACTION] Listening & processing: \"$query\"\n" +
                        "-> Note: Use '/voice /on' for persistent mode, or '/voice /agent <goal>' for autonomous agent tasks."
            )
        }

        // Default /voice toggle
        runtimeState.activateVoiceOnly()
        return CommandResult.info(
            "[VOICE] Voice conversation mode active.\n" +
                    "Modifiers:\n" +
                    "  /voice /on       - Enable persistent voice mode\n" +
                    "  /voice /off      - Disable persistent voice mode\n" +
                    "  /voice /agent <g>- Voice-driven agentic execution"
        )
    }

    private suspend fun handleAgentCommand(inv: CommandInvocation): CommandResult {
        val hasVoice = inv.hasModifier("voice")
        val hasOn = inv.isPersistentOn
        val hasOff = inv.isPersistentOff
        val hasStatus = inv.hasModifier("status")
        val goal = inv.queryArg.trim()

        if (hasOff) {
            runtimeState.toggleAgent(false)
            return CommandResult.success("[AGENT] Agent mode toggled OFF. Returned to direct chat.")
        }

        if (hasStatus) {
            val status = cns.getAllAgents().joinToString("\n") {
                "• ${it.identity()}: ${it.status().displayName} (${it.capabilities().joinToString { c -> c.name }})"
            }
            return CommandResult.info("Registered Agents & Status:\n$status")
        }

        // /agent /voice <goal> -> Agent-first execution with voice telemetry
        if (hasVoice) {
            runtimeState.activateAgentVoiceCompound(goal.ifBlank { null })
            if (goal.isNotBlank()) {
                val task = taskManager.createTask(goal, "CommandAgent")
                val orchRes = cns.orchestrateGoal(goal)
                return if (orchRes.success) {
                    taskManager.completeTask(task.taskId, orchRes.synthesis)
                    CommandResult.success("[AGENT VOICE COMPLETED]\n${orchRes.synthesis}")
                } else {
                    taskManager.failTask(task.taskId, orchRes.synthesis)
                    CommandResult.error("[AGENT VOICE FAILED] ${orchRes.synthesis}")
                }
            } else {
                return CommandResult.info("[AGENT VOICE] Agent-first mode ready with voice telemetry. Run '/agent /voice <goal>'")
            }
        }

        // Standard /agent <goal>
        if (goal.isNotBlank()) {
            runtimeState.activateAgentOnly(goal)
            val task = taskManager.createTask(goal, "CommandAgent")
            val orchRes = cns.orchestrateGoal(goal)
            return if (orchRes.success) {
                taskManager.completeTask(task.taskId, orchRes.synthesis)
                CommandResult.success("[AGENT COMPLETED]\n${orchRes.synthesis}")
            } else {
                taskManager.failTask(task.taskId, orchRes.synthesis)
                CommandResult.error("[AGENT FAILED] ${orchRes.synthesis}")
            }
        }

        runtimeState.activateAgentOnly(null)
        return CommandResult.info(
            "[AGENT MODE] Autonomous Agent mode active.\n" +
                    "Usage:\n" +
                    "  /agent <goal>         - Execute autonomous goal\n" +
                    "  /agent /voice <goal>  - Agent execution with voice telemetry\n" +
                    "  /agent /status        - View active agents and capabilities\n" +
                    "  /agent /off           - Return to chat mode"
        )
    }

    private fun handleTaskCommand(inv: CommandInvocation): CommandResult {
        val sub = inv.positionalArgs.firstOrNull()?.lowercase() ?: "list"
        val targetId = inv.positionalArgs.getOrNull(1)

        return when (sub) {
            "list" -> CommandResult.info(taskManager.formatTasksReport())
            "status" -> {
                val task = if (targetId != null) taskManager.getTask(targetId) else taskManager.getActiveTask()
                if (task != null) {
                    val report = buildString {
                        appendLine("=== TASK STATUS: ${task.taskId} ===")
                        appendLine("Goal:     ${task.goal}")
                        appendLine("Status:   ${task.status.displayName}")
                        appendLine("Agent:    ${task.assignedAgent}")
                        appendLine("Progress: ${(task.progress * 100).toInt()}%")
                        appendLine("Steps (${task.steps.size}):")
                        task.steps.forEach { s ->
                            appendLine("  [${s.status.name}] Step ${s.stepNumber}: ${s.description} (${s.toolOrAgent})")
                        }
                        if (task.result != null) appendLine("Result: ${task.result}")
                        if (task.error != null) appendLine("Error: ${task.error}")
                    }.trim()
                    CommandResult.info(report)
                } else {
                    CommandResult.error("Task not found. Run '/task list' to see registered tasks.")
                }
            }
            "pause" -> {
                val ok = taskManager.pauseTask(targetId)
                if (ok) CommandResult.success("Task paused.") else CommandResult.error("No active running task to pause.")
            }
            "resume" -> {
                val ok = taskManager.resumeTask(targetId)
                if (ok) CommandResult.success("Task resumed.") else CommandResult.error("No paused task to resume.")
            }
            "stop", "cancel" -> {
                val ok = taskManager.cancelTask(targetId)
                if (ok) CommandResult.success("Task cancelled.") else CommandResult.error("No active task to cancel.")
            }
            else -> CommandResult.info("Usage: /task [list | status <id> | pause | resume | stop | cancel]")
        }
    }

    private fun handleMemoryCommand(inv: CommandInvocation): CommandResult {
        val sub = inv.positionalArgs.firstOrNull()?.lowercase() ?: "list"
        return when (sub) {
            "list" -> CommandResult.info(memoryStore.formatReport())
            "get" -> {
                val key = inv.positionalArgs.getOrNull(1)
                if (key == null) {
                    CommandResult.error("Usage: /memory get <key>")
                } else {
                    val entry = memoryStore.getEntry(key)
                    if (entry != null) {
                        CommandResult.info("[MEMORY] ${entry.key} = \"${entry.value}\" (Scope: ${entry.scope.name})")
                    } else {
                        CommandResult.error("Memory key '$key' not found.")
                    }
                }
            }
            "set" -> {
                val key = inv.positionalArgs.getOrNull(1)
                val value = inv.positionalArgs.drop(2).joinToString(" ")
                if (key == null || value.isBlank()) {
                    CommandResult.error("Usage: /memory set <key> <value>")
                } else {
                    val entry = memoryStore.setEntry(key, value, MemoryScope.GLOBAL)
                    CommandResult.success("[MEMORY STORED] ${entry.key} = \"${entry.value}\"")
                }
            }
            "search" -> {
                val query = inv.positionalArgs.drop(1).joinToString(" ")
                if (query.isBlank()) {
                    CommandResult.error("Usage: /memory search <query>")
                } else {
                    val matches = memoryStore.search(query)
                    val report = buildString {
                        appendLine("Memory matches for '$query' (${matches.size}):")
                        matches.forEach { appendLine("• [${it.scope.name}] ${it.key}: ${it.value.take(80)}") }
                    }.trim()
                    CommandResult.info(report)
                }
            }
            "clear" -> {
                val count = memoryStore.clearScope(MemoryScope.TASK)
                CommandResult.success("Cleared $count task-scoped memory entries.")
            }
            else -> CommandResult.info("Usage: /memory [list | get <key> | set <key> <value> | search <query> | clear]")
        }
    }

    private fun handleContextCommand(inv: CommandInvocation): CommandResult {
        val sub = inv.positionalArgs.firstOrNull()?.lowercase() ?: "show"
        return when (sub) {
            "show" -> {
                val activeTask = runtimeState.activeTask.value
                val sessionContext = contextRouter.getOrCreateSessionContext("default_session")
                val report = buildString {
                    appendLine("=== CONTEXT ISOLATION REPORT ===")
                    appendLine("Global Session Scope: ${sessionContext.sessionId}")
                    appendLine("Session Variables:    ${sessionContext.sessionVariables.size}")
                    appendLine("Active Task Scope:    ${activeTask?.let { "${it.taskId} ('${it.goal}')" } ?: "None (Clean Isolation)"}")
                    appendLine("Isolated Scratchpads: ${contextRouter.getActiveTaskContextsCount()}")
                }.trim()
                CommandResult.info(report)
            }
            "reset" -> {
                contextRouter.resetAllScopes()
                runtimeState.resetToTextChat()
                CommandResult.success("[CONTEXT] All task and session scratchpads cleared. Clean slate established.")
            }
            else -> CommandResult.info("Usage: /context [show | reset]")
        }
    }

    private fun handleConfigCommand(inv: CommandInvocation): CommandResult {
        val sub = inv.positionalArgs.firstOrNull()?.lowercase() ?: "list"
        return when (sub) {
            "list" -> CommandResult.info(configManager.formatReport())
            "get" -> {
                val key = inv.positionalArgs.getOrNull(1)
                if (key == null) {
                    CommandResult.error("Usage: /config get <key>")
                } else {
                    val value = configManager.get(key)
                    if (value != null) {
                        CommandResult.info("[CONFIG] $key = $value")
                    } else {
                        CommandResult.error("Config key '$key' not found.")
                    }
                }
            }
            "set" -> {
                val key = inv.positionalArgs.getOrNull(1)
                val value = inv.positionalArgs.drop(2).joinToString(" ")
                if (key == null || value.isBlank()) {
                    CommandResult.error("Usage: /config set <key> <value>")
                } else {
                    val ok = configManager.set(key, value)
                    if (ok) {
                        CommandResult.success("[CONFIG UPDATED] $key = $value")
                    } else {
                        CommandResult.error("Config key '$key' is read-only or invalid.")
                    }
                }
            }
            else -> CommandResult.info("Usage: /config [list | get <key> | set <key> <value>]")
        }
    }

    companion object {
        val global: CommandRegistry by lazy { CommandRegistry() }
    }
}
