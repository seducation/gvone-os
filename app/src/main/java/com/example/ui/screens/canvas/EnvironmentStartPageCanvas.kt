package com.example.ui.screens.canvas

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.components.suggestions.SuggestionsPanel

@Composable
fun EnvironmentStartPageCanvas(
    environment: Environment,
    allEnvironments: List<Environment>,
    isPrivate: Boolean,
    isTorActive: Boolean,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onSelectEnvironment: (String) -> Unit,
    onCreateEnvironment: (name: String, icon: String, theme: String, preset: String, initialLinkUrl: String?, initialLinkTitle: String?) -> Unit,
    onDuplicateEnvironment: (String) -> Unit,
    onDeleteEnvironment: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onAddObject: (CanvasObject) -> Unit,
    onUpdateObject: (CanvasObject) -> Unit,
    onDeleteObject: (String) -> Unit,
    onReorderObjects: (List<CanvasObject>) -> Unit,
    onUpdateBackground: (EnvironmentBackground) -> Unit,
    onUpdateLayoutMode: (EnvironmentLayoutMode) -> Unit,
    showSuggestions: Boolean = false,
    suggestions: List<Suggestion> = emptyList(),
    onExecuteSuggestion: (Suggestion) -> Unit = {},
    onCloseSuggestions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showEnvSwitchSheet by remember { mutableStateOf(false) }
    var showAddLinkDialog by remember { mutableStateOf(false) }
    var showAddWidgetDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showBackgroundSheet by remember { mutableStateOf(false) }

    // Parse environment theme colors
    val primaryColor = remember(environment.background.primaryColorHex) {
        parseColorSafe(environment.background.primaryColorHex, Color(0xFF0A0F1D))
    }
    val secondaryColor = remember(environment.background.secondaryColorHex) {
        parseColorSafe(environment.background.secondaryColorHex, Color(0xFF1E1B4B))
    }
    val accentColor = remember(environment.background.accentColorHex) {
        parseColorSafe(environment.background.accentColorHex, Color(0xFF38BDF8))
    }

    // Auto-scroll to top when suggestions panel is opened
    LaunchedEffect(showSuggestions) {
        if (showSuggestions) {
            scrollState.animateScrollTo(0)
        }
    }

    // Directly open starting link if environment startPageUrl is set
    LaunchedEffect(environment.id, environment.startPageUrl, isEditMode) {
        if (!isEditMode && !environment.startPageUrl.isNullOrBlank()) {
            onNavigate(environment.startPageUrl)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("environment_start_page_canvas")
    ) {
        // -------------------------------------------------------------
        // BACKGROUND ATMOSPHERE LAYER
        // -------------------------------------------------------------
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (environment.background.blurRadius > 0f) {
                        Modifier.blur(environment.background.blurRadius.dp)
                    } else Modifier
                )
                .background(
                    when (environment.background.type) {
                        BackgroundType.SOLID -> Brush.linearGradient(listOf(primaryColor, primaryColor))
                        BackgroundType.GRADIENT, BackgroundType.WALLPAPER -> Brush.verticalGradient(
                            listOf(
                                primaryColor,
                                secondaryColor,
                                primaryColor.copy(alpha = 0.95f)
                            )
                        )
                    }
                )
        ) {
            // Subtle ambient radial glow in the top-right / center
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 60.dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                accentColor.copy(alpha = 0.12f * environment.background.opacity),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // -------------------------------------------------------------
        // MAIN SCROLLABLE CANVAS CONTENT
        // -------------------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // TOP ENVIRONMENT HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Environment Selector Pill (Safari Profiles inspired)
                Surface(
                    onClick = { showEnvSwitchSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF131826).copy(alpha = 0.8f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        accentColor.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("env_selector_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = getIconForName(environment.iconName),
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = environment.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "Switch Environment",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Action Badges & Customize Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isPrivate) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.2f))
                                .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Vault", color = Color(0xFFC084FC), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Customize / Done Button
                    Surface(
                        onClick = onToggleEditMode,
                        shape = RoundedCornerShape(18.dp),
                        color = if (isEditMode) Color(0xFF38BDF8) else Color(0xFF1E293B).copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isEditMode) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("customize_canvas_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Rounded.Check else Icons.Rounded.Edit,
                                contentDescription = if (isEditMode) "Done" else "Customize",
                                tint = if (isEditMode) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isEditMode) "Done" else "Customize",
                                color = if (isEditMode) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // PROACTIVE SUGGESTIONS PANEL (Rendered near top above home content)
            // -------------------------------------------------------------
            AnimatedVisibility(
                visible = showSuggestions,
                enter = fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.95f, animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)) +
                        slideInVertically(initialOffsetY = { -it / 4 }, animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                       scaleOut(targetScale = 0.95f, animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                       slideOutVertically(targetOffsetY = { -it / 4 }, animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SuggestionsPanel(
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion ->
                            onExecuteSuggestion(suggestion)
                        },
                        onClose = onCloseSuggestions,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                    )
                }
            }

            if (!environment.startPageUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    onClick = { onNavigate(environment.startPageUrl) },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF131826).copy(alpha = 0.85f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("env_start_page_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RocketLaunch,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Start Page Website",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = environment.startPageUrl,
                                color = accentColor,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Open",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // -------------------------------------------------------------
            // CANVAS OBJECTS RENDERER
            // -------------------------------------------------------------
            if (environment.objects.isEmpty()) {
                // Requirement 10: "An Environment must be allowed to contain absolutely nothing except a background"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isEditMode) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Empty Environment Canvas",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Use the customization dock below to add links, widgets, folders, or notes.",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
            } else {
                // Grid-based layout flow
                // We chunk objects into rows according to their grid width (up to 4 grid columns per row)
                val chunkedRows = remember(environment.objects) {
                    val rows = mutableListOf<List<CanvasObject>>()
                    var currentRow = mutableListOf<CanvasObject>()
                    var currentWidth = 0f

                    for (obj in environment.objects) {
                        val objW = obj.width.coerceIn(1f, 4f)
                        if (currentWidth + objW > 4.1f && currentRow.isNotEmpty()) {
                            rows.add(currentRow)
                            currentRow = mutableListOf(obj)
                            currentWidth = objW
                        } else {
                            currentRow.add(obj)
                            currentWidth += objW
                        }
                    }
                    if (currentRow.isNotEmpty()) {
                        rows.add(currentRow)
                    }
                    rows
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    chunkedRows.forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { obj ->
                                val itemIndex = environment.objects.indexOf(obj)
                                val baseHeight = when (obj) {
                                    is CanvasObject.LinkObject -> 82.dp
                                    is CanvasObject.FolderObject -> 106.dp
                                    is CanvasObject.NoteObject -> 126.dp
                                    is CanvasObject.WidgetObject -> when (obj.widgetType) {
                                        CanvasWidgetType.SEARCH -> 56.dp
                                        CanvasWidgetType.CLOCK, CanvasWidgetType.WEATHER, CanvasWidgetType.SYSTEM_INFO -> 106.dp
                                        CanvasWidgetType.DATE_CALENDAR, CanvasWidgetType.RSS_FEED -> 136.dp
                                        CanvasWidgetType.QUICK_TOOLS -> 106.dp
                                        CanvasWidgetType.NOTES_WIDGET -> 126.dp
                                    }
                                    is CanvasObject.WebPortionWidgetObject -> (110 * obj.height).coerceIn(120f, 280f).dp
                                }

                                CanvasObjectCard(
                                    obj = obj,
                                    isEditMode = isEditMode,
                                    isTorActive = isTorActive,
                                    onNavigate = onNavigate,
                                    onDelete = onDeleteObject,
                                    onUpdate = onUpdateObject,
                                    onMoveUp = {
                                        if (itemIndex > 0) {
                                            val reordered = environment.objects.toMutableList()
                                            val item = reordered.removeAt(itemIndex)
                                            reordered.add(itemIndex - 1, item)
                                            onReorderObjects(reordered)
                                        }
                                    },
                                    onMoveDown = {
                                        if (itemIndex < environment.objects.size - 1) {
                                            val reordered = environment.objects.toMutableList()
                                            val item = reordered.removeAt(itemIndex)
                                            reordered.add(itemIndex + 1, item)
                                            onReorderObjects(reordered)
                                        }
                                    },
                                    onConfigure = {
                                        // Configure or edit object
                                        if (it is CanvasObject.LinkObject) {
                                            showAddLinkDialog = true
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(obj.width)
                                        .height(baseHeight)
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacing to ensure floating address bar / dock doesn't obscure contents
            Spacer(modifier = Modifier.height(if (isEditMode) 130.dp else 90.dp))
        }

        // -------------------------------------------------------------
        // BOTTOM CUSTOMIZATION DOCK (Active in Edit Mode)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = isEditMode,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            CanvasCustomizationDock(
                layoutMode = environment.layoutMode,
                onAddLink = { showAddLinkDialog = true },
                onAddWidget = { showAddWidgetDialog = true },
                onAddFolder = { showAddFolderDialog = true },
                onAddNote = { showAddNoteDialog = true },
                onChangeBackground = { showBackgroundSheet = true },
                onToggleLayoutMode = {
                    val nextMode = when (environment.layoutMode) {
                        EnvironmentLayoutMode.GRID -> EnvironmentLayoutMode.AUTO
                        EnvironmentLayoutMode.AUTO -> EnvironmentLayoutMode.FREEFORM
                        EnvironmentLayoutMode.FREEFORM -> EnvironmentLayoutMode.GRID
                    }
                    onUpdateLayoutMode(nextMode)
                },
                onDone = onToggleEditMode
            )
        }
    }

    // -------------------------------------------------------------
    // MODAL DIALOGS & SHEETS
    // -------------------------------------------------------------
    if (showEnvSwitchSheet) {
        EnvironmentSwitchSheet(
            environments = allEnvironments,
            activeEnvironmentId = environment.id,
            onSelectEnvironment = onSelectEnvironment,
            onCreateEnvironment = onCreateEnvironment,
            onDuplicateEnvironment = onDuplicateEnvironment,
            onDeleteEnvironment = onDeleteEnvironment,
            onDismiss = { showEnvSwitchSheet = false }
        )
    }

    if (showAddLinkDialog) {
        AddLinkDialog(
            onDismiss = { showAddLinkDialog = false },
            onConfirm = {
                onAddObject(it)
                showAddLinkDialog = false
            }
        )
    }

    if (showAddWidgetDialog) {
        AddWidgetDialog(
            onDismiss = { showAddWidgetDialog = false },
            onConfirm = {
                onAddObject(it)
                showAddWidgetDialog = false
            }
        )
    }

    if (showAddFolderDialog) {
        AddFolderDialog(
            onDismiss = { showAddFolderDialog = false },
            onConfirm = {
                onAddObject(it)
                showAddFolderDialog = false
            }
        )
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            onDismiss = { showAddNoteDialog = false },
            onConfirm = {
                onAddObject(it)
                showAddNoteDialog = false
            }
        )
    }

    if (showBackgroundSheet) {
        BackgroundCustomizerSheet(
            currentBackground = environment.background,
            onUpdateBackground = onUpdateBackground,
            onDismiss = { showBackgroundSheet = false }
        )
    }
}
