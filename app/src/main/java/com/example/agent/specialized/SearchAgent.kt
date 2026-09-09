package com.example.agent.specialized

import com.example.agent.core.*
import com.example.data.ai.GVONEAIService

/**
 * SearchAgent executes web searches and synthesizes intelligent answers
 * using GVONE's Gemini AI Service and Tor privacy network.
 */
class SearchAgent(
    private val aiService: GVONEAIService,
    logger: StepLogger = StepLogger.global
) : AgentBase(name = "SearchAgent", logger = logger) {

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "web_search",
            description = "Perform AI-synthesized search across the open web",
            supportedActions = listOf("search", "synthesize_query"),
            riskLevel = RiskLevel.LOW
        )
    )

    override fun observe(): Map<String, Any?> = mapOf(
        "serviceReady" to true,
        "agent" to name
    )

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        val startTime = System.currentTimeMillis()
        val query = request.parameters["query"] as? String
            ?: return AgentResult(
                requestId = request.requestId,
                status = AgentStatus.FAILED,
                error = "Missing 'query' parameter"
            )

        return executeAction(StepType.FETCH, "Search and synthesize: '$query'") {
            val searchResult = aiService.searchAndSynthesize(query)
            AgentResult(
                requestId = request.requestId,
                status = AgentStatus.COMPLETED,
                data = mapOf(
                    "query" to searchResult.query,
                    "answer" to searchResult.aiAnswer,
                    "takeaways" to searchResult.keyTakeaways,
                    "sourceCount" to searchResult.sources.size,
                    "sources" to searchResult.sources.map { it.url }
                ),
                durationMs = System.currentTimeMillis() - startTime,
                provenance = Provenance(
                    origin = "gvone://ai-search",
                    agentName = name,
                    confidence = 0.95
                )
            )
        }
    }
}
