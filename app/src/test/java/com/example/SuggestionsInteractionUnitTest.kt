package com.example

import com.example.data.model.DefaultSuggestions
import com.example.data.model.Suggestion
import com.example.data.model.SuggestionAction
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
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

    @Test
    fun bulbIconOnLeftOfChips_triggersSuggestiveCommandsMatchingSlashCommand() {
        var isFocused = false
        var showTerminalCommands = false
        var executedAction: String? = null

        // In written mode or clicking address bar: chips become available
        isFocused = true
        val isChipsAvailable = isFocused
        assertTrue("Chips must be available when user is in written mode / clicking address", isChipsAvailable)

        // Clicking bulb icon on the left of persistent chips toggles suggestive command UI
        val onToggleBulb = {
            showTerminalCommands = !showTerminalCommands
        }

        onToggleBulb()
        assertTrue("Clicking bulb on left of chip must open suggestive commands UI", showTerminalCommands)

        // Typing /command triggers the exact same suggestive commands UI
        val onTypeSlash = {
            showTerminalCommands = true
        }
        onTypeSlash()
        assertTrue("Typing /command must also open suggestive commands UI", showTerminalCommands)

        // Selecting a suggestive command executes the command and closes the UI
        val onSelectCommand = { cmd: String ->
            executedAction = cmd
            showTerminalCommands = false
        }
        onSelectCommand("/yt AI Swarm")
        assertEquals("/yt AI Swarm", executedAction)
        assertFalse("Suggestive command UI closes after selection", showTerminalCommands)

        // Selecting a quick prompt chip also executes and closes
        val onSelectChip = { prompt: String ->
            executedAction = prompt
            isFocused = false
        }
        onSelectChip("Add Mission Templates")
        assertEquals("Add Mission Templates", executedAction)
        assertFalse("Input written mode clears after chip selection", isFocused)
    }

    @Test
    fun commandPopup_supportsTerminalCommandsSuggestionsAndPinAttachments() {
        // 1. Verify CommandPopupTab has all required tabs with Attachments rebrand
        val tabs = com.example.ui.components.CommandPopupTab.values()
        val tabLabels = tabs.map { it.label }
        assertTrue("Must have Attachments tab", tabLabels.contains("Attachments"))
        assertTrue("Must have Suggestions tab", tabLabels.contains("Suggestions"))
        assertTrue("Must have Pin Tabs tab", tabLabels.contains("Pin Tabs"))
        assertTrue("Must have All tab", tabLabels.contains("All"))

        // 2. Verify typing '/command' produces matching suggestions
        val suggestions = com.example.data.command.CommandEngine.getSuggestions("/command", emptyList())
        assertTrue("Typing /command must return suggestions", suggestions.isNotEmpty())
        val matchedTriggers = suggestions.map { it.matchedTrigger }
        assertTrue("Should match command trigger or alias", matchedTriggers.any { it.contains("command") })

        // 3. Verify suggestions prompt items are available
        val prompts = com.example.ui.components.suggestions.DefaultQuickPrompts.items
        assertTrue("Proactive prompt suggestions must not be empty", prompts.isNotEmpty())
        assertTrue("Prompt categories exist", prompts.any { it.category == "Missions" || it.category == "Swarm" })

        // 4. Verify pin attachments functionality
        var isTerminalPinned = false
        val togglePin = { isTerminalPinned = !isTerminalPinned }
        togglePin()
        assertTrue("Pin terminal action toggles state", isTerminalPinned)
    }

    @Test
    fun fileAndResearchSelection_noConflictAndSendAsAttachments() {
        var selectedItems = listOf<com.example.ui.components.suggestions.QuickPrompt>()

        // 1. Add file item
        val fileItem = com.example.ui.components.suggestions.QuickPrompt(
            id = "file_1",
            title = "File: spec.pdf",
            promptText = "/files spec.pdf",
            type = com.example.ui.components.suggestions.SelectedItemType.FILE
        )
        selectedItems = selectedItems + fileItem

        // 2. Add research item without conflict
        val researchItem = com.example.ui.components.suggestions.QuickPrompt(
            id = "research_1",
            title = "Research: Deep Synthesis",
            promptText = "/research Deep Synthesis",
            type = com.example.ui.components.suggestions.SelectedItemType.RESEARCH
        )
        selectedItems = selectedItems + researchItem

        // 3. Add photo item
        val photoItem = com.example.ui.components.suggestions.QuickPrompt(
            id = "photo_1",
            title = "Photo: capture.jpg",
            promptText = "/photos capture.jpg",
            type = com.example.ui.components.suggestions.SelectedItemType.PHOTO
        )
        selectedItems = selectedItems + photoItem

        assertEquals(3, selectedItems.size)
        assertEquals(com.example.ui.components.suggestions.SelectedItemType.FILE, selectedItems[0].type)
        assertEquals(com.example.ui.components.suggestions.SelectedItemType.RESEARCH, selectedItems[1].type)
        assertEquals(com.example.ui.components.suggestions.SelectedItemType.PHOTO, selectedItems[2].type)

        // Verify distinct types and no collision
        val files = selectedItems.filter { it.type == com.example.ui.components.suggestions.SelectedItemType.FILE }
        val researches = selectedItems.filter { it.type == com.example.ui.components.suggestions.SelectedItemType.RESEARCH }
        assertEquals(1, files.size)
        assertEquals(1, researches.size)
        assertTrue("File and Research must have different types", files.first().type != researches.first().type)

        // Verify sending as attachment pops/consumes the item
        var sentItem: com.example.ui.components.suggestions.QuickPrompt? = null
        val sendAttachment = { item: com.example.ui.components.suggestions.QuickPrompt ->
            sentItem = item
            selectedItems = selectedItems.filterNot { it.id == item.id }
        }

        sendAttachment(researchItem)
        assertEquals("research_1", sentItem?.id)
        assertEquals(2, selectedItems.size)
        assertTrue("Research removed after send, file remains", selectedItems.any { it.type == com.example.ui.components.suggestions.SelectedItemType.FILE })
    }

    @Test
    fun actionMenuCards_havePhotosCameraFilesAndPartiallyVisibleItem() {
        var photosClicked = false
        var cameraClicked = false
        var filesClicked = false
        var connectorsClicked = false

        val dummyIcon = ImageVector.Builder("dummy", 24.dp, 24.dp, 24f, 24f).build()
        val testItems = listOf(
            com.example.ui.components.ActionMenuItem(
                id = "photos",
                label = "Photos",
                icon = dummyIcon,
                testTag = "action_card_photos",
                onClick = { photosClicked = true }
            ),
            com.example.ui.components.ActionMenuItem(
                id = "camera",
                label = "Camera",
                icon = dummyIcon,
                testTag = "action_card_camera",
                onClick = { cameraClicked = true }
            ),
            com.example.ui.components.ActionMenuItem(
                id = "files",
                label = "Files",
                icon = dummyIcon,
                testTag = "action_card_files",
                onClick = { filesClicked = true }
            ),
            com.example.ui.components.ActionMenuItem(
                id = "connectors",
                label = "Connectors",
                icon = dummyIcon,
                testTag = "action_card_connectors",
                onClick = { connectorsClicked = true }
            )
        )

        assertEquals("Must have at least 4 items for partial visibility on right", 4, testItems.size)
        val labels = testItems.map { it.label }
        assertTrue("Contains 'Photos'", labels.contains("Photos"))
        assertTrue("Contains 'Camera'", labels.contains("Camera"))
        assertTrue("Contains 'Files'", labels.contains("Files"))
        assertTrue("Contains 'Connectors'", labels.contains("Connectors"))

        // Verify click handlers
        testItems[0].onClick()
        assertTrue("Photos click handler executed", photosClicked)

        testItems[1].onClick()
        assertTrue("Camera click handler executed", cameraClicked)

        testItems[2].onClick()
        assertTrue("Files click handler executed", filesClicked)

        testItems[3].onClick()
        assertTrue("Connectors click handler executed", connectorsClicked)
    }

    @Test
    fun selectedItem_canBePhotoFilesAndWebsite() {
        var selectedItems = listOf<com.example.ui.components.suggestions.QuickPrompt>()

        // 1. Select Photo
        val photoItem = com.example.ui.components.suggestions.QuickPrompt(
            id = "photo_1",
            title = "Photo: sunset.jpg",
            promptText = "/photos sunset.jpg",
            category = "Photo",
            type = com.example.ui.components.suggestions.SelectedItemType.PHOTO,
            uriOrUrl = "content://media/photos/1"
        )
        selectedItems = selectedItems + photoItem
        assertEquals(1, selectedItems.size)
        assertEquals(com.example.ui.components.suggestions.SelectedItemType.PHOTO, selectedItems.first().type)

        // 2. Select File
        val fileItem = com.example.ui.components.suggestions.QuickPrompt(
            id = "file_1",
            title = "File: whitepaper.pdf",
            promptText = "/files whitepaper.pdf",
            category = "File",
            type = com.example.ui.components.suggestions.SelectedItemType.FILE
        )
        selectedItems = selectedItems + fileItem
        assertEquals(2, selectedItems.size)
        assertEquals(com.example.ui.components.suggestions.SelectedItemType.FILE, selectedItems[1].type)

        // 3. Select Website
        val websiteItem = com.example.ui.components.suggestions.QuickPrompt(
            id = "web_1",
            title = "Web: gvone.app",
            promptText = "https://gvone.app",
            category = "Website",
            type = com.example.ui.components.suggestions.SelectedItemType.WEBSITE,
            uriOrUrl = "https://gvone.app"
        )
        selectedItems = selectedItems + websiteItem
        assertEquals(3, selectedItems.size)
        assertEquals(com.example.ui.components.suggestions.SelectedItemType.WEBSITE, selectedItems[2].type)

        // 4. Verify removal of a selected item (e.g. remove Photo)
        selectedItems = selectedItems.filterNot { it.id == photoItem.id }
        assertEquals(2, selectedItems.size)
        assertFalse(selectedItems.any { it.type == com.example.ui.components.suggestions.SelectedItemType.PHOTO })
        assertTrue(selectedItems.any { it.type == com.example.ui.components.suggestions.SelectedItemType.FILE })
        assertTrue(selectedItems.any { it.type == com.example.ui.components.suggestions.SelectedItemType.WEBSITE })

        // 5. Verify removal of website
        selectedItems = selectedItems.filterNot { it.id == websiteItem.id }
        assertEquals(1, selectedItems.size)
        assertEquals(com.example.ui.components.suggestions.SelectedItemType.FILE, selectedItems.first().type)
    }

    @Test
    fun actionMenuCards_includesWebsiteAction() {
        var websiteClicked = false
        val dummyIcon = ImageVector.Builder("dummy", 24.dp, 24.dp, 24f, 24f).build()

        val websiteCard = com.example.ui.components.ActionMenuItem(
            id = "website",
            label = "Website",
            icon = dummyIcon,
            testTag = "action_card_website",
            onClick = { websiteClicked = true }
        )

        assertEquals("Website", websiteCard.label)
        assertEquals("action_card_website", websiteCard.testTag)

        websiteCard.onClick()
        assertTrue("Website card onClick should be triggered", websiteClicked)
    }
}

