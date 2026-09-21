package com.example.data.files

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

/**
 * Version control representation for a commit in a specific project.
 */
data class ProjectCommit(
    val hash: String,
    val message: String,
    val description: String = "",
    val branch: String = "main",
    val author: String = "developer",
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String = "",
    val changedFiles: List<String> = emptyList()
) {
    val shortHash: String get() = hash.take(7)
    val formattedDate: String get() = formattedTime.ifBlank { "Recently" }
}

/**
 * Git working tree status of a file inside a project.
 */
enum class GitFileStatus {
    UNMODIFIED,
    MODIFIED,
    UNTRACKED,
    DELETED
}

/**
 * Summary of a project's version control repository.
 */
data class ProjectRepoInfo(
    val projectName: String,
    val projectPath: String, // e.g. "Projects/MyReactApp"
    val description: String = "",
    val currentBranch: String = "main",
    val branches: List<String> = listOf("main"),
    val commits: List<ProjectCommit> = emptyList(),
    val isGitInitialized: Boolean = true,
    val uncommittedFilesCount: Int = 0
)

/**
 * Robust Version Control Manager for GVONE projects.
 * Supports multiple distinct projects with independent commit histories,
 * branches, working tree change tracking, and file status indicators.
 */
class GVONEGitManager(private val context: Context, private val rootDir: File) {

    private val prefs: SharedPreferences = context.getSharedPreferences("gvone_git_repos", Context.MODE_PRIVATE)

    /**
     * Resolves which project a relative file/folder path belongs to.
     * E.g. "Projects/MySoftware/src/Main.kt" -> "Projects/MySoftware" (Project: "MySoftware")
     */
    fun resolveProjectRoot(relativePath: String): String? {
        val clean = relativePath.trim('/').replace('\\', '/')
        if (clean.isBlank()) return null

        val parts = clean.split('/')
        return when {
            // Under Projects folder: Projects/<ProjectName>/...
            parts.size >= 2 && parts[0].equals("Projects", ignoreCase = true) -> {
                "${parts[0]}/${parts[1]}"
            }
            // Root-level folder treated as project
            parts.isNotEmpty() && !parts[0].equals("Downloads", ignoreCase = true) && !parts[0].equals("Cloud", ignoreCase = true) -> {
                parts[0]
            }
            else -> null
        }
    }

    /**
     * Extracts user-friendly project display name.
     */
    fun getProjectDisplayName(projectPath: String): String {
        return projectPath.trimEnd('/').substringAfterLast('/')
    }

    /**
     * Lists all projects discovered in the file system.
     */
    suspend fun listProjects(): List<ProjectRepoInfo> = withContext(Dispatchers.IO) {
        val projects = mutableListOf<ProjectRepoInfo>()
        val projectsDir = File(rootDir, "Projects")

        if (projectsDir.exists() && projectsDir.isDirectory) {
            projectsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { dir ->
                val relPath = "Projects/${dir.name}"
                projects.add(getProjectRepoInfo(relPath))
            }
        }

        // Also check root folders if any user created top-level project folders
        rootDir.listFiles()?.filter { 
            it.isDirectory && 
            !it.name.startsWith(".") && 
            !it.name.equals("Projects", ignoreCase = true) &&
            !it.name.equals("Downloads", ignoreCase = true) &&
            !it.name.equals("Cloud", ignoreCase = true) &&
            !it.name.equals("Images", ignoreCase = true) &&
            !it.name.equals("Documents", ignoreCase = true) &&
            !it.name.equals("GVONE", ignoreCase = true)
        }?.forEach { dir ->
            val relPath = dir.name
            projects.add(getProjectRepoInfo(relPath))
        }

        // Ensure default "Projects" folder itself is represented if empty
        if (projects.isEmpty()) {
            val defaultProject = "Projects/DefaultProject"
            val defDir = File(rootDir, defaultProject)
            if (!defDir.exists()) defDir.mkdirs()
            initializeProjectRepo(defaultProject, "Default Workspace Project")
            projects.add(getProjectRepoInfo(defaultProject))
        }

        projects.sortedBy { it.projectName.lowercase(Locale.ROOT) }
    }

