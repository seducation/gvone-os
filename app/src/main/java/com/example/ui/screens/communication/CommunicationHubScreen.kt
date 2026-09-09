package com.example.ui.screens.communication

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.communication.*
import com.example.ui.theme.GVONEPrimary
import kotlinx.coroutines.launch

enum class CommHubTab(val label: String, val icon: ImageVector) {
    UNIFIED_INBOX("Unified Inbox", Icons.Rounded.Inbox),
    NOTIFICATIONS("Notifications", Icons.Rounded.Notifications),
    AI_DIGEST("AI Triage Digest", Icons.Rounded.AutoAwesome)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunicationHubScreen(
    manager: CommunicationHubManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val messages by manager.messages.collectAsStateWithLifecycle()
    val notifications by manager.notifications.collectAsStateWithLifecycle()
    val triageDigest by manager.triageDigest.collectAsStateWithLifecycle()
    val isGeneratingDigest by manager.isGeneratingDigest.collectAsStateWithLifecycle()
    val statusMessage by manager.statusMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(CommHubTab.UNIFIED_INBOX) }
    var selectedServiceFilter by remember { mutableStateOf<CommServiceType?>(null) }
    var filterOnlyUnread by remember { mutableStateOf(false) }
    var filterOnlyMentions by remember { mutableStateOf(false) }

