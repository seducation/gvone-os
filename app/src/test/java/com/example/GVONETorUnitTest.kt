package com.example

import com.example.data.model.BrowserSettings
import com.example.data.tor.TorConnectionState
import com.example.data.tor.TorManager
import com.example.data.tor.TorStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.Proxy

class GVONETorUnitTest {

    private lateinit var torManager: TorManager

    @Before
    fun setUp() {
        torManager = TorManager()
    }

    @Test
    fun testInitialTorStateIsDisconnected() {
        val status = torManager.torStatus.value
        assertEquals(TorConnectionState.DISCONNECTED, status.state)
        assertFalse(status.onionRoutingActive)
        assertFalse(status.webViewProxyApplied)
        assertNull(status.lastError)
    }

    @Test
    fun testOnionAddressDetection() {
        assertTrue(torManager.isOnionAddress("http://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion"))
        assertTrue(torManager.isOnionAddress("https://check.torproject.org.onion/"))
        assertTrue(torManager.isOnionAddress("http://3g2upl4pq6kufc4m.onion/path?arg=1"))
        assertFalse(torManager.isOnionAddress("https://www.google.com"))
        assertFalse(torManager.isOnionAddress("https://duckduckgo.com"))
        assertFalse(torManager.isOnionAddress("https://youtube.com"))
    }

    @Test
    fun testConnectWithoutDaemonSetsErrorStateGracefully() = runTest {
        // When no daemon is listening on local port, connect should fail-closed into ERROR state without crashing
        torManager.connect(host = "127.0.0.1", port = 59999)
        val status = torManager.torStatus.value
        assertEquals(TorConnectionState.ERROR, status.state)
        assertFalse(status.onionRoutingActive)
        assertNotNull(status.lastError)
        assertTrue(status.lastError!!.contains("No active Tor proxy found"))
    }

    @Test
    fun testDisconnectClearsState() = runTest {
        torManager.connect(host = "127.0.0.1", port = 59999)
        assertEquals(TorConnectionState.ERROR, torManager.torStatus.value.state)

        torManager.disconnect()
        val status = torManager.torStatus.value
        assertEquals(TorConnectionState.DISCONNECTED, status.state)
        assertFalse(status.onionRoutingActive)
        assertFalse(status.webViewProxyApplied)
        assertNull(status.lastError)
        assertNull(torManager.testResult.value)
    }

    @Test
    fun testRapidDisconnectCancelsPendingConnect() = runTest {
        // Calling connect followed immediately by disconnect should end in DISCONNECTED state
        torManager.connect(host = "127.0.0.1", port = 59998)
        torManager.disconnect()

        val status = torManager.torStatus.value
        assertEquals(TorConnectionState.DISCONNECTED, status.state)
        assertFalse(status.onionRoutingActive)
    }

    @Test
    fun testOkHttpClientUsesDirectWhenDisconnected() {
        val client = torManager.getOkHttpClient()
        assertNotNull(client)
        assertNull(client.proxy)
    }

    @Test
    fun testTestTorConnectionWhenDisconnectedReturnsNotice() = runTest {
        val result = torManager.testTorConnection()
        assertFalse(result.isSuccessful)
        assertTrue(result.message.contains("Tor is not connected"))
    }

    @Test
    fun testBrowserSettingsTorDefaults() {
        val defaultSettings = BrowserSettings()
        assertFalse(defaultSettings.torEnabled)
        assertEquals("127.0.0.1", defaultSettings.torProxyHost)
        assertEquals(9050, defaultSettings.torProxyPort)
    }
}
