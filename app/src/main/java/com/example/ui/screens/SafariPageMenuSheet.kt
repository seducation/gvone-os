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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafariPageMenuSheet(
    tab: BrowserTab?,
    isTorActive: Boolean,
    onToggleDesktop: () -> Unit,
    onToggleTor: () -> Unit,
    onFindInPage: () -> Unit,
    onShare: () -> Unit,
    onReload: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoomPercentage by remember { mutableIntStateOf(100) }
    var readerModeActive by remember { mutableStateOf(false) }

    val isHomeApp = isInternalHomeUrl(tab?.url)
    val domain = if (isHomeApp) "Safari Start Page" else try {
        val parsed = java.net.URI(tab?.url ?: "").host
        if (!parsed.isNullOrBlank()) parsed else (tab?.url ?: "")
    } catch (e: Exception) {
        tab?.url ?: ""
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF141924),
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
        modifier = modifier.testTag("safari_page_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Site format & Domain
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (isHomeApp) "Safari Start Page" else domain.ifEmpty { "Safari Start Page" },
                        color = GVONETextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isHomeApp) "Protected · Start Page" else if (tab?.url?.startsWith("https://") == true) "Encrypted Connection (TLS 1.3)" else "Local / Standard Protocol",
                        color = if (isHomeApp || tab?.url?.startsWith("https://") == true) GVONETertiary else GVONETextSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTorActive) GVONETertiary.copy(alpha = 0.2f) else GVONEPrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isTorActive) GVONETertiary else GVONEPrimary)
                ) {
                    Text(
                        text = if (isTorActive) "TOR ACTIVE" else "SHIELD ON",
                        color = if (isTorActive) GVONETertiary else GVONEPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Safari Zoom Capsule: [ A- | 100% | A+ ]
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B2332),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C384E))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { if (zoomPercentage > 50) zoomPercentage -= 10 },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "A",
                            color = GVONETextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "$zoomPercentage%",
                        color = GVONETextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    IconButton(
                        onClick = { if (zoomPercentage < 200) zoomPercentage += 10 },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text(
                            text = "A",
                            color = GVONETextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Safari Action Group 1: Reader Mode, Desktop Site, Find in Page
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B2332),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C384E))
            ) {
                Column {
                    SafariMenuItem(
                        icon = Icons.Rounded.MenuBook,
                        label = "Reader View",
                        trailingText = if (readerModeActive) "Active" else "Available",
                        isActive = readerModeActive,
                        onClick = { readerModeActive = !readerModeActive }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariMenuItem(
                        icon = Icons.Rounded.DesktopWindows,
                        label = "Request Desktop Website",
                        isActive = tab?.desktopMode == true,
                        onClick = onToggleDesktop
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariMenuItem(
                        icon = Icons.Rounded.FindInPage,
                        label = "Find in Page",
                        onClick = {
                            onClose()
                            onFindInPage()
                        }
                    )
                }
            }

            // Safari Action Group 2: Privacy Report, Tor Circuit, Website Settings
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B2332),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C384E))
            ) {
                Column {
                    SafariMenuItem(
                        icon = Icons.Rounded.Security,
                        label = "Privacy Report",
                        trailingText = "14 Trackers Blocked",
                        onClick = { }
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariMenuItem(
                        icon = Icons.Rounded.VpnKey,
                        label = "Tor Onion Routing",
                        isActive = isTorActive,
                        trailingText = if (isTorActive) "Connected (9050)" else "Off",
                        onClick = onToggleTor
                    )
                    Divider(color = Color(0xFF263348), thickness = 0.5.dp)
                    SafariMenuItem(
                        icon = Icons.Rounded.Settings,
                        label = "Website Settings",
                        onClick = {
                            onClose()
                            onOpenSettings()
                        }
                    )
                }
            }

            // Bottom Quick Action Buttons (Share & Reload)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onClose()
                        onShare()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF253043))
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp), tint = GVONETextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Page", color = GVONETextPrimary, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        onClose()
                        onReload()
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reload", color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun SafariMenuItem(
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
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) GVONESecondary else GVONETextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                color = GVONETextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (trailingText != null) {
            Text(
                text = trailingText,
                color = if (isActive) GVONESecondary else GVONETextSecondary,
                fontSize = 12.sp
            )
        }
    }
}
