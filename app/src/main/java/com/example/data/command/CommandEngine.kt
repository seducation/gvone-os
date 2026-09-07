package com.example.data.command

import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

object CommandEngine {

    val BUILT_IN_COMMANDS: List<CustomCommandEntity> = listOf(
        // Search Commands
        CustomCommandEntity(
            id = "cmd_yt",
            command = "/yt",
            name = "YouTube Search",
            description = "Search YouTube for videos and playlists",
            type = CommandType.SEARCH,
            template = "https://www.youtube.com/results?search_query={query}",
            aliasesRaw = "/youtube,/you",
            category = CommandCategory.SEARCH,
            targetProvider = "YouTube",
            isEnabled = true,
            isPinned = true,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_g",
            command = "/g",
            name = "GVONE / Google Search",
            description = "Search Google with GVONE Universal Engine",
            type = CommandType.SEARCH,
            template = "https://www.google.com/search?q={query}",
            aliasesRaw = "/google,/search",
            category = CommandCategory.SEARCH,
            targetProvider = "Google",
            isEnabled = true,
            isPinned = true,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_wiki",
            command = "/wiki",
            name = "Wikipedia Search",
            description = "Search Wikipedia encyclopedia directly",
            type = CommandType.SEARCH,
            template = "https://en.wikipedia.org/wiki/Special:Search?search={query}",
            aliasesRaw = "/wikipedia,/w",
            category = CommandCategory.SEARCH,
            targetProvider = "Wikipedia",
            isEnabled = true,
            isPinned = true,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_reddit",
            command = "/reddit",
            name = "Reddit Search",
            description = "Search discussions, subreddits, and posts on Reddit",
            type = CommandType.URL,
            template = "https://www.reddit.com/search/?q={query}",
            aliasesRaw = "/r,/red",
            category = CommandCategory.SEARCH,
            targetProvider = "Reddit",
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_github",
            command = "/gh",
            name = "GitHub Search",
            description = "Search code, repositories, and developers on GitHub",
            type = CommandType.SEARCH,
            template = "https://github.com/search?q={query}",
            aliasesRaw = "/github,/git",
            category = CommandCategory.SEARCH,
            targetProvider = "GitHub",
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_ddg",
            command = "/ddg",
            name = "DuckDuckGo",
            description = "Private search without tracking via DuckDuckGo",
            type = CommandType.SEARCH,
            template = "https://duckduckgo.com/?q={query}",
            aliasesRaw = "/duck,/privacy",
            category = CommandCategory.SEARCH,
            targetProvider = "DuckDuckGo",
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_scholar",
            command = "/scholar",
            name = "Google Scholar",
            description = "Search peer-reviewed papers, theses, and journals",
            type = CommandType.SEARCH,
            template = "https://scholar.google.com/scholar?q={query}",
            aliasesRaw = "/academic,/paper",
            category = CommandCategory.SEARCH,
            targetProvider = "Google Scholar",
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),

        // GVONE AI Commands
        CustomCommandEntity(
            id = "cmd_explain",
            command = "/explain",
            name = "GVONE AI Medical & Technical Explanation",
            description = "Explain complex topics in structured MBBS-level clarity",
            type = CommandType.AI,
            template = "Explain this in simple MBBS-level terminology: {query}",
            aliasesRaw = "/exp,/clarify",
            category = CommandCategory.GVONE,
            isEnabled = true,
            isPinned = true,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_ask",
            command = "/ask",
            name = "GVONE AI Q&A",
            description = "Ask GVONE AI any question for a synthesized answer",
            type = CommandType.AI,
            template = "Provide a comprehensive, factual answer to: {query}",
            aliasesRaw = "/ai,/prompt",
            category = CommandCategory.GVONE,
            isEnabled = true,
            isPinned = true,
            isBuiltIn = true
        ),

        // Page Action Commands
        CustomCommandEntity(
            id = "cmd_summarize",
            command = "/summarize",
            name = "Summarize Page",
            description = "Synthesize key takeaways and executive summary of the current page",
            type = CommandType.PAGE_ACTION,
            template = "summarize_page",
            aliasesRaw = "/sum,/tl;dr,/brief",
            category = CommandCategory.PAGE,
            isEnabled = true,
            isPinned = true,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_translate",
            command = "/translate",
            name = "Translate Page / Query",
            description = "Translate current page or query into English",
            type = CommandType.PAGE_ACTION,
            template = "translate_page",
            aliasesRaw = "/tr,/trans",
            category = CommandCategory.PAGE,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_extract",
            command = "/extract",
            name = "Extract Page Data",
            description = "Extract key data points, contacts, statistics, and references",
            type = CommandType.PAGE_ACTION,
            template = "extract_info",
            aliasesRaw = "/ext,/data",
            category = CommandCategory.PAGE,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_reader",
            command = "/reader",
            name = "Reader Mode",
            description = "Open distraction-free article reader mode",
            type = CommandType.PAGE_ACTION,
            template = "reader_mode",
            aliasesRaw = "/read,/article",
            category = CommandCategory.PAGE,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),

        // Browser Actions
        CustomCommandEntity(
            id = "cmd_new",
            command = "/new",
            name = "New Tab",
            description = "Open a clean new browser tab",
            type = CommandType.BROWSER_ACTION,
            template = "new_tab",
            aliasesRaw = "/tab,/nt",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_reload",
            command = "/reload",
            name = "Reload Page",
            description = "Refresh the current webpage",
            type = CommandType.BROWSER_ACTION,
            template = "reload",
            aliasesRaw = "/refresh,/rld",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_back",
            command = "/back",
            name = "Go Back",
            description = "Navigate backward in page history",
            type = CommandType.BROWSER_ACTION,
            template = "back",
            aliasesRaw = "/prev",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_forward",
            command = "/forward",
            name = "Go Forward",
            description = "Navigate forward in page history",
            type = CommandType.BROWSER_ACTION,
            template = "forward",
            aliasesRaw = "/next",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_history",
            command = "/history",
            name = "Browsing History",
            description = "Open your recent browsing history",
            type = CommandType.BROWSER_ACTION,
            template = "history",
            aliasesRaw = "/hist",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_bookmarks",
            command = "/bookmarks",
            name = "Bookmarks",
            description = "Open saved bookmarks and reading list",
            type = CommandType.BROWSER_ACTION,
            template = "bookmarks",
            aliasesRaw = "/favs,/bmarks",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_downloads",
            command = "/downloads",
            name = "Downloads",
            description = "View downloaded files and active tasks",
            type = CommandType.BROWSER_ACTION,
            template = "downloads",
            aliasesRaw = "/dl",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_close",
            command = "/close",
            name = "Close Tab",
            description = "Close the current active tab",
            type = CommandType.BROWSER_ACTION,
            template = "close_tab",
            aliasesRaw = "/closew,/w",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_tabs",
            command = "/tabs",
            name = "Tab Overview",
            description = "Open 3D tab overview & environments",
            type = CommandType.BROWSER_ACTION,
            template = "tab_overview",
            aliasesRaw = "/switcher,/windows",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_commands",
            command = "/commands",
            name = "Command Manager",
            description = "Manage, create, and customize browser commands",
            type = CommandType.BROWSER_ACTION,
            template = "commands",
            aliasesRaw = "/cmd,/manager,/customcommands",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = true,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_settings",
            command = "/settings",
            name = "Settings",
            description = "Open GVONE browser settings & privacy controls",
            type = CommandType.BROWSER_ACTION,
            template = "settings",
            aliasesRaw = "/config,/pref",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_private",
            command = "/privatetab",
            name = "New Private Tab",
            description = "Launch an incognito private browsing tab",
            type = CommandType.BROWSER_ACTION,
            template = "new_private_tab",
            aliasesRaw = "/incognito,/private,/secret",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_find",
            command = "/find",
            name = "Find in Page",
            description = "Search text on current webpage",
            type = CommandType.BROWSER_ACTION,
            template = "find_in_page",
            aliasesRaw = "/findpage,/f",
            category = CommandCategory.BROWSER,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),

        // JavaScript / Page Automation Commands
        CustomCommandEntity(
            id = "cmd_scrollbottom",
            command = "/scrollbottom",
            name = "Scroll to Bottom",
            description = "Smoothly scroll the webpage to the bottom",
            type = CommandType.AUTOMATION,
            template = "window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'});",
            aliasesRaw = "/bottom,/sb",
            category = CommandCategory.AUTOMATION,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_scrolltop",
            command = "/scrolltop",
            name = "Scroll to Top",
            description = "Smoothly scroll the webpage to the top",
            type = CommandType.AUTOMATION,
            template = "window.scrollTo({top: 0, behavior: 'smooth'});",
            aliasesRaw = "/top,/st",
            category = CommandCategory.AUTOMATION,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_print",
            command = "/print",
            name = "Print Page",
            description = "Open the browser print dialog for current page",
            type = CommandType.AUTOMATION,
            template = "window.print();",
            aliasesRaw = "/printpage,/pdf",
            category = CommandCategory.AUTOMATION,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        ),
        CustomCommandEntity(
            id = "cmd_contrast",
            command = "/darkmode",
            name = "Invert Dark Mode Filter",
            description = "Apply night-contrast filter to web content",
            type = CommandType.AUTOMATION,
            template = "(function(){var id='__gvone_inv__';var el=document.getElementById(id);if(el){el.remove();}else{var s=document.createElement('style');s.id=id;s.innerHTML='html{filter:invert(90%) hue-rotate(180deg) !important; background:#121212 !important;} img, video, canvas {filter:invert(100%) hue-rotate(180deg) !important;}';document.head.appendChild(s);}})();",
            aliasesRaw = "/invert,/contrast",
            category = CommandCategory.AUTOMATION,
            isEnabled = true,
            isPinned = false,
            isBuiltIn = true
        )
    )

