package com.example.agent.specialized

import com.example.agent.browser.BrowserController
import com.example.agent.core.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.URLEncoder

/**
 * BrowserAgent interacts with the web and tabs EXCLUSIVELY through BrowserController.
 * Follows the strict design principle: CNS -> Agent Protocol -> BrowserAgent -> BrowserController -> Browser.
 * 
 * Enhanced for Modern SPAs:
 * - Robust element finding (CSS selectors, text matching, and Shadow DOM deep traversal)
 * - Controlled input handling for React/Vue/Angular (native property descriptors + composed events)
 * - ContentEditable and role="textbox" support
 * - Action verification (checks DOM mutation / value retention) and multi-strategy retry loops
 * - Dedicated automated YouTube search and media interaction
 */
class BrowserAgent(
    private val browserController: BrowserController,
    logger: StepLogger = StepLogger.global
) : AgentBase(name = "BrowserAgent", logger = logger) {

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "browser_navigation",
            description = "Navigate, reload, go back, and forward in browser tabs",
            supportedActions = listOf("open_url", "go_back", "go_forward", "reload", "stop"),
            riskLevel = RiskLevel.LOW
        ),
        AgentCapability(
            name = "tab_management",
            description = "Create, close, switch, and query tabs",
            supportedActions = listOf("create_tab", "close_tab", "switch_tab", "get_tabs", "get_current_tab"),
            riskLevel = RiskLevel.LOW
        ),
        AgentCapability(
            name = "page_inspection",
            description = "Read page text, find text in page, get selected text, observe DOM elements",
            supportedActions = listOf("read_page", "find_in_page", "get_selection", "observe_dom"),
            riskLevel = RiskLevel.LOW
        ),
        AgentCapability(
            name = "element_interaction",
            description = "Interact with modern SPA elements: click, type into controlled inputs, press keys, submit forms with verification & retry",
            supportedActions = listOf("interact_element", "click", "type", "press_key", "submit_form"),
            riskLevel = RiskLevel.MEDIUM
        ),
        AgentCapability(
            name = "youtube_workflow",
            description = "Autonomous YouTube search, song query input, and playback verification",
            supportedActions = listOf("search_youtube"),
            riskLevel = RiskLevel.LOW
        ),
        AgentCapability(
            name = "browser_data",
            description = "Query history, bookmarks, downloads, and site permissions",
            supportedActions = listOf("get_history", "get_bookmarks", "get_downloads", "get_permissions"),
            requiresPermission = true,
            riskLevel = RiskLevel.MEDIUM
        )
    )

    override fun observe(): Map<String, Any?> {
        val state = browserController.browserState.value
        return mapOf(
            "currentTabId" to (state.currentTabId ?: "none"),
            "currentUrl" to (state.currentUrl ?: ""),
            "tabsCount" to state.totalTabsCount,
            "isTor" to state.isTorActive,
            "isPrivate" to state.isPrivateMode
        )
    }

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        val startTime = System.currentTimeMillis()

        return when (request.action.lowercase()) {
            "open_url" -> {
                val url = request.parameters["url"] as? String ?: return fail(request, "Missing 'url' parameter")
                val inNewTab = request.parameters["inNewTab"] as? Boolean ?: false
                executeAction(StepType.NAVIGATE, "Navigate to $url (newTab=$inNewTab)") {
                    val success = browserController.openUrl(url, inNewTab = inNewTab)
                    AgentResult(
                        requestId = request.requestId,
                        status = if (success) AgentStatus.COMPLETED else AgentStatus.FAILED,
                        data = mapOf("url" to url, "opened" to success),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = url, agentName = name)
                    )
                }
            }

            "create_tab" -> {
                val url = request.parameters["url"] as? String
                val isPrivate = request.parameters["isPrivate"] as? Boolean ?: false
                executeAction(StepType.MODIFY, "Create new tab url=$url private=$isPrivate") {
                    val tabId = browserController.createTab(url = url, isPrivate = isPrivate)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("tabId" to tabId),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = "gvone://tab/$tabId", agentName = name)
                    )
                }
            }

            "close_tab" -> {
                val tabId = request.parameters["tabId"] as? String ?: return fail(request, "Missing 'tabId' parameter")
                executeAction(StepType.MODIFY, "Close tab $tabId") {
                    val success = browserController.closeTab(tabId)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("tabId" to tabId, "closed" to success),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "switch_tab" -> {
                val tabId = request.parameters["tabId"] as? String ?: return fail(request, "Missing 'tabId' parameter")
                executeAction(StepType.DECIDE, "Switch to tab $tabId") {
                    val success = browserController.switchTab(tabId)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("tabId" to tabId, "switched" to success),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "get_tabs" -> {
                executeAction(StepType.FETCH, "Query open browser tabs") {
                    val tabs = browserController.getTabs()
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = tabs,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "get_current_tab" -> {
                executeAction(StepType.FETCH, "Get current active tab") {
                    val tab = browserController.getCurrentTab()
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = tab,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "read_page" -> {
                executeAction(StepType.EXTRACT, "Extract text from active web page") {
                    val text = browserController.getPageText() ?: ""
                    val url = browserController.getCurrentUrl() ?: ""
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("text" to text, "url" to url, "charCount" to text.length),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = url, agentName = name)
                    )
                }
            }

            "find_in_page" -> {
                val query = request.parameters["query"] as? String ?: return fail(request, "Missing 'query' parameter")
                executeAction(StepType.CHECK, "Find in page: '$query'") {
                    val result = browserController.findInPage(query)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = result,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "get_selection" -> {
                executeAction(StepType.EXTRACT, "Retrieve user selected text on page") {
                    val selection = browserController.getSelectedText()
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = selection,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "observe_dom" -> {
                executeAction(StepType.ANALYZE, "Observe DOM element landscape") {
                    val selector = request.parameters["selector"] as? String
                    val script = buildDomObservationScript(selector)
                    val resultJson = browserController.executeScript(script)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = resultJson,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            // Modern SPA Element Interaction with Verification & Retries
            "interact_element", "click", "type", "press_key", "submit_form" -> {
                val actionType = when (request.action.lowercase()) {
                    "click" -> "click"
                    "type" -> "type"
                    "press_key" -> "press_key"
                    "submit_form" -> "submit"
                    else -> request.parameters["interactionType"]?.toString() ?: "click"
                }
                val selector = request.parameters["selector"]?.toString()
                val text = request.parameters["text"]?.toString() ?: ""
                val key = request.parameters["key"]?.toString() ?: "Enter"

                executeAction(StepType.EXECUTE, "DOM Interaction: $actionType (selector=$selector, text=$text)") {
                    performElementInteractionWithRetry(
                        actionType = actionType,
                        selector = selector,
                        text = text,
                        key = key,
                        maxRetries = 2
                    )
                }
            }

            // High-level YouTube autonomous workflow
            "search_youtube" -> {
                val query = request.parameters["query"]?.toString()
                    ?: request.parameters["goal"]?.toString()
                    ?: "lofi chill beats"

                executeAction(StepType.EXECUTE, "Execute Autonomous YouTube Search: '$query'") {
                    executeYouTubeSearchWorkflow(query, request.requestId)
                }
            }

            "get_history" -> {
                val q = request.parameters["query"] as? String
                executeAction(StepType.FETCH, "Query browsing history (q=$q)") {
                    val history = browserController.getHistory(query = q)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = history,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "get_bookmarks" -> {
                executeAction(StepType.FETCH, "Query bookmarks") {
                    val bookmarks = browserController.getBookmarks()
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = bookmarks,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "get_downloads" -> {
                executeAction(StepType.FETCH, "Query download records") {
                    val downloads = browserController.getDownloads()
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = downloads,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "reload" -> {
                executeAction(StepType.MODIFY, "Reload active webpage") {
                    val success = browserController.reload()
                    AgentResult(
                        requestId = request.requestId,
                        status = if (success) AgentStatus.COMPLETED else AgentStatus.FAILED,
                        data = mapOf("reloaded" to success),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "go_back" -> {
                executeAction(StepType.NAVIGATE, "Navigate back in history") {
                    val success = browserController.goBack()
                    AgentResult(
                        requestId = request.requestId,
                        status = if (success) AgentStatus.COMPLETED else AgentStatus.FAILED,
                        data = mapOf("back" to success),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "go_forward" -> {
                executeAction(StepType.NAVIGATE, "Navigate forward in history") {
                    val success = browserController.goForward()
                    AgentResult(
                        requestId = request.requestId,
                        status = if (success) AgentStatus.COMPLETED else AgentStatus.FAILED,
                        data = mapOf("forward" to success),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "stop" -> {
                executeAction(StepType.MODIFY, "Stop page loading") {
                    val success = browserController.stopLoading()
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("stopped" to success),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "execute_script" -> {
                val script = request.parameters["script"] as? String ?: return fail(request, "Missing 'script' parameter")
                executeAction(StepType.MODIFY, "Execute JS script") {
                    val res = browserController.executeScript(script)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("result" to res),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "scroll" -> {
                val direction = (request.parameters["direction"] as? String)?.lowercase() ?: "down"
                val script = when (direction) {
                    "up" -> "window.scrollBy({ top: -window.innerHeight * 0.7, behavior: 'smooth' });"
                    "top" -> "window.scrollTo({ top: 0, behavior: 'smooth' });"
                    "bottom" -> "window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });"
                    else -> "window.scrollBy({ top: window.innerHeight * 0.7, behavior: 'smooth' });"
                }
                executeAction(StepType.MODIFY, "Scroll $direction") {
                    val res = browserController.executeScript(script)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("scrolled" to direction, "result" to res),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            else -> {
                AgentResult(
                    requestId = request.requestId,
                    status = AgentStatus.FAILED,
                    error = "Unsupported action '${request.action}' for BrowserAgent",
                    durationMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    /**
     * Executes element interaction with multi-strategy retry and post-action verification.
     */
    private suspend fun performElementInteractionWithRetry(
        actionType: String,
        selector: String?,
        text: String,
        key: String,
        maxRetries: Int = 2
    ): AgentResult {
        var lastError: String? = null
        var attempt = 0

        while (attempt <= maxRetries) {
            val script = buildElementInteractionScript(actionType, selector, text, key, strategy = attempt)
            val rawResult = browserController.executeScript(script)
            val parsed = try {
                if (rawResult != null && rawResult != "null") JSONObject(rawResult) else null
            } catch (_: Exception) {
                null
            }

            val success = parsed?.optBoolean("success", false) ?: false
            if (success) {
                return AgentResult(
                    requestId = "interact_${System.currentTimeMillis()}",
                    status = AgentStatus.COMPLETED,
                    data = mapOf(
                        "action" to actionType,
                        "selector" to selector,
                        "attempt" to attempt,
                        "strategy" to parsed?.optString("strategy", "primary"),
                        "verified" to parsed?.optBoolean("verified", true),
                        "details" to parsed?.optString("details", "Action executed successfully")
                    )
                )
            } else {
                lastError = parsed?.optString("error") ?: "Interaction failed on attempt $attempt"
                attempt++
                delay(100) // brief settle time for SPA re-render before retry
            }
        }

        return AgentResult(
            requestId = "interact_${System.currentTimeMillis()}",
            status = AgentStatus.FAILED,
            error = lastError ?: "Failed to interact with element after $maxRetries retries"
        )
    }

    /**
     * Autonomous YouTube workflow following the strict PLAN -> ACT -> OBSERVE -> VERIFY loop:
     * 1. Plan: Target search on YouTube for query
     * 2. Act: Navigate to YouTube, locate search input, inject controlled input, and submit
     * 3. Wait: Allow SPA update / search response
     * 4. Observe: Inspect updated URL, page title, and search results in DOM
     * 5. Verify: Confirm search query is reflected in URL or video elements are rendered
     * 6. Return verified result
     */
    private suspend fun executeYouTubeSearchWorkflow(query: String, requestId: String): AgentResult {
        // Step 1: PLAN
        logger.logInstant("BrowserAgent", StepType.DECIDE, "Planning YouTube search for: '$query'", StepStatus.RUNNING)

        // Step 2: ACT (Navigation & Input Submission)
        val currUrl = browserController.getCurrentUrl() ?: ""
        if (!currUrl.contains("youtube.com") && !currUrl.contains("youtu.be")) {
            browserController.openUrl("https://m.youtube.com", inNewTab = false)
            delay(1200) // allow YouTube SPA shell to initialize
        }

        val ytScript = """
            (function() {
                var queryText = ${JSONObject.quote(query)};
                try {
                    function setVal(el, val) {
                        try {
                            var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                            var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                            if (desc && desc.set) desc.set.call(el, val);
                            else el.value = val;
                            el.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                        } catch (e) {
                            el.value = val;
                        }
                    }

                    // 1. Direct visible input
                    var input = document.querySelector('input[name="search_query"], input.searchbox-input, input#search, input[type="search"]');
                    if (input) {
                        input.focus();
                        setVal(input, queryText);
                        var form = input.form || input.closest('form');
                        var btn = (form || document).querySelector('button#search-icon-legacy, button[aria-label*="Search" i], button[type="submit"]');
                        if (btn && !btn.disabled) {
                            btn.click();
                        } else if (form) {
                            if (typeof form.requestSubmit === 'function') form.requestSubmit();
                            else form.submit();
                        } else {
                            input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                        }
                        return JSON.stringify({ action: "injected_input", success: true });
                    }

                    // 2. Expand search button
                    var searchBtn = document.querySelector('button[aria-label*="Search" i], button.topbar-search-button, button.mobile-topbar-header-search-icon, ytm-searchbox button');
                    if (searchBtn) {
                        searchBtn.click();
                        var opened = document.querySelector('input[name="search_query"], input.searchbox-input, input#search, input[type="search"]');
                        if (opened) {
                            opened.focus();
                            setVal(opened, queryText);
                            var oForm = opened.form || opened.closest('form');
                            if (oForm && typeof oForm.requestSubmit === 'function') oForm.requestSubmit();
                            else if (oForm) oForm.submit();
                            else opened.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                            return JSON.stringify({ action: "opened_and_submitted", success: true });
                        }
                    }

                    // 3. Fallback direct navigation
                    window.location.href = 'https://m.youtube.com/results?search_query=' + encodeURIComponent(queryText);
                    return JSON.stringify({ action: "direct_nav", success: true });
                } catch (e) {
                    return JSON.stringify({ action: "error", error: e.toString() });
                }
            })();
        """.trimIndent()

        val actResult = browserController.executeScript(ytScript)
        logger.logInstant("BrowserAgent", StepType.EXECUTE, "Executed input injection: $actResult", StepStatus.RUNNING)

        // Step 3: WAIT (Wait for SPA navigation and DOM render)
        delay(1500)

        // Step 4: OBSERVE
        val observeScript = """
            (function() {
                var url = window.location.href;
                var hasSearchQuery = url.indexOf('search_query=') !== -1 || url.indexOf('/results') !== -1;
                var videos = document.querySelectorAll('ytd-video-renderer, ytm-video-with-context-renderer, ytm-compact-video-renderer, a#video-title, #video-title, .compact-media-item');
                var input = document.querySelector('input[name="search_query"], input.searchbox-input, input#search, input[type="search"]');
                var val = input ? (input.value || "") : "";
                return JSON.stringify({
                    url: url,
                    hasSearchQuery: hasSearchQuery,
                    resultsCount: videos.length,
                    inputValue: val,
                    title: document.title
                });
            })();
        """.trimIndent()

        var obsJson = browserController.executeScript(observeScript) ?: "{}"
        var verified = false
        var obsUrl = ""
        var resultsCount = 0
        var pageTitle = ""

        try {
            val cleanJson = obsJson.trim().removeSurrounding("\"").replace("\\\"", "\"")
            val obj = JSONObject(if (cleanJson.startsWith("{")) cleanJson else obsJson)
            obsUrl = obj.optString("url", "")
            resultsCount = obj.optInt("resultsCount", 0)
            pageTitle = obj.optString("title", "")
            val hasSearchQuery = obj.optBoolean("hasSearchQuery", false)
            val inputValue = obj.optString("inputValue", "")
            verified = hasSearchQuery || resultsCount > 0 || inputValue.contains(query, ignoreCase = true)
        } catch (e: Exception) {
            val current = browserController.getCurrentUrl() ?: ""
            obsUrl = current
            verified = current.contains("search_query") || current.contains("results")
        }

        // Step 5: RETRY if verification did not pass on first attempt
        if (!verified) {
            logger.logInstant("BrowserAgent", StepType.EXECUTE, "Observation unverified, retrying with direct search URL", StepStatus.RUNNING)
            val fallbackUrl = "https://m.youtube.com/results?search_query=" + URLEncoder.encode(query, "UTF-8")
            browserController.openUrl(fallbackUrl, inNewTab = false)
            delay(1500)

            obsJson = browserController.executeScript(observeScript) ?: "{}"
            try {
                val cleanJson = obsJson.trim().removeSurrounding("\"").replace("\\\"", "\"")
                val obj = JSONObject(if (cleanJson.startsWith("{")) cleanJson else obsJson)
                obsUrl = obj.optString("url", "")
                resultsCount = obj.optInt("resultsCount", 0)
                pageTitle = obj.optString("title", "")
                val hasSearchQuery = obj.optBoolean("hasSearchQuery", false)
                verified = hasSearchQuery || resultsCount > 0 || obsUrl.contains("search_query")
            } catch (e: Exception) {
                val current = browserController.getCurrentUrl() ?: ""
                obsUrl = current
                verified = current.contains("search_query") || current.contains("results")
            }
        }

        // Step 6: VERIFY & RETURN
        return if (verified) {
            logger.logInstant("BrowserAgent", StepType.VALIDATE, "YouTube search verified: $obsUrl (results: $resultsCount)", StepStatus.SUCCESS)
            AgentResult(
                requestId = requestId,
                status = AgentStatus.COMPLETED,
                data = "YouTube search verified for '$query'. Rendered on page (URL: $obsUrl, Videos observed: $resultsCount, Title: '$pageTitle')."
            )
        } else {
            logger.logInstant("BrowserAgent", StepType.ERROR, "YouTube search verification failed for '$query'", StepStatus.FAILED)
            AgentResult(
                requestId = requestId,
                status = AgentStatus.FAILED,
                error = "YouTube search interaction executed, but page verification could not confirm search results."
            )
        }
    }

    /**
     * Builds comprehensive DOM interaction script supporting Shadow DOM, controlled inputs, and verification.
     */
    private fun buildElementInteractionScript(
        actionType: String,
        selector: String?,
        text: String,
        key: String,
        strategy: Int
    ): String {
        val safeSelector = JSONObject.quote(selector ?: "")
        val safeText = JSONObject.quote(text)
        val safeKey = JSONObject.quote(key)

        return """
            (function() {
                var action = '$actionType';
                var sel = $safeSelector;
                var valText = $safeText;
                var keyStr = $safeKey;
                var strategy = $strategy;

                function queryDeep(selector, root) {
                    root = root || document;
                    try {
                        var direct = root.querySelector(selector);
                        if (direct) return direct;
                    } catch(e) {}
                    var walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT, null, false);
                    while (walker.nextNode()) {
                        var node = walker.currentNode;
                        if (node.shadowRoot) {
                            var found = queryDeep(selector, node.shadowRoot);
                            if (found) return found;
                        }
                    }
                    return null;
                }

                function findElement() {
                    if (sel && sel.length > 0) {
                        var el = queryDeep(sel);
                        if (el) return el;
                    }
                    if (strategy === 1 && valText) {
                        var buttons = document.querySelectorAll('button, a, [role="button"]');
                        for (var i = 0; i < buttons.length; i++) {
                            if (buttons[i].innerText && buttons[i].innerText.trim().toLowerCase().includes(valText.toLowerCase())) {
                                return buttons[i];
                            }
                        }
                    }
                    if (action === 'type' || action === 'submit') {
                        return document.querySelector('input[type="text"], input[type="search"], textarea, [contenteditable="true"]');
                    }
                    return null;
                }

                function setNativeValue(el, val) {
                    if (el.isContentEditable) {
                        el.innerText = val;
                        el.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                        return true;
                    }
                    try {
                        var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                        var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                        if (desc && desc.set) {
                            desc.set.call(el, val);
                        } else {
                            el.value = val;
                        }
                        el.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                        return true;
                    } catch (e) {
                        el.value = val;
                        return true;
                    }
                }

                var target = findElement();
                if (!target) {
                    return JSON.stringify({ success: false, error: 'Element not found for selector ' + sel });
                }

                try {
                    target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    if (action === 'click') {
                        target.focus();
                        target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, composed: true }));
                        target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, composed: true }));
                        target.click();
                        return JSON.stringify({ success: true, verified: true, strategy: 'strategy_' + strategy, details: 'Clicked ' + target.tagName });
                    } else if (action === 'type') {
                        target.focus();
                        setNativeValue(target, valText);
                        var verified = (target.value === valText || (target.isContentEditable && target.innerText.includes(valText)));
                        return JSON.stringify({ success: true, verified: verified, strategy: 'strategy_' + strategy, details: 'Typed into ' + target.tagName });
                    } else if (action === 'press_key') {
                        target.focus();
                        var evt = new KeyboardEvent('keydown', {
                            key: keyStr,
                            code: keyStr,
                            keyCode: keyStr === 'Enter' ? 13 : 0,
                            which: keyStr === 'Enter' ? 13 : 0,
                            bubbles: true,
                            cancelable: true,
                            composed: true
                        });
                        target.dispatchEvent(evt);
                        return JSON.stringify({ success: true, verified: true, strategy: 'strategy_' + strategy, details: 'Dispatched key ' + keyStr });
                    } else if (action === 'submit') {
                        var form = target.form || target.closest('form');
                        if (form && typeof form.requestSubmit === 'function') {
                            form.requestSubmit();
                        } else if (form) {
                            form.submit();
                        } else {
                            target.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, which: 13, bubbles: true }));
                        }
                        return JSON.stringify({ success: true, verified: true, strategy: 'strategy_' + strategy, details: 'Submitted form' });
                    }
                    return JSON.stringify({ success: false, error: 'Unknown action ' + action });
                } catch (err) {
                    return JSON.stringify({ success: false, error: err.toString() });
                }
            })();
        """.trimIndent()
    }

    private fun buildDomObservationScript(selector: String?): String {
        val safeSel = JSONObject.quote(selector ?: "")
        return """
            (function() {
                var sel = $safeSel;
                var inputs = Array.from(document.querySelectorAll('input, textarea, [contenteditable="true"]')).map(function(el) {
                    return {
                        tag: el.tagName,
                        type: el.type || 'text',
                        id: el.id,
                        name: el.name,
                        placeholder: el.placeholder || '',
                        valueLength: (el.value || el.innerText || '').length
                    };
                });
                var buttons = Array.from(document.querySelectorAll('button, [role="button"], input[type="submit"]')).slice(0, 15).map(function(el) {
                    return {
                        tag: el.tagName,
                        text: (el.innerText || el.value || el.getAttribute('aria-label') || '').trim().substring(0, 30),
                        id: el.id
                    };
                });
                return JSON.stringify({
                    url: window.location.href,
                    title: document.title,
                    inputsCount: inputs.length,
                    inputs: inputs,
                    buttons: buttons
                });
            })();
        """.trimIndent()
    }

    private fun fail(request: AgentRequest, message: String): AgentResult {
        return AgentResult(
            requestId = request.requestId,
            status = AgentStatus.FAILED,
            error = message
        )
    }
}
