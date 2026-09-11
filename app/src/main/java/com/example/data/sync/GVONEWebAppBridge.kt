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
import com.example.ui.contextmenu.ContextMenuTargetType
import com.example.ui.contextmenu.LinkContextMenuData

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

    fun isYouTubeShorts(url: String?): Boolean {
        if (!isYouTubeOrigin(url)) return false
        return try {
            val uri = URI(url!!.trim())
            val path = uri.path ?: ""
            path.startsWith("/shorts") || path.contains("/shorts/")
        } catch (_: Exception) {
            val lower = url!!.trim().lowercase(Locale.ROOT)
            lower.contains("/shorts")
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

        // Check if internal browser page (e.g. gvone://newtab, about:blank, etc.)
        val isInternalPage = currentTabUrl.isNullOrBlank() ||
                currentTabUrl == "gvone://newtab" ||
                currentTabUrl == "about:blank" ||
                currentTabUrl.startsWith("gvone://") ||
                currentTabUrl.startsWith("gvone-file://")
        if (isInternalPage) {
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

data class ShortsAudioStatus(
    val isShorts: Boolean,
    val isMuted: Boolean,
    val mode: String
)

data class MediaPlayerStatus(
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val title: String = "",
    val isShorts: Boolean = false
)

/**
 * GVONE Web App Communication Bridge:
 * Handles secure bidirectional messaging between the GVONE Browser and the GVONE Web App.
 */
class GVONEWebAppBridge(
    private val onStateChanged: (WebAppConnectionState) -> Unit = {},
    private val onInputDelivered: (text: String, success: Boolean) -> Unit = { _, _ -> },
    private val onShortsAudioStateChanged: (isShorts: Boolean, isMuted: Boolean, mode: String) -> Unit = { _, _, _ -> },
    private val onMediaPlayerStateChanged: (isPlaying: Boolean, isMuted: Boolean, title: String, isShorts: Boolean) -> Unit = { _, _, _, _ -> }
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

    private val _shortsAudioState = MutableStateFlow<ShortsAudioStatus?>(null)
    val shortsAudioState: StateFlow<ShortsAudioStatus?> = _shortsAudioState.asStateFlow()

    private val _mediaPlayerState = MutableStateFlow<MediaPlayerStatus?>(null)
    val mediaPlayerState: StateFlow<MediaPlayerStatus?> = _mediaPlayerState.asStateFlow()

    var onContextMenuListener: ((LinkContextMenuData) -> Unit)? = null

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
                    "shorts_audio_status" -> {
                        val isShorts = json.optBoolean("isShorts", false)
                        val isMuted = json.optBoolean("isMuted", false)
                        val mode = json.optString("mode", "ALWAYS_UNMUTED")
                        val newStatus = ShortsAudioStatus(isShorts, isMuted, mode)
                        _shortsAudioState.value = newStatus
                        onShortsAudioStateChanged(isShorts, isMuted, mode)
                    }
                    "media_player_status" -> {
                        val isPlaying = json.optBoolean("isPlaying", false)
                        val isMuted = json.optBoolean("isMuted", false)
                        val title = json.optString("title", "")
                        val isShorts = json.optBoolean("isShorts", false)
                        val newStatus = MediaPlayerStatus(isPlaying, isMuted, title, isShorts)
                        _mediaPlayerState.value = newStatus
                        onMediaPlayerStateChanged(isPlaying, isMuted, title, isShorts)
                    }
                    "context_menu_triggered" -> {
                        onContextMenuTriggered(messageJson)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse message from web app", e)
        }
    }

    @JavascriptInterface
    fun onContextMenuTriggered(messageJson: String) {
        try {
            val json = JSONObject(messageJson)
            val url = json.optString("url")
            if (url.isBlank() || url.startsWith("javascript:")) return

            val targetTypeStr = json.optString("targetType", "LINK")
            val targetType = try {
                ContextMenuTargetType.valueOf(targetTypeStr.uppercase(Locale.ROOT))
            } catch (_: Exception) {
                ContextMenuTargetType.LINK
            }

            val data = LinkContextMenuData(
                url = url,
                title = json.optString("title"),
                text = json.optString("text"),
                srcUrl = json.optString("srcUrl").ifBlank { null },
                targetType = targetType,
                mimeType = json.optString("mimeType").ifBlank { null }
            )

            mainHandler.post {
                onContextMenuListener?.invoke(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse context menu message", e)
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
     * Explicitly update the bridge connection state from native browser lifecycle events.
     */
    fun setConnectionState(newState: WebAppConnectionState) {
        mainHandler.post {
            updateState(newState)
        }
    }

    /**
     * Injects the standard GVONE bridge runtime into the page.
     * Sets up window.postMessage listener, custom event listener, and DOM helper.
     */
    fun injectBridgeRuntime(
        webView: WebView,
        currentUrl: String?,
        enabled: Boolean = true,
        applyToAll: Boolean = true,
        shortsAudioMode: com.example.data.model.ShortsAudioMode = com.example.data.model.ShortsAudioMode.ALWAYS_UNMUTED
    ) {
        val isYouTubeOrigin = PageContextDetector.isYouTubeOrigin(currentUrl)

        // Always ensure YouTube Shorts Smart Audio Engine is active on YouTube origins
        if (isYouTubeOrigin) {
            injectYouTubeShortsAudioScript(webView, shortsAudioMode)
        }

        if (!enabled) return
        val isTrustedOrigin = PageContextDetector.isTrustedGVONEOrigin(currentUrl)
        if (!applyToAll && !isTrustedOrigin && !isYouTubeOrigin) {
            return
        }

        val injectionJs = """
            (function() {
                function sendReadySignal() {
                    try {
                        if (window.GVONEBrowserBridge && typeof window.GVONEBrowserBridge.notifyReady === 'function') {
                            var host = (window.location && window.location.hostname) ? window.location.hostname : 'gvone_web_app';
                            window.GVONEBrowserBridge.notifyReady(host, '1.1');
                            return true;
                        }
                    } catch(e) {}
                    return false;
                }

                if (window.__GVONE_BRIDGE_INSTALLED__) {
                    sendReadySignal();
                    return;
                }
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

                // Expose direct bidirectional sendToBrowser / postMessage API
                window.GVONE.sendToBrowser = function(payload) {
                    if (window.GVONEBrowserBridge && typeof window.GVONEBrowserBridge.postMessageToBrowser === 'function') {
                        var str = typeof payload === 'string' ? payload : JSON.stringify(payload);
                        window.GVONEBrowserBridge.postMessageToBrowser(str);
                    }
                };
                window.GVONE.postMessage = window.GVONE.sendToBrowser;

                // Forward standard Web App window.postMessage events to native Android bridge
                window.addEventListener('message', function(event) {
                    if (!event.data) return;
                    try {
                        var data = event.data;
                        if (typeof data === 'string') {
                            try { data = JSON.parse(data); } catch(e) {}
                        }
                        if (typeof data === 'object' && data !== null) {
                            if (data.source === 'gvone_browser' || data.type === 'INPUT_FROM_BROWSER') {
                                return; // Avoid echoing browser's own messages
                            }
                            if (window.GVONEBrowserBridge && typeof window.GVONEBrowserBridge.postMessageToBrowser === 'function') {
                                window.GVONEBrowserBridge.postMessageToBrowser(typeof data === 'string' ? data : JSON.stringify(data));
                            }
                        }
                    } catch(err) {
                        console.error('[GVONE Bridge] Error forwarding postMessage:', err);
                    }
                });

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

                // Resilient handshake with native bridge
                if (!sendReadySignal()) {
                    var attempts = 0;
                    var retryInterval = setInterval(function() {
                        attempts++;
                        if (sendReadySignal() || attempts >= 20) {
                            clearInterval(retryInterval);
                        }
                    }, 150);
                    if (document.readyState === 'loading') {
                        document.addEventListener('DOMContentLoaded', sendReadySignal, { once: true });
                        window.addEventListener('load', sendReadySignal, { once: true });
                    }
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

    /**
     * Injects the dedicated YouTube Shorts Smart Audio Engine.
     * Ensures that video sound behavior on YouTube Shorts (scrolling, swiping, SPA navigation)
     * strictly adheres to user intent (ALWAYS_UNMUTED, ALWAYS_MUTED, or REMEMBER_STATE).
     */
    fun injectYouTubeShortsAudioScript(
        webView: WebView,
        shortsAudioMode: com.example.data.model.ShortsAudioMode
    ) {
        val script = """
            (function() {
                var targetMode = '${shortsAudioMode.name}';
                if (window.__GVONE_SET_SHORTS_AUDIO_MODE__) {
                    window.__GVONE_SET_SHORTS_AUDIO_MODE__(targetMode);
                    return;
                }
                if (window.__GVONE_SHORTS_AUDIO_INSTALLED__) return;
                window.__GVONE_SHORTS_AUDIO_INSTALLED__ = true;

                var currentMode = targetMode;
                var userManualMuted = (currentMode === 'ALWAYS_MUTED');
                var isUserInteracting = false;
                var lastInteractionTime = 0;

                function isShortsUrl() {
                    try {
                        var path = (window.location && window.location.pathname) ? window.location.pathname.toLowerCase() : '';
                        if (path.indexOf('/shorts') !== -1) return true;
                        if (document.querySelector('ytm-shorts-carousel, ytd-shorts, [is-active], [class*="shorts"]')) return true;
                    } catch(e) {}
                    return false;
                }

                function notifyShortsStatus(isMuted) {
                    try {
                        if (window.GVONEBrowserBridge && window.GVONEBrowserBridge.postMessageToBrowser) {
                            window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                                type: 'shorts_audio_status',
                                isShorts: isShortsUrl(),
                                isMuted: !!isMuted,
                                mode: currentMode
                            }));
                        }
                    } catch(e) {}
                }

                function getTargetMutedState() {
                    if (currentMode === 'ALWAYS_UNMUTED') return false;
                    if (currentMode === 'ALWAYS_MUTED') return true;
                    return !!userManualMuted;
                }

                function applyAudioToVideo(video, source) {
                    if (!video || !isShortsUrl()) return;
                    var targetMuted = getTargetMutedState();

                    try {
                        if (targetMuted) {
                            if (!video.muted) {
                                video.muted = true;
                            }
                        } else {
                            if (video.muted) {
                                video.muted = false;
                                video.volume = 1.0;
                                video.removeAttribute('muted');
                            }
                            // Also trigger native YouTube unmute button click if visible
                            var unmuteBtn = document.querySelector('button[aria-label*="Unmute" i], .ytm-shorts-player-controls-sound-button[aria-label*="Unmute" i], ytm-shorts-player-controls-overlay button[aria-label*="Unmute" i], [aria-label*="unmute" i]');
                            if (unmuteBtn) {
                                unmuteBtn.click();
                            }
                            var tapOverlay = document.querySelector('.ytm-shorts-tap-to-unmute, [aria-label*="tap to unmute" i]');
                            if (tapOverlay) {
                                tapOverlay.click();
                            }
                        }
                        notifyShortsStatus(targetMuted);
                    } catch(e) {
                        console.warn('[GVONE Shorts Audio] apply error:', e);
                    }
                }

                function checkAndApplyAllVideos(reason) {
                    if (!isShortsUrl()) return;
                    var videos = document.querySelectorAll('video');
                    for (var i = 0; i < videos.length; i++) {
                        var v = videos[i];
                        var rect = v.getBoundingClientRect();
                        var isVisible = rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.top < (window.innerHeight || document.documentElement.clientHeight);
                        if (isVisible || !v.paused || videos.length === 1) {
                            applyAudioToVideo(v, reason);
                        }
                    }
                }

                // Listen to video lifecycle events
                ['play', 'playing', 'loadeddata', 'loadstart', 'canplay'].forEach(function(evt) {
                    document.addEventListener(evt, function(e) {
                        if (e.target && e.target.tagName === 'VIDEO') {
                            applyAudioToVideo(e.target, evt);
                            setTimeout(function() { applyAudioToVideo(e.target, evt + '-delayed1'); }, 40);
                            setTimeout(function() { applyAudioToVideo(e.target, evt + '-delayed2'); }, 180);
                            setTimeout(function() { applyAudioToVideo(e.target, evt + '-delayed3'); }, 400);
                        }
                    }, true);
                });

                // Detect user manual mute/unmute action
                document.addEventListener('click', function(e) {
                    var target = e.target;
                    var btn = target ? target.closest('button') : null;
                    if (btn) {
                        var label = (btn.getAttribute('aria-label') || '') + ' ' + (btn.className || '');
                        if (/mute/i.test(label)) {
                            isUserInteracting = true;
                            lastInteractionTime = Date.now();
                            setTimeout(function() {
                                var v = document.querySelector('video');
                                if (v) {
                                    userManualMuted = v.muted;
                                    notifyShortsStatus(userManualMuted);
                                }
                                isUserInteracting = false;
                            }, 100);
                        }
                    }
                }, true);

                // Watch DOM mutations for active short slide switch
                var shortsObserver = new MutationObserver(function(mutations) {
                    if (!isShortsUrl()) return;
                    for (var i = 0; i < mutations.length; i++) {
                        var m = mutations[i];
                        if (m.type === 'childList' && m.addedNodes.length > 0) {
                            checkAndApplyAllVideos('mutation-child');
                            break;
                        } else if (m.type === 'attributes' && (m.attributeName === 'is-active' || m.attributeName === 'aria-hidden' || m.attributeName === 'src')) {
                            checkAndApplyAllVideos('mutation-attr');
                            break;
                        }
                    }
                });
                shortsObserver.observe(document.documentElement || document.body, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['is-active', 'aria-hidden', 'src', 'class']
                });

                // Watch scroll and touch end
                var scrollTimeout = null;
                function onScrollDetected() {
                    if (scrollTimeout) clearTimeout(scrollTimeout);
                    scrollTimeout = setTimeout(function() {
                        checkAndApplyAllVideos('scroll');
                    }, 80);
                }
                window.addEventListener('scroll', onScrollDetected, { passive: true });
                window.addEventListener('touchend', function() {
                    setTimeout(function() { checkAndApplyAllVideos('touchend'); }, 120);
                }, { passive: true });

                // Watch SPA navigation
                window.addEventListener('yt-navigate-finish', function() {
                    setTimeout(function() { checkAndApplyAllVideos('yt-navigate'); }, 150);
                });
                window.addEventListener('popstate', function() {
                    setTimeout(function() { checkAndApplyAllVideos('popstate'); }, 150);
                });

                window.__GVONE_SET_SHORTS_AUDIO_MODE__ = function(mode) {
                    currentMode = mode || 'ALWAYS_UNMUTED';
                    if (currentMode === 'ALWAYS_MUTED') userManualMuted = true;
                    else if (currentMode === 'ALWAYS_UNMUTED') userManualMuted = false;
                    checkAndApplyAllVideos('mode-change');
                };

                window.__GVONE_TOGGLE_SHORTS_AUDIO__ = function() {
                    var videos = document.querySelectorAll('video');
                    var currentlyMuted = true;
                    for (var i = 0; i < videos.length; i++) {
                        if (!videos[i].muted) {
                            currentlyMuted = false;
                            break;
                        }
                    }
                    var newMuted = !currentlyMuted;
                    userManualMuted = newMuted;
                    for (var j = 0; j < videos.length; j++) {
                        videos[j].muted = newMuted;
                        if (!newMuted) videos[j].volume = 1.0;
                    }
                    notifyShortsStatus(newMuted);
                    notifyMediaStatus(!newMuted);
                    return !newMuted;
                };

                function notifyMediaStatus(isPlaying) {
                    try {
                        if (window.GVONEBrowserBridge && window.GVONEBrowserBridge.postMessageToBrowser) {
                            var videos = document.querySelectorAll('video, audio');
                            var isMuted = false;
                            if (videos.length > 0) isMuted = videos[0].muted;
                            var pageTitle = document.title || '';
                            window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                                type: 'media_player_status',
                                isPlaying: !!isPlaying,
                                isMuted: isMuted,
                                title: pageTitle,
                                isShorts: isShortsUrl()
                            }));
                        }
                    } catch(e) {}
                }

                function attachMediaListeners(media) {
                    if (!media || media.__gvone_tracked__) return;
                    media.__gvone_tracked__ = true;
                    media.addEventListener('play', function() { notifyMediaStatus(true); });
                    media.addEventListener('pause', function() { notifyMediaStatus(false); });
                    media.addEventListener('volumechange', function() { notifyMediaStatus(!media.paused); });
                }

                var allMedia = document.querySelectorAll('video, audio');
                for (var m = 0; m < allMedia.length; m++) {
                    attachMediaListeners(allMedia[m]);
                }

                window.__GVONE_MEDIA_TOGGLE_PLAY__ = function() {
                    var mediaElements = document.querySelectorAll('video, audio');
                    if (!mediaElements || mediaElements.length === 0) return false;
                    var anyPlaying = false;
                    for (var i = 0; i < mediaElements.length; i++) {
                        if (!mediaElements[i].paused && !mediaElements[i].ended && mediaElements[i].currentTime > 0) {
                            anyPlaying = true;
                            break;
                        }
                    }
                    if (anyPlaying) {
                        window.__GVONE_USER_INTENTIONAL_PAUSE__ = true;
                        for (var i = 0; i < mediaElements.length; i++) {
                            try { mediaElements[i].pause(); } catch(e) {}
                        }
                        notifyMediaStatus(false);
                        return false;
                    } else {
                        window.__GVONE_USER_INTENTIONAL_PAUSE__ = false;
                        var target = mediaElements[0];
                        for (var j = 0; j < mediaElements.length; j++) {
                            if (mediaElements[j].offsetWidth > 0 || mediaElements[j].offsetHeight > 0) {
                                target = mediaElements[j];
                                break;
                            }
                        }
                        if (target) {
                            try { target.play(); } catch(e) {}
                        }
                        notifyMediaStatus(true);
                        return true;
                    }
                };

                window.__GVONE_MEDIA_PREV__ = function() {
                    try {
                        var prevShortBtn = document.querySelector('button[aria-label*="Previous short" i], button[aria-label*="Previous" i], .ytm-shorts-player-controls-prev-button');
                        if (prevShortBtn) {
                            prevShortBtn.click();
                            return;
                        }
                        var mediaElements = document.querySelectorAll('video, audio');
                        for (var i = 0; i < mediaElements.length; i++) {
                            mediaElements[i].currentTime = Math.max(0, mediaElements[i].currentTime - 10);
                        }
                    } catch(e) {}
                };

                window.__GVONE_MEDIA_NEXT__ = function() {
                    try {
                        var nextShortBtn = document.querySelector('button[aria-label*="Next short" i], button[aria-label*="Next video" i], button[aria-label*="Next" i], .ytm-shorts-player-controls-next-button');
                        if (nextShortBtn) {
                            nextShortBtn.click();
                            return;
                        }
                        var mediaElements = document.querySelectorAll('video, audio');
                        for (var i = 0; i < mediaElements.length; i++) {
                            var dur = mediaElements[i].duration || (mediaElements[i].currentTime + 10);
                            mediaElements[i].currentTime = Math.min(dur, mediaElements[i].currentTime + 10);
                        }
                    } catch(e) {}
                };

                setTimeout(function() { checkAndApplyAllVideos('initial'); }, 300);
            })();
        """.trimIndent()

        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Injects the Background Player engine.
     * Prevents video/audio from pausing when screen is locked, minimized, or tab is switched.
     * Comprehensive fix for YouTube, YouTube Music, and all HTML5 web media players.
     */
    fun injectBackgroundPlayerScript(webView: WebView, enabled: Boolean) {
        val script = """
            (function() {
                window.__GVONE_BACKGROUND_PLAYER_ENABLED__ = ${if (enabled) "true" else "false"};
                if (window.__GVONE_BG_PLAYER_INSTALLED__) return;
                window.__GVONE_BG_PLAYER_INSTALLED__ = true;

                try {
                    // 1. Page Visibility API overrides on both Document.prototype and document
                    var returnFalse = function() { return false; };
                    var returnVisible = function() { return 'visible'; };
                    var returnTrue = function() { return true; };

                    var visibilityProps = ['hidden', 'webkitHidden', 'mozHidden', 'msHidden'];
                    for (var i = 0; i < visibilityProps.length; i++) {
                        var prop = visibilityProps[i];
                        try {
                            Object.defineProperty(Document.prototype, prop, { get: returnFalse, configurable: true });
                            Object.defineProperty(document, prop, { get: returnFalse, configurable: true });
                        } catch(e) {}
                    }

                    var stateProps = ['visibilityState', 'webkitVisibilityState', 'mozVisibilityState', 'msVisibilityState'];
                    for (var j = 0; j < stateProps.length; j++) {
                        var sProp = stateProps[j];
                        try {
                            Object.defineProperty(Document.prototype, sProp, { get: returnVisible, configurable: true });
                            Object.defineProperty(document, sProp, { get: returnVisible, configurable: true });
                        } catch(e) {}
                    }

                    try {
                        Document.prototype.hasFocus = returnTrue;
                        document.hasFocus = returnTrue;
                    } catch(e) {}

                    try {
                        Object.defineProperty(document, 'onvisibilitychange', {
                            get: function() { return null; },
                            set: function() {},
                            configurable: true
                        });
                    } catch(e) {}

                    // 2. Immediate capture and suppression of all visibility and pagehide events
                    var blockedEvents = [
                        'visibilitychange',
                        'webkitvisibilitychange',
                        'mozvisibilitychange',
                        'msvisibilitychange',
                        'pagehide',
                        'freeze'
                    ];

                    var eventStopper = function(e) {
                        if (window.__GVONE_BACKGROUND_PLAYER_ENABLED__) {
                            e.stopImmediatePropagation();
                            e.stopPropagation();
                        }
                    };

                    for (var k = 0; k < blockedEvents.length; k++) {
                        window.addEventListener(blockedEvents[k], eventStopper, true);
                        document.addEventListener(blockedEvents[k], eventStopper, true);
                    }

                    var windowBlurStopper = function(e) {
                        if (window.__GVONE_BACKGROUND_PLAYER_ENABLED__) {
                            if (e.target === window || e.target === document) {
                                e.stopImmediatePropagation();
                                e.stopPropagation();
                            }
                        }
                    };
                    window.addEventListener('blur', windowBlurStopper, true);
                    document.addEventListener('blur', windowBlurStopper, true);

                    // 3. User intentional pause flag vs background auto-pause defense
                    window.__GVONE_USER_INTENTIONAL_PAUSE__ = false;

                    function attachMediaProtection(media) {
                        if (!media || media.__gvone_bg_protected__) return;
                        media.__gvone_bg_protected__ = true;

                        var wasPlaying = false;
                        media.addEventListener('playing', function() {
                            wasPlaying = true;
                        });

                        media.addEventListener('pause', function() {
                            // If paused not by user interaction, and background play is enabled, auto resume
                            if (window.__GVONE_BACKGROUND_PLAYER_ENABLED__ && wasPlaying && !window.__GVONE_USER_INTENTIONAL_PAUSE__) {
                                setTimeout(function() {
                                    if (window.__GVONE_BACKGROUND_PLAYER_ENABLED__ && !window.__GVONE_USER_INTENTIONAL_PAUSE__ && media.paused && !media.ended) {
                                        try {
                                            var p = media.play();
                                            if (p && p.catch) p.catch(function() {});
                                        } catch(err) {}
                                    }
                                }, 80);
                            }
                            if (window.__GVONE_USER_INTENTIONAL_PAUSE__) {
                                wasPlaying = false;
                            }
                        });
                    }

                    // Apply protection to all existing and future media elements
                    var allMedia = document.querySelectorAll('video, audio');
                    for (var m = 0; m < allMedia.length; m++) {
                        attachMediaProtection(allMedia[m]);
                    }

                    var obs = new MutationObserver(function() {
                        var elements = document.querySelectorAll('video, audio');
                        for (var n = 0; n < elements.length; n++) {
                            attachMediaProtection(elements[n]);
                        }
                    });
                    try {
                        obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
                    } catch(e) {}

                    // YouTube specific dialog dismisser ("Video paused. Continue watching?")
                    setInterval(function() {
                        if (!window.__GVONE_BACKGROUND_PLAYER_ENABLED__) return;
                        try {
                            var btn = document.querySelector('yt-confirm-dialog-renderer button, ytd-popup-container button, [aria-label*="Yes"], [aria-label*="Continue"]');
                            if (btn && btn.offsetParent !== null) {
                                btn.click();
                            }
                        } catch(e) {}
                    }, 2000);

                } catch(e) {
                    console.warn('[GVONE Background Player] setup error:', e);
                }
            })();
        """.trimIndent()
        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }

    /**
     * Injects Chrome-like long-press context menu detection script across all websites.
     * Accurately identifies links, images, media, documents, and YouTube video elements
     * with touch slop and 500ms duration verification.
     */
    fun injectContextMenuScript(webView: WebView) {
        val script = """
            (function() {
                if (window.__GVONE_CONTEXT_MENU_INSTALLED__) return;
                window.__GVONE_CONTEXT_MENU_INSTALLED__ = true;

                var startX = 0;
                var startY = 0;
                var longPressTimer = null;
                var touchTarget = null;
                var touchMoved = false;

                function getFileType(url) {
                    if (!url) return null;
                    var clean = url.split('?')[0].toLowerCase();
                    if (clean.match(/\.(jpg|jpeg|png|gif|webp|svg|bmp|ico)$/)) return 'IMAGE';
                    if (clean.match(/\.(mp4|webm|mkv|mov|avi|flv|m3u8|mpd)$/)) return 'VIDEO';
                    if (clean.match(/\.(mp3|wav|ogg|m4a|aac|flac)$/)) return 'AUDIO';
                    if (clean.match(/\.(pdf|doc|docx|xls|xlsx|ppt|pptx|zip|tar|gz|apk)$/)) return 'DOCUMENT';
                    return null;
                }

                function extractElementInfo(el) {
                    if (!el || el === document.body || el === document.documentElement) return null;
                    var curr = el;
                    var anchor = null;
                    var image = null;
                    var video = null;
                    var audio = null;
                    var depth = 0;

                    while (curr && curr !== document.body && curr !== document.documentElement && depth < 12) {
                        depth++;
                        var tag = (curr.tagName || '').toLowerCase();
                        if (!video && tag === 'video') video = curr;
                        if (!audio && tag === 'audio') audio = curr;
                        if (!image && tag === 'img') image = curr;
                        if (!anchor && tag === 'a' && curr.href) anchor = curr;
                        if (!anchor && curr.getAttribute) {
                            var role = curr.getAttribute('role');
                            var dataHref = curr.getAttribute('data-href') || curr.getAttribute('data-url');
                            if (role === 'link' || dataHref) {
                                try {
                                    var resolved = dataHref ? new URL(dataHref, window.location.href).href : window.location.href;
                                    anchor = {
                                        href: resolved,
                                        innerText: curr.innerText || curr.textContent || '',
                                        title: curr.title || curr.getAttribute('aria-label') || ''
                                    };
                                } catch(e) {}
                            }
                        }
                        curr = curr.parentElement;
                    }

                    if (video) {
                        var vSrc = video.currentSrc || video.src || (video.querySelector('source') ? video.querySelector('source').src : '') || (anchor ? anchor.href : '');
                        var vTitle = video.title || video.getAttribute('aria-label') || (anchor ? (anchor.innerText || anchor.title) : '') || 'Video';
                        return {
                            url: vSrc || (anchor ? anchor.href : window.location.href),
                            title: vTitle,
                            text: vTitle,
                            srcUrl: vSrc,
                            targetType: 'VIDEO'
                        };
                    }

                    if (audio) {
                        var aSrc = audio.currentSrc || audio.src || (audio.querySelector('source') ? audio.querySelector('source').src : '');
                        var aTitle = audio.title || (anchor ? (anchor.innerText || anchor.title) : '') || 'Audio';
                        return {
                            url: aSrc || (anchor ? anchor.href : window.location.href),
                            title: aTitle,
                            text: aTitle,
                            srcUrl: aSrc,
                            targetType: 'AUDIO'
                        };
                    }

                    if (anchor && image) {
                        var iSrc = image.currentSrc || image.src || '';
                        var linkText = (anchor.innerText || anchor.textContent || image.alt || image.title || anchor.title || '').trim();
                        return {
                            url: anchor.href,
                            title: linkText || anchor.href,
                            text: linkText,
                            srcUrl: iSrc,
                            targetType: 'IMAGE_LINK'
                        };
                    }

                    if (image) {
                        var imgSrc = image.currentSrc || image.src || '';
                        var alt = (image.alt || image.title || '').trim();
                        return {
                            url: imgSrc,
                            title: alt || imgSrc,
                            text: alt,
                            srcUrl: imgSrc,
                            targetType: 'IMAGE'
                        };
                    }

                    if (anchor) {
                        var fileType = getFileType(anchor.href);
                        var aText = (anchor.innerText || anchor.textContent || anchor.getAttribute('aria-label') || anchor.title || '').trim();
                        return {
                            url: anchor.href,
                            title: aText || anchor.title || anchor.href,
                            text: aText,
                            srcUrl: null,
                            targetType: fileType ? fileType : 'LINK'
                        };
                    }

                    return null;
                }

                function triggerNativeContextMenu(info) {
                    if (!info || !info.url) return;
                    try {
                        var payload = JSON.stringify(info);
                        if (window.GVONEBrowserBridge && typeof window.GVONEBrowserBridge.onContextMenuTriggered === 'function') {
                            window.GVONEBrowserBridge.onContextMenuTriggered(payload);
                        } else if (window.GVONEBrowserBridge && typeof window.GVONEBrowserBridge.postMessageToBrowser === 'function') {
                            window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                                type: 'context_menu_triggered',
                                url: info.url,
                                title: info.title,
                                text: info.text,
                                srcUrl: info.srcUrl,
                                targetType: info.targetType
                            }));
                        }
                    } catch(err) {
                        console.warn('[GVONE ContextMenu] dispatch error:', err);
                    }
                }

                // 1. Native DOM contextmenu listener (capture phase)
                window.addEventListener('contextmenu', function(e) {
                    var target = e.target || document.elementFromPoint(e.clientX, e.clientY);
                    var info = extractElementInfo(target);
                    if (info) {
                        e.preventDefault();
                        e.stopPropagation();
                        triggerNativeContextMenu(info);
                    }
                }, true);

                // 2. Touch-based long-press listener (with touch slop and cancel)
                window.addEventListener('touchstart', function(e) {
                    if (e.touches.length !== 1) {
                        if (longPressTimer) clearTimeout(longPressTimer);
                        return;
                    }
                    var touch = e.touches[0];
                    startX = touch.clientX;
                    startY = touch.clientY;
                    touchTarget = touch.target;
                    touchMoved = false;

                    if (longPressTimer) clearTimeout(longPressTimer);
                    longPressTimer = setTimeout(function() {
                        if (touchMoved) return;
                        var target = touchTarget || document.elementFromPoint(startX, startY);
                        var info = extractElementInfo(target);
                        if (info) {
                            triggerNativeContextMenu(info);
                        }
                    }, 480);
                }, { passive: true });

                window.addEventListener('touchmove', function(e) {
                    if (!longPressTimer) return;
                    if (e.touches.length > 0) {
                        var touch = e.touches[0];
                        var dx = touch.clientX - startX;
                        var dy = touch.clientY - startY;
                        if (Math.hypot(dx, dy) > 12) {
                            touchMoved = true;
                            clearTimeout(longPressTimer);
                            longPressTimer = null;
                        }
                    }
                }, { passive: true });

                window.addEventListener('touchend', function() {
                    if (longPressTimer) {
                        clearTimeout(longPressTimer);
                        longPressTimer = null;
                    }
                }, { passive: true });

                window.addEventListener('touchcancel', function() {
                    if (longPressTimer) {
                        clearTimeout(longPressTimer);
                        longPressTimer = null;
                    }
                }, { passive: true });
            })();
        """.trimIndent()
        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }
}
