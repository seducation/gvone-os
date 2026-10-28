package com.example.data.model

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.screens.MenuShortcut
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage manager for Safari Shortcuts panel (pinned URLs and custom shortcuts).
 */
object ShortcutStorage {
    private const val PREFS_NAME = "gvone_shortcuts_prefs"
    private const val KEY_PINNED_URLS = "pinned_shortcuts_urls"
    private const val KEY_CUSTOM_SHORTCUTS = "custom_shortcuts_json"

    fun getPinnedUrls(context: Context, defaultPinned: Set<String>): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_PINNED_URLS, null)
        return stored ?: defaultPinned
    }

    fun savePinnedUrls(context: Context, pinnedUrls: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_PINNED_URLS, pinnedUrls).apply()
    }

    fun getCustomShortcuts(context: Context): List<MenuShortcut> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_SHORTCUTS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<MenuShortcut>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val title = obj.optString("title", "")
                val url = obj.optString("url", "")
                val initialLetters = obj.optString("initialLetters", if (title.isNotEmpty()) title.take(2).uppercase() else "S")
                val category = obj.optString("category", "General")

                val colors = when (category) {
                    "Dev & AI" -> listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                    "Social" -> listOf(Color(0xFF0EA5E9), Color(0xFF3B82F6))
                    "Media" -> listOf(Color(0xFFEC4899), Color(0xFFF43F5E))
                    "News" -> listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                    "Shopping" -> listOf(Color(0xFF10B981), Color(0xFF059669))
                    else -> listOf(Color(0xFF64748B), Color(0xFF475569))
                }

                list.add(
                    MenuShortcut(
                        title = title,
                        url = url,
                        initialLetters = initialLetters,
                        badgeBg = Brush.linearGradient(colors),
                        textColor = Color.White,
                        category = category
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveCustomShortcuts(context: Context, shortcuts: List<MenuShortcut>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        shortcuts.forEach { shortcut ->
            val obj = JSONObject()
            obj.put("title", shortcut.title)
            obj.put("url", shortcut.url)
            obj.put("initialLetters", shortcut.initialLetters)
            obj.put("category", shortcut.category)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_SHORTCUTS, jsonArray.toString()).apply()
    }
}
