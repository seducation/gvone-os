package com.example.data.files

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Universal Unified File System for GVONE.
 * Provides a shared file layer across Browser Tabs, Terminal CLI,
 * AI Agents, and Connectors.
 */
class GVONEFileSystem(private val context: Context) {

    private val rootDir: File = File(context.filesDir, "gvone_fs")
    private val prefs: SharedPreferences = context.getSharedPreferences("gvone_fs_prefs", Context.MODE_PRIVATE)
    val gitManager: GVONEGitManager = GVONEGitManager(context, rootDir)

    companion object {
        private const val KEY_FAVORITES = "fs_favorites_set"
        private const val KEY_RECENT = "fs_recent_json"
        private const val KEY_CONFIG = "fs_storage_config_json"

        val DEFAULT_FOLDERS = listOf(
            "Documents",
            "Projects",
            "Images",
            "GVONE",
            "Downloads",
            "Cloud"
        )
    }

    init {
        initializeFileSystem()
    }

    /**
     * Initializes the root directory, standard folder hierarchy, starter projects, and files.
     */
    fun initializeFileSystem() {
        try {
            if (!rootDir.exists()) {
                rootDir.mkdirs()
            }

            for (folderName in DEFAULT_FOLDERS) {
                val folder = File(rootDir, folderName)
                if (!folder.exists()) {
                    folder.mkdirs()
                }
            }

            // Seed starter files if Documents is empty
            val docsFolder = File(rootDir, "Documents")
            val notesFile = File(docsFolder, "notes.md")
            if (!notesFile.exists()) {
                notesFile.writeText(
                    """
                    # GVONE Universal File System
                    
                    Welcome to the GVONE integrated file manager and software workspace.
                    This workspace is unified across:
                    - **Browser Tabs**: Open Markdown, Text, Code, JSON, Images, and PDFs in native tabs.
                    - **Terminal CLI**: Access, edit, and manage these files using `ls`, `cat`, `mkdir`, and `touch`.
                    - **Multi-Project VCS**: Create projects with nested subfolders, files, and independent Git version control.
                    - **AI Agents**: Read project files for context and assistance.
                    
                    ## Quick Tips
                    - Tap any project folder to enter its directory tree.
                    - Add subfolders (`src/`, `components/`, `assets/`) and files.
                    - Edit code files and commit changes with the version control engine.
                    """.trimIndent()
                )
            }

            // Seed distinct starter software projects in Projects/
            val projectsFolder = File(rootDir, "Projects")

            // Project 1: Kotlin Calculator
            val kotlinProjDir = File(projectsFolder, "KotlinCalculator")
            if (!kotlinProjDir.exists()) {
                kotlinProjDir.mkdirs()
                val srcDir = File(kotlinProjDir, "src")
                srcDir.mkdirs()
                File(srcDir, "Calculator.kt").writeText(
                    """
                    package com.gvone.calculator
                    
                    class Calculator {
                        fun add(a: Double, b: Double): Double = a + b
                        fun subtract(a: Double, b: Double): Double = a - b
                        fun multiply(a: Double, b: Double): Double = a * b
                        fun divide(a: Double, b: Double): Double = if (b != 0.0) a / b else Double.NaN
                    }
                    
                    fun main() {
                        val calc = Calculator()
                        println("GVONE Calculator Ready: 15 + 27 = ${'$'}{calc.add(15.0, 27.0)}")
                    }
                    """.trimIndent()
                )
                File(kotlinProjDir, "README.md").writeText(
                    """
                    # Kotlin Calculator Project
                    
                    A modular Kotlin utility application running in the GVONE Workspace.
                    
                    ## Project Tree
                    - `src/Calculator.kt`: Core arithmetic functions
                    - `build.gradle.kts`: Gradle build definition
                    - `.gitignore`: Ignored build files
                    """.trimIndent()
                )
                File(kotlinProjDir, "build.gradle.kts").writeText(
                    """
                    plugins {
                        kotlin("jvm") version "2.0.0"
                    }
                    """.trimIndent()
                )
                File(kotlinProjDir, ".gitignore").writeText("build/\n.gradle/\n*.log\n")
            }

            // Project 2: Web Frontend Portfolio
            val webProjDir = File(projectsFolder, "WebPortfolio")
            if (!webProjDir.exists()) {
                webProjDir.mkdirs()
                val srcDir = File(webProjDir, "src")
                val componentsDir = File(srcDir, "components")
                componentsDir.mkdirs()

                File(webProjDir, "index.html").writeText(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Developer Portfolio</title>
                        <link rel="stylesheet" href="src/styles.css">
                    </head>
                    <body>
                        <header>
                            <h1>Software Engineer Portfolio</h1>
                            <p>Crafted in GVONE Universal IDE</p>
                        </header>
                        <main id="app">
                            <section class="card">
                                <h2>Active Projects</h2>
                                <ul id="project-list"></ul>
                            </section>
                        </main>
                        <script src="src/app.js"></script>
                    </body>
                    </html>
                    """.trimIndent()
                )
                File(srcDir, "styles.css").writeText(
                    """
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        background: #0b1120;
                        color: #f1f5f9;
                        padding: 24px;
                    }
                    .card {
                        background: #1e293b;
                        padding: 20px;
                        border-radius: 12px;
                        border: 1px solid #334155;
                    }
                    """.trimIndent()
                )
                File(srcDir, "app.js").writeText(
                    """
                    console.log("WebPortfolio initialized.");
                    const projects = ["KotlinCalculator", "WebPortfolio", "PythonAutomation"];
                    const list = document.getElementById("project-list");
                    if (list) {
                        projects.forEach(p => {
                            const li = document.createElement("li");
                            li.textContent = p;
                            list.appendChild(li);
                        });
                    }
                    """.trimIndent()
                )
                File(componentsDir, "Navbar.js").writeText(
                    """
                    export function renderNavbar() {
                        return `<nav class="navbar"><a href="#">Home</a> | <a href="#projects">Projects</a></nav>`;
                    }
                    """.trimIndent()
                )
                File(webProjDir, "README.md").writeText(
                    """
                    # Web Developer Portfolio
                    
                    Modern HTML/CSS/JS frontend application with component modularity.
                    """.trimIndent()
                )
                File(webProjDir, ".gitignore").writeText("dist/\nnode_modules/\n*.log\n")
            }

            val specsFile = File(docsFolder, "specs.txt")
            if (!specsFile.exists()) {
                specsFile.writeText(
                    """
                    GVONE System Specifications
                    OS: Android (ARM64 / x86_64)
                    Architecture: Unified Shared Storage Layer
                    Bridge: Bidirectional WebSocket/HTTP v2.4
                    Security: Encrypted Sandboxed File Root
                    Root: ${rootDir.absolutePath}
                    """.trimIndent()
                )
            }

            val gvoneFolder = File(rootDir, "GVONE")
            val archMd = File(gvoneFolder, "architecture.md")
            if (!archMd.exists()) {
                archMd.writeText(
                    """
                    # GVONE Architecture: Unified File Layer
                    
                    Unlike standard browsers that only support a download directory,
                    GVONE provides an operating-system-level shared file layer.
                    
                    ```
                    [ GVONE Universal File System (gvone_fs) ]
                                    │
                        ┌───────────┼───────────┐
                        ▼           ▼           ▼
                   [Browser]   [Terminal]   [Connectors]
                    ```
                    
                    This guarantees consistent file operations across mobile,
                    desktop, and future Linux/Flutter migrations.
                    """.trimIndent()
                )
            }

            val downloadsFolder = File(rootDir, "Downloads")
            val samplePdf = File(downloadsFolder, "document.pdf")
            if (!samplePdf.exists()) {
                createSamplePdf(samplePdf)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Creates a valid, real Android PDF document so the PDF viewer can immediately render it.
     */
    private fun createSamplePdf(file: File) {
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                color = AndroidColor.BLACK
                textSize = 20f
                isAntiAlias = true
            }

            val titlePaint = Paint().apply {
                color = AndroidColor.rgb(16, 185, 129)
                textSize = 28f
                isFakeBoldText = true
                isAntiAlias = true
            }

            canvas.drawText("GVONE Document Reader", 50f, 80f, titlePaint)
            paint.textSize = 14f
            canvas.drawText("Native Portable Document Format (PDF) Support", 50f, 120f, paint)
            canvas.drawText("This file is rendered by the built-in GVONE PDF viewer in a tab.", 50f, 150f, paint)
            canvas.drawText("Pages can be browsed, zoomed, and exported easily.", 50f, 180f, paint)

            val linePaint = Paint().apply {
                color = AndroidColor.LTGRAY
                strokeWidth = 2f
            }
            canvas.drawLine(50f, 210f, 545f, 210f, linePaint)
            canvas.drawText("Universal File System • Browser • Terminal • Agent", 50f, 240f, paint)

            doc.finishPage(page)
            FileOutputStream(file).use { out ->
                doc.writeTo(out)
            }
            doc.close()
        } catch (_: Exception) {
            file.writeText("%PDF-1.4\n% Sample GVONE PDF Document")
        }
    }

    /**
     * Lists files based on active location and folder.
     */
    suspend fun listFiles(
        location: StorageLocation,
        currentFolder: String = "",
        searchQuery: String = "",
        showHidden: Boolean = false
    ): List<GVONEFileItem> = withContext(Dispatchers.IO) {
        val favorites = getFavoritePaths()

        val rawList = when (location) {
            StorageLocation.MY_FILES -> {
                val targetDir = if (currentFolder.isBlank()) rootDir else File(rootDir, currentFolder)
                scanDirectory(targetDir, showHidden)
            }
            StorageLocation.PROJECTS -> {
                val targetDir = if (currentFolder.isBlank()) File(rootDir, "Projects") else File(rootDir, currentFolder)
                scanDirectory(targetDir, showHidden)
            }
            StorageLocation.CLOUD -> {
                val cloudDir = File(rootDir, "Cloud")
                scanDirectory(cloudDir, showHidden)
            }
            StorageLocation.DOWNLOADS -> {
                val downloadsDir = File(rootDir, "Downloads")
                scanDirectory(downloadsDir, showHidden)
            }
            StorageLocation.FAVORITES -> {
                favorites.mapNotNull { relPath ->
                    val f = File(rootDir, relPath)
                    if (f.exists()) toFileItem(f, favorites) else null
                }
            }
            StorageLocation.RECENT -> {
                getRecentFiles()
            }
        }

        val filtered = if (searchQuery.isNotBlank()) {
            val query = searchQuery.trim().lowercase(Locale.ROOT)
            rawList.filter { it.name.lowercase(Locale.ROOT).contains(query) }
        } else {
            rawList
        }

        // Sort: Folders first, then alphabetically
        filtered.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))
    }

    private suspend fun scanDirectory(dir: File, showHidden: Boolean): List<GVONEFileItem> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val favorites = getFavoritePaths()
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { file ->
            if (!showHidden && file.name.startsWith(".")) false else true
        }.map { file ->
            val relPath = file.relativeTo(rootDir).path
            val status = if (file.isDirectory) GitFileStatus.UNMODIFIED else gitManager.getFileGitStatus(relPath)
            toFileItem(file, favorites, status)
        }
    }

    fun toFileItem(
        file: File, 
        favorites: Set<String> = getFavoritePaths(),
        gitStatus: GitFileStatus = GitFileStatus.UNMODIFIED
    ): GVONEFileItem {
        val relativePath = file.relativeTo(rootDir).path
        val parentPath = file.parentFile?.relativeTo(rootDir)?.path.orEmpty().let {
            if (it == ".") "" else it
        }
        val isDir = file.isDirectory
        val ext = if (isDir) "" else file.extension.lowercase(Locale.ROOT)
        val fileType = determineFileType(file.name, isDir)
        val size = if (isDir) computeDirectorySize(file) else file.length()
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

        return GVONEFileItem(
            id = relativePath,
            name = file.name,
            path = relativePath,
            absolutePath = file.absolutePath,
            parentPath = parentPath,
            isDirectory = isDir,
            size = size,
            formattedSize = if (isDir) "${(file.list()?.size ?: 0)} items" else formatFileSize(size),
            lastModified = file.lastModified(),
            formattedDate = sdf.format(Date(file.lastModified())),
            extension = ext,
            fileType = fileType,
            isFavorite = favorites.contains(relativePath),
            mimeType = getMimeType(ext),
            gitStatus = gitStatus
        )
    }

    fun getFileItem(relativePath: String): GVONEFileItem? {
        val file = getFile(relativePath)
        return if (file.exists()) toFileItem(file) else null
    }

    fun determineFileType(fileName: String, isDirectory: Boolean): FileType {
        if (isDirectory) return FileType.FOLDER
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "md", "markdown" -> FileType.MARKDOWN
            "txt", "log", "conf", "cfg", "env" -> FileType.TEXT
            "json" -> FileType.JSON
            "kt", "java", "py", "js", "ts", "html", "htm", "css", "sh", "bash", "xml", "c", "cpp", "h", "rs", "go", "sql" -> FileType.CODE
            "pdf" -> FileType.PDF
            "png", "jpg", "jpeg", "webp", "gif", "svg", "bmp", "ico" -> FileType.IMAGE
            "zip", "tar", "gz", "rar", "7z" -> FileType.ARCHIVE
            else -> FileType.UNKNOWN
        }
    }

    fun getMimeType(ext: String): String {
        return when (ext.lowercase(Locale.ROOT)) {
            "md", "markdown" -> "text/markdown"
            "txt", "log" -> "text/plain"
            "json" -> "application/json"
            "kt" -> "text/x-kotlin"
            "java" -> "text/x-java-source"
            "py" -> "text/x-python"
            "js" -> "application/javascript"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    private fun computeDirectorySize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            size += if (f.isDirectory) computeDirectorySize(f) else f.length()
        }
        return size
    }

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    /**
     * Creates a new file inside the designated parent folder.
     */
    suspend fun createFile(
        parentFolder: String,
        name: String,
        extension: String,
        initialContent: String = ""
    ): GVONEFileItem = withContext(Dispatchers.IO) {
        val targetDir = if (parentFolder.isBlank()) rootDir else File(rootDir, parentFolder)
        if (!targetDir.exists()) targetDir.mkdirs()

        val fullName = if (extension.isNotBlank() && !name.endsWith(".$extension", ignoreCase = true)) {
            "$name.$extension"
        } else {
            name
        }

        var candidate = File(targetDir, fullName)
        var count = 1
        while (candidate.exists()) {
            val base = fullName.substringBeforeLast('.')
            val ext = fullName.substringAfterLast('.', "")
            val newName = if (ext.isNotEmpty()) "$base ($count).$ext" else "$base ($count)"
            candidate = File(targetDir, newName)
            count++
        }

        candidate.writeText(initialContent)
        val item = toFileItem(candidate, getFavoritePaths())
        recordRecent(item)
        item
    }

    /**
     * Creates a new folder inside parent folder.
     */
    suspend fun createFolder(
        parentFolder: String,
        name: String
    ): GVONEFileItem = withContext(Dispatchers.IO) {
        val targetDir = if (parentFolder.isBlank()) rootDir else File(rootDir, parentFolder)
        if (!targetDir.exists()) targetDir.mkdirs()

        var candidate = File(targetDir, name)
        var count = 1
        while (candidate.exists()) {
            candidate = File(targetDir, "$name ($count)")
            count++
        }

        candidate.mkdirs()
        toFileItem(candidate, getFavoritePaths())
    }

    /**
     * Creates a full-fledged software project repository with custom templates,
     * directory scaffolding (subfolders like src/), starter code files, and initialized Git repo.
     */
    suspend fun createProject(
        projectName: String,
        template: ProjectTemplate,
        description: String = "",
        isPrivate: Boolean = false,
        initReadme: Boolean = true,
        initGitignore: Boolean = true
    ): GVONEFileItem = withContext(Dispatchers.IO) {
        val cleanName = projectName.trim().replace(" ", "-")
        val projectRelPath = "Projects/$cleanName"
        val projectDir = File(rootDir, projectRelPath)
        if (!projectDir.exists()) projectDir.mkdirs()

        val srcDir = File(projectDir, "src")
        srcDir.mkdirs()

        when (template) {
            ProjectTemplate.KOTLIN_APP -> {
                val mainFile = File(srcDir, "Main.kt")
                mainFile.writeText(
                    """
                    package com.gvone.$cleanName

                    fun main() {
                        println("=== $cleanName: Kotlin Application ===")
                        val engine = AppEngine(name = "$cleanName")
                        engine.start()
                    }

                    class AppEngine(val name: String) {
                        fun start() {
                            println("Engine for ${'$'}name started successfully.")
                            println("Version control and multi-folder workspace active.")
                        }
                    }
                    """.trimIndent()
                )

                val utilsDir = File(srcDir, "utils")
                utilsDir.mkdirs()
                File(utilsDir, "AppUtils.kt").writeText(
                    """
                    package com.gvone.$cleanName.utils

                    object AppUtils {
                        fun formatTimestamp(epoch: Long): String = "Timestamp: ${'$'}epoch"
                    }
                    """.trimIndent()
                )

                File(projectDir, "build.gradle.kts").writeText(
                    """
                    plugins {
                        kotlin("jvm") version "2.0.0"
                        application
                    }

                    application {
                        mainClass.set("com.gvone.$cleanName.MainKt")
                    }
                    """.trimIndent()
                )
            }
            ProjectTemplate.WEB_HTML -> {
                File(projectDir, "index.html").writeText(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>$cleanName - GVONE Workspace</title>
                        <link rel="stylesheet" href="src/styles.css">
                    </head>
                    <body>
                        <div class="container">
                            <header>
                                <h1>$cleanName</h1>
                                <p class="tagline">Interactive Web Application crafted in GVONE IDE</p>
                            </header>
                            <main id="app-root">
                                <div class="card">
                                    <h2>Application Status</h2>
                                    <p id="status-text">Ready to develop and commit changes.</p>
                                    <button id="action-btn">Trigger Action</button>
                                </div>
                            </main>
                        </div>
                        <script src="src/app.js"></script>
                    </body>
                    </html>
                    """.trimIndent()
                )

                File(srcDir, "styles.css").writeText(
                    """
                    :root {
                        --bg-color: #0b1120;
                        --card-bg: #1e293b;
                        --primary: #38bdf8;
                        --text-main: #f8fafc;
                        --text-muted: #94a3b8;
                    }
                    body {
                        margin: 0;
                        padding: 24px;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        background: var(--bg-color);
                        color: var(--text-main);
                    }
                    .container { max-width: 800px; margin: 0 auto; }
                    .card {
                        background: var(--card-bg);
                        padding: 20px;
                        border-radius: 12px;
                        border: 1px solid #334155;
                        margin-top: 16px;
                    }
                    button {
                        background: var(--primary);
                        color: #0f172a;
                        border: none;
                        padding: 10px 18px;
                        border-radius: 8px;
                        font-weight: bold;
                        cursor: pointer;
                    }
                    """.trimIndent()
                )

                val componentsDir = File(srcDir, "components")
                componentsDir.mkdirs()
                File(componentsDir, "Navbar.js").writeText(
                    """
                    export function initNavbar() {
                        console.log("Navbar component initialized for $cleanName");
                    }
                    """.trimIndent()
                )

                File(srcDir, "app.js").writeText(
                    """
                    import { initNavbar } from './components/Navbar.js';

                    document.addEventListener("DOMContentLoaded", () => {
                        initNavbar();
                        const btn = document.getElementById("action-btn");
                        const status = document.getElementById("status-text");
                        if (btn && status) {
                            btn.addEventListener("click", () => {
                                status.textContent = "Action executed at " + new Date().toLocaleTimeString();
                                status.style.color = "#38bdf8";
                            });
                        }
                    });
                    """.trimIndent()
                )
            }
            ProjectTemplate.PYTHON_SCRIPT -> {
                File(projectDir, "main.py").writeText(
                    """
                    #!/usr/bin/env python3
                    \"\"\"
                    $cleanName - GVONE Python Workspace Application
                    \"\"\"
                    from src.models import TaskManager
                    from src.utils import log_message

                    def main():
                        log_message("Starting $cleanName...")
                        manager = TaskManager("$cleanName")
                        manager.add_task("Initial project setup")
                        manager.add_task("Implement core business logic")
                        manager.list_tasks()
                        log_message("Execution complete.")

                    if __name__ == "__main__":
                        main()
                    """.trimIndent()
                )

                File(srcDir, "models.py").writeText(
                    """
                    class TaskManager:
                        def __init__(self, name: str):
                            self.name = name
                            self.tasks = []

                        def add_task(self, title: str):
                            self.tasks.append(title)
                            print(f"[+] Task added: {title}")

                        def list_tasks(self):
                            print(f"--- Tasks in {self.name} ---")
                            for idx, task in enumerate(self.tasks, 1):
                                print(f"{idx}. {task}")
                    """.trimIndent()
                )

                File(srcDir, "utils.py").writeText(
                    """
                    import datetime

                    def log_message(msg: str):
                        now = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                        print(f"[{now}] {msg}")
                    """.trimIndent()
                )

                File(projectDir, "requirements.txt").writeText(
                    """
                    requests>=2.31.0
                    pytest>=7.4.0
                    """.trimIndent()
                )
            }
            ProjectTemplate.NODE_JS -> {
                File(projectDir, "server.js").writeText(
                    """
                    // $cleanName - GVONE Node.js Application Server
                    const http = require('http');
                    const { handleApiRoutes } = require('./src/routes/api');

                    const PORT = process.env.PORT || 3000;

                    const server = http.createServer((req, res) => {
                        if (req.url.startsWith('/api')) {
                            return handleApiRoutes(req, res);
                        }
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({
                            status: 'online',
                            project: '$cleanName',
                            timestamp: new Date().toISOString()
                        }));
                    });

                    server.listen(PORT, () => {
                        console.log(`Server running on port ${'$'}{PORT}`);
                    });
                    """.trimIndent()
                )

                val routesDir = File(srcDir, "routes")
                routesDir.mkdirs()
                File(routesDir, "api.js").writeText(
                    """
                    function handleApiRoutes(req, res) {
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ message: "API endpoint for $cleanName operational" }));
                    }

                    module.exports = { handleApiRoutes };
                    """.trimIndent()
                )

                File(projectDir, "package.json").writeText(
                    """
                    {
                      "name": "${cleanName.lowercase(Locale.ROOT)}",
                      "version": "1.0.0",
                      "description": "$description",
                      "main": "server.js",
                      "scripts": {
                        "start": "node server.js",
                        "dev": "nodemon server.js"
                      },
                      "keywords": ["gvone", "software", "project"],
                      "author": "developer",
                      "license": "MIT"
                    }
                    """.trimIndent()
                )
            }
            ProjectTemplate.BLANK -> {
                // Blank project with clean src directory
                File(srcDir, ".keep").writeText("")
            }
        }

