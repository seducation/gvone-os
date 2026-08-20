package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.isInternalHomeUrl
import com.example.ui.theme.*

/**
 * Premium Safari-Inspired Bottom Floating Browser Controls
 * Exactly 3 major UI areas:
 * 1. Left Circular Button: Safari-style overlapping tabs switcher with scale-down feedback.
 * 2. Center Elongated Pill: Glassmorphic address/search bar with SF Pro typography, SSL lock, URL preview,
 *    live progress line, and autocomplete suggestions flyout.
 * 3. Right Circular Button: Safari three-dot "More" action menu.
 */
@Composable
fun FloatingAddressBar(
    currentTab: BrowserTab?,
    tabCount: Int,
    isPrivate: Boolean,
    isTorActive: Boolean,
    onTabOverviewClick: () -> Unit,
    onActionsMenuClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onSwipeNextTab: () -> Unit,
    onSwipePrevTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var inputText by remember(currentTab?.url) {
        val displayUrl = if (isInternalHomeUrl(currentTab?.url)) "" else (currentTab?.url ?: "")
        mutableStateOf(displayUrl)
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
                // Focus requester safe handling
            }
        }
    }

    val displayHost = remember(currentTab?.url) {
        val url = currentTab?.url ?: ""
        if (isInternalHomeUrl(url)) {
            if (isPrivate) "Search or enter website name (Private)" else "Search or enter website name"
        } else {
            try {
                val uri = java.net.URI(url)
                val host = uri.host
                if (!host.isNullOrBlank()) host.removePrefix("www.") else url
            } catch (e: Exception) {
                url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            }
        }
    }

    val isHttps = currentTab?.url?.startsWith("https://") == true
    val isLoading = currentTab?.isLoading == true || (currentTab?.progress ?: 100) < 100
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // Interaction sources for Apple-style spring press feedback
    val leftButtonSource = remember { MutableInteractionSource() }
    val isLeftPressed by leftButtonSource.collectIsPressedAsState()
    val leftScale by animateFloatAsState(
        targetValue = if (isLeftPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "left_scale"
    )

    val rightButtonSource = remember { MutableInteractionSource() }
    val isRightPressed by rightButtonSource.collectIsPressedAsState()
    val rightScale by animateFloatAsState(
        targetValue = if (isRightPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "right_scale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Quick Search Suggestions Overlay when actively editing URL
        AnimatedVisibility(
            visible = isFocused,
            enter = fadeIn() + slideInVertically { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 68.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .shadow(24.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.8f)),
                color = Color(0xF5161C26),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isPrivate) "Private Browsing Search" else "Smart Search Suggestions",
                            color = if (isPrivate) GVONESecondary else GVONETextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Safari Pro",
                            color = GVONETextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    HorizontalDivider(color = Color(0x22FFFFFF), thickness = 0.5.dp)

                    // Live Search Autocomplete item
                    if (inputText.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    focusManager.clearFocus()
                                    onNavigate(inputText)
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = GVONEPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Search for \"$inputText\"",
                                    color = GVONETextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Google / DuckDuckGo Search",
                                    color = GVONETextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    // Direct Search Engines Bar
                    Text(
                        text = "SEARCH ENGINES",
                        color = GVONETextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SafariEnginePill(
                            name = "Google",
                            color = Color(0xFF4285F4),
                            onClick = {
                                val query = inputText.ifBlank { "apple" }
                                focusManager.clearFocus()
                                onNavigate("https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                            }
                        )
                        SafariEnginePill(
                            name = "DuckDuckGo",
                            color = Color(0xFFDE5833),
                            onClick = {
                                val query = inputText.ifBlank { "top news" }
                                focusManager.clearFocus()
                                onNavigate("https://duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}")
                            }
                        )
                        SafariEnginePill(
                            name = "Wikipedia",
                            color = Color(0xFF006699),
                            onClick = {
                                val query = inputText.ifBlank { "Safari web browser" }
                                focusManager.clearFocus()
                                onNavigate("https://en.wikipedia.org/wiki/Special:Search?search=${java.net.URLEncoder.encode(query, "UTF-8")}")
                            }
                        )
                    }

                    // Frequently Visited / Quick Shortcuts
                    Text(
                        text = "FREQUENTLY VISITED",
                        color = GVONETextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SafariQuickSiteChip(name = "Apple", url = "https://www.apple.com", onClick = { focusManager.clearFocus(); onNavigate("https://www.apple.com") })
                        SafariQuickSiteChip(name = "Quanta", url = "https://www.quantamagazine.org", onClick = { focusManager.clearFocus(); onNavigate("https://www.quantamagazine.org") })
                        SafariQuickSiteChip(name = "GitHub", url = "https://github.com", onClick = { focusManager.clearFocus(); onNavigate("https://github.com") })
                        SafariQuickSiteChip(name = "HackerNews", url = "https://news.ycombinator.com", onClick = { focusManager.clearFocus(); onNavigate("https://news.ycombinator.com") })
                    }
                }
            }
        }

        // =========================================================================
        // EXACT THREE-PART SAFARI BOTTOM TOOLBAR:
        // [ 1. Left Circular Button ] --- [ 2. Center Elongated Pill ] --- [ 3. Right Circular Button ]
        // =========================================================================
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // -------------------------------------------------------------
            // 1. LEFT CIRCULAR BUTTON: Safari Tabs Switcher (Overlapping Rectangles)
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(leftScale)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.6f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xDD222B3A),
                                Color(0xEE141A24)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x66FFFFFF),
                                Color(0x11FFFFFF)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = leftButtonSource,
                        indication = null
                    ) {
                        onTabOverviewClick()
                    }
                    .testTag("safari_tab_switcher_button"),
                contentAlignment = Alignment.Center
            ) {
                // Iconic Safari overlapping rectangles tab icon
                SafariTabsIcon(
                    color = if (isPrivate) GVONESecondary else Color(0xFFF0F3F8),
                    modifier = Modifier.size(22.dp)
                )
            }

            // -------------------------------------------------------------
            // 2. CENTER ELONGATED PILL: Search / URL Bar with Glassmorphic styling
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(26.dp),
                        spotColor = if (isPrivate) GVONESecondary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xDD1C2330),
                                Color(0xEE111620)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                if (isPrivate) GVONESecondary.copy(alpha = 0.8f) else if (isTorActive) GVONETertiary.copy(alpha = 0.8f) else Color(0x55FFFFFF),
                                Color(0x11FFFFFF)
                            )
                        ),
                        shape = RoundedCornerShape(26.dp)
                    )
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragOffset < -50) {
                                    onSwipeNextTab()
                                } else if (dragOffset > 50) {
                                    onSwipePrevTab()
                                }
                                dragOffset = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffset += dragAmount
                            }
                        )
                    }
                    .clickable {
                        isFocused = true
                    }
                    .testTag("safari_address_pill"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small website / search / lock icon on the left
                    Icon(
                        imageVector = if (isTorActive) Icons.Rounded.VpnKey else if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Search,
                        contentDescription = "Search / Security",
                        tint = if (isTorActive) GVONETertiary else if (isHttps) GVONETertiary else GVONETextSecondary,
                        modifier = Modifier.size(16.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // SF Pro style typography for URL / Search placeholder
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = if (isFocused) inputText else (if (isInternalHomeUrl(currentTab?.url)) "" else displayHost),
                            onValueChange = {
                                if (isFocused) {
                                    inputText = it
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { state ->
                                    if (state.isFocused != isFocused) {
                                        isFocused = state.isFocused
                                        if (state.isFocused) {
                                            inputText = if (isInternalHomeUrl(currentTab?.url)) "" else (currentTab?.url ?: "")
                                        }
                                    }
                                }
                                .testTag("address_bar_input"),
                            textStyle = TextStyle(
                                color = if (!isFocused && (currentTab?.url.isNullOrBlank() || isInternalHomeUrl(currentTab?.url))) Color(0xFF8E9BAE) else Color(0xFFE6EDF6),
                                fontSize = 14.sp,
                                fontWeight = if (!isFocused && (currentTab?.url.isNullOrBlank() || isInternalHomeUrl(currentTab?.url))) FontWeight.Normal else FontWeight.SemiBold
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(if (isPrivate) GVONESecondary else GVONEPrimary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Go,
                                keyboardType = KeyboardType.Uri
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    isFocused = false
                                    focusManager.clearFocus()
                                    onNavigate(inputText)
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (!isFocused && (currentTab?.url.isNullOrBlank() || isInternalHomeUrl(currentTab?.url))) {
                                    Text(
                                        text = displayHost,
                                        color = Color(0xFF8E9BAE),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                } else if (isFocused && inputText.isEmpty()) {
                                    Text(
                                        text = if (isPrivate) "Search or enter website name (Private)" else "Search or enter website name",
                                        color = Color(0xFF8E9BAE),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }

                    // Reload / Clear Icon inside the pill
                    if (isFocused && inputText.isNotEmpty()) {
                        IconButton(
                            onClick = { inputText = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cancel,
                                contentDescription = "Clear",
                                tint = GVONETextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else if (isLoading) {
                        IconButton(
                            onClick = onReload,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Stop",
                                tint = GVONETextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onReload,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reload",
                                tint = Color(0xFF8E9BAE),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Apple Safari slim progress bar line along the bottom rim of the pill
                if (isLoading) {
                    val progress = (currentTab?.progress ?: 30).coerceIn(10, 100) / 100f
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        if (isPrivate) GVONESecondary else GVONEPrimary,
                                        if (isPrivate) GVONEPrimary else GVONESecondary
                                    )
                                )
                            )
                    )
                }
            }

            // -------------------------------------------------------------
            // 3. RIGHT CIRCULAR BUTTON: Safari Three-Dot "More" Actions Menu
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(rightScale)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.6f)
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xDD222B3A),
                                Color(0xEE141A24)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x66FFFFFF),
                                Color(0x11FFFFFF)
                            )
                        ),
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = rightButtonSource,
                        indication = null
                    ) {
                        onActionsMenuClick()
                    }
                    .testTag("safari_more_actions_button"),
                contentAlignment = Alignment.Center
            ) {
                // Horizontally aligned three dots in light gray/white
                SafariThreeDotsIcon(
                    color = Color(0xFFF0F3F8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Custom Vector Canvas for Safari Tab Switcher (Overlapping Rounded Rectangles)
 */
@Composable
fun SafariTabsIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 1.6.dp.toPx()
        val cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
        val cardWidth = size.width * 0.62f
        val cardHeight = size.height * 0.62f

        // Back overlapping rectangle (offset top-right)
        drawRoundRect(
            color = color.copy(alpha = 0.75f),
            topLeft = Offset(size.width * 0.35f, size.height * 0.05f),
            size = Size(cardWidth, cardHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )

        // Front overlapping rectangle (offset bottom-left)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.05f, size.height * 0.33f),
            size = Size(cardWidth, cardHeight),
            cornerRadius = cornerRadius,
            style = Stroke(width = strokeWidth)
        )
    }
}

/**
 * Custom Horizontally Aligned Safari Three Dots Icon
 */
@Composable
fun SafariThreeDotsIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = 2.2.dp.toPx()
        val centerY = size.height / 2f
        val spacing = size.width / 3.2f

        // Center dot
        drawCircle(color = color, radius = radius, center = Offset(size.width / 2f, centerY))
        // Left dot
        drawCircle(color = color, radius = radius, center = Offset(size.width / 2f - spacing, centerY))
        // Right dot
        drawCircle(color = color, radius = radius, center = Offset(size.width / 2f + spacing, centerY))
    }
}

@Composable
fun SafariEnginePill(
    name: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF222B3B),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x33FFFFFF)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(name, color = Color(0xFFE2E8F0), fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun SafariQuickSiteChip(
    name: String,
    url: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E2635),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0x22FFFFFF)),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = name,
            color = Color(0xFFCCD6E5),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}
