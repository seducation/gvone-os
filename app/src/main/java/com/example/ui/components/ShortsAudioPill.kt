package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ShortsAudioMode
import com.example.ui.theme.*

/**
 * Floating interactive pill displayed when the user is watching YouTube Shorts.
 * Allows instant sound toggle (Mute/Unmute) and custom sound persistence configuration
 * (Always Unmuted on scroll, Always Muted, or Remember Choice).
 */
@Composable
fun ShortsAudioPill(
    visible: Boolean,
    isMuted: Boolean,
    currentMode: ShortsAudioMode,
    onToggleMute: () -> Unit,
    onSelectMode: (ShortsAudioMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuOpen by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(true) }

    // Auto collapse after a brief period, but keep accessible
    LaunchedEffect(visible, isMuted) {
        if (visible) {
            isExpanded = true
            kotlinx.coroutines.delay(4000)
            isExpanded = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(spring()) + slideInVertically { -it / 2 } + scaleIn(initialScale = 0.85f),
        exit = fadeOut(spring()) + slideOutVertically { -it / 2 } + scaleOut(targetScale = 0.85f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(end = 16.dp, top = 12.dp)
                .testTag("shorts_audio_pill")
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = GVONEGlassBackground,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            colors = if (!isMuted) {
                                listOf(GVONEPrimary.copy(alpha = 0.8f), GVONESecondary.copy(alpha = 0.8f))
                            } else {
                                listOf(GVONEBorder, GVONEGlassBorder)
                            }
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple()
                    ) {
                        if (!isExpanded) {
                            isExpanded = true
                        } else {
                            onToggleMute()
                        }
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    // Audio State Icon with pulse/accent
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (!isMuted) {
                                    Brush.linearGradient(listOf(GVONEPrimary, GVONESecondary))
                                } else {
                                    Brush.linearGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (!isMuted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                            contentDescription = if (!isMuted) "Shorts Audio Unmuted" else "Shorts Audio Muted",
                            tint = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("shorts_audio_toggle_btn")
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + fadeOut()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (!isMuted) "Shorts Unmuted" else "Shorts Muted",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = when (currentMode) {
                                        ShortsAudioMode.ALWAYS_UNMUTED -> "Always on scroll"
                                        ShortsAudioMode.ALWAYS_MUTED -> "Always muted"
                                        ShortsAudioMode.REMEMBER_STATE -> "Remember choice"
                                    },
                                    color = GVONETextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Settings dropdown trigger
                            IconButton(
                                onClick = { isMenuOpen = true },
                                modifier = Modifier
                                    .size(24.dp)
                                    .testTag("shorts_audio_menu_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "Shorts Audio Settings",
                                    tint = GVONETextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Dropdown menu for selecting Shorts Audio mode
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = { isMenuOpen = false },
                modifier = Modifier
                    .background(GVONESurfaceDark)
                    .border(1.dp, GVONEBorder, RoundedCornerShape(12.dp))
            ) {
                Text(
                    text = "YouTube Shorts Audio",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GVONESecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )

                HorizontalDivider(color = GVONEBorder, thickness = 0.5.dp)

                ShortsAudioMode.values().forEach { mode ->
                    val isSelected = currentMode == mode
                    DropdownMenuItem(
                        text = {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (mode) {
                                            ShortsAudioMode.ALWAYS_UNMUTED -> Icons.Rounded.VolumeUp
                                            ShortsAudioMode.ALWAYS_MUTED -> Icons.Rounded.VolumeOff
                                            ShortsAudioMode.REMEMBER_STATE -> Icons.Rounded.Sync
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) GVONESecondary else GVONETextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = mode.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else GVONETextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = mode.description,
                                    fontSize = 10.sp,
                                    color = GVONETextSecondary,
                                    modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Selected",
                                    tint = GVONESecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        },
                        onClick = {
                            onSelectMode(mode)
                            isMenuOpen = false
                        },
                        modifier = Modifier.testTag("shorts_audio_mode_item_${mode.name}")
                    )
                }
            }
        }
    }
}
