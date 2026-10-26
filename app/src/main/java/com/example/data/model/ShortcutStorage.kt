package com.example.data.model

import android.content.Context
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.screens.MenuShortcut
import org.json.JSONArray
import org.json.JSONObject

object ShortcutStorage {
    private const val PREFS_NAME = "shortcut_storage_prefs"
    private const val KEY_CUSTOM_SHORTCUTS = "custom_shortcuts_json"
    private const val KEY_PINNED_URLS = "pinned_urls_set"

    fun getCustomShortcuts(context: Context): List<MenuShortcut> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_SHORTCUTS, null) ?: return emptyList()
        val list = mutableListOf<MenuShortcut>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val title = obj.optString("title", "Shortcut")
                val url = obj.optString("url", "")
                val initials = obj.optString("initialLetters", if (title.isNotEmpty()) title.take(2).uppercase() else "SC")
                val category = obj.optString("category", "General")
                list.add(
                    MenuShortcut(
                        title = title,
                        url = url,
                        initialLetters = initials,
                        badgeBg = Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1))),
                        textColor = Color.White,
                        category = category
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveCustomShortcuts(context: Context, shortcuts: List<MenuShortcut>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        shortcuts.forEach {
            val obj = JSONObject()
            obj.put("title", it.title)
            obj.put("url", it.url)
            obj.put("initialLetters", it.initialLetters)
            obj.put("category", it.category)
            array.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_SHORTCUTS, array.toString()).apply()
    }

    fun getPinnedUrls(context: Context, defaultUrls: Set<String>): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PINNED_URLS, defaultUrls)?.toSet() ?: defaultUrls
    }

    fun savePinnedUrls(context: Context, urls: Set<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_PINNED_URLS, urls).apply()
    }
}
