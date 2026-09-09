package com.example.agent.memory

import com.example.agent.core.Provenance
import java.util.UUID

/**
 * An episode of interaction or task execution stored in episodic memory.
 */
data class EpisodicMemory(
    val id: String = UUID.randomUUID().toString(),
    val taskDescription: String,
    val agentName: String,
    val outcomeSummary: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    var strength: Double = 1.0 // decays over time
)

/**
 * Fact or conceptual knowledge stored in semantic memory.
 */
data class SemanticMemory(
    val key: String,
    val value: String,
    val category: String,
    val provenance: Provenance? = null,
    val confidence: Double = 1.0,
    var accessCount: Int = 1,
    var lastAccessed: Long = System.currentTimeMillis()
)

/**
 * Procedural skill or workflow template stored in procedural memory.
 */
data class ProceduralMemory(
    val procedureName: String,
    val requiredAgents: List<String>,
    val steps: List<String>,
    val successRate: Double = 1.0
)

/**
 * Memory subsystem providing episodic, semantic, and procedural storage with retrieval and decay.
 */
class AgentMemory {
    private val episodic = mutableListOf<EpisodicMemory>()
    private val semantic = mutableMapOf<String, SemanticMemory>()
    private val procedural = mutableMapOf<String, ProceduralMemory>()

    @Synchronized
    fun recordEpisode(task: String, agent: String, outcome: String, success: Boolean) {
        episodic.add(
            EpisodicMemory(
                taskDescription = task,
                agentName = agent,
                outcomeSummary = outcome,
                success = success
            )
        )
    }

    @Synchronized
    fun getRecentEpisodes(limit: Int = 10): List<EpisodicMemory> {
        return episodic.sortedByDescending { it.timestamp }.take(limit)
    }

    @Synchronized
    fun storeFact(key: String, value: String, category: String, provenance: Provenance? = null) {
        semantic[key] = SemanticMemory(
            key = key,
            value = value,
            category = category,
            provenance = provenance
        )
    }

    @Synchronized
    fun recallFact(key: String): SemanticMemory? {
        val mem = semantic[key] ?: return null
        mem.accessCount++
        mem.lastAccessed = System.currentTimeMillis()
        return mem
    }

    @Synchronized
    fun searchFacts(query: String): List<SemanticMemory> {
        val q = query.lowercase()
        return semantic.values.filter {
            it.key.lowercase().contains(q) || it.value.lowercase().contains(q)
        }
    }

    @Synchronized
    fun registerProcedure(procedure: ProceduralMemory) {
        procedural[procedure.procedureName] = procedure
    }

    @Synchronized
    fun getProcedure(name: String): ProceduralMemory? {
        return procedural[name]
    }

    /**
     * Simulates cognitive forgetting / decay of stale episodic memories.
     */
    @Synchronized
    fun applyDecay(decayFactor: Double = 0.9) {
        val iterator = episodic.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            item.strength *= decayFactor
            if (item.strength < 0.2) {
                iterator.remove()
            }
        }
    }

    companion object {
        val global = AgentMemory()
    }
}
