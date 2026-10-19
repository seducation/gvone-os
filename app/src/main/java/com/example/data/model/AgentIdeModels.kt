package com.example.data.model

import java.util.UUID

/**
 * Target workspace scoping model for IDE operations.
 * Binds terminal shell, file system operations, and Agent execution to this directory.
 */
data class TargetDirectory(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val absolutePath: String,
    val isProjectRoot: Boolean = false,
    val fileCount: Int = 0
)

/**
 * High-level agent step types for autonomous execution.
 */
enum class AgentStepType(val displayName: String, val iconLabel: String) {
    PLAN("Plan & Architecture", "📐"),
    CREATE_FILE("File Generation", "📝"),
    EDIT_FILE("File Modification", "✏️"),
    RUN_COMMAND("Command Execution", "⚡"),
    BUG_CHECK("Syntax & Bug Check", "🔍")
}

/**
 * Status of an individual agent step in the execution pipeline.
 */
enum class AgentStepStatus(val displayName: String) {
    PENDING("Pending"),
    RUNNING("Running..."),
    AWAITING_APPROVAL("Awaiting Approval"),
    COMPLETED("Completed"),
    FAILED("Failed")
}

/**
 * Agent task step with inspection, diff preview, and approval gates.
 */
data class AgentTaskStep(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val type: AgentStepType,
    val status: AgentStepStatus = AgentStepStatus.PENDING,
    val targetFile: String? = null,
    val codeDiff: String? = null,
    val shellCommand: String? = null,
    val approvalRequired: Boolean = false,
    val generatedContent: String? = null,
    val targetDirectory: String = "workspace"
)

/**
 * Interactive AI question prompt in the Build/Command panel.
 */
data class AgentQuestionPrompt(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val options: List<String>,
    val recommendedIndex: Int = 0,
    val isMultiSelect: Boolean = false,
    val onAnswerSelected: (String) -> Unit = {}
)

/**
 * Real-time execution status displayed in the Floating Address Bar & Terminal status pill.
 */
enum class TerminalExecutionStatus(val displayName: String) {
    IDLE("Ready"),
    RUNNING("Running..."),
    AWAITING_APPROVAL("Approval Required"),
    ERROR("Execution Error")
}

/**
 * Single file write operation produced by multi-file autonomous generation.
 */
data class FileWriteOperation(
    val relativePath: String,
    val content: String,
    val description: String,
    val isNewFile: Boolean = true
)
