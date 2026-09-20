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
        private const val KEY_SAVED_SESSIONS = "terminal_saved_sessions_v1"
        private const val MAX_HISTORY_ITEMS = 200
        private const val MAX_SAVED_LINES = 100
        private const val MAX_SAVED_SESSIONS = 50
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

    fun getSavedSessions(): List<TerminalSession> {
        val raw = prefs.getString(KEY_SAVED_SESSIONS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<TerminalSession>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val title = obj.optString("title", "Session ${i + 1}")
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val lastActiveAt = obj.optLong("lastActiveAt", System.currentTimeMillis())
                val linesArr = obj.optJSONArray("lines")
                val lines = mutableListOf<TerminalLine>()
                if (linesArr != null) {
                    for (j in 0 until linesArr.length()) {
                        val lObj = linesArr.getJSONObject(j)
                        val text = lObj.optString("text", "")
                        val typeStr = lObj.optString("type", TerminalLineType.OUTPUT.name)
                        val type = try {
                            TerminalLineType.valueOf(typeStr)
                        } catch (_: Exception) {
                            TerminalLineType.OUTPUT
                        }
                        val ts = lObj.optLong("ts", System.currentTimeMillis())
                        val taskId = if (lObj.has("taskId") && !lObj.isNull("taskId")) lObj.getString("taskId") else null
                        lines.add(TerminalLine(text = text, type = type, timestamp = ts, taskId = taskId))
                    }
                }
                list.add(
                    TerminalSession(
                        id = id,
                        title = title,
                        lines = lines,
                        createdAt = createdAt,
                        lastActiveAt = lastActiveAt
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSessions(sessions: List<TerminalSession>) {
        try {
            val arr = JSONArray()
            val trimmedSessions = sessions.takeLast(MAX_SAVED_SESSIONS)
            for (session in trimmedSessions) {
                val sObj = JSONObject()
                sObj.put("id", session.id)
                sObj.put("title", session.title)
                sObj.put("createdAt", session.createdAt)
                sObj.put("lastActiveAt", session.lastActiveAt)

                val linesArr = JSONArray()
                val linesToSave = session.lines.takeLast(MAX_SAVED_LINES)
                for (line in linesToSave) {
                    val lObj = JSONObject()
                    lObj.put("text", line.text)
                    lObj.put("type", line.type.name)
                    lObj.put("ts", line.timestamp)
                    if (line.taskId != null) {
                        lObj.put("taskId", line.taskId)
                    }
                    linesArr.put(lObj)
                }
                sObj.put("lines", linesArr)
                arr.put(sObj)
            }
            prefs.edit().putString(KEY_SAVED_SESSIONS, arr.toString()).apply()
        } catch (_: Exception) {
        }
    }

    fun deleteSession(sessionId: String) {
        val current = getSavedSessions().filterNot { it.id == sessionId }
        saveSessions(current)
    }

    fun clearAllSessions() {
        prefs.edit().remove(KEY_SAVED_SESSIONS).apply()
    }
}
