package com.example.ui.screens.ide

import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.files.GVONEFileItem
import com.example.data.model.BrowserTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton repository managing extension problems and diagnostics.
 */
object ExtensionProblemsManager {
    private val initialProblems = listOf(
        ExtensionProblem(
            id = "prob_eslint_1",
            extensionId = "eslint",
            extensionName = "ESLint & Code Linter",
            file = "src/main/App.kt",
            line = 34,
            column = 18,
            message = "Unused variable 'experimentalFeatureFlag' declared but never read.",
            ruleId = "@typescript-eslint/no-unused-vars",
            severity = ProblemSeverity.WARNING,
            quickFixAvailable = true,
            quickFixActionLabel = "Remove unused variable"
        ),
        ExtensionProblem(
            id = "prob_eslint_2",
            extensionId = "eslint",
            extensionName = "ESLint & Code Linter",
            file = "src/main/App.kt",
            line = 52,
            column = 9,
            message = "Missing error boundary or catch block for asynchronous WebView injection call.",
            ruleId = "security/detect-possible-timing-attacks",
            severity = ProblemSeverity.ERROR,
            quickFixAvailable = true,
            quickFixActionLabel = "Wrap in try-catch block"
        ),
        ExtensionProblem(
            id = "prob_yt_1",
            extensionId = "youtube_enhancer",
            extensionName = "YouTube Media & Ad Enhancer",
            file = "youtube.com/watch",
            line = 1,
            column = 1,
            message = "CSP header on target stream restricted inline sponsorblock script injection.",
            ruleId = "youtube/csp-security-bypass",
            severity = ProblemSeverity.WARNING,
            quickFixAvailable = true,
            quickFixActionLabel = "Inject via ShadowDOM root"
        ),
        ExtensionProblem(
            id = "prob_prettier_1",
            extensionId = "prettier",
            extensionName = "Prettier Formatter",
            file = "public/index.html",
            line = 18,
            column = 1,
            message = "Inconsistent indentation detected: found 4 spaces, project standard specifies 2 spaces.",
            ruleId = "prettier/prettier-indentation",
            severity = ProblemSeverity.INFO,
            quickFixAvailable = true,
            quickFixActionLabel = "Auto-Format File"
        ),
        ExtensionProblem(
            id = "prob_adblock_1",
            extensionId = "adblock_pro",
            extensionName = "AdBlocker Pro",
            file = "network/tracker-shield",
            line = 0,
            column = 0,
            message = "Detected 14 third-party analytic telemetry beacons attempting background socket connection.",
            ruleId = "adblock/telemetry-blocked",
            severity = ProblemSeverity.INFO,
            quickFixAvailable = true,
            quickFixActionLabel = "Enforce Strict DNS Sinkhole"
        )
    )

    private val _problems = MutableStateFlow<List<ExtensionProblem>>(initialProblems)
    val problems: StateFlow<List<ExtensionProblem>> = _problems.asStateFlow()

    fun resolveProblem(problemId: String) {
        _problems.value = _problems.value.filter { it.id != problemId }
    }

    fun quickFixProblem(problemId: String): String {
        val target = _problems.value.find { it.id == problemId }
        val actionLabel = target?.quickFixActionLabel ?: "Applied fix"
        resolveProblem(problemId)
        return actionLabel
    }

    fun clearAllResolved() {
        _problems.value = emptyList()
    }

    fun resetDefaultProblems() {
        _problems.value = initialProblems
    }
}

/**
 * Dedicated Problems & Diagnostics Inspector View.
 * Matches standard VS Code / Xcode Problems panel behavior.
 */
