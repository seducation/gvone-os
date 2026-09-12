package com.example.agent

import com.example.agent.cns.CentralNervousSystem
import com.example.agent.nodal.*
import com.example.agent.rules.*
import com.example.agent.safety.PermissionSystem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Production-grade unit tests for GVONE OS Declarative Rule Engine and Orchestration.
 */
class RuleEngineUnitTest {

    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        ruleEngine = RuleEngine()
    }

    @Test
    fun testDefaultRulesLoadedAndOrderedByPriority() {
        val rules = ruleEngine.getRules()
        assertTrue("Default rules must be registered", rules.isNotEmpty())

        // Verify rules are strictly sorted descending by priority
        for (i in 0 until rules.size - 1) {
            assertTrue(
                "Rule at index $i (priority ${rules[i].priority}) must be >= index ${i + 1} (priority ${rules[i + 1].priority})",
                rules[i].priority >= rules[i + 1].priority
            )
        }
    }

    @Test
    fun testEmergencyHaltRuleBlocksDestructiveCommand() = runBlocking {
        val context = RuleContext(
            event = RuleEvents.REQUEST_RECEIVED,
            request = RuleRequestContext(
                goal = "execute rm -rf / without asking",
                caller = "User",
                rawInput = "execute rm -rf / without asking"
            )
        )

        val trace = ruleEngine.evaluate(context)
        assertNotNull("Trace must identify winning rule", trace.selectedRule)
        assertEquals("rule_danger_shell_halt", trace.selectedRule!!.id)
        assertTrue("Selected rule must have priority >= 1000", trace.selectedRule!!.priority >= 1000)

        val hasStopAction = trace.selectedRule!!.actions.any { it.type == RuleActionTypes.STOP_EXECUTION }
        assertTrue("Must trigger STOP_EXECUTION action", hasStopAction)

        // Execute action
        val (executedTrace, results) = ruleEngine.evaluateAndExecute(context)
        assertTrue("Context execution must be halted", context.isExecutionHalted())
        assertTrue("Results must report STOP_EXECUTION", results.any { it.actionType == RuleActionTypes.STOP_EXECUTION })
    }

    @Test
    fun testMultiAgentWorkflowRoutingRuleForDocumentWebComparison() = runBlocking {
        val context = RuleContext(
            event = RuleEvents.REQUEST_RECEIVED,
            request = RuleRequestContext(
                goal = "compare the financial pdf with the quarterly website report",
                caller = "User",
                rawInput = "compare the financial pdf with the quarterly website report"
            )
        )

        val trace = ruleEngine.evaluate(context)
        assertNotNull(trace.selectedRule)
        assertEquals("rule_compare_web_document", trace.selectedRule!!.id)

        // Execute actions on context
        val (_, results) = ruleEngine.evaluateAndExecute(context)
        val pipeline = context.variables["sequentialPipeline"] as? List<*>
        assertNotNull("Rule must establish sequential pipeline for multi-agent execution", pipeline)
        assertEquals(listOf("WebReviewAgent", "FileAgent"), pipeline)
    }

    @Test
    fun testHighRiskToolRequiresApprovalWhenPermissionMissing() = runBlocking {
        val context = RuleContext(
            event = RuleEvents.TOOL_STARTED,
            tools = RuleToolContext(
                toolName = "ShellTool",
                riskLevel = "HIGH"
            ),
            permissions = emptySet() // Permission missing
        )

        val (trace, results) = ruleEngine.evaluateAndExecute(context)
        assertNotNull(trace.selectedRule)
        assertEquals("rule_high_risk_tool_approval", trace.selectedRule!!.id)

        val approvalAction = results.find { it.actionType == RuleActionTypes.REQUEST_APPROVAL }
        assertNotNull("Must yield REQUEST_APPROVAL action", approvalAction)
        assertFalse("Approval must be false when permission not present", context.variables["approvalGranted"] == true)
    }

    @Test
    fun testHighRiskToolPermittedWhenPermissionGranted() = runBlocking {
        val context = RuleContext(
            event = RuleEvents.TOOL_STARTED,
            tools = RuleToolContext(
                toolName = "ShellTool",
                riskLevel = "HIGH"
            ),
            permissions = setOf(PermissionSystem.PERM_SHELL_EXECUTE)
        )

        val (trace, results) = ruleEngine.evaluateAndExecute(context)
        assertNotNull(trace.selectedRule)
        assertEquals(true, context.variables["approvalGranted"])
    }

    @Test
    fun testRetryPolicyOnNetworkTimeout() = runBlocking {
        val context = RuleContext(
            event = RuleEvents.TOOL_FAILED,
            tools = RuleToolContext(
                toolName = "BrowserTool",
                errorMessage = "SocketTimeoutException: connection timed out",
                retryCount = 1
            )
        )

        val (trace, results) = ruleEngine.evaluateAndExecute(context)
        assertNotNull(trace.selectedRule)
        assertEquals("rule_network_timeout_retry", trace.selectedRule!!.id)

        val retryAction = results.find { it.actionType == RuleActionTypes.RETRY }
        assertNotNull(retryAction)
        assertEquals(true, retryAction!!.success)
        assertEquals(2, context.variables["retryAttempt"])
    }

    @Test
    fun testConflictResolutionChoosesHigherPriorityRule() {
        // Register two competing rules matching "test priority conflict"
        val lowPriorityRule = RuleDefinition(
            id = "test_rule_low",
            name = "Low Priority Rule",
            description = "Competitor low",
            priority = 200,
            trigger = RuleTrigger(RuleEvents.REQUEST_RECEIVED),
            conditions = SingleCondition(field = "request.goal", operator = ConditionOperator.CONTAINS, value = "priority conflict"),
            actions = listOf(RuleAction(type = RuleActionTypes.LOG, target = "low"))
        )

        val highPriorityRule = RuleDefinition(
            id = "test_rule_high",
            name = "High Priority Rule",
            description = "Competitor high",
            priority = 900,
            trigger = RuleTrigger(RuleEvents.REQUEST_RECEIVED),
            conditions = SingleCondition(field = "request.goal", operator = ConditionOperator.CONTAINS, value = "priority conflict"),
            actions = listOf(RuleAction(type = RuleActionTypes.LOG, target = "high"))
        )

        ruleEngine.registerRule(lowPriorityRule)
        ruleEngine.registerRule(highPriorityRule)

        val context = RuleContext(
            event = RuleEvents.REQUEST_RECEIVED,
            request = RuleRequestContext(goal = "This is a priority conflict test")
        )

        val trace = ruleEngine.evaluate(context)
        assertEquals(2, trace.matchedRules.size)
        assertEquals("High Priority Rule must be selected due to higher priority score", "test_rule_high", trace.selectedRule!!.id)
        assertTrue("Conflict explanation must explain priority resolution", trace.conflictResolutionReason.contains("priority"))
    }

    @Test
    fun testNodalConditionBranchingEvaluatedByRuleEngine() = runBlocking {
        val nodalEngine = NodalEngine()

        val conditionalWorkflow = NodalWorkflow(
            id = "test_conditional_workflow",
            name = "Conditional Rule Workflow",
            description = "Tests true/false conditional branch routing",
            triggerCommands = listOf("/test_cond"),
            nodes = listOf(
                NodalNode("n1", NodeType.TRIGGER_COMMAND, "Trigger"),
                NodalNode(
                    "n2",
                    NodeType.CONDITION_NODE,
                    "Branch on Variable",
                    config = mapOf(
                        "variable" to "status",
                        "operator" to "EQUALS",
                        "value" to "APPROVED"
                    )
                ),
                NodalNode("n3_true", NodeType.RESULT_NODE, "Approved Result", config = mapOf("result" to "Success Path")),
                NodalNode("n3_false", NodeType.RESULT_NODE, "Denied Result", config = mapOf("result" to "Failure Path"))
            ),
            connections = listOf(
                NodeConnection("n1", "n2"),
                NodeConnection("n2", "n3_true", outputPort = "true"),
                NodeConnection("n2", "n3_false", outputPort = "false")
            )
        )

        nodalEngine.registerWorkflow(conditionalWorkflow)

        // Case 1: Variable is APPROVED -> should take n3_true
        val resultApproved = nodalEngine.executeWorkflow(
            workflow = conditionalWorkflow,
            command = "/test_cond",
            queryArg = "test",
            rawInput = "/test_cond test",
            initialVariables = mapOf("status" to "APPROVED")
        )
        assertTrue(resultApproved.success)
        assertTrue(resultApproved.logs.any { it.nodeId == "n3_true" })
        assertFalse(resultApproved.logs.any { it.nodeId == "n3_false" })

        // Case 2: Variable is REJECTED -> should take n3_false
        val resultDenied = nodalEngine.executeWorkflow(
            workflow = conditionalWorkflow,
            command = "/test_cond",
            queryArg = "test",
            rawInput = "/test_cond test",
            initialVariables = mapOf("status" to "REJECTED")
        )
        assertTrue(resultDenied.success)
        assertFalse(resultDenied.logs.any { it.nodeId == "n3_true" })
        assertTrue(resultDenied.logs.any { it.nodeId == "n3_false" })
    }

    @Test
    fun testCentralNervousSystemHaltsOnRuleViolation() = runBlocking {
        val cns = CentralNervousSystem()
        val result = cns.orchestrateGoal("please run rm -rf / on the host")

        assertFalse("Goal must fail due to rule violation", result.success)
        assertTrue(
            "Synthesis must indicate policy or rule blockage",
            result.synthesis.contains("Blocked by Safety Policy") || result.synthesis.contains("Blocked by")
        )
        assertTrue("No agents should participate in blocked goal", result.participatingAgents.isEmpty())
    }

    @Test
    fun testGeminiGemsSeededWithScopesAndInstructions() {
        val gems = ruleEngine.getAllRules().filter { it.isGem }
        assertTrue("Must have Gemini Gems seeded", gems.isNotEmpty())

        val globalGems = gems.filter { it.scope == "GLOBAL" }
        assertTrue("Must have global gems", globalGems.isNotEmpty())
        globalGems.forEach { gem ->
            assertTrue("Gem must have icon", gem.gemIcon.isNotBlank())
            assertTrue("Gem must have system instructions", gem.gemInstructions.isNotBlank())
            assertNull("Global gem should not be tied to single chat", gem.chatId)
        }

        val chatGems = gems.filter { it.scope == "CHAT" }
        assertTrue("Must have chat-specific gems", chatGems.isNotEmpty())
        chatGems.forEach { gem ->
            assertNotNull("Chat gem must have target chatId", gem.chatId)
        }
    }

    @Test
    fun testChatSpecificRuleFilteringAndOneTimeGlobalPromotion() = runBlocking {
        val chatScopedRule = RuleDefinition(
            id = "rule_custom_chat_test",
            name = "Custom Chat Rule",
            description = "Applies only to chat_abc",
            priority = 700,
            trigger = RuleTrigger(RuleEvents.REQUEST_RECEIVED),
            conditions = SingleCondition("request.goal", ConditionOperator.CONTAINS, "chat_keyword"),
            actions = listOf(RuleAction(RuleActionTypes.ROUTE_AGENT, target = "CodingAgent")),
            scope = "CHAT",
            chatId = "chat_abc",
            isGem = true,
            gemIcon = "💎"
        )
        ruleEngine.registerRule(chatScopedRule)

        // 1. Context matching chat_abc -> Rule should match
        val matchingContext = RuleContext(
            event = RuleEvents.REQUEST_RECEIVED,
            chatId = "chat_abc",
            request = RuleRequestContext(
                goal = "execute chat_keyword workflow",
                caller = "User",
                chatId = "chat_abc",
                rawInput = "execute chat_keyword workflow"
            )
        )
        val matchTrace = ruleEngine.evaluate(matchingContext)
        assertEquals("rule_custom_chat_test", matchTrace.selectedRule?.id)

        // 2. Context with different chat_xyz -> Rule should NOT match
        val nonMatchingContext = RuleContext(
            event = RuleEvents.REQUEST_RECEIVED,
            chatId = "chat_xyz",
            request = RuleRequestContext(
                goal = "execute chat_keyword workflow",
                caller = "User",
                chatId = "chat_xyz",
                rawInput = "execute chat_keyword workflow"
            )
        )
        val nonMatchTrace = ruleEngine.evaluate(nonMatchingContext)
        assertNotEquals("rule_custom_chat_test", nonMatchTrace.selectedRule?.id)

        // 3. User taps "Save as Global / Appear in All Chats" (one-time promote)
        val promoted = ruleEngine.promoteToGlobal("rule_custom_chat_test")
        assertNotNull(promoted)
        assertEquals("GLOBAL", promoted!!.scope)
        assertNull(promoted.chatId)

        // 4. Now context in chat_xyz should match! It appears and takes effect in all chats!
        val postPromotionTrace = ruleEngine.evaluate(nonMatchingContext)
        assertEquals("rule_custom_chat_test", postPromotionTrace.selectedRule?.id)
    }
}
