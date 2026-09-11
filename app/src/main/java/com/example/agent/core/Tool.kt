package com.example.agent.core

import com.example.agent.safety.PermissionStatus
import com.example.agent.safety.PermissionSystem
import java.util.concurrent.ConcurrentHashMap

/**
 * Unified tool interface for GVONE OS.
 * Every tool callable by specialized agents or NodalEngine must implement this interface.
 */
interface Tool {
    val name: String
    val description: String
    val riskLevel: RiskLevel get() = RiskLevel.LOW
    val requiresPermission: Boolean get() = requiredPermissions.isNotEmpty()
    val requiredPermissions: List<String> get() = emptyList()
    val inputSchema: Map<String, String> get() = emptyMap()
    val outputSchema: Map<String, String> get() = emptyMap()

    suspend fun execute(parameters: Map<String, Any?>): ToolResult
}

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val success: Boolean,
    val data: Any? = null,
    val error: String? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val durationMs: Long = 0L
) {
    val isSuccess: Boolean get() = success && error == null
}

/**
 * Thread-safe registry of all operational tools in GVONE OS.
 * Enforces permission mediation: agents cannot bypass permission checks when invoking tools.
 */
class ToolRegistry private constructor(
    private val permissionSystem: PermissionSystem = PermissionSystem.global,
    private val logger: StepLogger = StepLogger.global
) {
    private val tools = ConcurrentHashMap<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name.lowercase()] = tool
    }

    fun unregister(name: String) {
        tools.remove(name.lowercase())
    }

    fun getTool(name: String): Tool? = tools[name.lowercase()]

    fun getAllTools(): List<Tool> = tools.values.toList()

    fun hasTool(name: String): Boolean = tools.containsKey(name.lowercase())

    fun clear() {
        tools.clear()
    }

    /**
     * Executes a tool with mandatory permission mediation.
     * Validates required permissions against PermissionSystem before invoking tool.
     */
    suspend fun executeMediated(
        toolName: String,
        parameters: Map<String, Any?>,
        callingAgent: String = "system"
    ): ToolResult {
        val tool = getTool(toolName)
            ?: return ToolResult(success = false, error = "Tool '$toolName' not registered in ToolRegistry.")

        val startTime = System.currentTimeMillis()

        // 1. Permission check
        for (perm in tool.requiredPermissions) {
            val check = permissionSystem.checkPermission(perm, callingAgent)
            if (check.status != PermissionStatus.GRANTED) {
                logger.logInstant(
                    agentName = callingAgent,
                    action = StepType.VALIDATE,
                    target = "Tool '$toolName' blocked: Missing permission '$perm'",
                    status = StepStatus.FAILED,
                    errorMessage = check.reason
                )
                return ToolResult(
                    success = false,
                    error = "PERMISSION_DENIED: Agent '$callingAgent' lacks permission '$perm' for tool '$toolName'. ${check.reason ?: ""}",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // 2. Execution under logging
        val step = logger.startStep(
            agentName = callingAgent,
            action = StepType.TOOL_CALL,
            target = "${tool.name}: ${parameters.keys.joinToString()}",
            metadata = parameters
        )

        return try {
            val result = tool.execute(parameters)
            val duration = System.currentTimeMillis() - startTime
            if (result.success) {
                logger.completeStep(step.stepId, duration, mapOf("toolSuccess" to true))
            } else {
                logger.failStep(step.stepId, result.error ?: "Tool reported failure", duration)
            }
            result.copy(durationMs = duration)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.failStep(step.stepId, e.message ?: e.toString(), duration)
            ToolResult(
                success = false,
                error = "Tool execution exception: ${e.message}",
                durationMs = duration
            )
        }
    }

    init {
        registerStandardTools()
    }

    fun registerStandardTools() {
        register(BrowserTool())
        register(SearchTool())
        register(FileTool())
        register(ShellTool())
        register(GitTool())
        register(HTTPTool())
        register(VoiceTool())
        register(ImageTool())
    }

    companion object {
        val global: ToolRegistry by lazy {
            val reg = ToolRegistry()
            reg
        }
    }
}

