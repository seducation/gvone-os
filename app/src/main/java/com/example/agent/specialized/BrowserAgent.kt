package com.example.agent.specialized

import com.example.agent.browser.BrowserController
import com.example.agent.core.*

/**
 * BrowserAgent interacts with the web and tabs EXCLUSIVELY through BrowserController.
 * Follows the strict design principle: CNS -> Agent Protocol -> BrowserAgent -> BrowserController -> Browser.
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
            description = "Read page text, find text in page, get selected text",
            supportedActions = listOf("read_page", "find_in_page", "get_selection"),
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

        return when (request.action) {
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

    private fun fail(request: AgentRequest, message: String): AgentResult {
        return AgentResult(
            requestId = request.requestId,
            status = AgentStatus.FAILED,
            error = message
        )
    }
}
