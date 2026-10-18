package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GVONE Browser", appName)
  }

  @get:org.junit.Rule val composeTestRule = androidx.compose.ui.test.junit4.createComposeRule()

  @Test
  fun `open terminal sheet does not crash viewmodel`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.BrowserViewModel(app)
    vm.openSheet(com.example.ui.viewmodel.ActiveSheet.Terminal)
    assertEquals(com.example.ui.viewmodel.ActiveSheet.Terminal, vm.activeSheet.value)
  }

  @Test
  fun `render terminal screen in compose`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.BrowserViewModel(app)
    composeTestRule.setContent {
      com.example.ui.theme.GVONEBrowserTheme {
        com.example.ui.screens.TerminalScreen(
          viewModel = vm,
          onClose = {}
        )
      }
    }
  }

  @Test
  fun `bot chat workspace tab opens and activates persona`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.BrowserViewModel(app)
    vm.openBotChat(com.example.ui.screens.ide.GrokBotPersona.WEB_SCRAPER)
    assertEquals(com.example.ui.screens.ide.XcodeWorkspaceMode.BOT_CHAT, vm.workspaceMode.value)
    assertEquals(com.example.ui.screens.ide.GrokBotPersona.WEB_SCRAPER, vm.activeBotPersona.value)
  }

  @Test
  fun `terminal target directory persists in viewmodel`() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val vm = com.example.ui.viewmodel.BrowserViewModel(app)
    vm.setTerminalTargetDirectory("Projects/KotlinCalculator")
    assertEquals("Projects/KotlinCalculator", vm.terminalTargetDirectory.value)
  }

  @Test
  fun `terminal target directory chip renders in SuggestionChipsBar`() {
    var selectedDir: String? = null
    composeTestRule.setContent {
      com.example.ui.theme.GVONEBrowserTheme {
        com.example.ui.components.suggestions.SuggestionChipsBar(
          targetDirectory = "Projects/KotlinCalculator",
          workspaceDirectories = listOf("Projects", "Projects/KotlinCalculator", "Documents"),
          onSelectTargetDirectory = { selectedDir = it },
          onSelectPrompt = {}
        )
      }
    }
    composeTestRule.onNode(androidx.compose.ui.test.hasTestTag("terminal_target_directory_chip")).assertExists()
  }
}
