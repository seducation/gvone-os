package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agent.safety.PermissionRequest
import com.example.agent.safety.PermissionSystem
import com.example.ui.theme.*

@Composable
fun PermissionPromptDialog(
    request: PermissionRequest,
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit,
    onBlockAlways: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCritical = request.risk.equals("CRITICAL", ignoreCase = true)
    val isHigh = request.risk.equals("HIGH", ignoreCase = true)
    
    val riskColor = when {
        isCritical -> GVONEAccentRed
        isHigh -> GVONEAccentOrange
        else -> GVONETertiary
    }

    val riskBg = when {
        isCritical -> Color(0x33EF4444)
        isHigh -> Color(0x33F97316)
        else -> Color(0x3310B981)
    }

    val permissionDescriptor = PermissionSystem.ALL_DESCRIPTORS.find {
        it.key.equals(request.permission, ignoreCase = true) ||
                request.permission.startsWith(it.key.substringBefore("."))
    }

    val icon: ImageVector = when {
        request.permission.contains("filesystem") || request.permission.contains("file") -> Icons.Rounded.FolderSpecial
        request.permission.contains("shell") -> Icons.Rounded.Terminal
        request.permission.contains("browser") -> Icons.Rounded.Language
        request.permission.contains("network") || request.permission.contains("tor") -> Icons.Rounded.VpnKey
        request.permission.contains("git") -> Icons.Rounded.Code
        request.permission.contains("external") -> Icons.Rounded.CloudSync
        else -> Icons.Rounded.Security
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(riskColor.copy(alpha = 0.6f), Color(0xFF30363D))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .testTag("permission_prompt_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131822)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                // Top Row: Agent & Risk Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Agent badge
                    Surface(
                        color = Color(0xFF1C2433),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFF2A364F))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartToy,
                                contentDescription = null,
                                tint = GVONEPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = request.agentName.ifBlank { "Agent System" },
                                color = GVONETextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Risk Badge
                    Surface(
                        color = riskBg,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.5f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(riskColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${request.risk.uppercase()} RISK",
                                color = riskColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Center Icon and Title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        color = riskColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, riskColor.copy(alpha = 0.3f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Permission Category Icon",
                                tint = riskColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = permissionDescriptor?.title ?: request.permission,
                            color = GVONETextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = request.permission,
                            color = GVONETextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Description Box
                Surface(
                    color = Color(0xFF0F141D),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF222B3D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Operation Details:",
                            color = GVONETextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = request.description.ifBlank {
                                permissionDescriptor?.description ?: "The agent requested to perform an operation requiring elevated safety clearance."
                            },
                            color = GVONETextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Primary choices (Allow Once vs Always Allow)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAllowOnce,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("permission_allow_once_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GVONEPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow Once", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }

                        FilledTonalButton(
                            onClick = onAlwaysAllow,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("permission_always_allow_btn"),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF1F293D),
                                contentColor = GVONESecondary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Always Allow", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Row 2: Secondary choices (Deny Once vs Always Block)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDeny,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("permission_deny_btn"),
                            border = BorderStroke(1.dp, Color(0xFF384459)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = GVONETextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = GVONETextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Deny", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onBlockAlways,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("permission_block_btn"),
                            border = BorderStroke(1.dp, GVONEAccentRed.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = GVONEAccentRed
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = GVONEAccentRed
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Always Block", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
