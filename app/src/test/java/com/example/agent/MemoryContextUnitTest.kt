package com.example.agent

import com.example.agent.memory.ContextRouter
import com.example.agent.memory.MemoryScope
import com.example.agent.memory.MemoryStore
import com.example.agent.runtime.AgentTask
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemoryContextUnitTest {

    private lateinit var memoryStore: MemoryStore
    private lateinit var contextRouter: ContextRouter

    @Before
    fun setUp() {
        memoryStore = MemoryStore.global
        contextRouter = ContextRouter.global
        contextRouter.resetAllScopes()
    }

    @Test
    fun testMemoryScopesAndIsolation() {
        // Global scope
        memoryStore.setEntry("user.theme", "dark", MemoryScope.GLOBAL)

        // Task scope
        memoryStore.setEntry("scrape.target", "https://news.ycombinator.com", MemoryScope.TASK, scopeIdentifier = "task_101")
        memoryStore.setEntry("scrape.target", "https://github.com/trending", MemoryScope.TASK, scopeIdentifier = "task_102")

        val globalEntry = memoryStore.getEntry("user.theme")
        assertNotNull(globalEntry)
        assertEquals("dark", globalEntry!!.value)
        assertEquals(MemoryScope.GLOBAL, globalEntry.scope)

        val taskEntries = memoryStore.listByScope(MemoryScope.TASK)
        assertTrue(taskEntries.isNotEmpty())

        // Search memory
        val searchResults = memoryStore.search("trending")
        assertEquals(1, searchResults.size)
        assertEquals("https://github.com/trending", searchResults.first().value)
    }

    @Test
    fun testContextRouterTaskIsolationNoBleed() {
        val taskA = AgentTask(taskId = "task_A", goal = "Debug auth error", agentName = "CodingAgent")
        val taskB = AgentTask(taskId = "task_B", goal = "Play youtube music", agentName = "BrowserAgent")

        val ctxA = contextRouter.getOrCreateTaskContext(taskA)
        val ctxB = contextRouter.getOrCreateTaskContext(taskB)

        // Record observations in Task A
        contextRouter.recordTaskObservation(taskA.taskId, "Grep auth tokens", "Found expired token")

        // Record observations in Task B
        contextRouter.recordTaskObservation(taskB.taskId, "Search YouTube", "Found lofi livestream")

        // Assemble context for Task A
        val assembledA = contextRouter.assembleAgentContext(taskA.taskId)
        @Suppress("UNCHECKED_CAST")
        val taskDataA = assembledA["task"] as Map<String, Any?>
        val stepsA = taskDataA["steps"] as List<String>
        assertTrue(stepsA.contains("Grep auth tokens"))
        assertFalse("Task A context must not contain Task B observations", stepsA.contains("Search YouTube"))

        // Assemble context for Task B
        val assembledB = contextRouter.assembleAgentContext(taskB.taskId)
        @Suppress("UNCHECKED_CAST")
        val taskDataB = assembledB["task"] as Map<String, Any?>
        val stepsB = taskDataB["steps"] as List<String>
        assertTrue(stepsB.contains("Search YouTube"))
        assertFalse("Task B context must not contain Task A observations", stepsB.contains("Grep auth tokens"))
    }
}
