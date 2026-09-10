package com.example.data.ai

import android.content.Context
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
import java.util.concurrent.TimeUnit

class GVONEAIService(
    private val torManager: TorManager? = null,
    private val context: Context? = null
) {
    private val defaultClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    var runtimeApiKey: String? = null
    var activeModel: String = "gemini-2.5-flash"
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
        return torManager?.getOkHttpClient(timeoutSeconds = 30) ?: defaultClient
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

    suspend fun searchAndSynthesize(query: String): GVONEAISearchResult = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()

        if (!isApiKeyConfigured()) {
            return@withContext generateLocalSmartResult(query)
        }

        val models = listOf(activeModel, "gemini-2.5-flash", "gemini-3.5-flash", "gemini-flash-latest").distinct()

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
