package com.example

import com.example.data.command.CommandEngine
import com.example.data.model.BrowserActionType
import com.example.data.model.BrowserSettings
import com.example.data.model.CommandType
import com.example.ui.components.suggestions.DefaultQuickPrompts
import org.junit.Assert.*
import org.junit.Test

class AddressBarContractionUnitTest {

    @Test
    fun testPinTerminalCommand_existsInBuiltInCommands() {
        val pinCmd = CommandEngine.BUILT_IN_COMMANDS.find { it.command == "/pin" }
        assertNotNull("Command /pin must be registered in BUILT_IN_COMMANDS", pinCmd)
        assertEquals(CommandType.BROWSER_ACTION, pinCmd?.type)
        assertEquals("pin_terminal", pinCmd?.template)
        assertTrue("Aliases should include /unpin or /split", pinCmd?.getAliasesList()?.contains("/unpin") == true)
        
        val action = BrowserActionType.fromActionId(pinCmd!!.template)
        assertEquals(BrowserActionType.PIN_TERMINAL, action)
    }

    @Test
    fun testDefaultQuickPrompts_includesPinTerminal() {
        val prompt = DefaultQuickPrompts.items.find { it.id == "pin_terminal" }
        assertNotNull("DefaultQuickPrompts must contain pin_terminal", prompt)
        assertEquals("Pin Terminal", prompt?.title)
        assertEquals("/pin", prompt?.promptText)
        assertEquals("Terminal", prompt?.category)
    }

    @Test
    fun testTerminalPinnedToScreen_settingsToggle() {
        val defaultSettings = BrowserSettings(terminalPinnedToScreen = false, terminalHeightFraction = 0.85f)
        assertFalse(defaultSettings.terminalPinnedToScreen)
        assertEquals(0.85f, defaultSettings.terminalHeightFraction, 0.001f)

        // Pin Terminal
        val pinnedSettings = defaultSettings.copy(
            terminalPinnedToScreen = true,
            terminalHeightFraction = 0.50f
        )
        assertTrue(pinnedSettings.terminalPinnedToScreen)
        assertEquals(0.50f, pinnedSettings.terminalHeightFraction, 0.001f)

        // Unpin Terminal
        val unpinnedSettings = pinnedSettings.copy(
            terminalPinnedToScreen = false,
            terminalHeightFraction = 0.85f
        )
        assertFalse(unpinnedSettings.terminalPinnedToScreen)
        assertEquals(0.85f, unpinnedSettings.terminalHeightFraction, 0.001f)
    }
}