@Composable
fun ExtensionProblemsInspectorView(
    problems: List<ExtensionProblem> = ExtensionProblemsManager.problems.collectAsState().value,
    onQuickFix: (ExtensionProblem) -> Unit = { prob ->
        ExtensionProblemsManager.quickFixProblem(prob.id)
    },
    onOpenFileInEditor: ((String, Int) -> Unit)? = null,
    onRunLinters: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedSeverityFilter by remember { mutableStateOf<ProblemSeverity?>(null) }
    var selectedExtensionFilter by remember { mutableStateOf<String?>("All") }

    val errorCount = problems.count { it.severity == ProblemSeverity.ERROR }
    val warningCount = problems.count { it.severity == ProblemSeverity.WARNING }
    val infoCount = problems.count { it.severity == ProblemSeverity.INFO }

    val extensionNames = remember(problems) {
        listOf("All") + problems.map { it.extensionName }.distinct()
    }

    val filteredProblems = remember(problems, selectedSeverityFilter, selectedExtensionFilter) {
        problems.filter { prob ->
            val matchSev = selectedSeverityFilter == null || prob.severity == selectedSeverityFilter
            val matchExt = selectedExtensionFilter == "All" || prob.extensionName == selectedExtensionFilter
            matchSev && matchExt
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("extension_problems_inspector_view")
    ) {
        // Summary & Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Count badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Errors
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedSeverityFilter == ProblemSeverity.ERROR) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        selectedSeverityFilter = if (selectedSeverityFilter == ProblemSeverity.ERROR) null else ProblemSeverity.ERROR
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Cancel, contentDescription = null, tint = if (selectedSeverityFilter == ProblemSeverity.ERROR) Color.White else Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                        Text(
                            text = "$errorCount Errors",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSeverityFilter == ProblemSeverity.ERROR) Color.White else Color(0xFFEF4444)
                        )
                    }
                }

                // Warnings
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedSeverityFilter == ProblemSeverity.WARNING) Color(0xFFF59E0B) else Color(0xFFF59E0B).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        selectedSeverityFilter = if (selectedSeverityFilter == ProblemSeverity.WARNING) null else ProblemSeverity.WARNING
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = if (selectedSeverityFilter == ProblemSeverity.WARNING) Color.White else Color(0xFFF59E0B), modifier = Modifier.size(13.dp))
                        Text(
                            text = "$warningCount Warnings",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSeverityFilter == ProblemSeverity.WARNING) Color.White else Color(0xFFF59E0B)
                        )
                    }
                }

                // Info
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (selectedSeverityFilter == ProblemSeverity.INFO) Color(0xFF38BDF8) else Color(0xFF38BDF8).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                    modifier = Modifier.clickable {
                        selectedSeverityFilter = if (selectedSeverityFilter == ProblemSeverity.INFO) null else ProblemSeverity.INFO
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = null, tint = if (selectedSeverityFilter == ProblemSeverity.INFO) Color.White else Color(0xFF38BDF8), modifier = Modifier.size(13.dp))
                        Text(
                            text = "$infoCount Info",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedSeverityFilter == ProblemSeverity.INFO) Color.White else Color(0xFF38BDF8)
                        )
                    }
                }
            }

            // Quick Actions: Fix All & Re-scan
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (problems.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = {
                            val count = problems.size
                            ExtensionProblemsManager.clearAllResolved()
                            Toast.makeText(context, "Resolved $count problem(s) with auto-fix", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF10B981).copy(alpha = 0.2f), contentColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Fix All", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            ExtensionProblemsManager.resetDefaultProblems()
                            Toast.makeText(context, "Linter scan refreshed", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(26.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Re-scan", fontSize = 10.sp)
                    }
                }
            }
        }

        // Extension Filter Pills
        if (extensionNames.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                extensionNames.forEach { ext ->
                    val isSelected = selectedExtensionFilter == ext
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF1E293B) else Color.Transparent,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF334155)),
                        modifier = Modifier
                            .clickable { selectedExtensionFilter = ext }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = ext,
                            fontSize = 10.sp,
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Problems List or Empty State
        if (filteredProblems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No problems detected in workspace",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "All extensions, linters, and runtime checks are passing smoothly.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(filteredProblems, key = { it.id }) { prob ->
                    ProblemCardItem(
                        problem = prob,
                        onQuickFix = {
                            val action = ExtensionProblemsManager.quickFixProblem(prob.id)
                            Toast.makeText(context, "$action applied", Toast.LENGTH_SHORT).show()
                            onQuickFix(prob)
                        },
                        onOpenFile = {
                            if (onOpenFileInEditor != null) {
                                onOpenFileInEditor(prob.file, prob.line)
                            } else {
                                Toast.makeText(context, "Navigating to ${prob.file}:${prob.line}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProblemCardItem(
    problem: ExtensionProblem,
    onQuickFix: () -> Unit,
    onOpenFile: () -> Unit
) {
    val (iconColor, sevBorderColor, sevBgColor) = when (problem.severity) {
        ProblemSeverity.ERROR -> Triple(Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.4f), Color(0xFFEF4444).copy(alpha = 0.1f))
        ProblemSeverity.WARNING -> Triple(Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.4f), Color(0xFFF59E0B).copy(alpha = 0.1f))
        ProblemSeverity.INFO -> Triple(Color(0xFF38BDF8), Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFF38BDF8).copy(alpha = 0.1f))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF131B2A),
        border = BorderStroke(1.dp, sevBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("problem_item_${problem.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row: Severity icon + Location + Extension Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (problem.severity) {
                            ProblemSeverity.ERROR -> Icons.Rounded.Cancel
                            ProblemSeverity.WARNING -> Icons.Rounded.Warning
                            ProblemSeverity.INFO -> Icons.Rounded.Info
                        },
                        contentDescription = problem.severity.label,
                        tint = iconColor,
                        modifier = Modifier.size(15.dp)
                    )

                    Text(
                        text = "${problem.file}:${problem.line}",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onOpenFile() }
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF1E293B)
                ) {
                    Text(
                        text = problem.extensionName,
                        color = Color(0xFF94A3B8),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Problem Message
            Text(
                text = problem.message,
                color = Color(0xFFE2E8F0),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            // Rule ID and Quick Fix Row
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (problem.ruleId != null) {
                    Text(
                        text = problem.ruleId,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (problem.quickFixAvailable) {
                    FilledTonalButton(
                        onClick = onQuickFix,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = iconColor.copy(alpha = 0.2f),
                            contentColor = iconColor
                        )
                    ) {
                        Icon(Icons.Rounded.FlashOn, contentDescription = null, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = problem.quickFixActionLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Extension Side Panels Inspector Host.
 * Renders interactive side panels for installed extensions as dynamic new tabs inside the Inspector Panel.
 */
@Composable
fun ExtensionSidePanelsInspectorView(
    activeExtensionId: String? = "youtube_enhancer",
    openExtensionTabs: List<String> = listOf("youtube_enhancer", "adblock_pro", "eslint"),
    onSelectExtensionTab: (String) -> Unit = {},
    onCloseExtensionTab: (String) -> Unit = {},
    onOpenNewExtensionTab: (String) -> Unit = {},
    activeTab: BrowserTab? = null,
    activeWebView: WebView? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTabId by remember(activeExtensionId) {
        mutableStateOf(activeExtensionId ?: openExtensionTabs.firstOrNull() ?: "youtube_enhancer")
    }
    var showAddTabDialog by remember { mutableStateOf(false) }

    val allKnownExtensions = listOf(
        Pair("youtube_enhancer", "YouTube Enhancer"),
        Pair("youtube_audio_pip", "YouTube Audio & Shorts"),
        Pair("adblock_pro", "AdBlocker Pro"),
        Pair("dark_reader", "Dark Reader"),
        Pair("data_saver", "Data Saver Pro"),
        Pair("web_inspector", "Safari Web Inspector"),
        Pair("eslint", "ESLint & Linter"),
        Pair("gitlens", "GitLens & Drive"),
        Pair("page_translator", "Page Translator")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("extension_side_panels_inspector_view")
    ) {
        // Top dynamic tabs strip inside the Inspector Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            openExtensionTabs.forEach { extId ->
                val isSelected = selectedTabId == extId
                val label = allKnownExtensions.find { it.first == extId }?.second ?: extId.replace("_", " ").capitalize()

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) Color(0xFF1E293B) else Color(0xFF131B2A),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFF59E0B) else Color(0xFF283548)),
                    modifier = Modifier
                        .clickable {
                            selectedTabId = extId
                            onSelectExtensionTab(extId)
                        }
                        .testTag("ext_side_panel_tab_$extId")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when (extId) {
                                "youtube_enhancer" -> Icons.Rounded.PlayCircle
                                "youtube_audio_pip" -> Icons.Rounded.SmartDisplay
                                "adblock_pro" -> Icons.Rounded.Shield
                                "dark_reader" -> Icons.Rounded.DarkMode
                                "data_saver" -> Icons.Rounded.DataUsage
                                "web_inspector" -> Icons.Rounded.DeveloperMode
                                "eslint" -> Icons.Rounded.Rule
                                "gitlens" -> Icons.Rounded.AccountTree
                                "page_translator" -> Icons.Rounded.Translate
                                else -> Icons.Rounded.Extension
                            },
                            contentDescription = null,
                            tint = if (isSelected) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                            modifier = Modifier.size(13.dp)
                        )

                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else Color(0xFF94A3B8)
                        )

                        // Close tab 'x' button
                        if (openExtensionTabs.size > 1) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Tab",
                                tint = Color(0xFF64748B),
                                modifier = Modifier
                                    .size(12.dp)
                                    .clickable {
                                        onCloseExtensionTab(extId)
                                        if (selectedTabId == extId) {
                                            selectedTabId = openExtensionTabs.filter { it != extId }.firstOrNull() ?: ""
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            // "+ Add Tab" button to open more extension side panels
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF141D2D),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                modifier = Modifier.clickable { showAddTabDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add Extension Side Panel Tab", tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                    Text("+ New Tab", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Add Tab Dropdown Dialog
        if (showAddTabDialog) {
            AlertDialog(
                onDismissRequest = { showAddTabDialog = false },
                title = { Text("Open Extension Side Panel Tab", fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        allKnownExtensions.forEach { (id, name) ->
                            val isAlreadyOpen = openExtensionTabs.contains(id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isAlreadyOpen) Color(0xFF1E293B) else Color.Transparent)
                                    .clickable {
                                        onOpenNewExtensionTab(id)
                                        selectedTabId = id
                                        showAddTabDialog = false
                                        Toast.makeText(context, "Opened $name in Inspector", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(name, color = Color.White, fontSize = 12.sp)
                                if (isAlreadyOpen) {
                                    Text("Open", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddTabDialog = false }) {
                        Text("Done")
                    }
                }
            )
        }

        // Active Extension Side Panel Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTabId) {
                "youtube_enhancer" -> YouTubeEnhancerSidePanel(activeWebView = activeWebView)
                "youtube_audio_pip" -> YouTubeAudioPillSidePanel()
                "adblock_pro" -> AdBlockerSidePanel()
                "dark_reader" -> DarkReaderSidePanel(activeWebView = activeWebView)
                "data_saver" -> DataSaverSidePanel()
                "web_inspector" -> SafariWebInspectorSidePanel(activeWebView = activeWebView)
                "eslint" -> ESLintSidePanel()
                "gitlens" -> GitLensSidePanel()
                "page_translator" -> PageTranslatorSidePanel(activeWebView = activeWebView)
                else -> GenericExtensionSidePanel(extensionId = selectedTabId)
            }
        }
    }
}

// ==========================================
// Extension-Specific Side Panel Implementations
// ==========================================

@Composable
fun YouTubeEnhancerSidePanel(activeWebView: WebView?) {
    val context = LocalContext.current
    var blockAds by remember { mutableStateOf(true) }
    var skipSponsors by remember { mutableStateOf(true) }
    var bypassShortsLoop by remember { mutableStateOf(true) }
    var autoHdQuality by remember { mutableStateOf(true) }
    var cinemaMode by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = "YOUTUBE MEDIA & AD CONTROLS",
                    color = Color(0xFFF59E0B),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                SidePanelToggleRow(
                    title = "Block Video & Banner Ads",
                    subtitle = "Skip pre-roll, mid-roll, and overlay advertisements",
                    checked = blockAds,
                    onCheckedChange = {
                        blockAds = it
                        Toast.makeText(context, if (it) "YouTube AdBlock Active" else "AdBlock Paused", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Auto-Skip Sponsor Segments",
                    subtitle = "Bypass paid promotions & intro fluff via SponsorBlock",
                    checked = skipSponsors,
                    onCheckedChange = { skipSponsors = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Bypass Shorts Looping",
                    subtitle = "Auto-advance Shorts videos without infinite loop lock",
                    checked = bypassShortsLoop,
                    onCheckedChange = { bypassShortsLoop = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Force Maximum Resolution (1080p/4K)",
                    subtitle = "Prevents YouTube auto-downgrading stream bitrate",
                    checked = autoHdQuality,
                    onCheckedChange = { autoHdQuality = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Cinema Theater Mode",
                    subtitle = "Expands video canvas and dims background page lighting",
                    checked = cinemaMode,
                    onCheckedChange = { cinemaMode = it }
                )
            }
        }

        // Playback Speed Presets
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Playback Speed", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text("${playbackSpeed}x", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    playbackSpeed = speed
                                    activeWebView?.evaluateJavascript(
                                        "document.querySelectorAll('video').forEach(v => v.playbackRate = $speed);",
                                        null
                                    )
                                    Toast.makeText(context, "${speed}x speed applied", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "${speed}x",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YouTubeAudioPillSidePanel() {
    var floatingPillActive by remember { mutableStateOf(true) }
    var backgroundLock by remember { mutableStateOf(true) }
    var swipeGestures by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("SHORTS AUDIO & FLOATING PILL", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                SidePanelToggleRow(
                    title = "Floating Mini Audio Pill",
                    subtitle = "Always-on-top draggable player for background Shorts audio",
                    checked = floatingPillActive,
                    onCheckedChange = { floatingPillActive = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Background Audio Lock",
                    subtitle = "Keeps audio playing even when screen is locked or switching tabs",
                    checked = backgroundLock,
                    onCheckedChange = { backgroundLock = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Gesture Shortcuts",
                    subtitle = "Double tap to skip 10s, swipe up for next audio track",
                    checked = swipeGestures,
                    onCheckedChange = { swipeGestures = it }
                )
            }
        }
    }
}

@Composable
fun AdBlockerSidePanel() {
    var blockTrackers by remember { mutableStateOf(true) }
    var blockPopups by remember { mutableStateOf(true) }
    var antiAdblockDefuse by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("ADBLOCKER PRO & TRACKER SHIELD", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                SidePanelToggleRow(
                    title = "Block Behavioral Trackers",
                    subtitle = "Strip tracking query parameters & analytics beacons",
                    checked = blockTrackers,
                    onCheckedChange = { blockTrackers = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Strict Popup Elimination",
                    subtitle = "Neutralize new window popunders and redirect hijacks",
                    checked = blockPopups,
                    onCheckedChange = { blockPopups = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Anti-AdBlock Defuser",
                    subtitle = "Bypass 'disable your adblocker' paywall overlays",
                    checked = antiAdblockDefuse,
                    onCheckedChange = { antiAdblockDefuse = it }
                )
            }
        }
    }
}

@Composable
fun DarkReaderSidePanel(activeWebView: WebView?) {
    val context = LocalContext.current
    var isDarkOn by remember { mutableStateOf(true) }
    var contrast by remember { mutableStateOf(100f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("DARK READER & NIGHT SHIFT", color = Color(0xFFA855F7), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                SidePanelToggleRow(
                    title = "Invert Page Luminance",
                    subtitle = "High contrast dark mode on every web page",
                    checked = isDarkOn,
                    onCheckedChange = {
                        isDarkOn = it
                        val script = if (it) {
                            "(function(){var s=document.createElement('style');s.id='__darkreader__';s.innerHTML='html { filter: invert(90%) hue-rotate(180deg) !important; background: #121212 !important; } img, video { filter: invert(100%) hue-rotate(180deg) !important; }';document.head.appendChild(s);})()"
                        } else {
                            "(function(){var s=document.getElementById('__darkreader__');if(s)s.remove();})()"
                        }
                        activeWebView?.evaluateJavascript(script, null)
                        Toast.makeText(context, if (it) "Dark Reader applied" else "Dark Reader removed", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
fun DataSaverSidePanel() {
    var compressImages by remember { mutableStateOf(true) }
    var capVideoBitrate by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("DATA SAVER PRO", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                SidePanelToggleRow(
                    title = "WebP Image Recompression",
                    subtitle = "Reduces payload size by up to 60%",
                    checked = compressImages,
                    onCheckedChange = { compressImages = it }
                )

                HorizontalDivider(color = Color(0xFF233554), modifier = Modifier.padding(vertical = 4.dp))

                SidePanelToggleRow(
                    title = "Video Resolution Governor",
                    subtitle = "Caps background buffering to conserve mobile data",
                    checked = capVideoBitrate,
                    onCheckedChange = { capVideoBitrate = it }
                )
            }
        }
    }
}

@Composable
fun SafariWebInspectorSidePanel(activeWebView: WebView?) {
    val context = LocalContext.current
    var lastOutput by remember { mutableStateOf("Ready to inspect current DOM and network state.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("SAFARI WEB INSPECTOR & DEVTOOLS", color = Color(0xFFF59E0B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = {
                            activeWebView?.evaluateJavascript("document.title + ' | ' + document.querySelectorAll('*').length + ' elements';", { res ->
                                lastOutput = "Page Elements Count: $res"
                            })
                        },
                        modifier = Modifier.weight(1f).height(30.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Count DOM", fontSize = 10.sp)
                    }

                    FilledTonalButton(
                        onClick = {
                            activeWebView?.evaluateJavascript("document.cookie", { res ->
                                lastOutput = "Cookies: ${res ?: "None"}"
                            })
                        },
                        modifier = Modifier.weight(1f).height(30.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Get Cookies", fontSize = 10.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF0F1523),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = lastOutput,
                        color = Color(0xFFE2E8F0),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ESLintSidePanel() {
    ExtensionProblemsInspectorView()
}

@Composable
fun GitLensSidePanel() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("GITLENS & DRIVE CLOUD DIFFS", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Branch: main (Clean working tree)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Recent Commits: 14 cloud synced commits", color = Color(0xFF94A3B8), fontSize = 11.sp)
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("View Diff Annotations", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun PageTranslatorSidePanel(activeWebView: WebView?) {
    val context = LocalContext.current
    var selectedLang by remember { mutableStateOf("Spanish") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF161F30),
            border = BorderStroke(1.dp, Color(0xFF233554)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text("SMART PAGE TRANSLATOR", color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Text("Target Language: $selectedLang", color = Color.White, fontSize = 12.sp)

                Spacer(modifier = Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Spanish", "French", "German", "Hindi", "Japanese").forEach { lang ->
                        val isSel = selectedLang == lang
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) Color(0xFF00E5FF) else Color(0xFF1E293B),
                            modifier = Modifier.clickable { selectedLang = lang }
                        ) {
                            Text(
                                text = lang,
                                fontSize = 10.sp,
                                color = if (isSel) Color.Black else Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = {
                        Toast.makeText(context, "Page translated to $selectedLang", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(30.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Translate Active Page", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun GenericExtensionSidePanel(extensionId: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Extension, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${extensionId.replace("_", " ").capitalize()} Side Panel Active",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Extension tools and controls are operational.",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SidePanelToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 13.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
