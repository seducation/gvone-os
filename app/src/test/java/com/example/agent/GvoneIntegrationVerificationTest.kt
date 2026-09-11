package com.example.agent

import com.example.agent.cns.CentralNervousSystem
import com.example.agent.core.*
import com.example.agent.nodal.*
import com.example.agent.registry.AgentRegistry
import com.example.agent.runtime.ExecutionType
import com.example.agent.runtime.InteractionType
import com.example.agent.runtime.RuntimeStateManager
import com.example.agent.safety.PermissionPolicy
import com.example.agent.safety.PermissionStatus
import com.example.agent.safety.PermissionSystem
import com.example.agent.specialized.FileAgent
import com.example.agent.task.TaskLifecycleStatus
import com.example.agent.task.TaskManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * GVONE OS End-to-End & Integration Test Suite.
 * Covers the 10 core architectural verification scenarios:
 * 1. /agent search YouTube goal delegation and execution
 * 2. Permission denial and security enforcement
 * 3. Multi-agent delegation and synthesis
 * 4. Task cancellation propagation & budget limits
 * 5. Loop node execution and iterative aggregation
 * 6. Parallel node concurrent execution
 * 7. Task persistence and resume-from-checkpoint
 * 8. Runtime mode separation (Voice mode vs Agent mode)
 * 9. Evaluator outcome verification gate (never complete without verification)
 * 10. FileAgent real operations and persistent verification
 */
@RunWith(RobolectricTestRunner::class)
class GvoneIntegrationVerificationTest {

    private lateinit var runtimeState: RuntimeStateManager
    private lateinit var permissionSystem: PermissionSystem
    private lateinit var toolRegistry: ToolRegistry
    private lateinit var agentRegistry: AgentRegistry
    private lateinit var nodalEngine: NodalEngine
    private lateinit var taskManager: TaskManager
    private lateinit var cns: CentralNervousSystem

    @Before
    fun setUp() {
        runtimeState = RuntimeStateManager.global
        runtimeState.resetToTextChat()
        permissionSystem = PermissionSystem.global
        toolRegistry = ToolRegistry.global
        toolRegistry.registerStandardTools()

        agentRegistry = AgentRegistry.global
        nodalEngine = NodalEngine(runtimeState = runtimeState)
        taskManager = TaskManager.global
        cns = CentralNervousSystem.global
    }

    // Scenario 1: /agent search YouTube autonomous execution
    @Test
    fun testScenario1_AgentSearchYouTubeExecution() = runBlocking {
        val goal = "/agent search YouTube for lofi beats"
        val result = cns.orchestrateGoal(goal)

        assertTrue("CNS orchestration should succeed", result.success)
        assertTrue("Result synthesis must contain completion marker", result.synthesis.contains("[TASK COMPLETED]"))
        assertTrue("BrowserAgent or SearchAgent must be involved",
            result.participatingAgents.contains("BrowserAgent") ||
                    result.participatingAgents.contains("SearchAgent") ||
                    result.participatingAgents.contains("CommandAgent"))
    }

    // Scenario 2: Permission denial and security enforcement
    @Test
    fun testScenario2_PermissionDenialEnforcement() = runBlocking {
        // Enforce DENY policy for privileged operations
        permissionSystem.setPolicy("execute_code", PermissionPolicy.DENY)
        permissionSystem.setPolicy("system.*", PermissionPolicy.DENY)

        val check = permissionSystem.checkPermission("execute_code", "TestAgent")
        assertEquals("Permission execute_code must be denied", PermissionStatus.DENIED, check.status)

        val checkWithWildcard = permissionSystem.checkPermission("system.root", "TestAgent")
        assertEquals("Privileged wildcard permission must be denied", PermissionStatus.DENIED, checkWithWildcard.status)
    }

    // Scenario 3: Multi-agent delegation
    @Test
    fun testScenario3_MultiAgentDelegation() = runBlocking {
        val goal = "Compare document and website information"
        val result = cns.orchestrateGoal(goal)

        assertTrue("Multi-agent goal should execute", result.success)
        assertTrue("Should invoke multiple agents", result.participatingAgents.size >= 2)
        assertTrue("Must include FileAgent", result.participatingAgents.contains("FileAgent"))
        assertTrue("Must include WebReviewAgent", result.participatingAgents.contains("WebReviewAgent"))
    }

