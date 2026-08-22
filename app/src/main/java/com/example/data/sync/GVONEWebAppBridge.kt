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

    fun isYouTubeOrigin(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.trim().lowercase(Locale.ROOT)
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun isYouTubeHomepage(url: String?): Boolean {
        if (!isYouTubeOrigin(url)) return false
        return try {
            val uri = URI(url!!.trim())
            val path = uri.path ?: ""
            path.isEmpty() || path == "/" || path == "/feed/explore" || path == "/home"
        } catch (_: Exception) {
            val lower = url!!.trim().lowercase(Locale.ROOT)
            lower.endsWith("youtube.com") || lower.endsWith("youtube.com/") || lower.endsWith("m.youtube.com") || lower.endsWith("m.youtube.com/")
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

        // 2. Deliver directly to Web App if current active page is a trusted GVONE origin, YouTube, or if apply to all websites is enabled
        val isGVONEActive = PageContextDetector.isTrustedGVONEOrigin(currentTabUrl)
        val isYouTubeActive = PageContextDetector.isYouTubeOrigin(currentTabUrl)
        if (isGVONEActive || isYouTubeActive || bridgeApplyToAllWebsites) {
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
        val isTrustedOrigin = PageContextDetector.isTrustedGVONEOrigin(currentUrl)
        val isYouTubeOrigin = PageContextDetector.isYouTubeOrigin(currentUrl)
        if (!applyToAll && !isTrustedOrigin && !isYouTubeOrigin) {
            return
        }

        val injectionJs = """
            (function() {
                if (window.__GVONE_BRIDGE_INSTALLED__) return;
                window.__GVONE_BRIDGE_INSTALLED__ = true;
                var isTrustedOrigin = $isTrustedOrigin;
                var isYouTubeOrigin = $isYouTubeOrigin || (window.location && (window.location.hostname.indexOf('youtube.com') !== -1 || window.location.hostname.indexOf('youtu.be') !== -1));
                var applyToAll = $applyToAll;

                // Create standard GVONE global object
                window.GVONE = window.GVONE || {};
                window.GVONE.source = "gvone_browser";
                window.GVONE.version = "1.1.0";
                
                // Helper to notify browser of state
                window.GVONE.notifyState = function(state) {
                    if (window.GVONEBrowserBridge && window.GVONEBrowserBridge.notifyState) {
                        window.GVONEBrowserBridge.notifyState(state);
                    }
                };

                // Submission state and deduplication tracking
                window.__GVONE_SUBMIT_STATE__ = {
                    lastSubmitTime: 0,
                    lastSubmitText: '',
                    isProcessing: false
                };

                // React/Vue/Angular controlled input value helper
                function setNativeInputValue(el, value) {
                    if (!el) return;
                    try {
                        if (el.isContentEditable) {
                            el.innerText = value;
                            el.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                            return;
                        }
                        var prototype = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                        var descriptor = Object.getOwnPropertyDescriptor(prototype, 'value');
                        if (descriptor && descriptor.set) {
                            descriptor.set.call(el, value);
                        } else {
                            el.value = value;
                        }
                        el.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                    } catch (e) {
                        try {
                            el.value = value;
                            el.dispatchEvent(new Event('input', { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                        } catch (err) {}
                    }
                }

                // Dedicated YouTube Search activator
                function activateYouTubeSearch(text, action) {
                    if (!text && action !== 'submit') return false;

                    // 1. Try to find already open/rendered search input
                    var ytInput = document.querySelector(
                        'input[name="search_query"], ' +
                        'input.searchbox-input, ' +
                        'input#search, ' +
                        'ytm-searchbox input, ' +
                        'ytd-searchbox input, ' +
                        'form.searchbox input, ' +
                        'input[type="search"]'
                    );

                    if (ytInput) {
                        ytInput.focus();
                        if (text) setNativeInputValue(ytInput, text);
                        if (action === 'submit') {
                            setTimeout(function() {
                                var form = ytInput.form || ytInput.closest('form');
                                var searchBtn = (form || document).querySelector(
                                    'button#search-icon-legacy, ' +
                                    'button.searchbox-submit, ' +
                                    'button[aria-label="Search" i], ' +
                                    'button[aria-label="Search YouTube" i], ' +
                                    'button[type="submit"]'
                                );
                                if (searchBtn && !searchBtn.disabled) {
                                    searchBtn.click();
                                } else if (form) {
                                    if (typeof form.requestSubmit === 'function') form.requestSubmit();
                                    else form.submit();
                                } else {
                                    try {
                                        var evt = new KeyboardEvent('keydown', {
                                            key: 'Enter',
                                            code: 'Enter',
                                            keyCode: 13,
                                            which: 13,
                                            bubbles: true,
                                            cancelable: true,
                                            composed: true
                                        });
                                        ytInput.dispatchEvent(evt);
                                    } catch (e) {}
                                }
                            }, 30);
                        }
                        return true;
                    }

                    // 2. On YouTube mobile homepage, search box is behind the topbar search icon button.
                    // Click search button to open the input field.
                    var topbarSearchBtn = document.querySelector(
                        'button[aria-label*="Search" i], ' +
                        'button.topbar-search-button, ' +
                        'button.mobile-topbar-header-search-icon, ' +
                        'ytm-searchbox button, ' +
                        'button[data-target-id="search-btn"], ' +
                        '[aria-label="Search YouTube"], ' +
                        '[aria-label="Search"]'
                    );

                    if (topbarSearchBtn) {
                        topbarSearchBtn.click();
                        setTimeout(function() {
                            var openedInput = document.querySelector(
                                'input[name="search_query"], ' +
                                'input.searchbox-input, ' +
                                'input#search, ' +
                                'ytm-searchbox input, ' +
                                'form.searchbox input, ' +
                                'input[type="search"]'
                            );
                            if (openedInput) {
                                openedInput.focus();
                                if (text) setNativeInputValue(openedInput, text);
                                if (action === 'submit') {
                                    setTimeout(function() {
                                        var form = openedInput.form || openedInput.closest('form');
                                        var submitBtn = (form || document).querySelector(
                                            'button#search-icon-legacy, ' +
                                            'button.searchbox-submit, ' +
                                            'button[aria-label*="Search" i], ' +
                                            'button[type="submit"]'
                                        );
                                        if (submitBtn) {
                                            submitBtn.click();
                                        } else if (form) {
                                            if (typeof form.requestSubmit === 'function') form.requestSubmit();
                                            else form.submit();
                                        } else {
                                            try {
                                                var evt = new KeyboardEvent('keydown', {
                                                    key: 'Enter',
                                                    code: 'Enter',
                                                    keyCode: 13,
                                                    which: 13,
                                                    bubbles: true,
                                                    cancelable: true,
                                                    composed: true
                                                });
                                                openedInput.dispatchEvent(evt);
                                            } catch (e) {}
                                        }
                                    }, 30);
                                }
                            } else if (action === 'submit' && text) {
                                window.location.href = (window.location.origin || 'https://m.youtube.com') + '/results?search_query=' + encodeURIComponent(text);
                            }
                        }, 40);
                        return true;
                    }

                    // 3. Robust URL fallback on YouTube
                    if (action === 'submit' && text) {
                        window.location.href = (window.location.origin || 'https://m.youtube.com') + '/results?search_query=' + encodeURIComponent(text);
                        return true;
                    }

                    return false;
                }

                // Helper to find the primary search/chat input on the page
                function findPrimarySearchChatInput() {
                    var focused = document.activeElement;
                    if (focused && (focused.tagName === 'INPUT' || focused.tagName === 'TEXTAREA' || focused.isContentEditable)) {
                        return focused;
                    }
                    return document.querySelector(
                        'input[type="search"]:not([disabled]), ' +
                        'textarea:not([disabled]), ' +
                        'input[type="text"]:not([disabled]), ' +
                        'input:not([type]):not([disabled]), ' +
                        '[contenteditable="true"]'
                    );
                }

                // Helper to find the matching send/submit/search button
                function findSubmitButton(inputEl) {
                    if (inputEl && inputEl.form) {
                        var formBtn = inputEl.form.querySelector('button[type="submit"], input[type="submit"], button:not([disabled])');
                        if (formBtn) return formBtn;
                    }
                    if (inputEl) {
                        var container = inputEl.closest('form, [role="search"], [role="region"], .input-container, .search-container, .chat-input-wrapper, fieldset, div');
                        if (container) {
                            var nearbyBtn = container.querySelector(
                                'button[aria-label*="send" i], button[aria-label*="search" i], button[aria-label*="submit" i], button[aria-label*="ask" i], button[aria-label*="prompt" i], ' +
                                'button[data-testid*="send" i], button[data-testid*="submit" i], button[data-testid*="search" i], ' +
                                'button[title*="send" i], button[title*="search" i], button[title*="submit" i], ' +
                                'button.send-btn, button.send-button, button.submit-btn, button.search-btn, ' +
                                'button[type="submit"], [role="button"][aria-label*="send" i], [role="button"][aria-label*="search" i]'
                            );
                            if (nearbyBtn) return nearbyBtn;
                        }
                    }
                    return document.querySelector(
                        'button[data-testid*="send" i], button[data-testid*="submit" i], button[data-testid*="search" i], ' +
                        'button[aria-label*="send" i], button[aria-label*="search" i], button[aria-label*="submit" i], button[aria-label*="ask" i], ' +
                        'button[title*="send" i], button[title*="search" i], button[title*="submit" i], ' +
                        'button.send-button, button.submit-btn, button.search-button, button[type="submit"], input[type="submit"], ' +
                        '[role="button"][aria-label*="send" i], [role="button"][aria-label*="search" i]'
                    );
                }

                // Determines if the context is primarily chat-oriented vs search-oriented
                function getEnterKeyHintType(inputEl) {
                    var placeholder = (inputEl.placeholder || inputEl.getAttribute('aria-label') || '').toLowerCase();
                    if (placeholder.includes('chat') || placeholder.includes('message') || placeholder.includes('ask') || placeholder.includes('prompt')) {
                        return 'send';
                    }
                    return 'search';
                }

                // Configures an input element with the proper platform IME search/send actions for trusted GVONE Web Apps ONLY
                function configureSearchChatInput(el) {
                    if (!el || el.__gvone_configured__) return;
                    el.__gvone_configured__ = true;

                    // Do not alter non-text inputs (password, email, number, etc.)
                    if (el.tagName === 'INPUT' && el.type && el.type !== 'text' && el.type !== 'search') {
                        return;
                    }

                    var isTextArea = el.tagName === 'TEXTAREA';
                    var hint = getEnterKeyHintType(el);
                    
                    // 1. Explicitly configure enterkeyhint on single-line inputs
                    if (!isTextArea) {
                        el.setAttribute('enterkeyhint', hint);
                    }

                    // 2. Handle Enter key for single-line search/chat inputs without Shift
                    if (!isTextArea) {
                        el.addEventListener('keydown', function(e) {
                            if (e.key === 'Enter' && !e.shiftKey) {
                                var text = el.isContentEditable ? el.innerText : el.value;
                                if (text && text.trim().length > 0) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    window.__GVONE_EXECUTE_SUBMIT__(el, 'keyboard_' + hint);
                                }
                            }
                        }, true);
                    }
                }

                // Global unified submit handler with single authoritative execution path
                window.__GVONE_EXECUTE_SUBMIT__ = function(targetInput, triggerSource) {
                    try {
                        var inputEl = targetInput || findPrimarySearchChatInput();
                        var currentText = '';
                        if (inputEl) {
                            currentText = (inputEl.isContentEditable ? inputEl.innerText : inputEl.value) || '';
                        }
                        currentText = currentText.trim();

                        var now = Date.now();
                        // Deduplication: prevent duplicate submission within 300ms for identical text
                        if (triggerSource !== 'address_bar_bridge' &&
                            now - window.__GVONE_SUBMIT_STATE__.lastSubmitTime < 300 && 
                            window.__GVONE_SUBMIT_STATE__.lastSubmitText === currentText &&
                            currentText.length > 0) {
                            return true;
                        }

                        window.__GVONE_SUBMIT_STATE__.lastSubmitTime = now;
                        window.__GVONE_SUBMIT_STATE__.lastSubmitText = currentText;
                        window.__GVONE_SUBMIT_STATE__.isProcessing = true;

                        // 1. Dispatch unified CustomEvents on window and document
                        var submitEvt = new CustomEvent('gvone:submit', {
                            detail: { text: currentText, source: triggerSource },
                            bubbles: true,
                            cancelable: true
                        });
                        window.dispatchEvent(submitEvt);
                        document.dispatchEvent(submitEvt);

                        // 2. If web app exposes a custom submission handler, invoke it and finish
                        if (typeof window.onGVONEBrowserSubmit === 'function') {
                            window.onGVONEBrowserSubmit({ text: currentText, source: triggerSource });
                            if (window.GVONEBrowserBridge) {
                                window.GVONEBrowserBridge.notifyState('processing');
                            }
                            return true;
                        }

                        var submitted = false;

                        // 3. Single-path submission: Try Submit/Send button first
                        var sendBtn = findSubmitButton(inputEl);
                        if (sendBtn && !sendBtn.disabled) {
                            try {
                                sendBtn.click();
                                submitted = true;
                            } catch (e) {}
                        } else if (inputEl && inputEl.form) {
                            // 4. Fallback to form submission only if no button was clicked
                            try {
                                if (typeof inputEl.form.requestSubmit === 'function') {
                                    inputEl.form.requestSubmit();
                                    submitted = true;
                                } else {
                                    inputEl.form.submit();
                                    submitted = true;
                                }
                            } catch (e) {}
                        } else if (inputEl) {
                            // 5. Fallback to single Enter keydown event only if neither button nor form exists
                            try {
                                var evt = new KeyboardEvent('keydown', {
                                    key: 'Enter',
                                    code: 'Enter',
                                    keyCode: 13,
                                    which: 13,
                                    bubbles: true,
                                    cancelable: true,
                                    composed: true
                                });
                                inputEl.dispatchEvent(evt);
                                submitted = true;
                            } catch (e) {}
                        }

                        if (window.GVONEBrowserBridge) {
                            window.GVONEBrowserBridge.notifyState('processing');
                        }

                        return submitted;
                    } catch (err) {
                        console.error('[GVONE Bridge] Error during submission execution:', err);
                        return false;
                    }
                };

                // Master handler for input delivery from browser address bar
                window.__GVONE_HANDLE_BROWSER_INPUT__ = function(eventData) {
                    try {
                        var text = eventData.text || '';
                        var action = eventData.action || 'submit';
                        
                        console.log('[GVONE Bridge] Received input from browser address bar:', text, 'action:', action);

                        // 1. YouTube specific handler
                        if (isYouTubeOrigin || (window.location && (window.location.hostname.indexOf('youtube.com') !== -1 || window.location.hostname.indexOf('youtu.be') !== -1))) {
                            var ytHandled = activateYouTubeSearch(text, action);
                            if (ytHandled) {
                                if (window.GVONEBrowserBridge) {
                                    window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                                        type: 'input_acknowledged',
                                        text: text,
                                        success: true
                                    }));
                                }
                                return true;
                            }
                        }

                        // 2. Dispatch custom DOM event for custom Web App listener
                        var customEvt = new CustomEvent('gvone:browser_input', {
                            detail: eventData,
                            bubbles: true,
                            cancelable: true
                        });
                        window.dispatchEvent(customEvt);
                        document.dispatchEvent(customEvt);

                        // 3. Dispatch window.postMessage for standard web app listeners
                        window.postMessage({
                            type: 'INPUT_FROM_BROWSER',
                            payload: eventData
                        }, '*');

                        // 4. Check for direct callback if exposed by web app
                        if (typeof window.onGVONEBrowserInput === 'function') {
                            window.onGVONEBrowserInput(eventData);
                            if (window.GVONEBrowserBridge) {
                                window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                                    type: 'input_acknowledged',
                                    text: text,
                                    success: true
                                }));
                            }
                            if (action === 'submit') {
                                window.__GVONE_EXECUTE_SUBMIT__(null, 'address_bar_bridge');
                            }
                            return true;
                        }

                        // 5. Universal Fallback: Inject into chat/search input and trigger submit
                        var targetInput = findPrimarySearchChatInput();
                        if (targetInput) {
                            configureSearchChatInput(targetInput);
                            targetInput.focus();
                            setNativeInputValue(targetInput, text);

                            if (action === 'submit') {
                                setTimeout(function() {
                                    window.__GVONE_EXECUTE_SUBMIT__(targetInput, 'address_bar_bridge');
                                }, 30);
                            }

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

                // On YouTube homepage, typing immediately activates YouTube search without requiring tapping the Search icon
                if (isYouTubeOrigin) {
                    document.addEventListener('keydown', function(e) {
                        if (e.ctrlKey || e.altKey || e.metaKey || e.isComposing) return;
                        var active = document.activeElement;
                        if (active && (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' || active.isContentEditable)) {
                            return;
                        }

                        // Avoid interrupting video player shortcuts on /watch or /shorts pages
                        var path = (window.location.pathname || '').toLowerCase();
                        if (path.indexOf('/watch') !== -1 || path.indexOf('/shorts') !== -1) {
                            return;
                        }

                        // Check for single printable character (letters, numbers, etc.)
                        if (e.key && e.key.length === 1 && !e.repeat) {
                            var searchInput = document.querySelector('input[name="search_query"], input.searchbox-input, input#search, input[type="search"]');
                            if (!searchInput) {
                                var searchBtn = document.querySelector(
                                    'button[aria-label*="Search" i], button.topbar-search-button, button.mobile-topbar-header-search-icon, ytm-searchbox button, [aria-label="Search YouTube"], [aria-label="Search"]'
                                );
                                if (searchBtn) {
                                    searchBtn.click();
                                }
                            }

                            setTimeout(function() {
                                var target = document.querySelector('input[name="search_query"], input.searchbox-input, input#search, input[type="search"]');
                                if (target) {
                                    target.focus();
                                    var val = target.value || '';
                                    setNativeInputValue(target, val + e.key);
                                }
                            }, 30);
                        }
                    }, false);
                }

                // Scan & observe DOM to auto-configure search/chat inputs on trusted GVONE Web App origins ONLY
                if (isTrustedOrigin) {
                    function scanAndConfigureInputs() {
                        var inputs = document.querySelectorAll('input, textarea, [contenteditable="true"]');
                        for (var i = 0; i < inputs.length; i++) {
                            configureSearchChatInput(inputs[i]);
                        }
                    }

                    // Initial scan
                    scanAndConfigureInputs();

                    // Listen for focusin so newly rendered inputs get configured immediately on tap
                    document.addEventListener('focusin', function(e) {
                        if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable)) {
                            configureSearchChatInput(e.target);
                        }
                    }, true);

                    // MutationObserver for dynamic SPAs on trusted GVONE origins
                    if (window.MutationObserver) {
                        var observer = new MutationObserver(function(mutations) {
                            scanAndConfigureInputs();
                        });
                        observer.observe(document.documentElement || document.body, {
                            childList: true,
                            subtree: true
                        });
                    }
                }

                // Notify native browser that page is ready
                if (window.GVONEBrowserBridge && window.GVONEBrowserBridge.notifyReady) {
                    window.GVONEBrowserBridge.notifyReady('gvone_web_app', '1.1');
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

        val now = System.currentTimeMillis()
        if (action != "submit" && now - lastSubmissionTimestamp < 300 && _lastDeliveredText.value == trimmed) {
            Log.d(TAG, "Skipping duplicate input sync: $trimmed")
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

                        // Check for YouTube fallback
                        if (window.location && (window.location.hostname.indexOf('youtube.com') !== -1 || window.location.hostname.indexOf('youtu.be') !== -1)) {
                            var ytInput = document.querySelector('input[name="search_query"], input.searchbox-input, input#search, input[type="search"]');
                            if (!ytInput) {
                                var btn = document.querySelector('button[aria-label*="Search" i], button.topbar-search-button, button.mobile-topbar-header-search-icon, ytm-searchbox button, [aria-label="Search YouTube"]');
                                if (btn) btn.click();
                            }
                            if (data.action === 'submit' && data.text) {
                                setTimeout(function() {
                                    var opened = document.querySelector('input[name="search_query"], input.searchbox-input, input#search, input[type="search"]');
                                    if (opened) {
                                        opened.focus();
                                        opened.value = data.text;
                                        var form = opened.form || opened.closest('form');
                                        if (form && typeof form.requestSubmit === 'function') form.requestSubmit();
                                        else if (form) form.submit();
                                        else window.location.href = (window.location.origin || 'https://m.youtube.com') + '/results?search_query=' + encodeURIComponent(data.text);
                                    } else {
                                        window.location.href = (window.location.origin || 'https://m.youtube.com') + '/results?search_query=' + encodeURIComponent(data.text);
                                    }
                                }, 40);
                                return true;
                            }
                        }
                        
                        var targetInput = document.querySelector('textarea:not([disabled]), input[type="search"]:not([disabled]), input[type="text"]:not([disabled]), input:not([type]):not([disabled]), [contenteditable="true"]');
                        if (targetInput) {
                            targetInput.focus();
                            try {
                                if (targetInput.isContentEditable) {
                                    targetInput.innerText = data.text;
                                } else {
                                    var proto = targetInput.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                                    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
                                    if (desc && desc.set) desc.set.call(targetInput, data.text);
                                    else targetInput.value = data.text;
                                }
                                targetInput.dispatchEvent(new Event('input', { bubbles: true, composed: true }));
                                targetInput.dispatchEvent(new Event('change', { bubbles: true, composed: true }));
                            } catch (e) {
                                targetInput.value = data.text;
                            }

                            if (data.action === 'submit') {
                                setTimeout(function() {
                                    var form = targetInput.form || targetInput.closest('form');
                                    var btn = (form || document).querySelector('button[type="submit"], button[aria-label*="send" i], button[aria-label*="search" i], button[aria-label*="submit" i], button[data-testid*="send" i]');
                                    if (btn) {
                                        btn.click();
                                    } else if (form) {
                                        if (typeof form.requestSubmit === 'function') form.requestSubmit();
                                        else form.submit();
                                    }
                                    ['keydown', 'keypress', 'keyup'].forEach(function(evtName) {
                                        targetInput.dispatchEvent(new KeyboardEvent(evtName, {
                                            key: 'Enter',
                                            code: 'Enter',
                                            keyCode: 13,
                                            which: 13,
                                            charCode: 13,
                                            bubbles: true,
                                            cancelable: true,
                                            composed: true
                                        }));
                                    });
                                }, 30);
                            }
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
