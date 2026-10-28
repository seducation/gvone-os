package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.unit.dp
import com.example.ui.screens.TerminalScreen
import com.example.ui.viewmodel.ActiveSheet
import com.example.ui.viewmodel.BrowserViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TerminalCrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTypingInTerminal() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = BrowserViewModel(app)
        vm.openSheet(ActiveSheet.Terminal)

        composeTestRule.setContent {
            TerminalScreen(
                viewModel = vm,
                isAddressBarBottom = true,
                addressBarBottomPadding = 64.dp,
                isFullScreen = false,
                autoFocus = true,
                isAddressBarWriting = false,
                onToggleFullScreen = {},
                onOpenAgentDashboard = {},
                onOpenPhotos = {},
                onOpenCamera = {},
                onOpenFiles = {},
                selectedPhotoUri = null,
                onClearSelectedPhotoUri = {},
                selectedFileUri = null,
                selectedFileName = null,
                onClearSelectedFileUri = {},
                onClose = {}
            )
        }

        // Now find the input field and type into it
        composeTestRule.onNodeWithTag("terminal_input_field").performTextInput("ls -la")
    }

    @Test
    fun testMainActivityLaunchAndTerminalOpen() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        
        scenario.onActivity { activity ->
            // Open SafariActions
            activity.browserViewModel.openSheet(ActiveSheet.SafariActions)
        }
        composeTestRule.waitForIdle()

        scenario.onActivity { activity ->
            // Now click Terminal from the sheet (same as onOpenTerminal())
            activity.browserViewModel.openSheet(ActiveSheet.Terminal)
        }
        composeTestRule.waitForIdle()

        // 3. Now type in terminal
        composeTestRule.onNodeWithTag("terminal_input_field", useUnmergedTree = true).performTextInput("hello")
        composeTestRule.waitForIdle()
    }

    @Test
    fun testSelectTargetAndTypeInTerminal() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.browserViewModel.openSheet(ActiveSheet.Terminal)
            activity.browserViewModel.setTerminalTargetDirectory("Projects/KotlinCalculator")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("terminal_input_field", useUnmergedTree = true).performTextInput("echo test")
        composeTestRule.waitForIdle()
    }

    @Test
    fun testClickProjectChatInProjectInspector() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = BrowserViewModel(app)
        composeTestRule.setContent {
            com.example.ui.screens.ide.ProjectInspectorView(
                fileSystem = vm.fileSystem,
                onClose = {}
            )
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testClickProjectChatInTabOverview() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.browserViewModel.openSheet(ActiveSheet.TabOverview)
        }
        composeTestRule.waitForIdle()
    }
}