    // Scenario 4: Task cancellation and budget limits
    @Test
    fun testScenario4_TaskCancellation() = runBlocking {
        val task = taskManager.createTask("Long running background task")
        assertEquals(TaskLifecycleStatus.RUNNING, task.status)

        // Cancel task
        val cancelled = taskManager.cancelTask(task.taskId)
        assertTrue("Task cancellation should succeed", cancelled)

        val updatedTask = taskManager.getTask(task.taskId)
        assertNotNull(updatedTask)
        assertEquals(TaskLifecycleStatus.CANCELLED, updatedTask!!.status)
        assertTrue("CancellationToken must be cancelled", updatedTask.cancellationToken.isCancelled)
    }

    // Scenario 5: NodalEngine Loop Node execution
    @Test
    fun testScenario5_LoopNodeExecution() = runBlocking {
        val loopWorkflow = NodalWorkflow(
            id = "test_loop_wf",
            name = "Test Loop Processing",
            description = "Loop test workflow",
            nodes = listOf(
                NodalNode("trig", NodeType.TRIGGER_COMMAND, "Start"),
                NodalNode("loop", NodeType.LOOP, "Process Items", config = mapOf(
                    "items" to listOf("alpha", "beta", "gamma"),
                    "action" to "process"
                )),
                NodalNode("res", NodeType.RESULT_NODE, "Finish")
            ),
            connections = listOf(
                NodeConnection("trig", "loop"),
                NodeConnection("loop", "res")
            )
        )

        val result = nodalEngine.executeWorkflow(loopWorkflow, "/loop", "", "run loop")
        assertTrue("Loop workflow should complete successfully", result.success)

        val loopOutput = result.finalOutput
        assertNotNull("Loop should produce final output", loopOutput)
        assertTrue("Output should indicate processed items", loopOutput.toString().contains("Processed item 0") || loopOutput.toString().contains("alpha"))
    }

    // Scenario 6: NodalEngine Parallel Node execution
    @Test
    fun testScenario6_ParallelNodeExecution() = runBlocking {
        val parallelWorkflow = NodalWorkflow(
            id = "test_parallel_wf",
            name = "Test Parallel Execution",
            description = "Parallel test workflow",
            nodes = listOf(
                NodalNode("trig", NodeType.TRIGGER_COMMAND, "Start"),
                NodalNode("par", NodeType.PARALLEL, "Fork Tasks", config = mapOf(
                    "actions" to listOf(
                        mapOf("tool" to "file_tool", "params" to mapOf("action" to "list", "path" to "/tmp")),
                        mapOf("tool" to "file_tool", "params" to mapOf("action" to "list", "path" to "/"))
                    )
                )),
                NodalNode("res", NodeType.RESULT_NODE, "Finish")
            ),
            connections = listOf(
                NodeConnection("trig", "par"),
                NodeConnection("par", "res")
            )
        )

        val result = nodalEngine.executeWorkflow(parallelWorkflow, "/parallel", "", "run parallel")
        assertTrue("Parallel workflow should execute successfully", result.success)
    }

    // Scenario 7: Task Checkpointing & Resume across Restarts
    @Test
    fun testScenario7_TaskPersistenceAndCheckpointing() {
        val testFile = File(System.getProperty("java.io.tmpdir"), "gvone_test_ckpt_store.json")
        if (testFile.exists()) testFile.delete()

        val task = taskManager.createTask("Process multi-chapter book")
        taskManager.updateProgress(task.taskId, 0.4f, "Finished chapter 4")

        // Create checkpoint
        val cp = taskManager.createCheckpoint(task.taskId, snapshotData = "chapter=4;page=120")
        assertNotNull("Checkpoint should be created", cp)

        // Save to file
        taskManager.persistToFile(testFile)
        assertTrue("Persistent checkpoint file must exist", testFile.exists())

        // Simulate process restart by restoring
        val restoredCount = taskManager.restoreFromFile(testFile)
        assertTrue("Should restore at least 1 task", restoredCount > 0)

        // Resume from checkpoint
        val resumedTask = taskManager.resumeFromCheckpoint(cp!!.checkpointId)
        assertNotNull("Should resume task from checkpoint", resumedTask)
        assertEquals(TaskLifecycleStatus.RUNNING, resumedTask!!.status)
        assertEquals(0.4f, resumedTask.progress, 0.01f)

        testFile.delete()
    }

