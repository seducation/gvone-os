package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class representing an item in the Action Menu.
 */
data class ActionMenuItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String,
    val onClick: () -> Unit
)

/**
 * Horizontal scrollable row of dark gray squircle cards.
 * Each card features a minimalist white outline icon at the top and a centered text label below.
 * Visible options: 'Photos' (with image-plus icon), 'Camera', 'Files', and a partially visible item on the right.
 */
@Composable
fun HorizontalActionCardsRow(
    onPhotosClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFilesClick: () -> Unit,
    modifier: Modifier = Modifier,
    onWebsiteClick: (() -> Unit)? = null,
    onConnectorsClick: (() -> Unit)? = null,
    onResearchClick: (() -> Unit)? = null,
    onPinTerminalClick: (() -> Unit)? = null
) {
    val items = remember(onPhotosClick, onCameraClick, onFilesClick, onWebsiteClick, onConnectorsClick, onResearchClick, onPinTerminalClick) {
        listOfNotNull(
            ActionMenuItem(
                id = "photos",
                label = "Photos",
                icon = Icons.Rounded.AddPhotoAlternate,
                testTag = "action_card_photos",
                onClick = onPhotosClick
            ),
            ActionMenuItem(
                id = "camera",
                label = "Camera",
                icon = Icons.Rounded.PhotoCamera,
                testTag = "action_card_camera",
                onClick = onCameraClick
            ),
            ActionMenuItem(
                id = "files",
                label = "Files",
                icon = Icons.Rounded.Folder,
                testTag = "action_card_files",
                onClick = onFilesClick
            ),
            if (onWebsiteClick != null) {
                ActionMenuItem(
                    id = "website",
                    label = "Website",
                    icon = Icons.Rounded.Language,
                    testTag = "action_card_website",
                    onClick = onWebsiteClick
                )
            } else null,
            // Partially visible item on the right (and additional items upon horizontal scroll)
            ActionMenuItem(
                id = "connectors",
                label = "Connectors",
                icon = Icons.Rounded.Hub,
                testTag = "action_card_connectors",
                onClick = { onConnectorsClick?.invoke() }
            ),
            if (onResearchClick != null) {
                ActionMenuItem(
                    id = "research",
                    label = "Research",
                    icon = Icons.Rounded.Science,
                    testTag = "action_card_research",
                    onClick = onResearchClick
                )
            } else null,
            if (onPinTerminalClick != null) {
                ActionMenuItem(
                    id = "terminal",
                    label = "Terminal",
                    icon = Icons.Rounded.PushPin,
                    testTag = "action_card_terminal",
                    onClick = onPinTerminalClick
                )
            } else null
        )
    }

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag("horizontal_action_cards_row"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(items, key = { it.id }) { item ->
            DarkSquircleActionCard(item = item)
        }
    }
}

/**
 * Dark gray squircle card with minimalist white outline icon at top and centered text label below.
 */
@Composable
fun DarkSquircleActionCard(
    item: ActionMenuItem,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(84.dp)
            .height(92.dp)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(18.dp),
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable { item.onClick() }
            .testTag(item.testTag),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF1E2430), // Dark gray squircle card
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    Color(0x33FFFFFF),
                    Color(0x12FFFFFF)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Minimalist white outline icon at the top
            Box(
                modifier = Modifier
                    .size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Centered text label below
            Text(
                text = item.label,
                color = Color(0xFFF1F5F9),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Horizontal scrollable bottom sheet action menu in dark mode.
 * The UI features:
 * - Top drag-handle pill indicator
 * - Dark mode theme
 * - Dark gray squircle cards arranged horizontally
 * - Minimalist white outline icon at top, centered text label below
 * - Visible options: 'Photos' (image-plus icon), 'Camera', 'Files', and a partially visible item on the right
 */
@Composable
fun ActionMenuBottomSheet(
    onPhotosClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFilesClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onWebsiteClick: (() -> Unit)? = null,
    onConnectorsClick: (() -> Unit)? = null,
    onResearchClick: (() -> Unit)? = null,
    onPinTerminalClick: (() -> Unit)? = null,
    title: String = "Action & Command"
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 24.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), spotColor = Color.Black)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(Color(0xFF0F141C)) // Dark mode background
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color(0x22FFFFFF),
                        Color(0x05FFFFFF)
                    )
                ),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .testTag("action_menu_bottom_sheet"),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xFF0F141C)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top drag-handle pill indicator
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF64748B)) // Muted drag-handle pill
                    .testTag("action_menu_drag_handle")
            )

            // Header Row with Title and Close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title.uppercase(),
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.testTag("action_menu_title")
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(26.dp)
                        .testTag("action_menu_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Action Menu",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Horizontal scrollable dark gray squircle cards
            HorizontalActionCardsRow(
                onPhotosClick = {
                    onPhotosClick()
                    onDismiss()
                },
                onCameraClick = {
                    onCameraClick()
                    onDismiss()
                },
                onFilesClick = {
                    onFilesClick()
                    onDismiss()
                },
                onWebsiteClick = onWebsiteClick?.let { action ->
                    {
                        action()
                        onDismiss()
                    }
                },
                onConnectorsClick = onConnectorsClick?.let { action ->
                    {
                        action()
                        onDismiss()
                    }
                },
                onResearchClick = onResearchClick?.let { action ->
                    {
                        action()
                        onDismiss()
                    }
                },
                onPinTerminalClick = onPinTerminalClick?.let { action ->
                    {
                        action()
                        onDismiss()
                    }
                }
            )
        }
    }
}
