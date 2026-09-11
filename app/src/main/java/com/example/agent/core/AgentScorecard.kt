package com.example.agent.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Agent Scorecard tracking empirical reliability and performance metrics.
 * Influences dynamic capability routing (e.g. Agent A vs Agent B).
 */
data class AgentScorecard(
    val agentName: String,
    var successCount: Int = 0,
    var failureCount: Int = 0,
    var timeoutCount: Int = 0,
    var cancelCount: Int = 0,
    var totalExecutionTimeMs: Long = 0L,
    private val recentOutcomes: MutableList<Boolean> = mutableListOf()
) {
    val totalExecutions: Int
        get() = successCount + failureCount + timeoutCount + cancelCount

    val successRate: Double
        get() = if (totalExecutions == 0) 1.0 else successCount.toDouble() / totalExecutions.toDouble()

    val averageExecutionTimeMs: Long
        get() = if (successCount == 0) 500L else totalExecutionTimeMs / successCount.coerceAtLeast(1)

    val reliability: Double
        get() {
            if (totalExecutions == 0) return 1.0
            // Recent performance weighted heavily
            val recentWeight = if (recentOutcomes.isNotEmpty()) {
                recentOutcomes.takeLast(10).count { it }.toDouble() / recentOutcomes.takeLast(10).size.toDouble()
            } else 1.0
            return (successRate * 0.4) + (recentWeight * 0.6)
        }

    @Synchronized
    fun recordSuccess(durationMs: Long) {
        successCount++
        totalExecutionTimeMs += durationMs
        recentOutcomes.add(true)
        if (recentOutcomes.size > 50) recentOutcomes.removeAt(0)
    }

    @Synchronized
    fun recordFailure(durationMs: Long = 0L, isTimeout: Boolean = false, isCancel: Boolean = false) {
        if (isTimeout) {
            timeoutCount++
        } else if (isCancel) {
            cancelCount++
        } else {
            failureCount++
        }
        totalExecutionTimeMs += durationMs
        recentOutcomes.add(false)
        if (recentOutcomes.size > 50) recentOutcomes.removeAt(0)
    }

    /**
     * Compute composite routing score for a given task requirement:
     * - Capability match: 40%
     * - Proficiency: 20%
     * - Reliability (from scorecard): 20%
     * - Load penalty: up to -10%
     * - Tool/Permission readiness: 10%
     */
    fun computeScore(
        capabilityMatchScore: Double,
        proficiency: Double,
        currentLoad: Int,
        maxConcurrency: Int,
        hasRequiredPermissions: Boolean
    ): Double {
        if (capabilityMatchScore <= 0.0) return 0.0

        val loadFactor = (currentLoad.toDouble() / maxConcurrency.coerceAtLeast(1).toDouble()).coerceIn(0.0, 1.0)
        val permBonus = if (hasRequiredPermissions) 0.10 else 0.0
        val baseScore = (capabilityMatchScore * 0.40) +
                (proficiency * 0.20) +
                (reliability * 0.20) +
                permBonus -
                (loadFactor * 0.10)
        return baseScore.coerceIn(0.0, 1.0)
    }
}

/**
 * Global repository for agent scorecards.
 */
class ScorecardRegistry {
    private val scorecards = ConcurrentHashMap<String, AgentScorecard>()

    fun getScorecard(agentName: String): AgentScorecard {
        return scorecards.computeIfAbsent(agentName) {
            AgentScorecard(agentName = it)
        }
    }

    fun recordSuccess(agentName: String, durationMs: Long) {
        getScorecard(agentName).recordSuccess(durationMs)
    }

    fun recordFailure(agentName: String, durationMs: Long = 0L, isTimeout: Boolean = false, isCancel: Boolean = false) {
        getScorecard(agentName).recordFailure(durationMs, isTimeout, isCancel)
    }

    fun getAllScorecards(): Map<String, AgentScorecard> = scorecards.toMap()

    fun clear() {
        scorecards.clear()
    }

    companion object {
        val global: ScorecardRegistry by lazy { ScorecardRegistry() }
    }
}
