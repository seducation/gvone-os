package com.example.data.terminal

import androidx.compose.runtime.Immutable
import java.util.UUID

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

enum class CommandOrigin {
    ADDRESS_BAR,
    TERMINAL,
    CNS_DASHBOARD,
    SHORTCUT,
    AUTOMATION
}

@Immutable
data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.OUTPUT,
    val timestamp: Long = System.currentTimeMillis(),
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
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

@Immutable
data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Session",
    val lines: List<TerminalLine> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastActiveAt: Long = System.currentTimeMillis()
)
