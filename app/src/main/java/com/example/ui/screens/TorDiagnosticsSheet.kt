package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserSettings
import com.example.data.tor.DiagnosticStatus
import com.example.data.tor.DiagnosticStep
import com.example.data.tor.TorDiagnosticReport
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorDiagnosticsSheet(
    report: TorDiagnosticReport?,
    isDiagnosing: Boolean,
    settings: BrowserSettings,
    onRunDiagnostics: () -> Unit,
    onApplyFix: (String) -> Unit,
    onReapplyProxy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val launchOrbot = {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("org.torproject.android")
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.torproject.android"))
                try {
                    context.startActivity(marketIntent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://orbot.app/")))
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
    }

    LaunchedEffect(Unit) {
        if (report == null && !isDiagnosing) {
            onRunDiagnostics()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0F141C),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF334155))
        },
        modifier = modifier.testTag("tor_diagnostics_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF7C3AED).copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Troubleshoot,
                                    contentDescription = null,
                                    tint = GVONESecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "Tor Network Diagnostic",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Deep inspection of proxy & WebView routing",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("close_diagnostics_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Status Summary Card
            item {
                val overallStatus = report?.overallStatus ?: if (isDiagnosing) DiagnosticStatus.RUNNING else DiagnosticStatus.WARNING
                val statusBg = when (overallStatus) {
                    DiagnosticStatus.PASSED -> Color(0xFF065F46).copy(alpha = 0.35f)
                    DiagnosticStatus.FAILED -> Color(0xFF7F1D1D).copy(alpha = 0.35f)
                    DiagnosticStatus.WARNING -> Color(0xFF78350F).copy(alpha = 0.35f)
                    DiagnosticStatus.RUNNING -> Color(0xFF1E293B)
                }
                val statusBorder = when (overallStatus) {
                    DiagnosticStatus.PASSED -> Color(0xFF10B981)
                    DiagnosticStatus.FAILED -> Color(0xFFEF4444)
                    DiagnosticStatus.WARNING -> Color(0xFFF59E0B)
                    DiagnosticStatus.RUNNING -> GVONESecondary
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = statusBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusBorder.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (isDiagnosing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = GVONESecondary
                                    )
                                } else {
                                    val icon = when (overallStatus) {
                                        DiagnosticStatus.PASSED -> Icons.Rounded.CheckCircle
                                        DiagnosticStatus.FAILED -> Icons.Rounded.Cancel
                                        DiagnosticStatus.WARNING -> Icons.Rounded.Warning
                                        DiagnosticStatus.RUNNING -> Icons.Rounded.Sync
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = statusBorder,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Text(
                                    text = when {
                                        isDiagnosing -> "Running Diagnostic Suite..."
                                        overallStatus == DiagnosticStatus.PASSED -> "TOR & WebView Healthy"
                                        overallStatus == DiagnosticStatus.FAILED -> "Traffic Stopped (Tor Offline)"
                                        else -> "Action Required"
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.3f),
                                modifier = Modifier.padding(2.dp)
                            ) {
                                Text(
                                    text = "${settings.torProxyHost}:${settings.torProxyPort}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = report?.summary ?: if (isDiagnosing) "Probing SOCKS5 socket, WebKit proxy override engine, and remote circuits..." else "Run diagnostics to analyze connection path.",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (report?.detectedExitIp != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VpnLock,
                                    contentDescription = null,
                                    tint = GVONESecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Verified Exit IP: ${report.detectedExitIp}",
                                    color = Color(0xFFA78BFA),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Quick Resolution Recommendations
            if (report?.suggestedFix != null && !isDiagnosing) {
                item {
                    val fix = report.suggestedFix
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF161C27),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263245)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoFixHigh,
                                    contentDescription = null,
                                    tint = GVONEPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Suggested 1-Tap Solution",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            when {
                                fix.startsWith("SWITCH_PORT_") -> {
                                    val port = fix.removePrefix("SWITCH_PORT_")
                                    Text(
                                        text = "Tor was detected actively running on port $port. Switch GVONE to port $port to resume browsing.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                    Button(
                                        onClick = { onApplyFix(fix) },
                                        modifier = Modifier.fillMaxWidth().testTag("fix_switch_port_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Switch GVONE to Port $port", fontSize = 13.sp)
                                    }
                                }

                                fix == "START_ORBOT" -> {
                                    Text(
                                        text = "Because Tor mode is enabled, GVONE prevents cleartext IP leaks by holding traffic until Orbot connects.",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = launchOrbot,
                                            modifier = Modifier.weight(1f).testTag("fix_launch_orbot_btn"),
                                            colors = ButtonDefaults.buttonColors(containerColor = GVONESecondary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Security,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Start Orbot", fontSize = 12.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { onApplyFix("DISABLE_TOR") },
                                            modifier = Modifier.weight(1f).testTag("fix_disable_tor_btn"),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Browse Direct", fontSize = 12.sp)
                                        }
                                    }
                                }

                                fix == "REAPPLY_PROXY" -> {
                                    Button(
                                        onClick = onReapplyProxy,
                                        modifier = Modifier.fillMaxWidth().testTag("fix_reapply_proxy_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Re-Apply Proxy to WebView Engine", fontSize = 13.sp)
                                    }
                                }

                                fix == "NEW_CIRCUIT" -> {
                                    Button(
                                        onClick = { onApplyFix("NEW_CIRCUIT") },
                                        modifier = Modifier.fillMaxWidth().testTag("fix_new_circuit_btn"),
                                        colors = ButtonDefaults.buttonColors(containerColor = GVONESecondary),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Rebuild Tor Circuit", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Diagnostic Step-by-Step Breakdown
            item {
                Text(
                    text = "DIAGNOSTIC LAYERS",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            val steps = report?.steps ?: emptyList()
            if (steps.isNotEmpty()) {
                items(steps) { step ->
                    DiagnosticStepItem(step = step)
                }
            } else if (isDiagnosing) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = GVONESecondary, strokeWidth = 2.dp)
                            Text(
                                text = "Evaluating network layers...",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Primary Bottom Actions
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onReapplyProxy,
                        enabled = !isDiagnosing,
                        modifier = Modifier.weight(1f).testTag("reapply_webview_proxy_btn"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = GVONESecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-sync WebKit", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onRunDiagnostics,
                        enabled = !isDiagnosing,
                        modifier = Modifier.weight(1f).testTag("retest_diagnostics_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isDiagnosing) "Testing..." else "Retest Network", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticStepItem(
    step: DiagnosticStep,
    modifier: Modifier = Modifier
) {
    val icon = when (step.status) {
        DiagnosticStatus.PASSED -> Icons.Rounded.CheckCircle
        DiagnosticStatus.FAILED -> Icons.Rounded.Cancel
        DiagnosticStatus.WARNING -> Icons.Rounded.Info
        DiagnosticStatus.RUNNING -> Icons.Rounded.Sync
    }
    val tint = when (step.status) {
        DiagnosticStatus.PASSED -> Color(0xFF10B981)
        DiagnosticStatus.FAILED -> Color(0xFFEF4444)
        DiagnosticStatus.WARNING -> Color(0xFFF59E0B)
        DiagnosticStatus.RUNNING -> GVONESecondary
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF131924),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = step.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (step.latencyMs != null) {
                        Text(
                            text = "${step.latencyMs}ms",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = step.details,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