        if (initReadme) {
            val readme = File(projectDir, "README.md")
            val desc = if (description.isNotBlank()) description else template.description
            readme.writeText(
                """
                # $cleanName

                $desc

                - **Template**: ${template.displayName}
                - **Workspace**: GVONE Universal File System
                - **Version Control**: Git-enabled project repository
                - **Active Branch**: `main`

                ## Project Structure
                - `src/`: Source code directory for components and modules
                - `README.md`: Project documentation and architecture notes
                - `.gitignore`: Ignored build artifacts and dependencies

                ## Version Control
                Edit files in the GVONE code editor and commit changes directly to the project branch history.
                """.trimIndent()
            )
        }

        if (initGitignore) {
            val gitignore = File(projectDir, ".gitignore")
            gitignore.writeText("build/\n.gradle/\n*.log\nnode_modules/\n__pycache__/\n*.pyc\n.env\n.DS_Store\n")
        }

        // Initialize Git repo in GitManager
        gitManager.initializeProjectRepo(projectRelPath, "Initial project structure ($cleanName)")

        toFileItem(projectDir, getFavoritePaths())
    }

    suspend fun createProject(
        projectName: String,
        template: String = "Kotlin",
        isPrivate: Boolean = false,
        initReadme: Boolean = true,
        initGitignore: Boolean = true
    ): GVONEFileItem = withContext(Dispatchers.IO) {
        val cleanName = projectName.trim().replace(" ", "-")
        val projectRelPath = "Projects/$cleanName"
        val projectDir = File(rootDir, projectRelPath)
        if (!projectDir.exists()) projectDir.mkdirs()

        val srcDir = File(projectDir, "src")
        if (!srcDir.exists()) srcDir.mkdirs()

        when (template.lowercase(Locale.ROOT)) {
            "kotlin", "android" -> {
                val mainKt = File(srcDir, "Main.kt")
                mainKt.writeText(
                    """
                    package com.gvone.$cleanName
                    
                    fun main() {
                        println("Running $cleanName on GVONE Universal Runtime!")
                        val components = listOf("Router", "Storage", "UI", "Network")
                        components.forEach { println(" - Loaded component: ${'$'}it") }
                    }
                    """.trimIndent()
                )
                File(projectDir, "build.gradle.kts").writeText(
                    """
                    plugins {
                        kotlin("jvm") version "2.0.0"
                        application
                    }
                    
                    application {
                        mainClass.set("com.gvone.$cleanName.MainKt")
                    }
                    """.trimIndent()
                )
            }
            "web", "html", "javascript", "react" -> {
                File(projectDir, "index.html").writeText(
                    """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>$cleanName</title>
                        <link rel="stylesheet" href="src/styles.css">
                    </head>
                    <body>
                        <div class="container">
                            <h1>$cleanName</h1>
                            <p>Interactive web application running on GVONE browser tab.</p>
                            <button id="action-btn">Click Me</button>
                            <p id="output"></p>
                        </div>
                        <script src="src/app.js"></script>
                    </body>
                    </html>
                    """.trimIndent()
                )
                File(srcDir, "styles.css").writeText(
                    """
                    body {
                        font-family: system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
                        background: #0f172a;
                        color: #f8fafc;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 100vh;
                        margin: 0;
                    }
                    .container {
                        background: #1e293b;
                        padding: 24px;
                        border-radius: 12px;
                        border: 1px solid #334155;
                        text-align: center;
                    }
                    button {
                        background: #38bdf8;
                        color: #0f172a;
                        font-weight: bold;
                        border: none;
                        padding: 10px 20px;
                        border-radius: 8px;
                        cursor: pointer;
                    }
                    """.trimIndent()
                )
                File(srcDir, "app.js").writeText(
                    """
                    document.getElementById('action-btn')?.addEventListener('click', () => {
                        const out = document.getElementById('output');
                        if (out) out.textContent = "Button triggered at " + new Date().toLocaleTimeString();
                    });
                    """.trimIndent()
                )
            }
            "python" -> {
                File(srcDir, "main.py").writeText(
                    """
                    #!/usr/bin/env python3
                    import sys
                    
                    def main():
                        print(f"Executing $cleanName v1.0 on GVONE Python runtime...")
                        print(f"Python sys.version: {sys.version}")
                    
                    if __name__ == "__main__":
                        main()
                    """.trimIndent()
                )
                File(projectDir, "requirements.txt").writeText("# Python dependencies\nrequests>=2.31.0\n")
            }
            "node", "typescript" -> {
                File(srcDir, "server.ts").writeText(
                    """
                    import http from 'http';
                    
                    const server = http.createServer((req, res) => {
                        res.writeHead(200, { 'Content-Type': 'application/json' });
                        res.end(JSON.stringify({ status: 'ok', project: '$cleanName' }));
                    });
                    
                    server.listen(3000, () => console.log('$cleanName server active on port 3000'));
                    """.trimIndent()
                )
                File(projectDir, "package.json").writeText(
                    """
                    {
                      "name": "$cleanName",
                      "version": "1.0.0",
                      "main": "src/server.ts",
                      "scripts": {
                        "start": "ts-node src/server.ts"
                      }
                    }
                    """.trimIndent()
                )
            }
            else -> {
                File(srcDir, "app.txt").writeText("Project $cleanName\nCreated in GVONE Universal Workspace.\n")
            }
        }

        if (initReadme) {
            val readme = File(projectDir, "README.md")
            readme.writeText(
                """
                # $cleanName
                
                Software project developed inside the GVONE Universal Workspace.
                
                ## Directory Hierarchy
                - `src/`: Application source code and components
                - `README.md`: Project documentation
                - `.gitignore`: Ignored build artifacts
                
                ## Version Control
                This repository is managed with Git version control. Commit history, branches, and diff tracking are enabled.
                """.trimIndent()
            )
        }

        if (initGitignore) {
            val gitignore = File(projectDir, ".gitignore")
            gitignore.writeText(
                """
                # GVONE Build and Cache Files
                build/
                dist/
                out/
                .cache/
                *.log
                .env
                node_modules/
                __pycache__/
                """.trimIndent()
            )
        }

        gitManager.initializeProjectRepo(projectRelPath, "Initial commit: scaffold $cleanName ($template project)")
        toFileItem(projectDir, getFavoritePaths())
    }

    /**
     * Imports a file selected from system storage via Uri.
     */
    suspend fun importFromUri(
        uri: Uri,
        targetFolder: String
    ): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (targetFolder.isBlank()) File(rootDir, "Downloads") else File(rootDir, targetFolder)
            if (!targetDir.exists()) targetDir.mkdirs()

            val fileName = queryFileName(uri) ?: "imported_file_${System.currentTimeMillis()}"
            val targetFile = File(targetDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val item = toFileItem(targetFile, getFavoritePaths())
            recordRecent(item)
            item
        } catch (_: Exception) {
            null
        }
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (colIndex != -1) {
                            name = cursor.getString(colIndex)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    /**
     * Reads text content of a file.
     */
    suspend fun readFileContent(relativePath: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, relativePath)
            if (file.exists() && file.isFile) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    /**
     * Writes text content to an existing or new file.
     */
    suspend fun writeFileContent(relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, relativePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            recordRecent(toFileItem(file, getFavoritePaths()))
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Deletes a file or directory recursively.
     */
    suspend fun deleteItem(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(rootDir, relativePath)
            if (file.exists()) {
                val success = file.deleteRecursively()
                if (success) {
                    removeFavorite(relativePath)
                }
                success
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Renames a file or directory.
     */
    suspend fun renameItem(relativePath: String, newName: String): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val source = File(rootDir, relativePath)
            if (!source.exists()) return@withContext null
            val dest = File(source.parentFile, newName)
            if (dest.exists()) return@withContext null
            val success = source.renameTo(dest)
            if (success) {
                val wasFav = isFavorite(relativePath)
                if (wasFav) {
                    removeFavorite(relativePath)
                    addFavorite(dest.relativeTo(rootDir).path)
                }
                toFileItem(dest, getFavoritePaths())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Moves a file or directory to a target folder.
     */
    suspend fun moveItem(sourcePath: String, targetFolder: String): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val source = File(rootDir, sourcePath)
            if (!source.exists()) return@withContext null
            val targetDir = if (targetFolder.isBlank()) rootDir else File(rootDir, targetFolder)
            if (!targetDir.exists()) targetDir.mkdirs()

            val dest = File(targetDir, source.name)
            if (dest.exists()) return@withContext null

            val success = source.renameTo(dest)
            if (success) {
                toFileItem(dest, getFavoritePaths())
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Copies a file or folder to a target folder.
     */
    suspend fun copyItem(sourcePath: String, targetFolder: String): GVONEFileItem? = withContext(Dispatchers.IO) {
        try {
            val source = File(rootDir, sourcePath)
            if (!source.exists()) return@withContext null
            val targetDir = if (targetFolder.isBlank()) rootDir else File(rootDir, targetFolder)
            if (!targetDir.exists()) targetDir.mkdirs()

            var dest = File(targetDir, source.name)
            var count = 1
            while (dest.exists()) {
                val base = source.name.substringBeforeLast('.')
                val ext = source.name.substringAfterLast('.', "")
                val newName = if (ext.isNotEmpty()) "$base (copy $count).$ext" else "$base (copy $count)"
                dest = File(targetDir, newName)
                count++
            }

            source.copyRecursively(dest, overwrite = false)
            toFileItem(dest, getFavoritePaths())
        } catch (_: Exception) {
            null
        }
    }

    // =========================================================================
    // FAVORITES & RECENT MANAGEMENT
    // =========================================================================

    fun getFavoritePaths(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun isFavorite(relativePath: String): Boolean {
        return getFavoritePaths().contains(relativePath)
    }

    fun toggleFavorite(relativePath: String): Boolean {
        val current = getFavoritePaths().toMutableSet()
        val newState = if (current.contains(relativePath)) {
            current.remove(relativePath)
            false
        } else {
            current.add(relativePath)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return newState
    }

    private fun addFavorite(path: String) {
        val current = getFavoritePaths().toMutableSet()
        current.add(path)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    private fun removeFavorite(path: String) {
        val current = getFavoritePaths().toMutableSet()
        current.remove(path)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
    }

    fun recordRecent(item: GVONEFileItem) {
        try {
            val raw = prefs.getString(KEY_RECENT, "[]")
            val arr = JSONArray(raw)
            val updated = JSONArray()
            updated.put(item.path)

            for (i in 0 until arr.length()) {
                val p = arr.getString(i)
                if (p != item.path && updated.length() < 25) {
                    updated.put(p)
                }
            }
            prefs.edit().putString(KEY_RECENT, updated.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getRecentFiles(): List<GVONEFileItem> {
        val favorites = getFavoritePaths()
        return try {
            val raw = prefs.getString(KEY_RECENT, "[]")
            val arr = JSONArray(raw)
            val list = mutableListOf<GVONEFileItem>()
            for (i in 0 until arr.length()) {
                val path = arr.getString(i)
                val file = File(rootDir, path)
                if (file.exists()) {
                    list.add(toFileItem(file, favorites))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearRecentFiles() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    /**
     * Inspects complete metadata for "Get Info" dialog.
     */
    suspend fun getFileMetadata(relativePath: String): FileMetadata = withContext(Dispatchers.IO) {
        val file = File(rootDir, relativePath)
        val sdf = SimpleDateFormat("EEEE, MMMM dd, yyyy 'at' hh:mm:ss a", Locale.US)
        val ext = file.extension.lowercase(Locale.ROOT)
        val isDir = file.isDirectory
        val sizeBytes = if (isDir) computeDirectorySize(file) else file.length()
        val perm = StringBuilder().apply {
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
        }.toString()

        val md5 = if (!isDir && file.length() < 10 * 1024 * 1024) {
            calculateMD5(file)
        } else {
            "N/A (Directory or Large File)"
        }

        FileMetadata(
            name = file.name,
            path = relativePath,
            absolutePath = file.absolutePath,
            parentFolder = file.parentFile?.name ?: "Root",
            sizeFormatted = if (isDir) "${file.list()?.size ?: 0} items (${formatFileSize(sizeBytes)})" else formatFileSize(sizeBytes),
            bytes = sizeBytes,
            createdFormatted = sdf.format(Date(file.lastModified())),
            modifiedFormatted = sdf.format(Date(file.lastModified())),
            mimeType = if (isDir) "directory/folder" else getMimeType(ext),
            permissions = "POSIX: $perm (0755)",
            isFavorite = isFavorite(relativePath),
            md5Checksum = md5
        )
    }

    private fun calculateMD5(file: File): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } > 0) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            "Unknown"
        }
    }

    /**
     * Storage summary metrics for Settings → Files & Storage.
     */
    suspend fun getStorageUsageSummary(): StorageUsageSummary = withContext(Dispatchers.IO) {
        var totalCount = 0
        var totalFolders = 0
        var totalBytes = 0L

        fun scan(f: File) {
            if (f.isDirectory) {
                totalFolders++
                f.listFiles()?.forEach { scan(it) }
            } else {
                totalCount++
                totalBytes += f.length()
            }
        }

        scan(rootDir)

        val docsDir = File(rootDir, "Documents")
        val projectsDir = File(rootDir, "Projects")
        val imagesDir = File(rootDir, "Images")
        val downloadsDir = File(rootDir, "Downloads")

        StorageUsageSummary(
            totalFilesCount = totalCount,
            totalFoldersCount = totalFolders,
            totalSizeBytes = totalBytes,
            formattedTotalSize = formatFileSize(totalBytes),
            documentsSize = formatFileSize(computeDirectorySize(docsDir)),
            projectsSize = formatFileSize(computeDirectorySize(projectsDir)),
            imagesSize = formatFileSize(computeDirectorySize(imagesDir)),
            downloadsSize = formatFileSize(computeDirectorySize(downloadsDir))
        )
    }

    /**
     * Exports all user files into a compressed ZIP archive.
     */
    suspend fun exportAllAsZip(outputZip: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipOutputStream(FileOutputStream(outputZip)).use { zipOut ->
                fun addFile(fileToZip: File, parentName: String) {
                    if (fileToZip.isDirectory) {
                        val folderName = if (parentName.isEmpty()) fileToZip.name else "$parentName/${fileToZip.name}"
                        zipOut.putNextEntry(ZipEntry("$folderName/"))
                        zipOut.closeEntry()
                        fileToZip.listFiles()?.forEach { child ->
                            addFile(child, folderName)
                        }
                    } else {
                        FileInputStream(fileToZip).use { fis ->
                            val zipEntry = ZipEntry(if (parentName.isEmpty()) fileToZip.name else "$parentName/${fileToZip.name}")
                            zipOut.putNextEntry(zipEntry)
                            fis.copyTo(zipOut)
                            zipOut.closeEntry()
                        }
                    }
                }

                rootDir.listFiles()?.forEach { file ->
                    addFile(file, "")
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getFile(relativePath: String): File {
        return File(rootDir, relativePath)
    }

    fun getRootDir(): File = rootDir
}
