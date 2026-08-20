package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.TabSortOption
import com.example.data.model.isInternalHomeUrl
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabOverviewScreen(
    tabs: List<BrowserTab>,
    currentTabId: String,
    isPrivateMode: Boolean,
    onTabSelected: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTab: () -> Unit,
    onTogglePrivate: (Boolean) -> Unit,
    onSortTabs: (TabSortOption) -> Unit,
    onCloseOverview: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val filteredTabs = tabs.filter { it.isPrivate == isPrivateMode }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Top Bar matching Screenshot 2: "Manage Tab Groups", "Arrange Tabs By", Checkmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Manage Tab Groups & Arrange Tabs Dropdown Button
                Box {
                    Surface(
                        onClick = { showSortMenu = !showSortMenu },
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

                    // Sort Menu Dropdown matching Screenshot 2
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF1B2230))
                            .border(1.dp, Color(0xFF303A4E), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.SortByAlpha,
                                        contentDescription = null,
                                        tint = GVONEPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Arrange Tabs By Title", color = GVONETextPrimary, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                onSortTabs(TabSortOption.BY_TITLE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = GVONESecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Arrange Tabs By Website", color = GVONETextPrimary, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                onSortTabs(TabSortOption.BY_WEBSITE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.AccessTime,
                                        contentDescription = null,
                                        tint = GVONETertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Recent (Default)", color = GVONETextPrimary, fontSize = 13.sp)
                                }
                            },
                            onClick = {
                                onSortTabs(TabSortOption.DEFAULT)
                                showSortMenu = false
                            }
                        )
                    }
                }

                // Done / Select button (Blue checkmark pill)
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

            // Grid of Tabs
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredTabs, key = { it.id }) { tab ->
                    TabCard(
                        tab = tab,
                        isSelected = tab.id == currentTabId,
                        onSelect = { onTabSelected(tab.id) },
                        onClose = { onTabClose(tab.id) }
                    )
                }
            }
        }

        // Bottom Bar matching Screenshot 2: [Private] [16 Tabs] and [+] Add New Tab Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Switch between Private and Regular Tabs
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF161F2E).copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C384D)),
                    modifier = Modifier.shadow(12.dp, RoundedCornerShape(24.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Private mode toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isPrivateMode) GVONESecondary.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onTogglePrivate(true) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Private",
                                color = if (isPrivateMode) GVONESecondary else GVONETextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (isPrivateMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // Regular tabs mode toggle
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (!isPrivateMode) GVONEPrimary.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { onTogglePrivate(false) }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${tabs.count { !it.isPrivate }} Tabs",
                                color = if (!isPrivateMode) Color.White else GVONETextSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (!isPrivateMode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // New Tab Button
                FloatingActionButton(
                    onClick = onNewTab,
                    containerColor = if (isPrivateMode) GVONESecondary else GVONEPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("new_tab_fab")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New Tab",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TabCard(
    tab: BrowserTab,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val borderColor = if (isSelected) (if (tab.isPrivate) GVONESecondary else GVONEPrimary) else Color(0xFF263042)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onSelect() }
            .testTag("tab_card_${tab.id}"),
        color = Color(0xFF141923),
        shape = RoundedCornerShape(16.dp)
    ) {
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

            // Card Body (Preview Simulation)
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
                }
            }
        }
    }
}