    var selectedMessageForReading by remember { mutableStateOf<CommMessage?>(null) }
    var showQuickComposeDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val unreadCount = remember(messages) { messages.count { it.isUnread } }
    val unreadNotifCount = remember(notifications) { notifications.count { !it.isRead } }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            manager.clearStatusMessage()
        }
    }

    val filteredMessages = remember(messages, selectedServiceFilter, filterOnlyUnread, filterOnlyMentions) {
        messages.filter { msg ->
            val matchService = selectedServiceFilter == null || msg.serviceType == selectedServiceFilter
            val matchUnread = !filterOnlyUnread || msg.isUnread
            val matchMention = !filterOnlyMentions || msg.isMention
            matchService && matchUnread && matchMention
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Communication Hub",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (unreadCount > 0) {
                                Surface(
                                    color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$unreadCount New",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Gmail • Slack • Discord • Microsoft Teams",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("comm_hub_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showQuickComposeDialog = true }) {
                        Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Compose", tint = GVONEPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F141C))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQuickComposeDialog = true },
                containerColor = GVONEPrimary,
                contentColor = Color.Black
            ) {
                Icon(imageVector = Icons.AutoMirrored.Rounded.Send, contentDescription = "Quick Send")
            }
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
                CommHubTab.values().forEach { tab ->
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
                                    text = if (tab == CommHubTab.NOTIFICATIONS && unreadNotifCount > 0) "${tab.label} ($unreadNotifCount)" else tab.label,
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
                CommHubTab.UNIFIED_INBOX -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Filter Chips Bar
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedServiceFilter == null,
                                    onClick = { selectedServiceFilter = null },
                                    label = { Text("All Services", fontSize = 12.sp) }
                                )
                            }

                            items(CommServiceType.values()) { srv ->
                                FilterChip(
                                    selected = selectedServiceFilter == srv,
                                    onClick = { selectedServiceFilter = if (selectedServiceFilter == srv) null else srv },
                                    label = { Text(srv.displayName, fontSize = 12.sp) }
                                )
                            }

                            item {
                                FilterChip(
                                    selected = filterOnlyUnread,
                                    onClick = { filterOnlyUnread = !filterOnlyUnread },
                                    label = { Text("Unread", fontSize = 12.sp) }
                                )
                            }

                            item {
                                FilterChip(
                                    selected = filterOnlyMentions,
                                    onClick = { filterOnlyMentions = !filterOnlyMentions },
                                    label = { Text("@ Mentions", fontSize = 12.sp) }
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B))

                        // Message List
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredMessages) { msg ->
                                CommMessageItemCard(
                                    message = msg,
                                    onClick = {
                                        manager.markMessageAsRead(msg.id)
                                        selectedMessageForReading = msg
                                    },
                                    onToggleStar = { manager.toggleStarMessage(msg.id) }
                                )
                            }
                        }
                    }
                }

                CommHubTab.NOTIFICATIONS -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Cross-Platform Notifications",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { manager.markAllNotificationsAsRead() }) {
                                        Text("Mark All Read", fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { manager.clearAllNotifications() }) {
                                        Text("Clear", fontSize = 12.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }

                        items(notifications) { notif ->
                            Surface(
                                color = if (notif.isRead) Color(0xFF131A24) else Color(0xFF182232),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (notif.isRead) Color(0xFF1E293B) else GVONEPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { manager.markNotificationAsRead(notif.id) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(notif.serviceType.brandColorHex).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = notif.serviceType.displayName.take(1),
                                            color = Color(notif.serviceType.brandColorHex),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = notif.serviceType.displayName,
                                                color = Color(notif.serviceType.brandColorHex),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = notif.timestamp,
                                                color = Color(0xFF64748B),
                                                fontSize = 11.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (notif.isRead) FontWeight.Medium else FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = notif.message,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                CommHubTab.AI_DIGEST -> {
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
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D52)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Icon(imageVector = Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(24.dp))
                                            Column {
                                                Text(text = "AI Priority Triage & Digest", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "Generated across Gmail, Slack, Discord & Teams", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    manager.generateAiTriageDigest()
                                                }
                                            },
                                            enabled = !isGeneratingDigest
                                        ) {
                                            if (isGeneratingDigest) {
                                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFFA78BFA), strokeWidth = 2.dp)
                                            } else {
                                                Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh Digest", tint = Color(0xFFA78BFA))
                                            }
                                        }
                                    }

                                    triageDigest?.let { digest ->
                                        Spacer(modifier = Modifier.height(14.dp))
                                        HorizontalDivider(color = Color(0xFF243042))
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(text = "Executive Summary", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = digest.executiveSummary, color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 19.sp)

                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(text = "Urgent Action Items", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        digest.urgentActionItems.forEach { item ->
                                            Row(modifier = Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(imageVector = Icons.Rounded.WarningAmber, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                Text(text = item, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(text = "Key Threads to Watch", color = Color(0xFF10B981), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        digest.keyThreadsToWatch.forEach { thread ->
                                            Row(modifier = Modifier.padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("•", color = Color(0xFF10B981))
                                                Text(text = thread, color = Color(0xFF94A3B8), fontSize = 12.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(text = "Last updated: ${digest.generatedAt}", color = Color(0xFF64748B), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Message Thread Reader Dialog
    selectedMessageForReading?.let { msg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForReading = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(msg.serviceType.brandColorHex).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = msg.serviceType.displayName,
                            color = Color(msg.serviceType.brandColorHex),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(msg.subjectOrTopic, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("From: ${msg.senderName} (${msg.senderHandleOrEmail})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("Channel/Folder: ${msg.recipientOrChannel} • ${msg.timestamp}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    HorizontalDivider(color = Color(0xFF243042))
                    Text(text = msg.body, color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 20.sp)

                    if (msg.attachments.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Attachments: ${msg.attachments.joinToString(", ")}", color = Color(0xFF38BDF8), fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedMessageForReading = null
                    showQuickComposeDialog = true
                }) {
                    Icon(imageVector = Icons.Rounded.Reply, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reply")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMessageForReading = null }) { Text("Close") }
            },
            containerColor = Color(0xFF151C28)
        )
    }

    // Modal: Quick Compose Dialog
    if (showQuickComposeDialog) {
        var composeService by remember { mutableStateOf(CommServiceType.GMAIL) }
        var composeTarget by remember { mutableStateOf("") }
        var composeSubject by remember { mutableStateOf("") }
        var composeBody by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showQuickComposeDialog = false },
            title = { Text("Quick Compose & Reply") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select Service:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CommServiceType.values().forEach { srv ->
                            FilterChip(
                                selected = composeService == srv,
                                onClick = { composeService = srv },
                                label = { Text(srv.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = composeTarget,
                        onValueChange = { composeTarget = it },
                        label = { Text("Recipient / Channel (e.g. #research or email)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (composeService == CommServiceType.GMAIL || composeService == CommServiceType.TEAMS) {
                        OutlinedTextField(
                            value = composeSubject,
                            onValueChange = { composeSubject = it },
                            label = { Text("Subject") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = composeBody,
                        onValueChange = { composeBody = it },
                        label = { Text("Message Body") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (composeTarget.isNotBlank() && composeBody.isNotBlank()) {
                            coroutineScope.launch {
                                manager.sendQuickMessage(
                                    QuickComposePayload(
                                        serviceType = composeService,
                                        recipientOrChannel = composeTarget,
                                        subject = composeSubject,
                                        messageText = composeBody
                                    )
                                )
                                showQuickComposeDialog = false
                            }
                        }
                    }
                ) {
                    Text("Send Message")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickComposeDialog = false }) { Text("Cancel") }
            },
            containerColor = Color(0xFF151C28)
        )
    }
}

@Composable
fun CommMessageItemCard(
    message: CommMessage,
    onClick: () -> Unit,
    onToggleStar: () -> Unit
) {
    Surface(
        color = if (message.isUnread) Color(0xFF161E2C) else Color(0xFF121720),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (message.isUnread) GVONEPrimary.copy(alpha = 0.4f) else Color(0xFF1E293B)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(message.serviceType.brandColorHex).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.senderName.take(1),
                    color = Color(message.serviceType.brandColorHex),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = message.senderName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (message.isUnread) FontWeight.Bold else FontWeight.Medium
                        )
                        Surface(
                            color = Color(message.serviceType.brandColorHex).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = message.serviceType.displayName,
                                color = Color(message.serviceType.brandColorHex),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(text = message.timestamp, color = Color(0xFF64748B), fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.subjectOrTopic,
                    color = if (message.isUnread) Color.White else Color(0xFFCBD5E1),
                    fontSize = 12.sp,
                    fontWeight = if (message.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = message.snippet,
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggleStar, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = if (message.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Star",
                    tint = if (message.isStarred) Color(0xFFF59E0B) else Color(0xFF64748B),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
