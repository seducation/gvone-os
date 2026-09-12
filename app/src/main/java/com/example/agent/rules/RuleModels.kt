package com.example.agent.rules

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Standard comparison and evaluation operators for declarative rules.
 */
enum class ConditionOperator(val symbol: String) {
    EQUALS("equals"),
    NOT_EQUALS("not_equals"),
    CONTAINS("contains"),
    NOT_CONTAINS("not_contains"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    MATCHES("matches"),
    REGEX("regex"),
    GREATER_THAN("greater_than"),
    LESS_THAN("less_than"),
    GREATER_OR_EQUAL("greater_or_equal"),
    LESS_OR_EQUAL("less_or_equal"),
    EXISTS("exists"),
    NOT_EXISTS("not_exists"),
    IN("in"),
    NOT_IN("not_in"),
    IS_TRUE("is_true"),
    IS_FALSE("is_false");

    companion object {
        fun fromString(str: String): ConditionOperator {
            val clean = str.trim().lowercase()
            return entries.find {
                it.name.equals(clean, ignoreCase = true) ||
                        it.symbol.equals(clean, ignoreCase = true) ||
                        when (clean) {
                            "==", "=" -> it == EQUALS
                            "!=" -> it == NOT_EQUALS
                            ">" -> it == GREATER_THAN
                            "<" -> it == LESS_THAN
                            ">=" -> it == GREATER_OR_EQUAL
                            "<=" -> it == LESS_OR_EQUAL
                            else -> false
                        }
            } ?: EQUALS
        }
    }
}

/**
 * Sealed interface for condition tree nodes supporting atomic conditions and composite (AND, OR, NOT).
 */
sealed interface RuleCondition {
    fun toJson(): JSONObject
    fun toReadableString(): String

    companion object {
        operator fun invoke(field: String, operator: ConditionOperator = ConditionOperator.EQUALS, value: Any? = null): RuleCondition {
            return SingleCondition(field, operator, value)
        }

        fun fromJson(json: JSONObject): RuleCondition {
            return when {
                json.has("all") || json.has("any") || json.has("not") -> {
                    val allList = mutableListOf<RuleCondition>()
                    val allArr = json.optJSONArray("all")
                    if (allArr != null) {
                        for (i in 0 until allArr.length()) {
                            allList.add(fromJson(allArr.getJSONObject(i)))
                        }
                    }

                    val anyList = mutableListOf<RuleCondition>()
                    val anyArr = json.optJSONArray("any")
                    if (anyArr != null) {
                        for (i in 0 until anyArr.length()) {
                            anyList.add(fromJson(anyArr.getJSONObject(i)))
                        }
                    }

                    val notCond = json.optJSONObject("not")?.let { fromJson(it) }

                    CompositeCondition(all = allList, any = anyList, not = notCond)
                }
                else -> {
                    val field = json.optString("field", "")
                    val opStr = json.optString("operator", "equals")
                    val value = if (json.has("value")) json.get("value") else null
                    SingleCondition(
                        field = field,
                        operator = ConditionOperator.fromString(opStr),
                        value = if (value == JSONObject.NULL) null else value
                    )
                }
            }
        }
    }
}

/**
 * Leaf atomic condition checking a specific context field against a value.
 */
data class SingleCondition(
    val field: String,
    val operator: ConditionOperator = ConditionOperator.EQUALS,
    val value: Any? = null
) : RuleCondition {
    override fun toJson(): JSONObject = JSONObject().apply {
        put("field", field)
        put("operator", operator.symbol)
        if (value != null) put("value", value)
    }

    override fun toReadableString(): String =
        "$field ${operator.symbol} ${value ?: ""}".trim()
}

/**
 * Composite boolean condition (AND, OR, NOT) supporting nested conditions.
 */
data class CompositeCondition(
    val all: List<RuleCondition> = emptyList(), // AND
    val any: List<RuleCondition> = emptyList(), // OR
    val not: RuleCondition? = null              // NOT
) : RuleCondition {
    override fun toJson(): JSONObject = JSONObject().apply {
        if (all.isNotEmpty()) put("all", JSONArray().apply { all.forEach { put(it.toJson()) } })
        if (any.isNotEmpty()) put("any", JSONArray().apply { any.forEach { put(it.toJson()) } })
        if (not != null) put("not", not.toJson())
    }

    override fun toReadableString(): String = buildString {
        val parts = mutableListOf<String>()
        if (all.isNotEmpty()) parts.add("ALL(${all.joinToString(" AND ") { it.toReadableString() }})")
        if (any.isNotEmpty()) parts.add("ANY(${any.joinToString(" OR ") { it.toReadableString() }})")
        if (not != null) parts.add("NOT(${not.toReadableString()})")
        append(parts.joinToString(" AND "))
    }
}

/**
 * Standard rule action types supported by the GVONE Rule Engine.
 */
object RuleActionTypes {
    const val ROUTE_AGENT = "route_agent"
    const val ROUTE_NODE = "route_node"
    const val START_WORKFLOW = "start_workflow"
    const val STOP_EXECUTION = "stop_execution"
    const val CONTINUE_EXECUTION = "continue_execution"
    const val HANDOFF = "handoff"
    const val PARALLEL_EXECUTE = "parallel_execute"
    const val SEQUENTIAL_EXECUTE = "sequential_execute"
    const val SET_CONTEXT = "set_context"
    const val REMOVE_CONTEXT = "remove_context"
    const val SET_VARIABLE = "set_variable"
    const val CLEAR_VARIABLE = "clear_variable"
    const val CALL_TOOL = "call_tool"
    const val REQUEST_APPROVAL = "request_approval"
    const val RETRY = "retry"
    const val FALLBACK = "fallback"
    const val EMIT_EVENT = "emit_event"
    const val LOG = "log"
    const val TRANSFORM = "transform"
    const val VALIDATE = "validate"
    const val CONTEXT_POLICY = "context_policy"
}

/**
 * Action triggered when a rule fires.
 */
data class RuleAction(
    val type: String,
    val target: String? = null,
    val parameters: Map<String, Any?> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        if (target != null) put("target", target)
        if (parameters.isNotEmpty()) put("parameters", JSONObject(parameters))
    }

