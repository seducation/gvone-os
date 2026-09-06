package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class EnvironmentLayoutMode(val displayName: String) {
    GRID("Grid Snap"),
    FREEFORM("Freeform Canvas"),
    AUTO("Flow Arrangement")
}

enum class BackgroundType(val displayName: String) {
    SOLID("Solid Color"),
    GRADIENT("Mesh Gradient"),
    WALLPAPER("Atmospheric Wallpaper")
}

data class BackgroundPreset(
    val id: String,
    val name: String,
    val type: BackgroundType,
    val primaryHex: String,
    val secondaryHex: String,
    val accentHex: String
)

object EnvironmentPresets {
    val BACKGROUND_PRESETS = listOf(
        BackgroundPreset("aurora", "Nordic Aurora", BackgroundType.GRADIENT, "#0A0F1D", "#1E1B4B", "#06B6D4"),
        BackgroundPreset("midnight", "Midnight Cyber", BackgroundType.GRADIENT, "#050811", "#1E0D36", "#EC4899"),
        BackgroundPreset("sunset", "Cosmic Sunset", BackgroundType.GRADIENT, "#180B1E", "#451A03", "#F43F5E"),
        BackgroundPreset("emerald", "Deep Forest", BackgroundType.GRADIENT, "#061A14", "#042F2E", "#10B981"),
        BackgroundPreset("ocean", "Abyssal Trench", BackgroundType.GRADIENT, "#081325", "#0C2A4D", "#38BDF8"),
        BackgroundPreset("slate", "Minimal Charcoal", BackgroundType.GRADIENT, "#0F172A", "#1E293B", "#64748B"),
        BackgroundPreset("lavender", "Violet Horizon", BackgroundType.GRADIENT, "#120B24", "#2E1065", "#A855F7"),
        BackgroundPreset("solid_dark", "Pure Obsidian", BackgroundType.SOLID, "#0A0D14", "#0A0D14", "#3B82F6"),
        BackgroundPreset("solid_navy", "Deep Navy", BackgroundType.SOLID, "#0B1528", "#0B1528", "#60A5FA")
    )
}

data class EnvironmentBackground(
    val type: BackgroundType = BackgroundType.GRADIENT,
    val primaryColorHex: String = "#0A0F1D",
    val secondaryColorHex: String = "#1E1B4B",
    val accentColorHex: String = "#06B6D4",
    val presetId: String = "aurora",
    val customImageUrl: String? = null,
    val blurRadius: Float = 0f,
    val opacity: Float = 0.95f
) {
    companion object {
        fun fromPreset(presetId: String): EnvironmentBackground {
            return when (presetId) {
                "aurora" -> EnvironmentBackground(type = BackgroundType.GRADIENT, primaryColorHex = "#0A0F1D", secondaryColorHex = "#1E1B4B", accentColorHex = "#06B6D4", presetId = "aurora")
                "sunset" -> EnvironmentBackground(type = BackgroundType.GRADIENT, primaryColorHex = "#1C0D26", secondaryColorHex = "#4A154B", accentColorHex = "#F43F5E", presetId = "sunset")
                "ocean" -> EnvironmentBackground(type = BackgroundType.GRADIENT, primaryColorHex = "#081325", secondaryColorHex = "#0C2A4D", accentColorHex = "#38BDF8", presetId = "ocean")
                "forest" -> EnvironmentBackground(type = BackgroundType.GRADIENT, primaryColorHex = "#081C15", secondaryColorHex = "#1B4332", accentColorHex = "#10B981", presetId = "forest")
                "cyberpunk" -> EnvironmentBackground(type = BackgroundType.GRADIENT, primaryColorHex = "#180828", secondaryColorHex = "#2B0B3F", accentColorHex = "#E11D48", presetId = "cyberpunk")
                "lavender" -> EnvironmentBackground(type = BackgroundType.GRADIENT, primaryColorHex = "#120B24", secondaryColorHex = "#2E1065", accentColorHex = "#A855F7", presetId = "lavender")
                "midnight" -> EnvironmentBackground(type = BackgroundType.SOLID, primaryColorHex = "#0A0D14", secondaryColorHex = "#0A0D14", accentColorHex = "#64748B", presetId = "midnight")
                else -> EnvironmentBackground(type = BackgroundType.GRADIENT, primaryColorHex = "#0A0F1D", secondaryColorHex = "#1E1B4B", accentColorHex = "#06B6D4", presetId = "aurora")
            }
        }
    }
}