    // Scenario 8: Runtime Mode Separation (Voice vs Agent)
    @Test
    fun testScenario8_RuntimeModeSeparation() {
        runtimeState.toggleVoice(true)
        assertTrue("Voice mode must be active", runtimeState.runtimeMode.value.isVoiceActive)
        assertFalse("Agent mode must be inactive", runtimeState.runtimeMode.value.isAgentic)
        assertEquals(InteractionType.VOICE, runtimeState.runtimeMode.value.interaction)
        assertEquals(ExecutionType.CHAT, runtimeState.runtimeMode.value.execution)

        // Switch to Agent mode
        runtimeState.toggleVoice(false)
        runtimeState.toggleAgent(true)
        assertTrue("Agent mode must now be active", runtimeState.runtimeMode.value.isAgentic)
        assertFalse("Voice mode must now be inactive", runtimeState.runtimeMode.value.isVoiceActive)
        assertEquals(ExecutionType.AGENT, runtimeState.runtimeMode.value.execution)
    }

    // Scenario 9: Evaluator Gate (A workflow fails when outcome is not verified)
    @Test
    fun testScenario9_EvaluatorOutcomeVerification() = runBlocking {
        val strictWorkflow = NodalWorkflow(
            id = "test_strict_eval_wf",
            name = "Strict Verification Gate",
            description = "Strict evaluator workflow",
            nodes = listOf(
                NodalNode("trig", NodeType.TRIGGER_COMMAND, "Start"),
                NodalNode("obs", NodeType.OBSERVER_NODE, "Inspect", config = mapOf("target" to "environment")),
                NodalNode("eval", NodeType.EVALUATOR_NODE, "Verify Gate", config = mapOf("verify" to "contains:REQUIRED_VERIFIED_KEYWORD")),
                NodalNode("res", NodeType.RESULT_NODE, "Done")
            ),
            connections = listOf(
                NodeConnection("trig", "obs"),
                NodeConnection("obs", "eval"),
                NodeConnection("eval", "res")
            )
        )

        val result = nodalEngine.executeWorkflow(strictWorkflow, "/strict", "", "run strict")
        assertFalse("Workflow MUST fail if verification condition fails", result.success)
        assertNotNull("Error message must be present", result.error)
        assertTrue(result.error!!.contains("Verification failed"))
    }

    // Scenario 10: FileAgent real operations and verified file persistence
    @Test
    fun testScenario10_FileAgentRealOperations() = runBlocking {
        permissionSystem.setPolicy(PermissionSystem.PERM_FILESYSTEM_WRITE, PermissionPolicy.ALLOW)
        permissionSystem.setPolicy(PermissionSystem.PERM_FILESYSTEM_DELETE, PermissionPolicy.ALLOW)
        val fileAgent = FileAgent(permissionSystem = permissionSystem)
        val tempDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val testFilePath = "$tempDir/gvone_audit_test_${System.currentTimeMillis()}.txt"
        val testContent = "GVONE OS Audit Verification Content - Strict File Check"

        // 1. Write file
        val writeReq = AgentRequest(
            sourceAgent = "IntegrationTest",
            targetAgent = "FileAgent",
            action = "write_file",
            parameters = mapOf("path" to testFilePath, "content" to testContent)
        )
        val writeRes = fileAgent.execute(writeReq)
        assertTrue("File write must succeed: ${writeRes.error}", writeRes.isSuccess)

        // Verify file on disk
        val physicalFile = File(testFilePath)
        assertTrue("File must physically exist on disk", physicalFile.exists())
        assertEquals(testContent, physicalFile.readText())

        // 2. Read file
        val readReq = AgentRequest(
            sourceAgent = "IntegrationTest",
            targetAgent = "FileAgent",
            action = "read_file",
            parameters = mapOf("path" to testFilePath)
        )
        val readRes = fileAgent.execute(readReq)
        assertTrue("File read must succeed: ${readRes.error}", readRes.isSuccess)
        val content = (readRes.data as? Map<*, *>)?.get("content")?.toString() ?: readRes.data.toString()
        assertEquals(testContent, content)

        // 3. Delete file
        val deleteReq = AgentRequest(
            sourceAgent = "IntegrationTest",
            targetAgent = "FileAgent",
            action = "delete_file",
            parameters = mapOf("path" to testFilePath)
        )
        val deleteRes = fileAgent.execute(deleteReq)
        assertTrue("File delete must succeed: ${deleteRes.error}", deleteRes.isSuccess)
        assertFalse("File must no longer exist after delete", physicalFile.exists())
    }
}
