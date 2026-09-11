package com.example.agent.specialized

import com.example.agent.core.AgentBase
import com.example.agent.core.AgentCapability
import com.example.agent.core.AgentRequest
import com.example.agent.core.AgentResult
import com.example.agent.core.AgentStatus
import com.example.agent.core.CancellationToken
import com.example.agent.core.StepLogger
import com.example.agent.core.StepType
import com.example.agent.runtime.RuntimeStateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceSessionState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

/**
 * VoiceAgent manages speech conversational interaction.
 * When invoked via /voice, it enters a continuous conversational interaction loop.
 * Crucially, it does NOT unilaterally initiate autonomous multi-step tasks unless requested.
 */
class VoiceAgent(
    logger: StepLogger = StepLogger.global,
    private val runtimeState: RuntimeStateManager = RuntimeStateManager.global
) : AgentBase("VoiceAgent", logger) {

    private val _voiceSessionState = MutableStateFlow(VoiceSessionState.IDLE)
    val voiceSessionState: StateFlow<VoiceSessionState> = _voiceSessionState.asStateFlow()

    private val _spokenTranscripts = MutableStateFlow<List<String>>(emptyList())
    val spokenTranscripts: StateFlow<List<String>> = _spokenTranscripts.asStateFlow()

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "voice_interaction",
            description = "Handles hands-free voice conversational exchanges and speech status",
            supportedActions = listOf("listen", "speak", "enable_voice", "disable_voice", "toggle_mic")
        ),
        AgentCapability(
            name = "speech_telemetry",
            description = "Provides spoken audio feedback for agent tasks",
            supportedActions = listOf("announce_step", "announce_completion")
        )
    )

    override fun observe(): Map<String, Any?> = mapOf(
        "voiceState" to _voiceSessionState.value.name,
        "isVoiceActive" to runtimeState.runtimeMode.value.isVoiceActive,
        "transcriptsCount" to _spokenTranscripts.value.size
    )

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        return when (request.action.lowercase()) {
            "enable_voice", "activate" -> {
                executeAction(StepType.EXECUTE, "Enable Voice Conversation Mode") {
                    runtimeState.activateVoiceOnly()
                    _voiceSessionState.value = VoiceSessionState.LISTENING
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = "Voice mode enabled. Microphone is active and listening for your conversation."
                    )
                }
            }

            "disable_voice", "deactivate" -> {
                executeAction(StepType.EXECUTE, "Disable Voice Mode") {
                    runtimeState.resetToTextChat()
                    _voiceSessionState.value = VoiceSessionState.IDLE
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = "Voice mode disabled. Returned to text chat."
                    )
                }
            }

            "speak" -> {
                val text = request.parameters["text"]?.toString() ?: "Voice acknowledgment"
                executeAction(StepType.EXECUTE, "Speak utterance: '$text'") {
                    _voiceSessionState.value = VoiceSessionState.SPEAKING
                    _spokenTranscripts.value = _spokenTranscripts.value + "GVONE: $text"
                    _voiceSessionState.value = VoiceSessionState.LISTENING
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("spoken" to text, "success" to true)
                    )
                }
            }

            "announce_completion" -> {
                val taskGoal = request.parameters["goal"]?.toString() ?: "Task"
                val message = "Task '$taskGoal' has completed. You can continue conversation or start another task."
                executeAction(StepType.EXECUTE, "Announce Task Boundary: $taskGoal") {
                    _spokenTranscripts.value = _spokenTranscripts.value + "GVONE [System]: $message"
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = message
                    )
                }
            }

            else -> {
                AgentResult(
                    requestId = request.requestId,
                    status = AgentStatus.FAILED,
                    error = "Unsupported VoiceAgent action: ${request.action}"
                )
            }
        }
    }
}
