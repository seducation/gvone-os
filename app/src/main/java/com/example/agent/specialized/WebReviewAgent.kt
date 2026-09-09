package com.example.agent.specialized

import com.example.agent.browser.BrowserController
import com.example.agent.core.*

/**
 * WebReviewAgent inspects, extracts, and critiques live web page contents.
 * Supports document-to-web comparison workflows when coordinated by the CNS.
 */
class WebReviewAgent(
    private val browserController: BrowserController,
    logger: StepLogger = StepLogger.global
) : AgentBase(name = "WebReviewAgent", logger = logger) {

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "web_review",
            description = "Extract and review structure, key claims, and summary of current webpage",
            supportedActions = listOf("review_page", "extract_claims", "compare_source"),
            riskLevel = RiskLevel.LOW
        )
    )

    override fun observe(): Map<String, Any?> = mapOf(
        "activeUrl" to (browserController.browserState.value.currentUrl ?: "")
    )

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        val startTime = System.currentTimeMillis()

        return when (request.action) {
            "review_page" -> {
                executeAction(StepType.ANALYZE, "Review current webpage content") {
                    val pageText = browserController.getPageText() ?: "No content available on page."
                    val title = browserController.getTitle() ?: "Untitled Page"
                    val url = browserController.getCurrentUrl() ?: ""

                    // Extract key headings or paragraphs
                    val paragraphs = pageText.split("\n\n").filter { it.isNotBlank() }
                    val summary = paragraphs.take(3).joinToString("\n")

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "title" to title,
                            "url" to url,
                            "totalParagraphs" to paragraphs.size,
                            "summary" to summary,
                            "fullTextLength" to pageText.length
                        ),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = url, agentName = name)
                    )
                }
            }

            "extract_claims" -> {
                executeAction(StepType.EXTRACT, "Extract verifiable claims from webpage") {
                    val pageText = browserController.getPageText() ?: ""
                    val sentences = pageText.split(Regex("[.!?]\\s+")).filter { it.length > 20 }
                    val claims = sentences.take(5)

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "claimsCount" to claims.size,
                            "claims" to claims
                        ),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            else -> {
                AgentResult(
                    requestId = request.requestId,
                    status = AgentStatus.FAILED,
                    error = "Unsupported action '${request.action}' for WebReviewAgent"
                )
            }
        }
    }
}
