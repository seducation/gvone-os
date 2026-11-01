package com.example

import com.example.data.model.BrowserTab
import com.example.ui.components.DevBarTab
import com.example.ui.components.DevConsoleLog
import android.webkit.ConsoleMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteDeveloperBarUnitTest {

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
}
