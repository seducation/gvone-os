package com.example.agent.registry

import com.example.agent.core.Agent
import com.example.agent.core.AgentCapability
import com.example.agent.core.AgentHealth
import com.example.agent.core.AgentRequest
import com.example.agent.core.AgentResult
import com.example.agent.core.AgentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Central Agent Registry for GVONE OS.
 * Manages dynamic agent discovery, capability indexing, and intelligent delegation.
 */
class AgentRegistry {

    private val agents = ConcurrentHashMap<String, Agent>()
    private val capabilityIndex = ConcurrentHashMap<String, MutableSet<String>>()

    private val _registeredAgentNames = MutableStateFlow<List<String>>(emptyList())
    val registeredAgentNames: StateFlow<List<String>> = _registeredAgentNames.asStateFlow()

    fun registerAgent(agent: Agent) {
        val name = agent.identity()
        agents[name] = agent

        // Index capabilities
        for (cap in agent.capabilities()) {
            val key = cap.name.lowercase()
            capabilityIndex.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(name)
            for (act in cap.supportedActions) {
                capabilityIndex.computeIfAbsent(act.lowercase()) { ConcurrentHashMap.newKeySet() }.add(name)
            }
        }

        _registeredAgentNames.value = agents.keys.toList().sorted()
    }

    fun unregisterAgent(name: String) {
        agents.remove(name)
        capabilityIndex.values.forEach { it.remove(name) }
        _registeredAgentNames.value = agents.keys.toList().sorted()
    }

    fun getAgent(name: String): Agent? = agents[name]

    fun getAllAgents(): List<Agent> = agents.values.toList()

    /**
     * Finds all agents declaring support for a specific action or capability.
     */
    fun findAgentsForCapability(capabilityOrAction: String): List<Agent> {
        val matchingNames = capabilityIndex[capabilityOrAction.lowercase()] ?: emptySet()
        return matchingNames.mapNotNull { agents[it] }
    }

    /**
     * Resolves the single most suitable agent for a user goal.
     */
    fun routeGoalToAgent(goal: String): Agent? {
        val lower = goal.lowercase()
        return when {
            lower.contains("voice") || lower.contains("speak") || lower.contains("listen") ->
                getAgent("VoiceAgent")

            lower.contains("command") || lower.startsWith("/") ->
                getAgent("CommandAgent")

            lower.contains("browse") || lower.contains("youtube") || lower.contains("web") ||
                    lower.contains("click") || lower.contains("page") || lower.contains("url") ->
                getAgent("BrowserAgent")

            lower.contains("code") || lower.contains("kotlin") || lower.contains("script") ||
                    lower.contains("bug") || lower.contains("refactor") ->
                getAgent("CodingAgent")

            lower.contains("file") || lower.contains("directory") || lower.contains("folder") ||
                    lower.contains("read") || lower.contains("write") ->
                getAgent("FileAgent")

            lower.contains("search") || lower.contains("find") || lower.contains("lookup") ->
                getAgent("SearchAgent")

            else -> getAgent("BrowserAgent") ?: agents.values.firstOrNull()
        }
    }

    /**
     * Gathers collective health of all agents in the system.
     */
    fun getSystemHealth(): List<AgentHealth> = agents.values.map { it.health() }

    /**
     * Dispatches request to target agent.
     */
    suspend fun dispatch(agentName: String, request: AgentRequest): AgentResult {
        val agent = agents[agentName]
            ?: return AgentResult(
                requestId = request.requestId,
                status = AgentStatus.FAILED,
                error = "Agent '$agentName' not found in registry"
            )
        return agent.execute(request)
    }

    companion object {
        val global: AgentRegistry by lazy { AgentRegistry() }
    }
}
