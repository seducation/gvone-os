package com.example.ui.screens.connectors

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.connector.*
import com.example.ui.theme.GVONEPrimary
import kotlinx.coroutines.launch

enum class ConnectorHubTab(val label: String, val icon: ImageVector) {
    CONNECTORS("Connectors", Icons.Rounded.Hub),
    AI_BRIDGE("AI Data Bridge", Icons.Rounded.Psychology),
    CLOUD_FILES("Cloud Files & Repos", Icons.Rounded.CloudSync)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorHubScreen(
    manager: ConnectorHubManager,
    onOpenUrlInTab: (String) -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connectors by manager.connectors.collectAsStateWithLifecycle()
    val driveFiles by manager.driveFiles.collectAsStateWithLifecycle()
    val gitHubRepos by manager.gitHubRepos.collectAsStateWithLifecycle()
    val slackChannels by manager.slackChannels.collectAsStateWithLifecycle()
    val notionPages by manager.notionPages.collectAsStateWithLifecycle()
    val dropboxFiles by manager.dropboxFiles.collectAsStateWithLifecycle()
    val statusMessage by manager.actionStatusMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(ConnectorHubTab.CONNECTORS) }
    var selectedConnectorForDetails by remember { mutableStateOf<ConnectorAccount?>(null) }
    var showSlackPostDialog by remember { mutableStateOf(false) }
    var slackTargetChannel by remember { mutableStateOf<SlackChannelItem?>(null) }
    var slackMessageInput by remember { mutableStateOf("") }
    var aiTestQuery by remember { mutableStateOf("Show research papers and PRs") }
    var aiTestResults by remember { mutableStateOf<List<ConnectedKnowledgeSnippet>>(emptyList()) }

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
                                text = "Connector Hub",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = GVONEPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "AI Bridge Active",
                                    color = GVONEPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Drive • GitHub • Slack • Notion • Dropbox",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("connector_hub_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
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
            // Tab Selector Row
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF131A24),
                contentColor = GVONEPrimary,
                divider = { HorizontalDivider(color = Color(0xFF243042), thickness = 1.dp) }
            ) {
                ConnectorHubTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
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
                ConnectorHubTab.CONNECTORS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Surface(
                                color = Color(0xFF161E2E),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2A374A)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Universal Connected Workspace",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Manage accounts, granular permissions, and expose connected files & notes to Gemini AI.",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        items(connectors) { connector ->
                            ConnectorCard(
                                connector = connector,
                                onToggleAiAccess = { enabled ->
                                    manager.toggleAiDataAccess(connector.id, enabled)
                                },
                                onConfigurePermissions = {
                                    selectedConnectorForDetails = connector
                                },
                                onToggleConnection = {
                                    if (connector.status == ConnectionStatus.CONNECTED) {
                                        manager.disconnectService(connector.id)
                                    } else {
                                        manager.connectService(connector.type)
                                    }
                                }
                            )
                        }
                    }
                }

                ConnectorHubTab.AI_BRIDGE -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Surface(
                                color = Color(0xFF151C28),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Psychology,
                                            contentDescription = null,
                                            tint = Color(0xFFA78BFA),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "AI Connected Data Grounding",
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "AI ko connected data access karwana: Gemini AI & Browser Agent query your Drive, GitHub, Slack, and Notion data.",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = Color(0xFF243042))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "Active AI Knowledge Sources",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    connectors.forEach { conn ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(10.dp)
                                                        .clip(CircleShape)
                                                        .background(if (conn.allowAiDataAccess && conn.status == ConnectionStatus.CONNECTED) Color(0xFF10B981) else Color(0xFF64748B))
                                                )
                                                Text(
                                                    text = conn.type.displayName,
                                                    color = Color.White,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "(${conn.indexedItemCount} items indexed)",
                                                    color = Color(0xFF94A3B8),
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Switch(
                                                checked = conn.allowAiDataAccess && conn.status == ConnectionStatus.CONNECTED,
                                                onCheckedChange = { manager.toggleAiDataAccess(conn.id, it) },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = Color(0xFFA78BFA)
                                                ),
                                                modifier = Modifier.height(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Test Knowledge Grounding Query
                        item {
                            Surface(
                                color = Color(0xFF161E2E),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B3A4F)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Test AI Query across Connected Data",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Verify what snippets Gemini AI retrieves from your connected accounts for a given prompt.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )

                                    OutlinedTextField(
                                        value = aiTestQuery,
                                        onValueChange = { aiTestQuery = it },
                                        placeholder = { Text("Ask about papers, PRs, Slack messages...") },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = {
                                                    aiTestResults = manager.queryConnectedDataForAi(aiTestQuery)
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Search,
                                                    contentDescription = "Search",
                                                    tint = Color(0xFFA78BFA)
                                                )
                                            }
                                        },
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
                                            aiTestResults = manager.queryConnectedDataForAi(aiTestQuery)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Retrieve Connected Grounding Context")
                                    }

                                    if (aiTestResults.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "Grounding Results (${aiTestResults.size} matches):",
                                            color = Color(0xFF38BDF8),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        aiTestResults.forEach { snip ->
                                            Surface(
                                                color = Color(0xFF0F172A),
                                                shape = RoundedCornerShape(8.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "[${snip.connectorType.displayName}] ${snip.itemTitle}",
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Surface(
                                                            color = Color(0xFF334155),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = snip.category,
                                                                color = Color(0xFFE2E8F0),
                                                                fontSize = 10.sp,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = snip.contentSnippet,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 12.sp
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

                ConnectorHubTab.CLOUD_FILES -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Google Drive section
                        item {
                            ConnectorSectionHeader(
                                title = "Google Drive Documents",
                                subtitle = "Sync directly into /gvone_fs/Cloud/GoogleDrive/",
                                icon = Icons.Rounded.FolderShared,
                                color = Color(0xFF4285F4)
                            )
                        }

                        items(driveFiles) { file ->
                            Surface(
                                color = Color(0xFF161E2E),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF4285F4),
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = file.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${file.sizeFormatted} • Modified ${file.modifiedTime}",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                manager.syncDriveFileToLocal(file.id)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (file.isSyncedLocally) Color(0xFF1E293B) else Color(0xFF2563EB)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (file.isSyncedLocally) Icons.Rounded.Check else Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (file.isSyncedLocally) "Synced" else "Sync FS",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // GitHub section
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            ConnectorSectionHeader(
                                title = "GitHub Repositories",
                                subtitle = "Clone into /gvone_fs/Projects/ for Terminal & Editor",
                                icon = Icons.Rounded.Code,
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        items(gitHubRepos) { repo ->
                            Surface(
                                color = Color(0xFF161E2E),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = repo.fullName,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (repo.isPrivate) {
                                                Surface(color = Color(0xFF334155), shape = RoundedCornerShape(4.dp)) {
                                                    Text("Private", color = Color(0xFF94A3B8), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                        }
                                        Text(
                                            text = repo.description,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${repo.language} • ⭐ ${repo.stars} • Issues: ${repo.openIssues}",
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                manager.cloneGitHubRepo(repo.id)
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (repo.isClonedLocally) Color(0xFF1E293B) else Color(0xFF334155)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (repo.isClonedLocally) Icons.Rounded.Check else Icons.Rounded.Terminal,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (repo.isClonedLocally) "Cloned" else "Clone FS",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Slack section
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            ConnectorSectionHeader(
                                title = "Slack Channels & Team",
                                subtitle = "Share notes, summaries, and post updates",
                                icon = Icons.Rounded.Chat,
                                color = Color(0xFFE879F9)
                            )
                        }

                        items(slackChannels) { channel ->
                            Surface(
                                color = Color(0xFF161E2E),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "#${channel.name}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${channel.memberCount} members • ${channel.topic}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            slackTargetChannel = channel
                                            showSlackPostDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(imageVector = Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Post", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Granular Permissions & Details
    selectedConnectorForDetails?.let { conn ->
        AlertDialog(
            onDismissRequest = { selectedConnectorForDetails = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Rounded.Security, contentDescription = null, tint = GVONEPrimary)
                    Text("${conn.type.displayName} Permissions")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Account: ${conn.emailOrHandle} • ${conn.organization}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    HorizontalDivider(color = Color(0xFF243042))

                    conn.permissions.forEach { perm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = perm.title,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = perm.description,
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                            Checkbox(
                                checked = perm.isGranted,
                                onCheckedChange = { granted ->
                                    manager.togglePermission(conn.id, perm.id, granted)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = GVONEPrimary)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedConnectorForDetails = null }) {
                    Text("Done")
                }
            },
            containerColor = Color(0xFF151C28),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Modal: Slack Quick Post
    if (showSlackPostDialog && slackTargetChannel != null) {
        AlertDialog(
            onDismissRequest = { showSlackPostDialog = false },
            title = { Text("Post to #${slackTargetChannel?.name}") },
            text = {
                Column {
                    Text("Send message or research highlight to Slack channel:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = slackMessageInput,
                        onValueChange = { slackMessageInput = it },
                        placeholder = { Text("Type message...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            manager.postSlackMessage(slackTargetChannel!!.id, slackMessageInput)
                            slackMessageInput = ""
                            showSlackPostDialog = false
                        }
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSlackPostDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF151C28)
        )
    }
}

@Composable
fun ConnectorCard(
    connector: ConnectorAccount,
    onToggleAiAccess: (Boolean) -> Unit,
    onConfigurePermissions: () -> Unit,
    onToggleConnection: () -> Unit
) {
    Surface(
        color = Color(0xFF141B26),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(connector.type.brandColorHex).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = connector.type.displayName.take(1),
                            color = Color(connector.type.brandColorHex),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = connector.type.displayName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = connector.emailOrHandle,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                }

                Surface(
                    color = when (connector.status) {
                        ConnectionStatus.CONNECTED -> Color(0xFF10B981).copy(alpha = 0.15f)
                        ConnectionStatus.SYNCING -> Color(0xFF38BDF8).copy(alpha = 0.15f)
                        ConnectionStatus.DISCONNECTED -> Color(0xFF64748B).copy(alpha = 0.15f)
                        ConnectionStatus.ACTION_REQUIRED -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = connector.status.name,
                        color = when (connector.status) {
                            ConnectionStatus.CONNECTED -> Color(0xFF10B981)
                            ConnectionStatus.SYNCING -> Color(0xFF38BDF8)
                            ConnectionStatus.DISCONNECTED -> Color(0xFF94A3B8)
                            ConnectionStatus.ACTION_REQUIRED -> Color(0xFFF59E0B)
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = connector.storageOrItemCountText,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(10.dp))

            // AI Data Access Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Psychology,
                        contentDescription = null,
                        tint = if (connector.allowAiDataAccess && connector.status == ConnectionStatus.CONNECTED) Color(0xFFA78BFA) else Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "AI Data Access",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Allow Gemini AI to search and read data",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = connector.allowAiDataAccess && connector.status == ConnectionStatus.CONNECTED,
                    onCheckedChange = onToggleAiAccess,
                    enabled = connector.status == ConnectionStatus.CONNECTED,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFA78BFA)
                    ),
                    modifier = Modifier.height(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onConfigurePermissions,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Permissions", fontSize = 12.sp)
                }

                Button(
                    onClick = onToggleConnection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connector.status == ConnectionStatus.CONNECTED) Color(0xFF334155) else Color(connector.type.brandColorHex)
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    Text(
                        text = if (connector.status == ConnectionStatus.CONNECTED) "Disconnect" else "Connect",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectorSectionHeader(title: String, subtitle: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Column {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
        }
    }
}
