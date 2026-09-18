package com.example.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GVONEAIService(private val context: Context) {
    val activeModel = "gemini-2.5-flash"

    fun isApiKeyConfigured(): Boolean {
        return false
    }

    suspend fun chat(userMessage: String): String = withContext(Dispatchers.IO) {
        "GVONE AI Assistant: Processing '$userMessage'. Ready in terminal sandbox."
    }

    suspend fun chatWithPhoto(promptText: String, photoUri: String, label: String? = null): String = withContext(Dispatchers.IO) {
        "GVONE Multimodal AI: Processed '${label ?: "image"}' with prompt: \"$promptText\"."
    }
}
