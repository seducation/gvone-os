package com.example.data.terminal

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class TerminalRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gvone_terminal_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_COMMAND_HISTORY = "terminal_command_history"
        private const val KEY_SESSION_LINES = "terminal_session_lines"
        private const val MAX_HISTORY_ITEMS = 200
        private const val MAX_SAVED_LINES = 100
    }

    fun getCommandHistory(): List<String> {
        val raw = prefs.getString(KEY_COMMAND_HISTORY, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val cmd = jsonArray.optString(i)
                if (!cmd.isNullOrBlank()) {
                    list.add(cmd)
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addCommandToHistory(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return

        val current = getCommandHistory().toMutableList()
        // Remove if existing at the end to prevent immediate duplicate
        if (current.isNotEmpty() && current.last() == trimmed) {
            return
        }
        current.add(trimmed)
        if (current.size > MAX_HISTORY_ITEMS) {
            current.removeAt(0)
        }

        try {
            val arr = JSONArray()
            current.forEach { arr.put(it) }
            prefs.edit().putString(KEY_COMMAND_HISTORY, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun clearCommandHistory() {
        prefs.edit().remove(KEY_COMMAND_HISTORY).apply()
    }

    fun getSavedSessionLines(): List<TerminalLine> {
        val raw = prefs.getString(KEY_SESSION_LINES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<TerminalLine>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val text = obj.optString("text", "")
                val typeStr = obj.optString("type", TerminalLineType.OUTPUT.name)
                val type = try {
                    TerminalLineType.valueOf(typeStr)
                } catch (_: Exception) {
                    TerminalLineType.OUTPUT
                }
                val ts = obj.optLong("ts", System.currentTimeMillis())
                list.add(TerminalLine(text = text, type = type, timestamp = ts))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSessionLines(lines: List<TerminalLine>) {
        try {
            val trimmedLines = lines.takeLast(MAX_SAVED_LINES)
            val arr = JSONArray()
            for (line in trimmedLines) {
                val obj = JSONObject()
                obj.put("text", line.text)
                obj.put("type", line.type.name)
                obj.put("ts", line.timestamp)
                arr.put(obj)
            }
            prefs.edit().putString(KEY_SESSION_LINES, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun clearSavedSessionLines() {
        prefs.edit().remove(KEY_SESSION_LINES).apply()
    }
}
