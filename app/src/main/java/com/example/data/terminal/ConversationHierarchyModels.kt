package com.example.data.terminal

import com.example.agent.core.AgentStatus
import com.example.agent.memory.ContextRouter
import com.example.agent.memory.TaskContext
import com.example.agent.runtime.AgentTask
import com.example.agent.runtime.ExecutionType
import com.example.agent.runtime.InteractionType
import com.example.agent.runtime.RuntimeStateManager
import com.example.agent.runtime.StepExecutionStatus
import com.example.agent.runtime.TaskStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Type of granular event/step shown in Level 3 details.
 */
enum class DetailNodeType {
    STEP,
    TOOL_CALL,
    REASONING,
    OBSERVATION,
    OUTPUT,
    ERROR,
    RESULT
}

/**
 * Category determining iconography and color scheme for Level 1 tasks.
 */
enum class TaskCategory(val defaultIcon: String, val label: String) {
    CODING("💻", "Coding"),
    MEDIA("🎵", "Media"),
    SEARCH("🌐", "Search"),
    BROWSER("🧭", "Browser"),
    VOICE("🎙️", "Voice"),
    SYSTEM("⚡", "System"),
    FILE("📁", "File"),
    GENERAL("🤖", "General")
}

/**
 * Level 3: Individual execution step, tool call, reasoning event, output, or result.
 */
