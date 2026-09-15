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

    companion object {
        private const val KEY_FAVORITES = "fs_favorites_set"
        private const val KEY_RECENT = "fs_recent_json"
        private const val KEY_CONFIG = "fs_storage_config_json"

        val DEFAULT_FOLDERS = listOf(
            "Documents",
            "Projects",
            "WebApps",
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
     * Initializes the root directory, standard folder hierarchy, and starter files.
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
                    
                    Welcome to the GVONE integrated file manager.
                    This workspace is unified across:
                    - **Browser Tabs**: Open Markdown, Text, Code, JSON, Images, and PDFs in native tabs.
                    - **Terminal CLI**: Access, edit, and manage these files using `ls`, `cat`, `mkdir`, and `touch`.
                    - **AI Agents**: Read project files for context and assistance.
                    - **Connectors**: Sync files with cloud services and web sessions.
                    
                    ## Quick Tips
                    - Tap any file to open it in an editor or viewer.
                    - Long-press for file options (Rename, Move, Copy, Share, Info).
                    - Use the **+ button** to create new files or folders.
                    """.trimIndent()
                )
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

            val projectsFolder = File(rootDir, "Projects")
            val mainKt = File(projectsFolder, "main.kt")
            if (!mainKt.exists()) {
                mainKt.writeText(
                    """
                    package com.example.project
                    
                    fun main() {
                        println("Hello from GVONE Project Runtime!")
                        val status = "Ready"
                        println("Status: ${'$'}status")
                    }
                    """.trimIndent()
                )
            }

            val readmeMd = File(projectsFolder, "README.md")
            if (!readmeMd.exists()) {
                readmeMd.writeText(
                    """
                    # Project Directory
                    
                    Place your development source code, web projects, and scripts here.
                    You can execute commands or inspect code via the GVONE Terminal.
                    """.trimIndent()
                )
            }

            val configJson = File(projectsFolder, "config.json")
            if (!configJson.exists()) {
                configJson.writeText(
                    """
                    {
                      "appName": "GVONE Workspace",
                      "version": "1.0.0",
                      "features": {
                        "unifiedFileSystem": true,
                        "terminalIntegration": true,
                        "tabViewer": true
                      },
                      "environment": "Development"
                    }
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

            // Seed GVONE AI Companion Web App for auto-loading in tabs & offline quick persistence
            val webAppsFolder = File(rootDir, "WebApps")
            val companionFile = File(webAppsFolder, "companion.html")
            if (!companionFile.exists()) {
                companionFile.writeText(getCompanionHtmlContent())
            }

            val projectCompanion = File(projectsFolder, "companion.html")
            if (!projectCompanion.exists()) {
                projectCompanion.writeText(getCompanionHtmlContent())
            }
        } catch (_: Exception) {
        }
    }

    private fun getCompanionHtmlContent(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>GVONE AI Companion</title>
    <style>
        :root {
            --bg-primary: #0b0f19;
            --bg-secondary: #131b2e;
            --card-bg: rgba(26, 36, 61, 0.7);
            --accent: #6366f1;
            --accent-light: #818cf8;
            --text-main: #f8fafc;
            --text-sub: #94a3b8;
            --border-color: rgba(255, 255, 255, 0.08);
            --success: #10b981;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
        body { background: var(--bg-primary); color: var(--text-main); min-height: 100vh; display: flex; flex-direction: column; overflow-x: hidden; }
        header { padding: 14px 18px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--border-color); background: rgba(11, 15, 25, 0.85); backdrop-filter: blur(12px); position: sticky; top: 0; z-index: 50; }
        .logo-area { display: flex; align-items: center; gap: 10px; }
        .logo-badge { width: 34px; height: 34px; border-radius: 10px; background: linear-gradient(135deg, #6366f1, #a855f7); display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 16px; box-shadow: 0 4px 12px rgba(99, 102, 241, 0.4); }
        .title { font-size: 16px; font-weight: 700; letter-spacing: 0.3px; }
        .status-pill { font-size: 11px; padding: 4px 10px; border-radius: 20px; background: rgba(16, 185, 129, 0.15); color: var(--success); font-weight: 600; display: flex; align-items: center; gap: 5px; }
        .status-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--success); box-shadow: 0 0 6px var(--success); }
        
        main { flex: 1; display: flex; flex-direction: column; max-width: 900px; margin: 0 auto; width: 100%; padding: 16px; gap: 16px; }
        
        /* 3D Canvas Visualizer */
        .canvas-card { position: relative; height: 180px; border-radius: 18px; background: radial-gradient(circle at center, #1e1b4b 0%, #0f172a 100%); overflow: hidden; border: 1px solid var(--border-color); display: flex; align-items: center; justify-content: center; }
        .orb { width: 90px; height: 90px; border-radius: 50%; background: radial-gradient(circle at 30% 30%, #818cf8, #4338ca 60%, #312e81 100%); box-shadow: 0 0 40px rgba(99, 102, 241, 0.5), inset -5px -5px 15px rgba(0,0,0,0.5); animation: float 4s ease-in-out infinite alternate; }
        @keyframes float { 0% { transform: translateY(-8px) scale(0.98); } 100% { transform: translateY(8px) scale(1.02); } }
        .canvas-label { position: absolute; bottom: 12px; font-size: 12px; color: var(--text-sub); }

        /* Tabs */
        .nav-tabs { display: flex; gap: 8px; border-bottom: 1px solid var(--border-color); padding-bottom: 8px; overflow-x: auto; }
        .tab-btn { background: transparent; border: none; color: var(--text-sub); padding: 8px 14px; border-radius: 8px; font-size: 13px; font-weight: 600; cursor: pointer; transition: all 0.2s; white-space: nowrap; }
        .tab-btn.active { background: var(--accent); color: #fff; box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3); }

        /* Chat Stream */
        .chat-box { flex: 1; min-height: 280px; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; padding: 8px 0; }
        .msg { max-width: 85%; padding: 12px 16px; border-radius: 16px; font-size: 14px; line-height: 1.5; animation: fadeIn 0.25s ease; }
        @keyframes fadeIn { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
        .msg.bot { align-self: flex-start; background: var(--card-bg); border: 1px solid var(--border-color); color: var(--text-main); border-bottom-left-radius: 4px; }
        .msg.user { align-self: flex-end; background: linear-gradient(135deg, #4f46e5, #6366f1); color: #fff; border-bottom-right-radius: 4px; }
        
        /* Input Bar */
        .input-bar { display: flex; gap: 10px; background: var(--bg-secondary); padding: 10px 14px; border-radius: 16px; border: 1px solid var(--border-color); }
        .input-bar input { flex: 1; background: transparent; border: none; outline: none; color: #fff; font-size: 14px; }
        .input-bar button { background: var(--accent); border: none; color: #fff; padding: 8px 16px; border-radius: 10px; font-weight: 600; font-size: 13px; cursor: pointer; }
    </style>
</head>
<body>
    <header>
        <div class="logo-area">
            <div class="logo-badge">G1</div>
            <div>
                <div class="title">GVONE AI Companion</div>
                <div style="font-size: 10px; color: var(--text-sub);">Auto-Loaded Interactive Node</div>
            </div>
        </div>
        <div class="status-pill">
            <span class="status-dot"></span>
            <span id="bridgeStatus">Bridge Synced</span>
        </div>
    </header>

    <main>
        <div class="canvas-card">
            <div class="orb"></div>
            <div class="canvas-label">GVONE Neural Companion Node • 3D Adaptive Core</div>
        </div>

        <div class="nav-tabs">
            <button class="tab-btn active" onclick="switchTab('chat')">AI Chat</button>
            <button class="tab-btn" onclick="switchTab('visual')">Visual Board</button>
            <button class="tab-btn" onclick="switchTab('tasks')">Task Progress</button>
            <button class="tab-btn" onclick="switchTab('artifacts')">Artifacts</button>
        </div>

        <div id="chatSection" class="chat-box">
            <div class="msg bot">
                👋 <strong>Welcome to GVONE AI Companion!</strong><br>
                This workspace is auto-loaded and persistently synchronized across tabs, terminal tasks, and quick persistence storage.<br><br>
                Try typing below, or submit prompts from the browser address bar directly.
            </div>
        </div>

        <div id="visualSection" style="display:none; padding: 20px; text-align: center; background: var(--card-bg); border-radius: 16px; border: 1px solid var(--border-color);">
            <h3 style="margin-bottom: 8px;">Adaptive Visual Board</h3>
            <p style="color: var(--text-sub); font-size: 13px;">Live multi-modal companion feeds and canvas graphs synchronizing with GVONE OS.</p>
        </div>

        <div id="tasksSection" style="display:none; padding: 20px; background: var(--card-bg); border-radius: 16px; border: 1px solid var(--border-color);">
            <h3 style="margin-bottom: 8px;">Active Neural Tasks</h3>
            <p style="color: var(--text-sub); font-size: 13px;">✔ Tab auto-load verified<br>✔ Quick persistence active (Room & LocalStorage)<br>✔ Bi-directional address bar bridge ready</p>
        </div>

        <div id="artifactsSection" style="display:none; padding: 20px; background: var(--card-bg); border-radius: 16px; border: 1px solid var(--border-color);">
            <h3 style="margin-bottom: 8px;">Generated Artifacts</h3>
            <p style="color: var(--text-sub); font-size: 13px;">Persisted snapshots and documents stored in GVONE Universal File System.</p>
        </div>

        <div class="input-bar">
            <input type="text" id="userInput" placeholder="Ask your AI companion anything..." onkeydown="if(event.key==='Enter') sendMsg()">
            <button onclick="sendMsg()">Send</button>
        </div>
    </main>

    <script>
        // Quick Persistence in LocalStorage & GVONE Browser Bridge
        const STORAGE_KEY = 'gvone_companion_chat_history';
        
        function loadChatHistory() {
            try {
                const saved = localStorage.getItem(STORAGE_KEY);
                if (saved) {
                    const list = JSON.parse(saved);
                    const chatBox = document.getElementById('chatSection');
                    list.forEach(item => {
                        appendMessageUi(item.text, item.sender);
                    });
                }
            } catch(e){}
        }

        function saveMessage(text, sender) {
            try {
                const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
                saved.push({ text, sender, timestamp: Date.now() });
                if (saved.length > 50) saved.shift();
                localStorage.setItem(STORAGE_KEY, JSON.stringify(saved));
            } catch(e){}
        }

        function appendMessageUi(text, sender) {
            const chatBox = document.getElementById('chatSection');
            const div = document.createElement('div');
            div.className = 'msg ' + sender;
            div.innerHTML = text;
            chatBox.appendChild(div);
            chatBox.scrollTop = chatBox.scrollHeight;
        }

        function sendMsg() {
            const input = document.getElementById('userInput');
            const val = input.value.trim();
            if (!val) return;
            input.value = '';

            appendMessageUi(val, 'user');
            saveMessage(val, 'user');

            if (window.GVONEBrowserBridge && window.GVONEBrowserBridge.postMessageToBrowser) {
                window.GVONEBrowserBridge.postMessageToBrowser(JSON.stringify({
                    type: 'companion_user_message',
                    text: val,
                    timestamp: Date.now()
                }));
            }

            // Quick simulated response if offline
            setTimeout(() => {
                const reply = "GVONE Neural Core processed your message: <em>\"" + val + "\"</em>. State synced to persistent session.";
                appendMessageUi(reply, 'bot');
                saveMessage(reply, 'bot');
            }, 500);
        }

        // Bridge input handler from Android Browser Address Bar
        window.__GVONE_HANDLE_BROWSER_INPUT__ = function(data) {
            if (data && data.text) {
                appendMessageUi(data.text, 'user');
                saveMessage(data.text, 'user');
                setTimeout(() => {
                    appendMessageUi("Received from Address Bar: " + data.text, 'bot');
                }, 400);
                return true;
            }
            return false;
        };

        function switchTab(name) {
            ['chat', 'visual', 'tasks', 'artifacts'].forEach(t => {
                document.getElementById(t + 'Section').style.display = (t === name) ? 'block' : 'none';
            });
            document.querySelectorAll('.tab-btn').forEach(btn => {
                btn.classList.toggle('active', btn.textContent.toLowerCase().includes(name));
            });
        }

        // Initialize GVONE Bridge Handshake
        window.addEventListener('DOMContentLoaded', () => {
            loadChatHistory();
            if (window.GVONEBrowserBridge) {
                document.getElementById('bridgeStatus').textContent = 'Bridge Linked';
                if (window.GVONEBrowserBridge.notifyReady) {
                    window.GVONEBrowserBridge.notifyReady('gvone_companion', '2.0');
                }
            }
        });
    </script>
</body>
</html>
        """.trimIndent()
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

    private fun scanDirectory(dir: File, showHidden: Boolean): List<GVONEFileItem> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val favorites = getFavoritePaths()
        val files = dir.listFiles() ?: return emptyList()
        return files.filter { file ->
            if (!showHidden && file.name.startsWith(".")) false else true
        }.map { toFileItem(it, favorites) }
    }

    fun toFileItem(file: File, favorites: Set<String> = getFavoritePaths()): GVONEFileItem {
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
            mimeType = getMimeType(ext)
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
