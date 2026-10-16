package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.ui.screens.ide.IdeTab
import com.example.ui.screens.ide.XcodeWorkspaceMode

/**
 * Top Development Bar for the unified Xcode-style IDE browser window.
 * Houses Xcode-style Run/Stop execution controls, active scheme/URL target capsule,
 * workspace mode switcher (Web vs Code), Tor privacy indicators, and panel toggles
 * for Navigator (bottom-left), Console (bottom), and Inspector (bottom-right).
 */
@Composable
fun XcodeDevelopmentBar(
    workspaceMode: XcodeWorkspaceMode,
    onToggleWorkspaceMode: () -> Unit,
    currentTab: BrowserTab?,
    activeEditorTab: IdeTab?,
    isTorActive: Boolean,
    isNavigatorOpen: Boolean,
    onToggleNavigator: () -> Unit,
    isConsoleOpen: Boolean,
    onToggleConsole: () -> Unit,
    isInspectorOpen: Boolean,
    onToggleInspector: () -> Unit,
    onRunAction: () -> Unit,
    onStopAction: () -> Unit,
    onAddressClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .testTag("xcode_development_bar"),
        color = Color(0xFF0E131F),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ================= LEFT: RUN / STOP CONTROLS =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Play / Run / Reload Button
                FilledIconButton(
                    onClick = onRunAction,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                        contentColor = Color(0xFF34D399)
                    ),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("xcode_run_button")
                ) {
                    Icon(
                        imageVector = if (workspaceMode == XcodeWorkspaceMode.BROWSER) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                        contentDescription = "Run / Reload",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Stop Button
                IconButton(
                    onClick = onStopAction,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("xcode_stop_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Stop,
                        contentDescription = "Stop",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ================= CENTER: TARGET SCHEME & MODE PILL =================
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF161F30),
                border = BorderStroke(1.dp, Color(0xFF2E3E5C)),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(34.dp)
                    .clickable { onAddressClick() }
                    .testTag("xcode_scheme_target_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Target Icon & Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (workspaceMode == XcodeWorkspaceMode.BROWSER) {
                                if (currentTab?.url?.startsWith("https") == true) Icons.Rounded.Lock else Icons.Rounded.Public
                            } else {
                                Icons.Rounded.Code
                            },
                            contentDescription = null,
                            tint = if (workspaceMode == XcodeWorkspaceMode.BROWSER) Color(0xFF38BDF8) else Color(0xFFA78BFA),
                            modifier = Modifier.size(14.dp)
                        )

                        val titleText = if (workspaceMode == XcodeWorkspaceMode.BROWSER) {
                            currentTab?.title?.takeIf { it.isNotBlank() } ?: currentTab?.url ?: "GVONE Browser"
                        } else {
                            activeEditorTab?.fileItem?.name ?: "Editor Workspace"
                        }

                        Text(
                            text = titleText,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isTorActive) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFC084FC))
                            )
                        }
                    }

                    // Mode Switcher Pill (Web ⇋ Code)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (workspaceMode == XcodeWorkspaceMode.CODE_EDITOR) Color(0xFF7C3AED).copy(alpha = 0.25f) else Color(0xFF0284C7).copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, if (workspaceMode == XcodeWorkspaceMode.CODE_EDITOR) Color(0xFFA78BFA) else Color(0xFF38BDF8)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleWorkspaceMode() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("xcode_mode_switcher_pill")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (workspaceMode == XcodeWorkspaceMode.BROWSER) Icons.Rounded.Public else Icons.Rounded.Code,
                                contentDescription = null,
                                tint = if (workspaceMode == XcodeWorkspaceMode.BROWSER) Color(0xFF38BDF8) else Color(0xFFA78BFA),
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (workspaceMode == XcodeWorkspaceMode.BROWSER) "WEB" else "CODE",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = if (workspaceMode == XcodeWorkspaceMode.BROWSER) Color(0xFF38BDF8) else Color(0xFFA78BFA)
                            )
                        }
                    }
                }
            }

            // ================= RIGHT: XCODE PANEL TOGGLE CONTROLS =================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. Bottom-Left Navigator Toggle
                IconButton(
                    onClick = onToggleNavigator,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("xcode_toggle_navigator_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ViewSidebar,
                        contentDescription = "Toggle Navigator Panel",
                        tint = if (isNavigatorOpen) Color(0xFF38BDF8) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 2. Bottom Debugger / Console Toggle
                IconButton(
                    onClick = onToggleConsole,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("xcode_toggle_console_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = "Toggle Debug Console",
                        tint = if (isConsoleOpen) Color(0xFF34D399) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // 3. Bottom-Right Inspector Toggle
                IconButton(
                    onClick = onToggleInspector,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("xcode_toggle_inspector_btn")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = "Toggle Inspector Panel",
                        tint = if (isInspectorOpen) Color(0xFFF59E0B) else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
