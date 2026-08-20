package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GVONEAISearchResult
import com.example.data.model.SourceCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISearchOverlay(
    result: GVONEAISearchResult?,
    isLoading: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onFollowUp: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xFF0D121C),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF324158))
            )
        },
        modifier = modifier.testTag("ai_search_overlay")
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(color = GVONEPrimary)
                    Text(
                        text = "GVONE AI synthesizing web knowledge...",
                        color = GVONETextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else if (result != null) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Query
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = GVONEPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = result.query,
                                color = GVONETextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = GVONETextSecondary
                            )
                        }
                    }
                }

                // AI Answer Card
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF141C2A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF283852)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(GVONETertiary)
                                )
                                Text(
                                    text = "AI ANSWER",
                                    color = GVONETertiary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = result.aiAnswer,
                                color = GVONETextPrimary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // Key Takeaways
                if (result.keyTakeaways.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Key Takeaways",
                                color = GVONETextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            result.keyTakeaways.forEach { takeaway ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = GVONESecondary,
                                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                                    )
                                    Text(
                                        text = takeaway,
                                        color = GVONETextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Source Cards (Prompt Section 6: 4-5 relevant source cards)
                if (result.sources.isNotEmpty()) {
                    item {
                        Text(
                            text = "Web Sources (${result.sources.size})",
                            color = GVONETextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(result.sources) { source ->
                        SourceCardView(
                            source = source,
                            onOpen = { onOpenUrl(source.url) },
                            onOpenNewTab = { onOpenInNewTab(source.url) }
                        )
                    }
                }

                // Follow-up Questions
                if (result.followUpQuestions.isNotEmpty()) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Follow-up Inquiries",
                                color = GVONETextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(result.followUpQuestions) { question ->
                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onFollowUp(question) },
                                        color = Color(0xFF162030),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF26364E))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Rounded.ArrowForward,
                                                contentDescription = null,
                                                tint = GVONEPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = question,
                                                color = GVONETextPrimary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SourceCardView(
    source: SourceCard,
    onOpen: () -> Unit,
    onOpenNewTab: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onOpen() },
        color = Color(0xFF131A26),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF243044))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
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
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        tint = GVONESecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = source.domain,
                        color = GVONESecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onOpenNewTab,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Open in New Tab",
                            tint = GVONETextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Text(
                text = source.title,
                color = GVONETextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (source.snippet.isNotBlank()) {
                Text(
                    text = source.snippet,
                    color = GVONETextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