    /**
     * Parse user address bar input against all registered commands.
     * Handles:
     * - "/yt cats" -> matches "/yt", query is "cats"
     * - "/youtube music" -> matches alias "/youtube", query is "music"
     * - "/explain nephrotic syndrome" -> matches "/explain", query is "nephrotic syndrome"
     * - "/back" -> matches "/back", no query
     */
    fun parse(
        input: String,
        registeredCommands: List<CustomCommandEntity>,
        context: CommandExecutionContext
    ): Pair<CustomCommandEntity, CommandExecutionResult>? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val allCommands = mergeWithBuiltIns(registeredCommands)

        // Split first token (command) from the rest (argument/query)
        val spaceIndex = trimmed.indexOf(' ')
        val token = if (spaceIndex != -1) trimmed.substring(0, spaceIndex).trim() else trimmed
        val queryArg = if (spaceIndex != -1) trimmed.substring(spaceIndex + 1).trim() else ""

        // Check if token matches any registered command (prioritizing enabled and built-in on conflict)
        val matchedCommand = findMatchingCommand(token, allCommands) ?: return null

        val result = executeMatchedCommand(matchedCommand, queryArg, context)
        return Pair(matchedCommand, result)
    }

    fun findMatchingCommand(token: String, commands: List<CustomCommandEntity>): CustomCommandEntity? {
        val normalizedToken = if (token.startsWith("/")) token.lowercase() else "/${token.lowercase()}"

        // Filter all enabled matching commands
        val matches = commands.filter { it.isEnabled && it.matchesTrigger(normalizedToken) }
        if (matches.isEmpty()) return null

        // 1. Built-in commands get priority by default when there is a conflict
        return matches.find { it.isBuiltIn } ?: matches.first()
    }

    fun executeMatchedCommand(
        command: CustomCommandEntity,
        queryArg: String,
        context: CommandExecutionContext
    ): CommandExecutionResult {
        val finalContext = context.copy(query = queryArg)

        return when (command.type) {
            CommandType.URL, CommandType.SEARCH -> {
                val resolvedUrl = substituteVariables(
                    template = command.template,
                    context = finalContext,
                    urlEncodeQuery = true
                )
                if (command.type == CommandType.SEARCH) {
                    CommandExecutionResult.ExecuteSearch(
                        provider = command.targetProvider ?: command.name,
                        query = queryArg,
                        url = resolvedUrl
                    )
                } else {
                    CommandExecutionResult.OpenUrl(resolvedUrl, title = "${command.name}: $queryArg")
                }
            }

            CommandType.AI -> {
                val prompt = substituteVariables(
                    template = command.template,
                    context = finalContext,
                    urlEncodeQuery = false
                )
                val finalPrompt = if (prompt.contains(queryArg) || queryArg.isBlank()) prompt else "$prompt: $queryArg"
                CommandExecutionResult.SendAIPrompt(finalPrompt, title = command.name)
            }

            CommandType.BROWSER_ACTION -> {
                val actionType = BrowserActionType.fromActionId(command.template)
                    ?: BrowserActionType.fromActionId(command.command.removePrefix("/"))
                    ?: BrowserActionType.NEW_TAB
                CommandExecutionResult.TriggerBrowserAction(actionType)
            }

            CommandType.PAGE_ACTION -> {
                when (command.template.lowercase()) {
                    "summarize_page" -> {
                        CommandExecutionResult.TriggerPageAction("summarize_page", queryArg)
                    }
                    "translate_page" -> {
                        if (queryArg.isNotBlank()) {
                            val encoded = URLEncoder.encode(queryArg, "UTF-8")
                            CommandExecutionResult.OpenUrl("https://translate.google.com/?sl=auto&tl=en&text=$encoded&op=translate")
                        } else {
                            val target = context.currentUrl
                            if (target.isNotBlank()) {
                                val encoded = URLEncoder.encode(target, "UTF-8")
                                CommandExecutionResult.OpenUrl("https://translate.google.com/translate?sl=auto&tl=en&u=$encoded")
                            } else {
                                CommandExecutionResult.TriggerPageAction("translate_page")
                            }
                        }
                    }
                    "extract_info" -> {
                        CommandExecutionResult.TriggerPageAction("extract_info", queryArg)
                    }
                    "reader_mode" -> {
                        CommandExecutionResult.TriggerBrowserAction(BrowserActionType.READER_MODE)
                    }
                    else -> {
                        CommandExecutionResult.TriggerPageAction(command.template, queryArg)
                    }
                }
            }

            CommandType.AUTOMATION -> {
                // Security check on JavaScript automation
                val safetyValidation = validateJavaScriptSafety(command.template)
                if (!safetyValidation.isSafe) {
                    CommandExecutionResult.ShowMessage(
                        message = "Blocked unsafe command: ${safetyValidation.reason}",
                        isError = true
                    )
                } else {
                    val code = substituteVariables(command.template, finalContext, urlEncodeQuery = false)
                    CommandExecutionResult.RunSafeJavaScript(code, description = command.name)
                }
            }
        }
    }

    /**
     * Variable & Placeholder Substitution:
     * - {query}: argument passed to command
     * - {url}: current page url
     * - {title}: current page title
     * - {domain}: current page host
     * - {selection}: current selection text
     * - {clipboard}: current clipboard text
     *
     * Also supports multi-variable patterns like "/translate {query} to {language}"
     */
    fun substituteVariables(
        template: String,
        context: CommandExecutionContext,
        urlEncodeQuery: Boolean = false
    ): String {
        val rawQuery = context.query
        val processedQuery = if (urlEncodeQuery) {
            try { URLEncoder.encode(rawQuery, "UTF-8") } catch (_: Exception) { rawQuery }
        } else {
            rawQuery
        }

        var result = template
            .replace("{query}", processedQuery)
            .replace("{url}", context.currentUrl)
            .replace("{title}", context.currentTitle)
            .replace("{domain}", context.currentDomain)
            .replace("{selection}", context.selectionText)
            .replace("{clipboard}", context.clipboardText)

        // If template has specific named variables (e.g. {language}) and query contained "to <lang>"
        if (template.contains("{language}")) {
            val toRegex = Regex("\\bto\\s+([a-zA-Z]+)", RegexOption.IGNORE_CASE)
            val match = toRegex.find(rawQuery)
            val lang = match?.groupValues?.getOrNull(1) ?: "en"
            result = result.replace("{language}", lang)
        }

        // Clean any leftover double encoding or fallback
        return result
    }

    /**
     * Security Enforcement:
     * Sandbox check for JavaScript Automation commands.
     * Prevents access to browser cookies, passwords, local storage, private databases, or arbitrary script injections.
     */
    data class SafetyCheckResult(val isSafe: Boolean, val reason: String? = null)

    fun validateJavaScriptSafety(jsCode: String): SafetyCheckResult {
        val lower = jsCode.lowercase()
        val forbiddenKeywords = listOf(
            "document.cookie" to "Access to browser cookies is strictly prohibited",
            "localstorage" to "Access to private local storage is prohibited",
            "sessionstorage" to "Access to private session storage is prohibited",
            "indexeddb" to "Access to browser database storage is prohibited",
            "password" to "Access to credential fields is prohibited",
            "eval(" to "Dynamic code execution via eval is disabled",
            "__android" to "Access to Android native bridge interfaces is restricted",
            "webextension" to "Access to internal extension APIs is prohibited"
        )

        for ((keyword, reason) in forbiddenKeywords) {
            if (lower.contains(keyword)) {
                return SafetyCheckResult(false, reason)
            }
        }
        return SafetyCheckResult(true)
    }

    /**
     * Check for command conflicts.
     * Returns a warning string if a user-created command's trigger or aliases collide with built-in commands.
     */
    fun checkConflicts(command: CustomCommandEntity, existingCommands: List<CustomCommandEntity>): List<String> {
        val warnings = mutableListOf<String>()
        val allTriggers = command.getAllTriggers()

        for (existing in existingCommands) {
            if (existing.id == command.id) continue
            val overlapping = allTriggers.intersect(existing.getAllTriggers().toSet())
            if (overlapping.isNotEmpty()) {
                val label = if (existing.isBuiltIn) "Built-in command '${existing.name}'" else "Existing command '${existing.name}'"
                warnings.add("Trigger(s) ${overlapping.joinToString(", ")} conflict with $label. Built-in commands have priority.")
            }
        }
        return warnings
    }

    fun isCommandCandidate(input: String, registeredCommands: List<CustomCommandEntity>): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("/")) return true
        val token = trimmed.split(" ").firstOrNull() ?: trimmed
        val allCommands = mergeWithBuiltIns(registeredCommands)
        return allCommands.any { it.isEnabled && it.matchesTrigger(token) }
    }

    fun parseResult(
        input: String,
        registeredCommands: List<CustomCommandEntity>,
        context: CommandExecutionContext
    ): CommandExecutionResult? {
        return parse(input, registeredCommands, context)?.second
    }

    /**
     * Autocomplete suggestions generation.
     */
    fun getSuggestions(
        input: String,
        registeredCommands: List<CustomCommandEntity>,
        context: CommandExecutionContext = CommandExecutionContext()
    ): List<CommandSuggestion> {
        val trimmed = input.trim()
        val allCommands = mergeWithBuiltIns(registeredCommands).filter { it.isEnabled }

        if (trimmed == "/" || trimmed.isEmpty()) {
            // Show all commands sorted by pinned first, then by category and name
            return allCommands
                .sortedWith(compareByDescending<CustomCommandEntity> { it.isPinned }
                    .thenBy { it.category.ordinal }
                    .thenBy { it.command })
                .map { cmd ->
                    CommandSuggestion(
                        command = cmd,
                        matchedTrigger = cmd.command,
                        userQuery = "",
                        previewText = cmd.description,
                        isExactMatch = false
                    )
                }
        }

        val spaceIndex = trimmed.indexOf(' ')
        val token = if (spaceIndex != -1) trimmed.substring(0, spaceIndex).trim() else trimmed
        val queryArg = if (spaceIndex != -1) trimmed.substring(spaceIndex + 1).trim() else ""

        val normalizedToken = if (token.startsWith("/")) token.lowercase() else "/${token.lowercase()}"

        val matchedSuggestions = mutableListOf<CommandSuggestion>()

        for (cmd in allCommands) {
            // Check exact trigger match
            val exactMatch = cmd.getAllTriggers().find { it.equals(normalizedToken, ignoreCase = true) }
            if (exactMatch != null) {
                val preview = if (queryArg.isNotEmpty()) {
                    when (cmd.type) {
                        CommandType.SEARCH -> "Search ${cmd.targetProvider ?: cmd.name} for \"$queryArg\""
                        CommandType.AI -> "Ask GVONE AI: \"$queryArg\""
                        CommandType.URL -> "Open ${cmd.name} with \"$queryArg\""
                        CommandType.PAGE_ACTION -> "${cmd.name}: $queryArg"
                        else -> "${cmd.name} ($queryArg)"
                    }
                } else {
                    cmd.description
                }
                matchedSuggestions.add(
                    CommandSuggestion(
                        command = cmd,
                        matchedTrigger = exactMatch,
                        userQuery = queryArg,
                        previewText = preview,
                        isExactMatch = true
                    )
                )
                continue
            }

            // Prefix match on trigger or alias (e.g. "/y" matches "/yt")
            val prefixMatch = cmd.getAllTriggers().find { it.startsWith(normalizedToken, ignoreCase = true) }
            if (prefixMatch != null && spaceIndex == -1) {
                matchedSuggestions.add(
                    CommandSuggestion(
                        command = cmd,
                        matchedTrigger = prefixMatch,
                        userQuery = "",
                        previewText = "${cmd.name} — ${cmd.description}",
                        isExactMatch = false
                    )
                )
                continue
            }

            // Keyword match on name or description if user typed a word
            val searchWord = token.removePrefix("/").lowercase()
            if (searchWord.length >= 2 && (cmd.name.lowercase().contains(searchWord) || cmd.description.lowercase().contains(searchWord))) {
                matchedSuggestions.add(
                    CommandSuggestion(
                        command = cmd,
                        matchedTrigger = cmd.command,
                        userQuery = queryArg,
                        previewText = cmd.description,
                        isExactMatch = false
                    )
                )
            }
        }

        // Sort: Exact matches first, then pinned, then others
        return matchedSuggestions.sortedWith(
            compareByDescending<CommandSuggestion> { it.isExactMatch }
                .thenByDescending { it.command.isPinned }
        )
    }

    /**
     * Merge stored user commands with built-in commands without duplicates.
     */
    fun mergeWithBuiltIns(userCommands: List<CustomCommandEntity>): List<CustomCommandEntity> {
        val userMap = userCommands.associateBy { it.id }
        val result = mutableListOf<CustomCommandEntity>()

        for (builtIn in BUILT_IN_COMMANDS) {
            val userOverride = userMap[builtIn.id]
            if (userOverride != null) {
                // Keep user's enabled/pinned/aliases settings on built-in
                result.add(userOverride.copy(isBuiltIn = true))
            } else {
                result.add(builtIn)
            }
        }

        // Add user-created custom commands that are not built-in overrides
        for (userCmd in userCommands) {
            if (BUILT_IN_COMMANDS.none { it.id == userCmd.id }) {
                result.add(userCmd)
            }
        }

        return result
    }

    /**
     * Pre-Packaged Command Collections that users can install with 1 tap.
     */
    data class CommandPack(
        val id: String,
        val name: String,
        val description: String,
        val icon: String,
        val commands: List<CustomCommandEntity>
    )

    val PRESET_COMMAND_PACKS: List<CommandPack> = listOf(
        CommandPack(
            id = "developer_pack",
            name = "Developer Pack",
            description = "Essential shortcuts for engineers: GitHub, StackOverflow, NPM, MDN, DevDocs",
            icon = "code",
            commands = listOf(
                CustomCommandEntity(
                    id = "pack_so",
                    command = "/so",
                    name = "StackOverflow Search",
                    description = "Search developer answers on StackOverflow",
                    type = CommandType.SEARCH,
                    template = "https://stackoverflow.com/search?q={query}",
                    aliasesRaw = "/stackoverflow",
                    category = CommandCategory.SEARCH,
                    targetProvider = "StackOverflow"
                ),
                CustomCommandEntity(
                    id = "pack_npm",
                    command = "/npm",
                    name = "NPM Package Search",
                    description = "Search node.js packages on npmjs.com",
                    type = CommandType.SEARCH,
                    template = "https://www.npmjs.com/search?q={query}",
                    aliasesRaw = "/pkg",
                    category = CommandCategory.SEARCH,
                    targetProvider = "NPM"
                ),
                CustomCommandEntity(
                    id = "pack_mdn",
                    command = "/mdn",
                    name = "MDN Web Docs",
                    description = "Look up web APIs, CSS, and HTML documentation",
                    type = CommandType.SEARCH,
                    template = "https://developer.mozilla.org/en-US/search?q={query}",
                    aliasesRaw = "/webdocs",
                    category = CommandCategory.SEARCH,
                    targetProvider = "MDN"
                )
            )
        ),
        CommandPack(
            id = "study_pack",
            name = "Study & Research Pack",
            description = "Academic tools: PubMed, ArXiv, Wikipedia, and MBBS technical explainer",
            icon = "school",
            commands = listOf(
                CustomCommandEntity(
                    id = "pack_pubmed",
                    command = "/pubmed",
                    name = "PubMed Medical Search",
                    description = "Search biomedical literature and MEDLINE database",
                    type = CommandType.SEARCH,
                    template = "https://pubmed.ncbi.nlm.nih.gov/?term={query}",
                    aliasesRaw = "/med,/ncbi",
                    category = CommandCategory.SEARCH,
                    targetProvider = "PubMed"
                ),
                CustomCommandEntity(
                    id = "pack_arxiv",
                    command = "/arxiv",
                    name = "arXiv Research Preprints",
                    description = "Search scientific papers in physics, math, and AI",
                    type = CommandType.SEARCH,
                    template = "https://arxiv.org/search/?query={query}&searchtype=all",
                    aliasesRaw = "/paper",
                    category = CommandCategory.SEARCH,
                    targetProvider = "arXiv"
                )
            )
        ),
        CommandPack(
            id = "media_pack",
            name = "Media & Social Pack",
            description = "Quick navigation for YouTube, Spotify, X (Twitter), Twitch",
            icon = "play_circle",
            commands = listOf(
                CustomCommandEntity(
                    id = "pack_spotify",
                    command = "/spotify",
                    name = "Spotify Search",
                    description = "Search songs, artists, and playlists on Spotify",
                    type = CommandType.SEARCH,
                    template = "https://open.spotify.com/search/{query}",
                    aliasesRaw = "/music,/spot",
                    category = CommandCategory.SEARCH,
                    targetProvider = "Spotify"
                ),
                CustomCommandEntity(
                    id = "pack_x",
                    command = "/x",
                    name = "X / Twitter Search",
                    description = "Search posts and trends on X",
                    type = CommandType.SEARCH,
                    template = "https://x.com/search?q={query}",
                    aliasesRaw = "/twitter,/tweet",
                    category = CommandCategory.SEARCH,
                    targetProvider = "X"
                ),
                CustomCommandEntity(
                    id = "pack_twitch",
                    command = "/twitch",
                    name = "Twitch Search",
                    description = "Search live streams on Twitch",
                    type = CommandType.SEARCH,
                    template = "https://www.twitch.tv/search?term={query}",
                    aliasesRaw = "/stream",
                    category = CommandCategory.SEARCH,
                    targetProvider = "Twitch"
                )
            )
        )
    )

    /**
     * JSON Export: Export custom commands collection to JSON string.
     */
    fun exportCommandsToJson(commands: List<CustomCommandEntity>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val arr = JSONArray()
        commands.forEach { arr.put(it.toJson()) }
        root.put("commands", arr)
        return root.toString(2)
    }

    /**
     * JSON Import: Parse JSON string into list of CustomCommandEntity.
     */
    fun importCommandsFromJson(jsonString: String): List<CustomCommandEntity> {
        val result = mutableListOf<CustomCommandEntity>()
        val root = JSONObject(jsonString)
        val arr = root.optJSONArray("commands") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(CustomCommandEntity.fromJson(obj))
        }
        return result
    }

    /**
     * Local Smart Rule-Based Command Generator for Natural Language queries
     * (used directly or as fallback when offline or no API key):
     * e.g. "Create a command /yt that searches YouTube"
     * "Create /wiki that searches Wikipedia"
     * "Create /reddit that searches Reddit"
     */
    fun generateCommandFromNaturalLanguageLocally(nlPrompt: String): CustomCommandEntity {
        val prompt = nlPrompt.trim()
        val commandRegex = Regex("/[a-zA-Z0-9_-]+")
        val matchedCommand = commandRegex.find(prompt)?.value ?: "/cmd"

        val lower = prompt.lowercase()
        return when {
            lower.contains("youtube") -> {
                CustomCommandEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    command = matchedCommand,
                    name = "YouTube Search",
                    description = "Search YouTube for videos",
                    type = CommandType.SEARCH,
                    template = "https://www.youtube.com/results?search_query={query}",
                    aliasesRaw = "/yt,/youtube",
                    category = CommandCategory.SEARCH,
                    targetProvider = "YouTube"
                )
            }
            lower.contains("wikipedia") -> {
                CustomCommandEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    command = matchedCommand,
                    name = "Wikipedia Search",
                    description = "Search Wikipedia encyclopedia",
                    type = CommandType.SEARCH,
                    template = "https://en.wikipedia.org/wiki/Special:Search?search={query}",
                    aliasesRaw = "/wiki,/w",
                    category = CommandCategory.SEARCH,
                    targetProvider = "Wikipedia"
                )
            }
            lower.contains("reddit") -> {
                CustomCommandEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    command = matchedCommand,
                    name = "Reddit Search",
                    description = "Search Reddit discussions",
                    type = CommandType.SEARCH,
                    template = "https://www.reddit.com/search/?q={query}",
                    aliasesRaw = "/r,/red",
                    category = CommandCategory.SEARCH,
                    targetProvider = "Reddit"
                )
            }
            lower.contains("github") -> {
                CustomCommandEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    command = matchedCommand,
                    name = "GitHub Search",
                    description = "Search code repositories on GitHub",
                    type = CommandType.SEARCH,
                    template = "https://github.com/search?q={query}",
                    aliasesRaw = "/gh,/git",
                    category = CommandCategory.SEARCH,
                    targetProvider = "GitHub"
                )
            }
            lower.contains("explain") || lower.contains("ai") || lower.contains("mbbs") -> {
                CustomCommandEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    command = matchedCommand,
                    name = "GVONE AI Analysis",
                    description = "Analyze topics with GVONE AI",
                    type = CommandType.AI,
                    template = "Explain the following in detail: {query}",
                    aliasesRaw = "",
                    category = CommandCategory.GVONE
                )
            }
            lower.contains("scroll") -> {
                CustomCommandEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    command = matchedCommand,
                    name = "Scroll Automation",
                    description = "Smoothly scroll the webpage",
                    type = CommandType.AUTOMATION,
                    template = if (lower.contains("bottom")) "window.scrollTo({top: document.body.scrollHeight, behavior: 'smooth'});" else "window.scrollTo({top: 0, behavior: 'smooth'});",
                    aliasesRaw = "",
                    category = CommandCategory.AUTOMATION
                )
            }
            else -> {
                // Default search / url command
                val providerName = prompt.replace("Create", "", ignoreCase = true)
                    .replace("a command", "", ignoreCase = true)
                    .replace("that searches", "", ignoreCase = true)
                    .replace(matchedCommand, "")
                    .trim()
                    .ifBlank { "Custom Search" }
                CustomCommandEntity(
                    id = "custom_${System.currentTimeMillis()}",
                    command = matchedCommand,
                    name = providerName.replaceFirstChar { it.uppercase() },
                    description = "Custom command for $providerName",
                    type = CommandType.SEARCH,
                    template = "https://www.google.com/search?q={query}",
                    aliasesRaw = "",
                    category = CommandCategory.CUSTOM,
                    targetProvider = providerName
                )
            }
        }
    }
}
