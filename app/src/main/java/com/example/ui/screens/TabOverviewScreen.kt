package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.Environment
import com.example.data.model.TabGroup
import com.example.data.model.TabSortOption
import com.example.data.model.isInternalHomeUrl
import com.example.data.terminal.TerminalSession
import com.example.ui.components.*
import com.example.ui.screens.canvas.CreateEnvironmentDialog
import com.example.ui.screens.canvas.EnvironmentSwitchSheet
import com.example.ui.screens.canvas.getIconForName
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabOverviewScreen(
    tabs: List<BrowserTab>,
    tabGroups: List<TabGroup>,
    terminalSessions: List<TerminalSession> = emptyList(),
    currentTabId: String,
    activeGroupId: String?,
    activeTerminalSessionId: String? = null,
    isPrivateMode: Boolean,
    environments: List<Environment> = emptyList(),
    currentEnvironment: Environment? = null,
    onSelectEnvironment: (String) -> Unit = {},
    onCreateEnvironment: (name: String, icon: String, theme: String, preset: String, initialLinkUrl: String?, initialLinkTitle: String?) -> Unit = { _, _, _, _, _, _ -> },
    onDuplicateEnvironment: (String) -> Unit = {},
    onDeleteEnvironment: (String) -> Unit = {},
    onTabSelected: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: (groupId: String?) -> Unit,
    onSelectChat: (String) -> Unit = {},
    onNewChat: (groupId: String?) -> Unit = {},
    onDeleteChat: (String) -> Unit = {},
    onRenameChat: (chatId: String, newName: String) -> Unit = { _, _ -> },
    onMoveChatToGroup: (chatId: String, targetGroupId: String?) -> Unit = { _, _ -> },
    onMoveChatsToGroup: (chatIds: List<String>, targetGroupId: String?) -> Unit = { _, _ -> },
    onCloseChatsInGroup: (groupId: String) -> Unit = {},
    onTogglePrivate: (Boolean) -> Unit,
    onSortTabs: (TabSortOption) -> Unit,
    onCreateGroup: (name: String, colorHex: String?, tabIds: List<String>) -> Unit,
    onRenameGroup: (groupId: String, newName: String) -> Unit,
    onDeleteGroup: (groupId: String, closeTabs: Boolean) -> Unit,
    onMoveTabToGroup: (tabId: String, targetGroupId: String?) -> Unit,
    onMoveTabsToGroup: (tabIds: List<String>, targetGroupId: String?) -> Unit,
    onCloseTabsInGroup: (groupId: String) -> Unit,
    onDuplicateTab: (tabId: String) -> Unit,
    onCloseOtherTabs: (tabId: String) -> Unit,
    onCloseTabsToRight: (tabId: String) -> Unit,
    onNavigateToUrl: ((String) -> Unit)? = null,
    onCloseOverview: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Navigation state: null = Root All Tabs (Folders + Ungrouped), non-null = Viewing specific Folder
    var currentFolderId by remember { mutableStateOf<String?>(null) }

    // Search and filter state
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedCategory by rememberSaveable { mutableStateOf(0) } // 0 = Tabs, 1 = Chats

    // Custom shortcuts added by the user in Tab Overview
    var customShortcuts by remember { mutableStateOf<List<MenuShortcut>>(emptyList()) }

    // Selection mode state (both tabs and chats)
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedTabIds by remember { mutableStateOf(setOf<String>()) }
    var selectedChatIds by remember { mutableStateOf(setOf<String>()) }
    val totalSelectedCount = selectedTabIds.size + selectedChatIds.size

    // Dialogs & Context menus state
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var initialTabsForNewGroup by remember { mutableStateOf<List<String>>(emptyList()) }
    var groupToRename by remember { mutableStateOf<TabGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<TabGroup?>(null) }
    var tabToMove by remember { mutableStateOf<BrowserTab?>(null) }
    var chatToMove by remember { mutableStateOf<TerminalSession?>(null) }
    var chatToRename by remember { mutableStateOf<TerminalSession?>(null) }
    var isMovingBatchToGroup by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showEnvironmentSheet by remember { mutableStateOf(false) }
    var showCreateEnvironmentDialog by remember { mutableStateOf(false) }

    // Drag and Drop tracking
    var draggingTabId by remember { mutableStateOf<String?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }
    val folderBounds = remember { mutableStateMapOf<String, Rect>() }
    var hoveredFolderId by remember { mutableStateOf<String?>(null) }

    // Handle back inside TabOverviewScreen gracefully
    BackHandler(enabled = isSearchActive || searchQuery.isNotEmpty() || isSelectionMode || currentFolderId != null) {
        when {
            searchQuery.isNotEmpty() -> searchQuery = ""
            isSearchActive -> isSearchActive = false
            isSelectionMode -> {
                isSelectionMode = false
                selectedTabIds = emptySet()
                selectedChatIds = emptySet()
            }
            currentFolderId != null -> currentFolderId = null
        }
    }

    // Tabs filtered by privacy mode
    val modeTabs = remember(tabs, isPrivateMode) {
        tabs.filter { it.isPrivate == isPrivateMode }
    }

    // Active Folder object if viewing a folder
    val currentFolder = remember(tabGroups, currentFolderId) {
        tabGroups.find { it.id == currentFolderId }
    }

    // Filtered tabs for active folder view
    val folderTabs = remember(modeTabs, currentFolderId) {
        if (currentFolderId != null) {
            modeTabs.filter { it.tabGroupId == currentFolderId }
        } else {
            emptyList()
        }
    }

    // Filtered chats for active folder view
    val folderChats = remember(terminalSessions, currentFolderId) {
        if (currentFolderId != null) {
            terminalSessions.filter { it.tabGroupId == currentFolderId }
        } else {
            emptyList()
        }
    }

    // Ungrouped tabs for root view
    val ungroupedTabs = remember(modeTabs) {
        modeTabs.filter { it.tabGroupId == null }
    }

    // Ungrouped chats for root view
    val ungroupedChats = remember(terminalSessions) {
        terminalSessions.filter { it.tabGroupId == null }
    }

    // Search results across all tabs, chats, and folders
    val isSearching = searchQuery.isNotBlank()
    val searchResultsTabs = remember(modeTabs, tabGroups, searchQuery) {
        if (isSearching) {
            val q = searchQuery.trim().lowercase()
            modeTabs.filter { tab ->
                tab.title.lowercase().contains(q) ||
                tab.url.lowercase().contains(q) ||
                tabGroups.find { it.id == tab.tabGroupId }?.name?.lowercase()?.contains(q) == true
            }
        } else {
            emptyList()
        }
    }

    val searchResultsChats = remember(terminalSessions, tabGroups, searchQuery) {
        if (isSearching) {
            val q = searchQuery.trim().lowercase()
            terminalSessions.filter { chat ->
                chat.title.lowercase().contains(q) ||
                chat.lines.any { it.text.lowercase().contains(q) } ||
                tabGroups.find { it.id == chat.tabGroupId }?.name?.lowercase()?.contains(q) == true
            }
        } else {
            emptyList()
        }
    }

    var isShortcutsExpanded by rememberSaveable { mutableStateOf(false) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Swiping up when shortcuts are expanded collapses them
                if (available.y < -20f && isShortcutsExpanded) {
                    isShortcutsExpanded = false
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Pulling down from anywhere on the tabs list expands shortcuts
                if (available.y > 10f && !isShortcutsExpanded) {
                    isShortcutsExpanded = true
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .statusBarsPadding()
            .navigationBarsPadding()
            .nestedScroll(nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // --- TOP NAVIGATION / BREADCRUMB BAR ---
            if (currentFolderId != null && currentFolder != null) {
                // FOLDER VIEW HEADER: "‹ All Tabs" | "📁 Folder Name (count)" | "＋" | "⋮"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(isShortcutsExpanded) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 12f && !isShortcutsExpanded) {
                                        isShortcutsExpanded = true
                                        change.consume()
                                    } else if (dragAmount < -12f && isShortcutsExpanded) {
                                        isShortcutsExpanded = false
                                        change.consume()
                                    }
                                }
                            )
                        }
                ) {
                    FolderViewHeader(
                        folder = currentFolder,
                        tabCount = folderTabs.size,
                        chatCount = folderChats.size,
                        onBack = { currentFolderId = null },
                        onAddTab = { onNewTab(currentFolder.id) },
                        onAddChat = { onNewChat(currentFolder.id) },
                        onRename = { groupToRename = currentFolder },
                        onCloseAllInFolder = { onCloseTabsInGroup(currentFolder.id) },
                        onCloseAllChatsInFolder = { onCloseChatsInGroup(currentFolder.id) },
                        onDeleteFolder = { groupToDelete = currentFolder }
                    )
                }
            } else {
                // ROOT "ALL TABS" HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(isShortcutsExpanded) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount > 12f && !isShortcutsExpanded) {
                                        isShortcutsExpanded = true
                                        change.consume()
                                    } else if (dragAmount < -12f && isShortcutsExpanded) {
                                        isShortcutsExpanded = false
                                        change.consume()
                                    }
                                }
                            )
                        }
                ) {
                    RootAllTabsHeader(
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onToggleSearch = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        isSelectionMode = isSelectionMode,
                        selectedCount = totalSelectedCount,
                        onToggleSelectionMode = {
                            isSelectionMode = !isSelectionMode
                            if (!isSelectionMode) {
                                selectedTabIds = emptySet()
                                selectedChatIds = emptySet()
                            }
                        },
                        showSortMenu = showSortMenu,
                        onToggleSortMenu = { showSortMenu = !showSortMenu },
                        onSortTabs = onSortTabs,
                        currentEnvironment = currentEnvironment,
                        onManageEnvironmentClick = { showEnvironmentSheet = true },
                        onCreateEnvironmentClick = { showCreateEnvironmentDialog = true },
                        onCreateFolderClick = {
                            initialTabsForNewGroup = emptyList()
                            showCreateGroupDialog = true
                        },
                        onCloseOverview = onCloseOverview
                    )
                }

                // Shortcuts UI: matching the one in the bottom-right three-dot menu, positioned below "Arrange Tabs By"
                if (!isSearching) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SafariShortcutsCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_shortcuts_card"),
                        customShortcuts = customShortcuts,
                        onShortcutClick = { shortcut ->
                            onNavigateToUrl?.invoke(shortcut.url) ?: run {
                                onNewTab(null)
                            }
                            onCloseOverview()
                        },
                        onAddCustomShortcut = { newShortcut ->
                            customShortcuts = customShortcuts + newShortcut
                        },
                        onRemoveShortcut = { target ->
                            customShortcuts = customShortcuts.filter { it.title != target.title || it.url != target.url }
                        },
                        testTagPrefix = "overview_shortcut",
                        expanded = isShortcutsExpanded,
                        onExpandedChange = { isShortcutsExpanded = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isSearching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF141923))
                        .border(1.dp, Color(0xFF2C384D), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Category 1: Tabs
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selectedCategory == 0) {
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF00E5FF), Color(0xFF0083B0))
                                    )
                                } else {
                                    Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
                                }
                            )
                            .clickable { selectedCategory = 0 }
                            .testTag("category_pill_tabs"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = null,
                                tint = if (selectedCategory == 0) Color.White else GVONETextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Tabs (${modeTabs.size})",
                                color = if (selectedCategory == 0) Color.White else GVONETextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Category 2: Chats
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (selectedCategory == 1) {
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0284C7))
                                    )
                                } else {
                                    Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
                                }
                            )
                            .clickable { selectedCategory = 1 }
                            .testTag("category_pill_chats"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                tint = if (selectedCategory == 1) Color.White else GVONETextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Chats (${terminalSessions.size})",
                                color = if (selectedCategory == 1) Color.White else GVONETextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- CONTENT AREA ---
            if (isSearching) {
                // SEARCH RESULTS VIEW: Tabs & Chats
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val totalMatches = searchResultsTabs.size + searchResultsChats.size
                    item(span = { GridItemSpan(2) }) {
                        Text(
                            text = "Search Results ($totalMatches)",
                            color = GVONETextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    if (searchResultsTabs.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            ) {
                                Icon(Icons.Rounded.Public, contentDescription = null, tint = GVONESecondary, modifier = Modifier.size(16.dp))
                                Text("Tabs (${searchResultsTabs.size})", color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        items(searchResultsTabs, key = { "tab_${it.id}" }) { tab ->
                            val folder = tabGroups.find { it.id == tab.tabGroupId }
                            TabCard(
                                tab = tab,
                                folderName = folder?.name,
                                folderColorHex = folder?.colorHex,
                                isSelected = tab.id == currentTabId,
                                isSelectionMode = isSelectionMode,
                                isChecked = selectedTabIds.contains(tab.id),
                                onToggleCheck = {
                                    selectedTabIds = if (selectedTabIds.contains(tab.id)) {
                                        selectedTabIds - tab.id
                                    } else {
                                        selectedTabIds + tab.id
                                    }
                                },
                                onSelect = {
                                    if (isSelectionMode) {
                                        selectedTabIds = if (selectedTabIds.contains(tab.id)) {
                                            selectedTabIds - tab.id
                                        } else {
                                            selectedTabIds + tab.id
                                        }
                                    } else {
                                        onTabSelected(tab.id)
                                    }
                                },
                                onClose = { onTabClose(tab.id) },
                                onMoveToGroup = { tabToMove = tab },
                                onDuplicate = { onDuplicateTab(tab.id) },
                                onCloseOthers = { onCloseOtherTabs(tab.id) },
                                onCloseToRight = { onCloseTabsToRight(tab.id) },
                                onRemoveFromGroup = if (tab.tabGroupId != null) { { onMoveTabToGroup(tab.id, null) } } else null
                            )
                        }
                    }

                    if (searchResultsChats.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                            ) {
                                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                Text("Terminal Chats (${searchResultsChats.size})", color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        items(searchResultsChats, key = { "chat_${it.id}" }) { chat ->
                            val folder = tabGroups.find { it.id == chat.tabGroupId }
                            ChatCard(
                                session = chat,
                                folderName = folder?.name,
                                folderColorHex = folder?.colorHex,
                                isActiveChat = chat.id == activeTerminalSessionId,
                                isSelectionMode = isSelectionMode,
                                isChecked = selectedChatIds.contains(chat.id),
                                onToggleCheck = {
                                    selectedChatIds = if (selectedChatIds.contains(chat.id)) {
                                        selectedChatIds - chat.id
                                    } else {
                                        selectedChatIds + chat.id
                                    }
                                },
                                onSelect = {
                                    if (isSelectionMode) {
                                        selectedChatIds = if (selectedChatIds.contains(chat.id)) {
                                            selectedChatIds - chat.id
                                        } else {
                                            selectedChatIds + chat.id
                                        }
                                    } else {
                                        onSelectChat(chat.id)
                                    }
                                },
                                onClose = { onDeleteChat(chat.id) },
                                onRename = { chatToRename = chat },
                                onMoveToGroup = { chatToMove = chat },
                                onRemoveFromGroup = if (chat.tabGroupId != null) { { onMoveChatToGroup(chat.id, null) } } else null
                            )
                        }
                    }

                    if (totalMatches == 0) {
                        item(span = { GridItemSpan(2) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No matching tabs or terminal chats found.", color = GVONETextSecondary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else if (currentFolderId != null && currentFolder != null) {
                // INSIDE DEDICATED FOLDER VIEW: Tabs and Chats in this folder
                val hasItems = folderTabs.isNotEmpty() || folderChats.isNotEmpty()
                if (!hasItems) {
                    // Empty folder state
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF161F2E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = parseHexColor(currentFolder.colorHex),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = "No tabs or chats in this group",
                                color = GVONETextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Add tabs or terminal chats to \"${currentFolder.name}\" to keep your work organized.",
                                color = GVONETextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onNewTab(currentFolder.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Tab")
                                }
                                Button(
                                    onClick = { onNewChat(currentFolder.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Chat")
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SECTION: Tabs in this group
                        if (selectedCategory == 0) {
                            if (folderTabs.isNotEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Public, contentDescription = null, tint = GVONESecondary, modifier = Modifier.size(16.dp))
                                            Text("Tabs (${folderTabs.size})", color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = { onNewTab(currentFolder.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = GVONESecondary)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New Tab", color = GVONESecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                items(folderTabs, key = { "tab_${it.id}" }) { tab ->
                                    TabCard(
                                        tab = tab,
                                        isSelected = tab.id == currentTabId,
                                        isSelectionMode = isSelectionMode,
                                        isChecked = selectedTabIds.contains(tab.id),
                                        onToggleCheck = {
                                            selectedTabIds = if (selectedTabIds.contains(tab.id)) {
                                                selectedTabIds - tab.id
                                            } else {
                                                selectedTabIds + tab.id
                                            }
                                        },
                                        onSelect = {
                                            if (isSelectionMode) {
                                                selectedTabIds = if (selectedTabIds.contains(tab.id)) {
                                                    selectedTabIds - tab.id
                                                } else {
                                                    selectedTabIds + tab.id
                                                }
                                            } else {
                                                onTabSelected(tab.id)
                                            }
                                        },
                                        onClose = { onTabClose(tab.id) },
                                        onMoveToGroup = { tabToMove = tab },
                                        onDuplicate = { onDuplicateTab(tab.id) },
                                        onCloseOthers = { onCloseOtherTabs(tab.id) },
                                        onCloseToRight = { onCloseTabsToRight(tab.id) },
                                        onRemoveFromGroup = { onMoveTabToGroup(tab.id, null) }
                                    )
                                }
                            } else {
                                item(span = { GridItemSpan(2) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("No tabs in this group.", color = GVONETextSecondary, fontSize = 13.sp)
                                            Button(
                                                onClick = { onNewTab(currentFolder.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Add Tab", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION: Chats in this group
                        if (selectedCategory == 1) {
                            if (folderChats.isNotEmpty()) {
                                item(span = { GridItemSpan(2) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                            Text("Terminal Chats (${folderChats.size})", color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = { onNewChat(currentFolder.id) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("New Chat", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                items(folderChats, key = { "chat_${it.id}" }) { chat ->
                                    ChatCard(
                                        session = chat,
                                        isActiveChat = chat.id == activeTerminalSessionId,
                                        isSelectionMode = isSelectionMode,
                                        isChecked = selectedChatIds.contains(chat.id),
                                        onToggleCheck = {
                                            selectedChatIds = if (selectedChatIds.contains(chat.id)) {
                                                selectedChatIds - chat.id
                                            } else {
                                                selectedChatIds + chat.id
                                            }
                                        },
                                        onSelect = {
                                            if (isSelectionMode) {
                                                selectedChatIds = if (selectedChatIds.contains(chat.id)) {
                                                    selectedChatIds - chat.id
                                                } else {
                                                    selectedChatIds + chat.id
                                                }
                                            } else {
                                                onSelectChat(chat.id)
                                            }
                                        },
                                        onClose = { onDeleteChat(chat.id) },
                                        onRename = { chatToRename = chat },
                                        onMoveToGroup = { chatToMove = chat },
                                        onRemoveFromGroup = { onMoveChatToGroup(chat.id, null) }
                                    )
                                }
                            } else {
                                item(span = { GridItemSpan(2) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text("No terminal chats in this group.", color = GVONETextSecondary, fontSize = 13.sp)
                                            Button(
                                                onClick = { onNewChat(currentFolder.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Add Chat", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // ROOT "ALL TABS" FILE MANAGER VIEW: Tab Group Folders + Ungrouped Tabs + Ungrouped Chats
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SECTION 1: TAB GROUP FOLDERS (Mixed Tabs & Chats)
                    if (tabGroups.isNotEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FolderCopy,
                                        contentDescription = null,
                                        tint = GVONEPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Tab Groups (${tabGroups.size})",
                                        color = GVONETextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    onClick = {
                                        initialTabsForNewGroup = emptyList()
                                        showCreateGroupDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = GVONEPrimary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Folder", color = GVONEPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        // Render each Tab Group folder card
                        items(tabGroups, key = { "group_${it.id}" }, span = { GridItemSpan(2) }) { group ->
                            val tabsInThisGroup = modeTabs.filter { it.tabGroupId == group.id }
                            val chatsInThisGroup = terminalSessions.filter { it.tabGroupId == group.id }
                            val isDropTarget = hoveredFolderId == group.id

                            Box(
                                modifier = Modifier.onGloballyPositioned { coordinates ->
                                    folderBounds[group.id] = coordinates.boundsInRoot()
                                }
                            ) {
                                TabFolderCard(
                                    group = group,
                                    tabsInGroup = tabsInThisGroup,
                                    chatsInGroup = chatsInThisGroup,
                                    isActiveGroup = activeGroupId == group.id,
                                    isDropTarget = isDropTarget,
                                    onClick = { currentFolderId = group.id },
                                    onRename = { groupToRename = group },
                                    onAddTab = { onNewTab(group.id) },
                                    onAddChat = { onNewChat(group.id) },
                                    onDelete = { groupToDelete = group }
                                )
                            }
                        }
                    }

                    // SECTION 2: UNGROUPED TABS
                    if (selectedCategory == 0) {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Public,
                                        contentDescription = null,
                                        tint = GVONESecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Ungrouped Tabs (${ungroupedTabs.size})",
                                        color = GVONETextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    onClick = { onNewTab(null) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = GVONESecondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Tab", color = GVONESecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (ungroupedTabs.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "All tabs are neatly organized in groups.",
                                        color = GVONETextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            items(ungroupedTabs, key = { "tab_${it.id}" }) { tab ->
                                TabCard(
                                    tab = tab,
                                    isSelected = tab.id == currentTabId,
                                    isSelectionMode = isSelectionMode,
                                    isChecked = selectedTabIds.contains(tab.id),
                                    onToggleCheck = {
                                        selectedTabIds = if (selectedTabIds.contains(tab.id)) {
                                            selectedTabIds - tab.id
                                        } else {
                                            selectedTabIds + tab.id
                                        }
                                    },
                                    onSelect = {
                                        if (isSelectionMode) {
                                            selectedTabIds = if (selectedTabIds.contains(tab.id)) {
                                                selectedTabIds - tab.id
                                            } else {
                                                selectedTabIds + tab.id
                                            }
                                        } else {
                                            onTabSelected(tab.id)
                                        }
                                    },
                                    onClose = { onTabClose(tab.id) },
                                    onMoveToGroup = { tabToMove = tab },
                                    onDuplicate = { onDuplicateTab(tab.id) },
                                    onCloseOthers = { onCloseOtherTabs(tab.id) },
                                    onCloseToRight = { onCloseTabsToRight(tab.id) },
                                    onDragStart = { startPos ->
                                        draggingTabId = tab.id
                                        dragPosition = startPos
                                    },
                                    onDrag = { offsetDelta ->
                                        dragPosition += offsetDelta
                                        val matched = folderBounds.entries.find { it.value.contains(dragPosition) }
                                        hoveredFolderId = matched?.key
                                    },
                                    onDragEnd = {
                                        hoveredFolderId?.let { targetGId ->
                                            onMoveTabToGroup(tab.id, targetGId)
                                        }
                                        draggingTabId = null
                                        hoveredFolderId = null
                                    }
                                )
                            }
                        }
                    }

                    // SECTION 3: UNGROUPED CHATS (Terminal Sessions)
                    if (selectedCategory == 1) {
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Terminal,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Ungrouped Chats (${ungroupedChats.size})",
                                        color = GVONETextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                TextButton(
                                    onClick = { onNewChat(null) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Chat", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (ungroupedChats.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No ungrouped terminal chats. Tap '+ New Chat' to start one.",
                                        color = GVONETextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        } else {
                            items(ungroupedChats, key = { "chat_${it.id}" }) { chat ->
                                ChatCard(
                                    session = chat,
                                    isActiveChat = chat.id == activeTerminalSessionId,
                                    isSelectionMode = isSelectionMode,
                                    isChecked = selectedChatIds.contains(chat.id),
                                    onToggleCheck = {
                                        selectedChatIds = if (selectedChatIds.contains(chat.id)) {
                                            selectedChatIds - chat.id
                                        } else {
                                            selectedChatIds + chat.id
                                        }
                                    },
                                    onSelect = {
                                        if (isSelectionMode) {
                                            selectedChatIds = if (selectedChatIds.contains(chat.id)) {
                                                selectedChatIds - chat.id
                                            } else {
                                                selectedChatIds + chat.id
                                            }
                                        } else {
                                            onSelectChat(chat.id)
                                        }
                                    },
                                    onClose = { onDeleteChat(chat.id) },
                                    onRename = { chatToRename = chat },
                                    onMoveToGroup = { chatToMove = chat }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- BOTTOM BAR: Actions or Mode Switcher ---
        if (isSelectionMode) {
            // MULTI-SELECT ACTIONS BAR
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF161F2E),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C384D)),
                shadowElevation = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$totalSelectedCount Selected",
                        color = GVONETextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Create Group from Selection
                        Button(
                            onClick = {
                                if (selectedTabIds.isNotEmpty()) {
                                    initialTabsForNewGroup = selectedTabIds.toList()
                                    showCreateGroupDialog = true
                                }
                            },
                            enabled = selectedTabIds.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Group", fontSize = 12.sp)
                        }

                        // Move Selection to Existing Group
                        OutlinedButton(
                            onClick = {
                                if (totalSelectedCount > 0) {
                                    isMovingBatchToGroup = true
                                }
                            },
                            enabled = totalSelectedCount > 0,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GVONETextPrimary),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333E52)),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Move", fontSize = 12.sp)
                        }

                        // Close Selected Tabs & Chats
                        IconButton(
                            onClick = {
                                selectedTabIds.forEach { onTabClose(it) }
                                selectedChatIds.forEach { onDeleteChat(it) }
                                selectedTabIds = emptySet()
                                selectedChatIds = emptySet()
                                isSelectionMode = false
                            },
                            enabled = totalSelectedCount > 0,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Selected", tint = Color(0xFFEF4444))
                        }
                    }
                }
            }
        } else {
            // NORMAL BOTTOM BAR: [Bottom-Left Environment Pill] [Private/Tabs Switch] and [+] FAB
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // BOTTOM LEFT: Environment pill & management
                    Surface(
                        onClick = { showEnvironmentSheet = true },
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF161F2E).copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C384D)),
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(24.dp))
                            .testTag("bottom_left_environment_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = getIconForName(currentEnvironment?.iconName ?: "Person"),
                                contentDescription = null,
                                tint = parseSafeColor(currentEnvironment?.background?.accentColorHex),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = currentEnvironment?.name ?: "Personal",
                                color = GVONETextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Rounded.UnfoldMore,
                                contentDescription = "Environments",
                                tint = GVONETextSecondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // BOTTOM CENTER: Switch between Private and Regular Tabs
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF161F2E).copy(alpha = 0.95f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C384D)),
                        modifier = Modifier.shadow(12.dp, RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Private mode toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (isPrivateMode) GVONESecondary.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable { onTogglePrivate(true) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Private",
                                    color = if (isPrivateMode) GVONESecondary else GVONETextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isPrivateMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            // Regular tabs mode toggle
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (!isPrivateMode) GVONEPrimary.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable { onTogglePrivate(false) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "${tabs.count { !it.isPrivate }} Tabs",
                                    color = if (!isPrivateMode) Color.White else GVONETextSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (!isPrivateMode) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // BOTTOM RIGHT: New Tab FAB
                    FloatingActionButton(
                        onClick = { onNewTab(currentFolderId) },
                        containerColor = if (isPrivateMode) GVONESecondary else GVONEPrimary,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("new_tab_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "New Tab",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    // --- ENVIRONMENT MANAGEMENT SHEETS & DIALOGS ---

    if (showEnvironmentSheet) {
        EnvironmentSwitchSheet(
            environments = environments,
            activeEnvironmentId = currentEnvironment?.id ?: "personal",
            onSelectEnvironment = { envId ->
                onSelectEnvironment(envId)
                showEnvironmentSheet = false
            },
            onCreateEnvironment = { name, icon, theme, preset, initialLinkUrl, initialLinkTitle ->
                onCreateEnvironment(name, icon, theme, preset, initialLinkUrl, initialLinkTitle)
                showEnvironmentSheet = false
            },
            onDuplicateEnvironment = onDuplicateEnvironment,
            onDeleteEnvironment = onDeleteEnvironment,
            onDismiss = { showEnvironmentSheet = false }
        )
    }

    if (showCreateEnvironmentDialog) {
        CreateEnvironmentDialog(
            onDismiss = { showCreateEnvironmentDialog = false },
            onConfirm = { name, icon, theme, preset, initialLinkUrl, initialLinkTitle ->
                onCreateEnvironment(name, icon, theme, preset, initialLinkUrl, initialLinkTitle)
                showCreateEnvironmentDialog = false
            }
        )
    }

    // --- DIALOGS ---

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            initialName = if (initialTabsForNewGroup.isNotEmpty()) "Selected Tabs" else "",
            onDismiss = {
                showCreateGroupDialog = false
                initialTabsForNewGroup = emptyList()
            },
            onConfirm = { name, colorHex ->
                onCreateGroup(name, colorHex, initialTabsForNewGroup)
                showCreateGroupDialog = false
                if (initialTabsForNewGroup.isNotEmpty()) {
                    selectedTabIds = emptySet()
                    isSelectionMode = false
                }
                initialTabsForNewGroup = emptyList()
            }
        )
    }

    groupToRename?.let { group ->
        RenameGroupDialog(
            group = group,
            onDismiss = { groupToRename = null },
            onConfirm = { newName ->
                onRenameGroup(group.id, newName)
                groupToRename = null
            }
        )
    }

    groupToDelete?.let { group ->
        val count = tabs.count { it.tabGroupId == group.id }
        DeleteGroupDialog(
            group = group,
            tabCount = count,
            onDismiss = { groupToDelete = null },
            onConfirm = { closeTabs ->
                onDeleteGroup(group.id, closeTabs)
                if (currentFolderId == group.id) {
                    currentFolderId = null
                }
                groupToDelete = null
            }
        )
    }

    tabToMove?.let { tab ->
        MoveToGroupDialog(
            tabGroups = tabGroups,
            currentGroupId = tab.tabGroupId,
            onDismiss = { tabToMove = null },
            onSelectGroup = { targetGroupId ->
                onMoveTabToGroup(tab.id, targetGroupId)
                tabToMove = null
            },
            onCreateNewGroup = {
                tabToMove = null
                initialTabsForNewGroup = listOf(tab.id)
                showCreateGroupDialog = true
            }
        )
    }

    chatToMove?.let { chat ->
        MoveToGroupDialog(
            tabGroups = tabGroups,
            currentGroupId = chat.tabGroupId,
            onDismiss = { chatToMove = null },
            onSelectGroup = { targetGroupId ->
                onMoveChatToGroup(chat.id, targetGroupId)
                chatToMove = null
            },
            onCreateNewGroup = {
                chatToMove = null
                showCreateGroupDialog = true
            }
        )
    }

    chatToRename?.let { chat ->
        RenameChatDialog(
            session = chat,
            onDismiss = { chatToRename = null },
            onConfirm = { newTitle ->
                onRenameChat(chat.id, newTitle)
                chatToRename = null
            }
        )
    }

    if (isMovingBatchToGroup) {
        MoveToGroupDialog(
            tabGroups = tabGroups,
            currentGroupId = null,
            onDismiss = { isMovingBatchToGroup = false },
            onSelectGroup = { targetGroupId ->
                if (selectedTabIds.isNotEmpty()) {
                    onMoveTabsToGroup(selectedTabIds.toList(), targetGroupId)
                }
                if (selectedChatIds.isNotEmpty()) {
                    onMoveChatsToGroup(selectedChatIds.toList(), targetGroupId)
                }
                selectedTabIds = emptySet()
                selectedChatIds = emptySet()
                isSelectionMode = false
                isMovingBatchToGroup = false
            },
            onCreateNewGroup = {
                isMovingBatchToGroup = false
                initialTabsForNewGroup = selectedTabIds.toList()
                showCreateGroupDialog = true
            }
        )
    }
}

// --- SUB-COMPONENTS: HEADERS & CARDS ---

@Composable
private fun RootAllTabsHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    isSelectionMode: Boolean,
    selectedCount: Int,
    onToggleSelectionMode: () -> Unit,
    showSortMenu: Boolean,
    onToggleSortMenu: () -> Unit,
    onSortTabs: (TabSortOption) -> Unit,
    currentEnvironment: Environment? = null,
    onManageEnvironmentClick: () -> Unit = {},
    onCreateEnvironmentClick: () -> Unit = {},
    onCreateFolderClick: () -> Unit,
    onCloseOverview: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Sort Dropdown Button
            Box {
                Surface(
                    onClick = onToggleSortMenu,
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E2430),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333E52))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = "Arrange Tabs",
                            tint = GVONETextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Arrange Tabs By",
                            color = GVONETextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Dropdown",
                            tint = GVONETextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = onToggleSortMenu,
                    modifier = Modifier
                        .background(Color(0xFF1B2230))
                        .border(1.dp, Color(0xFF303A4E), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.SortByAlpha, contentDescription = null, tint = GVONEPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Arrange Tabs By Title", color = GVONETextPrimary, fontSize = 13.sp)
                            }
                        },
                        onClick = {
                            onSortTabs(TabSortOption.BY_TITLE)
                            onToggleSortMenu()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Language, contentDescription = null, tint = GVONESecondary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Arrange Tabs By Website", color = GVONETextPrimary, fontSize = 13.sp)
                            }
                        },
                        onClick = {
                            onSortTabs(TabSortOption.BY_WEBSITE)
                            onToggleSortMenu()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AccessTime, contentDescription = null, tint = GVONETertiary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Recent (Default)", color = GVONETextPrimary, fontSize = 13.sp)
                            }
                        },
                        onClick = {
                            onSortTabs(TabSortOption.DEFAULT)
                            onToggleSortMenu()
                        }
                    )
                    HorizontalDivider(color = Color(0xFF303A4E), modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = getIconForName(currentEnvironment?.iconName ?: "Person"),
                                    contentDescription = null,
                                    tint = parseSafeColor(currentEnvironment?.background?.accentColorHex),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Environment: ${currentEnvironment?.name ?: "Personal"}", color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Switch or manage environments", color = GVONETextSecondary, fontSize = 10.sp)
                                }
                            }
                        },
                        onClick = {
                            onToggleSortMenu()
                            onManageEnvironmentClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("New Environment with Link...", color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        },
                        onClick = {
                            onToggleSortMenu()
                            onCreateEnvironmentClick()
                        }
                    )
                }
            }

            // Right action pills: Search toggle, Select mode toggle, Environment, New Folder, and Done
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Environment Switcher Button
                IconButton(
                    onClick = onManageEnvironmentClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                        .border(1.dp, Color(0xFF333E52), CircleShape)
                        .testTag("header_environment_button")
                ) {
                    Icon(
                        imageVector = getIconForName(currentEnvironment?.iconName ?: "Person"),
                        contentDescription = "Environments",
                        tint = parseSafeColor(currentEnvironment?.background?.accentColorHex),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Search Toggle
                IconButton(
                    onClick = onToggleSearch,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSearchActive) GVONEPrimary.copy(alpha = 0.2f) else Color(0xFF1E2430))
                        .border(1.dp, if (isSearchActive) GVONEPrimary else Color(0xFF333E52), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                        contentDescription = "Search Tabs",
                        tint = if (isSearchActive) GVONEPrimary else GVONETextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Multi-select toggle
                IconButton(
                    onClick = onToggleSelectionMode,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelectionMode) GVONESecondary.copy(alpha = 0.2f) else Color(0xFF1E2430))
                        .border(1.dp, if (isSelectionMode) GVONESecondary else Color(0xFF333E52), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isSelectionMode) Icons.Rounded.ChecklistRtl else Icons.Rounded.Checklist,
                        contentDescription = "Multi Select Mode",
                        tint = if (isSelectionMode) GVONESecondary else GVONETextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // New Folder Button
                IconButton(
                    onClick = onCreateFolderClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                        .border(1.dp, Color(0xFF333E52), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = "New Folder",
                        tint = GVONEPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Environment Switcher / Management Button
                IconButton(
                    onClick = onManageEnvironmentClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f), CircleShape)
                        .testTag("all_tabs_environment_header_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DashboardCustomize,
                        contentDescription = "Environments",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Done checkmark
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(GVONEPrimary)
                        .clickable { onCloseOverview() }
                        .testTag("close_tab_overview_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Expandable Search Bar
        AnimatedVisibility(visible = isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search by tab title, URL or folder...", color = GVONETextSecondary, fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = GVONETextSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = GVONETextSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GVONETextPrimary,
                    unfocusedTextColor = GVONETextPrimary,
                    focusedBorderColor = GVONEPrimary,
                    unfocusedBorderColor = Color(0xFF303A4E),
                    focusedContainerColor = Color(0xFF141923),
                    unfocusedContainerColor = Color(0xFF141923)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            )
        }
    }
}

@Composable
private fun FolderViewHeader(
    folder: TabGroup,
    tabCount: Int,
    chatCount: Int = 0,
    onBack: () -> Unit,
    onAddTab: () -> Unit,
    onAddChat: () -> Unit = {},
    onRename: () -> Unit,
    onCloseAllInFolder: () -> Unit,
    onCloseAllChatsInFolder: () -> Unit = {},
    onDeleteFolder: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val accentColor = parseHexColor(folder.colorHex)

    val countSummary = buildString {
        append("$tabCount tabs")
        if (chatCount > 0) {
            append(", $chatCount chats")
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back Button: "‹ All Tabs"
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E2430),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333E52))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back to All Tabs",
                    tint = GVONEPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "All Tabs",
                    color = GVONETextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Folder Title in center
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Folder,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = folder.name,
                color = GVONETextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "($countSummary)",
                color = GVONETextSecondary,
                fontSize = 12.sp
            )
        }

        // Action Buttons: [+] Add Tab, [Terminal] Add Chat, [⋮] Folder Options
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onAddTab,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2430))
                    .border(1.dp, Color(0xFF333E52), CircleShape)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Tab to Group", tint = GVONEPrimary, modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = onAddChat,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2430))
                    .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Rounded.Terminal, contentDescription = "Add Chat to Group", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2430))
                        .border(1.dp, Color(0xFF333E52), CircleShape)
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More Options", tint = GVONETextSecondary, modifier = Modifier.size(18.dp))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(Color(0xFF1B2230))
                        .border(1.dp, Color(0xFF303A4E), RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename Group", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = GVONEPrimary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Close All Tabs in Group", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onCloseAllInFolder()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Close All Chats in Group", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onCloseAllChatsInFolder()
                        }
                    )
                    Divider(color = Color(0xFF263042), thickness = 0.5.dp)
                    DropdownMenuItem(
                        text = { Text("Delete Group", color = Color(0xFFEF4444), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onDeleteFolder()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabCard(
    tab: BrowserTab,
    folderName: String? = null,
    folderColorHex: String? = null,
    isSelected: Boolean,
    isSelectionMode: Boolean = false,
    isChecked: Boolean = false,
    onToggleCheck: () -> Unit = {},
    onSelect: () -> Unit,
    onClose: () -> Unit,
    onMoveToGroup: (() -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onCloseOthers: (() -> Unit)? = null,
    onCloseToRight: (() -> Unit)? = null,
    onRemoveFromGroup: (() -> Unit)? = null,
    onDragStart: ((Offset) -> Unit)? = null,
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val borderColor = if (isChecked) GVONESecondary else if (isSelected) (if (tab.isPrivate) GVONESecondary else GVONEPrimary) else Color(0xFF263042)

    var cardModifier = Modifier
        .fillMaxWidth()
        .height(210.dp)
        .clip(RoundedCornerShape(16.dp))
        .border(if (isSelected || isChecked) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))

    // Gesture handling: long press for context menu or drag
    cardModifier = if (onDragStart != null && onDrag != null && onDragEnd != null) {
        cardModifier.pointerInput(tab.id) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    onDragStart(offset)
                },
                onDrag = { _, dragAmount ->
                    onDrag(dragAmount)
                },
                onDragEnd = {
                    onDragEnd()
                },
                onDragCancel = {
                    onDragEnd()
                }
            )
        }
    } else {
        cardModifier
    }

    Surface(
        modifier = cardModifier
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleCheck() else onSelect()
                },
                onLongClick = {
                    showContextMenu = true
                }
            )
            .testTag("tab_card_${tab.id}"),
        color = Color(0xFF141923),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Card Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C2331))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isSelectionMode) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleCheck() },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GVONEPrimary,
                                    uncheckedColor = Color(0xFF4B5563)
                                ),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }

                        Icon(
                            imageVector = if (tab.isPrivate) Icons.Rounded.VpnLock else Icons.Rounded.Language,
                            contentDescription = null,
                            tint = if (tab.isPrivate) GVONESecondary else GVONEPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = tab.title.ifEmpty { "New Tab" },
                            color = GVONETextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .clickable { onClose() }
                                .testTag("close_tab_${tab.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Tab",
                                tint = GVONETextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                // Card Body (Preview)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1C2230), Color(0xFF0F131C))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isInternalHomeUrl(tab.url)) Icons.Rounded.Home else if (tab.url.contains("quanta", true)) Icons.Rounded.AutoAwesome else if (tab.url.contains("duckduckgo", true)) Icons.Rounded.Search else Icons.Rounded.Public,
                            contentDescription = null,
                            tint = GVONETextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isInternalHomeUrl(tab.url)) "Start Page" else tab.url.removePrefix("https://").removePrefix("http://"),
                            color = GVONETextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // If folder name badge is present (e.g. In search mode)
                        if (!folderName.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val fColor = parseHexColor(folderColorHex, GVONEPrimary)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = fColor.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, fColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = fColor, modifier = Modifier.size(10.dp))
                                    Text(
                                        text = folderName,
                                        color = fColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Context Menu Dropdown
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                modifier = Modifier
                    .background(Color(0xFF1B2230))
                    .border(1.dp, Color(0xFF303A4E), RoundedCornerShape(12.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Open Tab", color = GVONETextPrimary, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = GVONEPrimary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showContextMenu = false
                        onSelect()
                    }
                )

                onMoveToGroup?.let {
                    DropdownMenuItem(
                        text = { Text("Move to Group...", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null, tint = GVONESecondary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showContextMenu = false
                            it()
                        }
                    )
                }

                onRemoveFromGroup?.let {
                    DropdownMenuItem(
                        text = { Text("Remove from Group", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.FolderOff, contentDescription = null, tint = GVONETertiary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showContextMenu = false
                            it()
                        }
                    )
                }

                onDuplicate?.let {
                    DropdownMenuItem(
                        text = { Text("Duplicate Tab", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = GVONETextSecondary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showContextMenu = false
                            it()
                        }
                    )
                }

                Divider(color = Color(0xFF263042), thickness = 0.5.dp)

                onCloseOthers?.let {
                    DropdownMenuItem(
                        text = { Text("Close Other Tabs", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.ClearAll, contentDescription = null, tint = GVONETextSecondary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showContextMenu = false
                            it()
                        }
                    )
                }

                onCloseToRight?.let {
                    DropdownMenuItem(
                        text = { Text("Close Tabs to Right", color = GVONETextPrimary, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.East, contentDescription = null, tint = GVONETextSecondary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showContextMenu = false
                            it()
                        }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Close Tab", color = Color(0xFFEF4444), fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showContextMenu = false
                        onClose()
                    }
                )
            }
        }
    }
}

private fun parseSafeColor(hex: String?, defaultColor: Color = Color(0xFF38BDF8)): Color {
    if (hex.isNullOrBlank()) return defaultColor
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        defaultColor
    }
}


