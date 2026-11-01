package com.example

import androidx.compose.material3.darkColorScheme
import com.example.data.files.FileType
import com.example.data.files.GVONEFileItem
import com.example.ui.screens.ide.*
import com.example.ui.viewmodel.ActiveSheet
import org.junit.Assert.*
import org.junit.Test

class GVONEIdeUnitTest {

    @Test
    fun testIdeTabDirtyState() {
        val fileItem = GVONEFileItem(
            id = "Projects/Main.kt",
            name = "Main.kt",
            path = "Projects/Main.kt",
            absolutePath = "/data/files/Projects/Main.kt",
            parentPath = "Projects",
            isDirectory = false,
            size = 100,
            formattedSize = "100 B",
            lastModified = System.currentTimeMillis(),
            formattedDate = "Today",
            extension = "kt",
            fileType = FileType.CODE
        )

        val cleanTab = IdeTab(
            id = fileItem.path,
            fileItem = fileItem,
            content = "fun main() {}",
            originalContent = "fun main() {}"
        )
        assertFalse("Clean tab should not be dirty", cleanTab.isDirty)

        val dirtyTab = cleanTab.copy(content = "fun main() { println(\"Hello\") }")
        assertTrue("Tab with modified content must be dirty", dirtyTab.isDirty)
    }

    @Test
    fun testIdeSyntaxHighlighter() {
        val colors = darkColorScheme()
        val kotlinCode = """
            package com.example
            import java.util.*
            
            // This is a comment
            fun main() {
                val greeting: String = "Hello GVONE"
                val count = 42
                println(greeting)
            }
        """.trimIndent()

        val highlighted = IdeSyntaxHighlighter.highlight(
            code = kotlinCode,
            extension = "kt",
            colorScheme = colors
        )

        assertEquals("Text length should match original code", kotlinCode.length, highlighted.text.length)
        assertTrue("AnnotatedString must have styling spans applied", highlighted.spanStyles.isNotEmpty())
    }

    @Test
    fun testIdeEnumsAndCommands() {
        // Activity tabs
        assertEquals(5, IdeActivityTab.values().size)
        assertTrue(IdeActivityTab.values().any { it == IdeActivityTab.EXPLORER })
        assertTrue(IdeActivityTab.values().any { it == IdeActivityTab.SEARCH })
        assertTrue(IdeActivityTab.values().any { it == IdeActivityTab.SOURCE_CONTROL })
        assertTrue(IdeActivityTab.values().any { it == IdeActivityTab.EXTENSIONS })
        assertTrue(IdeActivityTab.values().any { it == IdeActivityTab.AI_ASSISTANT })

        // Bottom tabs
        assertEquals(4, IdeBottomTab.values().size)
        assertTrue(IdeBottomTab.values().any { it == IdeBottomTab.TERMINAL })
        assertTrue(IdeBottomTab.values().any { it == IdeBottomTab.OUTPUT })
        assertTrue(IdeBottomTab.values().any { it == IdeBottomTab.PROBLEMS })
        assertTrue(IdeBottomTab.values().any { it == IdeBottomTab.AI_REVIEW })

        // Command execution
        var executed = false
        val command = IdeCommand(
            id = "test_cmd",
            title = "Test Command",
            category = "Testing",
            shortcut = "Ctrl+T"
        ) {
            executed = true
        }

        assertEquals("Test Command", command.title)
        command.action()
        assertTrue("Command action must execute", executed)
    }

    @Test
    fun testActiveSheetIdePresence() {
        val sheet: ActiveSheet = ActiveSheet.Ide
        assertNotNull(sheet)
        assertEquals(ActiveSheet.Ide, sheet)
    }

    @Test
    fun testXcodeNavigatorCategoriesIncludeMcpAndSettings() {
        val categories = XcodeNavigatorCategory.values()
        assertEquals("Navigator must have 7 categories: Tabs, Chats, Files, Bots, Extensions, MCP, Settings", 7, categories.size)
        assertEquals(XcodeNavigatorCategory.TABS, categories[0])
        assertEquals(XcodeNavigatorCategory.CHATS, categories[1])
        assertEquals(XcodeNavigatorCategory.FILES, categories[2])
        assertEquals(XcodeNavigatorCategory.BOTS, categories[3])
        assertEquals(XcodeNavigatorCategory.EXTENSIONS, categories[4])
        assertEquals(XcodeNavigatorCategory.MCP, categories[5])
        assertEquals(XcodeNavigatorCategory.SETTINGS, categories[6])

        assertEquals(5, XcodeNavigatorCategory.MCP.id)
        assertEquals("MCP", XcodeNavigatorCategory.MCP.title)
        assertEquals(6, XcodeNavigatorCategory.SETTINGS.id)
        assertEquals("Settings", XcodeNavigatorCategory.SETTINGS.title)
    }

    @Test
    fun testMcpServerManagerOperations() {
        val servers = McpServerManager.servers.value
        assertTrue("MCP server manager should contain default servers", servers.isNotEmpty())
        assertTrue("Should contain filesystem server", servers.any { it.id == "mcp_filesystem" })
        assertTrue("Should contain git server", servers.any { it.id == "mcp_git" })
        assertTrue("Should contain fetch server", servers.any { it.id == "mcp_fetch" })

        val initialStatus = servers.first { it.id == "mcp_filesystem" }.isConnected
        McpServerManager.toggleServer("mcp_filesystem")
        assertEquals(!initialStatus, McpServerManager.servers.value.first { it.id == "mcp_filesystem" }.isConnected)
        McpServerManager.toggleServer("mcp_filesystem") // restore

        val latency = McpServerManager.pingServer("mcp_filesystem")
        assertTrue("Latency should be positive", latency > 0)
    }
}
