package com.example

import com.example.ui.components.getDefaultExtraShortcuts
import com.example.ui.components.getDefaultPrimaryShortcuts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutsUnitTest {

    @Test
    fun testPrimaryPinnedShortcuts() {
        val primary = getDefaultPrimaryShortcuts()
        assertEquals(4, primary.size)
        val titles = primary.map { it.title }
        assertTrue(titles.contains("Flipkart Lite"))
        assertTrue(titles.contains("Amazon India"))
        assertTrue(titles.contains("ESPNcricinfo"))
        assertTrue(titles.contains("The Financial..."))
    }

    @Test
    fun testExtraShortcuts() {
        val extra = getDefaultExtraShortcuts()
        assertTrue(extra.size >= 10)
        val titles = extra.map { it.title }
        assertTrue(titles.contains("Google"))
        assertTrue(titles.contains("YouTube"))
        assertTrue(titles.contains("DuckDuckGo"))
        assertTrue(titles.contains("Wikipedia"))
        assertTrue(titles.contains("GitHub"))
        assertTrue(titles.contains("Reddit"))
    }

    @Test
    fun testCombinedAllShortcutsUniqueUrls() {
        val primary = getDefaultPrimaryShortcuts()
        val extra = getDefaultExtraShortcuts()
        val combined = (primary + extra).distinctBy { it.url }
        assertEquals(primary.size + extra.size, combined.size)
    }

    @Test
    fun testShortcutCategories() {
        val extra = getDefaultExtraShortcuts()
        val devShortcuts = extra.filter { it.category == "Dev" }
        val socialShortcuts = extra.filter { it.category == "Social" }
        val mediaShortcuts = extra.filter { it.category == "Media" }
        val newsShortcuts = extra.filter { it.category == "News" }

        assertTrue(devShortcuts.any { it.title == "GitHub" })
        assertTrue(socialShortcuts.any { it.title == "Reddit" })
        assertTrue(mediaShortcuts.any { it.title == "YouTube" })
        assertTrue(newsShortcuts.any { it.title == "Wikipedia" })
    }
}
