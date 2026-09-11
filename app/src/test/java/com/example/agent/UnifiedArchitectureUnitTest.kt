package com.example.agent

import com.example.agent.cns.CentralNervousSystem
import com.example.agent.cns.CnsWorkflowResult
import com.example.agent.core.*
import com.example.agent.memory.ContextRouter
import com.example.agent.nodal.*
import com.example.agent.runtime.RuntimeStateManager
import com.example.agent.specialized.CommandAgent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UnifiedArchitectureUnitTest {

    private lateinit var runtimeState: RuntimeStateManager
    private lateinit var contextRouter: ContextRouter
    private lateinit var toolRegistry: ToolRegistry

    @Before
    fun setUp() {
        runtimeState = RuntimeStateManager.global
        contextRouter = ContextRouter()
        toolRegistry = ToolRegistry.global
        toolRegistry.clear()
    }

    @Test
    fun testToolRegistryAndExecution() = runBlocking {
        var executed = false
        val testTool = object : Tool {
            override val name: String = "test_search"
            override val description: String = "Searches test documents"
            override suspend fun execute(parameters: Map<String, Any?>): ToolResult {
                executed = true
                val query = parameters["query"]?.toString() ?: ""
                return if (query == "valid") {
                    ToolResult(success = true, data = "Found 42 results")
                } else {
                    ToolResult(success = false, error = "Invalid query parameter")
                }
            }
        }

        toolRegistry.register(testTool)
        assertTrue(toolRegistry.hasTool("test_search"))
        assertEquals(testTool, toolRegistry.getTool("test_search"))

        val successRes = toolRegistry.getTool("test_search")!!.execute(mapOf("query" to "valid"))
        assertTrue(successRes.success)
        assertEquals("Found 42 results", successRes.data)
        assertTrue(executed)

        val failRes = toolRegistry.getTool("test_search")!!.execute(mapOf("query" to "bad"))
        assertFalse(failRes.success)
        assertEquals("Invalid query parameter", failRes.error)
    }

    @Test
    fun testNodalEvaluatorFailsWhenConditionNotMet() = runBlocking {
        // Build a workflow with an EVALUATOR_NODE that fails
        val failWorkflow = NodalWorkflow(
            id = "test_fail_wf",
            name = "Test Workflow with Strict Verification",
            description = "Fails when condition is not satisfied",
            triggerCommands = listOf("/strict_test"),
            nodes = listOf(
                NodalNode("node_1", NodeType.TRIGGER_COMMAND, "Trigger"),
                NodalNode("node_2", NodeType.OBSERVER_NODE, "Observe State", config = mapOf("target" to "state")),
                NodalNode("node_3", NodeType.EVALUATOR_NODE, "Verify Output Contains Magic", config = mapOf("verify" to "contains:NON_EXISTENT_MAGIC_STRING")),
                NodalNode("node_4", NodeType.RESULT_NODE, "Finalize")
            ),
            connections = listOf(
                NodeConnection(fromNodeId = "node_1", toNodeId = "node_2"),
                NodeConnection(fromNodeId = "node_2", toNodeId = "node_3"),
                NodeConnection(fromNodeId = "node_3", toNodeId = "node_4")
            )
        )

        val nodalEngine = NodalEngine(runtimeState = runtimeState)
        nodalEngine.registerWorkflow(failWorkflow)

        val result = nodalEngine.executeWorkflow(failWorkflow, "/strict_test", "run", "/strict_test run")

        // CRITICAL CHECK: master prompt tenet "A workflow is successful only when its required outcome is verified."
        assertFalse("Workflow MUST fail when evaluator condition fails", result.success)
        assertNotNull("Error message must be present", result.error)
        assertTrue("Error must mention verification failure", result.error!!.contains("Verification failed"))
    }

    @Test
    fun testNodalEvaluatorSucceedsWhenConditionMet() = runBlocking {
        val passWorkflow = NodalWorkflow(
            id = "test_pass_wf",
            name = "Test Workflow with Satisfied Verification",
            description = "Passes when condition is satisfied",
            triggerCommands = listOf("/pass_test"),
            nodes = listOf(
                NodalNode("node_1", NodeType.TRIGGER_COMMAND, "Trigger"),
                NodalNode("node_2", NodeType.OBSERVER_NODE, "Observe State", config = mapOf("target" to "state")),
                NodalNode("node_3", NodeType.EVALUATOR_NODE, "Verify State Exists", config = mapOf("verify" to "contains:Mode=")),
                NodalNode("node_4", NodeType.RESULT_NODE, "Finalize")
            ),
            connections = listOf(
                NodeConnection(fromNodeId = "node_1", toNodeId = "node_2"),
                NodeConnection(fromNodeId = "node_2", toNodeId = "node_3"),
                NodeConnection(fromNodeId = "node_3", toNodeId = "node_4")
            )
        )

        val nodalEngine = NodalEngine(runtimeState = runtimeState)
        nodalEngine.registerWorkflow(passWorkflow)

        val result = nodalEngine.executeWorkflow(passWorkflow, "/pass_test", "run", "/pass_test run")

        assertTrue("Workflow must succeed when evaluator condition is satisfied", result.success)
        assertNull("Error must be null", result.error)
    }

    @Test
    fun testContextIsolationPreventsContextLeakageBetweenTasks() {
        val taskA = runtimeState.activateAgentOnly("Research quantum algorithms")
        assertNotNull(taskA)
        contextRouter.getOrCreateTaskContext(taskA!!)
        contextRouter.recordTaskObservation(taskA.taskId, "Found paper", "Superposition in Shor's algorithm")

        val taskAContext = contextRouter.getTaskContext(taskA.taskId)
        assertNotNull(taskAContext)
        assertEquals(1, taskAContext!!.stepHistory.size)
        assertEquals("Superposition in Shor's algorithm", taskAContext.toolOutputs.first())

        // Archive Task A upon completion
        runtimeState.completeTask(taskA.taskId, "Completed research on quantum algorithms")
        contextRouter.archiveTaskContext(taskA.taskId, "Completed research", isSuccess = true)

        // Verify Task A context was cleaned from active contexts
        assertNull("Task A context must be archived and removed from active pool", contextRouter.getTaskContext(taskA.taskId))

        // Start Task B
        val taskB = runtimeState.activateAgentOnly("Book a hotel room in Paris")
        assertNotNull(taskB)
        val taskBContext = contextRouter.getOrCreateTaskContext(taskB!!)

        // CRITICAL CHECK: Task B must NOT contain any observations or context from Task A
        assertEquals("Task B stepHistory must be empty and completely isolated from Task A", 0, taskBContext.stepHistory.size)
        assertEquals("Task B toolOutputs must be empty", 0, taskBContext.toolOutputs.size)
        assertFalse(
            "Task B context must not leak Task A's data",
            taskBContext.toolOutputs.any { it.contains("quantum") }
        )
    }

    @Test
    fun testCommandAgentDelegatesToOrchestrator() = runBlocking {
        var orchestratorInvoked = false
        val dispatchedRequests = mutableListOf<AgentRequest>()

        val mockOrchestrator = object : AgentOrchestrator {
            override suspend fun orchestrateGoal(userGoal: String): CnsWorkflowResult {
                orchestratorInvoked = true
                return CnsWorkflowResult(
                    workflowId = "wf_1",
                    taskId = "task_test",
                    userGoal = userGoal,
                    success = true,
                    synthesis = "Orchestrated: $userGoal",
                    participatingAgents = listOf("MockAgent"),
                    subResults = emptyMap(),
                    durationMs = 10L
                )
            }

            override suspend fun dispatchToAgent(agentName: String, request: AgentRequest): AgentResult {
                orchestratorInvoked = true
                dispatchedRequests.add(request)
                return AgentResult(request.requestId, AgentStatus.COMPLETED, "Dispatched to $agentName for ${request.parameters["goal"] ?: request.action}")
            }

            override fun registerAgent(agent: Agent) {}
            override fun getAgent(name: String): Agent? = null
            override fun getAllAgents(): List<Agent> = emptyList()
        }

        val commandAgent = CommandAgent(
            runtimeState = runtimeState,
            orchestrator = mockOrchestrator
        )

        val request = AgentRequest(
            sourceAgent = "User",
            targetAgent = "CommandAgent",
            action = "execute_command",
            parameters = mapOf("command" to "/agent analyze market trends")
        )

        val result = commandAgent.execute(request)

        assertTrue("Result must be success", result.isSuccess)
        assertTrue("Orchestrator must be invoked through unified dispatch pipeline", orchestratorInvoked)
        assertTrue(
            "Dispatched requests must include autonomous goal execution",
            dispatchedRequests.any { it.action == "execute_task" && it.parameters["goal"] == "analyze market trends" }
        )
        assertTrue(
            "Dispatched requests must include DOM/environment observation step",
            dispatchedRequests.any { it.action == "observe_dom" }
        )
    }
}
