package com.example.agent.rules

import com.example.agent.core.StepLogger
import com.example.agent.core.StepStatus
import com.example.agent.core.StepType
import com.example.agent.safety.PermissionSystem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Result of resolving matched rules to determine the winning rule and any conflicts.
 */
data class RuleResolutionResult(
    val winningRule: RuleDefinition?,
    val winningConditionResult: ConditionEvaluationResult?,
    val allMatches: List<Pair<RuleDefinition, ConditionEvaluationResult>>,
    val conflict: RuleConflictReport?
)

/**
 * Validation result for rule definitions.
 */
data class RuleValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

/**
 * Production-grade Declarative Rule Engine for GVONE OS.
 * Acts as the sovereign decision and orchestration layer between Commands, Nodal graphs,
 * Agents, Tools, Memory, Context, and Safety.
 */
class RuleEngine(
    private val conditionEvaluator: ConditionEvaluator = ConditionEvaluator.global,
    private val actionRegistry: RuleActionRegistry = RuleActionRegistry.global,
    private val logger: StepLogger = StepLogger.global
) {
    private val rulesMap = ConcurrentHashMap<String, RuleDefinition>()
    // Index by event trigger for fast lookup
    private val triggerIndex = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()

    private val _rulesState = MutableStateFlow<List<RuleDefinition>>(emptyList())
    val rulesState: StateFlow<List<RuleDefinition>> = _rulesState.asStateFlow()
    val rules: StateFlow<List<RuleDefinition>> get() = _rulesState.asStateFlow()

    // Recent evaluation trace history for explainability and observability
    private val _recentTraces = CopyOnWriteArrayList<RuleEvaluationTrace>()
    val recentTraces: List<RuleEvaluationTrace> get() = _recentTraces.takeLast(50)
    private val _tracesFlow = MutableStateFlow<List<RuleEvaluationTrace>>(emptyList())
    val tracesFlow: StateFlow<List<RuleEvaluationTrace>> get() = _tracesFlow.asStateFlow()

    fun getRules(): List<RuleDefinition> = getAllRules()

    // Loop protection: maximum rule evaluation depth per execution trace
    private val maxDepth = 15
    private val executionVisitedRules = ConcurrentHashMap<String, MutableSet<String>>()

    init {
        registerDefaultRules()
    }

    /**
     * Registers a declarative rule and updates indices.
     */
    fun registerRule(rule: RuleDefinition) {
        rulesMap[rule.id] = rule
        indexRule(rule)
        updateState()
    }

    /**
     * Unregisters a rule by ID.
     */
    fun unregisterRule(ruleId: String) {
        val removed = rulesMap.remove(ruleId)
        if (removed != null) {
            deindexRule(removed)
            updateState()
        }
    }

    fun getRule(ruleId: String): RuleDefinition? = rulesMap[ruleId]

    fun getAllRules(): List<RuleDefinition> = rulesMap.values.sortedByDescending { it.priority }

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        val rule = rulesMap[ruleId] ?: return
        registerRule(rule.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
    }

    /**
     * Promotes a chat-specific rule to global so it appears and acts in all chats.
     */
    fun promoteToGlobal(ruleId: String): RuleDefinition? {
        val rule = rulesMap[ruleId] ?: return null
        val updated = rule.copy(
            scope = "GLOBAL",
            chatId = null,
            updatedAt = System.currentTimeMillis()
        )
        registerRule(updated)
        return updated
    }

    /**
     * Attaches or clones a rule for a specific chat.
     */
    fun attachRuleToChat(ruleId: String, targetChatId: String): RuleDefinition? {
        val rule = rulesMap[ruleId] ?: return null
        val newId = "${rule.id}_chat_${targetChatId.takeLast(6)}"
        val chatScoped = rule.copy(
            id = newId,
            scope = "CHAT",
            chatId = targetChatId,
            updatedAt = System.currentTimeMillis()
        )
        registerRule(chatScoped)
        return chatScoped
    }

    /**
     * Returns rules filtered by scope ("GLOBAL", "CHAT", or "ALL") and optional chatId.
     */
    fun getRulesForScope(scope: String, targetChatId: String? = null): List<RuleDefinition> {
        val all = getAllRules()
        return when (scope.uppercase()) {
            "GLOBAL" -> all.filter { it.scope.equals("GLOBAL", ignoreCase = true) }
            "CHAT" -> {
                if (targetChatId != null) {
                    all.filter { it.scope.equals("CHAT", ignoreCase = true) && it.chatId.equals(targetChatId, ignoreCase = true) }
                } else {
                    all.filter { it.scope.equals("CHAT", ignoreCase = true) }
                }
            }
            "GEMS" -> all.filter { it.isGem }
            else -> all
        }
    }

    /**
     * Validates a rule's syntax and integrity before registration.
     */
    fun validateRule(rule: RuleDefinition): RuleValidationResult {
        val errors = mutableListOf<String>()
        if (rule.id.isBlank()) errors.add("Rule ID cannot be blank")
        if (rule.name.isBlank()) errors.add("Rule Name cannot be blank")
        if (rule.actions.isEmpty() && rule.onSuccess.isEmpty() && rule.onFailure.isEmpty()) {
            errors.add("Rule must define at least one action, onSuccess, or onFailure")
        }
        return RuleValidationResult(errors.isEmpty(), errors)
    }

    /**
     * Evaluates all applicable rules against the context, resolves the winning rule,
     * checks for conflicts, and logs an explainable execution trace.
     */
    fun evaluate(context: RuleContext): RuleEvaluationTrace {
        val startTime = System.currentTimeMillis()
        val execId = context.executionId

        // Cycle / Loop protection
        val visited = executionVisitedRules.computeIfAbsent(execId) { mutableSetOf() }
        if (context.executionState.depth >= maxDepth) {
            val haltTrace = RuleEvaluationTrace(
                executionId = execId,
                event = context.event,
                evaluatedCount = 0,
                matchedRules = emptyList(),
                selectedRule = null,
                conflict = null,
                executedActions = listOf(
                    ActionExecutionResult(
                        actionType = RuleActionTypes.STOP_EXECUTION,
                        success = false,
                        error = "Loop protection triggered: Maximum rule evaluation depth ($maxDepth) reached"
                    )
                ),
                durationMs = System.currentTimeMillis() - startTime,
                explainabilitySummary = "Halted: Exceeded maximum rule recursion depth"
            )
            recordTrace(haltTrace)
            return haltTrace
        }

        val candidateRules = getCandidateRulesForEvent(context.event)
        val matchResults = mutableListOf<Pair<RuleDefinition, ConditionEvaluationResult>>()
        val matchTraces = mutableListOf<RuleMatchTrace>()

        for (rule in candidateRules) {
            if (!rule.enabled) continue

            // Scope filter: If rule is CHAT specific, check if context matches that chat
            if (rule.scope.equals("CHAT", ignoreCase = true) && !rule.chatId.isNullOrBlank()) {
                val ctxChatId = context.chatId ?: context.request.chatId ?: context.request.taskId
                if (ctxChatId != null && !ctxChatId.equals(rule.chatId, ignoreCase = true)) {
                    continue
                }
            }

            // Trigger-level filter check if specified
            if (rule.trigger.filter.isNotEmpty()) {
                val filterPassed = rule.trigger.filter.all { (key, expected) ->
                    context.resolvePath(key)?.toString().equals(expected, ignoreCase = true)
                }
                if (!filterPassed) continue
            }

            val evalRes = conditionEvaluator.evaluate(rule.conditions, context)
            matchTraces.add(
                RuleMatchTrace(
                    ruleId = rule.id,
                    ruleName = rule.name,
                    priority = rule.priority,
                    matched = evalRes.matched,
                    specificity = evalRes.specificity,
                    reason = evalRes.reason
                )
            )

            if (evalRes.matched) {
                matchResults.add(Pair(rule, evalRes))
            }
        }

        val resolution = resolveMatches(matchResults)
        val winningRule = resolution.winningRule

        if (winningRule != null) {
            visited.add(winningRule.id)
        }

        val summary = buildString {
            if (winningRule != null) {
                append("Selected rule '${winningRule.id}' [priority=${winningRule.priority}] (${winningRule.name}). ")
                append("Matched ${resolution.allMatches.size} rule(s). ")
                if (resolution.conflict != null) {
                    append("Resolved conflict with [${resolution.conflict.conflictingRuleIds.joinToString()}]: ${resolution.conflict.resolutionReason}")
                }
            } else {
                append("No rules matched event '${context.event}'. Default behavior maintained.")
            }
        }.trim()

        val trace = RuleEvaluationTrace(
            executionId = execId,
            event = context.event,
            evaluatedCount = candidateRules.size,
            matchedRules = matchTraces.filter { it.matched },
            selectedRule = winningRule,
            conflict = resolution.conflict,
            executedActions = emptyList(), // Actions populated upon execution
            durationMs = System.currentTimeMillis() - startTime,
            explainabilitySummary = summary
        )

        recordTrace(trace)
        return trace
    }

    /**
     * Resolves multiple matching rules into a single winning rule using:
     * 1. Deterministic Priority (Highest number wins)
     * 2. Specificity score (More specific condition tree wins)
     * 3. Deterministic ID tie-breaker (Lexicographical ordering)
     * Also detects and logs conflicting rules (e.g. routing to different agents).
     */
    fun resolveMatches(matches: List<Pair<RuleDefinition, ConditionEvaluationResult>>): RuleResolutionResult {
        if (matches.isEmpty()) {
            return RuleResolutionResult(null, null, emptyList(), null)
        }

        if (matches.size == 1) {
            return RuleResolutionResult(matches.first().first, matches.first().second, matches, null)
        }

        // Sort by: priority DESC -> specificity DESC -> rule id ASC
        val sorted = matches.sortedWith(
            compareByDescending<Pair<RuleDefinition, ConditionEvaluationResult>> { it.first.priority }
                .thenByDescending { it.second.specificity }
                .thenBy { it.first.id }
        )

        val winner = sorted.first()
        val runnerUp = sorted[1]

        var conflict: RuleConflictReport? = null
        // Conflict detection: If runnerUp has identical priority and proposes different agent or action target
        val winnerAgent = winner.first.actions.find { it.type == RuleActionTypes.ROUTE_AGENT }?.target
        val runnerUpAgent = runnerUp.first.actions.find { it.type == RuleActionTypes.ROUTE_AGENT }?.target
        val hasConflictingRouting = winnerAgent != null && runnerUpAgent != null && winnerAgent != runnerUpAgent

        if (winner.first.priority == runnerUp.first.priority || hasConflictingRouting) {
            val reason = if (winner.first.priority > runnerUp.first.priority) {
                "Resolved by higher priority (${winner.first.priority} vs ${runnerUp.first.priority})"
            } else if (winner.second.specificity > runnerUp.second.specificity) {
                "Resolved by higher condition specificity (${winner.second.specificity} vs ${runnerUp.second.specificity})"
            } else {
                "Resolved via deterministic lexicographical tie-breaker ('${winner.first.id}' < '${runnerUp.first.id}')"
            }

            conflict = RuleConflictReport(
                conflictingRuleIds = sorted.take(3).map { it.first.id },
                winningRuleId = winner.first.id,
                winnerPriority = winner.first.priority,
                resolutionReason = reason
            )
            logger.logInstant("RuleEngine", StepType.DECIDE, "Rule conflict detected: $reason", StepStatus.SUCCESS)
        }

        return RuleResolutionResult(
            winningRule = winner.first,
            winningConditionResult = winner.second,
            allMatches = sorted,
            conflict = conflict
        )
    }

    /**
     * Executes actions of a rule with the ActionRegistry.
     */
    suspend fun executeActions(actions: List<RuleAction>, context: RuleContext): List<ActionExecutionResult> {
        val results = mutableListOf<ActionExecutionResult>()
        for (action in actions) {
            val res = actionRegistry.execute(action, context)
            results.add(res)
            if (!res.success && action.type == RuleActionTypes.STOP_EXECUTION) {
                break
            }
        }
        return results
    }

    /**
     * Full evaluate-and-execute cycle.
     */
    suspend fun evaluateAndExecute(context: RuleContext): Pair<RuleEvaluationTrace, List<ActionExecutionResult>> {
        val trace = evaluate(context)
        val winning = trace.selectedRule
        val actionResults = mutableListOf<ActionExecutionResult>()

        if (winning != null) {
            val mainResults = executeActions(winning.actions, context)
            actionResults.addAll(mainResults)

            val allSucceeded = mainResults.all { it.success }
            if (allSucceeded && winning.onSuccess.isNotEmpty()) {
                actionResults.addAll(executeActions(winning.onSuccess, context))
            } else if (!allSucceeded && winning.onFailure.isNotEmpty()) {
                actionResults.addAll(executeActions(winning.onFailure, context))
            }
        }

        val updatedTrace = trace.copy(executedActions = actionResults)
        recordTrace(updatedTrace)
        return Pair(updatedTrace, actionResults)
    }

    /**
     * Simulates rule evaluation in Dry Run mode without mutating system state or invoking external tools.
     */
    fun simulate(context: RuleContext): RuleSimulationResult {
        val candidateRules = getCandidateRulesForEvent(context.event)
        val matched = mutableListOf<Pair<RuleDefinition, ConditionEvaluationResult>>()

        for (rule in candidateRules) {
            if (!rule.enabled) continue
            val eval = conditionEvaluator.evaluate(rule.conditions, context)
            if (eval.matched) {
                matched.add(Pair(rule, eval))
            }
        }

        val resolution = resolveMatches(matched)
        val winning = resolution.winningRule

        val proposedAgent = winning?.actions?.find { it.type == RuleActionTypes.ROUTE_AGENT }?.target
            ?: winning?.actions?.find { it.type == RuleActionTypes.FALLBACK }?.target
            ?: context.activeAgent
            ?: "BrowserAgent"

        val proposedNode = winning?.actions?.find { it.type == RuleActionTypes.ROUTE_NODE }?.target

        val explanation = buildString {
            appendLine("=== RULE ENGINE DRY-RUN SIMULATION ===")
            appendLine("Event: ${context.event}")
            appendLine("Matched Rules (${matched.size}):")
            matched.forEachIndexed { i, (r, res) ->
                appendLine("  ${i + 1}. ${r.id} [P=${r.priority}, spec=${res.specificity}] - ${r.name}")
            }
            if (winning != null) {
                appendLine("\nSelected Rule: ${winning.id} (${winning.name})")
                appendLine("Target Agent:  $proposedAgent")
                if (proposedNode != null) appendLine("Target Node:   $proposedNode")
                appendLine("Proposed Actions (${winning.actions.size}):")
                winning.actions.forEach { act ->
                    appendLine("  • ${act.type}(${act.target ?: ""}) ${if (act.parameters.isNotEmpty()) act.parameters else ""}")
                }
                if (resolution.conflict != null) {
                    appendLine("\nConflict Resolution: ${resolution.conflict.resolutionReason}")
                }
            } else {
                appendLine("\nNo matching rules. Default fallback behavior would be used.")
            }
        }.trim()

        return RuleSimulationResult(
            executionId = context.executionId,
            event = context.event,
            matchedRules = matched.map { it.first },
            selectedRule = winning,
            selectedAgent = proposedAgent,
            selectedNode = proposedNode,
            proposedActions = winning?.actions ?: emptyList(),
            conflict = resolution.conflict,
            explanation = explanation
        )
    }

    private fun getCandidateRulesForEvent(event: String): List<RuleDefinition> {
        val eventRules = triggerIndex[event] ?: emptyList()
        val allEventRules = triggerIndex[RuleEvents.ALL] ?: emptyList()

        val combinedIds = (eventRules + allEventRules).distinct()
        return combinedIds.mapNotNull { rulesMap[it] }.sortedByDescending { it.priority }
    }

    private fun indexRule(rule: RuleDefinition) {
        val event = rule.trigger.event
        val list = triggerIndex.computeIfAbsent(event) { CopyOnWriteArrayList() }
        if (!list.contains(rule.id)) list.add(rule.id)
    }

    private fun deindexRule(rule: RuleDefinition) {
        triggerIndex[rule.trigger.event]?.remove(rule.id)
        triggerIndex[RuleEvents.ALL]?.remove(rule.id)
    }

    private fun updateState() {
        _rulesState.value = rulesMap.values.sortedByDescending { it.priority }
    }

    private fun recordTrace(trace: RuleEvaluationTrace) {
        _recentTraces.add(trace)
        if (_recentTraces.size > 100) {
            _recentTraces.removeAt(0)
        }
        _tracesFlow.value = _recentTraces.takeLast(50)
    }

    /**
     * Export all rules as JSON.
     */
    fun exportRulesJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        getAllRules().forEach { arr.put(it.toJson()) }
        root.put("rules", arr)
        return root.toString(2)
    }

    /**
     * Import rules from JSON string.
     */
    fun importRulesJson(jsonStr: String): Int {
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("rules") ?: return 0
        var count = 0
        for (i in 0 until arr.length()) {
            val r = RuleDefinition.fromJson(arr.getJSONObject(i))
            registerRule(r)
            count++
        }
        return count
    }

    /**
     * Export tabular representations for spreadsheet / n8n-style sheets.
     */
    fun exportTabularConfig(): List<Map<String, String>> =
        getAllRules().map { it.toTabularRow() }

    /**
     * Import tabular configuration rows.
     */
    fun importTabularConfig(rows: List<Map<String, String>>): Int {
        var count = 0
        for (row in rows) {
            val r = RuleDefinition.fromTabularRow(row)
            registerRule(r)
            count++
        }
        return count
    }

    /**
     * Seeds out-of-the-box system, safety, routing, handoff, and recovery rules.
     */
    private fun registerDefaultRules() {
        // 1. EMERGENCY: Destructive shell command block
        registerRule(
            RuleDefinition(
                id = "rule_danger_shell_halt",
                name = "Destructive Shell Invocations Block",
                description = "Halts destructive filesystem deletion or fork bombs",
                priority = RulePriority.EMERGENCY,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = SingleCondition(
                    field = "request.goal",
                    operator = ConditionOperator.REGEX,
                    value = "(rm\\s+-rf\\s+/|format\\s+drive|:[(][{]\\s+:[|]:[&]\\s+[}][)];:)"
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.STOP_EXECUTION, parameters = mapOf("reason" to "Destructive command pattern rejected by system emergency policy"))
                )
            )
        )

        // 2. SECURITY: High-risk tools require permission/confirmation
        registerRule(
            RuleDefinition(
                id = "rule_high_risk_tool_approval",
                name = "High Risk Tool Gate",
                description = "Requires explicit user confirmation for shell execution tool",
                priority = RulePriority.SECURITY,
                trigger = RuleTrigger(RuleEvents.TOOL_STARTED),
                conditions = CompositeCondition(
                    any = listOf(
                        SingleCondition("tool.name", ConditionOperator.EQUALS, "ShellTool"),
                        SingleCondition("tool.risk", ConditionOperator.EQUALS, "HIGH")
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.REQUEST_APPROVAL, parameters = mapOf("permission" to PermissionSystem.PERM_SHELL_EXECUTE))
                )
            )
        )

        // 3. EXPLICIT COMMAND: /voice on /agent or /voice /agent compound mode
        registerRule(
            RuleDefinition(
                id = "rule_cmd_voice_agent_compound",
                name = "Voice-First Agent Command Route",
                description = "Routes /voice /agent compound command to voice-first workflow",
                priority = RulePriority.COMMAND,
                trigger = RuleTrigger(RuleEvents.COMMAND_PARSED),
                conditions = SingleCondition("request.command", ConditionOperator.CONTAINS, "voice"),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "VoiceAgent"),
                    RuleAction(RuleActionTypes.SET_CONTEXT, target = "interaction_mode", parameters = mapOf("value" to "VOICE")),
                    RuleAction(RuleActionTypes.START_WORKFLOW, target = "wf_voice_agent_compound")
                )
            )
        )

        // 4. AGENT ROUTE: Coding intent -> CodingAgent
        registerRule(
            RuleDefinition(
                id = "rule_route_coding_agent",
                name = "Coding Task Intent Router",
                description = "Directs programming, compilation, and scripting tasks to CodingAgent",
                priority = RulePriority.AGENT_ROUTE,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = CompositeCondition(
                    any = listOf(
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "code"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "kotlin"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "script"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "syntax"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "compile"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "bug")
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "CodingAgent"),
                    RuleAction(RuleActionTypes.SET_CONTEXT, target = "execution_mode", parameters = mapOf("value" to "coding"))
                )
            )
        )

        // 5. AGENT ROUTE: Web & Document comparison -> Multi-Agent Handoff / Sequential Execution
        registerRule(
            RuleDefinition(
                id = "rule_compare_web_document",
                name = "Web & Document Comparison Router",
                description = "Routes cross-source comparison goals to WebReviewAgent followed by FileAgent",
                priority = RulePriority.AGENT_ROUTE + 10,
                trigger = RuleTrigger(RuleEvents.REQUEST_RECEIVED),
                conditions = CompositeCondition(
                    all = listOf(
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "compare"),
                        CompositeCondition(
                            any = listOf(
                                SingleCondition("request.goal", ConditionOperator.CONTAINS, "web"),
                                SingleCondition("request.goal", ConditionOperator.CONTAINS, "page"),
                                SingleCondition("request.goal", ConditionOperator.CONTAINS, "site")
                            )
                        ),
                        CompositeCondition(
                            any = listOf(
                                SingleCondition("request.goal", ConditionOperator.CONTAINS, "doc"),
                                SingleCondition("request.goal", ConditionOperator.CONTAINS, "file"),
                                SingleCondition("request.goal", ConditionOperator.CONTAINS, "pdf")
                            )
                        )
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "WebReviewAgent"),
                    RuleAction(
                        RuleActionTypes.HANDOFF,
                        target = "FileAgent",
                        parameters = mapOf(
                            "from" to "WebReviewAgent",
                            "to" to "FileAgent",
                            "include" to listOf("goal", "task", "webFindings")
                        )
                    ),
                    RuleAction(RuleActionTypes.SEQUENTIAL_EXECUTE, parameters = mapOf("pipeline" to listOf("WebReviewAgent", "FileAgent")))
                )
            )
        )

        // 6. AGENT ROUTE: Search / research intent -> SearchAgent
        registerRule(
            RuleDefinition(
                id = "rule_route_search_agent",
                name = "Search & Query Intent Router",
                description = "Directs query and lookup goals to SearchAgent",
                priority = RulePriority.AGENT_ROUTE,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = CompositeCondition(
                    any = listOf(
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "search"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "find"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "who is"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "what is")
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "SearchAgent"),
                    RuleAction(RuleActionTypes.START_WORKFLOW, target = "wf_search")
                )
            )
        )

        // 7. AGENT ROUTE: Browser inspection & navigation -> BrowserAgent
        registerRule(
            RuleDefinition(
                id = "rule_route_browser_agent",
                name = "Browser Interaction Router",
                description = "Directs DOM observation and browser navigation to BrowserAgent",
                priority = RulePriority.AGENT_ROUTE,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = CompositeCondition(
                    any = listOf(
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "browse"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "dom"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "tab"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "youtube"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "yt")
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "BrowserAgent")
                )
            )
        )

        // 8. RETRY RULE: Retry on tool failure when retry count is below threshold
        registerRule(
            RuleDefinition(
                id = "rule_network_timeout_retry",
                name = "Tool Failure Retry Policy",
                description = "Allows retries on transient tool failure or network timeout",
                priority = RulePriority.FALLBACK + 20,
                trigger = RuleTrigger(RuleEvents.TOOL_FAILED),
                conditions = CompositeCondition(
                    any = listOf(
                        SingleCondition("execution.retry_count", ConditionOperator.LESS_THAN, 3),
                        SingleCondition("tool.retry_count", ConditionOperator.LESS_THAN, 3),
                        SingleCondition("tool.error", ConditionOperator.CONTAINS, "timeout")
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.RETRY, parameters = mapOf("max_attempts" to 3))
                )
            )
        )

        // 9. FALLBACK: Fallback to BrowserAgent if primary agent fails repeatedly
        registerRule(
            RuleDefinition(
                id = "rule_fallback_consecutive_failures",
                name = "Fallback on Consecutive Agent Failures",
                description = "Switches to BrowserAgent when repeated failures occur",
                priority = RulePriority.FALLBACK,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = SingleCondition("execution.retry_count", ConditionOperator.GREATER_OR_EQUAL, 3),
                actions = listOf(
                    RuleAction(RuleActionTypes.FALLBACK, target = "BrowserAgent")
                )
            )
        )

        // 10. DEFAULT: Base general router
        registerRule(
            RuleDefinition(
                id = "rule_default_routing",
                name = "Default Browser Routing",
                description = "Fallback router to default BrowserAgent",
                priority = RulePriority.DEFAULT,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = SingleCondition("agent", ConditionOperator.EXISTS),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "BrowserAgent")
                )
            )
        )

        // 11. GEMINI GEMS (GLOBAL)
        registerRule(
            RuleDefinition(
                id = "gem_code_architect",
                name = "Code Architect Gem",
                description = "Senior Kotlin & Distributed Systems Architect. Enforces type safety and coroutines.",
                priority = RulePriority.AGENT_ROUTE + 5,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = CompositeCondition(
                    any = listOf(
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "architect"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "refactor")
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "CodingAgent"),
                    RuleAction(RuleActionTypes.SET_CONTEXT, target = "gem_active", parameters = mapOf("gem" to "CodeArchitect"))
                ),
                scope = "GLOBAL",
                isGem = true,
                gemIcon = "💎",
                gemInstructions = "Provide clean, type-safe Kotlin code using Jetpack Compose, StateFlow, and Coroutines. Never leave TODOs."
            )
        )

        registerRule(
            RuleDefinition(
                id = "gem_strict_guardian",
                name = "Strict Security Guardian Gem",
                description = "Zero-trust guardian. Blocks shell execution and unverified filesystem modifications.",
                priority = RulePriority.SECURITY - 5,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = CompositeCondition(
                    any = listOf(
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "guard"),
                        SingleCondition("request.goal", ConditionOperator.CONTAINS, "security"),
                        SingleCondition("tool.risk", ConditionOperator.EQUALS, "CRITICAL")
                    )
                ),
                actions = listOf(
                    RuleAction(RuleActionTypes.REQUEST_APPROVAL, parameters = mapOf("reason" to "Strict Security Guardian Gem zero-trust policy"))
                ),
                scope = "GLOBAL",
                isGem = true,
                gemIcon = "🛡️",
                gemInstructions = "Require explicit human confirmation before executing any shell script or destructive file mutation."
            )
        )

        registerRule(
            RuleDefinition(
                id = "gem_deep_researcher",
                name = "Deep Research Analyst Gem",
                description = "Fact-checking and web synthesis with structured comparative summaries.",
                priority = RulePriority.AGENT_ROUTE + 5,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = SingleCondition("request.goal", ConditionOperator.CONTAINS, "research"),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "SearchAgent"),
                    RuleAction(RuleActionTypes.SET_CONTEXT, target = "gem_active", parameters = mapOf("gem" to "DeepResearcher"))
                ),
                scope = "GLOBAL",
                isGem = true,
                gemIcon = "🌐",
                gemInstructions = "Synthesize web sources, verify claims against reliable pages, and output concise bulleted takeaways."
            )
        )

        registerRule(
            RuleDefinition(
                id = "gem_autonomous_lead",
                name = "Autonomous Lead Orchestrator Gem",
                description = "Autonomous task decomposition engine spanning multi-agent handoffs.",
                priority = RulePriority.AGENT_ROUTE + 8,
                trigger = RuleTrigger(RuleEvents.REQUEST_RECEIVED),
                conditions = SingleCondition("request.goal", ConditionOperator.CONTAINS, "auto"),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "WorkspaceAgent")
                ),
                scope = "GLOBAL",
                isGem = true,
                gemIcon = "⚡",
                gemInstructions = "Decompose ambiguous user instructions into distinct steps and orchestrate agent handoffs."
            )
        )

        // 12. CHAT-SPECIFIC GEMS (Bound to sample initial conversations)
        registerRule(
            RuleDefinition(
                id = "gem_kotlin_refactor_chat",
                name = "Kotlin Refactor Specialist Gem",
                description = "Chat-specific Gem: Optimizes coroutine dispatchers and eliminates thread contention.",
                priority = RulePriority.AGENT_ROUTE + 15,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = SingleCondition("request.goal", ConditionOperator.CONTAINS, "kotlin"),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "CodingAgent")
                ),
                scope = "CHAT",
                chatId = "task_seed_coding",
                isGem = true,
                gemIcon = "💻",
                gemInstructions = "Scoped to Kotlin Refactoring Task: Enforce structured concurrency and memory bounds."
            )
        )

        registerRule(
            RuleDefinition(
                id = "gem_news_digest_chat",
                name = "News & Market Digest Gem",
                description = "Chat-specific Gem: Extracts high-signal AI news and formats into executive bullet points.",
                priority = RulePriority.AGENT_ROUTE + 15,
                trigger = RuleTrigger(RuleEvents.ALL),
                conditions = SingleCondition("request.goal", ConditionOperator.CONTAINS, "news"),
                actions = listOf(
                    RuleAction(RuleActionTypes.ROUTE_AGENT, target = "WebReviewAgent")
                ),
                scope = "CHAT",
                chatId = "task_seed_news",
                isGem = true,
                gemIcon = "📰",
                gemInstructions = "Scoped to AI News Task: Summarize top 3 breakthroughs with dates and source URLs."
            )
        )
    }

    companion object {
        val global: RuleEngine by lazy { RuleEngine() }
    }
}
