package com.example.agent

import com.example.agent.core.AgentStatus
import com.example.agent.runtime.StepExecutionStatus
import com.example.data.terminal.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ConversationTreeHierarchyUnitTest {

    private lateinit var treeManager: ConversationTreeManager

    @Before
    fun setUp() {
        treeManager = ConversationTreeManager()
    }

    @Test
    fun testLevel1TaskCreationAndIndependentExpansion() {
        // Create 2 tasks
        val task1 = treeManager.createTask(
            title = "YouTube — Search & Play Song",
            category = TaskCategory.MEDIA,
            commandPrompt = "/yt lo-fi chill hop"
        )
        val task2 = treeManager.createTask(
            title = "Coding — Fix Authentication Bug",
            category = TaskCategory.CODING,
            commandPrompt = "/agent fix auth bug"
        )

        val tasks = treeManager.tasks.value
        // Includes initial seeded tasks + the 2 created tasks
        assertTrue(tasks.size >= 2)

        // Verify Level 1 independent toggle
        val initialTask1 = treeManager.tasks.value.find { it.id == task1.id }!!
        assertTrue(initialTask1.isExpanded)

        treeManager.toggleTaskExpansion(task1.id)
        val updatedTask1 = treeManager.tasks.value.find { it.id == task1.id }!!
        assertFalse(updatedTask1.isExpanded)

        // Task 2 expansion state remains unaffected
        val updatedTask2 = treeManager.tasks.value.find { it.id == task2.id }!!
        assertTrue(updatedTask2.isExpanded)
    }

    @Test
    fun testLevel2AgentRegistrationAndIndependentExpansion() {
        val task = treeManager.createTask(
            title = "Coding — Fix Authentication Bug",
            category = TaskCategory.CODING
        )

        // Register two Level 2 agents under the task
        val agentId1 = treeManager.addOrUpdateAgent(
            taskId = task.id,
            agentName = "CodingAgent",
            icon = "🤖",
            role = "Autonomous Sub-Agent"
        )
        val agentId2 = treeManager.addOrUpdateAgent(
            taskId = task.id,
            agentName = "BrowserAgent",
            icon = "🌐",
            role = "Documentation & API Lookup"
        )

        val updatedTask = treeManager.tasks.value.find { it.id == task.id }!!
        assertEquals(2, updatedTask.agents.size)

        // Toggle Level 2 agent 1 expansion
        val agent1 = updatedTask.agents.find { it.id == agentId1 }!!
        assertTrue(agent1.isExpanded)

        treeManager.toggleAgentExpansion(task.id, agentId1)
        val refreshedTask = treeManager.tasks.value.find { it.id == task.id }!!
        val refreshedAgent1 = refreshedTask.agents.find { it.id == agentId1 }!!
        val refreshedAgent2 = refreshedTask.agents.find { it.id == agentId2 }!!

        assertFalse("Agent 1 should be collapsed", refreshedAgent1.isExpanded)
        assertTrue("Agent 2 should remain expanded", refreshedAgent2.isExpanded)
    }

    @Test
    fun testLevel3StepToolCallAndReasoningDetails() {
        val task = treeManager.createTask(
            title = "Coding — Fix Authentication Bug",
            category = TaskCategory.CODING
        )
        val agentId = treeManager.addOrUpdateAgent(
            taskId = task.id,
            agentName = "CodingAgent",
            icon = "🤖"
        )

        // Add Level 3: Step
        treeManager.addDetailStep(
            taskId = task.id,
            agentId = agentId,
            type = DetailNodeType.STEP,
            title = "Analyze repository structure",
            status = StepExecutionStatus.COMPLETED
        )

        // Add Level 3: Tool Call
        treeManager.addDetailStep(
            taskId = task.id,
            agentId = agentId,
            type = DetailNodeType.TOOL_CALL,
            title = "grep AuthToken",
            toolName = "grep",
            toolArgs = "grep -rn 'AuthToken' /src",
            content = "Found 3 matches in AuthController.kt",
            durationMs = 45
        )

        // Add Level 3: Reasoning / Thought
        treeManager.addDetailStep(
            taskId = task.id,
            agentId = agentId,
            type = DetailNodeType.REASONING,
            title = "Reasoning",
            content = "Token refresh latch expired before listener fired."
        )

        // Add Level 3: Final Result
        treeManager.addDetailStep(
            taskId = task.id,
            agentId = agentId,
            type = DetailNodeType.RESULT,
            title = "Outcome",
            content = "Patched AuthController.kt with synchronized token refresh."
        )

        val updatedTask = treeManager.tasks.value.find { it.id == task.id }!!
        val updatedAgent = updatedTask.agents.find { it.id == agentId }!!
        assertEquals(4, updatedAgent.steps.size)

        assertEquals(DetailNodeType.STEP, updatedAgent.steps[0].type)
        assertEquals(DetailNodeType.TOOL_CALL, updatedAgent.steps[1].type)
        assertEquals("grep", updatedAgent.steps[1].toolName)
        assertEquals(DetailNodeType.REASONING, updatedAgent.steps[2].type)
        assertEquals(DetailNodeType.RESULT, updatedAgent.steps[3].type)
    }

    @Test
    fun testStreamLogExpandableTaskIntegration() {
        val task = treeManager.createTask(
            title = "YouTube — Search & Play Song",
            category = TaskCategory.MEDIA
        )

        // Stream log line representation
        val streamLine = TerminalLine(
            text = task.title,
            type = TerminalLineType.EXPANDABLE_TASK,
            taskId = task.id
        )

        assertEquals(TerminalLineType.EXPANDABLE_TASK, streamLine.type)
        assertEquals(task.id, streamLine.taskId)

        // Global expand / collapse helpers
        treeManager.collapseAll()
        val allCollapsed = treeManager.tasks.value.all { !it.isExpanded }
        assertTrue("All tasks should be collapsed", allCollapsed)

        treeManager.expandAll()
        val allExpanded = treeManager.tasks.value.all { it.isExpanded }
        assertTrue("All tasks should be expanded", allExpanded)
    }
}
