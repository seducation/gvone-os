package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay
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
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.command.CommandEngine
import com.example.data.model.*
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
@OptIn(ExperimentalFoundationApi::class)
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
    isCompact: Boolean = false,
    onExpand: () -> Unit = {},
    onContract: () -> Unit = {},
    onToggleCompact: () -> Unit = {},
    customCommands: List<CustomCommandEntity> = emptyList(),
    onOpenCommandManager: () -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    isTerminalOpen: Boolean = false,
    onCloseTerminal: () -> Unit = {},
    onOpenConnector: ((com.example.data.connector.WebsiteAccessContext) -> Unit)? = null,
    onOpenPhotos: (() -> Unit)? = null,
    onOpenCamera: (() -> Unit)? = null,
    onOpenAvatar: (() -> Unit)? = null,
    currentEnvironmentId: String = "default",
    currentEnvironmentName: String = "Default",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var isFocused by remember { mutableStateOf(false) }
    var showTargetControlDialog by remember { mutableStateOf(false) }
    var showControlActionSheet by remember { mutableStateOf(false) }
    val autoLoadEnabled = settings?.autoLoadTargetOnFocus ?: true
    val targetUrl = settings?.autoLoadTargetUrl ?: ADDRESS_BAR_TARGET_URL
    val bridgeEnabled = settings?.bidirectionalBridgeEnabled ?: true

    val bridgeApplyToAll = settings?.bridgeApplyToAllWebsites ?: false

    val isGVONEActive = remember(currentTab?.url, targetUrl) {
        val url = currentTab?.url.orEmpty()
        PageContextDetector.isTrustedGVONEOrigin(url) || 
            (targetUrl.isNotBlank() && url.contains(targetUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')))
    }

    val isYouTubeActive = remember(currentTab?.url) {
        PageContextDetector.isYouTubeOrigin(currentTab?.url)
    }

    val isYouTubeHome = remember(currentTab?.url) {
        PageContextDetector.isYouTubeHomepage(currentTab?.url)
    }

    // Direct Bridge / Type-to-write input mode is active on trusted GVONE origins or when "Apply Bridge to All Websites" is toggled ON
    val isBridgeActiveForCurrentPage = remember(bridgeEnabled, bridgeApplyToAll, isGVONEActive) {
        bridgeEnabled && (isGVONEActive || bridgeApplyToAll)
    }

    val handleTerminalAddressBarTrigger: () -> Unit = {
        if (settings?.terminalAutoAppearOnAddressBar == true) {
            onOpenTerminal()
        } else if (isTerminalOpen) {
            onCloseTerminal()
        }
    }

    var inputText by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val commandSuggestions = remember(inputText, customCommands, isFocused) {
        if (isFocused && (inputText.startsWith("/") || (inputText.isNotBlank() && customCommands.any { it.matchesTrigger(inputText) }))) {
            CommandEngine.getSuggestions(inputText, customCommands)
        } else {
            emptyList()
        }
    }

    // Synchronize non-focused address bar input with active tab URL without wiping active user typing
    LaunchedEffect(currentTab?.url, isFocused, isBridgeActiveForCurrentPage) {
        if (!isFocused) {
            val url = currentTab?.url.orEmpty()
            inputText = if (isBridgeActiveForCurrentPage || isInternalHomeUrl(url)) "" else url
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            delay(50)
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
            "" // When bridge is active (including Apply to All), hide the website url to show type-to-write placeholder
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

    val effectivelyCompact = isCompact && !isFocused

    val sideButtonsAlpha by animateFloatAsState(
        targetValue = if (effectivelyCompact) 0f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "side_buttons_alpha"
    )

    val sideButtonsScale by animateFloatAsState(
        targetValue = if (effectivelyCompact) 0.5f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "side_buttons_scale"
    )

    val sideButtonWidth by animateDpAsState(
        targetValue = if (effectivelyCompact) 0.dp else 52.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "side_button_width"
    )

    val rowSpacing by animateDpAsState(
        targetValue = if (effectivelyCompact) 0.dp else 10.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "row_spacing"
    )

    val pillHeight by animateDpAsState(
        targetValue = if (effectivelyCompact) 34.dp else 52.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "pill_height"
    )

    val pillCornerRadius by animateDpAsState(
        targetValue = if (effectivelyCompact) 17.dp else 26.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "pill_corner_radius"
    )

    val verticalBarPadding by animateDpAsState(
        targetValue = if (effectivelyCompact) 8.dp else 12.dp,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "vertical_bar_padding"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isBottom = settings?.addressBarBottom ?: true

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isBottom) Modifier.navigationBarsPadding()
                else Modifier.statusBarsPadding()
            )
            .imePadding()
            .padding(
                horizontal = if (isLandscape) 24.dp else 14.dp,
                vertical = if (isLandscape) 6.dp else verticalBarPadding
            ),
        contentAlignment = if (isBottom) Alignment.BottomCenter else Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isBottom) {
                AnimatedVisibility(
                    visible = isFocused && commandSuggestions.isNotEmpty(),
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    CommandAutocompletePopup(
                        suggestions = commandSuggestions,
                        onSelectSuggestion = { suggestion, executeNow ->
                            if (executeNow) {
                                val arg = suggestion.queryArgument
                                val runStr = if (arg.isNotEmpty()) "${suggestion.matchedTrigger} $arg" else suggestion.matchedTrigger
                                onNavigate(runStr)
                                isFocused = false
                                focusManager.clearFocus()
                            } else {
                                inputText = "${suggestion.matchedTrigger} "
                            }
                        },
                        onOpenCommandManager = onOpenCommandManager,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
            val maxAvailableWidth = maxWidth
            val fullPillWidth = (maxAvailableWidth - 124.dp).coerceAtLeast(140.dp)
            val compactPillWidth = 138.dp.coerceAtMost(maxAvailableWidth - 32.dp)
            val targetPillWidth = if (effectivelyCompact) compactPillWidth else fullPillWidth

            val animatedPillWidth by animateDpAsState(
                targetValue = targetPillWidth,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                label = "pill_width"
            )

            Row(
                modifier = Modifier
                    .wrapContentSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (effectivelyCompact) {
                                onExpand()
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (effectivelyCompact) {
                                onExpand()
                            } else {
                                onContract()
                            }
                        }
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(rowSpacing)
            ) {
                // 1. LEFT CIRCULAR BUTTON: Safari Tabs Switcher (Overlapping Rectangles)
                Box(
                    modifier = Modifier
                        .width(sideButtonWidth)
                        .height(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (sideButtonWidth > 4.dp) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .scale(leftScale * sideButtonsScale)
                                .alpha(sideButtonsAlpha)
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
                                .combinedClickable(
                                    interactionSource = leftButtonSource,
                                    indication = null,
                                    enabled = !effectivelyCompact,
                                    onClick = {
                                        onTabOverviewClick()
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (effectivelyCompact) {
                                            onExpand()
                                        } else {
                                            onContract()
                                        }
                                    }
                                )
                                .testTag("safari_tab_switcher_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            SafariTabsIcon(
                                color = if (isPrivate) GVONESecondary else Color(0xFFF0F3F8),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // 2. CENTER PILL: Search / URL Bar with smooth Compact Pill Transformation
                Box(
                    modifier = Modifier
                        .width(animatedPillWidth)
                        .height(pillHeight)
                        .shadow(
                            elevation = if (effectivelyCompact) 8.dp else 16.dp,
                            shape = RoundedCornerShape(pillCornerRadius),
                            spotColor = if (isPrivate) GVONESecondary.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.6f)
                        )
                        .clip(RoundedCornerShape(pillCornerRadius))
                        .background(
                            Brush.verticalGradient(
                                colors = if (effectivelyCompact) listOf(
                                    Color(0xF01A212D),
                                    Color(0xF80F141E)
                                ) else listOf(
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
                            shape = RoundedCornerShape(pillCornerRadius)
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
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (effectivelyCompact) {
                                    onExpand()
                                    handleTerminalAddressBarTrigger()
                                    return@combinedClickable
                                }
                                val currentUrl = currentTab?.url.orEmpty()
                                if (autoLoadEnabled && targetUrl.isNotBlank()) {
                                    val cleanTarget = targetUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                    val isAlreadyOnTarget = currentUrl.contains(cleanTarget)
                                    if (!isAlreadyOnTarget) {
                                        onNavigate(targetUrl)
                                    }
                                }
                                isFocused = true
                                if (isBridgeActiveForCurrentPage || isInternalHomeUrl(currentUrl)) {
                                    inputText = ""
                                } else {
                                    inputText = currentUrl
                                }
                                try {
                                    focusRequester.requestFocus()
                                } catch (_: Exception) {}

                                handleTerminalAddressBarTrigger()
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (effectivelyCompact) {
                                    onExpand()
                                } else {
                                    isFocused = false
                                    focusManager.clearFocus()
                                    onContract()
                                }
                            }
                        )
                        .testTag("safari_address_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = effectivelyCompact,
                        animationSpec = tween(durationMillis = 200),
                        label = "pill_content_crossfade"
                    ) { compact ->
                        if (compact) {
                            // Compact Minimal Capsule View matching Image 2
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isPrivate) Icons.Rounded.VpnLock
                                        else if (isTorActive) Icons.Rounded.Security
                                        else if (currentTab?.url.isNullOrBlank() || isInternalHomeUrl(currentTab?.url)) Icons.Rounded.Search
                                        else Icons.Rounded.Lock,
                                    contentDescription = "Security Status",
                                    tint = if (isPrivate) GVONESecondary
                                        else if (isTorActive) GVONETertiary
                                        else Color(0xFF8E9BAE),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (displayHost.isNotBlank()) displayHost
                                        else (currentTab?.title?.takeIf { it.isNotBlank() } ?: if (isPrivate) "Private Tab" else "Search or URL"),
                                    color = Color(0xFFE6EDF6),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            // Full Normal Address Bar View matching Image 1
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showControlActionSheet = true },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("address_bar_control_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = "Control & Target Website Settings",
                                        tint = if (isBridgeActiveForCurrentPage) GVONEPrimary else if (autoLoadEnabled) GVONESecondary else Color(0xFF8E9BAE),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = if (isFocused) inputText else (if (isInternalHomeUrl(currentTab?.url) || isBridgeActiveForCurrentPage) "" else displayHost),
                                        onValueChange = { newText ->
                                            inputText = newText
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                            .onFocusChanged { state ->
                                                if (state.isFocused != isFocused) {
                                                    isFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        onExpand()
                                                        val currentUrl = currentTab?.url.orEmpty()
                                                        if (autoLoadEnabled && targetUrl.isNotBlank()) {
                                                            val cleanTarget = targetUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                                            val isAlreadyOnTarget = currentUrl.contains(cleanTarget)
                                                            if (!isAlreadyOnTarget) {
                                                                onNavigate(targetUrl)
                                                            }
                                                        }
                                                        if (isBridgeActiveForCurrentPage || isInternalHomeUrl(currentUrl)) {
                                                            inputText = ""
                                                        } else {
                                                            inputText = currentUrl
                                                        }
                                                        handleTerminalAddressBarTrigger()
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
                                            keyboardType = if (isBridgeActiveForCurrentPage || isYouTubeActive) KeyboardType.Text else KeyboardType.Uri
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
                                                    text = if (isYouTubeActive) "Search YouTube" else if (isBridgeActiveForCurrentPage) "Type to write, prompt, or enter URL..." else if (isPrivate) "Search or enter website name (Private)" else "Search or enter website name",
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

                                    // Touch interceptor overlay when not focused to ensure long press contracts the pill and tap expands/focuses
                                    if (!isFocused) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .combinedClickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {
                                                        onExpand()
                                                        isFocused = true
                                                        val currentUrl = currentTab?.url.orEmpty()
                                                        if (autoLoadEnabled && targetUrl.isNotBlank()) {
                                                            val cleanTarget = targetUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
                                                            val isAlreadyOnTarget = currentUrl.contains(cleanTarget)
                                                            if (!isAlreadyOnTarget) {
                                                                onNavigate(targetUrl)
                                                            }
                                                        }
                                                        if (isBridgeActiveForCurrentPage || isInternalHomeUrl(currentUrl)) {
                                                            inputText = ""
                                                        } else {
                                                            inputText = currentUrl
                                                        }
                                                        try {
                                                            focusRequester.requestFocus()
                                                        } catch (_: Exception) {}

                                                        handleTerminalAddressBarTrigger()
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (effectivelyCompact) {
                                                            onExpand()
                                                        } else {
                                                            isFocused = false
                                                            focusManager.clearFocus()
                                                            onContract()
                                                        }
                                                    }
                                                )
                                        )
                                    }
                                }

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
                                .clip(RoundedCornerShape(bottomStart = pillCornerRadius, bottomEnd = pillCornerRadius))
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

                // 3. RIGHT CIRCULAR BUTTON: Safari Three-Dot "More" Actions Menu
                Box(
                    modifier = Modifier
                        .width(sideButtonWidth)
                        .height(52.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (sideButtonWidth > 4.dp) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .scale(rightScale * sideButtonsScale)
                                .alpha(sideButtonsAlpha)
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
                                .combinedClickable(
                                    interactionSource = rightButtonSource,
                                    indication = null,
                                    enabled = !effectivelyCompact,
                                    onClick = {
                                        onActionsMenuClick()
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (effectivelyCompact) {
                                            onExpand()
                                        } else {
                                            onContract()
                                        }
                                    }
                                )
                                .testTag("safari_more_actions_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            SafariThreeDotsIcon(
                                color = Color(0xFFF0F3F8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        if (!isBottom) {
            AnimatedVisibility(
                visible = isFocused && commandSuggestions.isNotEmpty(),
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                CommandAutocompletePopup(
                    suggestions = commandSuggestions,
                    onSelectSuggestion = { suggestion, executeNow ->
                        if (executeNow) {
                            val arg = suggestion.queryArgument
                            val runStr = if (arg.isNotEmpty()) "${suggestion.matchedTrigger} $arg" else suggestion.matchedTrigger
                            onNavigate(runStr)
                            isFocused = false
                            focusManager.clearFocus()
                        } else {
                            inputText = "${suggestion.matchedTrigger} "
                        }
                    },
                    onOpenCommandManager = onOpenCommandManager,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
        }
    }
}

    // Address Bar Control Action Menu (Dark-mode Apple-inspired horizontal action sheet)
    if (showControlActionSheet) {
        val currentUrl = currentTab?.url.orEmpty()
        val currentDomain = com.example.data.connector.WebsiteAccessConnectorService.extractDomain(currentUrl)
        val accessContext = com.example.data.connector.WebsiteAccessContext(
            currentTabId = currentTab?.id ?: "default",
            currentUrl = currentUrl,
            currentDomain = currentDomain,
            currentEnvironmentId = currentEnvironmentId,
            currentEnvironmentName = currentEnvironmentName
        )

        val controlActions = ControlActionRegistry.buildDefaultActions(
            onPhotos = {
                showControlActionSheet = false
                onOpenPhotos?.invoke()
            },
            onCamera = {
                showControlActionSheet = false
                onOpenCamera?.invoke()
            },
            onAvatar = {
                showControlActionSheet = false
                onOpenAvatar?.invoke()
            },
            onConnector = {
                showControlActionSheet = false
                onOpenConnector?.invoke(accessContext)
            },
            onTerminal = {
                showControlActionSheet = false
                onOpenTerminal()
            },
            onBridge = {
                // Handled inside ControlActionSheet by scrolling to Bridge section
            }
        )

        ControlActionSheet(
            actions = controlActions,
            websiteContext = accessContext,
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onOpenTerminal = {
                showControlActionSheet = false
                onOpenTerminal()
            },
            onOpenCommandManager = {
                showControlActionSheet = false
                onOpenCommandManager()
            },
            onDismiss = { showControlActionSheet = false }
        )
    }

    // Redirect legacy target control dialog to the unified Control & Terminal Center
    if (showTargetControlDialog) {
        showControlActionSheet = true
        showTargetControlDialog = false
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
