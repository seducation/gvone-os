package com.example.agent.core

/**
 * Root structured error interface for the GVONE OS environment.
 */
sealed class GvoneError(
    open val messageText: String,
    open val causeThrowable: Throwable? = null,
    open val actionableResolution: String = ""
) : Exception(messageText, causeThrowable)

data class CommandError(
    val command: String,
    override val messageText: String,
    override val actionableResolution: String = "Check syntax with /help or verify available modifiers."
) : GvoneError("Command error in '$command': $messageText", actionableResolution = actionableResolution)

data class AgentError(
    val agentName: String,
    val action: String,
    override val messageText: String,
    override val actionableResolution: String = "Verify agent permissions or retry with refined goal."
) : GvoneError("Agent '$agentName' failed executing '$action': $messageText", actionableResolution = actionableResolution)

data class ToolError(
    val toolName: String,
    override val messageText: String,
    val exitCode: Int? = null,
    override val actionableResolution: String = "Inspect tool arguments and execution environment."
) : GvoneError("Tool '$toolName' execution error: $messageText", actionableResolution = actionableResolution)

data class TaskError(
    val taskId: String,
    override val messageText: String,
    override val actionableResolution: String = "Check task status using '/task status $taskId'."
) : GvoneError("Task '$taskId' error: $messageText", actionableResolution = actionableResolution)

data class ConfigurationError(
    val key: String,
    override val messageText: String,
    override val actionableResolution: String = "Inspect configuration using '/config list' or set valid value."
) : GvoneError("Configuration error for '$key': $messageText", actionableResolution = actionableResolution)

data class ValidationError(
    val parameter: String,
    override val messageText: String,
    override val actionableResolution: String = "Provide a valid argument according to schema."
) : GvoneError("Validation error for '$parameter': $messageText", actionableResolution = actionableResolution)

data class ContextError(
    val scopeId: String,
    override val messageText: String,
    override val actionableResolution: String = "Reset scope using '/context reset' or check isolation boundaries."
) : GvoneError("Context error in scope '$scopeId': $messageText", actionableResolution = actionableResolution)
