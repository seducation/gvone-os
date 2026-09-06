package com.example.ui.contextmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkContextMenuBottomSheet(
    data: LinkContextMenuData,
    onDismiss: () -> Unit,
    onOpenInNewTab: (String) -> Unit,
    onOpenInNewTabInGroup: (String) -> Unit,
    onOpenInIncognito: (String) -> Unit,
    onOpenInNewWindow: (String) -> Unit,
    onPreviewPage: (String, String) -> Unit,
    onCopyLinkAddress: (String) -> Unit,
    onCopyLinkText: (String) -> Unit,
    onDownloadResource: (String, String?) -> Unit,
    onToggleBookmark: (String, String, String?) -> Unit,
    onToggleReadingList: (String, String, String?) -> Unit,
    onShareLink: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0D131F),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x35FFFFFF))
            )
        },
        modifier = modifier.testTag("link_context_menu_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Header Preview Card
            ContextMenuHeader(data = data)

            HorizontalDivider(
                color = Color(0x18FFFFFF),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Scrollable Menu Rows
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                // Section 1: Navigation Actions
                item {
                    val openTabTitle = when (data.targetType) {
                        ContextMenuTargetType.IMAGE -> "Open image in new tab"
                        ContextMenuTargetType.VIDEO -> "Open video in new tab"
                        ContextMenuTargetType.AUDIO -> "Open audio in new tab"
                        else -> "Open in new tab"
                    }
                    ContextMenuRow(
                        icon = Icons.Rounded.Tab,
                        title = openTabTitle,
                        tag = "menu_open_in_new_tab",
                        onClick = {
                            onDismiss()
                            onOpenInNewTab(data.url)
                        }
                    )
                }

                item {
                    val groupTitle = when (data.targetType) {
                        ContextMenuTargetType.IMAGE -> "Open image in new tab in group"
                        ContextMenuTargetType.VIDEO -> "Open video in new tab in group"
                        else -> "Open in new tab in group"
                    }
                    ContextMenuRow(
                        icon = Icons.Rounded.CreateNewFolder,
                        title = groupTitle,
                        tag = "menu_open_in_new_tab_group",
                        onClick = {
                            onDismiss()
                            onOpenInNewTabInGroup(data.url)
                        }
                    )
                }

                item {
                    ContextMenuRow(
                        icon = Icons.Rounded.Security,
                        title = "Open in Incognito tab",
                        tag = "menu_open_in_incognito",
                        onClick = {
                            onDismiss()
                            onOpenInIncognito(data.url)
                        }
                    )
                }

                item {
                    ContextMenuRow(
                        icon = Icons.Rounded.OpenInNew,
                        title = "Open in new window",
                        tag = "menu_open_in_new_window",
                        onClick = {
                            onDismiss()
                            onOpenInNewWindow(data.url)
                        }
                    )
                }

                item {
                    val previewTitle = when (data.targetType) {
                        ContextMenuTargetType.IMAGE -> "Preview image"
                        ContextMenuTargetType.VIDEO -> "Preview video"
                        ContextMenuTargetType.AUDIO -> "Preview audio"
                        else -> "Preview page"
                    }
                    ContextMenuRow(
                        icon = Icons.Rounded.Visibility,
                        title = previewTitle,
                        tag = "menu_preview_page",
                        onClick = {
                            onDismiss()
                            onPreviewPage(data.url, data.displayTitle)
                        }
                    )
                }

                // Section 2 Divider: Content & Downloads
                item {
                    HorizontalDivider(
                        color = Color(0x14FFFFFF),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                item {
                    val copyUrlTitle = when (data.targetType) {
                        ContextMenuTargetType.IMAGE -> "Copy image address"
                        ContextMenuTargetType.VIDEO -> "Copy video address"
                        ContextMenuTargetType.AUDIO -> "Copy audio address"
                        else -> "Copy link address"
                    }
                    ContextMenuRow(
                        icon = Icons.Rounded.ContentCopy,
                        title = copyUrlTitle,
                        tag = "menu_copy_link_address",
                        onClick = {
                            onDismiss()
                            onCopyLinkAddress(data.url)
                        }
                    )
                }

                // If element is an image wrapped inside a link, offer copying both
                if (data.targetType == ContextMenuTargetType.IMAGE_LINK && !data.srcUrl.isNullOrBlank()) {
                    item {
                        ContextMenuRow(
                            icon = Icons.Rounded.Image,
                            title = "Copy image address",
                            tag = "menu_copy_image_address",
                            onClick = {
                                onDismiss()
                                onCopyLinkAddress(data.srcUrl)
                            }
                        )
                    }
                }

                // Copy Link Text (only when visible text/title is present)
                if (data.text.isNotBlank() && data.text != data.url) {
                    item {
                        ContextMenuRow(
                            icon = Icons.Rounded.TextFields,
                            title = "Copy link text",
                            tag = "menu_copy_link_text",
                            onClick = {
                                onDismiss()
                                onCopyLinkText(data.text)
                            }
                        )
                    }
                }

                // Download Resource
                item {
                    val downloadTitle = when (data.targetType) {
                        ContextMenuTargetType.IMAGE -> "Save image"
                        ContextMenuTargetType.VIDEO -> "Save video"
                        ContextMenuTargetType.AUDIO -> "Save audio"
                        ContextMenuTargetType.DOCUMENT -> "Download file"
                        else -> "Download link"
                    }
                    val downloadUrl = if (data.targetType.isMedia && !data.srcUrl.isNullOrBlank()) data.srcUrl else data.url
                    ContextMenuRow(
                        icon = Icons.Rounded.Download,
                        title = downloadTitle,
                        tag = "menu_download_link",
                        onClick = {
                            onDismiss()
                            onDownloadResource(downloadUrl, data.mimeType)
                        }
                    )
                }

                // Section 3 Divider: Organization & Sharing
                item {
                    HorizontalDivider(
                        color = Color(0x14FFFFFF),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // Add to bookmark / Remove from bookmarks
                item {
                    val bookmarkTitle = if (data.isBookmarked) "Remove from bookmarks" else "Add to bookmark"
                    val bookmarkIcon = if (data.isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder
                    val bookmarkTint = if (data.isBookmarked) GVONESecondary else GVONETextPrimary
                    ContextMenuRow(
                        icon = bookmarkIcon,
                        title = bookmarkTitle,
                        iconTint = bookmarkTint,
                        tag = "menu_toggle_bookmark",
                        onClick = {
                            onDismiss()
                            onToggleBookmark(data.url, data.displayTitle, data.faviconUrl)
                        }
                    )
                }

                // Add to reading list / Remove from reading list
                item {
                    val readingListTitle = if (data.isInReadingList) "Remove from reading list" else "Add to reading list"
                    val readingListIcon = Icons.Rounded.MenuBook
                    val readingListTint = if (data.isInReadingList) GVONETertiary else GVONETextPrimary
                    ContextMenuRow(
                        icon = readingListIcon,
                        title = readingListTitle,
                        iconTint = readingListTint,
                        tag = "menu_toggle_reading_list",
                        onClick = {
                            onDismiss()
                            onToggleReadingList(data.url, data.displayTitle, data.faviconUrl)
                        }
                    )
                }

                // Share link / Share media
                item {
                    val shareTitle = when (data.targetType) {
                        ContextMenuTargetType.IMAGE -> "Share image"
                        ContextMenuTargetType.VIDEO -> "Share video"
                        else -> "Share link"
                    }
                    ContextMenuRow(
                        icon = Icons.Rounded.Share,
                        title = shareTitle,
                        tag = "menu_share_link",
                        onClick = {
                            onDismiss()
                            onShareLink(data.url, data.displayTitle)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Top Header inside the context menu displaying element preview, truncated title, and URL
 */
@Composable
private fun ContextMenuHeader(
    data: LinkContextMenuData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail or Type Icon Box
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF192132)),
            contentAlignment = Alignment.Center
        ) {
            val (icon, tint) = when (data.targetType) {
                ContextMenuTargetType.IMAGE, ContextMenuTargetType.IMAGE_LINK -> Icons.Rounded.Image to GVONEPrimary
                ContextMenuTargetType.VIDEO -> Icons.Rounded.Videocam to GVONETertiary
                ContextMenuTargetType.AUDIO -> Icons.Rounded.Audiotrack to GVONEAccentPurple
                ContextMenuTargetType.DOCUMENT -> Icons.Rounded.Description to GVONEAccentOrange
                ContextMenuTargetType.LINK -> Icons.Rounded.Language to GVONEPrimary
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and URL info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = data.displayTitle,
                color = GVONETextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = data.displaySubtitle,
                color = GVONETextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Individual tappable menu item row with 48dp+ touch target and ripple feedback
 */
@Composable
private fun ContextMenuRow(
    icon: ImageVector,
    title: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = GVONETextPrimary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = GVONETextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
