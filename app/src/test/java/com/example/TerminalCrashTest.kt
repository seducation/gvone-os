package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
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
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testOpenTerminalDirectly() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val vm = ViewModelProvider(activity)[BrowserViewModel::class.java]

        activity.runOnUiThread {
            vm.openSheet(ActiveSheet.Terminal)
        }
        composeTestRule.waitForIdle()

        // Verify terminal screen rendered
        composeTestRule.onNodeWithTag("terminal_screen").assertExists()
        composeTestRule.onNodeWithTag("terminal_input_field").assertExists()
        composeTestRule.onNodeWithTag("terminal_send_button").assertExists()
        composeTestRule.onNodeWithTag("terminal_history_button").assertExists()

        // Close terminal
        activity.runOnUiThread {
            vm.closeSheet()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testOpenTerminalFromSafariActions() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val vm = ViewModelProvider(activity)[BrowserViewModel::class.java]

        activity.runOnUiThread {
            vm.openSheet(ActiveSheet.SafariActions)
        }
        composeTestRule.waitForIdle()

        activity.runOnUiThread {
            vm.openSheet(ActiveSheet.Terminal)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("terminal_screen").assertExists()
    }

    @Test
    fun testOpenControlActionSheet() {
        composeTestRule.waitForIdle()
        val activity = composeTestRule.activity
        val vm = ViewModelProvider(activity)[BrowserViewModel::class.java]

        composeTestRule.onNodeWithTag("address_bar_control_toggle_button").performClick()
        composeTestRule.waitForIdle()
    }
}