enum class CanvasObjectType {
    LINK,
    WIDGET,
    FOLDER,
    NOTE
}

enum class LinkOpenBehavior(val displayName: String) {
    CURRENT_TAB("Open in Current Tab"),
    NEW_TAB("Open in New Tab"),
    NEW_BACKGROUND_TAB("Open in Background")
}

enum class CanvasWidgetType(val displayName: String, val subtitle: String) {
    CLOCK("Live Clock", "Digital & Analog clock with seconds and date"),
    DATE_CALENDAR("Monthly Calendar", "Interactive calendar with today highlighted"),
    WEATHER("Live Weather", "Real-time forecast, temperature & conditions"),
    SEARCH("Universal Search", "Instant web search & GVONE AI prompt"),
    SYSTEM_INFO("System & Web Shield", "Battery, storage, and Tor privacy status"),
    NOTES_WIDGET("Canvas Sticky Note", "Rich markdown and quick scratchpad note"),
    RSS_FEED("Curated Feeds", "Live science, technology, and tech headlines"),
    QUICK_TOOLS("Safari Quick Tools", "Tor switch, bookmarks, reading list & downloads")
}

data class FolderItem(
    val id: String,
    val title: String,
    val url: String,
    val iconName: String = "Globe",
    val accentColorHex: String = "#3B82F6"
)

sealed class CanvasObject {
    abstract val id: String
    abstract val type: CanvasObjectType
    abstract val x: Float
    abstract val y: Float
    abstract val width: Float
    abstract val height: Float
    abstract val zIndex: Int

    data class LinkObject(
        override val id: String,
        override val type: CanvasObjectType = CanvasObjectType.LINK,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = 1f,
        override val height: Float = 1f,
        override val zIndex: Int = 0,
        val title: String,
        val url: String,
        val iconName: String = "Globe",
        val accentColorHex: String = "#3B82F6",
        val openBehavior: LinkOpenBehavior = LinkOpenBehavior.CURRENT_TAB
    ) : CanvasObject()

    data class WidgetObject(
        override val id: String,
        override val type: CanvasObjectType = CanvasObjectType.WIDGET,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = 2f,
        override val height: Float = 1.4f,
        override val zIndex: Int = 0,
        val widgetType: CanvasWidgetType,
        val config: Map<String, String> = emptyMap()
    ) : CanvasObject()

    data class FolderObject(
        override val id: String,
        override val type: CanvasObjectType = CanvasObjectType.FOLDER,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = 2f,
        override val height: Float = 1.3f,
        override val zIndex: Int = 0,
        val title: String,
        val iconName: String = "Folder",
        val accentColorHex: String = "#6366F1",
        val items: List<FolderItem> = emptyList()
    ) : CanvasObject()

    data class NoteObject(
        override val id: String,
        override val type: CanvasObjectType = CanvasObjectType.NOTE,
        override val x: Float = 0f,
        override val y: Float = 0f,
        override val width: Float = 2f,
        override val height: Float = 1.6f,
        override val zIndex: Int = 0,
        val title: String = "Scratchpad",
        val content: String = "",
        val colorHex: String = "#F59E0B"
    ) : CanvasObject()
}

data class Environment(
    val id: String,
    val name: String,
    val iconName: String = "Person",
    val themeMode: String = "dark",
    val background: EnvironmentBackground = EnvironmentBackground(),
    val layoutMode: EnvironmentLayoutMode = EnvironmentLayoutMode.GRID,
    val objects: List<CanvasObject> = emptyList(),
    val startPageUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