data class ExecutionDetailNode(
    val id: String = UUID.randomUUID().toString(),
    val parentAgentId: String,
    val parentTaskId: String,
    val type: DetailNodeType = DetailNodeType.STEP,
    val title: String,
    val content: String? = null,
    val status: StepExecutionStatus = StepExecutionStatus.COMPLETED,
    val toolName: String? = null,
    val toolArgs: String? = null,
    val durationMs: Long? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Level 2: Agent or execution session participating in the task.
 */
data class AgentExecutionNode(
    val id: String = UUID.randomUUID().toString(),
    val parentTaskId: String,
    val agentName: String,
    val icon: String = "🤖",
    val role: String = "Autonomous Operator",
    val status: AgentStatus = AgentStatus.COMPLETED,
    val steps: List<ExecutionDetailNode> = emptyList(),
    val isExpanded: Boolean = false,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null
) {
    val completedStepsCount: Int
        get() = steps.count { it.status == StepExecutionStatus.COMPLETED || it.status == StepExecutionStatus.VERIFIED }
}

/**
 * Level 1: Independent user task or conversation.
 * Strictly context-isolated to ensure no memory leakage between tasks.
 */
data class ConversationTaskNode(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val category: TaskCategory = TaskCategory.GENERAL,
    val icon: String = category.defaultIcon,
    val commandPrompt: String = "",
    val interactionType: InteractionType = InteractionType.TEXT,
    val executionType: ExecutionType = ExecutionType.AGENT,
    val status: AgentStatus = AgentStatus.COMPLETED,
    val agents: List<AgentExecutionNode> = emptyList(),
    val isExpanded: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val isolatedMemoryScopeId: String = id,
    val summaryResult: String? = null
) {
    val totalStepsCount: Int
        get() = agents.sumOf { it.steps.size }

    val activeAgentCount: Int
        get() = agents.size
}

/**
 * State Manager for 3-Level Collapsible Conversation Structure in GVONE OS CLI.
 * Enforces:
 * - Level 1: Conversation / Task (independently expandable/collapsible)
 * - Level 2: Agent / Execution (independently expandable/collapsible)
 * - Level 3: Steps / Events / Details (terminal-native inspection)
 * - Context isolation between tasks.
 */
class ConversationTreeManager() {

    private val _tasks = MutableStateFlow<List<ConversationTaskNode>>(emptyList())
    val tasks: StateFlow<List<ConversationTaskNode>> = _tasks.asStateFlow()

    private val _selectedFilter = MutableStateFlow<String>("ALL") // ALL, ACTIVE, COMPLETED
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _activeTaskId = MutableStateFlow<String?>(null)
    val activeTaskId: StateFlow<String?> = _activeTaskId.asStateFlow()

    init {
        seedInitialConversations()
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setActiveTask(taskId: String?) {
        _activeTaskId.value = taskId
    }

    /**
     * Toggle Level 1 item expansion independently.
     */
    fun toggleTaskExpansion(taskId: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(isExpanded = !task.isExpanded)
            } else {
                task
            }
        }
    }

    /**
     * Toggle Level 2 agent expansion independently.
     */
    fun toggleAgentExpansion(taskId: String, agentId: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val updatedAgents = task.agents.map { agent ->
                    if (agent.id == agentId) {
                        agent.copy(isExpanded = !agent.isExpanded)
                    } else {
                        agent
                    }
                }
                task.copy(agents = updatedAgents)
            } else {
                task
            }
        }
    }

    /**
     * Expand all tasks and agents.
     */
    fun expandAll() {
        _tasks.value = _tasks.value.map { task ->
            task.copy(
                isExpanded = true,
                agents = task.agents.map { it.copy(isExpanded = true) }
            )
        }
    }

    /**
     * Collapse all tasks and agents.
     */
    fun collapseAll() {
        _tasks.value = _tasks.value.map { task ->
            task.copy(
                isExpanded = false,
                agents = task.agents.map { it.copy(isExpanded = false) }
            )
        }
    }

    /**
     * Creates a new isolated task at Level 1.
     */
    fun createTask(
        title: String,
        category: TaskCategory = TaskCategory.GENERAL,
        commandPrompt: String = "",
        interactionType: InteractionType = InteractionType.TEXT,
        executionType: ExecutionType = ExecutionType.AGENT,
        initialAgents: List<AgentExecutionNode> = emptyList(),
        taskId: String = UUID.randomUUID().toString()
    ): ConversationTaskNode {
        val newTask = ConversationTaskNode(
            id = taskId,
            title = title,
            category = category,
            icon = category.defaultIcon,
            commandPrompt = commandPrompt,
            interactionType = interactionType,
            executionType = executionType,
            status = AgentStatus.EXECUTING,
            agents = initialAgents,
            isExpanded = true,
            isolatedMemoryScopeId = taskId
        )

        // Ensure strictly isolated task context in ContextRouter
        val taskContext = ContextRouter.global.getOrCreateTaskContext(
            AgentTask(
                taskId = taskId,
                goal = title,
                agentName = initialAgents.firstOrNull()?.agentName ?: "AutoAgent"
            )
        )

        _tasks.value = listOf(newTask) + _tasks.value
        _activeTaskId.value = taskId
        return newTask
    }

    /**
     * Add or register an agent under a Level 1 task (Level 2).
     */
    fun addOrUpdateAgent(
        taskId: String,
        agentName: String,
        icon: String = "🤖",
        role: String = "Specialized Agent",
        status: AgentStatus = AgentStatus.EXECUTING,
        isExpanded: Boolean = true
    ): String {
        var returnedAgentId = ""
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val existing = task.agents.find { it.agentName.equals(agentName, ignoreCase = true) }
                if (existing != null) {
                    returnedAgentId = existing.id
                    val updatedAgents = task.agents.map {
                        if (it.id == existing.id) it.copy(status = status, isExpanded = isExpanded) else it
                    }
                    task.copy(agents = updatedAgents)
                } else {
                    val newAgentId = UUID.randomUUID().toString()
                    returnedAgentId = newAgentId
                    val newAgent = AgentExecutionNode(
                        id = newAgentId,
                        parentTaskId = taskId,
                        agentName = agentName,
                        icon = icon,
                        role = role,
                        status = status,
                        isExpanded = isExpanded
                    )
                    task.copy(agents = task.agents + newAgent)
                }
            } else {
                task
            }
        }
        return returnedAgentId
    }

    /**
     * Append an execution step / tool call / event under a Level 2 agent (Level 3).
     */
    fun addDetailStep(
        taskId: String,
        agentId: String,
        type: DetailNodeType,
        title: String,
        content: String? = null,
        status: StepExecutionStatus = StepExecutionStatus.COMPLETED,
        toolName: String? = null,
        toolArgs: String? = null,
        durationMs: Long? = null
    ) {
        val stepNode = ExecutionDetailNode(
            parentAgentId = agentId,
            parentTaskId = taskId,
            type = type,
            title = title,
            content = content,
            status = status,
            toolName = toolName,
            toolArgs = toolArgs,
            durationMs = durationMs
        )

        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val updatedAgents = task.agents.map { agent ->
                    if (agent.id == agentId) {
                        agent.copy(steps = agent.steps + stepNode)
                    } else {
                        agent
                    }
                }
                task.copy(agents = updatedAgents)
            } else {
                task
            }
        }

        // Record in ContextRouter's isolated task scratchpad
        ContextRouter.global.recordTaskObservation(taskId, "$type: $title", content ?: toolArgs ?: "")
    }

    /**
     * Complete a task and stamp result summary.
     */
    fun completeTask(taskId: String, summary: String, status: AgentStatus = AgentStatus.COMPLETED) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) {
                val updatedAgents = task.agents.map {
                    if (it.status == AgentStatus.EXECUTING) it.copy(status = AgentStatus.COMPLETED, endTime = System.currentTimeMillis()) else it
                }
                task.copy(
                    status = status,
                    summaryResult = summary,
                    completedAt = System.currentTimeMillis(),
                    agents = updatedAgents
                )
            } else {
                task
            }
        }
    }

    fun removeTask(taskId: String) {
        _tasks.value = _tasks.value.filterNot { it.id == taskId }
        if (_activeTaskId.value == taskId) {
            _activeTaskId.value = null
        }
    }

    fun clearCompleted() {
        _tasks.value = _tasks.value.filter { it.status == AgentStatus.EXECUTING || it.status == AgentStatus.PLANNING }
    }

    fun clearAllTasks() {
        _tasks.value = emptyList()
        _activeTaskId.value = null
    }

    /**
     * Seed initial tasks matching user specification examples.
     */
    private fun seedInitialConversations() {
        val taskCodingId = "task_seed_coding"
        val agentCodingId = "agent_seed_coding"
        val agentBrowserId = "agent_seed_browser"
        val agentFileId = "agent_seed_file"

        // Task 1: 💻 Coding — Fix Authentication Bug
        val codingSteps = listOf(
            ExecutionDetailNode(
                id = "step_1",
                parentAgentId = agentCodingId,
                parentTaskId = taskCodingId,
                type = DetailNodeType.STEP,
                title = "Analyze repository structure & auth dependencies",
                status = StepExecutionStatus.COMPLETED,
                durationMs = 120
            ),
            ExecutionDetailNode(
                id = "step_2",
                parentAgentId = agentCodingId,
                parentTaskId = taskCodingId,
                type = DetailNodeType.TOOL_CALL,
                title = "Run grep on token validation routines",
                toolName = "grep",
                toolArgs = "-r \"AuthToken\" /app/src/main/java/com/example/auth",
                content = "Found AuthService.kt:42: fun validateToken(token: String): Boolean = false",
                status = StepExecutionStatus.COMPLETED,
                durationMs = 85
            ),
            ExecutionDetailNode(
                id = "step_3",
                parentAgentId = agentCodingId,
                parentTaskId = taskCodingId,
                type = DetailNodeType.REASONING,
                title = "Identify root cause in AuthService expiration logic",
                content = "Token expiry timestamp was evaluated in seconds instead of milliseconds, causing instant invalidation.",
                status = StepExecutionStatus.COMPLETED
            ),
            ExecutionDetailNode(
                id = "step_4",
                parentAgentId = agentCodingId,
                parentTaskId = taskCodingId,
                type = DetailNodeType.STEP,
                title = "Patch auth token expiration verification logic",
                status = StepExecutionStatus.COMPLETED,
                durationMs = 150
            ),
            ExecutionDetailNode(
                id = "step_5",
                parentAgentId = agentCodingId,
                parentTaskId = taskCodingId,
                type = DetailNodeType.TOOL_CALL,
                title = "Execute unit test suite",
                toolName = "gradle",
                toolArgs = ":app:testDebugUnitTest --tests *AuthServiceTest*",
                content = "BUILD SUCCESSFUL in 420ms (3 tests passed)",
                status = StepExecutionStatus.COMPLETED,
                durationMs = 420
            ),
            ExecutionDetailNode(
                id = "step_6",
                parentAgentId = agentCodingId,
                parentTaskId = taskCodingId,
                type = DetailNodeType.RESULT,
                title = "Result: Authentication bug successfully patched & verified",
                content = "Corrected timestamp comparison in AuthService.kt. Unit tests passing.",
                status = StepExecutionStatus.COMPLETED
            )
        )

        val taskCoding = ConversationTaskNode(
            id = taskCodingId,
            title = "Coding — Fix Authentication Bug",
            category = TaskCategory.CODING,
            icon = "💻",
            commandPrompt = "/code Fix authentication token bug in AuthService",
            interactionType = InteractionType.TEXT,
            executionType = ExecutionType.AGENT,
            status = AgentStatus.COMPLETED,
            isExpanded = true,
            agents = listOf(
                AgentExecutionNode(
                    id = agentCodingId,
                    parentTaskId = taskCodingId,
                    agentName = "Coding Agent",
                    icon = "🤖",
                    role = "Kotlin Code Analyzer & Compiler",
                    status = AgentStatus.COMPLETED,
                    isExpanded = true,
                    steps = codingSteps
                ),
                AgentExecutionNode(
                    id = agentBrowserId,
                    parentTaskId = taskCodingId,
                    agentName = "Browser Agent",
                    icon = "🌐",
                    role = "OAuth Callback Verifier",
                    status = AgentStatus.COMPLETED,
                    isExpanded = false,
                    steps = listOf(
                        ExecutionDetailNode(
                            parentAgentId = agentBrowserId,
                            parentTaskId = taskCodingId,
                            type = DetailNodeType.STEP,
                            title = "Inspect redirect URI and header tokens",
                            status = StepExecutionStatus.COMPLETED
                        )
                    )
                ),
                AgentExecutionNode(
                    id = agentFileId,
                    parentTaskId = taskCodingId,
                    agentName = "File Agent",
                    icon = "📁",
                    role = "Sandbox File System Writer",
                    status = AgentStatus.COMPLETED,
                    isExpanded = false,
                    steps = listOf(
                        ExecutionDetailNode(
                            parentAgentId = agentFileId,
                            parentTaskId = taskCodingId,
                            type = DetailNodeType.STEP,
                            title = "Persist patch diff to /Documents/auth_fix.patch",
                            status = StepExecutionStatus.COMPLETED
                        )
                    )
                )
            ),
            summaryResult = "Fixed authentication bug in AuthService.kt and verified with passing unit test."
        )

        // Task 2: 🌐 News — Search Latest News
        val taskNews = ConversationTaskNode(
            id = "task_seed_news",
            title = "News — Search Latest News",
            category = TaskCategory.SEARCH,
            icon = "🌐",
            commandPrompt = "/search Latest artificial intelligence and technology breakthroughs",
            interactionType = InteractionType.TEXT,
            executionType = ExecutionType.AGENT,
            status = AgentStatus.COMPLETED,
            isExpanded = false,
            agents = listOf(
                AgentExecutionNode(
                    id = "agent_seed_news_search",
                    parentTaskId = "task_seed_news",
                    agentName = "Search Agent",
                    icon = "🔍",
                    role = "Multi-Source Intelligence Retriever",
                    status = AgentStatus.COMPLETED,
                    isExpanded = false,
                    steps = listOf(
                        ExecutionDetailNode(
                            parentAgentId = "agent_seed_news_search",
                            parentTaskId = "task_seed_news",
                            type = DetailNodeType.TOOL_CALL,
                            title = "Query tech news RSS and search APIs",
                            toolName = "AtlasWebEngine",
                            status = StepExecutionStatus.COMPLETED
                        ),
                        ExecutionDetailNode(
                            parentAgentId = "agent_seed_news_search",
                            parentTaskId = "task_seed_news",
                            type = DetailNodeType.RESULT,
                            title = "Synthesized top 5 technology breakthroughs",
                            status = StepExecutionStatus.COMPLETED
                        )
                    )
                )
            ),
            summaryResult = "Retrieved 5 breakthrough headlines across AI and quantum computing."
        )

        // Task 3: 🎵 YouTube — Search & Play Song
        val taskYouTube = ConversationTaskNode(
            id = "task_seed_yt",
            title = "YouTube — Search & Play Song",
            category = TaskCategory.MEDIA,
            icon = "🎵",
            commandPrompt = "/yt lofi hip hop radio - beats to relax/study to",
            interactionType = InteractionType.TEXT,
            executionType = ExecutionType.AGENT,
            status = AgentStatus.COMPLETED,
            isExpanded = false,
            agents = listOf(
                AgentExecutionNode(
                    id = "agent_seed_yt_browser",
                    parentTaskId = "task_seed_yt",
                    agentName = "Browser Agent",
                    icon = "🌐",
                    role = "Autonomous Web Controller",
                    status = AgentStatus.COMPLETED,
                    isExpanded = false,
                    steps = listOf(
                        ExecutionDetailNode(
                            parentAgentId = "agent_seed_yt_browser",
                            parentTaskId = "task_seed_yt",
                            type = DetailNodeType.STEP,
                            title = "Navigate to youtube.com and query 'lofi hip hop radio'",
                            status = StepExecutionStatus.COMPLETED
                        ),
                        ExecutionDetailNode(
                            parentAgentId = "agent_seed_yt_browser",
                            parentTaskId = "task_seed_yt",
                            type = DetailNodeType.TOOL_CALL,
                            title = "DOM Click on verified video element",
                            toolName = "BrowserController.clickElement",
                            status = StepExecutionStatus.COMPLETED
                        ),
                        ExecutionDetailNode(
                            parentAgentId = "agent_seed_yt_browser",
                            parentTaskId = "task_seed_yt",
                            type = DetailNodeType.RESULT,
                            title = "Playback engaged: Lofi Girl 24/7 Live Stream",
                            status = StepExecutionStatus.COMPLETED
                        )
                    )
                )
            ),
            summaryResult = "Now playing: Lofi Girl 24/7 Live Stream."
        )

        _tasks.value = listOf(taskCoding, taskNews, taskYouTube)
    }

    companion object {
        val global: ConversationTreeManager by lazy { ConversationTreeManager() }
    }
}
