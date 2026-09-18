package com.example

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.ui.viewmodel.ActiveSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TerminalCrashTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testOpenTerminalInMainActivity() {
        val activity = composeTestRule.activity
        activity.runOnUiThread {
            // Simulate user opening Terminal
            val viewModel = androidx.lifecycle.ViewModelProvider(activity)[com.example.ui.viewmodel.BrowserViewModel::class.java]
            viewModel.openSheet(ActiveSheet.Terminal)
        }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        activity.runOnUiThread {
            val viewModel = androidx.lifecycle.ViewModelProvider(activity)[com.example.ui.viewmodel.BrowserViewModel::class.java]
            viewModel.appendTerminalLines(listOf(
                com.example.data.terminal.TerminalLine("Test line 1", com.example.data.terminal.TerminalLineType.COMMAND),
                com.example.data.terminal.TerminalLine("Test output 1", com.example.data.terminal.TerminalLineType.OUTPUT),
                com.example.data.terminal.TerminalLine("Test line 2", com.example.data.terminal.TerminalLineType.COMMAND),
                com.example.data.terminal.TerminalLine("Test output 2", com.example.data.terminal.TerminalLineType.OUTPUT)
            ))
            viewModel.closeSheet()
            viewModel.openSheet(ActiveSheet.Terminal)
        }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()
    }
}
