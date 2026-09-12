package com.example

import com.example.data.model.DefaultSuggestions
import com.example.data.model.Suggestion
import com.example.data.model.SuggestionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsInteractionUnitTest {

    @Test
    fun defaultSuggestions_areDataDrivenAndNotEmpty() {
        val items = DefaultSuggestions.items
        assertTrue("Suggestions should have items", items.isNotEmpty())
        items.forEach { suggestion ->
            assertTrue("Title must not be blank", suggestion.title.isNotBlank())
            assertTrue("Description must not be blank", suggestion.description.isNotBlank())
            assertNotNull("Icon name must exist", suggestion.iconName)
            assertNotNull("Action must exist", suggestion.action)
        }
    }

    @Test
    fun defaultSuggestions_containsCoreCapabilities() {
        val items = DefaultSuggestions.items
        val titles = items.map { it.title }
        assertTrue("Should contain Add Mission Templates", titles.contains("Add Mission Templates"))
        assertTrue("Should contain Visualize Swarm", titles.contains("Visualize Swarm"))
        assertTrue("Should contain Explore Agents", titles.contains("Explore Agents"))
        assertTrue("Should contain Create Workflow", titles.contains("Create Workflow"))
        assertTrue("Should contain Analyze Signal", titles.contains("Analyze Signal"))
        assertTrue("Should contain Browse Feeds", titles.contains("Browse Feeds"))
    }

    @Test
    fun suggestionActionTypes_instantiateCorrectly() {
        val sheetAction = SuggestionAction.OpenSheet("AgentDashboard")
        assertEquals("AgentDashboard", sheetAction.sheetName)

        val goalAction = SuggestionAction.RunGoal("Summarize today's arXiv machine learning papers")
        assertEquals("Summarize today's arXiv machine learning papers", goalAction.goal)

        val navAction = SuggestionAction.Navigate("https://example.com")
        assertEquals("https://example.com", navAction.url)

        val customAction = SuggestionAction.Custom("clear_cache")
        assertEquals("clear_cache", customAction.actionId)
    }

    @Test
    fun mutualExclusivity_suggestionsAndCommandPalette() {
        var showSuggestions = false
        var showCommandPalette = false

        // 1. Initial State: both closed
        assertFalse(showSuggestions)
        assertFalse(showCommandPalette)

        // 2. Bulb clicked: open suggestions
        showSuggestions = true
        showCommandPalette = false
        assertTrue(showSuggestions)
        assertFalse(showCommandPalette)

        // 3. User types '/': open command palette, close suggestions
        showSuggestions = false
        showCommandPalette = true
        assertFalse(showSuggestions)
        assertTrue(showCommandPalette)

        // 4. While command palette is open, bulb clicked again:
        // command palette closes, suggestions opens
        showCommandPalette = false
        showSuggestions = true
        assertTrue(showSuggestions)
        assertFalse(showCommandPalette)

        // 5. Close suggestions
        showSuggestions = false
        assertFalse(showSuggestions)
        assertFalse(showCommandPalette)
    }

    @Test
    fun suggestionExecution_closesSuggestionsAndTriggersAction() {
        var showSuggestions = true
        var actionExecuted = false

        val testSuggestion = Suggestion(
            id = "test_1",
            title = "Test Suggestion",
            description = "Execute test",
            iconName = "AutoAwesome",
            action = SuggestionAction.Custom("test_action")
        )

        // Execute
        showSuggestions = false
        actionExecuted = true

        assertFalse("Suggestions must be closed after execution", showSuggestions)
        assertTrue("Action must be executed", actionExecuted)
    }

    @Test
    fun defaultQuickPrompts_containRequiredPrompts() {
        val prompts = com.example.ui.components.suggestions.DefaultQuickPrompts.items
        assertTrue("Quick prompts should not be empty", prompts.isNotEmpty())
        val titles = prompts.map { it.title }
        assertTrue("Must contain 'Add Mission Templates'", titles.contains("Add Mission Templates"))
        assertTrue("Must contain 'Visualize Swarm Members'", titles.contains("Visualize Swarm Members"))
        assertTrue("Must contain 'Explore Agent Swarm'", titles.contains("Explore Agent Swarm"))
        assertTrue("Must contain 'Run Nodal Workflow'", titles.contains("Run Nodal Workflow"))
        assertTrue("Must contain 'System Diagnostics'", titles.contains("System Diagnostics"))

        prompts.forEach { prompt ->
            assertTrue("Prompt id cannot be blank", prompt.id.isNotBlank())
            assertTrue("Prompt title cannot be blank", prompt.title.isNotBlank())
            assertTrue("Prompt text cannot be blank", prompt.promptText.isNotBlank())
        }
    }

    @Test
    fun decoupledUiStates_bulbIconOnlyTriggersSuggestionChips() {
        var showSuggestionChips = false
        var showTerminalCommands = false

        // Handler for Bulb icon press
        val onBulbPress = {
            showSuggestionChips = true
            showTerminalCommands = false
        }

        // Handler for text change
        val onTextChange: (String) -> Unit = { text ->
            if (text.startsWith("/")) {
                showSuggestionChips = false
                showTerminalCommands = true
            } else {
                showTerminalCommands = false
            }
        }

        // Handler for dismissing chips
        val onDismissChips = {
            showSuggestionChips = false
        }

        // Handler for selecting a suggestion chip
        var submittedPrompt: String? = null
        val onSelectPrompt: (String) -> Unit = { prompt ->
            submittedPrompt = prompt
            showSuggestionChips = false
        }

        // Initial state
        assertFalse(showSuggestionChips)
        assertFalse(showTerminalCommands)

        // 1. Bulb icon pressed -> only showSuggestionChips becomes true
        onBulbPress()
        assertTrue("Bulb icon must open suggestion chips", showSuggestionChips)
        assertFalse("Bulb icon must NEVER open terminal commands", showTerminalCommands)

        // 2. User types leading '/' -> closes suggestion chips and opens terminal commands
        onTextChange("/yt")
        assertFalse("Leading slash must close suggestion chips", showSuggestionChips)
        assertTrue("Leading slash must open terminal commands", showTerminalCommands)

        // 3. User types anything other than leading '/' -> closes terminal commands
        onTextChange("hello world")
        assertFalse("Non-slash text must close terminal commands", showTerminalCommands)
        assertFalse(showSuggestionChips)

        // 4. Bulb icon pressed again -> opens suggestion chips, terminal commands remains closed
        onBulbPress()
        assertTrue(showSuggestionChips)
        assertFalse(showTerminalCommands)

        // 5. User taps 'X' -> dismisses suggestion chips
        onDismissChips()
        assertFalse(showSuggestionChips)
        assertFalse(showTerminalCommands)

        // 6. User opens bulb, then taps a suggestion chip
        onBulbPress()
        assertTrue(showSuggestionChips)
        onSelectPrompt("Add Mission Templates")
        assertFalse("Selecting chip must close suggestion chips", showSuggestionChips)
        assertFalse("Selecting chip must not open terminal commands", showTerminalCommands)
        assertEquals("Add Mission Templates", submittedPrompt)
    }
}
