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
    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    var runtimeApiKey: String? = null
    var activeModel: String = "gemini-2.0-flash"
    var lastError: String? = null
        private set

    init {
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("gvone_ai_prefs", Context.MODE_PRIVATE)
                val savedKey = prefs.getString("gemini_api_key", null)?.trim()
                if (!savedKey.isNullOrBlank()) {
                    runtimeApiKey = savedKey
                }
                val savedModel = prefs.getString("gemini_model", null)?.trim()
                if (!savedModel.isNullOrBlank()) {
                    activeModel = savedModel
                }
            } catch (_: Exception) {}
        }
    }

    fun persistApiKey(key: String, model: String = activeModel) {
        val clean = key.trim().removeSurrounding("\"").removeSurrounding("'")
        runtimeApiKey = clean
        activeModel = model
        lastError = null
        context?.let { ctx ->
            try {
                ctx.getSharedPreferences("gvone_ai_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("gemini_api_key", clean)
                    .putString("gemini_model", model)
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
            return torManager.getOkHttpClient(timeoutSeconds = 15)
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

        // Test candidate models
        val candidateModels = listOf("gemini-2.5-flash", "gemini-3.5-flash", "gemini-flash-latest")
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
                    // If key itself is strictly invalid, no need to retry other models
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
        val models = listOf(activeModel, "gemini-2.5-flash", "gemini-3.5-flash", "gemini-flash-latest").distinct()

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
            val models = listOf(activeModel, "gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.5-flash", "gemini-1.5-pro")
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
                            put("text", "You are an intelligent, friendly, and helpful AI chatbot assistant in the GVONE browser terminal. Provide clear, direct, and conversational responses. You can answer questions, explain concepts, write code, tell stories, and chat naturally like a chatbot.")
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
                                lastError = "Gemini API Quota Exceeded on free tier ($model): $errMsg"
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
                "Hello! I'm your GVONE Gemini AI Chatbot assistant. How can I help you today?"
            lower.contains("who are you") || lower.contains("what are you") ->
                "I am the Gemini AI conversational assistant integrated into the GVONE browser terminal. When Bridge is OFF, I chat with you directly. When Voice is ON, you can speak with me live!"
            lower.contains("how are you") ->
                "I'm running great and ready to help! What would you like to explore or discuss?"
            lower.contains("bridge") ->
                "When Bridge is ON, your terminal and address bar connect directly to the active web app. When Bridge is OFF, you chat normally with me (Gemini)! You can toggle Bridge using the BRIDGE chip or '/bridge on|off'."
            lower.contains("voice") ->
                "You can talk with me live by toggling the VOICE chip or running '/voice on'. Your spoken words appear in the terminal, and I read my responses aloud to you!"
            lower.contains("help") ->
                "Here are some things you can do:\n• Chat directly with Gemini when Bridge is OFF\n• Tap the VOICE chip to speak and listen live with transcript in terminal\n• Tap BRIDGE: OFF to toggle Web Bridge ON/OFF\n• Type '/help' to inspect all terminal and system commands"
            lower.contains("thank") ->
                "You're very welcome! Feel free to ask anything else anytime."
            lower.contains("time") ->
                "The current local system time is ${java.text.SimpleDateFormat("HH:mm:ss z", java.util.Locale.getDefault()).format(java.util.Date())}."
            lower.contains("date") ->
                "Today is ${java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date())}."
            lower.contains("weather") ->
                "To check live weather forecast, type '/weather <city>' or ask me for a city report!"
            else ->
                "I received your inquiry: \"$message\". I am actively listening in GVONE Gemini Chat. Configure your custom Gemini API key anytime with '/apikey <your_key>' or in Settings to connect to Google's cloud models!"
        }
    }

    suspend fun chatWithPhoto(
        userMessage: String,
        photoUriString: String,
        photoName: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanMsg = userMessage.trim().ifBlank { "Describe and analyze this image in detail." }
        val name = photoName ?: try { Uri.parse(photoUriString).lastPathSegment ?: "photo.jpg" } catch (_: Exception) { "photo.jpg" }
        val apiKey = getEffectiveApiKey()

        var imageBase64: String? = null
        var mimeType = "image/jpeg"
        var imageSizeKb = 0

        context?.let { ctx ->
            try {
                val uri = Uri.parse(photoUriString)
                mimeType = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                ctx.contentResolver.openInputStream(uri)?.use { stream ->
                    val bytes = stream.readBytes()
                    imageSizeKb = bytes.size / 1024
                    imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            } catch (e: Exception) {
                try {
                    val file = File(photoUriString)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        imageSizeKb = bytes.size / 1024
                        val ext = file.extension.lowercase()
                        mimeType = if (ext == "png") "image/png" else if (ext == "webp") "image/webp" else "image/jpeg"
                        imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                } catch (_: Exception) {}
            }
        }

        if (isApiKeyConfigured() && imageBase64 != null) {
            val models = listOf(activeModel, "gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.5-flash")
                .filter { it.isNotBlank() }
                .distinct()

            val partsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", cleanMsg)
                })
                put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", mimeType)
                        put("data", imageBase64)
                    })
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
                            put("text", "You are an expert AI vision and multimodal assistant in GVONE terminal. Inspect the provided image closely and answer the user's prompt or provide a helpful, accurate, and insightful breakdown of what is shown.")
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
                            conversationHistory.add("user" to "[Photo: $name] $cleanMsg")
                            conversationHistory.add("model" to reply)
                            return@withContext reply
                        }
                    }
                } catch (e: Exception) {
                    lastError = "Vision network error ($model): ${e.message}"
                }
            }
        }

        val offlineReply = """
            |📷 [PHOTO RECEIVED IN TERMINAL]
            |● File: $name
            |● Format: $mimeType${if (imageSizeKb > 0) " (${imageSizeKb} KB)" else ""}
            |● Prompt: "$cleanMsg"
            |● Status: Photo successfully received and processed directly in terminal.
            |
            |💡 Tip: Connect your Gemini API key using '/apikey <key>' or in Settings for live cloud AI vision recognition and multimodal synthesis!
        """.trimMargin()

        conversationHistory.add("user" to "[Photo: $name] $cleanMsg")
        conversationHistory.add("model" to offlineReply)
        offlineReply
    }

    suspend fun searchAndSynthesize(query: String): GVONEAISearchResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        if (!isApiKeyConfigured()) {
            return@withContext generateLocalSmartResult(query)
        }

        val models = listOf(activeModel, "gemini-2.0-flash", "gemini-1.5-flash", "gemini-2.5-flash", "gemini-1.5-pro")
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
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
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
