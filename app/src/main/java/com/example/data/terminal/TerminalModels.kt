package com.example.data.terminal

import java.util.UUID

enum class TerminalLineType {
    COMMAND,      // e.g. gvone@browser:~$ /yt lo-fi
    OUTPUT,       // standard output text
    SUCCESS,      // green highlighted output
    ERROR,        // red error output
    INFO,         // cyan / sky blue info text
    WARNING,      // yellow warning
    SYSTEM,       // muted system header or timestamp
    AI_RESPONSE   // AI response text
}

data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT,
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis()
)

data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Session 1",
    val lines: List<TerminalLine> = emptyList(),
    val currentInput: String = "",
    val historyIndex: Int = -1
)
