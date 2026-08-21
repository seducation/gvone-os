package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*

data class ShortcutItem(
    val title: String,
    val url: String,
    val iconVector: ImageVector,
    val accentColor: Color
)

@Composable
fun NewTabScreen(
    isPrivate: Boolean,
    isTorActive: Boolean,
    onNavigate: (String) -> Unit,
    onAskAI: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortcuts = listOf(
        ShortcutItem("RSS Group Feed", "https://rssgroupfeed-jaelvwfd.manus.space", Icons.Rounded.RssFeed, Color(0xFFF59E0B)),
        ShortcutItem("Quanta Magazine", "https://www.quantamagazine.org", Icons.Rounded.AutoAwesome, Color(0xFF6366F1)),
        ShortcutItem("DuckDuckGo", "https://duckduckgo.com", Icons.Rounded.Search, Color(0xFFDE5833)),
        ShortcutItem("Wikipedia", "https://en.wikipedia.org", Icons.Rounded.MenuBook, Color(0xFF006699)),
        ShortcutItem("GitHub", "https://github.com", Icons.Rounded.Code, Color(0xFF8B5CF6)),
        ShortcutItem("Bloomberg", "https://www.bloomberg.com", Icons.Rounded.TrendingUp, Color(0xFF10B981)),
        ShortcutItem("Apple", "https://www.apple.com", Icons.Rounded.Devices, Color(0xFF06B6D4)),
        ShortcutItem("Hacker News", "https://news.ycombinator.com", Icons.Rounded.Forum, Color(0xFFFF6600))
    )

    val aiSuggestions = listOf(
        "Explain Quantum Geometry outside space and time",
        "What is nephrotic syndrome pathophysiology?",
        "Explain Starling forces in capillaries",
        "Breakthroughs in fusion energy 2026"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(top = 48.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // Futuristic Logo & Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isPrivate) GVONESecondary else GVONEPrimary,
                                    Color(0xFF0B0E14)
                                )
                            )
                        )
                        .border(
                            1.5.dp,
                            if (isPrivate) GVONESecondary else GVONEPrimary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPrivate) Icons.Rounded.VpnLock else Icons.Rounded.Public,
                        contentDescription = "GVONE Logo",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = if (isPrivate) "GVONE Private Vault" else "GVONE Browser",
                    color = GVONETextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isPrivate) "Zero tracking • Ephemeral session • Private browsing" else "Next-Gen Web & AI Navigation",
                    color = GVONETextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // Privacy Shield Banner
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF131A26),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isTorActive) GVONETertiary.copy(alpha = 0.5f) else Color(0xFF232D3F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isTorActive) Icons.Rounded.VpnKey else Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = if (isTorActive) GVONETertiary else GVONEPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (isTorActive) "Tor Network Onion Shield" else "GVONE Web Shield Active",
                                color = GVONETextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isTorActive) "SOCKS5 Proxy 127.0.0.1:9050 Connected" else "Trackers blocked • HTTPS enforced",
                                color = GVONETextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Protected",
                        tint = GVONETertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // AI Prompt Suggestions
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = GVONEPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "GVONE AI Search Explorations",
                        color = GVONETextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(aiSuggestions) { prompt ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onAskAI(prompt) },
                            color = Color(0xFF161E2C),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263348))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = GVONEPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = prompt,
                                    color = GVONETextPrimary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Shortcuts Grid
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Top Shortcuts",
                    color = GVONETextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    userScrollEnabled = false
                ) {
                    items(shortcuts) { shortcut ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigate(shortcut.url) }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF161D2A))
                                    .border(1.dp, shortcut.accentColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = shortcut.iconVector,
                                    contentDescription = shortcut.title,
                                    tint = shortcut.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = shortcut.title,
                                color = GVONETextPrimary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
