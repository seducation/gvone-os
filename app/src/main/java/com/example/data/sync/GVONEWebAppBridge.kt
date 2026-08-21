package com.example.data.sync

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URI
import java.util.Locale

/**
 * Web App Connection and Processing States
 */
enum class WebAppConnectionState {
    IDLE,
    CONNECTING,
    READY,
    PROCESSING,
    COMPLETED,
    UNAVAILABLE
}

/**
 * Input classification for universal address bar routing
 */
enum class InputDestination {
    NAVIGATE_URL,
    DELIVER_TO_WEB_APP,
    UNIVERSAL_SEARCH,
    AI_SEARCH
}

/**
 * Model for Browser -> Web App Communication
 */
data class BrowserToWebAppMessage(
    val type: String = "address_bar_input",
    val text: String,
    val action: String = "submit",
    val source: String = "gvone_browser",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("type", type)
            put("text", text)
            put("action", action)
            put("source", source)
            put("timestamp", timestamp)
        }.toString()
    }
}

/**
 * Page Context Detector:
 * Identifies trusted GVONE Web App origins, handshake states, and security context.
 */
object PageContextDetector {
    private val TRUSTED_DOMAINS = setOf(
        "charassist-c4uzg7hb.manus.space",
        "rssgroupfeed-jaelvwfd.manus.space",
        "gvone.app",
        "gvone.io",
        "gvone.com",
        "manus.space"
    )

    fun isTrustedGVONEOrigin(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (trimmed == "gvone://newtab" || trimmed.startsWith("gvone://")) return true

        return try {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false
            TRUSTED_DOMAINS.any { trusted ->
                host == trusted || host.endsWith(".$trusted")
            }
        } catch (_: Exception) {
            val lower = trimmed.lowercase(Locale.ROOT)
            TRUSTED_DOMAINS.any { lower.contains(it) }
        }
    }

