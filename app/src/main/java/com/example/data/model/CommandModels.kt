package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

enum class CommandType(val displayName: String, val description: String) {
    SEARCH("Search Command", "Searches with a specific provider"),
    URL("URL Command", "Opens a templated website link"),
    AI("GVONE AI Command", "Sends a prompt to GVONE AI"),
    BROWSER_ACTION("Browser Action", "Triggers browser navigation or settings"),
    PAGE_ACTION("Page Action", "Interacts with or extracts from the current page"),
    AUTOMATION("JavaScript Automation", "Runs safe page scripts or browser automation")
}

enum class CommandCategory(val displayName: String) {
    SEARCH("Search Commands"),
    GVONE("GVONE AI Commands"),
    BROWSER("Built-in Browser Commands"),
    PAGE("Page Commands"),
    CUSTOM("Custom Commands"),
    AUTOMATION("Automation Commands")
}

enum class BrowserActionType(val actionId: String, val label: String) {
    NEW_TAB("new_tab", "Open New Tab"),
    NEW_PRIVATE_TAB("new_private_tab", "Open New Private Tab"),
    RELOAD("reload", "Reload Current Page"),
    BACK("back", "Go Back"),
    FORWARD("forward", "Go Forward"),
    HISTORY("history", "Open History"),
    BOOKMARKS("bookmarks", "Open Bookmarks"),
    DOWNLOADS("downloads", "Open Downloads"),
    CLOSE_TAB("close_tab", "Close Current Tab"),
    TAB_OVERVIEW("tab_overview", "Open Tab Overview"),
    SETTINGS("settings", "Open Settings"),
    COMMAND_MANAGER("commands", "Open Command Manager"),
    DESKTOP_MODE("desktop_mode", "Toggle Desktop Site"),
    TOR_DIAGNOSTICS("tor_diagnostics", "Open Tor Diagnostics"),
    FIND_IN_PAGE("find_in_page", "Find in Page"),
    READER_MODE("reader_mode", "Toggle Reader Mode"),
    CLEAR_DATA("clear_data", "Clear Browsing Data");

    companion object {
        fun fromActionId(id: String): BrowserActionType? {
            return entries.find { it.actionId.equals(id, ignoreCase = true) }
        }
    }
}

@Entity(tableName = "custom_commands")
data class CustomCommandEntity(
    @PrimaryKey val id: String, // e.g. "/yt" or unique id
    val command: String, // Primary trigger, e.g. "/yt"
    val name: String,
    val description: String,
    val type: CommandType,
    val template: String, // URL template, search query template, AI prompt, browser action id, or safe JS
    val aliasesRaw: String = "", // Comma-separated aliases, e.g. "/youtube,/you"
    val category: CommandCategory = CommandCategory.CUSTOM,
    val targetProvider: String? = null,
    val isEnabled: Boolean = true,
    val isPinned: Boolean = false,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getAliasesList(): List<String> {
        if (aliasesRaw.isBlank()) return emptyList()
        return aliasesRaw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { if (it.startsWith("/")) it else "/$it" }
    }

    fun getAllTriggers(): List<String> {
        val primary = if (command.startsWith("/")) command.lowercase() else "/${command.lowercase()}"
        return listOf(primary) + getAliasesList().map { it.lowercase() }
    }

    fun matchesTrigger(trigger: String): Boolean {
        val normalized = if (trigger.startsWith("/")) trigger.lowercase() else "/${trigger.lowercase()}"
        return getAllTriggers().any { it.equals(normalized, ignoreCase = true) }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("command", command)
        obj.put("name", name)
        obj.put("description", description)
        obj.put("type", type.name)
        obj.put("template", template)
        val arr = JSONArray()
        getAliasesList().forEach { arr.put(it) }
        obj.put("aliases", arr)
        obj.put("category", category.name)
        obj.put("targetProvider", targetProvider ?: "")
        obj.put("isEnabled", isEnabled)
        obj.put("isPinned", isPinned)
        obj.put("isBuiltIn", isBuiltIn)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): CustomCommandEntity {
            val command = obj.optString("command", "/cmd")
            val aliasesArr = obj.optJSONArray("aliases")
            val aliasesList = mutableListOf<String>()
            if (aliasesArr != null) {
                for (i in 0 until aliasesArr.length()) {
                    aliasesList.add(aliasesArr.getString(i))
                }
            }

            val typeStr = obj.optString("type", CommandType.SEARCH.name)
            val type = try {
                CommandType.valueOf(typeStr)
            } catch (_: Exception) {
                CommandType.SEARCH
            }

            val categoryStr = obj.optString("category", CommandCategory.CUSTOM.name)
            val category = try {
                CommandCategory.valueOf(categoryStr)
            } catch (_: Exception) {
                CommandCategory.CUSTOM
            }

            return CustomCommandEntity(
                id = obj.optString("id", command),
                command = command,
                name = obj.optString("name", command),
                description = obj.optString("description", ""),
                type = type,
                template = obj.optString("template", ""),
                aliasesRaw = aliasesList.joinToString(","),
                category = category,
                targetProvider = obj.optString("targetProvider").takeIf { it.isNotBlank() },
                isEnabled = obj.optBoolean("isEnabled", true),
                isPinned = obj.optBoolean("isPinned", false),
                isBuiltIn = obj.optBoolean("isBuiltIn", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            )
        }
    }
}

data class CommandExecutionContext(
    val query: String = "",
    val currentUrl: String = "",
    val currentTitle: String = "",
    val currentDomain: String = "",
    val selectionText: String = "",
    val clipboardText: String = "",
    val extraArgs: Map<String, String> = emptyMap(),
    val url: String = currentUrl,
    val title: String = currentTitle,
    val domain: String = currentDomain,
    val clipboard: String = clipboardText
)

sealed class CommandExecutionResult {
    data class OpenUrl(val url: String, val title: String? = null) : CommandExecutionResult()
    data class ExecuteSearch(val provider: String?, val query: String, val url: String) : CommandExecutionResult() {
        val searchUrl: String get() = url
    }
    data class SendAIPrompt(val prompt: String, val title: String = "GVONE AI Synthesis") : CommandExecutionResult()
    data class TriggerBrowserAction(val action: BrowserActionType) : CommandExecutionResult()
    data class TriggerPageAction(val action: String, val argument: String? = null) : CommandExecutionResult()
    data class RunSafeJavaScript(val jsCode: String, val description: String) : CommandExecutionResult() {
        val javascriptCode: String get() = jsCode
    }
    data class ShowMessage(val message: String, val isError: Boolean = false) : CommandExecutionResult()
}

data class CommandSuggestion(
    val command: CustomCommandEntity,
    val matchedTrigger: String,
    val userQuery: String = "",
    val previewText: String = "",
    val isExactMatch: Boolean = false
) {
    val queryArgument: String get() = userQuery
    val displayPreview: String get() = previewText
}
