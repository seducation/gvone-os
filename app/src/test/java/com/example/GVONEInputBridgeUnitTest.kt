package com.example

import com.example.data.sync.PageContextDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GVONEInputBridgeUnitTest {

    @Test
    fun `normal websites are correctly identified as untrusted and not youtube`() {
        val normalWebsites = listOf(
            "https://www.google.com",
            "https://www.google.com/search?q=android",
            "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
            "https://www.reddit.com/r/androiddev",
            "https://github.com/login",
            "https://news.ycombinator.com",
            "https://amazon.com",
            "https://duckduckgo.com"
        )

        for (url in normalWebsites) {
            assertFalse(
                "Expected $url to NOT be a trusted GVONE origin",
                PageContextDetector.isTrustedGVONEOrigin(url)
            )
            assertFalse(
                "Expected $url to NOT be a YouTube origin",
                PageContextDetector.isYouTubeOrigin(url)
            )
        }
    }

    @Test
    fun `youtube urls are correctly identified for non-intrusive auto search`() {
        val youtubeUrls = listOf(
            "https://www.youtube.com",
            "https://www.youtube.com/",
            "https://m.youtube.com",
            "https://m.youtube.com/results?search_query=test",
            "https://youtu.be/dQw4w9WgXcQ"
        )

        for (url in youtubeUrls) {
            assertFalse(
                "Expected $url to NOT be a trusted GVONE origin",
                PageContextDetector.isTrustedGVONEOrigin(url)
            )
            assertTrue(
                "Expected $url to be a YouTube origin",
                PageContextDetector.isYouTubeOrigin(url)
            )
        }
    }

    @Test
    fun `trusted GVONE web apps are correctly identified`() {
        val trustedUrls = listOf(
            "https://gvone.app",
            "https://app.gvone.com",
            "https://charassist-c4uzg7hb.manus.space",
            "https://rssgroupfeed-jaelvwfd.manus.space",
            "gvone://newtab"
        )

        for (url in trustedUrls) {
            assertTrue(
                "Expected $url to be a trusted GVONE origin",
                PageContextDetector.isTrustedGVONEOrigin(url)
            )
        }
    }

    @Test
    fun `youtube homepage detection identifies homepage vs watch page`() {
        assertTrue(PageContextDetector.isYouTubeHomepage("https://www.youtube.com"))
        assertTrue(PageContextDetector.isYouTubeHomepage("https://www.youtube.com/"))
        assertTrue(PageContextDetector.isYouTubeHomepage("https://m.youtube.com/"))
        assertFalse(PageContextDetector.isYouTubeHomepage("https://www.youtube.com/watch?v=12345"))
        assertFalse(PageContextDetector.isYouTubeHomepage("https://m.youtube.com/shorts/12345"))
    }
}
