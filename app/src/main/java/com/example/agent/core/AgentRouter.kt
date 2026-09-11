package com.example.agent.core

import com.example.agent.registry.AgentRegistry

/**
 * Intelligent Agent Router for GVONE OS.
 * Routes user intents, commands, and goals to the most qualified agent.
 */
class AgentRouter(
    private val registry: AgentRegistry = AgentRegistry.global
) {
    /**
     * Resolves the primary agent for a given user goal or command request.
     */
    fun routeIntent(goal: String, preferredAgent: String? = null): Agent {
        if (!preferredAgent.isNullOrBlank()) {
            val explicit = registry.getAgent(preferredAgent)
            if (explicit != null) return explicit
        }

        val trimmed = goal.trim()
        val lower = trimmed.lowercase()

        // 1. Direct command prefixes
        if (lower.startsWith("/voice") || lower.contains("voice interaction") || lower.contains("speak to me")) {
            registry.getAgent("VoiceAgent")?.let { return it }
        }
        if (lower.startsWith("/code") || lower.contains("code") || lower.contains("compile") || lower.contains("bug") || lower.contains("refactor")) {
            registry.getAgent("CodingAgent")?.let { return it }
        }
        if (lower.startsWith("/yt") || lower.contains("youtube") || lower.contains("browse") || lower.contains("web") || lower.contains("open url")) {
            registry.getAgent("BrowserAgent")?.let { return it }
        }
        if (lower.startsWith("/search") || lower.contains("search") || lower.contains("find out") || lower.contains("research")) {
            registry.getAgent("SearchAgent")?.let { return it }
        }
        if (lower.startsWith("pwd") || lower.startsWith("ls") || lower.startsWith("cat") || lower.contains("file") || lower.contains("folder")) {
            registry.getAgent("FileAgent")?.let { return it }
        }
        if (lower.startsWith("/") || lower.contains("command")) {
            registry.getAgent("CommandAgent")?.let { return it }
        }

        // 2. Capability index search
        val words = lower.split("\\s+".toRegex())
        for (word in words) {
            val matches = registry.findAgentsForCapability(word)
            if (matches.isNotEmpty()) {
                return matches.first()
            }
        }

        // 3. Fallback default
        return registry.getAgent("BrowserAgent")
            ?: registry.getAllAgents().firstOrNull()
            ?: throw AgentError("AgentRouter", "routeIntent", "No agents registered in GVONE OS", "Ensure agents are registered in AgentRegistry.")
    }

    /**
     * Calculates an ordered plan of agents for multi-agent workflows (Sequential or Handoff).
     */
    fun planMultiAgentExecution(goal: String): List<Agent> {
        val lower = goal.lowercase()
        val planned = mutableListOf<Agent>()

        if (lower.contains("compare") && (lower.contains("pdf") || lower.contains("file") || lower.contains("doc"))) {
            registry.getAgent("WebReviewAgent")?.let { planned.add(it) }
            registry.getAgent("FileAgent")?.let { planned.add(it) }
            registry.getAgent("CodingAgent")?.let { planned.add(it) }
        } else if (lower.contains("download") && lower.contains("extract")) {
            registry.getAgent("BrowserAgent")?.let { planned.add(it) }
            registry.getAgent("FileAgent")?.let { planned.add(it) }
        } else {
            planned.add(routeIntent(goal))
        }

        return planned.distinctBy { it.identity() }
    }

    companion object {
        val global: AgentRouter by lazy { AgentRouter() }
    }
}
