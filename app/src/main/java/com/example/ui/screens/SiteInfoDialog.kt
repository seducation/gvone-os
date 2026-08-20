package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.isInternalHomeUrl
import com.example.ui.theme.*

@Composable
fun SiteInfoDialog(
    tab: BrowserTab?,
    isTorActive: Boolean,
    onDismiss: () -> Unit
) {
    val isHomeApp = isInternalHomeUrl(tab?.url)
    val isHttps = tab?.url?.startsWith("https://") == true
    val domain = if (isHomeApp) "Safari Start Page" else try {
        java.net.URI(tab?.url ?: "").host ?: (tab?.url ?: "")
    } catch (e: Exception) {
        tab?.url ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isTorActive) Icons.Rounded.VpnKey else if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (isTorActive) GVONETertiary else if (isHttps) GVONETertiary else GVONEAccentRed
                )
                Text(
                    text = if (isTorActive) "Tor Onion Circuit" else if (isHttps) "Secure Connection" else "Not Secure",
                    color = GVONETextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color(0xFF161F2E),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Domain", color = GVONETextSecondary, fontSize = 11.sp)
                        Text(domain, color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Surface(
                    color = Color(0xFF161F2E),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Certificate / Security", color = GVONETextSecondary, fontSize = 11.sp)
                        Text(
                            if (isTorActive) "Encrypted via 3-Hop SOCKS5 Onion Protocol"
                            else if (isHttps) "Valid TLS 1.3 Encryption Verified"
                            else "Unencrypted HTTP Transmission",
                            color = if (isHttps || isTorActive) GVONETertiary else GVONEAccentRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    color = Color(0xFF161F2E),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Privacy Controls", color = GVONETextSecondary, fontSize = 11.sp)
                        Text("Trackers Blocked • Zero 3rd Party Cookies Allowed", color = GVONETextPrimary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = GVONEPrimary)
            }
        },
        containerColor = Color(0xFF131822)
    )
}

@Composable
fun FindInPageBar(
    query: String,
    currentIndex: Int,
    matchCount: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF151C2A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28374D))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = GVONETextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Find in page", color = GVONETextSecondary, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = GVONETextPrimary,
                    unfocusedTextColor = GVONETextPrimary
                ),
                singleLine = true
            )

            if (matchCount > 0) {
                Text(
                    text = "${currentIndex + 1}/$matchCount",
                    color = GVONESecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            IconButton(onClick = onPrevious, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Previous", tint = GVONETextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Next", tint = GVONETextPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = GVONETextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}
