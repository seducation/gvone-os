package com.example.agent.specialized

import android.content.Context
import com.example.agent.core.*
import java.io.File

/**
 * FileAgent discovers, reads, and processes local documents and downloaded files.
 */
class FileAgent(
    private val context: Context,
    logger: StepLogger = StepLogger.global
) : AgentBase(name = "FileAgent", logger = logger) {

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "file_system",
            description = "Read local files, list downloads and documents",
            supportedActions = listOf("read_file", "list_files", "extract_text"),
            requiresPermission = true,
            riskLevel = RiskLevel.MEDIUM
        )
    )

    override fun observe(): Map<String, Any?> {
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        return mapOf(
            "downloadsPath" to (downloadsDir?.absolutePath ?: "unavailable"),
            "agent" to name
        )
    }

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        val startTime = System.currentTimeMillis()

        return when (request.action) {
            "read_file" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")
                executeAction(StepType.FETCH, "Read file content from: $path") {
                    val file = File(path)
                    if (!file.exists()) {
                        throw IllegalArgumentException("File does not exist: $path")
                    }
                    val content = file.readText().take(50000)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "path" to path,
                            "size" to file.length(),
                            "content" to content
                        ),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = path, agentName = name)
                    )
                }
            }

            "list_files" -> {
                val dirPath = request.parameters["dir"] as? String
                val baseDir = if (dirPath != null) File(dirPath) else context.filesDir
                executeAction(StepType.FETCH, "List files in: ${baseDir.absolutePath}") {
                    val files = baseDir.listFiles()?.map {
                        mapOf("name" to it.name, "isDirectory" to it.isDirectory, "size" to it.length())
                    } ?: emptyList()
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = files,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "extract_text" -> {
                // In simulated or real file environments, extracts plain text from document
                val rawText = request.parameters["text"] as? String ?: ""
                val fileName = request.parameters["fileName"] as? String ?: "document.txt"
                executeAction(StepType.EXTRACT, "Extract text from $fileName") {
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "fileName" to fileName,
                            "extractedLength" to rawText.length,
                            "sample" to rawText.take(500)
                        ),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = fileName, agentName = name)
                    )
                }
            }

            else -> fail(request, "Unsupported action: ${request.action}")
        }
    }

    private fun fail(request: AgentRequest, msg: String): AgentResult {
        return AgentResult(
            requestId = request.requestId,
            status = AgentStatus.FAILED,
            error = msg
        )
    }
}
