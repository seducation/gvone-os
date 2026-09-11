package com.example.agent

import com.example.agent.runtime.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RuntimeModeSeparationUnitTest {

    private lateinit var runtimeManager: RuntimeStateManager

    @Before
    fun setUp() {
        runtimeManager = RuntimeStateManager.global
        runtimeManager.resetToTextChat()
    }

    @Test
    fun testDefaultStateIsTextChat() {
        val mode = runtimeManager.runtimeMode.value
        assertEquals(InteractionType.TEXT, mode.interaction)
        assertEquals(ExecutionType.CHAT, mode.execution)
        assertFalse(mode.isVoiceActive)
        assertFalse(mode.isVoice)
        assertTrue(mode.isText)
        assertFalse(mode.isAgentic)
    }

    @Test
    fun testToggleVoiceIndependently() {
        // Turn voice ON
        runtimeManager.toggleVoice(true)
        var mode = runtimeManager.runtimeMode.value
        assertEquals(InteractionType.VOICE, mode.interaction)
        assertTrue(mode.isVoiceActive)
        assertTrue(mode.isVoice)
        assertFalse(mode.isText)
        assertEquals(ExecutionType.CHAT, mode.execution) // Agent remains CHAT

        // Turn voice OFF
        runtimeManager.toggleVoice(false)
        mode = runtimeManager.runtimeMode.value
        assertEquals(InteractionType.TEXT, mode.interaction)
        assertFalse(mode.isVoiceActive)
        assertFalse(mode.isVoice)
        assertTrue(mode.isText)
        assertEquals(ExecutionType.CHAT, mode.execution)

        // Toggle voice with no argument (should turn ON)
        runtimeManager.toggleVoice()
        mode = runtimeManager.runtimeMode.value
        assertTrue(mode.isVoiceActive)
        assertTrue(mode.isVoice)

        // Toggle voice again with no argument (should turn OFF)
        runtimeManager.toggleVoice()
        mode = runtimeManager.runtimeMode.value
        assertFalse(mode.isVoiceActive)
        assertFalse(mode.isVoice)
    }

    @Test
    fun testToggleAgentIndependently() {
        // Turn agent ON
        runtimeManager.toggleAgent(true)
        var mode = runtimeManager.runtimeMode.value
        assertEquals(ExecutionType.AGENT, mode.execution)
        assertTrue(mode.isAgentic)
        assertEquals(InteractionType.TEXT, mode.interaction) // Interaction remains TEXT
        assertTrue(mode.isText)
        assertFalse(mode.isVoice)

        // Turn agent OFF
        runtimeManager.toggleAgent(false)
        mode = runtimeManager.runtimeMode.value
        assertEquals(ExecutionType.CHAT, mode.execution)
        assertFalse(mode.isAgentic)
        assertEquals(InteractionType.TEXT, mode.interaction)

        // Toggle agent with no argument (should turn ON)
        runtimeManager.toggleAgent()
        mode = runtimeManager.runtimeMode.value
        assertTrue(mode.isAgentic)

        // Toggle agent again with no argument (should turn OFF)
        runtimeManager.toggleAgent()
        mode = runtimeManager.runtimeMode.value
        assertFalse(mode.isAgentic)
    }

    @Test
    fun testTextAgentPreservesTextModeWhileEnablingAgent() {
        // Ensure starting in text
        runtimeManager.setTextMode()
        // Turn on Agent -> This is a Text Agent!
        runtimeManager.toggleAgent(true)

        val mode = runtimeManager.runtimeMode.value
        assertTrue("Agent execution must be enabled", mode.isAgentic)
        assertTrue("Text interaction must remain active for Text Agent", mode.isText)
        assertFalse("Voice interaction must remain inactive for Text Agent", mode.isVoice)
    }

    @Test
    fun testVoiceAgentPreservesVoiceWhenTogglingAgent() {
        // Turn on Voice
        runtimeManager.toggleVoice(true)
        assertTrue(runtimeManager.runtimeMode.value.isVoice)

        // Turn on Agent -> Voice Agent!
        runtimeManager.toggleAgent(true)
        val mode = runtimeManager.runtimeMode.value
        assertTrue("Agent must be active", mode.isAgentic)
        assertTrue("Voice must remain active", mode.isVoice)

        // Turn off Voice via setTextMode() -> Converts to Text Agent
        runtimeManager.setTextMode()
        val textAgentMode = runtimeManager.runtimeMode.value
        assertTrue("Agent must still be active after switching to text", textAgentMode.isAgentic)
        assertTrue("Text must now be active", textAgentMode.isText)
        assertFalse("Voice must now be inactive", textAgentMode.isVoice)
    }

    @Test
    fun testVoiceOnAgentOffCompoundState() {
        // Voice ON, Agent OFF
        runtimeManager.toggleVoice(true)
        runtimeManager.toggleAgent(false)
        val mode = runtimeManager.runtimeMode.value
        assertTrue("Voice must be ON", mode.isVoice)
        assertFalse("Agent must be OFF", mode.isAgentic)
        assertEquals(ExecutionType.CHAT, mode.execution)
        assertEquals(InteractionType.VOICE, mode.interaction)
    }

    @Test
    fun testVoiceOffAgentOnCompoundState() {
        // Voice OFF, Agent ON
        runtimeManager.toggleVoice(false)
        runtimeManager.toggleAgent(true)
        val mode = runtimeManager.runtimeMode.value
        assertFalse("Voice must be OFF", mode.isVoice)
        assertTrue("Agent must be ON", mode.isAgentic)
        assertEquals(ExecutionType.AGENT, mode.execution)
        assertEquals(InteractionType.TEXT, mode.interaction)
    }

    @Test
    fun testBothOffResetsToStandardTextChat() {
        runtimeManager.toggleVoice(true)
        runtimeManager.toggleAgent(true)
        assertTrue(runtimeManager.runtimeMode.value.isVoice)
        assertTrue(runtimeManager.runtimeMode.value.isAgentic)

        // Turn both off
        runtimeManager.toggleVoice(false)
        runtimeManager.toggleAgent(false)
        val mode = runtimeManager.runtimeMode.value
        assertFalse(mode.isVoice)
        assertFalse(mode.isAgentic)
        assertTrue(mode.isText)
        assertEquals(ExecutionType.CHAT, mode.execution)
    }
}
