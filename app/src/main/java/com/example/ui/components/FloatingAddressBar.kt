package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.data.model.ADDRESS_BAR_TARGET_URL
import com.example.data.model.BrowserSettings
import com.example.data.model.BrowserTab
import com.example.data.model.HOME_WEB_APP_URL
import com.example.data.model.isInternalHomeUrl
import com.example.data.sync.PageContextDetector
import com.example.ui.theme.*

/**
 * Premium Safari-Inspired Bottom Floating Browser Controls
 * Exactly 3 major UI areas:
 * 1. Left Circular Button: Safari-style overlapping tabs switcher with scale-down feedback.
 * 2. Center Elongated Pill: Glassmorphic address/search bar with SF Pro typography, Control/Target toggle, URL preview,
 *    live progress line, and quick target selector.
 * 3. Right Circular Button: Safari three-dot "More" action menu.
 */
@Composable
fun FloatingAddressBar(
    currentTab: BrowserTab?,
    tabCount: Int,
    isPrivate: Boolean,
    isTorActive: Boolean,
    settings: BrowserSettings? = null,
    onUpdateSettings: ((BrowserSettings) -> Unit)? = null,
    onTabOverviewClick: () -> Unit,
    onActionsMenuClick: () -> Unit,
    onNavigate: (String) -> Unit,
    onReload: () -> Unit,
    onSwipeNextTab: () -> Unit,
    onSwipePrevTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var showTargetControlDialog by remember { mutableStateOf(false) }
    val autoLoadEnabled = settings?.autoLoadTargetOnFocus ?: true
    val targetUrl = settings?.autoLoadTargetUrl ?: ADDRESS_BAR_TARGET_URL
    val bridgeEnabled = settings?.bidirectionalBridgeEnabled ?: true
    val bridgeApplyAll = settings?.bridgeApplyToAllWebsites ?: true

    val isGVONEActive = remember(currentTab?.url, targetUrl) {
        val url = currentTab?.url.orEmpty()
        PageContextDetector.isTrustedGVONEOrigin(url) || 
            (targetUrl.isNotBlank() && url.contains(targetUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')))
    }

    val isBridgeActiveForCurrentPage = remember(bridgeEnabled, bridgeApplyAll, isGVONEActive, currentTab?.url) {
        bridgeEnabled && (bridgeApplyAll || isGVONEActive)
    }

    var inputText by remember(currentTab?.url, isBridgeActiveForCurrentPage) {
        val displayUrl = if (isBridgeActiveForCurrentPage || isInternalHomeUrl(currentTab?.url)) "" else (currentTab?.url ?: "")
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

    val displayHost = remember(currentTab?.url, isBridgeActiveForCurrentPage) {
        val url = currentTab?.url ?: ""
        if (isBridgeActiveForCurrentPage) {
            "" // Hide URL completely when Bridge / Type-to-Write mode is active
        } else if (isInternalHomeUrl(url)) {
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
            .imePadding()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
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
                        if (autoLoadEnabled && targetUrl.isNotBlank()) {
                            val currentUrl = currentTab?.url.orEmpty()
                            val cleanTarget = targetUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                            if (!currentUrl.contains(cleanTarget)) {
                                onNavigate(targetUrl)
                            }
                        }
                        isFocused = true
                        inputText = ""
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {}
                    }
                    .testTag("safari_address_pill"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Control Toggle Button (Replaces Lock Icon)
                    // Tapping opens the Quick Target Selection & Auto-load Settings dialog
                    IconButton(
                        onClick = { showTargetControlDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("address_bar_control_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Control & Target Website Settings",
                            tint = if (autoLoadEnabled) GVONEPrimary else Color(0xFF8E9BAE),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // SF Pro style typography for URL / Search placeholder
                    Box(
                        modifier = Modifier
                            .weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = if (isFocused) inputText else (if (isInternalHomeUrl(currentTab?.url) || isBridgeActiveForCurrentPage) "" else displayHost),
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
                                            if (autoLoadEnabled && targetUrl.isNotBlank()) {
                                                val currentUrl = currentTab?.url.orEmpty()
                                                val cleanTarget = targetUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                                if (!currentUrl.contains(cleanTarget)) {
                                                    onNavigate(targetUrl)
                                                }
                                            }
                                            inputText = ""
                                        }
                                    }
                                }
                                .testTag("address_bar_input"),
                            textStyle = TextStyle(
                                color = if (!isFocused && (currentTab?.url.isNullOrBlank() || isInternalHomeUrl(currentTab?.url) || isBridgeActiveForCurrentPage)) Color(0xFF8E9BAE) else Color(0xFFE6EDF6),
                                fontSize = 14.sp,
                                fontWeight = if (!isFocused && (currentTab?.url.isNullOrBlank() || isInternalHomeUrl(currentTab?.url) || isBridgeActiveForCurrentPage)) FontWeight.Normal else FontWeight.SemiBold
                            ),
                            singleLine = true,
                            cursorBrush = SolidColor(if (isPrivate) GVONESecondary else GVONEPrimary),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Search,
                                keyboardType = if (isBridgeActiveForCurrentPage) KeyboardType.Text else KeyboardType.Uri
                            ),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    val query = inputText.trim()
                                    isFocused = false
                                    focusManager.clearFocus()
                                    if (query.isNotEmpty()) {
                                        onNavigate(query)
                                    }
                                },
                                onSend = {
                                    val query = inputText.trim()
                                    isFocused = false
                                    focusManager.clearFocus()
                                    if (query.isNotEmpty()) {
                                        onNavigate(query)
                                    }
                                },
                                onGo = {
                                    val query = inputText.trim()
                                    isFocused = false
                                    focusManager.clearFocus()
                                    if (query.isNotEmpty()) {
                                        onNavigate(query)
                                    }
                                },
                                onDone = {
                                    val query = inputText.trim()
                                    isFocused = false
                                    focusManager.clearFocus()
                                    if (query.isNotEmpty()) {
                                        onNavigate(query)
                                    }
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (!isFocused) {
                                    if (isBridgeActiveForCurrentPage) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (isPrivate) "Type to write (Private)..." else "Type to write / search...",
                                                color = Color(0xFF94A3B8),
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    } else if (currentTab?.url.isNullOrBlank() || isInternalHomeUrl(currentTab?.url)) {
                                        Text(
                                            text = displayHost,
                                            color = Color(0xFF8E9BAE),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                } else if (isFocused && inputText.isEmpty()) {
                                    Text(
                                        text = if (isBridgeActiveForCurrentPage) "Type to write, prompt, or enter URL..." else if (isPrivate) "Search or enter website name (Private)" else "Search or enter website name",
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

                    // Close / Return to Home button inside the central pill
                    IconButton(
                        onClick = {
                            isFocused = false
                            focusManager.clearFocus()
                            inputText = ""
                            onNavigate(HOME_WEB_APP_URL)
                        },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("address_bar_home_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Return to Home",
                            tint = Color(0xFF8E9BAE),
                            modifier = Modifier.size(17.dp)
                        )
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

    // Target Website & Address Bar Behavior Control Dialog
    if (showTargetControlDialog && settings != null && onUpdateSettings != null) {
        var tempUrl by remember(settings.autoLoadTargetUrl) { mutableStateOf(settings.autoLoadTargetUrl) }
        var tempAutoLoad by remember(settings.autoLoadTargetOnFocus) { mutableStateOf(settings.autoLoadTargetOnFocus) }
        var tempBridgeEnabled by remember(settings.bidirectionalBridgeEnabled) { mutableStateOf(settings.bidirectionalBridgeEnabled) }
        var tempBridgeApplyToAll by remember(settings.bridgeApplyToAllWebsites) { mutableStateOf(settings.bridgeApplyToAllWebsites) }

        val presetSites = listOf(
            Pair("GVONE CharAssist", "https://charassist-c4uzg7hb.manus.space"),
            Pair("RSS Group Feed", "https://rssgroupfeed-jaelvwfd.manus.space"),
            Pair("DuckDuckGo", "https://duckduckgo.com"),
            Pair("Google", "https://www.google.com"),
            Pair("Brave Search", "https://search.brave.com")
        )

        AlertDialog(
            onDismissRequest = { showTargetControlDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = GVONEPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Address Bar & Bridge Controls", color = GVONETextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Configure the bidirectional bridge architecture, input router, and address bar behaviors.",
                        color = GVONETextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    // 1. Bidirectional Bridge / InputRouter Master Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B2332),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (tempBridgeEnabled) GVONEPrimary.copy(alpha = 0.5f) else Color(0x33FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.SyncAlt,
                                        contentDescription = null,
                                        tint = if (tempBridgeEnabled) GVONEPrimary else Color(0xFF8E9BAE),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Bidirectional Bridge / InputRouter",
                                        color = GVONETextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "Directly route address queries to webpage input/chat without reloading",
                                    color = GVONETextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = tempBridgeEnabled,
                                onCheckedChange = { tempBridgeEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GVONEPrimary
                                )
                            )
                        }
                    }

                    // 2. Apply to All Websites (Universal Bridge) Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B2332),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (tempBridgeApplyToAll && tempBridgeEnabled) GVONEPrimary.copy(alpha = 0.5f) else Color(0x33FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = if (tempBridgeApplyToAll && tempBridgeEnabled) GVONEPrimary else Color(0xFF8E9BAE),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Apply Bridge to All Websites",
                                        color = if (tempBridgeEnabled) GVONETextPrimary else Color(0xFF6B7A90),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = if (tempBridgeApplyToAll) "Active for all websites & AI web apps" else "Active only for trusted GVONE web apps",
                                    color = GVONETextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = tempBridgeApplyToAll,
                                enabled = tempBridgeEnabled,
                                onCheckedChange = { tempBridgeApplyToAll = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GVONEPrimary
                                )
                            )
                        }
                    }

                    // 3. Auto-load on Tap Toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1B2332),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "Auto-load Target on Tap",
                                    color = GVONETextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Automatically load target website when focusing address bar",
                                    color = GVONETextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = tempAutoLoad,
                                onCheckedChange = { tempAutoLoad = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = GVONEPrimary
                                )
                            )
                        }
                    }

                    // 4. Target Website URL Input
                    Column {
                        Text(
                            text = "TARGET WEBSITE URL",
                            color = GVONEPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        OutlinedTextField(
                            value = tempUrl,
                            onValueChange = { tempUrl = it },
                            placeholder = { Text("https://...", color = Color(0xFF6B7A90)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF161D2A),
                                unfocusedContainerColor = Color(0xFF161D2A),
                                focusedBorderColor = GVONEPrimary,
                                unfocusedBorderColor = Color(0xFF2C394E),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("target_url_input_field")
                        )
                    }

                    // 5. Preset Website Chips
                    Column {
                        Text(
                            text = "QUICK PRESETS",
                            color = GVONETextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetSites.take(3).forEach { (name, url) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (tempUrl == url) GVONEPrimary.copy(alpha = 0.25f) else Color(0xFF1C2433),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (tempUrl == url) GVONEPrimary else Color(0x22FFFFFF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { tempUrl = url }
                                ) {
                                    Text(
                                        text = name,
                                        color = if (tempUrl == url) GVONEPrimary else Color(0xFFCCD6E5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalUrl = if (tempUrl.isNotBlank() && !tempUrl.startsWith("http://") && !tempUrl.startsWith("https://")) {
                            "https://$tempUrl"
                        } else {
                            tempUrl
                        }
                        onUpdateSettings(
                            settings.copy(
                                autoLoadTargetOnFocus = tempAutoLoad,
                                autoLoadTargetUrl = finalUrl,
                                bidirectionalBridgeEnabled = tempBridgeEnabled,
                                bridgeApplyToAllWebsites = tempBridgeApplyToAll
                            )
                        )
                        showTargetControlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Save & Apply", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTargetControlDialog = false }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            },
            containerColor = Color(0xFF141923)
        )
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