    companion object {
        fun fromJson(json: JSONObject): RuleAction {
            val params = mutableMapOf<String, Any?>()
            val pObj = json.optJSONObject("parameters")
            if (pObj != null) {
                val keys = pObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    params[k] = pObj.get(k)
                }
            }
            return RuleAction(
                type = json.getString("type"),
                target = json.optString("target").ifBlank { null },
                parameters = params
            )
        }
    }
}

/**
 * Standard event names recognized across the GVONE OS architecture.
 */
object RuleEvents {
    const val REQUEST_RECEIVED = "request.received"
    const val COMMAND_PARSED = "command.parsed"
    const val INTENT_DETECTED = "intent.detected"
    const val NODE_STARTED = "node.started"
    const val NODE_COMPLETED = "node.completed"
    const val NODE_FAILED = "node.failed"
    const val AGENT_STARTED = "agent.started"
    const val AGENT_COMPLETED = "agent.completed"
    const val AGENT_FAILED = "agent.failed"
    const val TOOL_STARTED = "tool.started"
    const val TOOL_COMPLETED = "tool.completed"
    const val TOOL_FAILED = "tool.failed"
    const val APPROVAL_REQUIRED = "approval.required"
    const val APPROVAL_GRANTED = "approval.granted"
    const val APPROVAL_DENIED = "approval.denied"
    const val WORKFLOW_COMPLETED = "workflow.completed"
    const val EXECUTION_TIMEOUT = "execution.timeout"
    const val EXECUTION_CANCELLED = "execution.cancelled"
    const val HANDOFF_REQUESTED = "handoff.requested"
    const val ALL = "*"
}

/**
 * Event trigger condition for when a rule should be evaluated.
 */
