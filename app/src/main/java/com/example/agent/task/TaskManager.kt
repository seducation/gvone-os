package com.example.agent.task

import com.example.agent.core.AgentStatus
import com.example.agent.core.CancellationToken
import com.example.agent.runtime.AgentTask
import com.example.agent.runtime.InteractionType
import com.example.agent.runtime.RuntimeStateManager
import com.example.agent.runtime.StepExecutionStatus
import com.example.agent.runtime.TaskStep
import com.example.data.terminal.ConversationTreeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * First-class Task Status in GVONE OS State Machine.
 * CREATED -> PLANNED -> RUNNING -> WAITING_PERMISSION -> SUSPENDED -> COMPLETED / FAILED / CANCELLED
 */
enum class TaskLifecycleStatus(val displayName: String) {
    CREATED("Created"),
    PLANNED("Planned"),
    QUEUED("Queued"),
    RUNNING("Running"),
    WAITING_PERMISSION("Waiting Permission"),
    WAITING("Waiting"),
    SUSPENDED("Suspended"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    FAILED("Failed"),
    CANCELLED("Cancelled")
}

/**
 * Task execution constraints and resource budgets.
 */
data class TaskBudget(
    val maxSteps: Int = 50,
    val maxTokens: Int = 16384,
    val maxDurationMs: Long = 120_000L
)

/**
 * First-class Task entity supporting tree hierarchy, isolation scopes, and artifact tracking.
 */
data class ManagedTask(
    val taskId: String = UUID.randomUUID().toString(),
    val parentTaskId: String? = null,
    val rootTaskId: String = taskId,
    val goal: String,
    val status: TaskLifecycleStatus = TaskLifecycleStatus.RUNNING,
    val assignedAgent: String = "AutoAgent",
    val priority: Int = 100,
    val budget: TaskBudget = TaskBudget(),
    val progress: Float = 0.0f,
    val steps: List<TaskStep> = emptyList(),
    val artifacts: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val result: String? = null,
    val error: String? = null,
    val cancellationToken: CancellationToken = CancellationToken(),
    val contextScopeId: String = taskId
)

/**
 * Central Task Manager for GVONE OS.
 * Provides first-class task queuing, execution tracking, pause/resume, and cancellation propagation.
 */
class TaskManager(
    private val runtimeState: RuntimeStateManager = RuntimeStateManager.global,
    private val treeManager: ConversationTreeManager = ConversationTreeManager.global
) {
    private val _tasks = MutableStateFlow<List<ManagedTask>>(emptyList())
    val tasks: StateFlow<List<ManagedTask>> = _tasks.asStateFlow()

    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    fun createTask(
        goal: String,
        assignedAgent: String = "AutoAgent",
        parentTaskId: String? = null,
        priority: Int = 100
    ): ManagedTask {
        val rootId = if (parentTaskId != null) {
            getTask(parentTaskId)?.rootTaskId ?: parentTaskId
        } else UUID.randomUUID().toString()

        val task = ManagedTask(
            parentTaskId = parentTaskId,
            rootTaskId = rootId,
            goal = goal,
            assignedAgent = assignedAgent,
            priority = priority,
            status = TaskLifecycleStatus.RUNNING
        )
        _tasks.value = listOf(task) + _tasks.value
        _activeTaskId.value = task.taskId

        // Sync with runtime state
        runtimeState.createTask(goal, InteractionType.TEXT)
        return task
    }

    fun getTask(taskId: String): ManagedTask? = _tasks.value.find { it.taskId == taskId }

    fun getActiveTask(): ManagedTask? = _activeTaskId.value?.let { getTask(it) }

    fun updateProgress(taskId: String, progress: Float, stepDescription: String? = null) {
        _tasks.value = _tasks.value.map { task ->
            if (task.taskId == taskId) {
                val updatedSteps = if (stepDescription != null) {
                    val step = TaskStep(
                        stepNumber = task.steps.size + 1,
                        description = stepDescription,
                        toolOrAgent = task.assignedAgent,
                        status = StepExecutionStatus.RUNNING
                    )
                    task.steps + step
                } else task.steps
                task.copy(
                    progress = progress.coerceIn(0f, 1f),
                    steps = updatedSteps,
                    updatedAt = System.currentTimeMillis()
                )
            } else task
        }
    }

    fun completeTask(taskId: String, result: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.taskId == taskId) {
                task.copy(
                    status = TaskLifecycleStatus.COMPLETED,
                    progress = 1.0f,
                    result = result,
                    updatedAt = System.currentTimeMillis()
                )
            } else task
        }
        if (_activeTaskId.value == taskId) {
            _activeTaskId.value = null
        }
        runtimeState.completeTask(taskId, result)
        treeManager.completeTask(taskId, result, AgentStatus.COMPLETED)
    }

    fun failTask(taskId: String, error: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.taskId == taskId) {
                task.copy(
                    status = TaskLifecycleStatus.FAILED,
                    error = error,
                    updatedAt = System.currentTimeMillis()
                )
            } else task
        }
        if (_activeTaskId.value == taskId) {
            _activeTaskId.value = null
        }
        runtimeState.failTask(taskId, error)
        treeManager.completeTask(taskId, "Failed: $error", AgentStatus.FAILED)
    }

    fun pauseTask(taskId: String? = null): Boolean {
        val id = taskId ?: _activeTaskId.value ?: return false
        var changed = false
        _tasks.value = _tasks.value.map { task ->
            if (task.taskId == id && task.status == TaskLifecycleStatus.RUNNING) {
                changed = true
                task.copy(status = TaskLifecycleStatus.PAUSED, updatedAt = System.currentTimeMillis())
            } else task
        }
        if (changed) {
            runtimeState.pauseTask(id)
        }
        return changed
    }

    fun resumeTask(taskId: String? = null): Boolean {
        val id = taskId ?: _activeTaskId.value ?: return false
        var changed = false
        _tasks.value = _tasks.value.map { task ->
            if (task.taskId == id && task.status == TaskLifecycleStatus.PAUSED) {
                changed = true
                task.copy(status = TaskLifecycleStatus.RUNNING, updatedAt = System.currentTimeMillis())
            } else task
        }
        if (changed) {
            runtimeState.resumeTask(id)
        }
        return changed
    }

    fun cancelTask(taskId: String? = null): Boolean {
        val id = taskId ?: _activeTaskId.value ?: return false
        var cancelled = false
        _tasks.value = _tasks.value.map { task ->
            if (task.taskId == id && (task.status == TaskLifecycleStatus.RUNNING || task.status == TaskLifecycleStatus.PAUSED)) {
                task.cancellationToken.cancel()
                cancelled = true
                task.copy(
                    status = TaskLifecycleStatus.CANCELLED,
                    error = "Task cancelled by user",
                    updatedAt = System.currentTimeMillis()
                )
            } else task
        }
        if (_activeTaskId.value == id) {
            _activeTaskId.value = null
        }
        if (cancelled) {
            runtimeState.cancelTask(id)
            treeManager.completeTask(id, "Cancelled by user", AgentStatus.CANCELLED)
        }
        return cancelled
    }

    fun formatTasksReport(): String = buildString {
        appendLine("=== GVONE OS TASK REGISTRY ===")
        val all = _tasks.value
        if (all.isEmpty()) {
            appendLine("No tasks registered.")
        } else {
            all.forEachIndexed { i, t ->
                val activeMarker = if (t.taskId == _activeTaskId.value) " [ACTIVE]" else ""
                appendLine("${i + 1}. [${t.status.displayName.uppercase()}]$activeMarker ${t.goal}")
                appendLine("   ID: ${t.taskId} | Agent: ${t.assignedAgent} | Progress: ${(t.progress * 100).toInt()}%")
                if (t.result != null) appendLine("   Result: ${t.result.take(100)}")
                if (t.error != null) appendLine("   Error: ${t.error}")
            }
        }
    }.trim()

    companion object {
        val global: TaskManager by lazy { TaskManager() }
    }
}
