package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.BrowserTab
import com.example.data.tor.TorConnectionState
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GVONEWebView(
    tab: BrowserTab,
    isTorActive: Boolean,
    torConnectionState: TorConnectionState = TorConnectionState.DISCONNECTED,
    torLastError: String? = null,
    onRetryTor: () -> Unit = {},
    onDisableTor: () -> Unit = {},
    onLaunchOrbot: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onTitleChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onFaviconChanged: (String?) -> Unit,
    onProgressChanged: (Int) -> Unit,
    onStartDownload: (url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var loadProgress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var webViewError by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // When Tor is enabled and has connection failure, fail closed to prevent leaking cleartext traffic
        if (isTorActive && torConnectionState == TorConnectionState.ERROR) {
            TorFailClosedErrorScreen(
                errorMessage = torLastError ?: "Tor SOCKS5 proxy is currently unreachable.",
                onRetry = onRetryTor,
                onDisableTor = onDisableTor,
                onLaunchOrbot = onLaunchOrbot,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportZoom(true)
                            if (tab.desktopMode) {
                                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            }
                        }

                        setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
                            onStartDownload(url, userAgent, contentDisposition, mimetype)
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                                isLoading = newProgress < 100
                                onProgressChanged(newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    onTitleChanged(title)
                                }
                            }

                            override fun onReceivedIcon(view: WebView?, icon: Bitmap?) {
                                onFaviconChanged(null)
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?
                            ) {
                                callback?.invoke(origin, false, false)
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    onUrlChanged(url)
                                    return false
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                webViewError = null
                                url?.let { onUrlChanged(it) }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                url?.let { onUrlChanged(it) }
                                view?.title?.let { onTitleChanged(it) }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    if (isTorActive) {
                                        webViewError = "Tor proxy error: Unable to route request anonymously."
                                    }
                                }
                            }

                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                // Enforce strict security unless user explicitly bypasses
                                handler?.cancel()
                            }
                        }

                        if (tab.url.isNotBlank() && tab.url != "gvone://newtab") {
                            loadUrl(tab.url)
                        }
                    }
                },
                update = { webView ->
                    if (tab.desktopMode) {
                        webView.settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                    } else {
                        webView.settings.userAgentString = null
                    }

                    if (tab.url.isNotBlank() && tab.url != "gvone://newtab" && webView.url != tab.url) {
                        webView.loadUrl(tab.url)
                    }
                }
            )

            // Loading bar at the top of webview
            if (isLoading && loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = if (isTorActive) GVONESecondary else if (tab.isPrivate) GVONESecondary else GVONEPrimary,
                    trackColor = Color.Transparent
                )
            }
        }
    }
}

/**
 * Fail-Closed error screen displayed when Tor is enabled by user but Tor connection is in ERROR state.
 * Strictly prevents leaking unencrypted cleartext requests.
 */
@Composable
private fun TorFailClosedErrorScreen(
    errorMessage: String,
    onRetry: () -> Unit,
    onDisableTor: () -> Unit,
    onLaunchOrbot: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0B0E14))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(GVONEAccentRed.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.VpnLock,
                    contentDescription = "Tor Blocked",
                    tint = GVONEAccentRed,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TOR Proxy Unreachable",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "TOR routing is active. To prevent IP and DNS leaks, traffic is held until an active Tor/Orbot SOCKS5 proxy is connected.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = Color(0xFF161C27),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = GVONEAccentRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = errorMessage,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Actions: Launch Orbot & Retry
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onLaunchOrbot,
                    modifier = Modifier.weight(1f).testTag("tor_launch_orbot_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GVONESecondary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Start Orbot", fontSize = 12.sp)
                }

                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).testTag("tor_failclosed_retry_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry TOR", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Secondary Actions: Continue in Normal Direct Mode or Open Settings
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    onClick = onDisableTor,
                    modifier = Modifier.weight(1f).testTag("tor_disable_browse_btn"),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Disable TOR & Load", fontSize = 12.sp)
                }

                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f).testTag("tor_failclosed_settings_btn"),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings", fontSize = 12.sp)
                }
            }
        }
    }
}
