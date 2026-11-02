package com.example

import com.example.data.model.BrowserTab
import com.example.ui.components.DevBarTab
import com.example.ui.components.DevConsoleLog
import android.webkit.ConsoleMessage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevBarUnitTest {

    @Test
    fun testDeveloperBarTabsConfiguration() {
        val tabs = DevBarTab.values()
        assertEquals(5, tabs.size)
        assertEquals("Console", DevBarTab.CONSOLE.label)
        assertEquals("DOM", DevBarTab.DOM_INSPECT.label)
        assertEquals("Network", DevBarTab.NETWORK.label)
        assertEquals("Storage", DevBarTab.STORAGE.label)
        assertEquals("Device", DevBarTab.DEVICE.label)
    }

    @Test
    fun testDevConsoleLogCreation() {
        val log = DevConsoleLog(
            message = "Test Console Log",
            level = ConsoleMessage.MessageLevel.LOG,
            sourceId = "index.html",
            lineNumber = 42
        )
        assertEquals("Test Console Log", log.message)
        assertEquals(ConsoleMessage.MessageLevel.LOG, log.level)
        assertEquals("index.html", log.sourceId)
        assertEquals(42, log.lineNumber)
        assertTrue(log.timestamp > 0)
    }

    @Test
    fun testBrowserTabUrlHostParsing() {
        val tab = BrowserTab(
            id = "test-tab",
            title = "Example Website",
            url = "https://example.com/subpath"
        )
        val uri = java.net.URI(tab.url)
        assertEquals("example.com", uri.host)
        assertTrue(tab.url.startsWith("https://"))
    }

    @Test
    fun testTargetDropdownOtherWorkspaceFiles() {
        val markdownFile = com.example.ui.components.OtherWorkspaceFile(
            title = "architecture.md",
            path = "GVONE/architecture.md",
            mode = com.example.ui.screens.ide.XcodeWorkspaceMode.MARKDOWN,
            description = "Markdown Docs",
            icon = Icons.Rounded.Description,
            tintColor = androidx.compose.ui.graphics.Color(0xFF10B981)
        )
        assertEquals("architecture.md", markdownFile.title)
        assertEquals(com.example.ui.screens.ide.XcodeWorkspaceMode.MARKDOWN, markdownFile.mode)

        val pdfFile = com.example.ui.components.OtherWorkspaceFile(
            title = "document.pdf",
            path = "Downloads/document.pdf",
            mode = com.example.ui.screens.ide.XcodeWorkspaceMode.PDF_VIEWER,
            description = "PDF Multi-page Document",
            icon = Icons.Rounded.PictureAsPdf,
            tintColor = androidx.compose.ui.graphics.Color(0xFFEF4444)
        )
        assertEquals("document.pdf", pdfFile.title)
        assertEquals(com.example.ui.screens.ide.XcodeWorkspaceMode.PDF_VIEWER, pdfFile.mode)
    }

    @Test
    fun testGrokBotPersonasCountAndAttributes() {
        val personas = com.example.ui.screens.ide.GrokBotPersona.values()
        assertEquals(5, personas.size)
        assertTrue(personas.any { it == com.example.ui.screens.ide.GrokBotPersona.GROK_CODER })
        assertTrue(personas.any { it == com.example.ui.screens.ide.GrokBotPersona.CODE_ARCHITECT })
        assertTrue(personas.any { it == com.example.ui.screens.ide.GrokBotPersona.BUG_HUNTER })
        assertTrue(personas.any { it == com.example.ui.screens.ide.GrokBotPersona.WEB_SCRAPER })
        assertTrue(personas.any { it == com.example.ui.screens.ide.GrokBotPersona.TEST_SPECIALIST })
    }

    @Test
    fun testXcodeWorkspaceModeTargetValues() {
        val modes = com.example.ui.screens.ide.XcodeWorkspaceMode.values()
        assertEquals(8, modes.size)
        assertEquals("Website", com.example.ui.screens.ide.XcodeWorkspaceMode.BROWSER.tag)
        assertEquals("Xcode", com.example.ui.screens.ide.XcodeWorkspaceMode.CODE_EDITOR.tag)
        assertEquals("Copilot", com.example.ui.screens.ide.XcodeWorkspaceMode.BOT_CHAT.tag)
    }

    @Test
    fun testDevBarExtensionsAndMcpIntegration() {
        // Test that MCP server manager is reachable and initialized
        val servers = com.example.ui.screens.ide.McpServerManager.servers.value
        assertTrue("MCP servers should not be empty", servers.isNotEmpty())
        assertTrue("Filesystem server should be present", servers.any { it.id == "mcp_filesystem" })
        assertTrue("Git server should be present", servers.any { it.id == "mcp_git" })

        val fsServer = servers.first { it.id == "mcp_filesystem" }
        assertTrue("FS server should contain read_file tool", fsServer.tools.any { it.name == "read_file" })
        assertTrue("FS server should contain write_file tool", fsServer.tools.any { it.name == "write_file" })

        // Test IDE extension model
        val extension = com.example.ui.screens.ide.IdeExtension(
            id = "prettier",
            name = "Prettier Formatter",
            version = "v3.2.0",
            publisher = "Prettier",
            description = "Code formatter",
            icon = Icons.Rounded.FormatPaint,
            isEnabled = true,
            category = "Formatters"
        )
        assertEquals("prettier", extension.id)
        assertTrue(extension.isEnabled)
    }
}

