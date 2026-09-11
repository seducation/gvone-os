package com.example.agent.command

import com.example.agent.core.ContextPolicy
import com.example.agent.runtime.ExecutionType

/**
 * Specification of an argument accepted by a command.
 */
data class CommandArgumentDefinition(
    val name: String,
    val description: String,
    val isRequired: Boolean = false,
    val defaultValue: String? = null
)

/**
 * Specification of an option/flag accepted by a command (e.g. /on, /off, /agent, -v).
 */
data class CommandOptionDefinition(
    val name: String,
    val flag: String,
    val description: String,
    val acceptsValue: Boolean = false
)

/**
 * Output presentation policy for a command.
 */
enum class OutputPolicy {
    TERMINAL_AND_UI,  // Output to both terminal buffer and UI state
    TERMINAL_ONLY,    // Output solely to terminal
    STREAM,           // Real-time streaming output
    SILENT            // Silent execution without terminal spam
}

/**
 * Comprehensive definition for a command in GVONE OS.
 */
data class CommandDefinition(
    val command: String,
    val description: String,
    val aliases: List<String> = emptyList(),
    val arguments: List<CommandArgumentDefinition> = emptyList(),
    val options: List<CommandOptionDefinition> = emptyList(),
    val permissions: List<String> = emptyList(),
    val agentBinding: String? = null,
    val executionMode: ExecutionType = ExecutionType.CHAT,
    val contextPolicy: ContextPolicy = ContextPolicy.STRICT_ISOLATED,
    val outputPolicy: OutputPolicy = OutputPolicy.TERMINAL_AND_UI,
    val isBuiltIn: Boolean = true,
    val isEnabled: Boolean = true,
    val handler: suspend (invocation: CommandInvocation) -> CommandResult
)
