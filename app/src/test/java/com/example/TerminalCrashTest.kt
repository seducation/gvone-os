package com.example

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.TerminalScreen
import com.example.ui.theme.GVONEBrowserTheme
import com.example.ui.viewmodel.BrowserViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalCrashTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testTerminalScreenOpensWithoutCrash() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = BrowserViewModel(app)

        composeTestRule.setContent {
            GVONEBrowserTheme {
                TerminalScreen(
                    viewModel = viewModel,
                    isAddressBarBottom = true,
                    addressBarBottomPadding = 0.dp,
                    isFullScreen = false,
                    autoFocus = true,
                    isAddressBarWriting = false,
                    onToggleFullScreen = {},
                    onOpenAgentDashboard = {},
                    onClose = {}
                )
            }
        }
    }
}
