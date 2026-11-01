package com.example.data.agent

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.data.sync.BotAssistantService
import com.example.ui.screens.ide.BotChatMessage
import com.example.ui.screens.ide.GrokBotPersona
import com.example.data.terminal.TerminalLineType
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class BotAssistantStatus {
    OFF, IDLE, WORKING, PAUSED, ERROR
}

data class BotAssistantJob(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val context: String,
    val personaId: String,
    val isContextReview: Boolean = false,
    val echoToTerminal: Boolean = false
)

data class BotTerminalOutput(val text: String, val type: TerminalLineType)

/** Shared, persisted conversation and work queue used by the navigator, terminal, and service. */
class BotAssistantRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("gvone_bot_assistant", Context.MODE_PRIVATE)
    private val lock = Any()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val _terminalOutput = MutableSharedFlow<BotTerminalOutput>(extraBufferCapacity = 32)
    val terminalOutput: SharedFlow<BotTerminalOutput> = _terminalOutput

    private val _messages = MutableStateFlow(loadMessages())
    val messages: StateFlow<List<BotChatMessage>> = _messages.asStateFlow()

    private val _persona = MutableStateFlow(loadPersona())
    val persona: StateFlow<GrokBotPersona> = _persona.asStateFlow()

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _paused = MutableStateFlow(prefs.getBoolean(KEY_PAUSED, false))
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _status = MutableStateFlow(
        if (!_enabled.value) BotAssistantStatus.OFF
        else if (_paused.value) BotAssistantStatus.PAUSED
        else BotAssistantStatus.IDLE
    )
    val status: StateFlow<BotAssistantStatus> = _status.asStateFlow()

    private val _activeTask = MutableStateFlow(prefs.getString(KEY_ACTIVE_TASK, null))
    val activeTask: StateFlow<String?> = _activeTask.asStateFlow()

    private val queue = loadQueue().toMutableList()

    init {
        if (queue.isNotEmpty()) wakeups.trySend(Unit)
    }

    fun setPersona(persona: GrokBotPersona) {
        _persona.value = persona
        prefs.edit().putString(KEY_PERSONA, persona.id).apply()
    }

    fun appendMessage(message: BotChatMessage) = synchronized(lock) {
        val updated = (_messages.value + message).takeLast(MAX_MESSAGES)
        _messages.value = updated
        saveMessages(updated)
    }

    fun submitMessage(prompt: String, context: String, echoToTerminal: Boolean = false) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return
        val selectedPersona = _persona.value
        appendMessage(
            BotChatMessage(
                senderId = "user",
                senderName = "You",
                content = cleanPrompt
            )
        )
        enqueue(
            BotAssistantJob(
                prompt = cleanPrompt,
                context = context,
                personaId = selectedPersona.id,
                echoToTerminal = echoToTerminal
            )
        )
        enableAndStartService()
    }

    /** Context changes are deduplicated and only queued after the user has enabled the assistant. */
    fun observeContext(context: String, sourceKey: String) {
        if (!_enabled.value || _paused.value || context.isBlank()) return
        val key = "context_hash_${sourceKey}"
        val fingerprint = context.hashCode().toString()
        if (prefs.getString(key, null) == fingerprint) return
        prefs.edit().putString(key, fingerprint).apply()
        enqueue(
            BotAssistantJob(
                prompt = "Review this new GVONE $sourceKey context and decide whether a useful task should be performed. If no action is useful, return exactly NO_ACTION.",
                context = context,
                personaId = _persona.value.id,
                isContextReview = true
            )
        )
        startService(ACTION_PROCESS)
    }

    fun setPaused(paused: Boolean) {
        if (!paused && !_enabled.value) {
            enableAndStartService()
            return
        }
        if (paused && !_enabled.value) return
        _paused.value = paused
        prefs.edit().putBoolean(KEY_PAUSED, paused).apply()
        if (!_enabled.value) return
        _status.value = if (paused) BotAssistantStatus.PAUSED else BotAssistantStatus.IDLE
        startService(if (paused) ACTION_PAUSE else ACTION_RESUME)
    }

    fun stop() {
        synchronized(lock) {
            queue.clear()
            saveQueue()
        }
        stopFromService()
        appContext.stopService(Intent(appContext, BotAssistantService::class.java))
    }

    fun stopFromService() {
        synchronized(lock) {
            queue.clear()
            saveQueue()
        }
        _enabled.value = false
        _paused.value = false
        _status.value = BotAssistantStatus.OFF
        _activeTask.value = null
        prefs.edit()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_PAUSED, false)
            .remove(KEY_ACTIVE_TASK)
            .apply()
    }

    fun setPausedFromService(paused: Boolean) {
        _paused.value = paused
        prefs.edit().putBoolean(KEY_PAUSED, paused).apply()
        if (_enabled.value) {
            _status.value = if (paused) BotAssistantStatus.PAUSED else BotAssistantStatus.IDLE
        }
    }

    fun nextJobOrNull(): BotAssistantJob? = synchronized(lock) {
        if (!_enabled.value || _paused.value || queue.isEmpty()) return@synchronized null
        val job = queue.removeAt(0)
        saveQueue()
        job
    }

    suspend fun awaitWorkSignal() {
        wakeups.receive()
    }

    fun notifyWorkAvailable() {
        wakeups.trySend(Unit)
    }

    fun setWorking(task: String?) {
        _activeTask.value = task
        _status.value = when {
            task != null -> BotAssistantStatus.WORKING
            !_enabled.value -> BotAssistantStatus.OFF
            _paused.value -> BotAssistantStatus.PAUSED
            else -> BotAssistantStatus.IDLE
        }
        prefs.edit().apply {
            if (task == null) remove(KEY_ACTIVE_TASK) else putString(KEY_ACTIVE_TASK, task)
        }.apply()
    }

    fun reportError(message: String) {
        _status.value = BotAssistantStatus.ERROR
        appendMessage(
            BotChatMessage(
                senderId = _persona.value.id,
                senderName = _persona.value.displayName,
                content = message
            )
        )
    }

    fun addAssistantResponse(
        text: String,
        personaId: String = _persona.value.id,
        extractedCode: String? = null
    ) {
        val responsePersona = GrokBotPersona.values().firstOrNull { it.id == personaId } ?: _persona.value
        appendMessage(
            BotChatMessage(
                senderId = responsePersona.id,
                senderName = responsePersona.displayName,
                content = text,
                extractedCode = extractedCode
            )
        )
    }

    fun publishTerminalOutput(text: String, type: TerminalLineType) {
        _terminalOutput.tryEmit(BotTerminalOutput(text, type))
    }

    fun setServiceStopped() {
        if (!_enabled.value) {
            _status.value = BotAssistantStatus.OFF
            return
        }
        _status.value = if (_paused.value) BotAssistantStatus.PAUSED else BotAssistantStatus.IDLE
        _activeTask.value = null
    }

    private fun enqueue(job: BotAssistantJob) = synchronized(lock) {
        // A context review already queued for the same context is redundant.
        if (job.isContextReview && queue.any { it.isContextReview && it.context == job.context }) return@synchronized
        queue.add(job)
        saveQueue()
        wakeups.trySend(Unit)
    }

    private fun enableAndStartService() {
        _enabled.value = true
        _paused.value = false
        _status.value = BotAssistantStatus.IDLE
        prefs.edit().putBoolean(KEY_ENABLED, true).putBoolean(KEY_PAUSED, false).apply()
        startService(ACTION_PROCESS)
    }

    private fun startService(action: String) {
        val intent = Intent(appContext, BotAssistantService::class.java).setAction(action)
        ContextCompat.startForegroundService(appContext, intent)
    }

    private fun saveMessages(messages: List<BotChatMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("senderId", message.senderId)
                    .put("senderName", message.senderName)
                    .put("content", message.content)
                    .put("timestamp", message.timestamp)
                    .put("extractedCode", message.extractedCode)
            )
        }
        prefs.edit().putString(KEY_MESSAGES, array.toString()).apply()
    }

    private fun loadMessages(): List<BotChatMessage> = try {
        val array = JSONArray(prefs.getString(KEY_MESSAGES, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            BotChatMessage(
                id = item.optString("id", UUID.randomUUID().toString()),
                senderId = item.optString("senderId", "user"),
                senderName = item.optString("senderName", "GVONE"),
                content = item.optString("content", ""),
                timestamp = item.optLong("timestamp", System.currentTimeMillis()),
                extractedCode = item.optString("extractedCode").takeIf { it.isNotEmpty() }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun saveQueue() {
        val array = JSONArray()
        queue.forEach { job ->
            array.put(
                JSONObject()
                    .put("id", job.id)
                    .put("prompt", job.prompt)
                    .put("context", job.context)
                    .put("personaId", job.personaId)
                    .put("isContextReview", job.isContextReview)
                    .put("echoToTerminal", job.echoToTerminal)
            )
        }
        prefs.edit().putString(KEY_QUEUE, array.toString()).apply()
    }

    private fun loadQueue(): List<BotAssistantJob> = try {
        val array = JSONArray(prefs.getString(KEY_QUEUE, "[]") ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            BotAssistantJob(
                id = item.optString("id", UUID.randomUUID().toString()),
                prompt = item.optString("prompt", ""),
                context = item.optString("context", ""),
                personaId = item.optString("personaId", GrokBotPersona.GROK_CODER.id),
                isContextReview = item.optBoolean("isContextReview", false),
                echoToTerminal = item.optBoolean("echoToTerminal", false)
            ).takeIf { it.prompt.isNotBlank() }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun loadPersona(): GrokBotPersona {
        val id = prefs.getString(KEY_PERSONA, GrokBotPersona.GROK_CODER.id)
        return GrokBotPersona.values().firstOrNull { it.id == id } ?: GrokBotPersona.GROK_CODER
    }

    companion object {
        const val ACTION_PROCESS = "com.example.botassistant.PROCESS"
        const val ACTION_PAUSE = "com.example.botassistant.PAUSE"
        const val ACTION_RESUME = "com.example.botassistant.RESUME"
        const val ACTION_STOP = "com.example.botassistant.STOP"

        private const val KEY_MESSAGES = "messages"
        private const val KEY_PERSONA = "persona"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PAUSED = "paused"
        private const val KEY_ACTIVE_TASK = "active_task"
        private const val KEY_QUEUE = "queue"
        private const val MAX_MESSAGES = 200

        @Volatile private var instance: BotAssistantRuntime? = null

        fun get(context: Context): BotAssistantRuntime = instance ?: synchronized(this) {
            instance ?: BotAssistantRuntime(context).also { instance = it }
        }
    }
}
