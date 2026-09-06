package com.example

import com.example.ui.contextmenu.ContextMenuTargetType
import com.example.ui.contextmenu.LinkContextMenuData
import org.junit.Assert.*
import org.junit.Test

class LinkContextMenuUnitTest {

    @Test
    fun testLinkContextMenuData_displayProperties() {
        val linkData = LinkContextMenuData(
            url = "https://www.example.com/blog/article?ref=homepage",
            title = "Innovative Breakthroughs in Computing",
            targetType = ContextMenuTargetType.LINK
        )

        assertEquals("Innovative Breakthroughs in Computing", linkData.displayTitle)
        assertEquals("example.com", linkData.displaySubtitle)
        assertFalse(linkData.targetType.isMedia)
    }

    @Test
    fun testLinkContextMenuData_imageFallbackTitle() {
        val imgData = LinkContextMenuData(
            url = "https://cdn.example.com/images/wallpaper_4k.jpg",
            title = "",
            srcUrl = "https://cdn.example.com/images/wallpaper_4k.jpg",
            targetType = ContextMenuTargetType.IMAGE
        )

        assertEquals("wallpaper_4k.jpg", imgData.displayTitle)
        assertEquals("cdn.example.com", imgData.displaySubtitle)
        assertTrue(imgData.targetType.isMedia)
    }

    @Test
    fun testLinkContextMenuData_videoFallbackTitle() {
        val videoData = LinkContextMenuData(
            url = "https://videos.example.org/stream/presentation.mp4",
            title = "",
            srcUrl = "https://videos.example.org/stream/presentation.mp4",
            targetType = ContextMenuTargetType.VIDEO
        )

        assertEquals("presentation.mp4", videoData.displayTitle)
        assertEquals("videos.example.org", videoData.displaySubtitle)
        assertTrue(videoData.targetType.isMedia)
    }
}
