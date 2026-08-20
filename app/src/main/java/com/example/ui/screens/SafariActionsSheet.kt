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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.isInternalHomeUrl
import com.example.ui.theme.*

/**
 * Premium Apple Safari Browser Action Menu Bottom Sheet
 * Triggered by the Right Circular Button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafariActionsSheet(
    tab: BrowserTab?,
    isTorActive: Boolean,
    isPrivateMode: Boolean,
    onNewTab: () -> Unit,
    onNewPrivateTab: () -> Unit,
    onToggleDesktop: () -> Unit,
    onToggleTor: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onReload: () -> Unit,
    onAddBookmark: () -> Unit,
    onAddFavorite: () -> Unit,
    onAddToReadingList: () -> Unit,
    onOpenReaderMode: () -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenAddToHomeScreen: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomPercentage by remember { mutableIntStateOf(100) }
    var bookmarkSavedToast by remember { mutableStateOf(false) }

    val isHomeApp = isInternalHomeUrl(tab?.url)
    val domain = if (isHomeApp) "Start Page" else try {
        val parsed = java.net.URI(tab?.url ?: "").host
        if (!parsed.isNullOrBlank()) parsed.removePrefix("www.") else (tab?.url ?: "")
    } catch (e: Exception) {
        tab?.url ?: ""
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xF5131822),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3B485E))
            )
        },
        modifier = modifier.testTag("safari_actions_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Site title, domain, TLS encryption, and Privacy / Tor pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHomeApp) "Home" else if (tab?.title.isNullOrBlank() || tab?.title == "New Tab") (domain.ifEmpty { "Safari Start Page" }) else tab?.title!!,
                        color = Color(0xFFF1F5F9),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (isHomeApp) "Protected · Start Page" else if (tab?.url?.startsWith("https://") == true) "Encrypted TLS 1.3 · $domain" else (domain.ifEmpty { "Local Start Page" }),
                        color = if (isHomeApp || tab?.url?.startsWith("https://") == true) GVONETertiary else Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTorActive) GVONETertiary.copy(alpha = 0.2f) else if (isPrivateMode) GVONESecondary.copy(alpha = 0.2f) else Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isTorActive) GVONETertiary else if (isPrivateMode) GVONESecondary else Color(0xFF334155))
                ) {
                    Text(
                        text = if (isTorActive) "TOR SHIELD" else if (isPrivateMode) "PRIVATE" else "PROTECTED",
                        color = if (isTorActive) GVONETertiary else if (isPrivateMode) GVONESecondary else Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Quick Actions 4-Grid: New Tab | Private Tab | Reload | Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SafariQuickTile(
                    icon = Icons.Rounded.Add,
                    label = "New Tab",
                    onClick = {
                        onClose()
                        onNewTab()
                    },
                    modifier = Modifier.weight(1f)
                )
                SafariQuickTile(
                    icon = Icons.Rounded.Security,
                    label = if (isPrivateMode) "Standard" else "Private",
                    isActive = isPrivateMode,
                    activeColor = GVONESecondary,
                    onClick = {
                        onClose()
                        onNewPrivateTab()
                    },
                    modifier = Modifier.weight(1f)
                )
                SafariQuickTile(
                    icon = Icons.Rounded.Refresh,
                    label = "Reload",
                    onClick = {
                        onClose()
                        onReload()
                    },
                    modifier = Modifier.weight(1f)
                )
                SafariQuickTile(
                    icon = Icons.Rounded.Share,
                    label = "Share",
                    onClick = {
                        onClose()
                        onShare()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Safari Page Zoom Capsule: [ A- | 100% | A+ ]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1A2230),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B374C))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (zoomPercentage > 50) zoomPercentage -= 10 },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Text(
                            text = "A",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Page Zoom $zoomPercentage%",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = { if (zoomPercentage < 200) zoomPercentage += 10 },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Text(
                            text = "A",
                            color = Color(0xFFE2E8F0),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Safari Action Group 1: Reader Mode, Find in Page, Desktop Website, Reading List
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1A2230),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B374C))
            ) {
                Column {
                    SafariActionRow(
                        icon = Icons.Rounded.MenuBook,
                        label = "Show Reader Mode",
                        trailingText = "Clean View",
                        onClick = {
                            onClose()
                            onOpenReaderMode()
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.FindInPage,
                        label = "Find on Page",
                        onClick = {
                            onClose()
                            onFindInPage()
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.DesktopWindows,
                        label = "Request Desktop Website",
                        isActive = tab?.desktopMode == true,
                        trailingText = if (tab?.desktopMode == true) "On" else "Off",
                        onClick = onToggleDesktop
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.BookmarkAdd,
                        label = "Add to Reading List",
                        onClick = {
                            onAddToReadingList()
                            bookmarkSavedToast = true
                        }
                    )
                }
            }

            // Safari Action Group 2: Bookmarks, Favorites, Add to Home Screen, Passwords
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1A2230),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B374C))
            ) {
                Column {
                    SafariActionRow(
                        icon = Icons.Rounded.BookmarkBorder,
                        label = "Add Bookmark",
                        onClick = {
                            onAddBookmark()
                            bookmarkSavedToast = true
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.StarBorder,
                        label = "Add to Favorites",
                        onClick = {
                            onAddFavorite()
                            bookmarkSavedToast = true
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.AddToHomeScreen,
                        label = "Add to Home Screen",
                        onClick = {
                            onClose()
                            onOpenAddToHomeScreen()
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.VpnKey,
                        label = "Passwords & Autofill",
                        trailingText = "iCloud Keychain",
                        onClick = {
                            onClose()
                            onOpenPasswords()
                        }
                    )
                }
            }

            // Safari Action Group 3: History, Downloads, Bookmarks, Website Settings
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1A2230),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B374C))
            ) {
                Column {
                    SafariActionRow(
                        icon = Icons.Rounded.History,
                        label = "Browsing History",
                        onClick = {
                            onClose()
                            onOpenHistory()
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.Download,
                        label = "Downloads",
                        onClick = {
                            onClose()
                            onOpenDownloads()
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.FolderOpen,
                        label = "Bookmarks Manager",
                        onClick = {
                            onClose()
                            onOpenBookmarks()
                        }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariActionRow(
                        icon = Icons.Rounded.Settings,
                        label = "Page Settings & Permissions",
                        onClick = {
                            onClose()
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SafariQuickTile(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    activeColor: Color = GVONEPrimary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) activeColor.copy(alpha = 0.2f) else Color(0xFF1A2230),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) activeColor.copy(alpha = 0.8f) else Color(0xFF2B374C)
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) activeColor else Color(0xFFF1F5F9),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = if (isActive) activeColor else Color(0xFFCCD6E5),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SafariActionRow(
    icon: ImageVector,
    label: String,
    trailingText: String? = null,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) GVONESecondary else Color(0xFFF1F5F9),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = Color(0xFFF1F5F9),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                color = if (isActive) GVONESecondary else Color(0xFF94A3B8),
                fontSize = 12.sp
            )
        }
    }
}
