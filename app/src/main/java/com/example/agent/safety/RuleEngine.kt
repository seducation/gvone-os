package com.example.agent.safety

import com.example.agent.core.Agent
import com.example.agent.core.AgentRequest
import com.example.agent.core.RiskLevel
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Action triggered when a declarative rule matches.
 */
enum class RuleActionType {
    ROUTE_TO_AGENT,
    REQUIRE_PERMISSION,
    RETRY_WITH_BACKOFF,
    FALLBACK_AGENT,
    FALLBACK_WORKFLOW,
    ABORT_EXECUTION,
    REQUEST_USER_CONFIRMATION
}

/**
 * Declarative rule for routing, safety policies, retries, and fallbacks.
 */
data class RuleDefinition(
    val ruleId: String,
    val name: String,
    val description: String,
    val priority: Int = 100, // Higher number = higher evaluation priority
    val condition: (context: RuleEvaluationContext) -> Boolean,
    val actionType: RuleActionType,
    val targetAgent: String? = null,
    val targetTool: String? = null,
    val requiredPermission: String? = null,
    val fallbackTarget: String? = null
)

/**
 * Context evaluated by rules.
 */
data class RuleEvaluationContext(
    val goal: String,
    val caller: String = "User",
    val candidateAgent: String? = null,
    val toolName: String? = null,
    val failureCount: Int = 0,
    val isTimeout: Boolean = false,
    val permissionsAvailable: Set<String> = emptySet(),
    val metadata: Map<String, Any?> = emptyMap()
)

/**
 * Result of evaluating rules.
 */
data class RuleEvaluationResult(
    val matchedRule: RuleDefinition?,
    val actionType: RuleActionType?,
    val targetAgent: String?,
    val requiredPermission: String?,
    val fallbackTarget: String?,
    val allowed: Boolean = true
)

/**
 * Declarative Rule Engine for GVONE OS.
 */
class RuleEngine {
    private val rules = CopyOnWriteArrayList<RuleDefinition>()

    init {
        registerDefaultRules()
    }

    fun registerRule(rule: RuleDefinition) {
        rules.add(rule)
        rules.sortByDescending { it.priority }
    }

    fun unregisterRule(ruleId: String) {
        rules.removeAll { it.ruleId == ruleId }
    }

    /**
     * Evaluates all registered rules in priority order against a given context.
     */
    fun evaluate(context: RuleEvaluationContext): RuleEvaluationResult {
        for (rule in rules) {
            if (rule.condition(context)) {
                return RuleEvaluationResult(
                    matchedRule = rule,
                    actionType = rule.actionType,
                    targetAgent = rule.targetAgent,
                    requiredPermission = rule.requiredPermission,
                    fallbackTarget = rule.fallbackTarget,
                    allowed = rule.actionType != RuleActionType.ABORT_EXECUTION
                )
            }
        }
        return RuleEvaluationResult(
            matchedRule = null,
            actionType = null,
            targetAgent = null,
            requiredPermission = null,
            fallbackTarget = null,
            allowed = true
        )
    }

    private fun registerDefaultRules() {
        // Rule 1: Prevent execution if danger keywords detected without permission
        registerRule(
            RuleDefinition(
                ruleId = "rule_danger_shell",
                name = "Shell Dangerous Command Block",
                description = "Block destructive shell invocations without explicit confirmation",
                priority = 1000,
                condition = { ctx ->
                    val cmd = ctx.metadata["command"]?.toString()?.lowercase() ?: ctx.goal.lowercase()
                    cmd.contains("rm -rf /") || cmd.contains("format drive") || cmd.contains(":(){ :|:& };:")
                },
                actionType = RuleActionType.ABORT_EXECUTION
            )
        )

        // Rule 2: If failure count exceeds threshold, trigger fallback agent
        registerRule(
            RuleDefinition(
                ruleId = "rule_fallback_on_consecutive_failures",
                name = "Fallback on Repeated Failure",
                description = "If candidate agent failed more than 2 times, redirect to general browser agent",
                priority = 800,
                condition = { ctx -> ctx.failureCount >= 3 },
                actionType = RuleActionType.FALLBACK_AGENT,
                fallbackTarget = "BrowserAgent"
            )
        )

        // Rule 3: High risk tool requires user confirmation
        registerRule(
            RuleDefinition(
                ruleId = "rule_high_risk_tool",
                name = "High Risk Tool Confirmation",
                description = "Prompt user before executing high risk tool",
                priority = 900,
                condition = { ctx ->
                    ctx.toolName == "ShellTool" && !ctx.permissionsAvailable.contains(PermissionSystem.PERM_SHELL_EXECUTE)
                },
                actionType = RuleActionType.REQUIRE_PERMISSION,
                requiredPermission = PermissionSystem.PERM_SHELL_EXECUTE
            )
        )
    }

    companion object {
        val global: RuleEngine by lazy { RuleEngine() }
    }
}
