package com.example.agent.memory

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Strict hierarchical memory scopes to prevent context bleeding.
 */
enum class MemoryScope {
    GLOBAL,
    PROJECT,
    SESSION,
    CONVERSATION,
    TASK,
    AGENT
}

/**
 * Structured Memory Entry separate from raw conversation logs.
 */
data class MemoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val key: String,
    val value: String,
    val scope: MemoryScope = MemoryScope.GLOBAL,
    val scopeIdentifier: String = "global",
    val tags: List<String> = emptyList(),
    val confidence: Double = 1.0,
    val accessCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Structured Memory Store for GVONE OS.
 * Manages persistent knowledge, scoped task variables, and associative memory.
 */
class MemoryStore {

    private val entries = ConcurrentHashMap<String, MemoryEntry>()
    private val _entriesFlow = MutableStateFlow<List<MemoryEntry>>(emptyList())
    val entriesFlow: StateFlow<List<MemoryEntry>> = _entriesFlow.asStateFlow()

    init {
        // Seed some system memory
        setEntry("system.os", "GVONE OS v2.5 Enterprise AI Operating Environment", MemoryScope.GLOBAL)
        setEntry("system.mode.default", "Text Chat", MemoryScope.GLOBAL)
    }

    fun setEntry(
        key: String,
        value: String,
        scope: MemoryScope = MemoryScope.GLOBAL,
        scopeIdentifier: String = "global",
        tags: List<String> = emptyList()
    ): MemoryEntry {
        val existing = entries[key]
        val entry = MemoryEntry(
            id = existing?.id ?: UUID.randomUUID().toString(),
            key = key,
            value = value,
            scope = scope,
            scopeIdentifier = scopeIdentifier,
            tags = tags,
            accessCount = (existing?.accessCount ?: 0) + 1,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        entries[key] = entry
        _entriesFlow.value = entries.values.toList().sortedByDescending { it.updatedAt }
        return entry
    }

    fun getEntry(key: String): MemoryEntry? {
        val entry = entries[key] ?: return null
        val accessed = entry.copy(accessCount = entry.accessCount + 1)
        entries[key] = accessed
        return accessed
    }

    fun search(query: String, scope: MemoryScope? = null): List<MemoryEntry> {
        val lower = query.lowercase()
        return entries.values.filter { entry ->
            (scope == null || entry.scope == scope) &&
                    (entry.key.lowercase().contains(lower) ||
                            entry.value.lowercase().contains(lower) ||
                            entry.tags.any { it.lowercase().contains(lower) })
        }
    }

    fun listByScope(scope: MemoryScope): List<MemoryEntry> =
        entries.values.filter { it.scope == scope }

    fun clearScope(scope: MemoryScope, scopeIdentifier: String? = null): Int {
        val toRemove = entries.values.filter {
            it.scope == scope && (scopeIdentifier == null || it.scopeIdentifier == scopeIdentifier)
        }
        toRemove.forEach { entries.remove(it.key) }
        _entriesFlow.value = entries.values.toList().sortedByDescending { it.updatedAt }
        return toRemove.size
    }

    fun formatReport(scope: MemoryScope? = null): String = buildString {
        appendLine("=== GVONE OS MEMORY STORE ===")
        val filtered = if (scope != null) listByScope(scope) else entries.values.toList()
        if (filtered.isEmpty()) {
            appendLine("No memory entries found.")
        } else {
            filtered.forEach {
                appendLine("[${it.scope.name}] ${it.key} = \"${it.value.take(80)}\" (accesses=${it.accessCount})")
            }
        }
    }.trim()

    companion object {
        val global: MemoryStore by lazy { MemoryStore() }
    }
}
