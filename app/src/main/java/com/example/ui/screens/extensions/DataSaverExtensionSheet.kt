package com.example.ui.screens.extensions

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.datasaver.DataSaverConfig
import com.example.data.datasaver.DataSaverManager
import com.example.data.datasaver.ImageCompressionMode
import com.example.ui.theme.GVONEPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataSaverExtensionSheet(
    dataSaverManager: DataSaverManager,
    currentDomain: String = "",
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val config by dataSaverManager.config.collectAsStateWithLifecycle()
    val stats by dataSaverManager.stats.collectAsStateWithLifecycle()
    val whitelistedDomains by dataSaverManager.whitelistedDomains.collectAsStateWithLifecycle()

    val isCurrentDomainWhitelisted = currentDomain.isNotBlank() && whitelistedDomains.contains(currentDomain)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Data Saver Extension",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = if (config.isEnabled) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF64748B).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (config.isEnabled) "ACTIVE" else "PAUSED",
                                    color = if (config.isEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Bandwidth compression • Video suppression • Image optimization",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("data_saver_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { dataSaverManager.resetStats() }) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Reset Stats", tint = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F141C))
            )
        },
        containerColor = Color(0xFF0B0F15),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Visual Meter Card
            item {
                Surface(
                    color = Color(0xFF131C2A),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Master Switch Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Data Compression Engine",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Intercepts traffic & reduces cellular payload",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked = config.isEnabled,
                                onCheckedChange = { dataSaverManager.toggleDataSaver(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Big Circular Savings Display
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(Color(0xFF059669).copy(alpha = 0.35f), Color(0xFF131C2A))
                                    )
                                )
                                .border(3.dp, Color(0xFF10B981), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${stats.savingsPercentage}%",
                                    color = Color(0xFF10B981),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "SAVED",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stats.formatBytes(stats.totalBytesSaved),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Data Saved",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color(0xFF243042)))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stats.formatBytes(stats.totalBytesTransferred),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Transferred",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }

                            Box(modifier = Modifier.width(1.dp).height(30.dp).background(Color(0xFF243042)))

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${stats.requestsFiltered}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Filtered Req",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // Savings Breakdown Row
            item {
                Text(
                    text = "Bandwidth Savings Breakdown",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SavingsMetricCard(
                        title = "Images",
                        savedText = stats.formatBytes(stats.imagesSavedBytes),
                        icon = Icons.Rounded.Image,
                        color = Color(0xFF38BDF8),
                        modifier = Modifier.weight(1f)
                    )
                    SavingsMetricCard(
                        title = "Video Autoplay",
                        savedText = stats.formatBytes(stats.videoSavedBytes),
                        icon = Icons.Rounded.VideocamOff,
                        color = Color(0xFFF59E0B),
                        modifier = Modifier.weight(1f)
                    )
                    SavingsMetricCard(
                        title = "Scripts & Trackers",
                        savedText = stats.formatBytes(stats.scriptsSavedBytes),
                        icon = Icons.Rounded.Shield,
                        color = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Granular Optimization Controls
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Optimization Rules & Features",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Image Compression Mode Selector
            item {
                Surface(
                    color = Color(0xFF141B26),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Rounded.BurstMode, contentDescription = null, tint = Color(0xFF38BDF8))
                            Text(text = "Image Quality Compression", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = config.imageCompressionMode.description,
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ImageCompressionMode.values().forEach { mode ->
                                FilterChip(
                                    selected = config.imageCompressionMode == mode,
                                    onClick = { dataSaverManager.setImageCompressionMode(mode) },
                                    label = { Text(mode.label.split(" ").first(), fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }

            // Block Video Autoplay Toggle
            item {
                Surface(
                    color = Color(0xFF141B26),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
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
                            Text(text = "Block Video Autoplay & Preload", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Prevents background video streaming from consuming mobile data", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = config.blockVideoAutoplay,
                            onCheckedChange = { dataSaverManager.toggleBlockVideoAutoplay(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF10B981))
                        )
                    }
                }
            }

            // Text-Only Reading Mode Toggle
            item {
                Surface(
                    color = Color(0xFF141B26),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
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
                            Text(text = "Text-Only Ultra Saver Mode", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Hides all images, canvases, and media for 90%+ data reduction", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        }
                        Switch(
                            checked = config.textOnlyReadingMode,
                            onCheckedChange = { dataSaverManager.toggleTextOnlyMode(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF10B981))
                        )
                    }
                }
            }

            // Current Site Whitelist Row
            if (currentDomain.isNotBlank()) {
                item {
                    Surface(
                        color = Color(0xFF161E2E),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E3D52)),
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
                                Text(text = "Bypass for $currentDomain", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isCurrentDomainWhitelisted) "Data Saver disabled on this site" else "Data Saver active on this site",
                                    color = if (isCurrentDomainWhitelisted) Color(0xFFF59E0B) else Color(0xFF10B981),
                                    fontSize = 11.sp
                                )
                            }

                            Switch(
                                checked = isCurrentDomainWhitelisted,
                                onCheckedChange = { dataSaverManager.toggleWhitelistDomain(currentDomain) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavingsMetricCard(
    title: String,
    savedText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF141B26),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243042)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = savedText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = title, color = Color(0xFF94A3B8), fontSize = 10.sp)
        }
    }
}
