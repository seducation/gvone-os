package com.example.agent

import com.example.agent.task.TaskLifecycleStatus
import com.example.agent.task.TaskManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class TaskManagementUnitTest {

    private lateinit var taskManager: TaskManager

    @Before
    fun setUp() {
        taskManager = TaskManager.global
    }

    @Test
    fun testTaskLifecycleTransitions() {
        // Create task
        val task = taskManager.createTask("Diagnose memory usage in browser tabs", "DiagnosticAgent")
        assertEquals("Diagnose memory usage in browser tabs", task.goal)
        assertEquals("DiagnosticAgent", task.assignedAgent)
        assertEquals(TaskLifecycleStatus.RUNNING, task.status)
        assertEquals(task.taskId, taskManager.activeTaskId.value)

        // Update progress
        taskManager.updateProgress(task.taskId, 0.45f, "Inspected 5 tabs")
        val updated = taskManager.getTask(task.taskId)
        assertNotNull(updated)
        assertEquals(0.45f, updated!!.progress, 0.01f)
        assertEquals(1, updated.steps.size)
        assertEquals("Inspected 5 tabs", updated.steps.first().description)

        // Pause task
        val pausedOk = taskManager.pauseTask(task.taskId)
        assertTrue(pausedOk)
        assertEquals(TaskLifecycleStatus.PAUSED, taskManager.getTask(task.taskId)?.status)

        // Resume task
        val resumeOk = taskManager.resumeTask(task.taskId)
        assertTrue(resumeOk)
        assertEquals(TaskLifecycleStatus.RUNNING, taskManager.getTask(task.taskId)?.status)

        // Complete task
        taskManager.completeTask(task.taskId, "Memory optimized. 3 dormant tabs suspended.")
        val completed = taskManager.getTask(task.taskId)
        assertNotNull(completed)
        assertEquals(TaskLifecycleStatus.COMPLETED, completed!!.status)
        assertEquals(1.0f, completed.progress, 0.01f)
        assertEquals("Memory optimized. 3 dormant tabs suspended.", completed.result)
        assertNull(taskManager.activeTaskId.value)
    }

    @Test
    fun testTaskCancellation() {
        val task = taskManager.createTask("Long running crawler task", "BrowserAgent")
        assertFalse(task.cancellationToken.isCancelled)

        val cancelledOk = taskManager.cancelTask(task.taskId)
        assertTrue(cancelledOk)

        val cancelled = taskManager.getTask(task.taskId)
        assertNotNull(cancelled)
        assertEquals(TaskLifecycleStatus.CANCELLED, cancelled!!.status)
        assertTrue(cancelled.cancellationToken.isCancelled)
    }
}
