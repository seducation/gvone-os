package com.example.data.terminal

import android.content.Context
import com.example.data.files.GVONEFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TerminalCommandExecutor(
    private val context: Context,
    private val fileSystem: GVONEFileSystem,
    private val scope: CoroutineScope
) {
    fun execute(
        input: String,
        appendLine: (TerminalLine) -> Unit,
        clearLines: () -> Unit
    ) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return

        appendLine(TerminalLine(text = "gvone@terminal:~$ $trimmed", type = TerminalLineType.COMMAND))

        val parts = trimmed.split(Regex("\\s+"))
        val command = parts.firstOrNull()?.lowercase() ?: ""
        val args = parts.drop(1)

        when (command) {
            "help" -> {
                appendLine(
                    TerminalLine(
                        text = """
                            GVONE Multimodal Terminal Commands:
                              /receive <url|path|data> [filename]  Receive, save, and display image preview
                              /download <url> [filename]          Download and render binary image file
                              ls [path]                           List local GVONE files
                              clear                               Clear terminal screen
                              help                                Display this help menu
                        """.trimIndent(),
                        type = TerminalLineType.INFO
                    )
                )
            }

            "clear" -> {
                clearLines()
            }

            "ls" -> {
                val relPath = args.firstOrNull() ?: ""
                val files = fileSystem.listFiles(relPath)
                if (files.isEmpty()) {
                    appendLine(TerminalLine(text = "Directory is empty.", type = TerminalLineType.SYSTEM))
                } else {
                    val formatted = files.joinToString("\n") { file ->
                        val prefix = if (file.isDirectory) "[DIR] " else "      "
                        "$prefix${file.name}"
                    }
                    appendLine(TerminalLine(text = formatted, type = TerminalLineType.OUTPUT))
                }
            }

            "/receive", "/download", "receive", "download" -> {
                val source = args.firstOrNull()
                if (source == null) {
                    appendLine(
                        TerminalLine(
                            text = "Usage: /receive <url|path|data_uri> [custom_filename]",
                            type = TerminalLineType.WARNING
                        )
                    )
                    return
                }

                val customName = if (args.size > 1) args.subList(1, args.size).joinToString("_") else null

                appendLine(
                    TerminalLine(
                        text = "[System] Downloading and processing image payload from: $source ...",
                        type = TerminalLineType.INFO
                    )
                )

                scope.launch(Dispatchers.IO) {
                    val result = fileSystem.receiveAndSaveImage(
                        sourceUriOrPathOrUrl = source,
                        customFileName = customName
                    )

                    if (result.success) {
                        appendLine(
                            TerminalLine(
                                text = "Successfully saved ${result.name} (${result.formattedSize()})",
                                type = TerminalLineType.IMAGE_PREVIEW,
                                imageUri = result.fileUri,
                                fileUri = result.fileUri,
                                fileName = result.name,
                                fileMimeType = result.mimeType,
                                fileSize = result.sizeBytes,
                                imageWidth = result.width,
                                imageHeight = result.height
                            )
                        )
                        appendLine(
                            TerminalLine(
                                text = result.toStructuredJson(),
                                type = TerminalLineType.SYSTEM
                            )
                        )
                    } else {
                        appendLine(
                            TerminalLine(
                                text = "[Error] Failed to receive image: ${result.error}",
                                type = TerminalLineType.ERROR
                            )
                        )
                    }
                }
            }

            else -> {
                // If user enters an image URL directly without command prefix
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    val ext = trimmed.substringAfterLast('.', "").lowercase()
                    if (ext in listOf("png", "jpg", "jpeg", "webp", "gif", "svg")) {
                        appendLine(TerminalLine(text = "[Auto-Detect] Image URL detected. Receiving image payload...", type = TerminalLineType.INFO))
                        scope.launch(Dispatchers.IO) {
                            val result = fileSystem.receiveAndSaveImage(trimmed)
                            if (result.success) {
                                appendLine(
                                    TerminalLine(
                                        text = "Saved ${result.name} to ~/Downloads/",
                                        type = TerminalLineType.IMAGE_PREVIEW,
                                        imageUri = result.fileUri,
                                        fileUri = result.fileUri,
                                        fileName = result.name,
                                        fileMimeType = result.mimeType,
                                        fileSize = result.sizeBytes,
                                        imageWidth = result.width,
                                        imageHeight = result.height
                                    )
                                )
                            } else {
                                appendLine(TerminalLine(text = "Failed to load image URL: ${result.error}", type = TerminalLineType.ERROR))
                            }
                        }
                        return
                    }
                }

                appendLine(
                    TerminalLine(
                        text = "gvone: command not found: $command. Type 'help' for available commands.",
                        type = TerminalLineType.ERROR
                    )
                )
            }
        }
    }
}
