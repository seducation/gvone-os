package com.example.agent.specialized

import android.content.Context
import com.example.agent.core.*
import com.example.agent.safety.PermissionStatus
import com.example.agent.safety.PermissionSystem
import java.io.File
import java.security.MessageDigest

/**
 * FileAgent discovers, reads, manages, modifies, and validates local documents and workspace files.
 * Enforces permissions for read, write, and destructive delete operations with outcome verification.
 */
class FileAgent(
    private val context: Context? = null,
    logger: StepLogger = StepLogger.global,
    private val permissionSystem: PermissionSystem = PermissionSystem.global
) : AgentBase(name = "FileAgent", logger = logger) {

    private val rootDir: File = (if (context != null) File(context.filesDir, "gvone_fs") else File(System.getProperty("java.io.tmpdir") ?: "/tmp", "gvone_fs")).apply { if (!exists()) mkdirs() }

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "file_system",
            description = "Read, search, write, edit, rename, move, copy, delete, analyze, and transform files",
            supportedActions = listOf(
                "search", "read", "read_file", "create", "create_file",
                "write", "write_file", "edit", "rename", "move", "copy",
                "delete", "analyze", "transform", "list_files", "extract_text"
            ),
            requiresPermission = true,
            riskLevel = RiskLevel.MEDIUM
        )
    )

    override fun observe(): Map<String, Any?> {
        val downloadsDir = context?.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        return mapOf(
            "downloadsPath" to (downloadsDir?.absolutePath ?: "unavailable"),
            "rootPath" to rootDir.absolutePath,
            "totalRootFiles" to (rootDir.listFiles()?.size ?: 0),
            "agent" to name
        )
    }

    private fun resolveFile(path: String): File {
        return if (path.startsWith("/") || path.startsWith("file://")) {
            File(path.removePrefix("file://"))
        } else {
            File(rootDir, path)
        }
    }

    private fun checkActionPermission(action: String): PermissionStatus {
        val perm = when (action.lowercase()) {
            "delete", "delete_file" -> PermissionSystem.PERM_FILESYSTEM_DELETE
            "create", "create_file", "write", "write_file", "edit", "rename", "move", "copy", "transform" ->
                PermissionSystem.PERM_FILESYSTEM_WRITE
            else -> PermissionSystem.PERM_FILESYSTEM_READ
        }
        val result = permissionSystem.checkPermission(perm, name, isHeadless = true)
        return result.status
    }

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        val startTime = System.currentTimeMillis()
        val action = request.action.lowercase()

        val permStatus = checkActionPermission(action)
        if (permStatus != PermissionStatus.GRANTED) {
            val msg = if (permStatus == PermissionStatus.REQUIRES_CONFIRMATION) {
                "Permission required: Destructive file action '$action' requires explicit confirmation."
            } else {
                "Permission denied: Action '$action' is not permitted by system policy."
            }
            return fail(request, msg)
        }

        return when (action) {
            "search" -> {
                val query = request.parameters["query"] as? String
                    ?: return fail(request, "Missing 'query' parameter")
                val dirPath = request.parameters["dir"] as? String
                val baseDir = if (dirPath != null) resolveFile(dirPath) else rootDir

                executeAction(StepType.FETCH, "Search files for '$query' in ${baseDir.path}") {
                    val matchingFiles = mutableListOf<Map<String, Any?>>()
                    baseDir.walkTopDown().maxDepth(6).forEach { file ->
                        val nameMatches = file.name.contains(query, ignoreCase = true)
                        var contentMatches = false
                        if (file.isFile && file.length() < 2 * 1024 * 1024) {
                            try {
                                val text = file.readText()
                                if (text.contains(query, ignoreCase = true)) contentMatches = true
                            } catch (_: Exception) {}
                        }
                        if (nameMatches || contentMatches) {
                            matchingFiles.add(
                                mapOf(
                                    "path" to file.absolutePath,
                                    "name" to file.name,
                                    "isDirectory" to file.isDirectory,
                                    "size" to file.length(),
                                    "matchedContent" to contentMatches
                                )
                            )
                        }
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "query" to query,
                            "matchCount" to matchingFiles.size,
                            "results" to matchingFiles
                        ),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = baseDir.absolutePath, agentName = name)
                    )
                }
            }

            "read", "read_file" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")
                executeAction(StepType.FETCH, "Read file: $path") {
                    val file = resolveFile(path)
                    if (!file.exists()) {
                        throw IllegalArgumentException("File does not exist: $path")
                    }
                    val content = file.readText().take(50000)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "path" to file.absolutePath,
                            "size" to file.length(),
                            "content" to content
                        ),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = file.absolutePath, agentName = name)
                    )
                }
            }

            "create", "create_file" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")
                val content = request.parameters["content"] as? String ?: ""

                executeAction(StepType.MODIFY, "Create file: $path") {
                    val file = resolveFile(path)
                    file.parentFile?.mkdirs()
                    file.writeText(content)

                    // Verify mutation
                    if (!file.exists() || !file.isFile) {
                        throw IllegalStateException("Verification failed: File was not created at ${file.absolutePath}")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "path" to file.absolutePath,
                            "size" to file.length(),
                            "created" to true,
                            "verified" to true
                        ),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = file.absolutePath, agentName = name)
                    )
                }
            }

            "write", "write_file" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")
                val content = request.parameters["content"] as? String ?: ""

                executeAction(StepType.MODIFY, "Write file: $path") {
                    val file = resolveFile(path)
                    file.parentFile?.mkdirs()
                    file.writeText(content)

                    // Verify mutation
                    if (!file.exists() || file.length() != content.toByteArray().size.toLong()) {
                        throw IllegalStateException("Verification failed: File write mismatch at ${file.absolutePath}")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "path" to file.absolutePath,
                            "size" to file.length(),
                            "verified" to true
                        ),
                        durationMs = System.currentTimeMillis() - startTime,
                        provenance = Provenance(origin = file.absolutePath, agentName = name)
                    )
                }
            }

            "edit" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")
                val appendContent = request.parameters["append"] as? String
                val replaceOld = request.parameters["replaceOld"] as? String
                val replaceNew = request.parameters["replaceNew"] as? String

                executeAction(StepType.MODIFY, "Edit file: $path") {
                    val file = resolveFile(path)
                    if (!file.exists()) {
                        throw IllegalArgumentException("File does not exist to edit: $path")
                    }
                    var current = file.readText()
                    if (replaceOld != null && replaceNew != null) {
                        current = current.replace(replaceOld, replaceNew)
                    }
                    if (appendContent != null) {
                        current += appendContent
                    }
                    file.writeText(current)

                    // Verify
                    val updated = file.readText()
                    if (appendContent != null && !updated.contains(appendContent)) {
                        throw IllegalStateException("Verification failed: Appended content not found in ${file.name}")
                    }
                    if (replaceNew != null && !updated.contains(replaceNew)) {
                        throw IllegalStateException("Verification failed: Replaced content not found in ${file.name}")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "path" to file.absolutePath,
                            "newSize" to file.length(),
                            "verified" to true
                        ),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "rename" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")
                val newName = request.parameters["newName"] as? String
                    ?: return fail(request, "Missing 'newName' parameter")

                executeAction(StepType.MODIFY, "Rename file $path to $newName") {
                    val file = resolveFile(path)
                    if (!file.exists()) throw IllegalArgumentException("Source file does not exist: $path")
                    val dest = File(file.parentFile, newName)
                    val success = file.renameTo(dest)

                    // Verify mutation
                    if (!success || !dest.exists() || file.exists()) {
                        throw IllegalStateException("Verification failed: File rename to $newName failed.")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("oldPath" to file.absolutePath, "newPath" to dest.absolutePath, "verified" to true),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "move" -> {
                val sourcePath = request.parameters["source"] as? String ?: request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'source' parameter")
                val targetDir = request.parameters["targetDir"] as? String ?: request.parameters["dest"] as? String
                    ?: return fail(request, "Missing 'targetDir' parameter")

                executeAction(StepType.MODIFY, "Move $sourcePath to $targetDir") {
                    val source = resolveFile(sourcePath)
                    if (!source.exists()) throw IllegalArgumentException("Source file does not exist: $sourcePath")
                    val targetDirectory = resolveFile(targetDir)
                    targetDirectory.mkdirs()
                    val dest = File(targetDirectory, source.name)

                    val success = source.renameTo(dest)
                    if (!success) {
                        source.copyRecursively(dest, overwrite = true)
                        source.deleteRecursively()
                    }

                    // Verify
                    if (!dest.exists() || source.exists()) {
                        throw IllegalStateException("Verification failed: File move to ${dest.absolutePath} failed.")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("source" to source.absolutePath, "dest" to dest.absolutePath, "verified" to true),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "copy" -> {
                val sourcePath = request.parameters["source"] as? String ?: request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'source' parameter")
                val targetDir = request.parameters["targetDir"] as? String ?: request.parameters["dest"] as? String
                    ?: return fail(request, "Missing 'targetDir' parameter")

                executeAction(StepType.MODIFY, "Copy $sourcePath to $targetDir") {
                    val source = resolveFile(sourcePath)
                    if (!source.exists()) throw IllegalArgumentException("Source does not exist: $sourcePath")
                    val targetDirectory = resolveFile(targetDir)
                    targetDirectory.mkdirs()
                    val dest = File(targetDirectory, source.name)

                    source.copyRecursively(dest, overwrite = true)

                    // Verify
                    if (!dest.exists() || (source.isFile && dest.length() != source.length())) {
                        throw IllegalStateException("Verification failed: Copied file does not match source.")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("source" to source.absolutePath, "dest" to dest.absolutePath, "verified" to true),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "delete", "delete_file" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")

                executeAction(StepType.MODIFY, "Delete file: $path") {
                    val file = resolveFile(path)
                    if (!file.exists()) {
                        return@executeAction AgentResult(
                            requestId = request.requestId,
                            status = AgentStatus.COMPLETED,
                            data = mapOf("path" to file.absolutePath, "deleted" to false, "message" to "File did not exist"),
                            durationMs = System.currentTimeMillis() - startTime
                        )
                    }

                    val success = file.deleteRecursively()
                    // Verify mutation
                    if (!success || file.exists()) {
                        throw IllegalStateException("Verification failed: File at ${file.absolutePath} still exists after delete.")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("path" to file.absolutePath, "deleted" to true, "verified" to true),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "analyze" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")

                executeAction(StepType.ANALYZE, "Analyze file: $path") {
                    val file = resolveFile(path)
                    if (!file.exists()) throw IllegalArgumentException("File does not exist: $path")

                    val isDir = file.isDirectory
                    val size = file.length()
                    var lines = 0
                    var words = 0
                    var md5 = "N/A"

                    if (!isDir && size < 10 * 1024 * 1024) {
                        val text = file.readText()
                        lines = text.lines().size
                        words = text.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
                        val digest = MessageDigest.getInstance("MD5").digest(file.readBytes())
                        md5 = digest.joinToString("") { "%02x".format(it) }
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "path" to file.absolutePath,
                            "name" to file.name,
                            "isDirectory" to isDir,
                            "size" to size,
                            "lineCount" to lines,
                            "wordCount" to words,
                            "extension" to file.extension,
                            "md5" to md5
                        ),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "transform" -> {
                val path = request.parameters["path"] as? String
                    ?: return fail(request, "Missing 'path' parameter")
                val operation = request.parameters["operation"] as? String ?: "uppercase"

                executeAction(StepType.MODIFY, "Transform file $path with operation $operation") {
                    val file = resolveFile(path)
                    if (!file.exists() || !file.isFile) throw IllegalArgumentException("File not found or is directory: $path")

                    val original = file.readText()
                    val transformed = when (operation.lowercase()) {
                        "uppercase" -> original.uppercase()
                        "lowercase" -> original.lowercase()
                        "trim" -> original.lines().joinToString("\n") { it.trim() }
                        "sort_lines" -> original.lines().sorted().joinToString("\n")
                        "reverse_lines" -> original.lines().reversed().joinToString("\n")
                        else -> original
                    }

                    file.writeText(transformed)

                    // Verify mutation
                    val readBack = file.readText()
                    if (readBack != transformed) {
                        throw IllegalStateException("Verification failed: Transformed content was not properly written.")
                    }

                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf(
                            "path" to file.absolutePath,
                            "operation" to operation,
                            "originalLength" to original.length,
                            "transformedLength" to transformed.length,
                            "verified" to true
                        ),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "list_files" -> {
                val dirPath = request.parameters["dir"] as? String
                val baseDir = if (dirPath != null) resolveFile(dirPath) else rootDir
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

