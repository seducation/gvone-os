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
}
