package com.example.agent.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Structured UI events emitted by the Agent System / CNS.
 * The browser UI observes these to display non-intrusive sheets, toasts, overlays, or status chips.
 */
sealed interface AgentUiEvent {
    data class ShowAgentStatus(val agentName: String, val statusText: String, val isWorking: Boolean) : AgentUiEvent
    data class ShowPermissionRequest(val requestId: String, val agentName: String, val permission: String, val rationale: String) : AgentUiEvent
    data class ShowReviewPanel(val title: String, val content: String, val actions: List<String>) : AgentUiEvent
    data class ShowFileResult(val fileName: String, val previewText: String, val mimeType: String) : AgentUiEvent
    data class ShowProgress(val agentName: String, val progressFraction: Float, val statusMessage: String) : AgentUiEvent
    data class ShowError(val agentName: String, val errorMessage: String, val canRetry: Boolean) : AgentUiEvent
}

class AgentUiBridge {
    private val _events = MutableSharedFlow<AgentUiEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AgentUiEvent> = _events.asSharedFlow()

    suspend fun emit(event: AgentUiEvent) {
        _events.emit(event)
    }

    companion object {
        val global = AgentUiBridge()
    }
}
