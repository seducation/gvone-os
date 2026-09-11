package com.example.agent.command

/**
 * Parsed invocation of a command with order-independent modifiers, arguments, and options.
 */
data class CommandInvocation(
    val rawInput: String,
    val command: String,
    val modifiers: Set<String> = emptySet(),
    val positionalArgs: List<String> = emptyList(),
    val namedOptions: Map<String, String> = emptyMap(),
    val queryArg: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    fun hasModifier(modifier: String): Boolean {
        val clean = modifier.removePrefix("/").lowercase()
        return modifiers.contains(clean)
    }

    val isVoiceRequested: Boolean
        get() = command.equals("/voice", ignoreCase = true) || hasModifier("voice")

    val isAgenticRequested: Boolean
        get() = command.equals("/agent", ignoreCase = true) || hasModifier("agent")

    val isPersistentOn: Boolean
        get() = hasModifier("on")

    val isPersistentOff: Boolean
        get() = hasModifier("off")

    /**
     * Determines the compound priority between voice and agent:
     * e.g. /voice /agent -> VOICE first (Interaction priority)
     * e.g. /agent /voice -> AGENT first (Execution priority)
     */
    val isVoiceFirst: Boolean
        get() = command.equals("/voice", ignoreCase = true) && hasModifier("agent")

    val isAgentFirst: Boolean
        get() = command.equals("/agent", ignoreCase = true) && hasModifier("voice")
}