data class RuleTrigger(
    val event: String = RuleEvents.ALL,
    val filter: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("event", event)
        if (filter.isNotEmpty()) put("filter", JSONObject(filter))
    }

    companion object {
        fun fromJson(json: JSONObject): RuleTrigger {
            val filterMap = mutableMapOf<String, String>()
            val fObj = json.optJSONObject("filter")
            if (fObj != null) {
                val keys = fObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    filterMap[k] = fObj.optString(k, "")
                }
            }
            return RuleTrigger(
                event = json.optString("event", RuleEvents.ALL),
                filter = filterMap
            )
        }
    }
}

/**
 * Standard priority bands for deterministic rule conflict resolution.
 */
object RulePriority {
    const val EMERGENCY = 1000   // System safety / abort
    const val REFLEX = 900       // Autonomic reflex block
    const val SECURITY = 500     // Permission / authorization checks
    const val COMMAND = 300      // Explicit user / slash command
    const val AGENT_ROUTE = 200  // Agent selection / capability matching
    const val INTENT_ROUTE = 100 // General intent routing
    const val FALLBACK = 50      // Fallback agent or recovery action
    const val DEFAULT = 0        // Base default behavior
}

/**
 * Declarative rule definition.
 */
typealias RuleModel = RuleDefinition

