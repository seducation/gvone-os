package com.example.agent.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Type-safe configuration parameter definition.
 */
data class ConfigParam(
    val key: String,
    val value: String,
    val category: String,
    val description: String,
    val isReadOnly: Boolean = false
)

/**
 * Central System Configuration Manager for GVONE OS.
 * Manages runtime models, voice parameters, agent permissions, memory policies, and terminal settings.
 */
class SystemConfigManager {

    private val configs = ConcurrentHashMap<String, ConfigParam>()
    private val _configsFlow = MutableStateFlow<Map<String, String>>(emptyMap())
    val configsFlow: StateFlow<Map<String, String>> = _configsFlow.asStateFlow()

    init {
        registerDefaultConfigs()
    }

    private fun registerDefaultConfigs() {
        register("model.default", "gemini-2.5-flash", "Model", "Default reasoning and planning AI model")
        register("model.coding", "gemini-2.5-pro", "Model", "Specialized model for code analysis & compilation")
        register("voice.auto_speak", "true", "Voice", "Speak agent telemetry and boundary announcements")
        register("voice.continuous", "false", "Voice", "Persistent voice listening loop")
        register("voice.stt_engine", "android_speech", "Voice", "Speech recognition provider")
        register("agent.max_steps", "15", "Agent", "Maximum execution steps per autonomous goal")
        register("agent.timeout_ms", "45000", "Agent", "Timeout threshold for sub-agent execution")
        register("agent.debug_mode", "false", "Agent", "Transparent step and planner log output")
        register("security.sandbox_mode", "strict", "Security", "Sandboxing policy for file and network execution")
        register("terminal.theme", "matrix_dark", "Terminal", "Terminal visual styling theme")
        register("terminal.history_size", "100", "Terminal", "Max command history entries")
    }

    fun register(key: String, value: String, category: String, description: String, isReadOnly: Boolean = false) {
        configs[key] = ConfigParam(key, value, category, description, isReadOnly)
        syncFlow()
    }

    fun get(key: String): String? = configs[key]?.value

    fun set(key: String, value: String): Boolean {
        val existing = configs[key]
        if (existing != null && existing.isReadOnly) {
            return false
        }
        val updated = existing?.copy(value = value) ?: ConfigParam(key, value, "Custom", "Custom runtime setting")
        configs[key] = updated
        syncFlow()
        return true
    }

    fun getAll(): List<ConfigParam> = configs.values.toList().sortedBy { it.key }

    fun listByCategory(category: String): List<ConfigParam> =
        configs.values.filter { it.category.equals(category, ignoreCase = true) }

    fun formatReport(): String = buildString {
        appendLine("=== GVONE OS SYSTEM CONFIGURATION ===")
        val byCat = configs.values.groupBy { it.category }
        byCat.forEach { (cat, params) ->
            appendLine("[$cat]")
            params.sortedBy { it.key }.forEach {
                appendLine("  ${it.key.padEnd(24)} = ${it.value} (${it.description})")
            }
        }
    }.trim()

    private fun syncFlow() {
        _configsFlow.value = configs.mapValues { it.value.value }
    }

    companion object {
        val global: SystemConfigManager by lazy { SystemConfigManager() }
    }
}
