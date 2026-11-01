package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.QuickMenuProject
import com.example.data.model.QuickMenuScheduleItem
import com.example.data.model.QuickMenuStorage
import com.example.ui.theme.GVONEBorder
import com.example.ui.theme.GVONESurfaceDark
import com.example.ui.theme.GVONETextPrimary
import com.example.ui.theme.GVONETextSecondary
import java.util.UUID

enum class QuickMenuAccordion {
    PROJECTS,
    SCHEDULE
}

/**
 * Quick Menu vertical list card placed between the pinned apps row
 * and the horizontal tab bar in Navigator Panel.
 *
 * Requirements:
 * 1. Dark theme, 16dp rounded corner card, subtle border.
 * 2. 48px rows for "Projects" and "Schedule", with left icon, title, dynamic subtitle, right chevron.
 * 3. Accordion expansion (smooth animation, one row open at a time).
 * 4. Local persistence via QuickMenuStorage (SharedPreferences localStorage equivalent).
 * 5. Collapsible header "Quick Menu" to hide the card when needed to save space.
 * 6. Touch targets >= 44dp, responsive on 360px mobile screen.
 */
@Composable
fun QuickMenuCard(
    modifier: Modifier = Modifier,
    onOpenProject: ((QuickMenuProject) -> Unit)? = null,
    onRunProject: ((QuickMenuProject) -> Unit)? = null,
    onSendToDrive: ((QuickMenuProject) -> Unit)? = null
) {
    val context = LocalContext.current

    // LocalStorage persisted data
    var projects by remember { mutableStateOf(QuickMenuStorage.getProjects(context)) }
    var scheduleItems by remember { mutableStateOf(QuickMenuStorage.getScheduleItems(context)) }
    var isMenuCollapsed by remember { mutableStateOf(QuickMenuStorage.isMenuCollapsed(context)) }

    // Active accordion: PROJECTS, SCHEDULE, or null (only one open at a time)
    var activeAccordion by remember { mutableStateOf<QuickMenuAccordion?>(null) }

    // Dialog states for CRUD
    var showNewProjectDialog by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<QuickMenuProject?>(null) }

    var showNewScheduleDialog by remember { mutableStateOf(false) }
    var scheduleItemToEdit by remember { mutableStateOf<QuickMenuScheduleItem?>(null) }

    // Dynamic subtitles
    val projectsSubtitle = "${projects.size} projects"
    val scheduleSubtitle = remember(scheduleItems) {
        QuickMenuStorage.getNextUpcomingLabel(scheduleItems)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_menu_card")
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF161C26),
        border = BorderStroke(1.dp, Color(0xFF243042)),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 10.dp)
        ) {
            // =========================================================================
            // 1. COLLAPSIBLE HEADER: "Quick Menu"
            // =========================================================================
            val headerChevronRotation by animateFloatAsState(
                targetValue = if (isMenuCollapsed) 0f else 180f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "header_chevron"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val next = !isMenuCollapsed
                        isMenuCollapsed = next
                        QuickMenuStorage.setMenuCollapsed(context, next)
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .testTag("quick_menu_header_toggle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF222F44)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Widgets,
                            contentDescription = "Quick Menu",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Text(
                        text = "Quick Menu",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )

                    // Compact preview chips when menu is collapsed
                    if (isMenuCollapsed) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${projects.size} Proj • $scheduleSubtitle",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isMenuCollapsed) "Expand" else "Hide",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isMenuCollapsed) "Expand Quick Menu" else "Hide Quick Menu",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(headerChevronRotation)
                    )
                }
            }

            // =========================================================================
            // 2. ACCORDION ROWS (Visible when not collapsed)
            // =========================================================================
            AnimatedVisibility(
                visible = !isMenuCollapsed,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    HorizontalDivider(
                        color = Color(0xFF1F2937),
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // -------------------------------------------------------------
                    // ROW A: PROJECTS
                    // -------------------------------------------------------------
                    QuickMenuRowItem(
                        icon = Icons.Rounded.FolderSpecial,
                        iconTint = Color(0xFF38BDF8),
                        iconBg = Color(0x2238BDF8),
                        title = "Projects",
                        subtitle = projectsSubtitle,
                        isExpanded = activeAccordion == QuickMenuAccordion.PROJECTS,
                        testTag = "quick_menu_row_projects",
                        onClick = {
                            activeAccordion = if (activeAccordion == QuickMenuAccordion.PROJECTS) {
                                null
                            } else {
                                QuickMenuAccordion.PROJECTS
                            }
                        }
                    )

                    // Projects Accordion Inline Content
                    AnimatedVisibility(
                        visible = activeAccordion == QuickMenuAccordion.PROJECTS,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                    ) {
                        ProjectsInlineView(
                            projects = projects,
                            onOpenProject = { proj ->
                                onOpenProject?.invoke(proj)
                                Toast.makeText(context, "Opened: ${proj.name}", Toast.LENGTH_SHORT).show()
                            },
                            onRunProject = onRunProject,
                            onSendToDrive = onSendToDrive,
                            onNewProjectClick = { showNewProjectDialog = true },
                            onEditProject = { projectToEdit = it },
                            onDeleteProject = { toDelete ->
                                val updated = projects.filter { it.id != toDelete.id }
                                projects = updated
                                QuickMenuStorage.saveProjects(context, updated)
                                Toast.makeText(context, "Project deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }

                    HorizontalDivider(
                        color = Color(0xFF1F2937),
                        thickness = 0.8.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // -------------------------------------------------------------
                    // ROW B: SCHEDULE
                    // -------------------------------------------------------------
                    QuickMenuRowItem(
                        icon = Icons.Rounded.Schedule,
                        iconTint = Color(0xFFF59E0B),
                        iconBg = Color(0x22F59E0B),
                        title = "Schedule",
                        subtitle = scheduleSubtitle,
                        isExpanded = activeAccordion == QuickMenuAccordion.SCHEDULE,
                        testTag = "quick_menu_row_schedule",
                        onClick = {
                            activeAccordion = if (activeAccordion == QuickMenuAccordion.SCHEDULE) {
                                null
                            } else {
                                QuickMenuAccordion.SCHEDULE
                            }
                        }
                    )

                    // Schedule Accordion Inline Content
                    AnimatedVisibility(
                        visible = activeAccordion == QuickMenuAccordion.SCHEDULE,
                        enter = expandVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeIn(),
                        exit = shrinkVertically(spring(stiffness = Spring.StiffnessMedium)) + fadeOut()
                    ) {
                        ScheduleInlineView(
                            items = scheduleItems,
                            onAddTaskClick = { showNewScheduleDialog = true },
                            onToggleComplete = { item ->
                                val updated = scheduleItems.map {
                                    if (it.id == item.id) it.copy(isCompleted = !it.isCompleted) else it
                                }
                                scheduleItems = updated
                                QuickMenuStorage.saveScheduleItems(context, updated)
                            },
                            onToggleReminder = { item ->
                                val updated = scheduleItems.map {
                                    if (it.id == item.id) it.copy(reminder = !it.reminder) else it
                                }
                                scheduleItems = updated
                                QuickMenuStorage.saveScheduleItems(context, updated)
                                val stateStr = if (!item.reminder) "enabled" else "disabled"
                                Toast.makeText(context, "Reminder $stateStr for: ${item.title}", Toast.LENGTH_SHORT).show()
                            },
                            onEditTask = { scheduleItemToEdit = it },
                            onDeleteTask = { toDelete ->
                                val updated = scheduleItems.filter { it.id != toDelete.id }
                                scheduleItems = updated
                                QuickMenuStorage.saveScheduleItems(context, updated)
                                Toast.makeText(context, "Task deleted", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS: ADD / EDIT PROJECT
    // =========================================================================
    if (showNewProjectDialog) {
        ProjectFormDialog(
            title = "New Project",
            initialProject = null,
            onDismiss = { showNewProjectDialog = false },
            onSave = { newProj ->
                val updated = projects + newProj
                projects = updated
                QuickMenuStorage.saveProjects(context, updated)
                showNewProjectDialog = false
                Toast.makeText(context, "Created project: ${newProj.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    projectToEdit?.let { currentProj ->
        ProjectFormDialog(
            title = "Edit Project",
            initialProject = currentProj,
            onDismiss = { projectToEdit = null },
            onSave = { updatedProj ->
                val updated = projects.map { if (it.id == updatedProj.id) updatedProj else it }
                projects = updated
                QuickMenuStorage.saveProjects(context, updated)
                projectToEdit = null
                Toast.makeText(context, "Updated: ${updatedProj.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // =========================================================================
    // DIALOGS: ADD / EDIT SCHEDULE TASK
    // =========================================================================
    if (showNewScheduleDialog) {
        ScheduleFormDialog(
            title = "Add Task to Schedule",
            initialItem = null,
            onDismiss = { showNewScheduleDialog = false },
            onSave = { newItem ->
                val updated = scheduleItems + newItem
                scheduleItems = updated
                QuickMenuStorage.saveScheduleItems(context, updated)
                showNewScheduleDialog = false
                Toast.makeText(context, "Task added: ${newItem.title}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    scheduleItemToEdit?.let { currentItem ->
        ScheduleFormDialog(
            title = "Edit Task",
            initialItem = currentItem,
            onDismiss = { scheduleItemToEdit = null },
            onSave = { updatedItem ->
                val updated = scheduleItems.map { if (it.id == updatedItem.id) updatedItem else it }
                scheduleItems = updated
                QuickMenuStorage.saveScheduleItems(context, updated)
                scheduleItemToEdit = null
                Toast.makeText(context, "Task updated", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

/**
 * 48px standard row with icon on left, title, small subtitle, and chevron on right.
 */
@Composable
fun QuickMenuRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "row_chevron_$title"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GVONETextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
            tint = if (isExpanded) iconTint else Color(0xFF64748B),
            modifier = Modifier
                .size(22.dp)
                .rotate(chevronRotation)
        )
    }
}

/**
 * Inline view for Projects: shows list of projects, tabs/files/chats per project, and "New Project" button.
 */
@Composable
fun ProjectsInlineView(
    projects: List<QuickMenuProject>,
    onOpenProject: (QuickMenuProject) -> Unit,
    onRunProject: ((QuickMenuProject) -> Unit)? = null,
    onSendToDrive: ((QuickMenuProject) -> Unit)? = null,
    onNewProjectClick: () -> Unit,
    onEditProject: (QuickMenuProject) -> Unit,
    onDeleteProject: (QuickMenuProject) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10141D))
            .border(1.dp, Color(0xFF1F2836), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Projects Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Projects Workspace",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCBD5E1)
            )

            // "+ New Project" Button (touch target >= 44dp)
            Button(
                onClick = onNewProjectClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("quick_menu_new_project_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "New Project",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Project", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        if (projects.isEmpty()) {
            Text(
                text = "No projects yet. Tap '+ New Project' to create one.",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            projects.forEach { project ->
                var showDetails by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, Color(0xFF222C3D), RoundedCornerShape(10.dp))
                        .clickable { onOpenProject(project) }
                        .testTag("quick_menu_project_item_${project.id}"),
                    color = Color(0xFF151B26)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Project Title & Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = project.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (project.description.isNotBlank()) {
                                    Text(
                                        text = project.description,
                                        fontSize = 11.sp,
                                        color = Color(0xFF8B9CB5),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Edit button (>= 44dp touch target)
                                IconButton(
                                    onClick = { onEditProject(project) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Project",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Delete button (>= 44dp touch target)
                                IconButton(
                                    onClick = { onDeleteProject(project) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete Project",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Badges: Tabs count, Files count, Chats count
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CountBadge(
                                icon = Icons.Rounded.Tab,
                                text = "${project.tabs.size} tabs",
                                color = Color(0xFF38BDF8)
                            )
                            CountBadge(
                                icon = Icons.Rounded.Description,
                                text = "${project.files.size} files",
                                color = Color(0xFF34D399)
                            )
                            CountBadge(
                                icon = Icons.Rounded.Chat,
                                text = "${project.chats.size} chats",
                                color = Color(0xFFA78BFA)
                            )

                            // Toggle preview chips button
                            Text(
                                text = if (showDetails) "Hide details" else "View contents",
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { showDetails = !showDetails }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Expandable details (tabs, files, chats preview chips)
                        AnimatedVisibility(visible = showDetails) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (project.tabs.isNotEmpty()) {
                                    SectionChipList(title = "Tabs", items = project.tabs, chipBg = Color(0xFF1E293B), chipText = Color(0xFF7DD3FC))
                                }
                                if (project.files.isNotEmpty()) {
                                    SectionChipList(title = "Files", items = project.files, chipBg = Color(0xFF132A22), chipText = Color(0xFF6EE7B7))
                                }
                                if (project.chats.isNotEmpty()) {
                                    SectionChipList(title = "Chats", items = project.chats, chipBg = Color(0xFF281C3D), chipText = Color(0xFFC4B5FD))
                                }
                            }
                        }

                        // Open / Select Project Button (Touch target >= 44dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onOpenProject(project) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color(0xFF334155)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 44.dp)
                                    .testTag("quick_menu_open_project_btn_${project.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = "Open Project",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Open Group",
                                    fontSize = 12.sp,
                                    color = Color(0xFFF1F5F9),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (onRunProject != null) {
                                FilledTonalIconButton(
                                    onClick = { onRunProject(project) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("quick_menu_run_project_${project.id}"),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color(0xFF0F3B26),
                                        contentColor = Color(0xFF4ADE80)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Run Project Preview",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (onSendToDrive != null) {
                                FilledTonalIconButton(
                                    onClick = { onSendToDrive(project) },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .testTag("quick_menu_drive_project_${project.id}"),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = Color(0xFF1E2638),
                                        contentColor = Color(0xFF60A5FA)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudUpload,
                                        contentDescription = "Send to GVONE Drive",
                                        modifier = Modifier.size(20.dp)
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

/**
 * Inline view for Schedule: displays tasks by date/time, add/edit/delete, and reminder toggles.
 */
@Composable
fun ScheduleInlineView(
    items: List<QuickMenuScheduleItem>,
    onAddTaskClick: () -> Unit,
    onToggleComplete: (QuickMenuScheduleItem) -> Unit,
    onToggleReminder: (QuickMenuScheduleItem) -> Unit,
    onEditTask: (QuickMenuScheduleItem) -> Unit,
    onDeleteTask: (QuickMenuScheduleItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF10141D))
            .border(1.dp, Color(0xFF1F2836), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Schedule Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Upcoming Schedule",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCBD5E1)
            )

            // "+ Add Task" Button (touch target >= 44dp)
            Button(
                onClick = onAddTaskClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .testTag("quick_menu_new_task_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Task",
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Task", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }

        if (items.isEmpty()) {
            Text(
                text = "No tasks in schedule. Tap '+ Add Task' to set a reminder.",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        } else {
            // Group tasks by date ("Today", "Tomorrow", etc.)
            val grouped = items.groupBy { it.date }

            grouped.forEach { (dateHeader, dateItems) ->
                // Date Section Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                    )
                    Text(
                        text = dateHeader,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B)
                    )
                }

                dateItems.forEach { task ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, Color(0xFF222C3D), RoundedCornerShape(10.dp)),
                        color = Color(0xFF151B26)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Checkbox to complete task (Touch target >= 44dp)
                            IconButton(
                                onClick = { onToggleComplete(task) },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (task.isCompleted) Color(0xFF10B981) else Color(0xFF1F2937))
                                        .border(
                                            1.dp,
                                            if (task.isCompleted) Color(0xFF10B981) else Color(0xFF4B5563),
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (task.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Completed",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Task Title, Time & Reminder Tag
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 2.dp, end = 6.dp)
                            ) {
                                Text(
                                    text = task.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (task.isCompleted) Color(0xFF64748B) else Color.White,
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    // Time badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF261D12))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = task.time,
                                            fontSize = 10.sp,
                                            color = Color(0xFFFBBF24),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Reminder status chip
                                    if (task.reminder) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF064E3B))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Reminder ON",
                                                fontSize = 9.sp,
                                                color = Color(0xFF6EE7B7),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Action buttons: Reminder toggle, Edit, Delete (>= 44dp touch targets)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { onToggleReminder(task) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = if (task.reminder) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                        contentDescription = "Toggle Reminder",
                                        tint = if (task.reminder) Color(0xFF10B981) else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onEditTask(task) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit Task",
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteTask(task) },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "Delete Task",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
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

/**
 * Count badge helper for tabs, files, and chats.
 */
@Composable
fun CountBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = text,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Section chip list preview for Project tabs, files, or chats.
 */
@Composable
fun SectionChipList(
    title: String,
    items: List<String>,
    chipBg: Color,
    chipText: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(chipBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item,
                        fontSize = 10.sp,
                        color = chipText,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Dialog to add or edit a Project with tabs, files, and chats.
 */
@Composable
fun ProjectFormDialog(
    title: String,
    initialProject: QuickMenuProject?,
    onDismiss: () -> Unit,
    onSave: (QuickMenuProject) -> Unit
) {
    var name by remember { mutableStateOf(initialProject?.name ?: "") }
    var description by remember { mutableStateOf(initialProject?.description ?: "") }
    var tabsText by remember { mutableStateOf(initialProject?.tabs?.joinToString(", ") ?: "Google, GitHub") }
    var filesText by remember { mutableStateOf(initialProject?.files?.joinToString(", ") ?: "Main.kt, app.py") }
    var chatsText by remember { mutableStateOf(initialProject?.chats?.joinToString(", ") ?: "Project kickoff") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF161C26),
            border = BorderStroke(1.dp, Color(0xFF2C394E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tabsText,
                    onValueChange = { tabsText = it },
                    label = { Text("Tabs (comma separated)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = filesText,
                    onValueChange = { filesText = it },
                    label = { Text("Files (comma separated)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                val tabsList = tabsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val filesList = filesText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val chatsList = chatsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                                onSave(
                                    initialProject?.copy(
                                        name = name.trim(),
                                        description = description.trim(),
                                        tabs = tabsList,
                                        files = filesList,
                                        chats = chatsList
                                    ) ?: QuickMenuProject(
                                        name = name.trim(),
                                        description = description.trim(),
                                        tabs = tabsList,
                                        files = filesList,
                                        chats = chatsList
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Save Project", color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Dialog to add or edit a Schedule Item with title, date, time, and reminder.
 */
@Composable
fun ScheduleFormDialog(
    title: String,
    initialItem: QuickMenuScheduleItem?,
    onDismiss: () -> Unit,
    onSave: (QuickMenuScheduleItem) -> Unit
) {
    var taskTitle by remember { mutableStateOf(initialItem?.title ?: "") }
    var taskDate by remember { mutableStateOf(initialItem?.date ?: "Today") }
    var taskTime by remember { mutableStateOf(initialItem?.time ?: "4:00 PM") }
    var reminder by remember { mutableStateOf(initialItem?.reminder ?: true) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF161C26),
            border = BorderStroke(1.dp, Color(0xFF2C394E))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("Task Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Date Presets & Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Date", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Today", "Tomorrow", "Next Week").forEach { preset ->
                            val selected = taskDate.equals(preset, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Color(0xFFD97706) else Color(0xFF1E293B))
                                    .clickable { taskDate = preset }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    color = if (selected) Color.White else Color(0xFF94A3B8),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = taskDate,
                        onValueChange = { taskDate = it },
                        label = { Text("Custom Date") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Time Presets & Field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Time", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("9:00 AM", "12:00 PM", "4:00 PM", "6:30 PM", "8:00 PM").forEach { preset ->
                            val selected = taskTime.equals(preset, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) Color(0xFFD97706) else Color(0xFF1E293B))
                                    .clickable { taskTime = preset }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    color = if (selected) Color.White else Color(0xFF94A3B8),
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = taskTime,
                        onValueChange = { taskTime = it },
                        label = { Text("Custom Time") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Reminder switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Enable Notification Reminder", fontSize = 12.sp, color = Color.White)
                    Switch(
                        checked = reminder,
                        onCheckedChange = { reminder = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981)
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Cancel", color = Color(0xFF94A3B8))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (taskTitle.isNotBlank()) {
                                onSave(
                                    initialItem?.copy(
                                        title = taskTitle.trim(),
                                        date = taskDate.trim(),
                                        time = taskTime.trim(),
                                        reminder = reminder
                                    ) ?: QuickMenuScheduleItem(
                                        title = taskTitle.trim(),
                                        date = taskDate.trim(),
                                        time = taskTime.trim(),
                                        reminder = reminder
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Text("Save Task", color = Color.White)
                    }
                }
            }
        }
    }
}
