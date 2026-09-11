package com.example.agent

import com.example.agent.command.CommandRegistry
import com.example.agent.command.CommandStatus
import com.example.agent.runtime.RuntimeStateManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CommandRegistryUnitTest {

    private lateinit var registry: CommandRegistry

    @Before
    fun setUp() {
        registry = CommandRegistry.global
        RuntimeStateManager.global.resetToTextChat()
    }

    @Test
    fun testOrderIndependentVoiceModifiers() {
        // /voice
        val inv1 = registry.parse("/voice")
        assertNotNull(inv1)
        assertEquals("/voice", inv1!!.command)
        assertFalse(inv1.isPersistentOn)
        assertFalse(inv1.isPersistentOff)
        assertFalse(inv1.isAgenticRequested)
        assertEquals("", inv1.queryArg)

        // /voice /on
        val inv2 = registry.parse("/voice /on")
        assertNotNull(inv2)
        assertEquals("/voice", inv2!!.command)
        assertTrue(inv2.isPersistentOn)
        assertFalse(inv2.isPersistentOff)
        assertFalse(inv2.isAgenticRequested)

        // /voice /off
        val inv3 = registry.parse("/voice /off")
        assertNotNull(inv3)
        assertEquals("/voice", inv3!!.command)
        assertTrue(inv3.isPersistentOff)
        assertFalse(inv3.isPersistentOn)

        // /voice /agent query
        val inv4 = registry.parse("/voice /agent research AI breakthroughs")
        assertNotNull(inv4)
        assertEquals("/voice", inv4!!.command)
        assertTrue(inv4.isVoiceRequested)
        assertTrue(inv4.isAgenticRequested)
        assertTrue(inv4.isVoiceFirst)
        assertFalse(inv4.isAgentFirst)
        assertEquals("research AI breakthroughs", inv4.queryArg)

        // Order-independent: /voice /on /agent query
        val inv5 = registry.parse("/voice /on /agent solve math problem")
        assertNotNull(inv5)
        assertEquals("/voice", inv5!!.command)
        assertTrue(inv5.isPersistentOn)
        assertTrue(inv5.isAgenticRequested)
        assertEquals("solve math problem", inv5.queryArg)

        // Order-independent: /voice /agent /on query
        val inv6 = registry.parse("/voice /agent /on solve math problem")
        assertNotNull(inv6)
        assertEquals("/voice", inv6!!.command)
        assertTrue(inv6.isPersistentOn)
        assertTrue(inv6.isAgenticRequested)
        assertEquals("solve math problem", inv6.queryArg)

        // Agent-first: /agent /voice query
        val inv7 = registry.parse("/agent /voice analyze code")
        assertNotNull(inv7)
        assertEquals("/agent", inv7!!.command)
        assertTrue(inv7.isVoiceRequested)
        assertTrue(inv7.isAgenticRequested)
        assertTrue(inv7.isAgentFirst)
        assertEquals("analyze code", inv7.queryArg)
    }

    @Test
    fun testCoreCommandsParsingAndExecution() = runBlocking {
        // /help
        val helpRes = registry.executeRaw("/help")
        assertNotNull(helpRes)
        assertTrue(helpRes!!.isSuccess)
        assertTrue(helpRes.message.contains("GVONE OS UNIFIED MANUAL"))

        // /status
        val statusRes = registry.executeRaw("/status")
        assertNotNull(statusRes)
        assertTrue(statusRes!!.isSuccess)
        assertTrue(statusRes.message.contains("RUNTIME STATUS"))

        // /config list
        val cfgRes = registry.executeRaw("/config list")
        assertNotNull(cfgRes)
        assertTrue(cfgRes!!.isSuccess)
        assertTrue(cfgRes.message.contains("SYSTEM CONFIGURATION"))

        // /config set and get
        val setRes = registry.executeRaw("/config set test.key hello_gvone")
        assertNotNull(setRes)
        assertTrue(setRes!!.isSuccess)

        val getRes = registry.executeRaw("/config get test.key")
        assertNotNull(getRes)
        assertTrue(getRes!!.isSuccess)
        assertTrue(getRes.message.contains("hello_gvone"))

        // /memory set and get
        val memSet = registry.executeRaw("/memory set test.token abc-xyz-123")
        assertNotNull(memSet)
        assertTrue(memSet!!.isSuccess)

        val memGet = registry.executeRaw("/memory get test.token")
        assertNotNull(memGet)
        assertTrue(memGet!!.isSuccess)
        assertTrue(memGet.message.contains("abc-xyz-123"))

        // /context show
        val ctxRes = registry.executeRaw("/context show")
        assertNotNull(ctxRes)
        assertTrue(ctxRes!!.isSuccess)
        assertTrue(ctxRes.message.contains("CONTEXT ISOLATION REPORT"))

        // /task list
        val taskRes = registry.executeRaw("/task list")
        assertNotNull(taskRes)
        assertTrue(taskRes!!.isSuccess)
        assertTrue(taskRes.message.contains("TASK REGISTRY"))
    }

    @Test
    fun testUnknownCommandReturnsError() = runBlocking {
        val unknownRes = registry.executeRaw("/nonexistent_cmd_123")
        assertNotNull(unknownRes)
        assertEquals(CommandStatus.ERROR, unknownRes!!.status)
        assertTrue(unknownRes.message.contains("not recognized"))
    }
}
