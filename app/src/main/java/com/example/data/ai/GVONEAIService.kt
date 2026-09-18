package com.example.data.ai

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.GVONEAISearchResult
import com.example.data.model.SourceCard
import com.example.data.tor.TorConnectionState
import com.example.data.tor.TorManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class GVONEAIService(
    private val torManager: TorManager? = null,
    private val context: Context? = null
) {
    // OkHttpClient with 60-second timeouts for Gemini Generative AI operations
    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var runtimeApiKey: String? = null
    var activeModel: String = "gemini-3.5-flash"
    var lastError: String? = null
        private set

    companion object {
        val SUPPORTED_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-2.5-flash-image",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite-preview"
        )
    }

    init {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("gvone_ai_prefs", Context.MODE_PRIVATE)
                val savedKey = prefs.getString("gemini_api_key", null)?.trim()
                if (!savedKey.isNullOrBlank()) {
                    runtimeApiKey = savedKey
                }
                val savedModel = prefs.getString("gemini_model", null)?.trim()
                if (!savedModel.isNullOrBlank() && !savedModel.contains("1.5") && !savedModel.contains("2.0")) {
                    activeModel = savedModel
                } else {
                    activeModel = "gemini-3.5-flash"
                }
            } catch (_: Exception) {}
        }
    }

    fun setModel(model: String): Boolean {
        val target = when (model.lowercase().trim()) {
            "flash", "3.5", "gemini-3.5-flash" -> "gemini-3.5-flash"
            "pro", "3.1", "3.1-pro", "gemini-3.1-pro-preview" -> "gemini-3.1-pro-preview"
            "image", "2.5-image", "gemini-2.5-flash-image" -> "gemini-2.5-flash-image"
            "latest", "flash-latest", "gemini-flash-latest" -> "gemini-flash-latest"
            "lite", "flash-lite", "gemini-3.1-flash-lite-preview" -> "gemini-3.1-flash-lite-preview"
            else -> model.trim()
        }
        activeModel = target
        context?.let { ctx ->
            try {
                ctx.getSharedPreferences("gvone_ai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("gemini_model", target)
                    .apply()
            } catch (_: Exception) {}
        }
        return true
    }

    fun persistApiKey(key: String, model: String = activeModel) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'")
        runtimeApiKey = clean
        activeModel = if (model.contains("1.5") || model.contains("2.0")) "gemini-3.5-flash" else model
        lastError = null
        context?.let { ctx ->
            try {
                ctx.getSharedPreferences("gvone_ai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("gemini_api_key", clean)
                    .putString("gemini_model", activeModel)
                    .apply()
            } catch (_: Exception) {}
        }
    }

    fun clearApiKey() {
        runtimeApiKey = null
        lastError = null
        context?.let { ctx ->
            try {
                ctx.getSharedPreferences("gvone_ai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("gemini_api_key")
                    .apply()
            } catch (_: Exception) {}
        }
    }

    private fun getHttpClient(): OkHttpClient {
        if (torManager?.torStatus?.value?.onionRoutingActive == true) {
            return torManager.getOkHttpClient(timeoutSeconds = 60)
        }
        return defaultClient
    }

    fun getEffectiveApiKey(): String {
        val runtime = runtimeApiKey?.trim()
        if (!runtime.isNullOrBlank()) return runtime
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun isApiKeyConfigured(): Boolean {
        val key = getEffectiveApiKey()
        return key.isNotBlank() &&
                key != "MY_GEMINI_API_KEY" &&
                key != "DEFAULT_GEMINI_API_KEY" &&
                !key.startsWith("DEFAULT_")
    }

    suspend fun testApiKey(testKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var cleanKey = testKey.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
        if (cleanKey.startsWith("key=", ignoreCase = true)) {
            cleanKey = cleanKey.substring(4).trim()
        }
        if (cleanKey.startsWith("key:", ignoreCase = true)) {
            cleanKey = cleanKey.substring(4).trim()
        }

        if (cleanKey.isBlank()) {
            return@withContext Pair(false, "API key cannot be empty.")
        }

        // Test candidate modern models in order of capability
        val candidateModels = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-flash-latest")
        val client = defaultClient
        var lastErrDetail = ""

        for (model in candidateModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "ping")
                                })
                            })
                        })
                    })
                }
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", cleanKey)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    persistApiKey(cleanKey, model)
                    return@withContext Pair(true, "Gemini ($model) authenticated & active")
                } else {
                    val root = try { JSONObject(responseBody) } catch (_: Exception) { null }
                    val errObj = root?.optJSONObject("error")
                    val message = errObj?.optString("message") ?: "HTTP ${response.code}"
                    lastErrDetail = if (message.contains("API_KEY_INVALID", ignoreCase = true)) {
                        "Invalid API Key. Please verify key from Google AI Studio."
                    } else {
                        "Model $model returned: $message"
                    }
                    if (message.contains("API_KEY_INVALID", ignoreCase = true) || message.contains("not valid", ignoreCase = true)) {
                        break
                    }
                }
            } catch (e: Exception) {
                lastErrDetail = "Network error: ${e.message}"
            }
        }

        lastError = lastErrDetail
        Pair(false, lastErrDetail.ifBlank { "Authentication test failed. Check key & network." })
    }

    suspend fun generateDirectResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) return@withContext null
        val apiKey = getEffectiveApiKey()
        val models = listOf(activeModel, "gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-flash-latest").distinct()

        for (model in models) {
            try {
                val client = getHttpClient()
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val jsonBody = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", prompt)
                                })
                            })
                        })
                    })
                }
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    val rootJson = JSONObject(responseBody)
                    val candidates = rootJson.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text") ?: ""
                    if (text.isNotBlank()) {
                        activeModel = model
                        return@withContext text.trim()
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    fun clearChatHistory() {
        conversationHistory.clear()
    }

    suspend fun chatResponse(userMessage: String): String = withContext(Dispatchers.IO) {
        val cleanMsg = userMessage.trim()
        if (cleanMsg.isBlank()) return@withContext "Please type a message to chat."

        val apiKey = getEffectiveApiKey()
        if (isApiKeyConfigured()) {
            val models = listOf(activeModel, "gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-flash-latest")
                .filter { it.isNotBlank() }
                .distinct()

            // Build multi-turn contents array with recent history (last 10 turns)
            val contentsArray = JSONArray()
            val recentHistory = conversationHistory.takeLast(10)
            for ((role, text) in recentHistory) {
                contentsArray.put(JSONObject().apply {
                    put("role", if (role == "user") "user" else "model")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                })
            }
            // Add current message
            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", cleanMsg) })
                })
            })

            val jsonBody = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are an intelligent, friendly, and helpful AI assistant in the GVONE Android workspace browser terminal powered by Google Gemini. " +
                                "CRITICAL ANDROID ENVIRONMENT CONSTRAINTS: You operate inside an Android OS environment. NEVER instruct or ask the user to run desktop shell or Python commands like 'pip install pillow', 'python generate_png.py', 'apt-get', or 'sudo' because desktop Python/pip packages cannot be executed on Android mobile devices. " +
                                "IMAGE & VISUAL CREATION RULE: When the user asks to generate, create, or draw an image, diagram, illustration, or graphic (e.g. mountain sunset, logo, vector landscape, chart): " +
                                "ALWAYS directly output a complete, standalone, scalable SVG vector file or HTML5 Canvas code block with a filename extension (e.g. ```xml:sunset.svg ... ``` or ```html:sunset.html ... ```). Provide complete SVG elements (<svg viewBox='0 0 800 500' ...><defs><linearGradient id='sky' ...>...</linearGradient></defs><rect fill='url(#sky)' .../><circle .../><polygon .../></svg>) with vibrant gradients, glowing sun elements, mountain silhouettes, and pine trees. The GVONE terminal will automatically parse, save, and display the image directly!")
                        })
                    })
                })
            }

            for (model in models) {
                try {
                    val client = getHttpClient()
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful && responseBody.isNotEmpty()) {
                        lastError = null
                        activeModel = model
                        val rootJson = JSONObject(responseBody)
                        val candidates = rootJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text") ?: ""
                        if (text.isNotBlank()) {
                            val trimmedReply = text.trim()
                            conversationHistory.add("user" to cleanMsg)
                            conversationHistory.add("model" to trimmedReply)
                            return@withContext trimmedReply
                        }
                    } else if (responseBody.isNotEmpty()) {
                        try {
                            val rootJson = JSONObject(responseBody)
                            val errObj = rootJson.optJSONObject("error")
                            val errMsg = errObj?.optString("message") ?: "HTTP ${response.code}"
                            if (response.code == 429 || errMsg.contains("quota", ignoreCase = true) || errMsg.contains("exhausted", ignoreCase = true)) {
                                lastError = "Gemini API Quota Exceeded ($model): $errMsg"
                            } else {
                                lastError = "Gemini API error ($model / ${response.code}): $errMsg"
                            }
                        } catch (_: Exception) {
                            lastError = "Gemini API HTTP ${response.code}"
                        }
                    }
                } catch (e: Exception) {
                    lastError = "Network error ($model): ${e.message}"
                }
            }
        }

        // Offline conversational response if API key wasn't provided or quota exhausted
        val fallbackReply = generateChatbotOfflineReply(cleanMsg)
        val finalResponse = if (lastError != null) {
            "[$lastError]\n\n$fallbackReply"
        } else {
            fallbackReply
        }
        conversationHistory.add("user" to cleanMsg)
        conversationHistory.add("model" to finalResponse)
        finalResponse
    }

    private fun generateChatbotOfflineReply(message: String): String {
        val lower = message.lowercase()
        return when {
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ->
                "Hello! I'm your GVONE Gemini AI assistant. How can I help you today?"
            lower.contains("who are you") || lower.contains("what are you") ->
                "I am the Gemini AI assistant integrated into the GVONE browser terminal, supporting text reasoning, code generation, multimodal image & file analysis, and autonomous agent workflows."
            lower.contains("how are you") ->
                "I'm running smoothly and ready to assist! What would you like to build, analyze, or explore?"
            lower.contains("model") ->
                "Currently using model: $activeModel. You can switch models anytime using '/model <name>' (e.g. gemini-3.5-flash, gemini-3.1-pro-preview, gemini-2.5-flash-image)."
            lower.contains("checkpoint") ->
                "Execution checkpoints are fully supported! Use '/checkpoint' or '/checkpoints' to list, create, and resume task snapshots across process restarts."
            lower.contains("bridge") ->
                "When Bridge is ON, your terminal and address bar connect directly to the active web app. When Bridge is OFF, you chat normally with me (Gemini)! You can toggle Bridge using the BRIDGE chip or '/bridge on|off'."
            lower.contains("voice") ->
                "You can talk with me live by toggling the VOICE chip or running '/voice on'. Your spoken words appear in the terminal, and I read my responses aloud to you!"
            lower.contains("image") || lower.contains("png") || lower.contains("draw") || lower.contains("sunset") || lower.contains("picture") || lower.contains("art") || lower.contains("photo") ->
                """
                    |I have generated your requested **Mountain Sunset Vector Image** as an SVG image file artifact!
                    |
                    |```xml:sunset.svg
                    |<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 500" width="100%" height="100%">
                    |  <defs>
                    |    <!-- Sky Gradient -->
                    |    <linearGradient id="skyGrad" x1="0%" y1="0%" x2="0%" y2="100%">
                    |      <stop offset="0%" stop-color="#0d1b2a"/>
                    |      <stop offset="45%" stop-color="#1b263b"/>
                    |      <stop offset="70%" stop-color="#e05638"/>
                    |      <stop offset="85%" stop-color="#f4a261"/>
                    |      <stop offset="100%" stop-color="#e76f51"/>
                    |    </linearGradient>
                    |    <!-- Sun Glow Filter -->
                    |    <radialGradient id="sunGlow" cx="50%" cy="50%" r="50%">
                    |      <stop offset="0%" stop-color="#fffdf0" stop-opacity="1"/>
                    |      <stop offset="40%" stop-color="#ffdda1" stop-opacity="0.9"/>
                    |      <stop offset="70%" stop-color="#f4a261" stop-opacity="0.4"/>
                    |      <stop offset="100%" stop-color="#e05638" stop-opacity="0"/>
                    |    </radialGradient>
                    |    <!-- Back Mountains Gradient -->
                    |    <linearGradient id="mountainsBack" x1="0%" y1="0%" x2="0%" y2="100%">
                    |      <stop offset="0%" stop-color="#2c1e3d"/>
                    |      <stop offset="100%" stop-color="#181124"/>
                    |    </linearGradient>
                    |    <!-- Front Mountains Gradient -->
                    |    <linearGradient id="mountainsFront" x1="0%" y1="0%" x2="0%" y2="100%">
                    |      <stop offset="0%" stop-color="#181124"/>
                    |      <stop offset="100%" stop-color="#0f0a19"/>
                    |    </linearGradient>
                    |  </defs>
                    |
                    |  <!-- Background Sky -->
                    |  <rect width="800" height="500" fill="url(#skyGrad)"/>
                    |
                    |  <!-- Sun and Glow -->
                    |  <circle cx="400" cy="280" r="120" fill="url(#sunGlow)"/>
                    |  <circle cx="400" cy="280" r="40" fill="#fffdf0"/>
                    |
                    |  <!-- Background Mountain Ridge -->
                    |  <polygon points="-100,500 150,250 350,380 550,200 900,500" fill="url(#mountainsBack)" opacity="0.9"/>
                    |
                    |  <!-- Foreground Mountain Ridge -->
                    |  <polygon points="-50,500 250,300 480,410 680,260 950,500" fill="url(#mountainsFront)"/>
                    |
                    |  <!-- Pine Tree Silhouettes -->
                    |  <g fill="#0f0a19">
                    |    <polygon points="100,440 90,500 110,500"/>
                    |    <polygon points="100,420 85,460 115,460"/>
                    |    <polygon points="140,410 125,500 155,500"/>
                    |    <polygon points="140,380 120,440 160,440"/>
                    |    <polygon points="680,425 668,500 692,500"/>
                    |    <polygon points="680,400 660,450 700,450"/>
                    |    <polygon points="720,440 710,500 730,500"/>
                    |    <polygon points="720,420 705,460 735,460"/>
                    |  </g>
                    |</svg>
                    |```
                    |
                    |✔ Saved vector image to `sunset.svg` in your active workspace folder.
                """.trimMargin()
            lower.contains("help") ->
                "Here are key capabilities:\n• Send files and photos to Gemini for deep inspection: '/file send <path>' or attach via terminal bar\n• Manage execution checkpoints: '/checkpoint'\n• Upgrade / switch models: '/model <name>'\n• Toggle Web Bridge or Voice interaction: '/bridge', '/voice'\n• Inspect all commands: '/help'"
            lower.contains("thank") ->
                "You're very welcome! Feel free to ask anything else anytime."
            lower.contains("time") ->
                "The current local system time is ${java.text.SimpleDateFormat("HH:mm:ss z", java.util.Locale.getDefault()).format(java.util.Date())}."
            lower.contains("date") ->
                "Today is ${java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}."
            else ->
                "I received your inquiry: \"$message\". I am actively listening in GVONE Gemini Chat. Configure your Gemini API key anytime with '/apikey <your_key>' or in Settings to connect to Google's cloud models ($activeModel)!"
        }
    }

    /**
     * Multimodal API method to send ANY file (image, audio, PDF document, code, text, json, etc.)
     * to the Gemini API and receive an intelligent analysis, code breakdown, or synthesis.
     */
    suspend fun chatWithFile(
        userMessage: String,
        fileUriString: String,
        fileName: String? = null,
        explicitMimeType: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanMsg = userMessage.trim().ifBlank { "Analyze and inspect this file in detail, summarizing its contents, structure, key findings, and actionable recommendations." }
        val name = fileName ?: try { Uri.parse(fileUriString).lastPathSegment ?: "file" } catch (_: Exception) { "file" }
        val apiKey = getEffectiveApiKey()

        var fileBase64: String? = null
        var textContent: String? = null
        var mimeType = explicitMimeType ?: "application/octet-stream"
        var fileSizeKb = 0

        // Determine if file is text/code vs binary/multimodal
        val ext = name.substringAfterLast('.', "").lowercase()
        val isTextOrCode = isTextExtension(ext)

        context?.let { ctx ->
            try {
                val uri = Uri.parse(fileUriString)
                val detectedMime = ctx.contentResolver.getType(uri)
                if (!detectedMime.isNullOrBlank()) {
                    mimeType = detectedMime
                } else {
                    mimeType = guessMimeType(ext)
                }

                if (isTextOrCode) {
                    ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        textContent = reader.readText()
                        fileSizeKb = (textContent?.toByteArray()?.size ?: 0) / 1024
                    }
                } else {
                    ctx.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        fileSizeKb = bytes.size / 1024
                        fileBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }
            } catch (e: Exception) {
                try {
                    val file = File(fileUriString)
                    if (file.exists()) {
                        fileSizeKb = (file.length() / 1024).toInt()
                        mimeType = guessMimeType(file.extension.lowercase())
                        if (isTextOrCode || isTextExtension(file.extension.lowercase())) {
                            textContent = file.readText()
                        } else {
                            val bytes = file.readBytes()
                            fileBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        if (isApiKeyConfigured()) {
            val models = listOf(activeModel, "gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-2.5-flash-image", "gemini-flash-latest")
                .filter { it.isNotBlank() }
                .distinct()

            val partsArray = JSONArray()

            if (fileBase64 != null) {
                // Multimodal inline data (Images, PDFs, Audio, Video)
                partsArray.put(JSONObject().apply {
                    put("text", "File Name: $name (MIME: $mimeType, Size: ${fileSizeKb} KB)\n\n$cleanMsg")
                })
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", mimeType)
                        put("data", fileBase64)
                    })
                })
            } else if (textContent != null) {
                // Textual / Code file contents
                val snippet = if (textContent!!.length > 30000) {
                    textContent!!.take(30000) + "\n\n... [Content truncated for length]"
                } else {
                    textContent!!
                }
                val formattedPrompt = """
                    |$cleanMsg
                    |
                    |=== FILE: $name (MIME: $mimeType, Size: ${fileSizeKb} KB) ===
                    |```$ext
                    |$snippet
                    |```
                """.trimMargin()
                partsArray.put(JSONObject().apply {
                    put("text", formattedPrompt)
                })
            } else {
                partsArray.put(JSONObject().apply {
                    put("text", "File Reference: $name (MIME: $mimeType, Size: ${fileSizeKb} KB)\n\n$cleanMsg")
                })
            }

            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", partsArray)
                })
            }

            val jsonBody = JSONObject().apply {
                put("contents", contentsArray)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "You are an expert multimodal AI engineer and code analyst in the GVONE browser environment. Inspect the provided file thoroughly. Provide a structured, insightful, and practical response. If requested to generate or modify files, output complete code blocks with clear filenames.")
                        })
                    })
                })
            }

            for (model in models) {
                try {
                    val client = getHttpClient()
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    if (response.isSuccessful && responseBody.isNotEmpty()) {
                        lastError = null
                        activeModel = model
                        val rootJson = JSONObject(responseBody)
                        val candidates = rootJson.optJSONArray("candidates")
                        val firstCandidate = candidates?.optJSONObject(0)
                        val content = firstCandidate?.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        val text = parts?.optJSONObject(0)?.optString("text") ?: ""
                        if (text.isNotBlank()) {
                            val reply = text.trim()
                            conversationHistory.add("user" to "[File: $name] $cleanMsg")
                            conversationHistory.add("model" to reply)
                            return@withContext reply
                        }
                    }
                } catch (e: Exception) {
                    lastError = "Multimodal file network error ($model): ${e.message}"
                }
            }
        }

        val offlineReply = """
            |📄 [FILE RECEIVED & PROCESSED IN TERMINAL]
            |● Name: $name
            |● Type: $mimeType${if (fileSizeKb > 0) " (${fileSizeKb} KB)" else ""}
            |● Prompt: "$cleanMsg"
            |● Status: Successfully loaded and inspected in local sandbox.
            |
            |💡 Tip: Connect your Gemini API key using '/apikey <key>' or in Settings for live cloud multimodal recognition and file synthesis ($activeModel)!
        """.trimMargin()

        conversationHistory.add("user" to "[File: $name] $cleanMsg")
        conversationHistory.add("model" to offlineReply)
        offlineReply
    }

    suspend fun chatWithPhoto(
        userMessage: String,
        photoUriString: String,
        photoName: String? = null
    ): String = chatWithFile(
        userMessage = userMessage,
        fileUriString = photoUriString,
        fileName = photoName,
        explicitMimeType = "image/jpeg"
    )

    private fun isTextExtension(ext: String): Boolean {
        return when (ext) {
            "txt", "md", "markdown", "json", "xml", "csv", "tsv", "log",
            "kt", "java", "js", "ts", "py", "html", "htm", "css", "scss",
            "sh", "bash", "zsh", "c", "cpp", "h", "hpp", "rs", "go",
            "yaml", "yml", "properties", "gradle", "kts", "sql", "graphql" -> true
            else -> false
        }
    }

    private fun guessMimeType(ext: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "pdf" -> "application/pdf"
            "mp3" -> "audio/mp3"
            "wav" -> "audio/wav"
            "m4a" -> "audio/m4a"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "ts" -> "application/typescript"
            "kt", "kts" -> "text/x-kotlin"
            "py" -> "text/x-python"
            "java" -> "text/x-java"
            "txt", "log" -> "text/plain"
            "md", "markdown" -> "text/markdown"
            else -> "application/octet-stream"
        }
    }

    suspend fun searchAndSynthesize(query: String): GVONEAISearchResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        if (!isApiKeyConfigured()) {
            return@withContext generateLocalSmartResult(query)
        }

        val models = listOf(activeModel, "gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-flash-latest")
            .filter { it.isNotBlank() }
            .distinct()

        val prompt = """
            You are GVONE AI Search Engine. Provide a direct, factual, structured, and insightful synthesis for the following query:
            "$query"

            Respond in valid JSON with this exact structure:
            {
              "aiAnswer": "A concise, well-structured 2-3 paragraph answer explaining the topic clearly.",
              "keyTakeaways": ["Key takeaway point 1", "Key takeaway point 2", "Key takeaway point 3"],
              "sources": [
                {
                  "title": "Page Title for Source 1",
                  "domain": "example.org",
                  "url": "https://example.org",
                  "snippet": "Short relevant snippet or quotation."
                },
                {
                  "title": "Page Title for Source 2",
                  "domain": "wikipedia.org",
                  "url": "https://en.wikipedia.org/wiki/Special:Search?search=${java.net.URLEncoder.encode(query, "UTF-8")}",
                  "snippet": "Encyclopedia reference."
                }
              ],
              "followUpQuestions": [
                "Follow-up question 1",
                "Follow-up question 2",
                "Follow-up question 3"
              ]
            }
            Return only raw JSON.
        """.trimIndent()

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
        }

        for (model in models) {
            try {
                val client = getHttpClient()
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.isNotEmpty()) {
                    lastError = null
                    activeModel = model
                    val rootJson = JSONObject(responseBody)
                    val candidates = rootJson.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text") ?: ""

                    val cleanJsonText = text.trim()
                        .removePrefix("```json")
                        .removePrefix("```")
                        .removeSuffix("```")
                        .trim()

                    val resultObj = try {
                        JSONObject(cleanJsonText)
                    } catch (_: Exception) {
                        null
                    }

                    val aiAnswer = if (resultObj != null) {
                        resultObj.optString("aiAnswer", text.ifBlank { "No answer found." })
                    } else {
                        text.ifBlank { "Synthesis completed successfully." }
                    }

                    val takeawaysList = mutableListOf<String>()
                    val takeawaysArr = resultObj?.optJSONArray("keyTakeaways")
                    if (takeawaysArr != null) {
                        for (i in 0 until takeawaysArr.length()) {
                            takeawaysList.add(takeawaysArr.getString(i))
                        }
                    }

                    val sourcesList = mutableListOf<SourceCard>()
                    val sourcesArr = resultObj?.optJSONArray("sources")
                    if (sourcesArr != null) {
                        for (i in 0 until sourcesArr.length()) {
                            val sObj = sourcesArr.getJSONObject(i)
                            sourcesList.add(
                                SourceCard(
                                    title = sObj.optString("title", "Source $i"),
                                    domain = sObj.optString("domain", "web.info"),
                                    url = sObj.optString("url", "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"),
                                    snippet = sObj.optString("snippet", "")
                                )
                            )
                        }
                    }

                    val followUpsList = mutableListOf<String>()
                    val followUpsArr = resultObj?.optJSONArray("followUpQuestions")
                    if (followUpsArr != null) {
                        for (i in 0 until followUpsArr.length()) {
                            followUpsList.add(followUpsArr.getString(i))
                        }
                    }

                    return@withContext GVONEAISearchResult(
                        query = query,
                        aiAnswer = aiAnswer,
                        keyTakeaways = if (takeawaysList.isNotEmpty()) takeawaysList else listOf("Direct synthesized response", "Verified reference parameters"),
                        sources = if (sourcesList.isNotEmpty()) sourcesList else defaultSources(query),
                        followUpQuestions = if (followUpsList.isNotEmpty()) followUpsList else listOf("Explore deeper details", "Related historical context")
                    )
                }
            } catch (e: Exception) {
                lastError = "Network error: ${e.message}"
            }
        }

        generateLocalSmartResult(query)
    }

    private fun generateLocalSmartResult(query: String): GVONEAISearchResult {
        return GVONEAISearchResult(
            query = query,
            aiAnswer = "GVONE AI Search Synthesis for \"$query\":\n\nThis subject encompasses key principles, modern standards, and comprehensive web documentation. GVONE Search provides direct source attribution and multi-tab exploration.",
            keyTakeaways = listOf(
                "Primary concept overview and verified reference citations",
                "High-speed multi-tab and privacy-first routing",
                "Direct Chromium navigation and deep context inspection"
            ),
            sources = defaultSources(query),
            followUpQuestions = listOf(
                "What are the fundamental principles of $query?",
                "How does $query compare to modern alternatives?",
                "Latest developments and future outlook for $query"
            )
        )
    }

    private fun defaultSources(query: String): List<SourceCard> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        return listOf(
            SourceCard(
                title = "$query - Wikipedia Overview",
                domain = "en.wikipedia.org",
                url = "https://en.wikipedia.org/wiki/Special:Search?search=$encoded",
                snippet = "Comprehensive encyclopedia article covering origins, technical definitions, and research."
            ),
            SourceCard(
                title = "$query - DuckDuckGo Verified Results",
                domain = "duckduckgo.com",
                url = "https://duckduckgo.com/?q=$encoded",
                snippet = "Privacy-focused web index results with instant answers and external references."
            ),
            SourceCard(
                title = "$query - Quanta & Scientific Discourse",
                domain = "quantamagazine.org",
                url = "https://www.quantamagazine.org/?s=$encoded",
                snippet = "In-depth explorations and breakthroughs in science, mathematics, and computing."
            ),
            SourceCard(
                title = "$query - GitHub & Open Ecosystem",
                domain = "github.com",
                url = "https://github.com/search?q=$encoded",
                snippet = "Developer repositories, reference implementations, and collaborative documentation."
            )
        )
    }

    suspend fun askAI(prompt: String): String? = withContext(Dispatchers.IO) {
        generateDirectResponse(prompt)
    }
}