    /**
     * Reads repository info for a given project.
     */
    suspend fun getProjectRepoInfo(projectPath: String): ProjectRepoInfo = withContext(Dispatchers.IO) {
        val projectName = getProjectDisplayName(projectPath)
        val prefKey = "repo_$projectPath"
        val rawJson = prefs.getString(prefKey, null)

        if (rawJson == null) {
            // Auto initialize starter git repo for this project
            return@withContext initializeProjectRepo(projectPath, "Initial project structure")
        }

        try {
            val obj = JSONObject(rawJson)
            val currentBranch = obj.optString("currentBranch", "main")
            val branchesArr = obj.optJSONArray("branches") ?: JSONArray().apply { put("main") }
            val branches = mutableListOf<String>()
            for (i in 0 until branchesArr.length()) {
                branches.add(branchesArr.getString(i))
            }
            if (!branches.contains("main")) branches.add(0, "main")

            val commitsArr = obj.optJSONArray("commits") ?: JSONArray()
            val commits = mutableListOf<ProjectCommit>()
            for (i in 0 until commitsArr.length()) {
                val cObj = commitsArr.getJSONObject(i)
                val changedArr = cObj.optJSONArray("changedFiles") ?: JSONArray()
                val changedList = mutableListOf<String>()
                for (j in 0 until changedArr.length()) {
                    changedList.add(changedArr.getString(j))
                }

                commits.add(
                    ProjectCommit(
                        hash = cObj.optString("hash", "1a2b3c4"),
                        message = cObj.optString("message", "Commit update"),
                        description = cObj.optString("description", ""),
                        branch = cObj.optString("branch", "main"),
                        author = cObj.optString("author", "developer"),
                        timestamp = cObj.optLong("timestamp", System.currentTimeMillis()),
                        formattedTime = cObj.optString("formattedTime", "Recently"),
                        changedFiles = changedList
                    )
                )
            }

            val uncommitted = calculateUncommittedChanges(projectPath)
            val description = obj.optString("description", "")

            ProjectRepoInfo(
                projectName = projectName,
                projectPath = projectPath,
                description = description,
                currentBranch = currentBranch,
                branches = branches.distinct(),
                commits = commits.sortedByDescending { it.timestamp },
                isGitInitialized = true,
                uncommittedFilesCount = uncommitted.size
            )
        } catch (_: Exception) {
            initializeProjectRepo(projectPath, "Initial project structure")
        }
    }

    /**
     * Initializes git repository tracking for a project.
     */
    suspend fun initializeProjectRepo(projectPath: String, initialMessage: String = "Initial repository commit"): ProjectRepoInfo = withContext(Dispatchers.IO) {
        val projectName = getProjectDisplayName(projectPath)
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val hash = generateShortHash("$projectPath-init-$now")

        val initialCommit = ProjectCommit(
            hash = hash,
            message = initialMessage,
            description = "Initialized version controlled project $projectName",
            branch = "main",
            author = "developer",
            timestamp = now,
            formattedTime = sdf.format(Date(now)),
            changedFiles = listOf("README.md", ".gitignore")
        )

        val info = ProjectRepoInfo(
            projectName = projectName,
            projectPath = projectPath,
            currentBranch = "main",
            branches = listOf("main", "develop"),
            commits = listOf(initialCommit),
            isGitInitialized = true,
            uncommittedFilesCount = 0
        )

        saveProjectRepoInfo(info)
        info
    }

    /**
     * Saves repository state to persistent storage.
     */
    private fun saveProjectRepoInfo(info: ProjectRepoInfo) {
        val prefKey = "repo_${info.projectPath}"
        val obj = JSONObject().apply {
            put("projectName", info.projectName)
            put("projectPath", info.projectPath)
            put("description", info.description)
            put("currentBranch", info.currentBranch)

            val bArr = JSONArray()
            info.branches.forEach { bArr.put(it) }
            put("branches", bArr)

            val cArr = JSONArray()
            info.commits.forEach { c ->
                val cObj = JSONObject().apply {
                    put("hash", c.hash)
                    put("message", c.message)
                    put("description", c.description)
                    put("branch", c.branch)
                    put("author", c.author)
                    put("timestamp", c.timestamp)
                    put("formattedTime", c.formattedTime)

                    val filesArr = JSONArray()
                    c.changedFiles.forEach { filesArr.put(it) }
                    put("changedFiles", filesArr)
                }
                cArr.put(cObj)
            }
            put("commits", cArr)
        }

        prefs.edit().putString(prefKey, obj.toString()).apply()
    }

