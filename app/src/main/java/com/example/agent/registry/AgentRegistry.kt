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
     * Resolves the single most suitable agent for a user goal using dynamic capability matching,
     * proficiencies, current load, and historical scorecard reliability.
     * Replaces hardcoded keyword heuristics with empirical multi-criteria routing.
     */
    fun findBestAgentForGoal(
        goal: String,
        requiredCapabilities: List<String> = emptyList(),
        requiredPermissions: List<String> = emptyList()
    ): Agent? {
        val availableAgents = agents.values.filter { !it.health().isQuarantined }
        if (availableAgents.isEmpty()) return null

        val goalTokens = goal.lowercase()
            .split("[\\W_]+".toRegex())
            .filter { it.length >= 2 }
            .toMutableSet()

        if (goal.trim().startsWith("/")) {
            val cmd = goal.trim().substring(1).substringBefore(" ").lowercase().trim()
            if (cmd.isNotEmpty()) goalTokens.add(cmd)
        }
        val tokens = goalTokens

        var bestAgent: Agent? = null
        var bestScore = -1.0

        for (agent in availableAgents) {
            val caps = agent.capabilities()
            var capabilityMatchCount = 0
            var maxProficiency = 0.5

            // Match identity tokens (e.g. VoiceAgent -> voice, agent)
            val idTokens = agent.identity()
                .split("(?=[A-Z])|[\\W_]+".toRegex())
                .map { it.lowercase().trim() }
                .filter { it.length >= 2 }
                .toSet()
            val idOverlap = tokens.intersect(idTokens).size
            if (idOverlap > 0) {
                capabilityMatchCount += idOverlap * 3
            }

            for (cap in caps) {
                // Check name, category, keywords, actions, and description
                val capTokens = (listOf(cap.name, cap.category, cap.description) + cap.keywords + cap.supportedActions)
                    .flatMap { it.lowercase().split("[\\W_]+".toRegex()) }
                    .filter { it.length >= 2 }
                    .toSet()

                val overlap = tokens.intersect(capTokens).size
                if (overlap > 0) {
                    capabilityMatchCount += overlap
                    if (cap.proficiency > maxProficiency) {
                        maxProficiency = cap.proficiency
                    }
                }
            }

            // Normalization: clamp match count to score between 0.0 and 1.0
            val capabilityMatchScore = (capabilityMatchCount * 0.25).coerceIn(0.0, 1.0)
            val scorecard = com.example.agent.core.ScorecardRegistry.global.getScorecard(agent.identity())

            val hasPerms = requiredPermissions.isEmpty() || agent.permissions.containsAll(requiredPermissions)

            val compositeScore = scorecard.computeScore(
                capabilityMatchScore = capabilityMatchScore,
                proficiency = maxProficiency,
                currentLoad = agent.currentLoad,
                maxConcurrency = agent.maxConcurrency,
                hasRequiredPermissions = hasPerms
            )

            if (compositeScore > bestScore) {
                bestScore = compositeScore
                bestAgent = agent
            }
        }

        return if (bestScore > 0.0) bestAgent else (getAgent("BrowserAgent") ?: availableAgents.firstOrNull())
    }

    /**
     * Backward-compatible router alias delegating directly to capability + scorecard engine.
     */
    fun routeGoalToAgent(goal: String): Agent? = findBestAgentForGoal(goal)

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
