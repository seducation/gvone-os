package com.example.data.terminal

import androidx.compose.runtime.Immutable

enum class TerminalLineType {
    COMMAND,
    OUTPUT,
    SUCCESS,
    ERROR,
    INFO,
    WARNING,
    SYSTEM,
    AI_RESPONSE,
    AGENT_PLAN,
    AGENT_STEP,
    AGENT_THOUGHT,
    AGENT_TOOL,
    EXPANDABLE_TASK,
    IMAGE_PREVIEW,
    FILE_PREVIEW
}

enum class TerminalBridgeMode {
    OFF,
    SHELL,
    WEB
}

@Immutable
data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT,
    val timestamp: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val fileMimeType: String? = null,
    val fileSize: Long? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val isCollapsible: Boolean = false,
    val isExpanded: Boolean = true
)
