package com.example.ui.screens.research

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.research.*
import com.example.ui.theme.GVONEPrimary
import kotlinx.coroutines.launch

enum class ResearchWorkspaceTab(val label: String, val icon: ImageVector) {
    SOURCES("Sources", Icons.Rounded.Article),
    NOTES("Notes", Icons.Rounded.EditNote),
    AI_ASSISTANT("AI Synthesis", Icons.Rounded.AutoAwesome),
    EVIDENCE_MAP("Evidence Map", Icons.Rounded.AccountTree)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchWorkspaceScreen(
    manager: ResearchWorkspaceManager,
    currentTabUrl: String = "",
    currentTabTitle: String = "",
    onOpenUrlInTab: (String) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sources by manager.sources.collectAsStateWithLifecycle()
    val highlights by manager.highlights.collectAsStateWithLifecycle()
    val evidenceClaims by manager.evidenceClaims.collectAsStateWithLifecycle()
    val aiSyntheses by manager.aiSyntheses.collectAsStateWithLifecycle()
    val isAiSynthesizing by manager.isAiSynthesizing.collectAsStateWithLifecycle()
    val statusMessage by manager.statusMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(ResearchWorkspaceTab.SOURCES) }
    var selectedSourceForDetail by remember { mutableStateOf<ResearchSource?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddClaimDialog by remember { mutableStateOf(false) }
    var selectedCitationStyle by remember { mutableStateOf(CitationStyle.APA_7) }
    var exportedContentText by remember { mutableStateOf("") }
    var aiPromptInput by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            manager.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Research Workspace",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "${sources.size} Sources • ${highlights.size} Notes",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Auto-citations • Evidence mapping • Gemini grounding",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("research_workspace_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                exportedContentText = manager.exportReferences(selectedCitationStyle)
                                showExportDialog = true
                            }
                        },
                        modifier = Modifier.testTag("export_references_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Share, contentDescription = "Export References", tint = GVONEPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F141C))
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF0B0F15),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Header Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF131A24),
                contentColor = GVONEPrimary,
                divider = { HorizontalDivider(color = Color(0xFF243042), thickness = 1.dp) }
            ) {
                ResearchWorkspaceTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab == tab) GVONEPrimary else Color(0xFF94A3B8)
                                )
                                Text(
                                    text = tab.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == tab) Color.White else Color(0xFF94A3B8)
                                )
                            }
                        }
                    )
                }
            }

            // Tab Content
            when (selectedTab) {
                ResearchWorkspaceTab.SOURCES -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Quick Action: Save Current Page
                        if (currentTabUrl.isNotBlank() && currentTabUrl != "about:blank") {
                            item {
                                Surface(
                                    color = Color(0xFF162030),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, GVONEPrimary.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Save Current Webpage as Source",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = currentTabTitle.ifBlank { currentTabUrl },
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                manager.saveWebpageAsSource(
                                                    url = currentTabUrl,
                                                    title = currentTabTitle,
                                                    excerpt = "Captured from active browser session.",
                                                    fullText = "Captured article and documentation from $currentTabUrl",
                                                    author = "Web Author"
                                                )
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Rounded.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Save Source", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        items(sources) { src ->
                            ResearchSourceCard(
                                source = src,
                                onSelect = { selectedSourceForDetail = src },
                                onCopyCitation = { style ->
                                    val citation = manager.generateCitation(src, style)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Citation", citation))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Copied ${style.label} citation to clipboard")
                                    }
                                },
                                onDelete = { manager.deleteSource(src.id) },
                                onOpenUrl = { onOpenUrlInTab(src.url) }
                            )
                        }
                    }
                }

                ResearchWorkspaceTab.NOTES -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Annotated Notes & Highlights (${highlights.size})",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = { showAddNoteDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Note", fontSize = 12.sp)
                                }
                            }
                        }

                        items(highlights) { hl ->
                            Surface(
                                color = Color(0xFF141B26),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(hl.colorHex)))
                                            Text(
                                                text = hl.sourceTitle,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        IconButton(
                                            onClick = { manager.deleteHighlight(hl.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(imageVector = Icons.Rounded.Close, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Surface(
                                        color = Color(0xFF0F141C),
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "\"${hl.selectedText}\"",
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 13.sp,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }

                                    if (hl.note.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = hl.note,
                                            color = Color(0xFFF1F5F9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }

                                    if (hl.tags.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            hl.tags.forEach { tag ->
                                                Surface(
                                                    color = Color(0xFF1E293B),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = tag,
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ResearchWorkspaceTab.AI_ASSISTANT -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Surface(
                                color = Color(0xFF161E2E),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D52)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(24.dp))
                                        Column {
                                            Text(text = "Gemini Research Synthesis", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                            Text(text = "Grounded with your ${sources.size} saved sources + connected Drive & Notion data.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Quick prompt chips
                                    Text(text = "Quick Inquiries:", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        AssistChip(
                                            onClick = {
                                                aiPromptInput = "Synthesize key arguments and common themes across all saved sources."
                                            },
                                            label = { Text("Synthesize Themes", fontSize = 11.sp) }
                                        )
                                        AssistChip(
                                            onClick = {
                                                aiPromptInput = "Identify any contradictions, counterarguments, or open questions."
                                            },
                                            label = { Text("Find Contradictions", fontSize = 11.sp) }
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    OutlinedTextField(
                                        value = aiPromptInput,
                                        onValueChange = { aiPromptInput = it },
                                        placeholder = { Text("Ask a research question across your sources...") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFA78BFA),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            if (aiPromptInput.isNotBlank()) {
                                                coroutineScope.launch {
                                                    manager.synthesizeResearchQuery(aiPromptInput)
                                                }
                                            }
                                        },
                                        enabled = !isAiSynthesizing && aiPromptInput.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        if (isAiSynthesizing) {
                                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Synthesizing Research Evidence...")
                                        } else {
                                            Icon(imageVector = Icons.Rounded.Psychology, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Synthesize with Sources & Grounding")
                                        }
                                    }
                                }
                            }
                        }

                        items(aiSyntheses) { syn ->
                            Surface(
                                color = Color(0xFF121824),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(text = syn.query, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(text = syn.timestamp, color = Color(0xFF64748B), fontSize = 11.sp)

                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(text = syn.synthesizedSummary, color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 20.sp)

                                    if (syn.keyFindings.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(text = "Key Takeaways:", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        syn.keyFindings.forEach { finding ->
                                            Row(modifier = Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text("•", color = Color(0xFF38BDF8))
                                                Text(finding, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                            }
                                        }
                                    }

                                    if (syn.suggestedFollowUps.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(text = "Suggested Inquiries:", color = Color(0xFFA78BFA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        syn.suggestedFollowUps.forEach { q ->
                                            Text(
                                                text = "→ $q",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp,
                                                modifier = Modifier
                                                    .clickable { aiPromptInput = q }
                                                    .padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ResearchWorkspaceTab.EVIDENCE_MAP -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(text = "Visual Evidence Map", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "Claims verified against citations & notes", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { showAddClaimDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(imageVector = Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Claim", fontSize = 12.sp)
                                }
                            }
                        }

                        items(evidenceClaims) { claim ->
                            EvidenceClaimCard(claim = claim, sources = sources, highlights = highlights)
                        }
                    }
                }
            }
        }
    }

    // Modal: Export References Sheet
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.Share, contentDescription = null, tint = GVONEPrimary)
                    Text("Export References & Citations")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose citation standard. File is automatically saved to /gvone_fs/Documents/.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CitationStyle.values().forEach { style ->
                            FilterChip(
                                selected = selectedCitationStyle == style,
                                onClick = {
                                    selectedCitationStyle = style
                                    coroutineScope.launch {
                                        exportedContentText = manager.exportReferences(style)
                                    }
                                },
                                label = { Text(style.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF0F141C),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            item {
                                Text(
                                    text = exportedContentText,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("References", exportedContentText))
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Copied references to clipboard")
                        }
                        showExportDialog = false
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Done") }
            },
            containerColor = Color(0xFF151C28)
        )
    }

    // Modal: Add Note Dialog
    if (showAddNoteDialog) {
        var noteQuote by remember { mutableStateOf("") }
        var noteComment by remember { mutableStateOf("") }
        var noteTag by remember { mutableStateOf("#insight") }
        var selectedSourceId by remember { mutableStateOf(sources.firstOrNull()?.id ?: "") }

        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("Add Research Note & Highlight") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = noteQuote,
                        onValueChange = { noteQuote = it },
                        label = { Text("Quoted / Highlighted Text") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteComment,
                        onValueChange = { noteComment = it },
                        label = { Text("Your Analysis / Annotation") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteTag,
                        onValueChange = { noteTag = it },
                        label = { Text("Tag (e.g. #architecture)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteQuote.isNotBlank()) {
                            manager.createHighlightNote(
                                sourceId = selectedSourceId,
                                selectedText = noteQuote,
                                note = noteComment,
                                tags = listOf(noteTag)
                            )
                            showAddNoteDialog = false
                        }
                    }
                ) {
                    Text("Save Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF151C28)
        )
    }

    // Modal: Add Claim Dialog
    if (showAddClaimDialog) {
        var claimText by remember { mutableStateOf("") }
        var claimSynthesis by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddClaimDialog = false },
            title = { Text("Add Evidence Claim / Hypothesis") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = claimText,
                        onValueChange = { claimText = it },
                        label = { Text("Hypothesis or Claim") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = claimSynthesis,
                        onValueChange = { claimSynthesis = it },
                        label = { Text("Supporting Synthesis & Evidence") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (claimText.isNotBlank()) {
                            manager.addEvidenceClaim(
                                hypothesis = claimText,
                                status = ClaimVerificationStatus.SUPPORTED,
                                sourceIds = sources.take(1).map { it.id },
                                highlightIds = highlights.take(1).map { it.id },
                                synthesis = claimSynthesis
                            )
                            showAddClaimDialog = false
                        }
                    }
                ) {
                    Text("Add to Evidence Map")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddClaimDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF151C28)
        )
    }
}

@Composable
fun ResearchSourceCard(
    source: ResearchSource,
    onSelect: () -> Unit,
    onCopyCitation: (CitationStyle) -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: () -> Unit
) {
    Surface(
        color = Color(0xFF141B26),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Rounded.Public, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                    Text(text = source.domain, color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "• ${source.readingTimeMinutes} min read", color = Color(0xFF64748B), fontSize = 11.sp)
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = source.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(6.dp))
            Text(text = source.excerpt, color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${source.author} (${source.publicationDate})",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { onCopyCitation(CitationStyle.APA_7) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Cite APA", fontSize = 10.sp)
                    }

                    OutlinedButton(
                        onClick = { onCopyCitation(CitationStyle.BIBTEX) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("BibTeX", fontSize = 10.sp)
                    }

                    IconButton(onClick = onOpenUrl, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Rounded.OpenInNew, contentDescription = "Open", tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EvidenceClaimCard(
    claim: EvidenceClaim,
    sources: List<ResearchSource>,
    highlights: List<ResearchHighlight>
) {
    Surface(
        color = Color(0xFF141B26),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = Color(claim.status.colorHex).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${claim.status.label} (${claim.confidenceScore}%)",
                        color = Color(claim.status.colorHex),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = "${claim.linkedSourceIds.size} Sources • ${claim.linkedHighlightIds.size} Notes",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = claim.hypothesisOrClaim, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)

            if (claim.synthesisSummary.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Evidence: ${claim.synthesisSummary}", color = Color(0xFFCBD5E1), fontSize = 12.sp)
            }

            if (claim.counterarguments.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = "Caveats: ${claim.counterarguments}", color = Color(0xFFF59E0B), fontSize = 11.sp)
            }
        }
    }
}