    /**
     * Records a new commit to a project's repository.
     */
    suspend fun commitChanges(
        projectPath: String,
        message: String,
        description: String = "",
        branch: String? = null,
        files: List<String> = emptyList()
    ): ProjectCommit = withContext(Dispatchers.IO) {
        val repo = getProjectRepoInfo(projectPath)
        val targetBranch = branch ?: repo.currentBranch
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        val now = System.currentTimeMillis()
        val hash = generateShortHash("$projectPath-$message-$now")

        val affectedFiles = if (files.isNotEmpty()) {
            files
        } else {
            calculateUncommittedChanges(projectPath).map { it.name }
        }

        val newCommit = ProjectCommit(
            hash = hash,
            message = message.trim(),
            description = description.trim(),
            branch = targetBranch,
            author = "developer",
            timestamp = now,
            formattedTime = sdf.format(Date(now)),
            changedFiles = if (affectedFiles.isEmpty()) listOf("all project files") else affectedFiles
        )

        val updatedCommits = mutableListOf(newCommit).apply { addAll(repo.commits) }
        val updatedBranches = if (!repo.branches.contains(targetBranch)) {
            repo.branches + targetBranch
        } else {
            repo.branches
        }

        val updatedRepo = repo.copy(
            currentBranch = targetBranch,
            branches = updatedBranches,
            commits = updatedCommits
        )

        saveProjectRepoInfo(updatedRepo)
        // Mark files as clean / recorded
        recordLastCommitTimestamp(projectPath, now)
        newCommit
    }

    /**
     * Switches active branch for a project.
     */
    suspend fun switchBranch(projectPath: String, branchName: String): ProjectRepoInfo = withContext(Dispatchers.IO) {
        val repo = getProjectRepoInfo(projectPath)
        val updatedBranches = if (!repo.branches.contains(branchName)) {
            repo.branches + branchName
        } else {
            repo.branches
        }
        val updated = repo.copy(currentBranch = branchName, branches = updatedBranches)
        saveProjectRepoInfo(updated)
        updated
    }

    /**
     * Creates a new branch for a project and optionally switches to it.
     */
    suspend fun createBranch(projectPath: String, newBranchName: String, switchTo: Boolean = true): ProjectRepoInfo = withContext(Dispatchers.IO) {
        val repo = getProjectRepoInfo(projectPath)
        val cleanBranch = newBranchName.trim().replace(" ", "-").lowercase(Locale.ROOT)
        val updatedBranches = (repo.branches + cleanBranch).distinct()
        val updated = repo.copy(
            currentBranch = if (switchTo) cleanBranch else repo.currentBranch,
            branches = updatedBranches
        )
        saveProjectRepoInfo(updated)
        updated
    }

    /**
     * Determines git status of a specific file item in a project.
     */
    suspend fun getFileGitStatus(relativePath: String): GitFileStatus = withContext(Dispatchers.IO) {
        val projectPath = resolveProjectRoot(relativePath) ?: return@withContext GitFileStatus.UNMODIFIED
        val file = File(rootDir, relativePath)
        if (!file.exists()) return@withContext GitFileStatus.DELETED

        val lastCommitTime = prefs.getLong("last_commit_$projectPath", 0L)
        if (lastCommitTime == 0L) {
            return@withContext GitFileStatus.UNTRACKED
        }

        if (file.lastModified() > lastCommitTime) {
            GitFileStatus.MODIFIED
        } else {
            GitFileStatus.UNMODIFIED
        }
    }

    /**
     * Calculates all uncommitted / modified files inside a project.
     */
    suspend fun calculateUncommittedChanges(projectPath: String): List<File> = withContext(Dispatchers.IO) {
        val projectDir = File(rootDir, projectPath)
        if (!projectDir.exists() || !projectDir.isDirectory) return@withContext emptyList()

        val lastCommitTime = prefs.getLong("last_commit_$projectPath", 0L)
        val uncommitted = mutableListOf<File>()

        fun scan(f: File) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    f.listFiles()?.forEach { scan(it) }
                }
            } else {
                if (!f.name.startsWith(".")) {
                    if (lastCommitTime == 0L || f.lastModified() > lastCommitTime) {
                        uncommitted.add(f)
                    }
                }
            }
        }

        scan(projectDir)
        uncommitted
    }

    private fun recordLastCommitTimestamp(projectPath: String, timestamp: Long) {
        prefs.edit().putLong("last_commit_$projectPath", timestamp).apply()
    }

    private fun generateShortHash(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(input.toByteArray())
            bytes.take(4).joinToString("") { "%02x".format(it) }.take(7)
        } catch (_: Exception) {
            UUID.randomUUID().toString().replace("-", "").take(7)
        }
    }
}
