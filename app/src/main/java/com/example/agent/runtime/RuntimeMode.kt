package com.example.agent.runtime

import com.example.agent.core.AgentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Interaction type determining how the user communicates.
 * TEXT: Keyboard / command input.
 * VOICE: Speech / audio conversational loop.
 */
enum class InteractionType(val displayName: String) {
    TEXT("Text"),
    VOICE("Voice")
}

/**
 * Execution type determining system behavior.
 * CHAT: Direct conversational Q&A / single responses.
 * AGENT: Multi-step autonomous task execution with planning, observation, and tools.
 */
enum class ExecutionType(val displayName: String) {
    CHAT("Chat"),
    AGENT("Agent")
}

/**
 * Mode Priority determining precedence during compound interaction:
 * e.g. /voice /agent -> INTERACTION priority (Voice is primary)
 * e.g. /agent /voice -> EXECUTION priority (Agent task is primary)
 */
enum class ModePriority(val displayName: String) {
    INTERACTION("Interaction Priority"),
    EXECUTION("Execution Priority")
}

/**
 * Unified runtime state representing the current operating environment.
 */
data class RuntimeMode(
    val interaction: InteractionType = InteractionType.TEXT,
    val execution: ExecutionType = ExecutionType.CHAT,
    val priority: ModePriority = ModePriority.INTERACTION,
    val activeTaskId: String? = null,
    val isDebugEnabled: Boolean = false,
    val isVoiceActive: Boolean = false
) {
    val isVoicePrimary: Boolean
        get() = interaction == InteractionType.VOICE && priority == ModePriority.INTERACTION

    val isAgentPrimary: Boolean
        get() = execution == ExecutionType.AGENT && priority == ModePriority.EXECUTION

    val isAgentic: Boolean
        get() = execution == ExecutionType.AGENT

    val isVoice: Boolean
        get() = interaction == InteractionType.VOICE || isVoiceActive

    val isText: Boolean
        get() = interaction == InteractionType.TEXT && !isVoiceActive

    fun toPromptLabel(): String = buildString {
        append("gvone[")
        append(if (interaction == InteractionType.VOICE) "voice" else "text")
        append(":")
        append(if (execution == ExecutionType.AGENT) "agent" else "chat")
        if (priority == ModePriority.INTERACTION && interaction == InteractionType.VOICE && execution == ExecutionType.AGENT) {
            append(":voice-first")
        } else if (priority == ModePriority.EXECUTION && interaction == InteractionType.VOICE && execution == ExecutionType.AGENT) {
            append(":agent-first")
        }
        append("]")
    }
}

/**
 * Individual step inside an Agent Task.
 */
