package com.example.ui.screens.files

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.data.files.FileType
import com.example.data.files.GVONEFileItem
import com.example.data.files.GVONEFileSystem
import com.example.data.files.RunCapability
import com.example.data.files.getFileRunCapability
import com.example.data.files.isRunnableFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Universal Tab Viewer & Editor for GVONE.
 * Opens different files (HTML, PDF, Markdown, Text, JSON, Code, Images) directly inside browser tabs.
 */
@Composable
fun GVONEFileViewerScreen(
    fileRelativePath: String,
    fileSystem: GVONEFileSystem,
    onCloseTab: () -> Unit,
    onOpenTerminalWithCommand: (String) -> Unit = {},
    onRunInBrowserTab: (url: String, title: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var fileContent by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var mdEditMode by remember { mutableStateOf(false) } // For markdown: preview vs edit
    var isRunnerActive by remember { mutableStateOf(false) } // Live Runner mode for .html & support files
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val file = remember(fileRelativePath) { fileSystem.getFile(fileRelativePath) }
    val fileName = remember(fileRelativePath) { file.name }
    val isDir = remember(file) { file.isDirectory }
    val fileType = remember(fileName, isDir) { fileSystem.determineFileType(fileName, isDir) }
    val runCapability = remember(fileName) { getFileRunCapability(fileName) }

    val hasUnsavedChanges by remember(fileContent, originalContent) {
        derivedStateOf { fileContent != originalContent }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    // Load file initial state
    LaunchedEffect(fileRelativePath) {
        isLoading = true
        withContext(Dispatchers.IO) {
            isFavorite = fileSystem.isFavorite(fileRelativePath)
            if (fileType == FileType.TEXT || fileType == FileType.MARKDOWN ||
                fileType == FileType.JSON || fileType == FileType.CODE || fileType == FileType.HTML
            ) {
                val text = fileSystem.readFileContent(fileRelativePath)
                withContext(Dispatchers.Main) {
                    fileContent = text
                    originalContent = text
                }
            }
        }
        isLoading = false
    }

    fun saveChanges() {
        coroutineScope.launch {
            isSaving = true
            val success = fileSystem.writeFileContent(fileRelativePath, fileContent)
            isSaving = false
            if (success) {
                originalContent = fileContent
                toastMessage = "Changes saved"
            } else {
                toastMessage = "Error saving file"
            }
        }
    }

    fun shareFile() {
        try {
            val fileToShare = fileSystem.getFile(fileRelativePath)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileToShare)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = fileSystem.getMimeType(fileToShare.extension)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share $fileName"))
        } catch (e: Exception) {
            // Fallback plain share
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, fileContent)
            }
            context.startActivity(Intent.createChooser(intent, "Share $fileName"))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .testTag("gvone_file_viewer_screen")
    ) {
        // TOP APP BAR / VIEWER CONTROLS
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF111726),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: File icon & Breadcrumb Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = fileType.color.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, fileType.color.copy(alpha = 0.4f)),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (fileType) {
                                    FileType.HTML -> Icons.Rounded.Language
                                    FileType.MARKDOWN -> Icons.Rounded.Description
                                    FileType.TEXT -> Icons.Rounded.Article
                                    FileType.JSON -> Icons.Rounded.DataObject
                                    FileType.CODE -> Icons.Rounded.Code
                                    FileType.PDF -> Icons.Rounded.PictureAsPdf
                                    FileType.IMAGE -> Icons.Rounded.Image
                                    FileType.ARCHIVE -> Icons.Rounded.FolderZip
                                    else -> Icons.Rounded.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = fileType.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fileName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (hasUnsavedChanges) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF38BDF8))
                                )
                            }
                        }
                        Text(
                            text = fileRelativePath,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                // Right: Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Run Button for .html and all other runnable files (JS, SH, PY, KT, SVG, etc.)
                    if (runCapability != RunCapability.NONE) {
                        Button(
                            onClick = {
                                if (hasUnsavedChanges) saveChanges()
                                isRunnerActive = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunnerActive) Color(0xFF059669) else Color(0xFF10B981)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("run_file_top_bar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Run",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isRunnerActive) "Running" else "Run",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Toggle back to Code Editor if runner is active
                    if (isRunnerActive) {
                        OutlinedButton(
                            onClick = { isRunnerActive = false },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("switch_to_code_button")
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = "Editor", tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Code", fontSize = 11.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Direct "Open in Browser Tab" for HTML files
                    if (runCapability == RunCapability.HTML_RUNNER) {
                        IconButton(
                            onClick = {
                                if (hasUnsavedChanges) saveChanges()
                                onRunInBrowserTab("file://${file.absolutePath}", fileName)
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .testTag("open_html_in_browser_tab_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInBrowser,
                                contentDescription = "Run in Browser Tab",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Markdown Mode Toggle
                    if (fileType == FileType.MARKDOWN) {
                        IconButton(
                            onClick = { mdEditMode = !mdEditMode },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = if (mdEditMode) Icons.Rounded.Visibility else Icons.Rounded.Edit,
                                contentDescription = if (mdEditMode) "Preview" else "Edit",
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // Save Button
                    if (hasUnsavedChanges) {
                        Button(
                            onClick = { saveChanges() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Favorite Toggle
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isFavorite = fileSystem.toggleFavorite(fileRelativePath)
                                toastMessage = if (isFavorite) "Added to Favorites" else "Removed from Favorites"
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) Color(0xFFFBBF24) else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Share
                    IconButton(
                        onClick = { shareFile() },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color(0xFF94A3B8), modifier = Modifier.size(19.dp))
                    }

                    // Info Dialog
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = "Info", tint = Color(0xFF94A3B8), modifier = Modifier.size(19.dp))
                    }

                    // Close Tab
                    IconButton(
                        onClick = onCloseTab,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFFE2E8F0), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // VIEWER CONTENT BODY
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                if (isRunnerActive) {
                    when (runCapability) {
                        RunCapability.HTML_RUNNER -> {
                            HtmlWebRunner(
                                file = file,
                                htmlContent = fileContent,
                                onBackToEditor = { isRunnerActive = false },
                                onOpenInBrowserTab = {
                                    if (hasUnsavedChanges) saveChanges()
                                    onRunInBrowserTab("file://${file.absolutePath}", fileName)
                                }
                            )
                        }
                        RunCapability.JS_RUNNER -> {
                            JsScriptRunner(
                                fileName = fileName,
                                jsCode = fileContent,
                                onBackToEditor = { isRunnerActive = false }
                            )
                        }
                        RunCapability.SHELL_RUNNER -> {
                            ShellScriptRunner(
                                fileName = fileName,
                                scriptContent = fileContent,
                                onOpenTerminal = {
                                    onOpenTerminalWithCommand("sh ${fileRelativePath}")
                                },
                                onBackToEditor = { isRunnerActive = false }
                            )
                        }
                        else -> {
                            GenericCodeRunner(
                                fileName = fileName,
                                codeContent = fileContent,
                                runCapability = runCapability,
                                onOpenTerminal = {
                                    onOpenTerminalWithCommand("/cat ${fileRelativePath}")
                                },
                                onBackToEditor = { isRunnerActive = false }
                            )
                        }
                    }
                } else {
                    when (fileType) {
                        FileType.HTML -> {
                            CodeViewerAndEditor(
                                codeText = fileContent,
                                extension = "html",
                                onCodeChange = { fileContent = it },
                                onRunInTerminal = {
                                    onOpenTerminalWithCommand("/cat ${fileRelativePath}")
                                },
                                onRunFile = {
                                    if (hasUnsavedChanges) saveChanges()
                                    isRunnerActive = true
                                }
                            )
                        }

                        FileType.MARKDOWN -> {
                            if (mdEditMode) {
                                CodeTextEditor(
                                    content = fileContent,
                                    onContentChange = { fileContent = it },
                                    language = "markdown"
                                )
                            } else {
                                MarkdownPreview(markdownText = fileContent)
                            }
                        }

                        FileType.TEXT -> {
                            CodeTextEditor(
                                content = fileContent,
                                onContentChange = { fileContent = it },
                                language = "text"
                            )
                        }

                        FileType.JSON -> {
                            JsonViewerAndEditor(
                                jsonText = fileContent,
                                onJsonChange = { fileContent = it }
                            )
                        }

                        FileType.CODE -> {
                            CodeViewerAndEditor(
                                codeText = fileContent,
                                extension = file.extension,
                                onCodeChange = { fileContent = it },
                                onRunInTerminal = {
                                    onOpenTerminalWithCommand("/cat ${fileRelativePath}")
                                },
                                onRunFile = if (runCapability != RunCapability.NONE) {
                                    {
                                        if (hasUnsavedChanges) saveChanges()
                                        isRunnerActive = true
                                    }
                                } else null
                            )
                        }

                        FileType.PDF -> {
                            PdfDocumentViewer(pdfFile = file)
                        }

                        FileType.IMAGE -> {
                            ImageFileViewer(imageFile = file)
                        }

                        else -> {
                            UnknownFileViewer(
                                file = file,
                                onOpenExternal = {
                                    try {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, fileSystem.getMimeType(file.extension))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showInfoDialog) {
        FileInfoModalDialog(
            relativePath = fileRelativePath,
            fileSystem = fileSystem,
            onDismiss = { showInfoDialog = false }
        )
    }
}

/**
 * Clean Code / Text Editor with line numbers.
 */
@Composable
fun CodeTextEditor(
    content: String,
    onContentChange: (String) -> Unit,
    language: String = "text"
) {
    val scrollState = rememberScrollState()
    val lines = remember(content) { content.lines() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .verticalScroll(scrollState)
            .padding(vertical = 12.dp)
    ) {
        // Line numbers column
        Column(
            modifier = Modifier
                .width(42.dp)
                .padding(end = 8.dp),
            horizontalAlignment = Alignment.End
        ) {
            for (i in 1..lines.size) {
                Text(
                    text = "$i",
                    color = Color(0xFF334155),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp
                )
            }
        }

        // Source text area
        BasicTextField(
            value = content,
            onValueChange = onContentChange,
            textStyle = TextStyle(
                color = Color(0xFFE2E8F0),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(Color(0xFF38BDF8)),
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
                .fillMaxHeight()
                .testTag("code_text_editor_field")
        )
    }
}

/**
 * Formatted Markdown visualizer.
 */
@Composable
fun MarkdownPreview(markdownText: String) {
    val scrollState = rememberScrollState()
    val lines = remember(markdownText) { markdownText.lines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B101B))
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        color = Color(0xFF38BDF8),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        color = Color(0xFF7DD3FC),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        color = Color(0xFFBAE6FD),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("• ", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        Text(trimmed.substring(2), color = Color(0xFFCBD5E1), fontSize = 14.sp)
                    }
                }
                trimmed.startsWith("> ") -> {
                    Surface(
                        color = Color(0xFF131D2E),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF1E3A8A)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = trimmed.removePrefix("> "),
                            color = Color(0xFF93C5FD),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                trimmed.startsWith("```") -> {
                    Surface(
                        color = Color(0xFF080C14),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = trimmed,
                            color = Color(0xFF4ADE80),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                trimmed.isEmpty() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    Text(
                        text = trimmed,
                        color = Color(0xFFE2E8F0),
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

/**
 * JSON Editor with validation and pretty printing.
 */
@Composable
fun JsonViewerAndEditor(
    jsonText: String,
    onJsonChange: (String) -> Unit
) {
    val context = LocalContext.current
    var validationStatus by remember { mutableStateOf<String?>(null) }
    var isValid by remember { mutableStateOf(true) }

    fun validateAndFormat(pretty: Boolean) {
        try {
            val trimmed = jsonText.trim()
            val formatted = if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed)
                if (pretty) arr.toString(2) else arr.toString()
            } else {
                val obj = JSONObject(trimmed)
                if (pretty) obj.toString(2) else obj.toString()
            }
            onJsonChange(formatted)
            isValid = true
            validationStatus = "Valid JSON (${if (pretty) "Prettified" else "Minified"})"
        } catch (e: Exception) {
            isValid = false
            validationStatus = "Invalid JSON: ${e.localizedMessage}"
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // JSON action toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF141C2B),
            border = BorderStroke(1.dp, Color(0xFF222F43))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { validateAndFormat(true) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Prettify", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { validateAndFormat(false) },
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("Minify", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }
                }

                if (validationStatus != null) {
                    Text(
                        text = validationStatus.orEmpty(),
                        color = if (isValid) Color(0xFF34D399) else Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        CodeTextEditor(
            content = jsonText,
            onContentChange = {
                onJsonChange(it)
                validationStatus = null
            },
            language = "json"
        )
    }
}

/**
 * Code viewer with "Run in Terminal" action.
 */
@Composable
fun CodeViewerAndEditor(
    codeText: String,
    extension: String,
    onCodeChange: (String) -> Unit,
    onRunInTerminal: () -> Unit,
    onRunFile: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF141C2B),
            border = BorderStroke(1.dp, Color(0xFF222F43))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lang: ${extension.uppercase()}",
                    color = Color(0xFFA78BFA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onRunFile != null) {
                        Button(
                            onClick = onRunFile,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).testTag("code_viewer_toolbar_run_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onRunInTerminal,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Inspect in Terminal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        CodeTextEditor(content = codeText, onContentChange = onCodeChange, language = extension)
    }
}

/**
 * Native Android PDF Document Viewer using PdfRenderer.
 */
@Composable
fun PdfDocumentViewer(pdfFile: File) {
    var currentPageIndex by remember { mutableStateOf(0) }
    var pageCount by remember { mutableStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var zoomScale by remember { mutableStateOf(1f) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    fun renderPage(index: Int) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pageCount = renderer.pageCount

                if (index in 0 until renderer.pageCount) {
                    val page = renderer.openPage(index)
                    val width = (page.width * 1.5).toInt()
                    val height = (page.height * 1.5).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    withContext(Dispatchers.Main) {
                        currentBitmap = bitmap
                        currentPageIndex = index
                    }
                }
                renderer.close()
                pfd.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMsg = "PDF Render Error: ${e.localizedMessage}"
                }
            }
        }
    }

    LaunchedEffect(pdfFile) {
        renderPage(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E293B)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // PDF navigation controls bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) renderPage(currentPageIndex - 1) },
                        enabled = currentPageIndex > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous", tint = Color.White)
                    }

                    Text(
                        text = "Page ${if (pageCount > 0) currentPageIndex + 1 else 0} of $pageCount",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = { if (currentPageIndex < pageCount - 1) renderPage(currentPageIndex + 1) },
                        enabled = currentPageIndex < pageCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next", tint = Color.White)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { if (zoomScale > 0.6f) zoomScale -= 0.2f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom Out", tint = Color(0xFF94A3B8))
                    }
                    Text(
                        text = "${(zoomScale * 100).toInt()}%",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                    IconButton(
                        onClick = { if (zoomScale < 2.5f) zoomScale += 0.2f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom In", tint = Color(0xFF94A3B8))
                    }
                }
            }
        }

        // PDF Page view
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (currentBitmap != null) {
                Card(
                    shape = RoundedCornerShape(4.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page",
                        modifier = Modifier.size(
                            width = (currentBitmap!!.width * zoomScale * 0.5f).dp,
                            height = (currentBitmap!!.height * zoomScale * 0.5f).dp
                        )
                    )
                }
            } else if (errorMsg != null) {
                Text(text = errorMsg.orEmpty(), color = Color(0xFFF87171), fontSize = 13.sp)
            } else {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
            }
        }
    }
}

/**
 * Image Viewer with resolution metadata and pinch-zoom layout.
 */
@Composable
fun ImageFileViewer(imageFile: File) {
    val bitmap = remember(imageFile) {
        try {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B12)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111827))
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = imageFile.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = "${bitmap.width} × ${bitmap.height} px  •  ${imageFile.length() / 1024} KB",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            Text("Unable to render image bitmap", color = Color(0xFF64748B), fontSize = 13.sp)
        }
    }
}

/**
 * Unknown file placeholder with option to open externally.
 */
@Composable
fun UnknownFileViewer(
    file: File,
    onOpenExternal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D16)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.InsertDriveFile,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(64.dp)
            )

            Text(
                text = file.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "No internal viewer registered for .${file.extension} files.\nYou can open this file in an external application.",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onOpenExternal,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
            ) {
                Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open with External App")
            }
        }
    }
}

/**
 * Detailed "Get Info" dialog.
 */
@Composable
fun FileInfoModalDialog(
    relativePath: String,
    fileSystem: GVONEFileSystem,
    onDismiss: () -> Unit
) {
    var metadata by remember { mutableStateOf<com.example.data.files.FileMetadata?>(null) }

    LaunchedEffect(relativePath) {
        metadata = fileSystem.getFileMetadata(relativePath)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text("File Information", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (metadata != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoRow("Name", metadata!!.name)
                    InfoRow("Folder", metadata!!.parentFolder)
                    InfoRow("Path", metadata!!.path)
                    InfoRow("Size", metadata!!.sizeFormatted)
                    InfoRow("Type", metadata!!.mimeType)
                    InfoRow("Modified", metadata!!.modifiedFormatted)
                    InfoRow("Permissions", metadata!!.permissions)
                    InfoRow("MD5 Checksum", metadata!!.md5Checksum)
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color(0xFF38BDF8))
            }
        },
        containerColor = Color(0xFF131926)
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = label.uppercase(), color = Color(0xFF64748B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color(0xFFE2E8F0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HTML & SUPPORT FILE RUNNER ENGINES
// ─────────────────────────────────────────────────────────────────────────────

enum class ConsoleLogLevel(val label: String, val color: Color) {
    LOG("LOG", Color(0xFF38BDF8)),
    INFO("INFO", Color(0xFF10B981)),
    WARN("WARN", Color(0xFFFBBF24)),
    ERROR("ERROR", Color(0xFFEF4444))
}

data class ConsoleLogEntry(
    val message: String,
    val source: String,
    val lineNumber: Int,
    val level: ConsoleLogLevel,
    val timestamp: Long = System.currentTimeMillis()
)

enum class HtmlViewportMode(val label: String, val widthDp: Int?) {
    RESPONSIVE("Responsive", null),
    PHONE("Mobile (380dp)", 380),
    TABLET("Tablet (680dp)", 680)
}

/**
 * High-performance, interactive local HTML runtime with live developer console,
 * viewport simulation, alert dialogs, and real-time JavaScript evaluation.
 */
@Composable
fun HtmlWebRunner(
    file: File,
    htmlContent: String,
    onBackToEditor: () -> Unit,
    onOpenInBrowserTab: () -> Unit
) {
    var viewportMode by remember { mutableStateOf(HtmlViewportMode.RESPONSIVE) }
    var showConsole by remember { mutableStateOf(false) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val consoleLogs = remember { mutableStateListOf<ConsoleLogEntry>() }
    var activeAlertMessage by remember { mutableStateOf<String?>(null) }
    var activeJsResult by remember { mutableStateOf<JsResult?>(null) }
    var replCommand by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val dateFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }

    // Alert dialog when JavaScript calls alert(...)
    if (activeAlertMessage != null) {
        AlertDialog(
            onDismissRequest = {
                activeJsResult?.cancel()
                activeJsResult = null
                activeAlertMessage = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Notifications, contentDescription = null, tint = Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("JavaScript Alert", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(activeAlertMessage ?: "", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            },
            confirmButton = {
                Button(
                    onClick = {
                        activeJsResult?.confirm()
                        activeJsResult = null
                        activeAlertMessage = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("OK")
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .testTag("html_web_runner")
    ) {
        // Runner Toolbar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131926),
            border = BorderStroke(1.dp, Color(0xFF222F43))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Back to Code + Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToEditor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = "Editor", tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editor", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RUNNING", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Center: Viewport Mode Switcher
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = { viewportMode = HtmlViewportMode.RESPONSIVE },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fullscreen,
                            contentDescription = "Responsive View",
                            tint = if (viewportMode == HtmlViewportMode.RESPONSIVE) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewportMode = HtmlViewportMode.PHONE },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Smartphone,
                            contentDescription = "Phone View",
                            tint = if (viewportMode == HtmlViewportMode.PHONE) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewportMode = HtmlViewportMode.TABLET },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tablet,
                            contentDescription = "Tablet View",
                            tint = if (viewportMode == HtmlViewportMode.TABLET) Color(0xFF38BDF8) else Color(0xFF64748B),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                // Right: Reload, Console Toggle, Open in dedicated Tab
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            reloadTrigger++
                            consoleLogs.add(
                                ConsoleLogEntry("Page reloaded", "system", 0, ConsoleLogLevel.INFO)
                            )
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Reload", tint = Color(0xFFE2E8F0), modifier = Modifier.size(16.dp))
                    }

                    // Console button with log count badge
                    Button(
                        onClick = { showConsole = !showConsole },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showConsole) Color(0xFF1E293B) else Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (showConsole) Color(0xFF38BDF8) else Color(0xFF334155)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Console", fontSize = 11.sp, color = Color.White)
                        if (consoleLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("(${consoleLogs.size})", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Open in Browser Tab
                    Button(
                        onClick = onOpenInBrowserTab,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Tab", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Web Viewer Canvas Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0B0F17)),
            contentAlignment = Alignment.Center
        ) {
            val frameModifier = if (viewportMode.widthDp != null) {
                Modifier
                    .width(viewportMode.widthDp!!.dp)
                    .fillMaxHeight()
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            } else {
                Modifier.fillMaxSize()
            }

            Box(modifier = frameModifier.background(Color.White)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                @Suppress("DEPRECATION")
                                allowFileAccessFromFileURLs = true
                                @Suppress("DEPRECATION")
                                allowUniversalAccessFromFileURLs = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    consoleMessage?.let {
                                        val entry = ConsoleLogEntry(
                                            message = it.message(),
                                            source = it.sourceId().substringAfterLast('/'),
                                            lineNumber = it.lineNumber(),
                                            level = when (it.messageLevel()) {
                                                ConsoleMessage.MessageLevel.ERROR -> ConsoleLogLevel.ERROR
                                                ConsoleMessage.MessageLevel.WARNING -> ConsoleLogLevel.WARN
                                                ConsoleMessage.MessageLevel.TIP -> ConsoleLogLevel.INFO
                                                else -> ConsoleLogLevel.LOG
                                            }
                                        )
                                        consoleLogs.add(entry)
                                    }
                                    return true
                                }

                                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                    activeAlertMessage = message
                                    activeJsResult = result
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    return false // Keep navigation inside web runner
                                }
                            }

                            loadDataWithBaseURL(
                                "file://${file.parentFile?.absolutePath}/",
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                            webViewRef = this
                        }
                    },
                    update = { wv ->
                        webViewRef = wv
                        // Trigger reload when requested
                        if (reloadTrigger > 0) {
                            wv.loadDataWithBaseURL(
                                "file://${file.parentFile?.absolutePath}/",
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }
                    }
                )
            }
        }

        // Developer Console Drawer
        if (showConsole) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                color = Color(0xFF090D16),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Console Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111726))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("JavaScript Console", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${consoleLogs.size} logs", color = Color(0xFF64748B), fontSize = 10.sp)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { consoleLogs.clear() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Clear", color = Color(0xFF94A3B8), fontSize = 10.sp)
                            }

                            IconButton(
                                onClick = { showConsole = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    // Console Logs List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (consoleLogs.isEmpty()) {
                            item {
                                Text(
                                    text = "Console is empty. Logs from console.log(), console.warn(), and console.error() appear here.",
                                    color = Color(0xFF475569),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        } else {
                            items(consoleLogs) { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            when (log.level) {
                                                ConsoleLogLevel.ERROR -> Color(0xFFEF4444).copy(alpha = 0.12f)
                                                ConsoleLogLevel.WARN -> Color(0xFFFBBF24).copy(alpha = 0.12f)
                                                else -> Color.Transparent
                                            },
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "[${log.level.name}]",
                                        color = log.level.color,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(48.dp)
                                    )

                                    Text(
                                        text = log.message,
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (log.source.isNotEmpty()) {
                                        Text(
                                            text = "${log.source}:${log.lineNumber}",
                                            color = Color(0xFF64748B),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // REPL Interactive Evaluation Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF141C2B),
                        border = BorderStroke(1.dp, Color(0xFF222F43))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(">", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.width(6.dp))

                            BasicTextField(
                                value = replCommand,
                                onValueChange = { replCommand = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(Color(0xFF38BDF8)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("html_runner_repl_input"),
                                decorationBox = { innerTextField ->
                                    if (replCommand.isEmpty()) {
                                        Text("evaluate JavaScript (e.g. document.title)...", color = Color(0xFF475569), fontSize = 11.sp)
                                    }
                                    innerTextField()
                                }
                            )

                            Button(
                                onClick = {
                                    val cmd = replCommand.trim()
                                    if (cmd.isNotEmpty()) {
                                        replCommand = ""
                                        consoleLogs.add(ConsoleLogEntry("> $cmd", "input", 1, ConsoleLogLevel.LOG))
                                        webViewRef?.evaluateJavascript(cmd) { result ->
                                            val formatted = if (result == "null" || result == null) "undefined" else result
                                            consoleLogs.add(ConsoleLogEntry("<- $formatted", "eval", 1, ConsoleLogLevel.INFO))
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("Eval", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Direct JavaScript execution runner that runs JS in an isolated WebView instance
 * and captures all console output, measurement times, and return values.
 */
@Composable
fun JsScriptRunner(
    fileName: String,
    jsCode: String,
    onBackToEditor: () -> Unit
) {
    val logs = remember { mutableStateListOf<ConsoleLogEntry>() }
    var executionDuration by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    fun executeScript(wv: WebView) {
        logs.clear()
        isRunning = true
        logs.add(ConsoleLogEntry("Executing $fileName...", "runner", 1, ConsoleLogLevel.INFO))

        val startTime = System.currentTimeMillis()
        val wrappedCode = """
            (function() {
                try {
                    $jsCode
                } catch(err) {
                    console.error(err.toString());
                }
            })();
        """.trimIndent()

        wv.evaluateJavascript(wrappedCode) { result ->
            executionDuration = System.currentTimeMillis() - startTime
            isRunning = false
            logs.add(ConsoleLogEntry("Returned: $result (in ${executionDuration}ms)", "runner", 1, ConsoleLogLevel.INFO))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .testTag("js_script_runner")
    ) {
        // Hidden WebView engine for JS evaluation
        AndroidView(
            modifier = Modifier.size(0.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                logs.add(
                                    ConsoleLogEntry(
                                        message = it.message(),
                                        source = fileName,
                                        lineNumber = it.lineNumber(),
                                        level = when (it.messageLevel()) {
                                            ConsoleMessage.MessageLevel.ERROR -> ConsoleLogLevel.ERROR
                                            ConsoleMessage.MessageLevel.WARNING -> ConsoleLogLevel.WARN
                                            ConsoleMessage.MessageLevel.TIP -> ConsoleLogLevel.INFO
                                            else -> ConsoleLogLevel.LOG
                                        }
                                    )
                                )
                            }
                            return true
                        }
                    }
                    webViewInstance = this
                    executeScript(this)
                }
            }
        )

        // Top bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131926),
            border = BorderStroke(1.dp, Color(0xFF222F43))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToEditor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editor", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                    }

                    Text("JavaScript Runner", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (executionDuration > 0) {
                        Text("${executionDuration}ms", color = Color(0xFF10B981), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = { webViewInstance?.let { executeScript(it) } },
                        enabled = !isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isRunning) "Running..." else "Re-run", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Terminal-style output
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            color = Color(0xFF090D16),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(logs) { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "[${entry.level.name}]",
                            color = entry.level.color,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(52.dp)
                        )
                        Text(
                            text = entry.message,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shell script runner supporting script inspection, line execution, and seamless terminal handoff.
 */
@Composable
fun ShellScriptRunner(
    fileName: String,
    scriptContent: String,
    onOpenTerminal: () -> Unit,
    onBackToEditor: () -> Unit
) {
    val scriptLines = remember(scriptContent) { scriptContent.lines() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .testTag("shell_script_runner")
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131926),
            border = BorderStroke(1.dp, Color(0xFF222F43))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToEditor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editor", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                    }

                    Text("Shell Runner: $fileName", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenTerminal,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Execute in Terminal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Script contents preview with command indicators
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            color = Color(0xFF090D16),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item {
                    Text("# Shell Script Inspection Mode", color = Color(0xFF64748B), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                items(scriptLines.size) { index ->
                    val line = scriptLines[index]
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${index + 1}".padStart(3, ' '),
                            color = Color(0xFF475569),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.width(36.dp)
                        )
                        Text(
                            text = line,
                            color = when {
                                line.startsWith("#") -> Color(0xFF64748B)
                                line.startsWith("echo") -> Color(0xFF38BDF8)
                                line.contains("=") -> Color(0xFFA78BFA)
                                else -> Color(0xFFE2E8F0)
                            },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

/**
 * Generic code runner for other support files (.py, .kt, .java, etc.)
 */
@Composable
fun GenericCodeRunner(
    fileName: String,
    codeContent: String,
    runCapability: RunCapability,
    onOpenTerminal: () -> Unit,
    onBackToEditor: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F17))
            .testTag("generic_code_runner")
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF131926),
            border = BorderStroke(1.dp, Color(0xFF222F43))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToEditor,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Code, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Editor", fontSize = 11.sp, color = Color(0xFFE2E8F0))
                    }

                    Text("${runCapability.label}: $fileName", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenTerminal,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Inspect in Terminal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(16.dp),
            color = Color(0xFF090D16),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                item {
                    Text(
                        text = "Ready to run with ${runCapability.label}.\nUse 'Inspect in Terminal' to run or execute commands against this file.",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = codeContent,
                        color = Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
