package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.command.CommandEngine
import com.example.data.model.*
import com.example.ui.theme.*

@Composable
fun CommandAutocompletePopup(
    suggestions: List<CommandSuggestion>,
    onSelectSuggestion: (CommandSuggestion, Boolean) -> Unit, // Boolean: executeImmediately
    onOpenCommandManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(18.dp), spotColor = Color.Black.copy(alpha = 0.7f))
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF5131A24),
                        Color(0xFA0B0F15)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GVONEPrimary.copy(alpha = 0.4f),
                        Color(0x22FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("command_autocomplete_popup"),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // Header: Terminal indicator & Manage button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = GVONEPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TERMINAL COMMANDS",
                        color = GVONEPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenCommandManager() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Manage",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            }

            Divider(color = Color(0xFF1E293B), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Suggestions List (up to 5 suggestions to keep it snappy)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                suggestions.take(5).forEachIndexed { index, suggestion ->
                    val cmd = suggestion.command
                    val isQueryEmpty = suggestion.queryArgument.isEmpty()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // If there is an argument, execute it; otherwise autocomplete text
                                onSelectSuggestion(suggestion, !isQueryEmpty)
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("command_suggestion_${cmd.command.removePrefix("/")}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Monospace command pill
                        Surface(
                            color = GVONEPrimary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GVONEPrimary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = suggestion.matchedTrigger,
                                color = GVONEPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // Details: Name + Action Preview
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = cmd.name,
                                    color = Color(0xFFF1F5F9),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                val (typeColor, typeLabel) = when (cmd.type) {
                                    CommandType.SEARCH -> GVONEPrimary to "Search"
                                    CommandType.AI -> GVONESecondary to "GVONE AI"
                                    CommandType.URL -> Color(0xFF38BDF8) to "URL"
                                    CommandType.PAGE_ACTION -> Color(0xFFA855F7) to "Page"
                                    CommandType.BROWSER_ACTION -> Color(0xFFF59E0B) to "Action"
                                    CommandType.AUTOMATION -> Color(0xFF10B981) to "JS"
                                }

                                Surface(
                                    color = typeColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = typeLabel,
                                        color = typeColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }

                            Text(
                                text = suggestion.displayPreview,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Trailing Action Icon: Run (arrow) or Fill (insert)
                        IconButton(
                            onClick = {
                                onSelectSuggestion(suggestion, true)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (!isQueryEmpty) Icons.Rounded.ArrowForward else Icons.Rounded.NorthWest,
                                contentDescription = if (!isQueryEmpty) "Execute" else "Autocomplete",
                                tint = if (!isQueryEmpty) GVONEPrimary else Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (index < suggestions.take(5).size - 1) {
                        Divider(
                            color = Color(0xFF17202D),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }
    }
}
