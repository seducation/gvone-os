package com.example.agent.rules

import com.example.agent.core.StepLogger
import com.example.agent.core.StepStatus
import com.example.agent.core.StepType
import com.example.agent.core.ToolRegistry
import com.example.agent.safety.PermissionSystem
import java.util.concurrent.ConcurrentHashMap

/**
 * Functional handler interface for executing a specific type of RuleAction.
 */
fun interface RuleActionHandler {
    suspend fun handle(action: RuleAction, context: RuleContext): ActionExecutionResult
}

/**
 * Extensible Action Registry for the GVONE Rule Engine.
 * Allows new action handlers to be plugged in dynamically without modifying the core evaluator.
 */
class RuleActionRegistry(
    private val logger: StepLogger = StepLogger.global,
    private val toolRegistry: ToolRegistry = ToolRegistry.global,
    private val permissionSystem: PermissionSystem = PermissionSystem.global
) {
    private val handlers = ConcurrentHashMap<String, RuleActionHandler>()

    init {
        registerBuiltInHandlers()
    }

    fun registerHandler(actionType: String, handler: RuleActionHandler) {
        handlers[actionType.lowercase()] = handler
    }

    fun unregisterHandler(actionType: String) {
        handlers.remove(actionType.lowercase())
    }

    fun getHandler(actionType: String): RuleActionHandler? = handlers[actionType.lowercase()]

    /**
     * Executes a RuleAction against context using the registered handler.
     */
    suspend fun execute(action: RuleAction, context: RuleContext): ActionExecutionResult {
        val handler = getHandler(action.type)
            ?: return ActionExecutionResult(
                actionType = action.type,
                success = false,
                target = action.target,
                error = "No action handler registered for action type '${action.type}'"
            )

        return try {
            handler.handle(action, context)
        } catch (e: Exception) {
            ActionExecutionResult(
                actionType = action.type,
                success = false,
                target = action.target,
                error = e.message ?: e.toString()
            )
        }
    }

    private fun registerBuiltInHandlers() {
        // 1. route_agent: Select or reassign the target agent
        registerHandler(RuleActionTypes.ROUTE_AGENT) { action, ctx ->
            val targetAgent = action.target
                ?: action.parameters["agent"]?.toString()
                ?: "BrowserAgent"
            ctx.variables["targetAgent"] = targetAgent
            ctx.variables["activeAgent"] = targetAgent
            logger.logInstant("RuleAction", StepType.DECIDE, "Routed agent -> $targetAgent", StepStatus.SUCCESS)
            ActionExecutionResult(
                actionType = RuleActionTypes.ROUTE_AGENT,
                success = true,
                target = targetAgent,
                output = "Agent routed to $targetAgent"
            )
        }

        // 2. route_node: Route execution to a specific workflow node
        registerHandler(RuleActionTypes.ROUTE_NODE) { action, ctx ->
            val targetNode = action.target ?: action.parameters["node"]?.toString() ?: ""
            ctx.variables["targetNodeId"] = targetNode
            logger.logInstant("RuleAction", StepType.DECIDE, "Routed node -> $targetNode", StepStatus.SUCCESS)
            ActionExecutionResult(
                actionType = RuleActionTypes.ROUTE_NODE,
                success = true,
                target = targetNode,
                output = "Next node set to $targetNode"
            )
        }

        // 3. start_workflow: Trigger a specific nodal workflow
        registerHandler(RuleActionTypes.START_WORKFLOW) { action, ctx ->
            val wfId = action.target ?: action.parameters["workflow"]?.toString() ?: ""
            ctx.variables["targetWorkflowId"] = wfId
            logger.logInstant("RuleAction", StepType.DECIDE, "Triggered workflow -> $wfId", StepStatus.SUCCESS)
            ActionExecutionResult(
                actionType = RuleActionTypes.START_WORKFLOW,
                success = true,
                target = wfId,
                output = "Workflow set to $wfId"
            )
        }

        // 4. stop_execution: Abort/halt current flow
        registerHandler(RuleActionTypes.STOP_EXECUTION) { action, ctx ->
            val reason = action.parameters["reason"]?.toString() ?: "Halted by rule policy"
            ctx.variables["executionHalted"] = true
            ctx.variables["haltReason"] = reason
            logger.logInstant("RuleAction", StepType.ERROR, "Execution stopped: $reason", StepStatus.FAILED)
            ActionExecutionResult(
                actionType = RuleActionTypes.STOP_EXECUTION,
                success = true,
                output = reason
            )
        }

        // 5. continue_execution: Explicit permit to proceed
        registerHandler(RuleActionTypes.CONTINUE_EXECUTION) { _, _ ->
            ActionExecutionResult(
                actionType = RuleActionTypes.CONTINUE_EXECUTION,
                success = true,
                output = "Execution continued"
            )
        }

        // 6. handoff: Explicit agent-to-agent handoff with strict context scoping
        registerHandler(RuleActionTypes.HANDOFF) { action, ctx ->
            val fromAgent = action.parameters["from"]?.toString() ?: ctx.activeAgent ?: "Unknown"
            val toAgent = action.target ?: action.parameters["to"]?.toString() ?: "BrowserAgent"
            @Suppress("UNCHECKED_CAST")
            val includeKeys = (action.parameters["include"] as? List<String>)
                ?: listOf("goal", "task", "findings", "tool_results")
            @Suppress("UNCHECKED_CAST")
            val excludeKeys = (action.parameters["exclude"] as? List<String>) ?: emptyList()

            // Isolate and filter handoff context to prevent accidental leakage
            val handoffPayload = mutableMapOf<String, Any?>()
            for (key in includeKeys) {
                if (!excludeKeys.contains(key)) {
                    val value = ctx.variables[key] ?: ctx.metadata[key]
                    if (value != null) handoffPayload[key] = value
                }
            }

            ctx.variables["targetAgent"] = toAgent
            ctx.variables["activeAgent"] = toAgent
            ctx.variables["lastHandoffFrom"] = fromAgent
            ctx.variables["handoffPayload"] = handoffPayload

            logger.logInstant("RuleAction", StepType.DECIDE, "Agent handoff: $fromAgent -> $toAgent with ${handoffPayload.size} scoped keys", StepStatus.SUCCESS)
            ActionExecutionResult(
                actionType = RuleActionTypes.HANDOFF,
                success = true,
                target = toAgent,
                output = mapOf("from" to fromAgent, "to" to toAgent, "scopedKeys" to handoffPayload.keys.toList())
            )
        }

        // 7. parallel_execute & sequential_execute
        registerHandler(RuleActionTypes.PARALLEL_EXECUTE) { action, ctx ->
            @Suppress("UNCHECKED_CAST")
            val agents = (action.parameters["agents"] as? List<String>) ?: emptyList()
            ctx.variables["parallelAgents"] = agents
            ActionExecutionResult(
                actionType = RuleActionTypes.PARALLEL_EXECUTE,
                success = true,
                output = "Configured parallel execution for: ${agents.joinToString()}"
            )
        }

        registerHandler(RuleActionTypes.SEQUENTIAL_EXECUTE) { action, ctx ->
            @Suppress("UNCHECKED_CAST")
            val pipeline = (action.parameters["pipeline"] as? List<String>) ?: emptyList()
            ctx.variables["sequentialPipeline"] = pipeline
            ActionExecutionResult(
                actionType = RuleActionTypes.SEQUENTIAL_EXECUTE,
                success = true,
                output = "Configured sequential pipeline: ${pipeline.joinToString(" -> ")}"
            )
        }

        // 8. set_context & set_variable
        registerHandler(RuleActionTypes.SET_CONTEXT) { action, ctx ->
            val key = action.target ?: action.parameters["key"]?.toString() ?: ""
            val value = action.parameters["value"]
            if (key.isNotBlank()) {
                ctx.variables[key] = value
            }
            ActionExecutionResult(actionType = RuleActionTypes.SET_CONTEXT, success = true, output = "$key=$value")
        }

        registerHandler(RuleActionTypes.SET_VARIABLE) { action, ctx ->
            val key = action.target ?: action.parameters["key"]?.toString() ?: ""
            val value = action.parameters["value"]
            if (key.isNotBlank()) {
                ctx.variables[key] = value
            }
            ActionExecutionResult(actionType = RuleActionTypes.SET_VARIABLE, success = true, output = "$key=$value")
        }

        // 9. remove_context & clear_variable
        registerHandler(RuleActionTypes.REMOVE_CONTEXT) { action, ctx ->
            val key = action.target ?: action.parameters["key"]?.toString() ?: ""
            ctx.variables.remove(key)
            ActionExecutionResult(actionType = RuleActionTypes.REMOVE_CONTEXT, success = true, output = "Removed $key")
        }

        registerHandler(RuleActionTypes.CLEAR_VARIABLE) { action, ctx ->
            val key = action.target ?: action.parameters["key"]?.toString() ?: ""
            ctx.variables.remove(key)
            ActionExecutionResult(actionType = RuleActionTypes.CLEAR_VARIABLE, success = true, output = "Cleared $key")
        }

        // 10. call_tool: executes mediated tool with safety/permission validation
        registerHandler(RuleActionTypes.CALL_TOOL) { action, ctx ->
            val toolName = action.target ?: action.parameters["tool"]?.toString() ?: ""
            @Suppress("UNCHECKED_CAST")
            val params = (action.parameters["params"] as? Map<String, Any?>) ?: emptyMap()
            val caller = ctx.activeAgent ?: "RuleEngine"

            val res = toolRegistry.executeMediated(toolName, params, caller)
            ActionExecutionResult(
                actionType = RuleActionTypes.CALL_TOOL,
                success = res.success,
                target = toolName,
                output = res.data,
                error = res.error
            )
        }

        // 11. request_approval: User confirmation gate
        registerHandler(RuleActionTypes.REQUEST_APPROVAL) { action, ctx ->
            val perm = action.parameters["permission"]?.toString() ?: "general.execute"
            val canonicalPerm = permissionSystem.canonicalPermission(perm)
            val hasPerm = ctx.permissions.contains(perm) ||
                    ctx.permissions.contains(canonicalPerm) ||
                    ctx.permissions.contains(perm.replace(".", ":")) ||
                    ctx.permissions.contains(perm.replace(":", ".")) ||
                    permissionSystem.hasPermission(perm) ||
                    permissionSystem.hasPermission(canonicalPerm)
            ctx.variables["approvalRequired"] = true
            ctx.variables["requiredPermission"] = perm
            ctx.variables["approvalGranted"] = hasPerm

            ActionExecutionResult(
                actionType = RuleActionTypes.REQUEST_APPROVAL,
                success = hasPerm,
                target = perm,
                output = if (hasPerm) "Approval automatically verified via grant" else "User approval required for $perm"
            )
        }

        // 12. retry: Configurable retry counter check
        registerHandler(RuleActionTypes.RETRY) { action, ctx ->
            val maxRetries = (action.parameters["max_attempts"] as? Number)?.toInt() ?: 3
            val currentRetries = maxOf(ctx.executionState.retryCount, ctx.tools.retryCount)
            if (currentRetries < maxRetries) {
                ctx.variables["retryScheduled"] = true
                ctx.variables["nextRetryAttempt"] = currentRetries + 1
                ctx.variables["retryAttempt"] = currentRetries + 1
                ActionExecutionResult(
                    actionType = RuleActionTypes.RETRY,
                    success = true,
                    output = "Retry allowed (attempt ${currentRetries + 1}/$maxRetries)"
                )
            } else {
                ActionExecutionResult(
                    actionType = RuleActionTypes.RETRY,
                    success = false,
                    error = "Max retry attempts ($maxRetries) exhausted"
                )
            }
        }

        // 13. fallback: Switch to fallback agent or node
        registerHandler(RuleActionTypes.FALLBACK) { action, ctx ->
            val fallbackAgent = action.parameters["agent"]?.toString() ?: action.target ?: "BrowserAgent"
            ctx.variables["targetAgent"] = fallbackAgent
            ctx.variables["activeAgent"] = fallbackAgent
            ctx.variables["isFallbackEngaged"] = true
            logger.logInstant("RuleAction", StepType.DECIDE, "Fallback engaged -> $fallbackAgent", StepStatus.SUCCESS)
            ActionExecutionResult(
                actionType = RuleActionTypes.FALLBACK,
                success = true,
                target = fallbackAgent,
                output = "Fallback engaged to $fallbackAgent"
            )
        }

        // 14. context_policy: Explicit memory scoping (inherit, copy, isolate, summarize, filter, reset)
        registerHandler(RuleActionTypes.CONTEXT_POLICY) { action, ctx ->
            val mode = action.parameters["mode"]?.toString()?.lowercase() ?: "isolate"
            ctx.variables["contextPolicyMode"] = mode
            ActionExecutionResult(
                actionType = RuleActionTypes.CONTEXT_POLICY,
                success = true,
                output = "Context policy applied: $mode"
            )
        }

        // 15. emit_event & log
        registerHandler(RuleActionTypes.EMIT_EVENT) { action, _ ->
            val eventName = action.target ?: action.parameters["event"]?.toString() ?: "custom.event"
            ActionExecutionResult(
                actionType = RuleActionTypes.EMIT_EVENT,
                success = true,
                target = eventName,
                output = "Emitted event: $eventName"
            )
        }

        registerHandler(RuleActionTypes.LOG) { action, _ ->
            val msg = action.parameters["message"]?.toString() ?: action.target ?: ""
            logger.logInstant("RuleAction", StepType.DECIDE, msg, StepStatus.SUCCESS)
            ActionExecutionResult(actionType = RuleActionTypes.LOG, success = true, output = msg)
        }

        // 16. transform
        registerHandler(RuleActionTypes.TRANSFORM) { action, ctx ->
            val sourceKey = action.parameters["source"]?.toString() ?: "tool_result"
            val targetKey = action.parameters["target"]?.toString() ?: "transformed_data"
            val raw = ctx.resolvePath(sourceKey)
            ctx.variables[targetKey] = raw?.toString()?.trim() ?: ""
            ActionExecutionResult(actionType = RuleActionTypes.TRANSFORM, success = true, output = "Transformed $sourceKey to $targetKey")
        }

        // 17. validate
        registerHandler(RuleActionTypes.VALIDATE) { action, ctx ->
            val expectedField = action.parameters["field"]?.toString() ?: "status"
            val expectedValue = action.parameters["value"]?.toString() ?: "SUCCESS"
            val actual = ctx.resolvePath(expectedField)?.toString()
            val valid = actual.equals(expectedValue, ignoreCase = true)
            ActionExecutionResult(
                actionType = RuleActionTypes.VALIDATE,
                success = valid,
                output = if (valid) "Validation passed: $expectedField == $expectedValue" else "Validation failed: $expectedField was '$actual', expected '$expectedValue'"
            )
        }
    }

    companion object {
        val global: RuleActionRegistry by lazy { RuleActionRegistry() }
    }
}
