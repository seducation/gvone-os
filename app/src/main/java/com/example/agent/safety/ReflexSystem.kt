package com.example.agent.safety

import com.example.agent.core.AgentRequest
import com.example.agent.core.StepLogger
import com.example.agent.core.StepStatus
import com.example.agent.core.StepType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Autonomic reflex response when a hazardous stimulus or action is detected.
 */
data class ReflexResponse(
    val isTriggered: Boolean,
    val threatName: String? = null,
    val reason: String? = null,
    val actionTaken: ReflexAction = ReflexAction.NONE
)

enum class ReflexAction {
    NONE,
    BLOCK_EXECUTION,
    EMERGENCY_FREEZE,
    NEUTRALIZE_PAYLOAD
}

/**
 * Fast deterministic safety layer ("Spinal Cord").
 * Intercepts dangerous patterns, malicious command injections, and nociceptive stimuli
 * in sub-millisecond time BEFORE higher cognitive reasoning or LLM invocations occur.
 */
class ReflexSystem(
    private val logger: StepLogger = StepLogger.global
) {
    private val _isFrozen = MutableStateFlow(false)
    val isFrozen: StateFlow<Boolean> = _isFrozen.asStateFlow()

    private val hazardousPatterns = listOf(
        Regex("(?i)rm\\s+-rf\\s+[/~]"),
        Regex("(?i):\\(\\)\\s*\\{\\s*:\\|:&\\s*\\};:"), // fork bomb
        Regex("(?i)chmod\\s+-R\\s+777\\s+/"),
        Regex("(?i)mkfs(\\.\\w+)?\\s+/dev/"),
        Regex("(?i)dd\\s+if=/dev/zero\\s+of=/dev/"),
        Regex("(?i)drop\\s+database\\s+"),
        Regex("(?i)<script.*?>.*?</script>"), // direct script injection in non-script payload
        Regex("(?i)javascript:\\s*document\\.cookie"),
        Regex("(?i)eval\\s*\\(\\s*unescape\\s*\\(")
    )

    private val injectionPatterns = listOf(
        Regex("(?i)ignore\\s+(all\\s+)?previous\\s+instructions"),
        Regex("(?i)disregard\\s+(all\\s+)?previous\\s+(directions|instructions)"),
        Regex("(?i)you\\s+are\\s+now\\s+in\\s+developer\\s+debug\\s+mode"),
        Regex("(?i)system\\s+override\\s+grant\\s+admin"),
        Regex("(?i)output\\s+your\\s+(system\\s+prompt|initial\\s+instructions)")
    )

    /**
     * Inspects a request or input for immediate physiological danger.
     */
    fun evaluateRequest(request: AgentRequest): ReflexResponse {
        if (_isFrozen.value) {
            return ReflexResponse(
                isTriggered = true,
                threatName = "SYSTEM_FROZEN",
                reason = "GVONE Reflex System is in Emergency Freeze mode.",
                actionTaken = ReflexAction.EMERGENCY_FREEZE
            )
        }

        // Check command injection in action and parameters
        val targetPayload = request.parameters.values.joinToString(" ") { it?.toString() ?: "" }
        val combinedText = "${request.action} $targetPayload"

        for (pattern in hazardousPatterns) {
            if (pattern.containsMatchIn(combinedText)) {
                triggerEmergencyFreeze("Hazardous OS pattern: ${pattern.pattern}")
                return ReflexResponse(
                    isTriggered = true,
                    threatName = "HAZARDOUS_OS_COMMAND",
                    reason = "Command contains catastrophic pattern: ${pattern.pattern}",
                    actionTaken = ReflexAction.EMERGENCY_FREEZE
                )
            }
        }

        for (pattern in injectionPatterns) {
            if (pattern.containsMatchIn(combinedText)) {
                logger.logInstant(
                    agentName = "ReflexSystem",
                    action = StepType.VALIDATE,
                    target = "Nociceptive Trigger: Prompt Injection Attempt [${pattern.pattern}]",
                    status = StepStatus.FAILED,
                    errorMessage = "Blocked prompt injection attempt"
                )
                return ReflexResponse(
                    isTriggered = true,
                    threatName = "PROMPT_INJECTION_ATTEMPT",
                    reason = "Payload matches known adversarial override signature: ${pattern.pattern}",
                    actionTaken = ReflexAction.BLOCK_EXECUTION
                )
            }
        }

        return ReflexResponse(isTriggered = false)
    }

    /**
     * Triggers emergency spinal freeze across all agent execution.
     */
    fun triggerEmergencyFreeze(reason: String) {
        _isFrozen.value = true
        logger.logInstant(
            agentName = "ReflexSystem",
            action = StepType.ERROR,
            target = "EMERGENCY FREEZE ACTIVATED: $reason",
            status = StepStatus.FAILED
        )
    }

    /**
     * Thaws and unfreezes agent execution.
     */
    fun clearEmergencyFreeze() {
        _isFrozen.value = false
        logger.logInstant(
            agentName = "ReflexSystem",
            action = StepType.DECIDE,
            target = "Emergency freeze deactivated by supervisor",
            status = StepStatus.SUCCESS
        )
    }

    companion object {
        val global = ReflexSystem()
    }
}
