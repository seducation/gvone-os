package com.example

import com.example.data.model.QuickMenuProject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectScopingUnitTest {

    @Test
    fun `QuickMenuProject includes files, tabs, chats, extensions, and bots`() {
        val project = QuickMenuProject(
            id = "proj_test_1",
            name = "Test Web App",
            description = "A responsive web application",
            tabs = listOf("index.html", "https://example.com"),
            files = listOf("index.html", "styles.css", "main.js"),
            chats = listOf("AI scaffolding query"),
            extensions = listOf("prettier", "eslint", "live_server"),
            bots = listOf("Coding Agent", "Terminal Agent")
        )

        assertEquals("Test Web App", project.name)
        assertEquals(3, project.files.size)
        assertTrue(project.files.contains("index.html"))
        assertTrue(project.files.contains("styles.css"))
        assertTrue(project.files.contains("main.js"))
        assertFalse(project.files.contains("unrelated.txt"))

        assertEquals(2, project.tabs.size)
        assertEquals(1, project.chats.size)
        assertEquals(3, project.extensions.size)
        assertEquals(2, project.bots.size)
    }

    @Test
    fun `QuickMenuProject JSON serialization preserves extensions and bots`() {
        val original = QuickMenuProject(
            id = "proj_json_test",
            name = "Kotlin Calc",
            description = "Math evaluator",
            tabs = listOf("Main.kt"),
            files = listOf("Main.kt", "Calc.kt"),
            chats = listOf("Optimize parser"),
            extensions = listOf("gitlens", "prettier"),
            bots = listOf("Architect", "Coding Agent")
        )

        val json = original.toJsonObject()
        val restored = QuickMenuProject.fromJsonObject(json)

        assertEquals(original.id, restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.files, restored.files)
        assertEquals(original.tabs, restored.tabs)
        assertEquals(original.chats, restored.chats)
        assertEquals(original.extensions, restored.extensions)
        assertEquals(original.bots, restored.bots)
    }

    @Test
    fun `Target directory unselect cleanses back to root workspace`() {
        fun sanitizeTarget(dir: String): String {
            return if (dir.isBlank() || dir == "Root (~)" || dir == "~" || dir == "root" || dir.equals("unselect", ignoreCase = true)) {
                ""
            } else {
                dir.trim().removePrefix("/")
            }
        }

        assertEquals("", sanitizeTarget(""))
        assertEquals("", sanitizeTarget("   "))
        assertEquals("", sanitizeTarget("Root (~)"))
        assertEquals("", sanitizeTarget("~"))
        assertEquals("", sanitizeTarget("unselect"))
        assertEquals("Projects/MyProject", sanitizeTarget("/Projects/MyProject"))
        assertEquals("Documents", sanitizeTarget("Documents"))
    }
}
