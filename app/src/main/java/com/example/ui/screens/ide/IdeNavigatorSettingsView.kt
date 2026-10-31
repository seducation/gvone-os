package com.example.ui.screens.ide

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Preferences for Navigator and Workspace IDE configuration.
 */
data class WorkspacePreferences(
    val unifiedDeveloperBarDropdown: Boolean = true,
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = true,
    val formatOnSave: Boolean = true,
    val tabSize: Int = 2,
    val mcpAutoConnect: Boolean = true,
    val requireToolApproval: Boolean = true,
    val torEnabled: Boolean = false,
    val strictHttps: Boolean = true,
    val blockThirdPartyCookies: Boolean = true
)

object WorkspacePreferencesManager {
    private val _prefs = MutableStateFlow(WorkspacePreferences())
    val prefs: StateFlow<WorkspacePreferences> = _prefs.asStateFlow()

    fun updatePrefs(transform: (WorkspacePreferences) -> WorkspacePreferences) {
        _prefs.update(transform)
    }
}

/**
 * Dedicated Navigator view for Workspace & IDE Settings.
 */
@Composable
fun IdeNavigatorSettingsView(
    onOpenFullSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs by WorkspacePreferencesManager.prefs.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(12.dp)
            .testTag("navigator_settings_container"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        // --- HEADER BANNER ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF64748B), Color(0xFF334155))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Workspace & IDE Settings",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Navigator layout, editor studio, MCP tools & privacy",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }

                    Button(
                        onClick = onOpenFullSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp).testTag("open_system_settings_btn")
                    ) {
                        Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("All Settings", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // --- SECTION 1: DEVELOPER BAR & WORKSPACE MODE ---
        item {
            SettingsCategoryCard(
                title = "Developer Bar & Layout",
                icon = Icons.Rounded.DashboardCustomize,
                iconTint = Color(0xFF00E5FF)
            ) {
                SettingsToggleRow(
                    title = "Unified Dropdown Menu",
                    subtitle = "Single dropdown menu replacing switchable toggle in top bar",
                    checked = prefs.unifiedDeveloperBarDropdown,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(unifiedDeveloperBarDropdown = it) }
                        Toast.makeText(context, "Developer Bar set to ${if (it) "Unified Dropdown" else "Segmented"}", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                SettingsToggleRow(
                    title = "Show Line Numbers",
                    subtitle = "Display line numbers in Monospace code studio editor",
                    checked = prefs.showLineNumbers,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(showLineNumbers = it) }
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                SettingsToggleRow(
                    title = "Editor Word Wrap",
                    subtitle = "Soft-wrap long lines of code in workspace editor",
                    checked = prefs.wordWrap,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(wordWrap = it) }
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                SettingsToggleRow(
                    title = "Format on Save (Prettier)",
                    subtitle = "Automatically run formatting when saving files",
                    checked = prefs.formatOnSave,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(formatOnSave = it) }
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                // Tab size selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Tab Indentation Size", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Number of spaces per indentation level", color = Color(0xFF94A3B8), fontSize = 10.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(2, 4).forEach { size ->
                            val isSelected = prefs.tabSize == size
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF0F172A),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF334155)),
                                modifier = Modifier.clickable {
                                    WorkspacePreferencesManager.updatePrefs { p -> p.copy(tabSize = size) }
                                }
                            ) {
                                Text(
                                    text = "$size Spaces",
                                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SECTION 2: MODEL CONTEXT PROTOCOL & AI AGENTS ---
        item {
            SettingsCategoryCard(
                title = "Model Context Protocol & Copilot",
                icon = Icons.Rounded.Hub,
                iconTint = Color(0xFFEC4899)
            ) {
                SettingsToggleRow(
                    title = "Auto-connect MCP Servers",
                    subtitle = "Automatically initialize stdio/SSE tool servers on launch",
                    checked = prefs.mcpAutoConnect,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(mcpAutoConnect = it) }
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                SettingsToggleRow(
                    title = "Require Tool Call Approval",
                    subtitle = "Prompt before AI executes file writes or shell commands",
                    checked = prefs.requireToolApproval,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(requireToolApproval = it) }
                    }
                )
            }
        }

        // --- SECTION 3: PRIVACY & NETWORK ---
        item {
            SettingsCategoryCard(
                title = "Privacy, Security & Onion Routing",
                icon = Icons.Rounded.Security,
                iconTint = Color(0xFF10B981)
            ) {
                SettingsToggleRow(
                    title = "Tor Onion Routing Proxy",
                    subtitle = "Route all web & API traffic through the Tor anonymity network",
                    checked = prefs.torEnabled,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(torEnabled = it) }
                        Toast.makeText(context, if (it) "Tor routing activated" else "Tor routing disabled", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                SettingsToggleRow(
                    title = "Strict HTTPS Enforcement",
                    subtitle = "Automatically upgrade connections to secure TLS 1.3",
                    checked = prefs.strictHttps,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(strictHttps = it) }
                    }
                )

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                SettingsToggleRow(
                    title = "Block Third-Party Cookies",
                    subtitle = "Prevent cross-site tracking scripts and cookies",
                    checked = prefs.blockThirdPartyCookies,
                    onCheckedChange = {
                        WorkspacePreferencesManager.updatePrefs { p -> p.copy(blockThirdPartyCookies = it) }
                    }
                )
            }
        }

        // --- SECTION 4: QUICK MAINTENANCE & RESET ACTIONS ---
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF161F2E),
                border = BorderStroke(1.dp, Color(0xFF2C3E5A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Workspace Maintenance", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Workspace cache cleared", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Cache", fontSize = 11.sp)
                        }

                        Button(
                            onClick = {
                                WorkspacePreferencesManager.updatePrefs { WorkspacePreferences() }
                                Toast.makeText(context, "Workspace reset to default", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Layout", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsCategoryCard(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161F2E),
        border = BorderStroke(1.dp, Color(0xFF2C3E5A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 10.sp, lineHeight = 13.sp)
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF38BDF8),
                uncheckedThumbColor = Color(0xFF94A3B8),
                uncheckedTrackColor = Color(0xFF334155)
            ),
            modifier = Modifier.size(36.dp)
        )
    }
}
