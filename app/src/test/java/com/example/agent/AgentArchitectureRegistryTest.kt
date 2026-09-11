package com.example.agent

import com.example.agent.core.*
import com.example.agent.registry.AgentRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AgentArchitectureRegistryTest {

    private lateinit var registry: AgentRegistry
    private lateinit var factory: AgentFactory
    private lateinit var router: AgentRouter
    private lateinit var executor: AgentExecutor
    private lateinit var runtime: AgentRuntime

    @Before
    fun setUp() {
        registry = AgentRegistry.global
        registry.registerAgent(com.example.agent.specialized.VoiceAgent())
        registry.registerAgent(com.example.agent.specialized.CommandAgent())
        registry.registerAgent(object : AgentBase("CodingAgent") {
            override fun identity(): String = "CodingAgent"
            override fun capabilities(): List<AgentCapability> = listOf(AgentCapability("Coding", "Code operations", listOf("code", "debug")))
            override fun observe(): Map<String, Any?> = emptyMap()
            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult = AgentResult(request.requestId, AgentStatus.COMPLETED)
        })
        registry.registerAgent(object : AgentBase("BrowserAgent") {
            override fun identity(): String = "BrowserAgent"
            override fun capabilities(): List<AgentCapability> = listOf(AgentCapability("Browser", "Web operations", listOf("browse", "yt")))
            override fun observe(): Map<String, Any?> = emptyMap()
            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult = AgentResult(request.requestId, AgentStatus.COMPLETED)
        })
        factory = AgentFactory(registry)
        router = AgentRouter(registry)
        executor = AgentExecutor(defaultTimeoutMs = 5000L)
        runtime = AgentRuntime(executor)
    }

    @Test
    fun testAgentDefinitionAndFactoryCreation() {
        val def = AgentDefinition(
            id = "custom_test_agent",
            name = "TestSpecialistAgent",
            description = "Handles specialized diagnostic algorithms",
            capabilities = listOf(
                AgentCapability(
                    name = "Diagnostics",
                    description = "System diagnosis",
                    supportedActions = listOf("diagnose", "scan")
                )
            ),
            tools = listOf("sys_scan", "log_tail"),
            model = "gemini-2.5-flash",
            contextPolicy = ContextPolicy.STRICT_ISOLATED,
            memoryPolicy = MemoryPolicy.READ_WRITE,
            executionPolicy = ExecutionPolicy.AUTONOMOUS_LOOP
        )

        val created = factory.createAndRegister(def)
        assertEquals("TestSpecialistAgent", created.identity())
        assertEquals(1, created.capabilities().size)

        // Verify retrieval in AgentRegistry
        val retrieved = registry.getAgent("TestSpecialistAgent")
        assertNotNull(retrieved)
        assertEquals("TestSpecialistAgent", retrieved!!.identity())

        // Verify capability lookup
        val matching = registry.findAgentsForCapability("diagnose")
        assertTrue(matching.any { it.identity() == "TestSpecialistAgent" })
    }

    @Test
    fun testAgentRouterIntentResolution() {
        val voiceAgent = router.routeIntent("/voice start speaking")
        assertEquals("VoiceAgent", voiceAgent.identity())

        val codingAgent = router.routeIntent("/code fix bug in authentication")
        assertEquals("CodingAgent", codingAgent.identity())

        val browserAgent = router.routeIntent("/yt lofi beats")
        assertEquals("BrowserAgent", browserAgent.identity())
    }

    @Test
    fun testAgentExecutorSuccessAndTimeout() = runBlocking {
        val agent = object : AgentBase("QuickAgent") {
            override fun identity(): String = "QuickAgent"
            override fun capabilities(): List<AgentCapability> = emptyList()
            override fun observe(): Map<String, Any?> = emptyMap()
            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
                return AgentResult(requestId = request.requestId, status = AgentStatus.COMPLETED, data = "Done fast")
            }
        }

        val res = executor.execute(agent, AgentRequest(sourceAgent = "User", targetAgent = "QuickAgent", action = "test"))
        assertTrue(res.isSuccess)
        assertEquals("Done fast", res.data)
    }

    @Test
    fun testAgentRuntimeSequentialExecution() = runBlocking {
        val agentA = object : AgentBase("AgentA") {
            override fun identity(): String = "AgentA"
            override fun capabilities(): List<AgentCapability> = emptyList()
            override fun observe(): Map<String, Any?> = emptyMap()
            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
                return AgentResult(requestId = request.requestId, status = AgentStatus.COMPLETED, data = "Result from A")
            }
        }
        val agentB = object : AgentBase("AgentB") {
            override fun identity(): String = "AgentB"
            override fun capabilities(): List<AgentCapability> = emptyList()
            override fun observe(): Map<String, Any?> = emptyMap()
            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
                val prev = request.parameters["previousOutput"]
                return AgentResult(requestId = request.requestId, status = AgentStatus.COMPLETED, data = "B processed ($prev)")
            }
        }

        val seqRes = runtime.executeSequential(listOf(agentA, agentB), "Initial Goal")
        assertTrue(seqRes.success)
        assertEquals("B processed (Result from A)", seqRes.finalSynthesis)
        assertEquals(RuntimeExecutionState.COMPLETED, runtime.state.value)
    }

    @Test
    fun testAgentRuntimeParallelExecution() = runBlocking {
        val agentX = object : AgentBase("AgentX") {
            override fun identity(): String = "AgentX"
            override fun capabilities(): List<AgentCapability> = emptyList()
            override fun observe(): Map<String, Any?> = emptyMap()
            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
                return AgentResult(requestId = request.requestId, status = AgentStatus.COMPLETED, data = "X output")
            }
        }
        val agentY = object : AgentBase("AgentY") {
            override fun identity(): String = "AgentY"
            override fun capabilities(): List<AgentCapability> = emptyList()
            override fun observe(): Map<String, Any?> = emptyMap()
            override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
                return AgentResult(requestId = request.requestId, status = AgentStatus.COMPLETED, data = "Y output")
            }
        }

        val parRes = runtime.executeParallel(listOf(agentX, agentY), "Parallel Goal")
        assertTrue(parRes.success)
        assertTrue(parRes.finalSynthesis.contains("AgentX: X output"))
        assertTrue(parRes.finalSynthesis.contains("AgentY: Y output"))
    }
}
