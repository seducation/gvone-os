package com.example.data.webwidget

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import android.webkit.WebView
import com.example.data.model.WebWidgetType
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object WebWidgetExtractionService {

    /**
     * Inspects the webpage under the specified screen crop rectangle.
     * Uses evaluateJavascript to find the most meaningful DOM element intersecting the crop area.
     */
    fun inspectElementAt(
        webView: WebView,
        cropX: Float,
        cropY: Float,
        cropW: Float,
        cropH: Float,
        onResult: (DetectedDomElement?) -> Unit
    ) {
        val density = webView.context.resources.displayMetrics.density
        // Convert screen pixel coordinates to CSS pixels
        val cssX = (cropX / density).toInt()
        val cssY = (cropY / density).toInt()
        val cssW = (cropW / density).toInt()
        val cssH = (cropH / density).toInt()

        val jsScript = """
            (function() {
                try {
                    var x = $cssX;
                    var y = $cssY;
                    var w = $cssW;
                    var h = $cssH;

                    var centerX = x + Math.max(1, w / 2);
                    var centerY = y + Math.max(1, h / 2);

                    var el = document.elementFromPoint(centerX, centerY);
                    if (!el) el = document.elementFromPoint(x + 10, y + 10);
                    if (!el) el = document.body;

                    // Walk up to find a stable semantic container
                    var current = el;
                    var candidate = el;
                    var semanticTags = ['ARTICLE', 'SECTION', 'TABLE', 'MAIN', 'ASIDE', 'NAV', 'DIV', 'HEADER', 'FORM'];

                    while (current && current !== document.body && current !== document.documentElement) {
                        var tag = current.tagName;
                        var rect = current.getBoundingClientRect();
                        if (semanticTags.indexOf(tag) !== -1) {
                            if (rect.width >= w * 0.4 && rect.height >= h * 0.4) {
                                candidate = current;
                                break;
                            }
                        }
                        current = current.parentElement;
                    }

                    var target = candidate || el;
                    var targetRect = target.getBoundingClientRect();

                    function getUniqueSelector(elem) {
                        if (elem.id) return '#' + CSS.escape(elem.id);
                        var path = [];
                        while (elem && elem.nodeType === Node.ELEMENT_NODE) {
                            if (elem.id) {
                                path.unshift('#' + CSS.escape(elem.id));
                                break;
                            }
                            var selector = elem.nodeName.toLowerCase();
                            var sibling = elem;
                            var nth = 1;
                            while (sibling = sibling.previousElementSibling) {
                                if (sibling.nodeName.toLowerCase() === selector) nth++;
                            }
                            if (nth !== 1) selector += ":nth-of-type(" + nth + ")";
                            path.unshift(selector);
                            elem = elem.parentNode;
                        }
                        return path.join(" > ");
                    }

                    var heading = "";
                    var hElem = target.querySelector("h1, h2, h3, h4, h5, h6, [role='heading'], [class*='title'], [class*='header']");
                    if (hElem && hElem.innerText) {
                        heading = hElem.innerText.trim().substring(0, 60);
                    } else if (target.getAttribute("aria-label")) {
                        heading = target.getAttribute("aria-label").trim().substring(0, 60);
                    } else if (target.innerText) {
                        var firstLine = target.innerText.trim().split("\n")[0];
                        if (firstLine && firstLine.length < 50) heading = firstLine;
                    }

                    var hasIframe = target.querySelector("iframe") !== null || target.tagName === 'IFRAME';
                    var hasCanvas = target.querySelector("canvas") !== null || target.tagName === 'CANVAS';
                    var hasVideo = target.querySelector("video") !== null || target.tagName === 'VIDEO';

                    var htmlLength = (target.innerHTML || "").length;
                    var isCleanDom = !hasIframe && !hasCanvas && !hasVideo && htmlLength < 60000;

                    var suggestedType = "LIVE_DOM";
                    if (!isCleanDom) {
                        suggestedType = (hasIframe || hasCanvas) ? "LIVE_URL" : "SNAPSHOT";
                    }

                    // Clone target and remove scripts for safety
                    var clone = target.cloneNode(true);
                    var scripts = clone.querySelectorAll("script, noscript, meta, iframe");
                    for (var s = 0; s < scripts.length; s++) scripts[s].remove();

                    return JSON.stringify({
                        tagName: target.tagName || "DIV",
                        selector: getUniqueSelector(target),
                        heading: heading || "",
                        rectLeft: targetRect.left || 0,
                        rectTop: targetRect.top || 0,
                        rectWidth: targetRect.width || 0,
                        rectHeight: targetRect.height || 0,
                        suggestedType: suggestedType,
                        isCleanDom: isCleanDom,
                        html: clone.outerHTML || ""
                    });
                } catch(e) {
                    return JSON.stringify({ error: e.toString() });
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsScript) { jsonString ->
            if (jsonString == null || jsonString == "null") {
                onResult(null)
                return@evaluateJavascript
            }
            try {
                // evaluateJavascript returns a JSON string, which may be escaped with quotes
                val unescaped = if (jsonString.startsWith("\"") && jsonString.endsWith("\"")) {
                    val decoded = org.json.JSONTokener(jsonString).nextValue().toString()
                    decoded
                } else {
                    jsonString
                }

                val obj = JSONObject(unescaped)
                if (obj.has("error")) {
                    onResult(null)
                    return@evaluateJavascript
                }

                val tagName = obj.optString("tagName", "DIV")
                val selector = obj.optString("selector", "div")
                val heading = obj.optString("heading", "")
                val rectLeft = obj.optDouble("rectLeft", 0.0).toFloat() * density
                val rectTop = obj.optDouble("rectTop", 0.0).toFloat() * density
                val rectWidth = obj.optDouble("rectWidth", 0.0).toFloat() * density
                val rectHeight = obj.optDouble("rectHeight", 0.0).toFloat() * density
                val typeStr = obj.optString("suggestedType", "LIVE_DOM")
                val suggestedType = try { WebWidgetType.valueOf(typeStr) } catch (e: Exception) { WebWidgetType.LIVE_DOM }
                val isCleanDom = obj.optBoolean("isCleanDom", true)
                val html = obj.optString("html", "")

                onResult(
                    DetectedDomElement(
                        tagName = tagName,
                        selector = selector,
                        heading = heading,
                        rectLeft = rectLeft,
                        rectTop = rectTop,
                        rectWidth = rectWidth,
                        rectHeight = rectHeight,
                        suggestedType = suggestedType,
                        isCleanDom = isCleanDom,
                        html = html
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(null)
            }
        }
    }

    /**
     * Captures a high-resolution snapshot bitmap of the WebView cropped to the specified rectangle.
     */
    fun capturePortionBitmap(
        webView: WebView,
        cropX: Int,
        cropY: Int,
        cropWidth: Int,
        cropHeight: Int
    ): Bitmap? {
        return try {
            val wvWidth = webView.width
            val wvHeight = webView.height
            if (wvWidth <= 0 || wvHeight <= 0) return null

            val fullBitmap = Bitmap.createBitmap(wvWidth, wvHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(fullBitmap)
            webView.draw(canvas)

            val safeX = cropX.coerceIn(0, wvWidth - 1)
            val safeY = cropY.coerceIn(0, wvHeight - 1)
            val safeW = cropWidth.coerceIn(1, wvWidth - safeX)
            val safeH = cropHeight.coerceIn(1, wvHeight - safeY)

            val cropped = Bitmap.createBitmap(fullBitmap, safeX, safeY, safeW, safeH)
            if (cropped != fullBitmap) {
                fullBitmap.recycle()
            }
            cropped
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Wraps the extracted HTML into a standalone, sandboxed HTML page with responsive typography,
     * CSS reset, dark mode variables, and sanitized link targets.
     */
    fun prepareDomWidgetHtml(
        originalHtml: String,
        baseUrl: String,
        isDark: Boolean = true
    ): String {
        val bgColor = if (isDark) "#111827" else "#FFFFFF"
        val textColor = if (isDark) "#F3F4F6" else "#111827"
        val secondaryTextColor = if (isDark) "#9CA3AF" else "#4B5563"
        val linkColor = if (isDark) "#60A5FA" else "#2563EB"
        val borderColor = if (isDark) "rgba(255,255,255,0.1)" else "rgba(0,0,0,0.1)"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=2.0, user-scalable=yes">
                <base href="$baseUrl">
                <style>
                    * {
                        box-sizing: border-box;
                        margin: 0;
                        padding: 0;
                        -webkit-tap-highlight-color: transparent;
                    }
                    body {
                        background-color: $bgColor;
                        color: $textColor;
                        font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        font-size: 13px;
                        line-height: 1.4;
                        padding: 8px;
                        overflow-x: hidden;
                        word-break: break-word;
                    }
                    a {
                        color: $linkColor;
                        text-decoration: none;
                        pointer-events: auto;
                    }
                    a:hover {
                        text-decoration: underline;
                    }
                    img, svg, picture {
                        max-width: 100%;
                        height: auto;
                        display: inline-block;
                        border-radius: 4px;
                    }
                    table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 12px;
                    }
                    th, td {
                        padding: 4px 6px;
                        border-bottom: 1px solid $borderColor;
                    }
                    /* Hide intrusive layout elements if carried over */
                    header, footer, nav, [class*='cookie'], [class*='banner'], [class*='advertisement'] {
                        max-height: none;
                    }
                    /* Scrollbars */
                    ::-webkit-scrollbar {
                        width: 4px;
                        height: 4px;
                    }
                    ::-webkit-scrollbar-thumb {
                        background: rgba(150, 150, 150, 0.3);
                        border-radius: 2px;
                    }
                </style>
            </head>
            <body>
                $originalHtml
            </body>
            </html>
        """.trimIndent()
    }
}
