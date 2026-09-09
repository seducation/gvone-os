package com.example

import com.example.data.connector.AccountDetectionStatus
import com.example.data.connector.WebsiteAccessConnectorService
import com.example.data.connector.WebsiteAccessContext
import com.example.data.model.SavedPasswordEntry
import com.example.ui.components.ControlActionRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteAccessConnectorUnitTest {

    @Test
    fun testControlActionRegistryOrderAndIds() {
        val actions = ControlActionRegistry.buildDefaultActions(
            onPhotos = {},
            onCamera = {},
            onAvatar = {},
            onConnector = {},
            onFiles = {},
            onTerminal = {},
            onBridge = {}
        )

        assertEquals(7, actions.size)
        assertEquals(ControlActionRegistry.ACTION_PHOTOS, actions[0].id)
        assertEquals("Photos", actions[0].title)
        assertEquals(ControlActionRegistry.ACTION_CAMERA, actions[1].id)
        assertEquals("Camera", actions[1].title)
        assertEquals(ControlActionRegistry.ACTION_AVATAR, actions[2].id)
        assertEquals("Avatar", actions[2].title)
        assertEquals(ControlActionRegistry.ACTION_CONNECTOR, actions[3].id)
        assertEquals("Connector", actions[3].title)
        assertEquals(ControlActionRegistry.ACTION_FILES, actions[4].id)
        assertEquals("Files", actions[4].title)
        assertEquals(ControlActionRegistry.ACTION_TERMINAL, actions[5].id)
        assertEquals("Terminal", actions[5].title)
        assertEquals(ControlActionRegistry.ACTION_BRIDGE, actions[6].id)
        assertEquals("Bridge", actions[6].title)
    }

    @Test
    fun testDomainExtraction() {
        assertEquals("github.com", WebsiteAccessConnectorService.extractDomain("https://github.com/torvalds/linux"))
        assertEquals("google.com", WebsiteAccessConnectorService.extractDomain("https://www.google.com/search?q=gvone"))
        assertEquals("Start Page", WebsiteAccessConnectorService.extractDomain("gvone://newtab"))
        assertEquals("Start Page", WebsiteAccessConnectorService.extractDomain(null))
    }

    @Test
    fun testLoginDetectionGitHubLoggedIn() {
        val service = WebsiteAccessConnectorService()
        val cookies = listOf(
            Pair("logged_in", "yes"),
            Pair("dotcom_user", "octocat"),
            Pair("user_session", "secret_session_token_123")
        )

        val status = service.detectLoginStatus("github.com", cookies, null)
        assertTrue(status is AccountDetectionStatus.LoggedIn)
        val loggedIn = status as AccountDetectionStatus.LoggedIn
        assertEquals("octocat", loggedIn.accountIdentifier)
    }

    @Test
    fun testLoginDetectionGitHubLoggedOut() {
        val service = WebsiteAccessConnectorService()
        val cookies = listOf(
            Pair("logged_in", "no")
        )

        val status = service.detectLoginStatus("github.com", cookies, null)
        assertTrue(status is AccountDetectionStatus.LoggedOut)
    }

    @Test
    fun testLoginDetectionUnknownNeverGuesses() {
        val service = WebsiteAccessConnectorService()
        val cookies = listOf(
            Pair("theme", "dark"),
            Pair("visited", "true")
        )

        val status = service.detectLoginStatus("random-blog.org", cookies, null)
        assertTrue(status is AccountDetectionStatus.Unknown)
    }

    @Test
    fun testInspectStartPageReturnsZeroMetrics() = runBlocking {
        val service = WebsiteAccessConnectorService()
        val context = WebsiteAccessContext(
            currentTabId = "tab_1",
            currentUrl = "gvone://newtab",
            currentDomain = "Start Page",
            currentEnvironmentId = "default",
            currentEnvironmentName = "Default"
        )

        val report = service.inspectWebsite(context)
        assertEquals("Start Page", report.context.currentDomain)
        assertEquals(0, report.cookiesCount)
        assertEquals(0, report.permissionsCount)
        assertEquals("0 KB", report.siteDataFormatted)
        assertTrue(report.accountStatus is AccountDetectionStatus.Unknown)
    }
}