data class RuleDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val priority: Int = RulePriority.DEFAULT,
    val enabled: Boolean = true,
    val trigger: RuleTrigger = RuleTrigger(RuleEvents.ALL),
    val conditions: RuleCondition = SingleCondition("intent", ConditionOperator.EXISTS),
    val actions: List<RuleAction> = emptyList(),
    val onSuccess: List<RuleAction> = emptyList(),
    val onFailure: List<RuleAction> = emptyList(),
    val version: Int = 1,
    val author: String = "system",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any?> = emptyMap(),
    val scope: String = "GLOBAL", // "GLOBAL" or "CHAT"
    val chatId: String? = null,   // Specific conversation ID or null for global
    val isGem: Boolean = false,
    val gemIcon: String = "💎",
    val gemInstructions: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("priority", priority)
        put("enabled", enabled)
        put("trigger", trigger.toJson())
        put("conditions", conditions.toJson())
        put("actions", JSONArray().apply { actions.forEach { put(it.toJson()) } })
        put("onSuccess", JSONArray().apply { onSuccess.forEach { put(it.toJson()) } })
        put("onFailure", JSONArray().apply { onFailure.forEach { put(it.toJson()) } })
        put("version", version)
        put("author", author)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("scope", scope)
        if (chatId != null) put("chatId", chatId)
        put("isGem", isGem)
        put("gemIcon", gemIcon)
        if (gemInstructions.isNotBlank()) put("gemInstructions", gemInstructions)
        if (metadata.isNotEmpty()) put("metadata", JSONObject(metadata))
    }

    /**
     * Converts to a spreadsheet-like row for n8n/tabular UI compatibility.
     * Columns: Rule ID | Trigger | Condition | Operator | Value | Action | Target | Priority | Enabled | Scope
     */
    fun toTabularRow(): Map<String, String> {
        val primaryCondition = when (conditions) {
            is SingleCondition -> conditions
            is CompositeCondition -> (conditions.all.firstOrNull() as? SingleCondition)
                ?: (conditions.any.firstOrNull() as? SingleCondition)
                ?: SingleCondition("composite", ConditionOperator.EXISTS, "")
        }
        val primaryAction = actions.firstOrNull() ?: RuleAction(RuleActionTypes.CONTINUE_EXECUTION)

        return mapOf(
            "id" to id,
            "name" to name,
            "trigger" to trigger.event,
            "conditionField" to primaryCondition.field,
            "conditionOperator" to primaryCondition.operator.symbol,
            "conditionValue" to (primaryCondition.value?.toString() ?: ""),
            "action" to primaryAction.type,
            "target" to (primaryAction.target ?: ""),
            "priority" to priority.toString(),
            "enabled" to enabled.toString(),
            "description" to description,
            "scope" to scope,
            "chatId" to (chatId ?: ""),
            "isGem" to isGem.toString()
        )
    }

    companion object {
        fun fromJson(json: JSONObject): RuleDefinition {
            val actionsList = mutableListOf<RuleAction>()
            val actArr = json.optJSONArray("actions")
            if (actArr != null) {
                for (i in 0 until actArr.length()) actionsList.add(RuleAction.fromJson(actArr.getJSONObject(i)))
            }

            val successList = mutableListOf<RuleAction>()
            val succArr = json.optJSONArray("onSuccess")
            if (succArr != null) {
                for (i in 0 until succArr.length()) successList.add(RuleAction.fromJson(succArr.getJSONObject(i)))
            }

            val failList = mutableListOf<RuleAction>()
            val failArr = json.optJSONArray("onFailure")
            if (failArr != null) {
                for (i in 0 until failArr.length()) failList.add(RuleAction.fromJson(failArr.getJSONObject(i)))
            }

            val metaMap = mutableMapOf<String, Any?>()
            val mObj = json.optJSONObject("metadata")
            if (mObj != null) {
                val keys = mObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    metaMap[k] = mObj.get(k)
                }
            }

            val triggerObj = json.optJSONObject("trigger") ?: JSONObject()
            val condObj = json.optJSONObject("conditions") ?: JSONObject()

            return RuleDefinition(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description", ""),
                priority = json.optInt("priority", RulePriority.DEFAULT),
                enabled = json.optBoolean("enabled", true),
                trigger = RuleTrigger.fromJson(triggerObj),
                conditions = RuleCondition.fromJson(condObj),
                actions = actionsList,
                onSuccess = successList,
                onFailure = failList,
                version = json.optInt("version", 1),
                author = json.optString("author", "system"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                metadata = metaMap,
                scope = json.optString("scope", "GLOBAL"),
                chatId = json.optString("chatId").ifBlank { null },
                isGem = json.optBoolean("isGem", false),
                gemIcon = json.optString("gemIcon", "💎"),
                gemInstructions = json.optString("gemInstructions", "")
            )
        }

        fun fromTabularRow(row: Map<String, String>): RuleDefinition {
            val id = row["id"]?.ifBlank { null } ?: "rule_${UUID.randomUUID().toString().take(8)}"
            val name = row["name"]?.ifBlank { null } ?: "Rule $id"
            val triggerEvent = row["trigger"]?.ifBlank { null } ?: RuleEvents.ALL
            val field = row["conditionField"]?.ifBlank { null } ?: "intent"
            val opStr = row["conditionOperator"]?.ifBlank { null } ?: "equals"
            val value = row["conditionValue"]
            val actionType = row["action"]?.ifBlank { null } ?: RuleActionTypes.CONTINUE_EXECUTION
            val target = row["target"]?.ifBlank { null }
            val priority = row["priority"]?.toIntOrNull() ?: RulePriority.DEFAULT
            val enabled = row["enabled"]?.toBooleanStrictOrNull() ?: true
            val desc = row["description"] ?: ""
            val scope = row["scope"]?.ifBlank { null } ?: "GLOBAL"
            val chatId = row["chatId"]?.ifBlank { null }
            val isGem = row["isGem"]?.toBooleanStrictOrNull() ?: false

            return RuleDefinition(
                id = id,
                name = name,
                description = desc,
                priority = priority,
                enabled = enabled,
                trigger = RuleTrigger(triggerEvent),
                conditions = SingleCondition(field, ConditionOperator.fromString(opStr), value),
                actions = listOf(RuleAction(type = actionType, target = target)),
                scope = scope,
                chatId = chatId,
                isGem = isGem
            )
        }
    }
}

/**
 * Unified execution context evaluated by the Rule Engine.
 */
data class RuleRequestContext(
    val goal: String = "",
    val command: String = "",
    val queryArg: String = "",
    val rawInput: String = "",
    val source: String = "User",
    val target: String? = null,
    val caller: String = "CNS",
    val chatId: String? = null,
    val taskId: String? = null
)

data class RuleNodeContext(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val previousNodeId: String? = null,
    val nextNodeId: String? = null
)

data class RuleWorkflowContext(
    val id: String = "",
    val name: String = "",
    val stepIndex: Int = 0
)

data class RuleExecutionState(
    val status: String = "RUNNING",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val isTimeout: Boolean = false,
    val error: String? = null,
    val depth: Int = 0
)