data class TaskStep(
    val stepNumber: Int,
    val description: String,
    val toolOrAgent: String,
    val expectedOutcome: String = "",
    val status: StepExecutionStatus = StepExecutionStatus.PENDING,
    val observation: String? = null,
    val error: String? = null,
    val retryCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

enum class StepExecutionStatus {
    PENDING,
    RUNNING,
    OBSERVING,
    RETRYING,
    VERIFIED,
    COMPLETED,
    FAILED,
    SKIPPED
}

/**
 * Isolated Agent Task model ensuring strict boundary isolation.
 * Task A's execution context, scratchpad, and memory are strictly scoped to this object
 * and never leak into subsequent tasks or conversations.
 */
data class AgentTask(
    val taskId: String = UUID.randomUUID().toString(),
    val sessionId: String = UUID.randomUUID().toString(),
    val goal: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: AgentStatus = AgentStatus.PLANNING,
    val agentName: String = "AutoAgent",
    val primaryInteraction: InteractionType = InteractionType.TEXT,
    val steps: List<TaskStep> = emptyList(),
    val observations: List<String> = emptyList(),
    val result: String? = null,
    val error: String? = null,
    val isCompleted: Boolean = false,
    val isolatedMemory: MutableMap<String, Any?> = mutableMapOf()
)

/**
 * Unified Runtime State Manager for GVONE OS.
 */
class RuntimeStateManager private constructor() {

    private val _runtimeMode = MutableStateFlow(RuntimeMode())
    val runtimeMode: StateFlow<RuntimeMode> = _runtimeMode.asStateFlow()

    private val _activeTask = MutableStateFlow<AgentTask?>(null)
    val activeTask: StateFlow<AgentTask?> = _activeTask.asStateFlow()

    private val _taskHistory = MutableStateFlow<List<AgentTask>>(emptyList())
    val taskHistory: StateFlow<List<AgentTask>> = _taskHistory.asStateFlow()

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    fun logDebug(message: String) {
        if (_runtimeMode.value.isDebugEnabled) {
            _debugLogs.value = (_debugLogs.value + "[DEBUG ${System.currentTimeMillis()}] $message").takeLast(200)
        }
    }

    /**
     * Switch to default text conversation mode.
     */
    fun resetToTextChat() {
        _runtimeMode.value = _runtimeMode.value.copy(
            interaction = InteractionType.TEXT,
            execution = ExecutionType.CHAT,
            priority = ModePriority.INTERACTION,
            activeTaskId = null,
            isVoiceActive = false
        )
    }

    /**
     * Toggle or explicitly set Voice interaction mode ON/OFF.
     * Preserves current execution mode (whether AGENT or CHAT).
     */
    fun toggleVoice(enable: Boolean? = null) {
        val currentActive = _runtimeMode.value.isVoiceActive || _runtimeMode.value.interaction == InteractionType.VOICE
        val next = enable ?: !currentActive
        _runtimeMode.value = _runtimeMode.value.copy(
            interaction = if (next) InteractionType.VOICE else InteractionType.TEXT,
            isVoiceActive = next
        )
        logDebug("Voice mode set to: $next (Agent remains ${_runtimeMode.value.execution.name})")
    }

    /**
     * Toggle or explicitly set Agent execution mode ON/OFF.
     * Preserves current interaction mode (whether TEXT or VOICE).
     */
    fun toggleAgent(enable: Boolean? = null) {
        val currentAgent = _runtimeMode.value.execution == ExecutionType.AGENT
        val next = enable ?: !currentAgent
        _runtimeMode.value = _runtimeMode.value.copy(
            execution = if (next) ExecutionType.AGENT else ExecutionType.CHAT,
            activeTaskId = if (next) _runtimeMode.value.activeTaskId else null
        )
        logDebug("Agent mode set to: $next (Voice remains ${_runtimeMode.value.interaction.name})")
    }

    /**
     * Explicitly switch interaction to TEXT mode (turning Voice OFF).
     * Preserves current execution mode (whether AGENT or CHAT).
     */
    fun setTextMode() {
        _runtimeMode.value = _runtimeMode.value.copy(
            interaction = InteractionType.TEXT,
            isVoiceActive = false
        )
        logDebug("Switched to TEXT mode. Voice is OFF, Agent remains ${_runtimeMode.value.execution.name}")
    }

    /**
     * /voice -> Enable voice conversation only (no agentic task).
     */
    fun activateVoiceOnly() {
        _runtimeMode.value = _runtimeMode.value.copy(
            interaction = InteractionType.VOICE,
            execution = ExecutionType.CHAT,
            priority = ModePriority.INTERACTION,
            isVoiceActive = true
        )
        logDebug("Mode transitioned: VOICE ONLY (interaction=VOICE, execution=CHAT, priority=INTERACTION)")
    }

    /**
     * /agent -> Start an agentic task (text-based by default).
     */
    fun activateAgentOnly(goal: String? = null): AgentTask? {
        val task = goal?.let { createTask(it, InteractionType.TEXT) }
        _runtimeMode.value = _runtimeMode.value.copy(
            interaction = InteractionType.TEXT,
            execution = ExecutionType.AGENT,
            priority = ModePriority.EXECUTION,
            activeTaskId = task?.taskId
        )
        logDebug("Mode transitioned: AGENT ONLY (interaction=TEXT, execution=AGENT, priority=EXECUTION)")
        return task
    }

    /**
     * /voice /agent -> Voice is primary (User speaks, Agent executes).
     */
    fun activateVoiceAgentCompound(goal: String? = null): AgentTask? {
        val task = goal?.let { createTask(it, InteractionType.VOICE) }
        _runtimeMode.value = _runtimeMode.value.copy(
            interaction = InteractionType.VOICE,
            execution = ExecutionType.AGENT,
            priority = ModePriority.INTERACTION,
            activeTaskId = task?.taskId,
            isVoiceActive = true
        )
        logDebug("Mode transitioned: VOICE-FIRST AGENT (interaction=VOICE, execution=AGENT, priority=INTERACTION)")
        return task
    }

    /**
     * /agent /voice -> Agent is primary (Agent task UI is primary, voice used for input/output).
     */
    fun activateAgentVoiceCompound(goal: String? = null): AgentTask? {
        val task = goal?.let { createTask(it, InteractionType.VOICE) }
        _runtimeMode.value = _runtimeMode.value.copy(
            interaction = InteractionType.VOICE,
            execution = ExecutionType.AGENT,
            priority = ModePriority.EXECUTION,
            activeTaskId = task?.taskId,
            isVoiceActive = true
        )
        logDebug("Mode transitioned: AGENT-FIRST VOICE (interaction=VOICE, execution=AGENT, priority=EXECUTION)")
        return task
    }

    /**
     * Creates a new isolated task ensuring no context bleeding from past tasks.
     */
    fun createTask(goal: String, interaction: InteractionType): AgentTask {
        val task = AgentTask(
            goal = goal,
            primaryInteraction = interaction,
            status = AgentStatus.PLANNING
        )
        _activeTask.value = task
        _runtimeMode.value = _runtimeMode.value.copy(activeTaskId = task.taskId)
        logDebug("Created isolated AgentTask: id=${task.taskId}, goal='$goal'")
        return task
    }

    /**
     * Updates an existing task's state.
     */
    fun updateTask(updater: (AgentTask) -> AgentTask) {
        val current = _activeTask.value ?: return
        val updated = updater(current)
        _activeTask.value = updated
    }

    /**
     * Complete current task and mark explicit boundary to prevent voice/chat context bleeding.
     */
    fun completeTask(taskId: String, result: String) {
        val current = _activeTask.value
        if (current?.taskId == taskId) {
            val completed = current.copy(
                status = AgentStatus.COMPLETED,
                result = result,
                isCompleted = true
            )
            _activeTask.value = null
            _taskHistory.value = _taskHistory.value + completed
            
            // Revert execution mode to CHAT so next user utterance is treated as fresh intent
            _runtimeMode.value = _runtimeMode.value.copy(
                execution = ExecutionType.CHAT,
                activeTaskId = null
            )
            logDebug("Task completed with isolation boundary: id=$taskId")
        }
    }

    /**
     * Fail current task and archive context.
     */
    fun failTask(taskId: String, error: String) {
        val current = _activeTask.value
        if (current?.taskId == taskId) {
            val failed = current.copy(
                status = AgentStatus.FAILED,
                error = error,
                isCompleted = true
            )
            _activeTask.value = null
            _taskHistory.value = _taskHistory.value + failed
            _runtimeMode.value = _runtimeMode.value.copy(
                execution = ExecutionType.CHAT,
                activeTaskId = null
            )
            logDebug("Task failed with isolation boundary: id=$taskId, error=$error")
        }
    }

    /**
     * Cancel running task.
     */
    fun cancelTask(taskId: String? = null) {
        val targetId = taskId ?: _activeTask.value?.taskId ?: return
        val current = _activeTask.value
        if (current?.taskId == targetId) {
            val cancelled = current.copy(
                status = AgentStatus.CANCELLED,
                error = "User cancelled task",
                isCompleted = true
            )
            _activeTask.value = null
            _taskHistory.value = _taskHistory.value + cancelled
        }
        _runtimeMode.value = _runtimeMode.value.copy(
            execution = ExecutionType.CHAT,
            activeTaskId = null
        )
        logDebug("Task cancelled: id=$targetId")
    }

    /**
     * Pause running task.
     */
    fun pauseTask(taskId: String? = null) {
        val targetId = taskId ?: _activeTask.value?.taskId ?: return
        val current = _activeTask.value
        if (current?.taskId == targetId) {
            _activeTask.value = current.copy(status = AgentStatus.PAUSED)
            logDebug("Task paused: id=$targetId")
        }
    }

    /**
     * Resume paused task.
     */
    fun resumeTask(taskId: String? = null) {
        val targetId = taskId ?: _activeTask.value?.taskId ?: return
        val current = _activeTask.value
        if (current?.taskId == targetId && current.status == AgentStatus.PAUSED) {
            _activeTask.value = current.copy(status = AgentStatus.EXECUTING)
            logDebug("Task resumed: id=$targetId")
        }
    }

    fun toggleDebugMode(enable: Boolean? = null) {
        val current = _runtimeMode.value.isDebugEnabled
        val next = enable ?: !current
        _runtimeMode.value = _runtimeMode.value.copy(isDebugEnabled = next)
        logDebug("Debug mode set to: $next")
    }

    companion object {
        val global: RuntimeStateManager by lazy { RuntimeStateManager() }
    }
}