    fun getCleanOrigin(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme ?: "https"
            val host = uri.host ?: return null
            val port = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""
            "$scheme://$host$port"
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Universal Input Router:
 * Determines whether user input should be routed to normal browser navigation,
 * delivered directly to the currently active GVONE Web App, or searched.
 */
object InputRouter {
    private val URL_PATTERN = Regex(
        "^(https?://|ftp://|file://|gvone://|about:|chrome:)[^\\s/$.?#].[^\\s]*$",
        RegexOption.IGNORE_CASE
    )

    private val DOMAIN_PATTERN = Regex(
        "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z]{2,})+(/.*)?$"
    )

    private val IP_PORT_PATTERN = Regex(
        "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|localhost)(:\\d{1,5})?(/.*)?$"
    )

    /**
     * Determines if the input string is a valid URL/domain or an intentional web address.
     */
    fun isExplicitUrl(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return false

        // If it contains spaces, it's definitely a search query / chat message
        if (trimmed.contains(" ") || trimmed.contains("\n")) return false

        if (URL_PATTERN.matches(trimmed)) return true
        if (DOMAIN_PATTERN.matches(trimmed)) return true
        if (IP_PORT_PATTERN.matches(trimmed)) return true

        return false
    }

    /**
     * Routes the raw address bar input according to the active page context and user intent.
     */
    fun resolveRouting(
        input: String,
        currentTabUrl: String?,
        isWebAppReady: Boolean,
        inputRouterEnabled: Boolean = true,
        bridgeApplyToAllWebsites: Boolean = true
    ): InputDestination {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return InputDestination.UNIVERSAL_SEARCH

        // 1. If it's a valid URL or domain, always navigate Chromium directly
        if (isExplicitUrl(trimmed)) {
            return InputDestination.NAVIGATE_URL
        }

        // If InputRouter / Bidirectional Bridge is disabled, treat non-URL text as normal search
        if (!inputRouterEnabled) {
            return InputDestination.UNIVERSAL_SEARCH
        }

        // 2. If Bridge applies to all websites or the current active page is a trusted origin
        val isGVONEActive = PageContextDetector.isTrustedGVONEOrigin(currentTabUrl)
        if (bridgeApplyToAllWebsites || isGVONEActive) {
            return InputDestination.DELIVER_TO_WEB_APP
        }

        // 3. Otherwise treat as universal search
        return InputDestination.UNIVERSAL_SEARCH
    }

    /**
     * Normalize URL for browser navigation
     */
    fun formatNavigationUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("gvone://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("chrome:", ignoreCase = true)
        ) {
            return trimmed
        }
        return "https://$trimmed"
    }
}

/**
 * GVONE Web App Communication Bridge:
 * Handles secure bidirectional messaging between the GVONE Browser and the GVONE Web App.
 */
class GVONEWebAppBridge(
    private val onStateChanged: (WebAppConnectionState) -> Unit = {},
    private val onInputDelivered: (text: String, success: Boolean) -> Unit = { _, _ -> }
) {
    companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "GVONEBrowserBridge"
        private const val TAG = "GVONEBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(WebAppConnectionState.IDLE)
    val connectionState: StateFlow<WebAppConnectionState> = _connectionState.asStateFlow()

    private val _lastDeliveredText = MutableStateFlow<String?>(null)
    val lastDeliveredText: StateFlow<String?> = _lastDeliveredText.asStateFlow()

    private var lastSubmissionTimestamp = 0L

    /**
     * JavaScript Interface method called by the Web App to report status.
     */
    @JavascriptInterface
    fun postMessageToBrowser(messageJson: String) {
        try {
            val json = JSONObject(messageJson)
            val type = json.optString("type")
            val status = json.optString("status")

            mainHandler.post {
                when (type) {
                    "gvone_app_state", "status_update" -> {
                        val state = when (status.lowercase(Locale.ROOT)) {
                            "ready" -> WebAppConnectionState.READY
                            "processing" -> WebAppConnectionState.PROCESSING
                            "completed" -> WebAppConnectionState.COMPLETED
                            "unavailable" -> WebAppConnectionState.UNAVAILABLE
                            else -> WebAppConnectionState.READY
                        }
                        updateState(state)
                    }
                    "input_acknowledged" -> {
                        val text = json.optString("text")
                        val success = json.optBoolean("success", true)
                        onInputDelivered(text, success)
                        updateState(WebAppConnectionState.PROCESSING)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message from web app", e)
        }
    }

    @JavascriptInterface
    fun notifyReady(appId: String?, version: String?) {
        mainHandler.post {
            Log.d(TAG, "GVONE Web App ready: appId=$appId, version=$version")
            updateState(WebAppConnectionState.READY)
        }
    }

    @JavascriptInterface
    fun notifyState(status: String?) {
        mainHandler.post {
            val state = when (status?.lowercase(Locale.ROOT)) {
                "ready" -> WebAppConnectionState.READY
                "processing" -> WebAppConnectionState.PROCESSING
                "completed" -> WebAppConnectionState.COMPLETED
                "unavailable" -> WebAppConnectionState.UNAVAILABLE
                else -> WebAppConnectionState.READY
            }
            updateState(state)
        }
    }

    private fun updateState(newState: WebAppConnectionState) {
        _connectionState.value = newState
        onStateChanged(newState)
    }

    /**
     * Injects the standard GVONE bridge runtime into the page.
     * Sets up window.postMessage listener, custom event listener, and DOM helper.
     */
    fun injectBridgeRuntime(
        webView: WebView,
        currentUrl: String?,
        enabled: Boolean = true,
        applyToAll: Boolean = true
    ) {
        if (!enabled) return
        if (!applyToAll && !PageContextDetector.isTrustedGVONEOrigin(currentUrl)) {
            return
        }

        val injectionJs = """
            (function() {
                if (window.__GVONE_BRIDGE_INSTALLED__) return;
                window.__GVONE_BRIDGE_INSTALLED__ = true;

                // Create standard GVONE global object
                window.GVONE = window.GVONE || {};
                window.GVONE.source = "gvone_browser";
                window.GVONE.version = "1.0.0";
                
                // Helper to notify browser of state
                window.GVONE.notifyState = function(state) {
                    if (window.GVONEBrowserBridge && window.GVONEBrowserBridge.notifyState) {
                        window.GVONEBrowserBridge.notifyState(state);
                    }
                };

                // Master handler for input delivery from browser address bar
                window.__GVONE_HANDLE_BROWSER_INPUT__ = function(eventData) {
                    try {
                        var text = eventData.text || '';
                        var action = eventData.action || 'submit';
                        
                        console.log('[GVONE Bridge] Received input from browser address bar:', text);

                        // 1. Dispatch custom DOM event for custom Web App listener
                        var customEvt = new CustomEvent('gvone:browser_input', {
                            detail: eventData,
                            bubbles: true,
                            cancelable: true
                        });
                        window.dispatchEvent(customEvt);
                        document.dispatchEvent(customEvt);

                        // 2. Dispatch window.postMessage for standard web app listeners
                        window.postMessage({
                            type: 'INPUT_FROM_BROWSER',
                            payload: eventData
                        }, '*');

                        // 3. Check for direct callback if exposed by web app
                        if (typeof window.onGVONEBrowserInput === 'function') {
                            window.onGVONEBrowserInput(eventData);
                            if (window.GVONEBrowserBridge) {
                                window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                                    type: 'input_acknowledged',
                                    text: text,
                                    success: true
                                }));
                            }
                            return true;
                        }

                        // 4. Universal Fallback: Inject into chat/search input and trigger submit
                        var targetInput = document.querySelector('textarea:not([disabled]), input[type="text"]:not([disabled]), input[type="search"]:not([disabled]), [contenteditable="true"]');
                        if (targetInput) {
                            targetInput.focus();
                            if (targetInput.isContentEditable) {
                                targetInput.innerText = text;
                            } else {
                                targetInput.value = text;
                            }

                            // Trigger realistic change events
                            targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                            targetInput.dispatchEvent(new Event('change', { bubbles: true }));

                            // Look for Send / Submit button or dispatch Enter key
                            setTimeout(function() {
                                var sendButton = document.querySelector('button[type="submit"], button[aria-label*="send" i], button[title*="send" i], button[aria-label*="search" i], button.send-button, button.submit-btn');
                                if (sendButton && !sendButton.disabled) {
                                    sendButton.click();
                                } else {
                                    var enterEvent = new KeyboardEvent('keydown', {
                                        key: 'Enter',
                                        code: 'Enter',
                                        keyCode: 13,
                                        which: 13,
                                        bubbles: true,
                                        cancelable: true
                                    });
                                    targetInput.dispatchEvent(enterEvent);
                                }
                            }, 50);

                            if (window.GVONEBrowserBridge) {
                                window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                                    type: 'input_acknowledged',
                                    text: text,
                                    success: true
                                }));
                            }
                            return true;
                        }

                        return false;
                    } catch (err) {
                        console.error('[GVONE Bridge] Error handling browser input:', err);
                        return false;
                    }
                };

                // Notify native browser that page is ready
                if (window.GVONEBrowserBridge && window.GVONEBrowserBridge.notifyReady) {
                    window.GVONEBrowserBridge.notifyReady('gvone_web_app', '1.0');
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(injectionJs, null)
    }

    /**
     * Dispatches user address bar text to the web app without reloading or navigating away.
     * Strictly prevents duplicate submissions.
     */
    fun deliverAddressBarInput(
        webView: WebView?,
        text: String,
        action: String = "submit"
    ): Boolean {
        if (webView == null) return false
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false

        // Prevent duplicate rapid submissions (debounce 500ms for exact same text)
        val now = System.currentTimeMillis()
        if (now - lastSubmissionTimestamp < 500 && _lastDeliveredText.value == trimmed) {
            Log.d(TAG, "Skipping duplicate submission: $trimmed")
            return true
        }

        lastSubmissionTimestamp = now
        _lastDeliveredText.value = trimmed

        val message = BrowserToWebAppMessage(
            type = "address_bar_input",
            text = trimmed,
            action = action,
            source = "gvone_browser",
            timestamp = now
        )

        val jsonString = message.toJson()
        val escapedJson = JSONObject.quote(jsonString)

        val dispatchScript = """
            (function() {
                try {
                    var data = JSON.parse($escapedJson);
                    if (window.__GVONE_HANDLE_BROWSER_INPUT__) {
                        return window.__GVONE_HANDLE_BROWSER_INPUT__(data);
                    } else {
                        // Resilient fallback if runtime hasn't finished initial evaluation
                        window.postMessage({
                            type: 'INPUT_FROM_BROWSER',
                            payload: data
                        }, '*');
                        
                        var targetInput = document.querySelector('textarea, input[type="text"], input[type="search"]');
                        if (targetInput) {
                            targetInput.value = data.text;
                            targetInput.dispatchEvent(new Event('input', { bubbles: true }));
                            targetInput.dispatchEvent(new Event('change', { bubbles: true }));
                            var sendButton = document.querySelector('button[type="submit"], button[aria-label*="send" i]');
                            if (sendButton) sendButton.click();
                            return true;
                        }
                        return false;
                    }
                } catch (e) {
                    console.error('[GVONE Bridge] Error dispatching input:', e);
                    return false;
                }
            })();
        """.trimIndent()

        mainHandler.post {
            updateState(WebAppConnectionState.PROCESSING)
            webView.evaluateJavascript(dispatchScript) { result ->
                val delivered = result == "true"
                Log.d(TAG, "Input delivery result: $delivered for text: $trimmed")
                onInputDelivered(trimmed, delivered)
            }
        }

        return true
    }
}
