package com.example.agent.core

import com.example.agent.registry.AgentRegistry

/**
 * Intelligent Agent Router for GVONE OS.
 * Routes user intents, commands, and goals to the most qualified agent based on
 * dynamic capability declarations, proficiencies, and scorecard reliability.
 * Strictly eliminates hard-coded keyword checks.
 */
class AgentRouter(
    private val registry: AgentRegistry = AgentRegistry.global
) {
    /**
     * Resolves the primary agent for a given user goal or command request.
     */
    fun routeIntent(goal: String, preferredAgent: String? = null): Agent {
        // 1. Explicit preferred agent
        if (!preferredAgent.isNullOrBlank()) {
            val explicit = registry.getAgent(preferredAgent)
            if (explicit != null && !explicit.health().isQuarantined) return explicit
        }

        val trimmed = goal.trim()

        // 2. Direct @AgentName prefix syntax
        if (trimmed.startsWith("@")) {
            val targetName = trimmed.substring(1).substringBefore(" ").trim()
            val explicit = registry.getAgent(targetName)
            if (explicit != null && !explicit.health().isQuarantined) return explicit
        }

        // 3. Dynamic capability + scorecard matching
        val bestAgent = registry.findBestAgentForGoal(trimmed)
        if (bestAgent != null) {
            return bestAgent
        }

        // 4. Fallback default
        return registry.getAgent("BrowserAgent")
            ?: registry.getAllAgents().firstOrNull()
            ?: throw AgentError("AgentRouter", "routeIntent", "No agents registered in GVONE OS", "Ensure agents are registered in AgentRegistry.")
    }

    /**
     * Calculates an ordered plan of agents for multi-agent workflows based on decomposed capabilities.
     */
    fun planMultiAgentExecution(goal: String): List<Agent> {
        val planned = mutableListOf<Agent>()
        val primary = routeIntent(goal)
        planned.add(primary)

        // Identify any supplementary agents required based on capabilities
        val words = goal.lowercase().split("\\W+".toRegex()).filter { it.length > 3 }
        for (word in words) {
            val candidates = registry.findAgentsForCapability(word)
            for (candidate in candidates) {
                if (!planned.any { it.identity() == candidate.identity() } && !candidate.health().isQuarantined) {
                    planned.add(candidate)
                }
            }
        }

        return planned.distinctBy { it.identity() }
    }

    companion object {
        val global: AgentRouter by lazy { AgentRouter() }
    }
}