data class RuleToolContext(
    val toolName: String? = null,
    val toolParams: Map<String, Any?> = emptyMap(),
    val toolResult: Any? = null,
    val isSuccess: Boolean = true,
    val riskLevel: String = "NORMAL",
    val errorMessage: String? = null,
    val retryCount: Int = 0
)

data class RuleApprovalState(
    val isApproved: Boolean = false,
    val isPending: Boolean = false,
    val requiredPermission: String? = null
)

data class RuleVoiceState(
    val isVoiceActive: Boolean = false,
    val interactionPriority: String = "EXECUTION"
)

data class RuleContext(
    val event: String = RuleEvents.REQUEST_RECEIVED,
    val request: RuleRequestContext = RuleRequestContext(),
    val intent: String? = null,
    val entities: Map<String, Any?> = emptyMap(),
    val activeAgent: String? = null,
    val candidateAgent: String? = null,
    val availableAgents: List<String> = emptyList(),
    val agentCapabilities: Map<String, List<String>> = emptyMap(),
    val node: RuleNodeContext? = null,
    val workflow: RuleWorkflowContext? = null,
    val executionState: RuleExecutionState = RuleExecutionState(),
    val tools: RuleToolContext = RuleToolContext(),
    val permissions: Set<String> = emptySet(),
    val approvalState: RuleApprovalState = RuleApprovalState(),
    val voiceState: RuleVoiceState = RuleVoiceState(),
    val variables: MutableMap<String, Any?> = mutableMapOf(),
    val metadata: Map<String, Any?> = emptyMap(),
    val executionId: String = UUID.randomUUID().toString(),
    val chatId: String? = null
) {
    fun isExecutionHalted(): Boolean =
        variables["executionHalted"] == true || executionState.status == "HALTED"

    /**
     * Resolves dot-notation dynamic path lookups (e.g. "request.intent", "agent.name", "execution.retry_count").
     */
    fun resolvePath(path: String): Any? {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return null

        // Support direct top-level aliases
        when (trimmed.lowercase()) {
            "intent" -> return intent ?: request.goal
            "command" -> return request.command
            "goal" -> return request.goal
            "chat", "chat_id", "chatid" -> return chatId ?: request.chatId ?: request.taskId
            "risk" -> return metadata["risk"] ?: "low"
            "user_approved" -> return approvalState.isApproved
            "retry_count" -> return executionState.retryCount
            "is_timeout" -> return executionState.isTimeout
            "error" -> return executionState.error
            "status" -> return executionState.status
            "agent" -> return activeAgent ?: candidateAgent
            "tool" -> return tools.toolName
            "tool_result" -> return tools.toolResult
            "voice_enabled", "voice_active" -> return voiceState.isVoiceActive
        }

        val parts = trimmed.split(".")
        val root = parts[0].lowercase()

        return when (root) {
            "chat" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "id" -> chatId ?: request.chatId ?: request.taskId
                    else -> chatId ?: request.chatId ?: request.taskId
                }
            }
            "request" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "intent" -> intent ?: request.goal
                    "command" -> request.command
                    "goal" -> request.goal
                    "chatid", "chat_id" -> request.chatId ?: chatId
                    "taskid", "task_id" -> request.taskId ?: chatId
                    "query", "queryarg" -> request.queryArg
                    "input", "rawinput" -> request.rawInput
                    "source" -> request.source
                    "target" -> request.target
                    "caller" -> request.caller
                    else -> null
                }
            }
            "intent" -> intent ?: request.goal
            "agent" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "active", "name", "id" -> activeAgent ?: candidateAgent
                    "candidate" -> candidateAgent
                    "capabilities" -> {
                        val ag = activeAgent ?: candidateAgent
                        if (ag != null) agentCapabilities[ag] ?: emptyList<String>() else emptyList<String>()
                    }
                    else -> activeAgent ?: candidateAgent
                }
            }
            "node" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "id" -> node?.id
                    "type" -> node?.type
                    "name" -> node?.name
                    "previous" -> node?.previousNodeId
                    "next" -> node?.nextNodeId
                    else -> node?.id
                }
            }
            "workflow" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "id" -> workflow?.id
                    "name" -> workflow?.name
                    "step", "stepindex" -> workflow?.stepIndex
                    else -> workflow?.id
                }
            }
            "execution" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "status" -> executionState.status
                    "retry_count", "retrycount" -> executionState.retryCount
                    "max_retries", "maxretries" -> executionState.maxRetries
                    "is_timeout", "istimeout" -> executionState.isTimeout
                    "error" -> executionState.error
                    "depth" -> executionState.depth
                    else -> null
                }
            }
            "tool", "tools" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "name" -> tools.toolName
                    "result" -> tools.toolResult
                    "success", "issuccess" -> tools.isSuccess
                    "risk", "risklevel" -> tools.riskLevel
                    "error", "errormessage" -> tools.errorMessage
                    "retry_count", "retrycount" -> tools.retryCount
                    else -> tools.toolName
                }
            }
            "permissions" -> {
                if (parts.size > 1 && parts[1].equals("has", ignoreCase = true)) {
                    val rawRemaining = parts.drop(2).joinToString(".")
                    val colonRemaining = parts.drop(2).joinToString(":")
                    permissions.contains(rawRemaining) ||
                        permissions.contains(colonRemaining) ||
                        permissions.any { it.equals(rawRemaining, ignoreCase = true) || it.equals(colonRemaining, ignoreCase = true) }
                } else {
                    permissions
                }
            }
            "approval" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "approved", "isapproved" -> approvalState.isApproved
                    "pending", "ispending" -> approvalState.isPending
                    "permission" -> approvalState.requiredPermission
                    else -> approvalState.isApproved
                }
            }
            "context" -> {
                val sub = parts.getOrNull(1) ?: return null
                if (sub.equals("user_approved", ignoreCase = true)) approvalState.isApproved
                else variables[sub] ?: metadata[sub]
            }
            "voice" -> {
                when (parts.getOrNull(1)?.lowercase()) {
                    "active", "enabled", "isactive" -> voiceState.isVoiceActive
                    "priority" -> voiceState.interactionPriority
                    else -> voiceState.isVoiceActive
                }
            }
            "variables" -> {
                val key = parts.getOrNull(1) ?: return null
                variables[key]
            }
            "metadata" -> {
                val key = parts.getOrNull(1) ?: return null
                metadata[key]
            }
            else -> variables[trimmed] ?: metadata[trimmed]
        }
    }
}

