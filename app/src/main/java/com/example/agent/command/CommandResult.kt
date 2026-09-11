package com.example.agent.command

import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType

/**
 * Execution status of a command.
 */
enum class CommandStatus {
    SUCCESS,
    ERROR,
    CANCELLED,
    HANDLED_INTERNALLY
}

/**
 * Result returned by a command handler.
 */
data class CommandResult(
    val status: CommandStatus = CommandStatus.SUCCESS,
    val message: String = "",
    val terminalLines: List<TerminalLine> = emptyList(),
    val data: Any? = null,
    val error: String? = null,
    val openTerminal: Boolean = true
) {
    val isSuccess: Boolean get() = status == CommandStatus.SUCCESS && error == null

    companion object {
        fun success(message: String, openTerminal: Boolean = true, data: Any? = null): CommandResult =
            CommandResult(
                status = CommandStatus.SUCCESS,
                message = message,
                terminalLines = listOf(TerminalLine(message, TerminalLineType.SUCCESS)),
                data = data,
                openTerminal = openTerminal
            )

        fun info(message: String, openTerminal: Boolean = true): CommandResult =
            CommandResult(
                status = CommandStatus.SUCCESS,
                message = message,
                terminalLines = listOf(TerminalLine(message, TerminalLineType.INFO)),
                openTerminal = openTerminal
            )

        fun error(message: String, openTerminal: Boolean = true): CommandResult =
            CommandResult(
                status = CommandStatus.ERROR,
                message = message,
                error = message,
                terminalLines = listOf(TerminalLine(message, TerminalLineType.ERROR)),
                openTerminal = openTerminal
            )

        fun lines(lines: List<TerminalLine>, openTerminal: Boolean = true): CommandResult =
            CommandResult(
                status = CommandStatus.SUCCESS,
                message = lines.firstOrNull()?.text ?: "",
                terminalLines = lines,
                openTerminal = openTerminal
            )
    }
}
