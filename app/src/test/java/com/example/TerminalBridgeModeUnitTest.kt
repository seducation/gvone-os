package com.example

import com.example.data.terminal.TerminalBridgeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalBridgeModeUnitTest {

    @Test
    fun `terminal bridge modes include chat web and terminal`() {
        val modes = TerminalBridgeMode.values()
        assertEquals(3, modes.size)
        assertTrue(modes.contains(TerminalBridgeMode.CHAT))
        assertTrue(modes.contains(TerminalBridgeMode.WEB))
        assertTrue(modes.contains(TerminalBridgeMode.TERMINAL))
    }

    @Test
    fun `cycling bridge modes rotates properly through chat web and terminal`() {
        var current = TerminalBridgeMode.CHAT
        val cycle = { mode: TerminalBridgeMode ->
            when (mode) {
                TerminalBridgeMode.CHAT -> TerminalBridgeMode.WEB
                TerminalBridgeMode.WEB -> TerminalBridgeMode.TERMINAL
                TerminalBridgeMode.TERMINAL -> TerminalBridgeMode.CHAT
            }
        }

        current = cycle(current)
        assertEquals(TerminalBridgeMode.WEB, current)

        current = cycle(current)
        assertEquals(TerminalBridgeMode.TERMINAL, current)

        current = cycle(current)
        assertEquals(TerminalBridgeMode.CHAT, current)
    }

    @Test
    fun `terminal bridge mode names and values match specification`() {
        assertEquals("CHAT", TerminalBridgeMode.CHAT.name)
        assertEquals("WEB", TerminalBridgeMode.WEB.name)
        assertEquals("TERMINAL", TerminalBridgeMode.TERMINAL.name)
    }
}
