package com.example.agent.world

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class WorldEntity(
    val id: String = UUID.randomUUID().toString(),
    val type: String, // "tab", "file", "agent", "task", "threat", "goal"
    val name: String,
    val attributes: Map<String, Any?> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class WorldSnapshot(
    val activeGoal: String? = null,
    val entities: Map<String, WorldEntity> = emptyMap(),
    val activeAgents: List<String> = emptyList(),
    val activeThreats: List<String> = emptyList(),
    val resourceUsage: Map<String, Double> = emptyMap()
)

/**
 * Shared Virtual World State for the GVONE Organism.
 * Maintained as an immutable snapshot stream updated via controlled commands.
 */
class WorldState {
    private val _snapshot = MutableStateFlow(WorldSnapshot())
    val snapshot: StateFlow<WorldSnapshot> = _snapshot.asStateFlow()

    fun updateEntity(entity: WorldEntity) {
        synchronized(this) {
            val curr = _snapshot.value
            val newEntities = curr.entities.toMutableMap().apply { put(entity.id, entity) }
            _snapshot.value = curr.copy(entities = newEntities)
        }
    }

    fun removeEntity(entityId: String) {
        synchronized(this) {
            val curr = _snapshot.value
            val newEntities = curr.entities.toMutableMap().apply { remove(entityId) }
            _snapshot.value = curr.copy(entities = newEntities)
        }
    }

    fun setActiveGoal(goal: String?) {
        synchronized(this) {
            _snapshot.value = _snapshot.value.copy(activeGoal = goal)
        }
    }

    fun registerActiveAgent(agentName: String) {
        synchronized(this) {
            val curr = _snapshot.value
            if (!curr.activeAgents.contains(agentName)) {
                _snapshot.value = curr.copy(activeAgents = curr.activeAgents + agentName)
            }
        }
    }

    fun recordThreat(threat: String) {
        synchronized(this) {
            val curr = _snapshot.value
            _snapshot.value = curr.copy(activeThreats = curr.activeThreats + threat)
        }
    }

    fun clearThreats() {
        synchronized(this) {
            _snapshot.value = _snapshot.value.copy(activeThreats = emptyList())
        }
    }

    companion object {
        val global = WorldState()
    }
}
