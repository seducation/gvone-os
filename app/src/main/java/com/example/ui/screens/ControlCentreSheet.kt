package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCentreSheet(
    currentTab: BrowserTab?,
    isTorActive: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleDesktop: () -> Unit,
    onFindInPage: () -> Unit,
    onToggleTor: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF141923),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3B475D))
            )
        },
        modifier = modifier.testTag("control_centre_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header Info & Quick Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Browser Control Centre",
                    color = GVONETextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTorActive) GVONETertiary.copy(alpha = 0.2f) else GVONEPrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isTorActive) GVONETertiary else GVONEPrimary)
                ) {
                    Text(
                        text = if (isTorActive) "TOR SHIELD ON" else "GVONE ENCRYPTED",
                        color = if (isTorActive) GVONETertiary else GVONEPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Quick Action Grid (matching Screenshot 1: History, Bookmarks, Downloads, Passwords, Desktop Site, Find, Tor, Private)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ControlActionTile(
                    icon = Icons.Rounded.History,
                    label = "History",
                    onClick = onOpenHistory
                )
                ControlActionTile(
                    icon = Icons.Rounded.BookmarkBorder,
                    label = "Bookmarks",
                    onClick = onOpenBookmarks
                )
                ControlActionTile(
                    icon = Icons.Rounded.Download,
                    label = "Downloads",
                    onClick = onOpenDownloads
                )
                ControlActionTile(
                    icon = Icons.Rounded.VpnKey,
                    label = "Tor Mode",
                    isActive = isTorActive,
                    onClick = onToggleTor
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ControlActionTile(
                    icon = Icons.Rounded.DesktopWindows,
                    label = "Desktop Site",
                    isActive = currentTab?.desktopMode == true,
                    onClick = onToggleDesktop
                )
                ControlActionTile(
                    icon = Icons.Rounded.FindInPage,
                    label = "Find in Page",
                    onClick = onFindInPage
                )
                ControlActionTile(
                    icon = Icons.Rounded.Security,
                    label = "Private Tab",
                    onClick = onNewPrivateTab
                )
                ControlActionTile(
                    icon = Icons.Rounded.Share,
                    label = "Share Link",
                    onClick = onShare
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Settings button matching Screenshot 1 red marker N
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenSettings() }
                    .testTag("open_settings_button"),
                color = Color(0xFF1E2636),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3B50))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = GVONEPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Settings",
                            color = GVONETextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "Open Settings",
                        tint = GVONETextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bottom Navigation utility row matching Screenshot 1: Back, Forward, Share, Refresh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F141E))
                    .border(1.dp, Color(0xFF252E3E), RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = GVONETextPrimary
                    )
                }
                IconButton(onClick = onNavigateForward) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = "Forward",
                        tint = GVONETextPrimary
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = GVONETextPrimary
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = GVONETextPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun ControlActionTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (isActive) GVONEPrimary else Color(0xFF1C2433))
                .border(1.dp, if (isActive) GVONESecondary else Color(0xFF2C394E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) Color.White else GVONETextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = GVONETextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1
        )
    }
}
