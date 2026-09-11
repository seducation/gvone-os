package com.example.agent.core

import com.example.agent.registry.AgentRegistry

/**
 * Factory for creating, configuring, and registering Agents from AgentDefinitions.
 */
class AgentFactory(
    private val registry: AgentRegistry = AgentRegistry.global
) {
    /**
     * Builds and registers a standard Agent from a definition.
     */
    fun createAndRegister(
        definition: AgentDefinition,
        customExecute: (suspend (request: AgentRequest, token: CancellationToken) -> AgentResult)? = null
    ): Agent {
        val agent = object : AgentBase(definition.name) {
            override fun identity(): String = definition.name

            override fun capabilities(): List<AgentCapability> = definition.capabilities

            override fun observe(): Map<String, Any?> = mapOf(
                "id" to definition.id,
                "model" to definition.model,
                "contextPolicy" to definition.contextPolicy.name,
                "memoryPolicy" to definition.memoryPolicy.name,
                "status" to status().name
            )

            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
                return if (customExecute != null) {
                    customExecute(request, token)
                } else {
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = "Executed '${request.action}' on ${definition.name} (${definition.model})"
                    )
                }
            }
        }

        registry.registerAgent(agent)
        return agent
    }

    companion object {
        val global: AgentFactory by lazy { AgentFactory() }
    }
}
