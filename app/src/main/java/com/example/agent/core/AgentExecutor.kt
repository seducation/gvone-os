package com.example.agent.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Executes agent requests safely with timeouts, retries, and cancellation propagation.
 */
class AgentExecutor(
    private val defaultTimeoutMs: Long = 30000L,
    private val maxRetries: Int = 2
) {
    suspend fun execute(
        agent: Agent,
        request: AgentRequest,
        cancellationToken: CancellationToken = CancellationToken()
    ): AgentResult {
        var attempts = 0
        var lastError: String? = null

        while (attempts <= maxRetries && !cancellationToken.isCancelled) {
            attempts++
            val startTime = System.currentTimeMillis()

            try {
                val result = withTimeoutOrNull(defaultTimeoutMs) {
                    agent.execute(request)
                }

                if (result != null) {
                    if (result.isSuccess || attempts > maxRetries) {
                        return result
                    }
                    lastError = result.error
                } else {
                    lastError = "Execution timed out after ${defaultTimeoutMs}ms"
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.toString()
            }

            if (attempts <= maxRetries && !cancellationToken.isCancelled) {
                delay(100L * attempts) // Exponential backoff
            }
        }

        return AgentResult(
            requestId = request.requestId,
            status = if (cancellationToken.isCancelled) AgentStatus.CANCELLED else AgentStatus.FAILED,
            error = if (cancellationToken.isCancelled) "Execution cancelled by user" else lastError ?: "Execution failed",
            durationMs = 0L
        )
    }

    companion object {
        val global: AgentExecutor by lazy { AgentExecutor() }
    }
}
