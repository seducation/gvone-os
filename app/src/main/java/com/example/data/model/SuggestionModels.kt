package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Data-driven model representing a proactive suggestion in GVONE.
 */
data class Suggestion(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val action: SuggestionAction,
    val category: String = "General"
)

sealed interface SuggestionAction {
    data class OpenSheet(val sheetName: String) : SuggestionAction
    data class RunGoal(val goal: String) : SuggestionAction
    data class Navigate(val url: String) : SuggestionAction
    data class Custom(val actionId: String) : SuggestionAction
}

object DefaultSuggestions {
    fun getIconForName(name: String): ImageVector = when (name) {
        "Assignment", "Task" -> Icons.AutoMirrored.Rounded.Assignment
        "Hub", "Swarm" -> Icons.Rounded.Hub
        "SmartToy", "Robot", "Agent" -> Icons.Rounded.SmartToy
        "AccountTree", "Workflow" -> Icons.Rounded.AccountTree
        "Insights", "Signal", "Analytics" -> Icons.Rounded.Insights
        "RssFeed", "Feed" -> Icons.Rounded.RssFeed
        "AutoAwesome", "Sparkle" -> Icons.Rounded.AutoAwesome
        "Lightbulb" -> Icons.Rounded.Lightbulb
        "Security", "Shield" -> Icons.Rounded.Security
        "Science", "Research" -> Icons.Rounded.Science
        else -> Icons.Rounded.Lightbulb
    }

    val items: List<Suggestion> = listOf(
        Suggestion(
            id = "add_mission_templates",
            title = "Add Mission Templates",
            description = "Bootstrap autonomous agent mission templates & goals",
            iconName = "Assignment",
            action = SuggestionAction.RunGoal("Load autonomous mission templates"),
            category = "Missions"
        ),
        Suggestion(
            id = "visualize_swarm",
            title = "Visualize Swarm",
            description = "Inspect multi-agent neural coordination & mesh topology",
            iconName = "Hub",
            action = SuggestionAction.OpenSheet("AgentDashboard"),
            category = "Swarm"
        ),
        Suggestion(
            id = "explore_agents",
            title = "Explore Agents",
            description = "Discover specialized coding, browser, search & reflex agents",
            iconName = "SmartToy",
            action = SuggestionAction.OpenSheet("AgentDashboard"),
            category = "Agents"
        ),
        Suggestion(
            id = "create_workflow",
            title = "Create Workflow",
            description = "Configure multi-step nodal workflows & pipeline triggers",
            iconName = "AccountTree",
            action = SuggestionAction.OpenSheet("CustomCommands"),
            category = "Workflows"
        ),
        Suggestion(
            id = "analyze_signal",
            title = "Analyze Signal",
            description = "Run deep research & intelligence telemetry scan",
            iconName = "Insights",
            action = SuggestionAction.OpenSheet("ResearchWorkspace"),
            category = "Signals"
        ),
        Suggestion(
            id = "browse_feeds",
            title = "Browse Feeds",
            description = "Explore aggregated live data streams & communication hub",
            iconName = "RssFeed",
            action = SuggestionAction.OpenSheet("CommunicationHub"),
            category = "Feeds"
        )
    )
}
