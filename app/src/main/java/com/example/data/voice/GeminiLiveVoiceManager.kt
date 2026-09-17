package com.example.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

/**
 * Manages continuous bidirectional live voice conversation with Gemini.
 * Captures user spoken words via SpeechRecognizer, reports transcript to Terminal,
 * and reads out Gemini's AI response aloud using Android TextToSpeech.
 */
class GeminiLiveVoiceManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val tag = "GeminiLiveVoice"
    private val mainHandler = Handler(Looper.getMainLooper())

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var onTranscriptRecognized: ((String) -> Unit)? = null
    var onSpeechStateChanged: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    init {
        initializeTts()
    }

    private fun initializeTts() {
        try {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech?.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech?.language = Locale.getDefault()
                    }
                    isTtsInitialized = true
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                            // If live voice mode is still active, resume listening for user response!
                            if (_isVoiceActive.value) {
                                mainHandler.postDelayed({
                                    if (_isVoiceActive.value && !_isSpeaking.value) {
                                        startListeningInternal()
                                    }
                                }, 350)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                            if (_isVoiceActive.value) {
                                mainHandler.postDelayed({
                                    if (_isVoiceActive.value) startListeningInternal()
                                }, 400)
                            }
                        }
                    })
                } else {
                    Log.w(tag, "TextToSpeech init failed with status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TextToSpeech", e)
        }
    }

    fun startLiveSession() {
        _isVoiceActive.value = true
        mainHandler.post {
            startListeningInternal()
        }
    }

    fun stopLiveSession() {
        _isVoiceActive.value = false
        _isListening.value = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e(tag, "Error stopping SpeechRecognizer", e)
            }
            stopSpeaking()
        }
    }

    private fun startListeningInternal() {
        if (!_isVoiceActive.value) return
        if (_isSpeaking.value) return

        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onSpeechError?.invoke("Speech recognition is not available on this device.")
                _isListening.value = false
                return
            }

            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createRecognitionListener())
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
            onSpeechStateChanged?.invoke("Listening...")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start listening", e)
            _isListening.value = false
            onSpeechError?.invoke("Mic error: ${e.message}")
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
            }

            override fun onBeginningOfSpeech() {
                onSpeechStateChanged?.invoke("Hearing speech...")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                onSpeechStateChanged?.invoke("Processing speech...")
            }

            override fun onError(error: Int) {
                _isListening.value = false
                val errorMsg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Audio record permission needed"
                    SpeechRecognizer.ERROR_NETWORK -> "Network connection error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Speech recognition error ($error)"
                }

                // If no match or timeout, quietly re-listen if still active
                if (_isVoiceActive.value && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    mainHandler.postDelayed({
                        if (_isVoiceActive.value && !_isSpeaking.value) {
                            startListeningInternal()
                        }
                    }, 500)
                } else if (_isVoiceActive.value && error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    mainHandler.postDelayed({
                        if (_isVoiceActive.value) {
                            try {
                                speechRecognizer?.cancel()
                                speechRecognizer?.destroy()
                                speechRecognizer = null
                            } catch (_: Exception) {}
                            startListeningInternal()
                        }
                    }, 800)
                } else if (_isVoiceActive.value) {
                    onSpeechError?.invoke(errorMsg)
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim()
                if (!recognizedText.isNullOrBlank()) {
                    coroutineScope.launch(Dispatchers.Main) {
                        onTranscriptRecognized?.invoke(recognizedText)
                    }
                } else if (_isVoiceActive.value) {
                    mainHandler.postDelayed({
                        if (_isVoiceActive.value && !_isSpeaking.value) {
                            startListeningInternal()
                        }
                    }, 400)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()
                if (!partial.isNullOrBlank()) {
                    onSpeechStateChanged?.invoke("Hearing: $partial")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * Speaks text aloud using Android TextToSpeech.
     */
    fun speakAloud(text: String, onComplete: (() -> Unit)? = null) {
        if (!isTtsInitialized || textToSpeech == null) {
            onComplete?.invoke()
            return
        }

        // Clean out markdown symbols for natural TTS reading
        val cleanSpeechText = text
            .replace(Regex("[*#`_~>]"), "")
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1")
            .take(600) // Keep TTS spoken answers reasonably concise and natural

        mainHandler.post {
            try {
                // Pause speech recognition while TTS is speaking so it doesn't hear itself
                speechRecognizer?.stopListening()
                _isListening.value = false
                _isSpeaking.value = true

                val utteranceId = UUID.randomUUID().toString()
                val params = Bundle().apply {
                    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                }
                textToSpeech?.speak(cleanSpeechText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } catch (e: Exception) {
                Log.e(tag, "Error in TTS speakAloud", e)
                _isSpeaking.value = false
                onComplete?.invoke()
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            try {
                if (_isSpeaking.value) {
                    textToSpeech?.stop()
                    _isSpeaking.value = false
                }
            } catch (e: Exception) {
                Log.e(tag, "Error stopping TTS", e)
            }
        }
    }

    fun destroy() {
        stopLiveSession()
        mainHandler.post {
            try {
                textToSpeech?.shutdown()
                textToSpeech = null
            } catch (_: Exception) {}
        }
    }
}
