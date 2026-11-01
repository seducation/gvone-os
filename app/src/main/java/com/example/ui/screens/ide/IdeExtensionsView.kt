package com.example.ui.screens.ide

import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.data.model.BrowserTab
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * VS Code Extensions Manager View for GVONE Code Studio.
 * Allows browsing, enabling/disabling, and triggering actions for extensions:
 * Prettier, ESLint, GitLens, Live Preview Server, and Language Tools.
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
    onOpenProblems: (String) -> Unit = {},
    onOpenSidePanel: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Formatters", "Linters", "Git", "Preview", "Languages")

    val filteredExtensions = extensions.filter { ext ->
        val matchesQuery = searchQuery.isEmpty() ||
                ext.name.contains(searchQuery, ignoreCase = true) ||
                ext.description.contains(searchQuery, ignoreCase = true)
        val matchesCategory = selectedCategory == "All" || ext.category.equals(selectedCategory, ignoreCase = true)
        matchesQuery && matchesCategory
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp)
            .testTag("ide_extensions_view")
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search Extensions in Marketplace", fontSize = 12.sp) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ide_extension_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

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
        }

        Spacer(modifier = Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filteredExtensions.forEach { ext ->
                ExtensionCard(
                    extension = ext,
                    onToggle = { onToggleExtension(ext.id) },
                    onAction = when (ext.id) {
                        "prettier" -> onRunPrettier
                        "eslint" -> ({ onInspectLinter(); onOpenProblems(ext.id) })
                        "gitlens" -> ({ onOpenGitLens(); onOpenSidePanel(ext.id) })
                        "live_server" -> ({ onLaunchLivePreview(); onOpenSidePanel(ext.id) })
                        else -> null
                    },
                    actionLabel = when (ext.id) {
                        "prettier" -> "Format Active"
                        "eslint" -> "Inspect"
                        "gitlens" -> "View Diffs"
                        "live_server" -> "Live Preview"
                        else -> null
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
    onToggle: () -> Unit,
    onAction: (() -> Unit)?,
    actionLabel: String?,
    activeTab: IdeTab?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (extension.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
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
                                if (extension.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = extension.icon,
                            contentDescription = extension.name,
                            tint = if (extension.isEnabled) MaterialTheme.colorScheme.primary
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

            // Extension specific action button if enabled
            if (extension.isEnabled && onAction != null && actionLabel != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = onAction,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(28.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(text = actionLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Diagnostics and problems inspector view showing lint and syntax items.
 */
@Composable
fun ExtensionProblemsInspectorView(
    onOpenFileInEditor: ((String, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sampleProblems = remember {
        listOf(
            Triple("index.html", 14, "Missing alt attribute on <img> element"),
            Triple("style.css", 48, "Unknown vendor property -webkit-user-select"),
            Triple("app.js", 32, "'result' is assigned a value but never used")
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Problems (${sampleProblems.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFE2E8F0)
                )
            }
            Text(
                text = "ESLint & CSS Linter",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF64748B)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(sampleProblems) { (file, line, msg) ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF131C2E),
                    border = BorderStroke(0.5.dp, Color(0xFF233554)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFileInEditor?.invoke(file, line) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = msg,
                                fontSize = 11.sp,
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$file:$line",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Extension side panels inspector view displaying active extensions & status.
 */
@Composable
fun ExtensionSidePanelsInspectorView(
    activeWebView: WebView?,
    activeTab: BrowserTab?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Extension,
                contentDescription = null,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "Active Extensions",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFE2E8F0)
            )
        }

        val activeExtList = remember {
            listOf(
                Triple("Prettier Formatter", "Active", Color(0xFF34D399)),
                Triple("ESLint Analyzer", "Running (3 issues)", Color(0xFFFBBF24)),
                Triple("Live Preview Server", "Port 8080 Active", Color(0xFF38BDF8)),
                Triple("GitLens", "main branch clean", Color(0xFFA78BFA))
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(activeExtList) { (name, status, color) ->
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF131C2E),
                    border = BorderStroke(0.5.dp, Color(0xFF233554)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE2E8F0)
                        )
                        Text(
                            text = status,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = color
                        )
                    }
                }
            }
        }
    }
}
