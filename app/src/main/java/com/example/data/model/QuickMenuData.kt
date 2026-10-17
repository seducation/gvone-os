package com.example.data.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Project model stored in QuickMenu.
 * Each project holds its own tabs, files, and chats.
 */
data class QuickMenuProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val tabs: List<String> = emptyList(),
    val files: List<String> = emptyList(),
    val chats: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("description", description)
        json.put("createdAt", createdAt)

        val tabsArray = JSONArray()
        tabs.forEach { tabsArray.put(it) }
        json.put("tabs", tabsArray)

        val filesArray = JSONArray()
        files.forEach { filesArray.put(it) }
        json.put("files", filesArray)

        val chatsArray = JSONArray()
        chats.forEach { chatsArray.put(it) }
        json.put("chats", chatsArray)

        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): QuickMenuProject {
            val tabsList = mutableListOf<String>()
            val tabsArray = json.optJSONArray("tabs")
            if (tabsArray != null) {
                for (i in 0 until tabsArray.length()) {
                    tabsList.add(tabsArray.optString(i))
                }
            }

            val filesList = mutableListOf<String>()
            val filesArray = json.optJSONArray("files")
            if (filesArray != null) {
                for (i in 0 until filesArray.length()) {
                    filesList.add(filesArray.optString(i))
                }
            }

            val chatsList = mutableListOf<String>()
            val chatsArray = json.optJSONArray("chats")
            if (chatsArray != null) {
                for (i in 0 until chatsArray.length()) {
                    chatsList.add(chatsArray.optString(i))
                }
            }

            return QuickMenuProject(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name", "Untitled Project"),
                description = json.optString("description", ""),
                tabs = tabsList,
                files = filesList,
                chats = chatsList,
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Schedule item model stored in QuickMenu.
 * Contains task title, date, time, reminder setting, and completion state.
 */
data class QuickMenuScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val date: String, // e.g. "Today", "Tomorrow", "2026-10-18"
    val time: String, // e.g. "4:00 PM", "10:30 AM"
    val reminder: Boolean = true,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("date", date)
        json.put("time", time)
        json.put("reminder", reminder)
        json.put("isCompleted", isCompleted)
        json.put("createdAt", createdAt)
        return json
    }

    companion object {
        fun fromJsonObject(json: JSONObject): QuickMenuScheduleItem {
            return QuickMenuScheduleItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Untitled Task"),
                date = json.optString("date", "Today"),
                time = json.optString("time", "4:00 PM"),
                reminder = json.optBoolean("reminder", true),
                isCompleted = json.optBoolean("isCompleted", false),
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

/**
 * Persistent storage manager for Quick Menu using SharedPreferences (localStorage equivalent on Android).
 */
object QuickMenuStorage {
    private const val PREFS_NAME = "gvone_quick_menu_prefs"
    private const val KEY_PROJECTS = "quick_menu_projects_json"
    private const val KEY_SCHEDULE = "quick_menu_schedule_json"
    private const val KEY_MENU_COLLAPSED = "quick_menu_is_collapsed"

    private fun getDefaultProjects(): List<QuickMenuProject> = listOf(
        QuickMenuProject(
            id = "proj_gvone_core",
            name = "GVONE Browser Core",
            description = "High performance web engine with tabs and integrated terminal",
            tabs = listOf("Google Search", "GitHub Repositories", "Kotlin Documentation", "Android Developer Portal"),
            files = listOf("MainActivity.kt", "BrowserViewModel.kt", "QuickMenuCard.kt", "strings.xml", "Theme.kt"),
            chats = listOf("Grok Bot: Terminal script review", "Architecture & security audit", "Design token sync")
        ),
        QuickMenuProject(
            id = "proj_calculator",
            name = "Kotlin Calculator",
            description = "Lightweight math expression evaluator and unit converter",
            tabs = listOf("Math AST Parser", "Unit Test Dashboard"),
            files = listOf("Calculator.kt", "Parser.kt", "Main.kt", "CalculatorTest.kt"),
            chats = listOf("Algorithm optimization query")
        ),
        QuickMenuProject(
            id = "proj_react_mobile",
            name = "React Mobile UI",
            description = "Cross-platform touch components and interactive widgets",
            tabs = listOf("Figma Designs", "Vite Localhost", "NPM Registry"),
            files = listOf("App.tsx", "Navigation.tsx", "index.css", "package.json", "Button.tsx", "vite.config.ts"),
            chats = listOf("Styling & CSS Grid adjustments", "Component animation polish")
        )
    )

    private fun getDefaultSchedule(): List<QuickMenuScheduleItem> = listOf(
        QuickMenuScheduleItem(
            id = "task_sync_4pm",
            title = "Team Sync & Code Review",
            date = "Today",
            time = "4:00 PM",
            reminder = true,
            isCompleted = false
        ),
        QuickMenuScheduleItem(
            id = "task_planning_630pm",
            title = "Sprint Planning & Backlog",
            date = "Today",
            time = "6:30 PM",
            reminder = false,
            isCompleted = false
        ),
        QuickMenuScheduleItem(
            id = "task_arch_10am",
            title = "Architecture & Release Audit",
            date = "Tomorrow",
            time = "10:00 AM",
            reminder = true,
            isCompleted = false
        )
    )

    fun getProjects(context: Context): List<QuickMenuProject> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PROJECTS, null)
        if (jsonStr.isNullOrEmpty()) {
            val defaults = getDefaultProjects()
            saveProjects(context, defaults)
            return defaults
        }

        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<QuickMenuProject>()
            for (i in 0 until jsonArray.length()) {
                list.add(QuickMenuProject.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            if (list.isEmpty()) getDefaultProjects() else list
        } catch (e: Exception) {
            getDefaultProjects()
        }
    }

    fun saveProjects(context: Context, projects: List<QuickMenuProject>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        projects.forEach { jsonArray.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_PROJECTS, jsonArray.toString()).apply()
    }

    fun getScheduleItems(context: Context): List<QuickMenuScheduleItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_SCHEDULE, null)
        if (jsonStr.isNullOrEmpty()) {
            val defaults = getDefaultSchedule()
            saveScheduleItems(context, defaults)
            return defaults
        }

        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<QuickMenuScheduleItem>()
            for (i in 0 until jsonArray.length()) {
                list.add(QuickMenuScheduleItem.fromJsonObject(jsonArray.getJSONObject(i)))
            }
            if (list.isEmpty()) getDefaultSchedule() else list
        } catch (e: Exception) {
            getDefaultSchedule()
        }
    }

    fun saveScheduleItems(context: Context, items: List<QuickMenuScheduleItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        items.forEach { jsonArray.put(it.toJsonObject()) }
        prefs.edit().putString(KEY_SCHEDULE, jsonArray.toString()).apply()
    }

    fun isMenuCollapsed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MENU_COLLAPSED, false)
    }

    fun setMenuCollapsed(context: Context, collapsed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MENU_COLLAPSED, collapsed).apply()
    }

    /**
     * Compute next upcoming schedule label, e.g. "Next: 4:00 PM"
     */
    fun getNextUpcomingLabel(items: List<QuickMenuScheduleItem>): String {
        val upcoming = items.firstOrNull { !it.isCompleted } ?: items.firstOrNull()
        return if (upcoming != null) {
            "Next: ${upcoming.time}"
        } else {
            "No upcoming tasks"
        }
    }
}