/**
 * Result of an individual rule action execution.
 */
data class ActionExecutionResult(
    val actionType: String,
    val success: Boolean,
    val target: String? = null,
    val output: Any? = null,
    val error: String? = null
)

/**
 * Trace entry for a single rule evaluated against context.
 */
data class RuleMatchTrace(
    val ruleId: String,
    val ruleName: String,
    val priority: Int,
    val matched: Boolean,
    val specificity: Int,
    val reason: String
)

/**
 * Report generated when multiple conflicting rules match the same event.
 */
data class RuleConflictReport(
    val conflictingRuleIds: List<String>,
    val winningRuleId: String,
    val winnerPriority: Int,
    val resolutionReason: String
)

/**
 * Complete evaluation trace for auditing, explainability, and debugging.
 */
data class RuleEvaluationTrace(
    val executionId: String,
    val event: String,
    val evaluatedCount: Int,
    val matchedRules: List<RuleMatchTrace>,
    val selectedRule: RuleDefinition?,
    val conflict: RuleConflictReport?,
    val executedActions: List<ActionExecutionResult>,
    val durationMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val explainabilitySummary: String
) {
    val totalRulesEvaluated: Int get() = evaluatedCount
    val conflictResolutionReason: String get() = conflict?.resolutionReason ?: explainabilitySummary
}

/**
 * Dry-run simulation output.
 */
data class RuleSimulationResult(
    val executionId: String,
    val event: String,
    val matchedRules: List<RuleDefinition>,
    val selectedRule: RuleDefinition?,
    val selectedAgent: String?,
    val selectedNode: String?,
    val proposedActions: List<RuleAction>,
    val conflict: RuleConflictReport?,
    val explanation: String
)
