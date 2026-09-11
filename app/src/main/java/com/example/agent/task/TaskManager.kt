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
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

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
 * Serializable execution checkpoint enabling resume-from-checkpoint after failures or restarts.
 */
data class TaskCheckpoint(
    val checkpointId: String = UUID.randomUUID().toString(),
    val taskId: String,
    val stepIndex: Int,
    val progress: Float,
    val status: TaskLifecycleStatus,
    val timestamp: Long = System.currentTimeMillis(),
    val snapshotData: String = ""
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

    private val _checkpoints = ConcurrentHashMap<String, MutableList<TaskCheckpoint>>()
    private val persistentStoreFile: File by lazy {
        File(System.getProperty("java.io.tmpdir") ?: "/tmp", "gvone_tasks_store.json")
    }

    init {
        restoreFromFile()
    }

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
        persistToFile()
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
        persistToFile()
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
        persistToFile()
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
        persistToFile()
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
            persistToFile()
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
            persistToFile()
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
        persistToFile()
        return cancelled
    }

    /**
     * Creates an execution checkpoint for a task, enabling recovery after restart or error.
     */
    fun createCheckpoint(taskId: String, snapshotData: String = ""): TaskCheckpoint? {
        val task = getTask(taskId) ?: return null
        val checkpoint = TaskCheckpoint(
            taskId = taskId,
            stepIndex = task.steps.size,
            progress = task.progress,
            status = task.status,
            snapshotData = snapshotData
        )
        val list = _checkpoints.computeIfAbsent(taskId) { mutableListOf() }
        synchronized(list) {
            list.add(checkpoint)
        }
        persistToFile()
        return checkpoint
    }

    /**
     * Retrieves all recorded checkpoints for a task.
     */
    fun getCheckpoints(taskId: String): List<TaskCheckpoint> {
        return _checkpoints[taskId]?.toList() ?: emptyList()
    }

    /**
     * Resumes task execution from a previously recorded checkpoint.
     */
    fun resumeFromCheckpoint(checkpointId: String): ManagedTask? {
        var foundCheckpoint: TaskCheckpoint? = null
        for ((_, list) in _checkpoints) {
            val cp = list.find { it.checkpointId == checkpointId }
            if (cp != null) {
                foundCheckpoint = cp
                break
            }
        }
        val cp = foundCheckpoint ?: return null
        val task = getTask(cp.taskId) ?: return null

        val restoredTask = task.copy(
            status = TaskLifecycleStatus.RUNNING,
            progress = cp.progress,
            updatedAt = System.currentTimeMillis()
        )

        _tasks.value = _tasks.value.map { if (it.taskId == restoredTask.taskId) restoredTask else it }
        _activeTaskId.value = restoredTask.taskId
        persistToFile()
        return restoredTask
    }

    /**
     * Persists all tasks and checkpoints to a persistent file.
     */
    fun persistToFile(targetFile: File? = null) {
        try {
            val file = targetFile ?: persistentStoreFile
            val rootObj = JSONObject()
            val tasksArray = JSONArray()

            for (task in _tasks.value) {
                val tObj = JSONObject().apply {
                    put("taskId", task.taskId)
                    task.parentTaskId?.let { put("parentTaskId", it) }
                    task.rootTaskId?.let { put("rootTaskId", it) }
                    put("goal", task.goal)
                    put("status", task.status.name)
                    put("assignedAgent", task.assignedAgent)
                    put("priority", task.priority)
                    put("progress", task.progress.toDouble())
                    put("createdAt", task.createdAt)
                    put("updatedAt", task.updatedAt)
                    task.result?.let { put("result", it) }
                    task.error?.let { put("error", it) }
                }
                tasksArray.put(tObj)
            }
            rootObj.put("tasks", tasksArray)

            val cpArray = JSONArray()
            for ((_, cpList) in _checkpoints) {
                for (cp in cpList) {
                    val cpObj = JSONObject().apply {
                        put("checkpointId", cp.checkpointId)
                        put("taskId", cp.taskId)
                        put("stepIndex", cp.stepIndex)
                        put("progress", cp.progress.toDouble())
                        put("status", cp.status.name)
                        put("timestamp", cp.timestamp)
                        cp.snapshotData?.let { put("snapshotData", it) }
                    }
                    cpArray.put(cpObj)
                }
            }
            rootObj.put("checkpoints", cpArray)

            file.parentFile?.mkdirs()
            file.writeText(rootObj.toString(2))
        } catch (e: Exception) {
            System.err.println("TaskManager.persistToFile error: ${e.message}")
        }
    }

    /**
     * Restores persisted tasks and checkpoints from storage after process restart.
     */
    fun restoreFromFile(sourceFile: File? = null): Int {
        try {
            val file = sourceFile ?: persistentStoreFile
            if (!file.exists()) return 0
            val text = file.readText()
            if (text.isBlank()) return 0
            val rootObj = JSONObject(text)

            val tasksArray = rootObj.optJSONArray("tasks")
            val loadedTasks = mutableListOf<ManagedTask>()
            if (tasksArray != null) {
                for (i in 0 until tasksArray.length()) {
                    val obj = tasksArray.getJSONObject(i)
                    val statusStr = obj.optString("status", TaskLifecycleStatus.RUNNING.name)
                    val status = try {
                        TaskLifecycleStatus.valueOf(statusStr)
                    } catch (_: Exception) {
                        TaskLifecycleStatus.RUNNING
                    }
                    val task = ManagedTask(
                        taskId = obj.getString("taskId"),
                        parentTaskId = if (obj.has("parentTaskId") && !obj.isNull("parentTaskId")) obj.getString("parentTaskId") else null,
                        rootTaskId = obj.optString("rootTaskId", obj.getString("taskId")),
                        goal = obj.optString("goal", "Restored task"),
                        status = status,
                        assignedAgent = obj.optString("assignedAgent", "AutoAgent"),
                        priority = obj.optInt("priority", 100),
                        progress = obj.optDouble("progress", 0.0).toFloat(),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        result = if (obj.has("result") && !obj.isNull("result")) obj.getString("result") else null,
                        error = if (obj.has("error") && !obj.isNull("error")) obj.getString("error") else null
                    )
                    loadedTasks.add(task)
                }
            }

            val cpArray = rootObj.optJSONArray("checkpoints")
            if (cpArray != null) {
                for (i in 0 until cpArray.length()) {
                    val cpObj = cpArray.getJSONObject(i)
                    val statusStr = cpObj.optString("status", TaskLifecycleStatus.RUNNING.name)
                    val status = try {
                        TaskLifecycleStatus.valueOf(statusStr)
                    } catch (_: Exception) {
                        TaskLifecycleStatus.RUNNING
                    }
                    val cp = TaskCheckpoint(
                        checkpointId = cpObj.getString("checkpointId"),
                        taskId = cpObj.getString("taskId"),
                        stepIndex = cpObj.optInt("stepIndex", 0),
                        progress = cpObj.optDouble("progress", 0.0).toFloat(),
                        status = status,
                        timestamp = cpObj.optLong("timestamp", System.currentTimeMillis()),
                        snapshotData = cpObj.optString("snapshotData", "")
                    )
                    val list = _checkpoints.computeIfAbsent(cp.taskId) { mutableListOf() }
                    synchronized(list) {
                        if (list.none { it.checkpointId == cp.checkpointId }) {
                            list.add(cp)
                        }
                    }
                }
            }

            if (loadedTasks.isNotEmpty()) {
                _tasks.value = loadedTasks
            }
            return loadedTasks.size
        } catch (_: Exception) {
            return 0
        }
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
