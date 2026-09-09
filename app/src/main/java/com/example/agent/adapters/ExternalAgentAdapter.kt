package com.example.agent.adapters

import com.example.agent.core.Agent
import com.example.agent.core.AgentCapability
import com.example.agent.core.AgentRequest
import com.example.agent.core.AgentResult

/**
 * Common base interface for adapters that bridge third-party external agent frameworks
 * into the standard GVONE Agent protocol.
 */
interface ExternalAgentAdapter : Agent {
    val externalEngineName: String
    val externalVersion: String
    fun isConnected(): Boolean
}
