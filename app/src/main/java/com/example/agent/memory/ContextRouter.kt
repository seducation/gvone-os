package com.example.agent.memory

import com.example.agent.runtime.AgentTask
import com.example.agent.runtime.RuntimeMode
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory scope classification to enforce strict context boundaries.
 */
enum class ContextScope {
    GLOBAL,
    SESSION,
    CONVERSATION,
    TASK
}

/**
 * Isolated Task Execution Context.
 * Prevents Task A's intermediate scratchpad and tool observations from bleeding into Task B or conversational voice chats.
 */
data class TaskContext(
    val taskId: String,
    val goal: String,
    val agentName: String,
    val scratchpad: MutableMap<String, Any?> = ConcurrentHashMap(),
    val stepHistory: MutableList<String> = mutableListOf(),
    val toolOutputs: MutableList<String> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Conversation Context for conversational chat/voice interactions.
 */
data class ConversationMessage(
    val role: String, // "user", "assistant", "system"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * ContextRouter ensures clean isolation between conversational interactions and autonomous task executions.
 */
class ContextRouter(
    private val memory: AgentMemory = AgentMemory.global
) {
    private val taskContexts = ConcurrentHashMap<String, TaskContext>()
    private val conversationHistory = mutableListOf<ConversationMessage>()
    private val globalPreferences = ConcurrentHashMap<String, String>()

    init {
        globalPreferences["system_name"] = "GVONE OS"
        globalPreferences["browser_engine"] = "Chromium"
        globalPreferences["version"] = "2.0.0"

        if (conversationHistory.isEmpty()) {
            conversationHistory.add(ConversationMessage("user", "Hello! What autonomous agent tools are available in GVONE OS?", System.currentTimeMillis() - 3600000))
            conversationHistory.add(ConversationMessage("assistant", "GVONE OS includes specialized agents for Coding, Browser automation, File I/O, and Voice interaction, all orchestrated through the Central Nervous System.", System.currentTimeMillis() - 3500000))
            conversationHistory.add(ConversationMessage("user", "How can I trigger an autonomous task in the terminal?", System.currentTimeMillis() - 1800000))
            conversationHistory.add(ConversationMessage("assistant", "You can type '/agent <goal>' to dispatch a multi-step task, or '/voice' to enter continuous speech conversational mode.", System.currentTimeMillis() - 1700000))
        }
    }

    /**
     * Creates or retrieves an isolated TaskContext for a specific task.
     */
    fun getOrCreateTaskContext(task: AgentTask): TaskContext {
        return taskContexts.computeIfAbsent(task.taskId) {
            TaskContext(
                taskId = task.taskId,
                goal = task.goal,
                agentName = task.agentName
            )
        }
    }

    /**
     * Gets existing task context or null if completed/archived.
     */
    fun getTaskContext(taskId: String): TaskContext? = taskContexts[taskId]

    /**
     * Adds an observation or tool result to the isolated task context.
     */
    fun recordTaskObservation(taskId: String, stepInfo: String, toolOutput: String) {
        val ctx = taskContexts[taskId] ?: return
        ctx.stepHistory.add(stepInfo)
        ctx.toolOutputs.add(toolOutput)
    }

    /**
     * Records a chat/voice conversational exchange.
     */
    @Synchronized
    fun recordConversation(role: String, text: String) {
        conversationHistory.add(ConversationMessage(role, text))
        if (conversationHistory.size > 100) {
            conversationHistory.removeAt(0)
        }
    }

    /**
     * Finalizes and archives an isolated task context when completed or cancelled.
     * Prevents any scratchpad data from bleeding into future conversational turns.
     */
    fun archiveTaskContext(taskId: String, outcome: String, isSuccess: Boolean) {
        val ctx = taskContexts.remove(taskId)
        if (ctx != null) {
            memory.recordEpisode(
                task = ctx.goal,
                agent = ctx.agentName,
                outcome = outcome,
                success = isSuccess
            )
        }
    }

    /**
     * Formulates the exact filtered context appropriate for an agent execution.
     * Guarantees other tasks' context is NOT included.
     */
    fun assembleAgentContext(
        taskId: String?,
        currentUrl: String? = null,
        mode: RuntimeMode? = null
    ): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        // 1. Global context
        result["global"] = mapOf(
            "os" to "GVONE OS",
            "mode" to (mode?.toPromptLabel() ?: "text:chat")
        )

        // 2. Session / Browser state
        if (currentUrl != null) {
            result["session"] = mapOf("currentUrl" to currentUrl)
        }

        // 3. Isolated Task context (ONLY for this specific taskId)
        if (taskId != null) {
            val taskCtx = taskContexts[taskId]
            if (taskCtx != null) {
                result["task"] = mapOf(
                    "taskId" to taskCtx.taskId,
                    "goal" to taskCtx.goal,
                    "agentName" to taskCtx.agentName,
                    "steps" to taskCtx.stepHistory.toList(),
                    "recentToolOutputs" to taskCtx.toolOutputs.takeLast(5)
                )
            }
        }

        return result
    }

    /**
     * Formulates conversation-only context for chat or voice interaction.
     * Explicitly omits deep task execution scratchpads.
     */
    @Synchronized
    fun assembleConversationContext(limit: Int = 10): List<ConversationMessage> {
        return conversationHistory.takeLast(limit)
    }

    @Synchronized
    fun getAllConversationMessages(): List<ConversationMessage> {
        return conversationHistory.toList()
    }

    @Synchronized
    fun clearConversationHistory() {
        conversationHistory.clear()
    }

    /**
     * Session context for persistent session variables.
     */
    data class SessionContext(
        val sessionId: String,
        val sessionVariables: MutableMap<String, String> = ConcurrentHashMap()
    )

    private val sessionContexts = ConcurrentHashMap<String, SessionContext>()

    fun getOrCreateSessionContext(sessionId: String = "default_session"): SessionContext {
        return sessionContexts.computeIfAbsent(sessionId) {
            SessionContext(sessionId)
        }
    }

    fun getActiveTaskContextsCount(): Int = taskContexts.size

    fun resetAllScopes() {
        taskContexts.clear()
        conversationHistory.clear()
        sessionContexts.clear()
    }

    companion object {
        val global: ContextRouter by lazy { ContextRouter() }
    }
}
