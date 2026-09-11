package com.example.agent.core

import com.example.agent.cns.CnsWorkflowResult

/**
 * Unified Agent Orchestrator interface.
 * Coordinates tasks, orchestrates multi-agent workflows, and dispatches requests
 * with safety and context isolation boundaries.
 */
interface AgentOrchestrator {
    suspend fun orchestrateGoal(userGoal: String): CnsWorkflowResult
    suspend fun dispatchToAgent(agentName: String, request: AgentRequest): AgentResult
    fun registerAgent(agent: Agent)
    fun getAgent(name: String): Agent?
    fun getAllAgents(): List<Agent>
}
