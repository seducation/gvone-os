package com.example.ui.screens.ide

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * VS Code Extensions Manager View for GVONE Code Studio.
 * Allows browsing, enabling/disabling, inspecting diagnostics & problems,
 * and opening extension tools into the Inspector Panel as side panel tabs.
 */
@Composable
fun IdeExtensionsView(
    extensions: List<IdeExtension>,
    onToggleExtension: (String) -> Unit,
    onRunPrettier: () -> Unit,
    onInspectLinter: () -> Unit,
    onOpenGitLens: () -> Unit,
    onLaunchLivePreview: () -> Unit,
    activeTab: IdeTab?,
    onOpenProblems: ((extensionId: String?) -> Unit)? = null,
    onOpenSidePanel: ((extensionId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Collect live extension problems from ExtensionProblemsManager
    val globalProblems by ExtensionProblemsManager.problems.collectAsState()
    val totalProblemCount = globalProblems.size

    val categories = remember(extensions) {
        listOf("All") + extensions.map { it.category }.distinct()
    }

    val filteredExtensions = extensions.filter { ext ->
        val matchesQuery = searchQuery.isEmpty() ||
                ext.name.contains(searchQuery, ignoreCase = true) ||
                ext.description.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "All" || ext.category.equals(selectedCategory, ignoreCase = true)
        matchesQuery && matchesCategory
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
            .testTag("ide_extensions_view")
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Extensions & Tools", fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ide_extension_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Extension Problems Banner if any issues exist
        if (totalProblemCount > 0) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFEF4444).copy(alpha = 0.15f),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onOpenProblems?.invoke(null)
                    }
                    .testTag("ide_extensions_problems_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "$totalProblemCount Extension Problem(s) Detected",
                                color = Color(0xFFEF4444),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Linter warnings, syntax issues & security CSP alerts",
                                color = Color(0xFFE2E8F0),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFEF4444),
                        modifier = Modifier.clickable { onOpenProblems?.invoke(null) }
                    ) {
                        Text(
                            text = "View in Inspector",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(
                        text = cat,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INSTALLED (${extensions.count { it.isEnabled }} / ${extensions.size})",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (onOpenProblems != null) {
                Text(
                    text = "Problems ($totalProblemCount)",
                    color = if (totalProblemCount > 0) Color(0xFFEF4444) else Color(0xFF10B981),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOpenProblems(null) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredExtensions, key = { it.id }) { ext ->
                val extProblems = globalProblems.filter { it.extensionId == ext.id }

                ExtensionCard(
                    extension = ext,
                    problems = extProblems,
                    onToggle = { onToggleExtension(ext.id) },
                    onAction = when (ext.id) {
                        "prettier" -> onRunPrettier
                        "eslint" -> {
                            if (extProblems.isNotEmpty() && onOpenProblems != null) {
                                { onOpenProblems(ext.id) }
                            } else {
                                onInspectLinter
                            }
                        }
                        "gitlens" -> onOpenGitLens
                        "live_server" -> onLaunchLivePreview
                        else -> {
                            {
                                if (onOpenSidePanel != null) {
                                    onOpenSidePanel(ext.id)
                                } else {
                                    onToggleExtension(ext.id)
                                }
                            }
                        }
                    },
                    actionLabel = when (ext.id) {
                        "prettier" -> "Format Active"
                        "eslint" -> if (extProblems.isNotEmpty()) "Problems (${extProblems.size})" else "Inspect"
                        "gitlens" -> "View Diffs"
                        "live_server" -> "Live Preview"
                        "youtube_enhancer" -> "Side Panel"
                        "youtube_audio_pip" -> "Side Panel"
                        "adblock_pro" -> "Shield Panel"
                        "dark_reader" -> "Dark Settings"
                        "data_saver" -> "Data Monitor"
                        "page_translator" -> "Translator"
                        "web_inspector" -> "DevTools"
                        else -> "Side Panel"
                    },
                    onOpenProblems = {
                        if (onOpenProblems != null) {
                            onOpenProblems(ext.id)
                        } else {
                            Toast.makeText(context, "${ext.name} has ${extProblems.size} problem(s)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenSidePanel = {
                        if (onOpenSidePanel != null) {
                            onOpenSidePanel(ext.id)
                        } else {
                            Toast.makeText(context, "Opening ${ext.name} in Inspector", Toast.LENGTH_SHORT).show()
                        }
                    },
                    activeTab = activeTab
                )
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    extension: IdeExtension,
    problems: List<ExtensionProblem> = emptyList(),
    onToggle: () -> Unit,
    onAction: (() -> Unit)?,
    actionLabel: String?,
    onOpenProblems: () -> Unit,
    onOpenSidePanel: () -> Unit,
    activeTab: IdeTab?
) {
    var expandedProblems by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (problems.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.5f)
            else if (extension.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ide_extension_${extension.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (problems.isNotEmpty()) Color(0xFFEF4444).copy(alpha = 0.2f)
                                else if (extension.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = extension.icon,
                            contentDescription = extension.name,
                            tint = if (problems.isNotEmpty()) Color(0xFFEF4444)
                            else if (extension.isEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = extension.name,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = extension.publisher,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = extension.version,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Enable/Disable switch
                Switch(
                    checked = extension.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = extension.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Problem Warning Badge if this extension reported issues
            if (problems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEF4444).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedProblems = !expandedProblems }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                            Text(
                                text = "${problems.size} problem(s) detected",
                                color = Color(0xFFEF4444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (expandedProblems) "Hide Details ▲" else "Show Details ▼",
                            color = Color(0xFFFCA5A5),
                            fontSize = 10.sp
                        )
                    }
                }

                // Expandable preview of problems on the card
                if (expandedProblems) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        problems.forEach { prob ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF131B2A),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${prob.file}:${prob.line}",
                                            color = Color(0xFF00E5FF),
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = prob.message,
                                            color = Color(0xFFE2E8F0),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = onOpenProblems,
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(22.dp)
                                    ) {
                                        Text("Fix", fontSize = 10.sp, color = Color(0xFFEF4444))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Extension Action Buttons Row (Side Panel & Action)
            if (extension.isEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Open in Inspector Side Panel button
                    OutlinedButton(
                        onClick = onOpenSidePanel,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ViewSidebar,
                            contentDescription = "Side Panel",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Side Panel",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // Primary action button if defined
                    if (onAction != null && actionLabel != null) {
                        FilledTonalButton(
                            onClick = onAction,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = if (problems.isNotEmpty()) {
                                ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.2f), contentColor = Color(0xFFEF4444))
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            }
                        ) {
                            Text(text = actionLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
