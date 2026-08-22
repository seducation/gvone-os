package com.example

import com.example.data.tor.TorConnectionState
import com.example.data.tor.TorManager
import com.example.data.tor.TorStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TorIntegrationUnitTest {

    @Test
    fun `onion address detection correctly identifies dot onion hostnames`() {
        val torManager = TorManager()
        assertTrue(torManager.isOnionAddress("http://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion"))
        assertTrue(torManager.isOnionAddress("https://duckduckgogg42xjoc72x3sjasowoarfbgcmvfimaftt6twagswzczad.onion/search?q=tor"))
        assertTrue(torManager.isOnionAddress("http://expyuz5wqqfdgah56trnjvdgah2sr2m6i45e.onion"))
        assertFalse(torManager.isOnionAddress("https://www.google.com"))
        assertFalse(torManager.isOnionAddress("https://duckduckgo.com"))
        assertFalse(torManager.isOnionAddress("https://onion.example.com"))
    }

    @Test
    fun `tor manager initial state is disconnected`() {
        val torManager = TorManager()
        assertEquals(TorConnectionState.DISCONNECTED, torManager.torStatus.value.state)
        assertFalse(torManager.torStatus.value.onionRoutingActive)
        assertEquals("127.0.0.1", torManager.torStatus.value.socksProxyHost)
        assertEquals(9050, torManager.torStatus.value.socksProxyPort)
    }

    @Test
    fun `disconnect clears status and restores disconnected state`() {
        val torManager = TorManager()
        torManager.disconnect()
        assertEquals(TorConnectionState.DISCONNECTED, torManager.torStatus.value.state)
        assertFalse(torManager.torStatus.value.onionRoutingActive)
    }
}
