package com.example.data.environment

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class EnvironmentManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("gvone_environments_prefs", Context.MODE_PRIVATE)

    private val _environments = MutableStateFlow<List<Environment>>(emptyList())
    val environments: StateFlow<List<Environment>> = _environments.asStateFlow()

    private val _activeEnvironmentId = MutableStateFlow<String>("personal")
    val activeEnvironmentId: StateFlow<String> = _activeEnvironmentId.asStateFlow()

    private val _currentEnvironment = MutableStateFlow<Environment>(createDefaultPersonalEnvironment())
    val currentEnvironment: StateFlow<Environment> = _currentEnvironment.asStateFlow()

    init {
        loadEnvironments()
    }

    private fun loadEnvironments() {
        val jsonString = prefs.getString("environments_json", null)
        val savedActiveId = prefs.getString("active_environment_id", "personal") ?: "personal"

        val loadedList = if (!jsonString.isNullOrBlank()) {
            try {
                deserializeEnvironments(jsonString)
            } catch (e: Exception) {
                createDefaultEnvironments()
            }
        } else {
            createDefaultEnvironments()
        }

        val finalList = if (loadedList.isEmpty()) createDefaultEnvironments() else loadedList
        val ensuredList = finalList.map { env ->
            if (env.id == "personal") {
                val hasRssLink = env.objects.any { it is CanvasObject.LinkObject && it.url.contains("rssgroupfeed-jaelvwfd.manus.space") }
                val startPage = if (env.startPageUrl.isNullOrBlank()) "https://rssgroupfeed-jaelvwfd.manus.space" else env.startPageUrl
                val envWithStartPage = env.copy(startPageUrl = startPage)
                if (!hasRssLink) {
                    val defaultRss = CanvasObject.LinkObject(
                        id = "p_link_rss",
                        title = "RSS Group Feed",
                        url = "https://rssgroupfeed-jaelvwfd.manus.space",
                        iconName = "RssFeed",
                        accentColorHex = "#F59E0B",
                        x = 0f,
                        y = 2.3f,
                        width = 1f,
                        height = 1f
                    )
                    val nonLinks = env.objects.filterNot { it is CanvasObject.LinkObject }
                    val links = env.objects.filterIsInstance<CanvasObject.LinkObject>()
                    envWithStartPage.copy(objects = nonLinks + listOf(defaultRss) + links)
                } else {
                    envWithStartPage
                }
            } else {
                env
            }
        }
        _environments.value = ensuredList

        val active = ensuredList.find { it.id == savedActiveId } ?: ensuredList.first()
        _activeEnvironmentId.value = active.id
        _currentEnvironment.value = active

        saveEnvironments(ensuredList, active.id)
    }

    private fun persist() {
        saveEnvironments(_environments.value, _activeEnvironmentId.value)
    }

    private fun saveEnvironments(list: List<Environment>, activeId: String) {
        try {
            val json = serializeEnvironments(list)
            prefs.edit()
                .putString("environments_json", json)
                .putString("active_environment_id", activeId)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun switchEnvironment(environmentId: String) {
        val target = _environments.value.find { it.id == environmentId } ?: return
        _activeEnvironmentId.value = target.id
        _currentEnvironment.value = target
        persist()
    }

    fun createEnvironment(
        name: String,
        iconName: String,
        themeMode: String = "dark",
        presetId: String = "aurora",
        initialLinkUrl: String? = null,
        initialLinkTitle: String? = null
    ): Environment {
        val preset = EnvironmentPresets.BACKGROUND_PRESETS.find { it.id == presetId }
            ?: EnvironmentPresets.BACKGROUND_PRESETS.first()
        val baseObjects = mutableListOf<CanvasObject>(
            CanvasObject.WidgetObject(
                id = UUID.randomUUID().toString(),
                x = 0f,
                y = 0f,
                width = 2f,
                height = 1.3f,
                widgetType = CanvasWidgetType.CLOCK
            ),
            CanvasObject.WidgetObject(
                id = UUID.randomUUID().toString(),
                x = 2f,
                y = 0f,
                width = 2f,
                height = 1.3f,
                widgetType = CanvasWidgetType.SEARCH
            )
        )

        val resolvedStartUrl = if (!initialLinkUrl.isNullOrBlank()) {
            if (initialLinkUrl.startsWith("http://") || initialLinkUrl.startsWith("https://")) {
                initialLinkUrl.trim()
            } else {
                "https://${initialLinkUrl.trim()}"
            }
        } else null

        if (resolvedStartUrl != null) {
            val title = if (!initialLinkTitle.isNullOrBlank()) {
                initialLinkTitle.trim()
            } else {
                resolvedStartUrl.removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore("/")
            }
            baseObjects.add(
                CanvasObject.LinkObject(
                    id = UUID.randomUUID().toString(),
                    title = title,
                    url = resolvedStartUrl,
                    iconName = "Globe",
                    accentColorHex = preset.accentHex,
                    x = 0f,
                    y = 1.3f,
                    width = 1f,
                    height = 1f
                )
            )
        }

        val newEnv = Environment(
            id = UUID.randomUUID().toString(),
            name = name,
            iconName = iconName,
            themeMode = themeMode,
            background = EnvironmentBackground(
                type = preset.type,
                primaryColorHex = preset.primaryHex,
                secondaryColorHex = preset.secondaryHex,
                accentColorHex = preset.accentHex,
                presetId = preset.id
            ),
            layoutMode = EnvironmentLayoutMode.GRID,
            objects = baseObjects,
            startPageUrl = resolvedStartUrl
        )

        val updated = _environments.value + newEnv
        _environments.value = updated
        switchEnvironment(newEnv.id)
        return newEnv
    }

    fun updateEnvironment(environment: Environment) {
        val updated = _environments.value.map {
            if (it.id == environment.id) environment else it
        }
        _environments.value = updated
        if (_activeEnvironmentId.value == environment.id) {
            _currentEnvironment.value = environment
        }
        persist()
    }

    fun duplicateEnvironment(environmentId: String) {
        val original = _environments.value.find { it.id == environmentId } ?: return
        val clone = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)",
            createdAt = System.currentTimeMillis()
        )
        val updated = _environments.value + clone
        _environments.value = updated
        switchEnvironment(clone.id)
    }

    fun deleteEnvironment(environmentId: String) {
        if (_environments.value.size <= 1) return // Keep at least one environment
        val updated = _environments.value.filter { it.id != environmentId }
        _environments.value = updated
        if (_activeEnvironmentId.value == environmentId) {
            val fallback = updated.first()
            _activeEnvironmentId.value = fallback.id
            _currentEnvironment.value = fallback
        }
        persist()
    }

    fun addObject(obj: CanvasObject) {
        val current = _currentEnvironment.value
        val updatedObjects = current.objects + obj
        val updatedEnv = current.copy(objects = updatedObjects)
        updateEnvironment(updatedEnv)
    }

    fun addObjectToEnvironment(obj: CanvasObject, targetEnvironmentId: String) {
        val targetEnv = _environments.value.find { it.id == targetEnvironmentId } ?: _currentEnvironment.value
        val updatedObjects = targetEnv.objects + obj
        val updatedEnv = targetEnv.copy(objects = updatedObjects)
        updateEnvironment(updatedEnv)
    }

    fun updateObject(obj: CanvasObject) {
        val current = _currentEnvironment.value
        val updatedObjects = current.objects.map {
            if (it.id == obj.id) obj else it
        }
        val updatedEnv = current.copy(objects = updatedObjects)
        updateEnvironment(updatedEnv)
    }

    fun removeObject(objectId: String) {
        val current = _currentEnvironment.value
        val updatedObjects = current.objects.filter { it.id != objectId }
        val updatedEnv = current.copy(objects = updatedObjects)
        updateEnvironment(updatedEnv)
    }

    fun updateBackground(background: EnvironmentBackground) {
        val current = _currentEnvironment.value
        val updatedEnv = current.copy(background = background)
        updateEnvironment(updatedEnv)
    }

    fun updateLayoutMode(mode: EnvironmentLayoutMode) {
        val current = _currentEnvironment.value
        val updatedEnv = current.copy(layoutMode = mode)
        updateEnvironment(updatedEnv)
    }

    // JSON Serialization & Deserialization
    private fun serializeEnvironments(list: List<Environment>): String {
        val array = JSONArray()
        for (env in list) {
            val obj = JSONObject()
            obj.put("id", env.id)
            obj.put("name", env.name)
            obj.put("iconName", env.iconName)
            obj.put("themeMode", env.themeMode)
            obj.put("layoutMode", env.layoutMode.name)
            obj.put("createdAt", env.createdAt)
            if (!env.startPageUrl.isNullOrBlank()) {
                obj.put("startPageUrl", env.startPageUrl)
            }

            // Background
            val bgObj = JSONObject()
            bgObj.put("type", env.background.type.name)
            bgObj.put("primaryColorHex", env.background.primaryColorHex)
            bgObj.put("secondaryColorHex", env.background.secondaryColorHex)
            bgObj.put("accentColorHex", env.background.accentColorHex)
            bgObj.put("presetId", env.background.presetId)
            bgObj.put("blurRadius", env.background.blurRadius.toDouble())
            bgObj.put("opacity", env.background.opacity.toDouble())
            if (env.background.customImageUrl != null) {
                bgObj.put("customImageUrl", env.background.customImageUrl)
            }
            obj.put("background", bgObj)

            // Objects
            val objArray = JSONArray()
            for (item in env.objects) {
                val itemJson = JSONObject()
                itemJson.put("id", item.id)
                itemJson.put("type", item.type.name)
                itemJson.put("x", item.x.toDouble())
                itemJson.put("y", item.y.toDouble())
                itemJson.put("width", item.width.toDouble())
                itemJson.put("height", item.height.toDouble())
                itemJson.put("zIndex", item.zIndex)

                when (item) {
                    is CanvasObject.LinkObject -> {
                        itemJson.put("title", item.title)
                        itemJson.put("url", item.url)
                        itemJson.put("iconName", item.iconName)
                        itemJson.put("accentColorHex", item.accentColorHex)
                        itemJson.put("openBehavior", item.openBehavior.name)
                    }
                    is CanvasObject.WidgetObject -> {
                        itemJson.put("widgetType", item.widgetType.name)
                        val configObj = JSONObject()
                        item.config.forEach { (k, v) -> configObj.put(k, v) }
                        itemJson.put("config", configObj)
                    }
                    is CanvasObject.FolderObject -> {
                        itemJson.put("title", item.title)
                        itemJson.put("iconName", item.iconName)
                        itemJson.put("accentColorHex", item.accentColorHex)
                        val folderArray = JSONArray()
                        for (folderItem in item.items) {
                            val fObj = JSONObject()
                            fObj.put("id", folderItem.id)
                            fObj.put("title", folderItem.title)
                            fObj.put("url", folderItem.url)
                            fObj.put("iconName", folderItem.iconName)
                            fObj.put("accentColorHex", folderItem.accentColorHex)
                            folderArray.put(fObj)
                        }
                        itemJson.put("items", folderArray)
                    }
                    is CanvasObject.NoteObject -> {
                        itemJson.put("title", item.title)
                        itemJson.put("content", item.content)
                        itemJson.put("colorHex", item.colorHex)
                    }
                    is CanvasObject.WebPortionWidgetObject -> {
                        itemJson.put("title", item.title)
                        itemJson.put("sourceUrl", item.sourceUrl)
                        itemJson.put("siteName", item.siteName)
                        if (item.faviconUrl != null) itemJson.put("faviconUrl", item.faviconUrl)
                        itemJson.put("widgetType", item.widgetType.name)
                        if (item.domSelector != null) itemJson.put("domSelector", item.domSelector)
                        if (item.domTagName != null) itemJson.put("domTagName", item.domTagName)
                        if (item.extractedHtml != null) itemJson.put("extractedHtml", item.extractedHtml)
                        if (item.snapshotBase64 != null) itemJson.put("snapshotBase64", item.snapshotBase64)
                        val cropObj = JSONObject()
                        cropObj.put("xPercent", item.cropBounds.xPercent.toDouble())
                        cropObj.put("yPercent", item.cropBounds.yPercent.toDouble())
                        cropObj.put("widthPercent", item.cropBounds.widthPercent.toDouble())
                        cropObj.put("heightPercent", item.cropBounds.heightPercent.toDouble())
                        cropObj.put("scrollXPx", item.cropBounds.scrollXPx)
                        cropObj.put("scrollYPx", item.cropBounds.scrollYPx)
                        cropObj.put("widthPx", item.cropBounds.widthPx)
                        cropObj.put("heightPx", item.cropBounds.heightPx)
                        itemJson.put("cropBounds", cropObj)
                        itemJson.put("refreshIntervalMinutes", item.refreshIntervalMinutes)
                        itemJson.put("lastRefreshedAt", item.lastRefreshedAt)
                        itemJson.put("interactionMode", item.interactionMode.name)
                        itemJson.put("isLiveValid", item.isLiveValid)
                        if (item.lastErrorMessage != null) itemJson.put("lastErrorMessage", item.lastErrorMessage)
                    }
                }
                objArray.put(itemJson)
            }
            obj.put("objects", objArray)
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeEnvironments(json: String): List<Environment> {
        val result = mutableListOf<Environment>()
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getString("id")
            val name = obj.getString("name")
            val iconName = obj.optString("iconName", "Person")
            val themeMode = obj.optString("themeMode", "dark")
            val layoutModeStr = obj.optString("layoutMode", "GRID")
            val layoutMode = try {
                EnvironmentLayoutMode.valueOf(layoutModeStr)
            } catch (e: Exception) {
                EnvironmentLayoutMode.GRID
            }
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val startPageUrl = if (obj.has("startPageUrl")) {
                obj.getString("startPageUrl").ifBlank { if (id == "personal") "https://rssgroupfeed-jaelvwfd.manus.space" else null }
            } else {
                if (id == "personal") "https://rssgroupfeed-jaelvwfd.manus.space" else null
            }

            val bgObj = obj.optJSONObject("background")
            val background = if (bgObj != null) {
                val typeStr = bgObj.optString("type", "GRADIENT")
                val bgType = try { BackgroundType.valueOf(typeStr) } catch (e: Exception) { BackgroundType.GRADIENT }
                EnvironmentBackground(
                    type = bgType,
                    primaryColorHex = bgObj.optString("primaryColorHex", "#0A0F1D"),
                    secondaryColorHex = bgObj.optString("secondaryColorHex", "#1E1B4B"),
                    accentColorHex = bgObj.optString("accentColorHex", "#06B6D4"),
                    presetId = bgObj.optString("presetId", "aurora"),
                    customImageUrl = if (bgObj.has("customImageUrl")) bgObj.getString("customImageUrl") else null,
                    blurRadius = bgObj.optDouble("blurRadius", 0.0).toFloat(),
                    opacity = bgObj.optDouble("opacity", 0.95).toFloat()
                )
            } else {
                EnvironmentBackground()
            }

            val objects = mutableListOf<CanvasObject>()
            val objArray = obj.optJSONArray("objects")
            if (objArray != null) {
                for (j in 0 until objArray.length()) {
                    val item = objArray.getJSONObject(j)
                    val itemId = item.getString("id")
                    val typeStr = item.getString("type")
                    val x = item.optDouble("x", 0.0).toFloat()
                    val y = item.optDouble("y", 0.0).toFloat()
                    val width = item.optDouble("width", 1.0).toFloat()
                    val height = item.optDouble("height", 1.0).toFloat()
                    val zIndex = item.optInt("zIndex", 0)

                    when (typeStr) {
                        CanvasObjectType.LINK.name -> {
                            val behaviorStr = item.optString("openBehavior", "CURRENT_TAB")
                            val behavior = try { LinkOpenBehavior.valueOf(behaviorStr) } catch (e: Exception) { LinkOpenBehavior.CURRENT_TAB }
                            objects.add(
                                CanvasObject.LinkObject(
                                    id = itemId,
                                    x = x,
                                    y = y,
                                    width = width,
                                    height = height,
                                    zIndex = zIndex,
                                    title = item.optString("title", "Link"),
                                    url = item.optString("url", "https://"),
                                    iconName = item.optString("iconName", "Globe"),
                                    accentColorHex = item.optString("accentColorHex", "#3B82F6"),
                                    openBehavior = behavior
                                )
                            )
                        }
                        CanvasObjectType.WIDGET.name -> {
                            val wTypeStr = item.optString("widgetType", "CLOCK")
                            val wType = try { CanvasWidgetType.valueOf(wTypeStr) } catch (e: Exception) { CanvasWidgetType.CLOCK }
                            val configMap = mutableMapOf<String, String>()
                            val configObj = item.optJSONObject("config")
                            if (configObj != null) {
                                val keys = configObj.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    configMap[key] = configObj.getString(key)
                                }
                            }
                            objects.add(
                                CanvasObject.WidgetObject(
                                    id = itemId,
                                    x = x,
                                    y = y,
                                    width = width,
                                    height = height,
                                    zIndex = zIndex,
                                    widgetType = wType,
                                    config = configMap
                                )
                            )
                        }
                        CanvasObjectType.FOLDER.name -> {
                            val folderItems = mutableListOf<FolderItem>()
                            val fArray = item.optJSONArray("items")
                            if (fArray != null) {
                                for (k in 0 until fArray.length()) {
                                    val fObj = fArray.getJSONObject(k)
                                    folderItems.add(
                                        FolderItem(
                                            id = fObj.optString("id", UUID.randomUUID().toString()),
                                            title = fObj.optString("title", "Link"),
                                            url = fObj.optString("url", "https://"),
                                            iconName = fObj.optString("iconName", "Globe"),
                                            accentColorHex = fObj.optString("accentColorHex", "#3B82F6")
                                        )
                                    )
                                }
                            }
                            objects.add(
                                CanvasObject.FolderObject(
                                    id = itemId,
                                    x = x,
                                    y = y,
                                    width = width,
                                    height = height,
                                    zIndex = zIndex,
                                    title = item.optString("title", "Folder"),
                                    iconName = item.optString("iconName", "Folder"),
                                    accentColorHex = item.optString("accentColorHex", "#6366F1"),
                                    items = folderItems
                                )
                            )
                        }
                        CanvasObjectType.NOTE.name -> {
                            objects.add(
                                CanvasObject.NoteObject(
                                    id = itemId,
                                    x = x,
                                    y = y,
                                    width = width,
                                    height = height,
                                    zIndex = zIndex,
                                    title = item.optString("title", "Note"),
                                    content = item.optString("content", ""),
                                    colorHex = item.optString("colorHex", "#F59E0B")
                                )
                            )
                        }
                        CanvasObjectType.WEB_PORTION.name -> {
                            val wTypeStr = item.optString("widgetType", "LIVE_DOM")
                            val wType = try { WebWidgetType.valueOf(wTypeStr) } catch (e: Exception) { WebWidgetType.LIVE_DOM }
                            val interStr = item.optString("interactionMode", "OPEN_ORIGINAL")
                            val interMode = try { WebWidgetInteraction.valueOf(interStr) } catch (e: Exception) { WebWidgetInteraction.OPEN_ORIGINAL }

                            val cropObj = item.optJSONObject("cropBounds")
                            val cropBounds = if (cropObj != null) {
                                WebWidgetCropBounds(
                                    xPercent = cropObj.optDouble("xPercent", 0.0).toFloat(),
                                    yPercent = cropObj.optDouble("yPercent", 0.0).toFloat(),
                                    widthPercent = cropObj.optDouble("widthPercent", 1.0).toFloat(),
                                    heightPercent = cropObj.optDouble("heightPercent", 1.0).toFloat(),
                                    scrollXPx = cropObj.optInt("scrollXPx", 0),
                                    scrollYPx = cropObj.optInt("scrollYPx", 0),
                                    widthPx = cropObj.optInt("widthPx", 320),
                                    heightPx = cropObj.optInt("heightPx", 220)
                                )
                            } else WebWidgetCropBounds()

                            objects.add(
                                CanvasObject.WebPortionWidgetObject(
                                    id = itemId,
                                    x = x,
                                    y = y,
                                    width = width,
                                    height = height,
                                    zIndex = zIndex,
                                    title = item.optString("title", "Web Widget"),
                                    sourceUrl = item.optString("sourceUrl", "https://"),
                                    siteName = item.optString("siteName", ""),
                                    faviconUrl = if (item.has("faviconUrl")) item.getString("faviconUrl") else null,
                                    widgetType = wType,
                                    domSelector = if (item.has("domSelector")) item.getString("domSelector") else null,
                                    domTagName = if (item.has("domTagName")) item.getString("domTagName") else null,
                                    extractedHtml = if (item.has("extractedHtml")) item.getString("extractedHtml") else null,
                                    snapshotBase64 = if (item.has("snapshotBase64")) item.getString("snapshotBase64") else null,
                                    cropBounds = cropBounds,
                                    refreshIntervalMinutes = item.optInt("refreshIntervalMinutes", 15),
                                    lastRefreshedAt = item.optLong("lastRefreshedAt", System.currentTimeMillis()),
                                    interactionMode = interMode,
                                    isLiveValid = item.optBoolean("isLiveValid", true),
                                    lastErrorMessage = if (item.has("lastErrorMessage")) item.getString("lastErrorMessage") else null
                                )
                            )
                        }
                    }
                }
            }

            result.add(
                Environment(
                    id = id,
                    name = name,
                    iconName = iconName,
                    themeMode = themeMode,
                    background = background,
                    layoutMode = layoutMode,
                    objects = objects,
                    startPageUrl = startPageUrl,
                    createdAt = createdAt
                )
            )
        }
        return result
    }

    companion object {
        fun createDefaultPersonalEnvironment(): Environment {
        return Environment(
            id = "personal",
            name = "Personal",
            iconName = "Person",
            themeMode = "dark",
            background = EnvironmentBackground(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#0A0F1D",
                secondaryColorHex = "#1E1B4B",
                accentColorHex = "#06B6D4",
                presetId = "aurora"
            ),
            layoutMode = EnvironmentLayoutMode.GRID,
            startPageUrl = "https://rssgroupfeed-jaelvwfd.manus.space",
            objects = listOf(
                CanvasObject.WidgetObject(
                    id = "p_search",
                    x = 0f,
                    y = 0f,
                    width = 4f,
                    height = 1.0f,
                    widgetType = CanvasWidgetType.SEARCH
                ),
                CanvasObject.WidgetObject(
                    id = "p_clock",
                    x = 0f,
                    y = 1f,
                    width = 2f,
                    height = 1.3f,
                    widgetType = CanvasWidgetType.CLOCK
                ),
                CanvasObject.WidgetObject(
                    id = "p_weather",
                    x = 2f,
                    y = 1f,
                    width = 2f,
                    height = 1.3f,
                    widgetType = CanvasWidgetType.WEATHER
                ),
                CanvasObject.LinkObject(
                    id = "p_link_rss",
                    title = "RSS Group Feed",
                    url = "https://rssgroupfeed-jaelvwfd.manus.space",
                    iconName = "RssFeed",
                    accentColorHex = "#F59E0B",
                    x = 0f,
                    y = 2.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "p_link_yt",
                    title = "YouTube",
                    url = "https://www.youtube.com",
                    iconName = "Video",
                    accentColorHex = "#EF4444",
                    x = 1f,
                    y = 2.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "p_link_wiki",
                    title = "Wikipedia",
                    url = "https://en.wikipedia.org",
                    iconName = "Book",
                    accentColorHex = "#0284C7",
                    x = 2f,
                    y = 2.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "p_link_reddit",
                    title = "Reddit",
                    url = "https://www.reddit.com",
                    iconName = "Forum",
                    accentColorHex = "#F97316",
                    x = 3f,
                    y = 2.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "p_link_ddg",
                    title = "DuckDuckGo",
                    url = "https://duckduckgo.com",
                    iconName = "Search",
                    accentColorHex = "#DE5833",
                    x = 3f,
                    y = 2.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.NoteObject(
                    id = "p_note_welcome",
                    title = "Welcome Note",
                    content = "Welcome to your personal Environment Canvas. Tap 'Customize' to arrange widgets, change background, or add links!",
                    colorHex = "#F59E0B",
                    x = 0f,
                    y = 3.3f,
                    width = 2f,
                    height = 1.4f
                ),
                CanvasObject.WidgetObject(
                    id = "p_tools",
                    x = 2f,
                    y = 3.3f,
                    width = 2f,
                    height = 1.4f,
                    widgetType = CanvasWidgetType.QUICK_TOOLS
                )
            )
        )
    }

        fun createDefaultEnvironments(): List<Environment> {
        val personal = createDefaultPersonalEnvironment()

        val study = Environment(
            id = "study",
            name = "Study",
            iconName = "School",
            themeMode = "dark",
            background = EnvironmentBackground(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#120B24",
                secondaryColorHex = "#2E1065",
                accentColorHex = "#A855F7",
                presetId = "lavender"
            ),
            layoutMode = EnvironmentLayoutMode.GRID,
            objects = listOf(
                CanvasObject.WidgetObject(
                    id = "s_search",
                    x = 0f,
                    y = 0f,
                    width = 4f,
                    height = 1.0f,
                    widgetType = CanvasWidgetType.SEARCH
                ),
                CanvasObject.WidgetObject(
                    id = "s_cal",
                    x = 0f,
                    y = 1f,
                    width = 2f,
                    height = 1.6f,
                    widgetType = CanvasWidgetType.DATE_CALENDAR
                ),
                CanvasObject.WidgetObject(
                    id = "s_clock",
                    x = 2f,
                    y = 1f,
                    width = 2f,
                    height = 1.3f,
                    widgetType = CanvasWidgetType.CLOCK
                ),
                CanvasObject.FolderObject(
                    id = "s_folder_research",
                    title = "Research & Reference",
                    iconName = "Folder",
                    accentColorHex = "#8B5CF6",
                    x = 2f,
                    y = 2.3f,
                    width = 2f,
                    height = 1.3f,
                    items = listOf(
                        FolderItem(UUID.randomUUID().toString(), "Quanta Magazine", "https://www.quantamagazine.org", "AutoAwesome", "#8B5CF6"),
                        FolderItem(UUID.randomUUID().toString(), "Wikipedia", "https://en.wikipedia.org", "Book", "#0284C7"),
                        FolderItem(UUID.randomUUID().toString(), "arXiv", "https://arxiv.org", "School", "#10B981"),
                        FolderItem(UUID.randomUUID().toString(), "Stanford Encyclopedia", "https://plato.stanford.edu", "MenuBook", "#F59E0B")
                    )
                ),
                CanvasObject.NoteObject(
                    id = "s_note_targets",
                    title = "Study Objectives",
                    content = "• Review Quantum Geometry notes\n• Starling forces in physiology\n• Fusion energy milestones 2026",
                    colorHex = "#8B5CF6",
                    x = 0f,
                    y = 2.6f,
                    width = 2f,
                    height = 1.4f
                )
            )
        )

        val development = Environment(
            id = "development",
            name = "Development",
            iconName = "Code",
            themeMode = "dark",
            background = EnvironmentBackground(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#061A14",
                secondaryColorHex = "#042F2E",
                accentColorHex = "#10B981",
                presetId = "emerald"
            ),
            layoutMode = EnvironmentLayoutMode.GRID,
            objects = listOf(
                CanvasObject.WidgetObject(
                    id = "d_search",
                    x = 0f,
                    y = 0f,
                    width = 4f,
                    height = 1.0f,
                    widgetType = CanvasWidgetType.SEARCH
                ),
                CanvasObject.WidgetObject(
                    id = "d_sysinfo",
                    x = 0f,
                    y = 1f,
                    width = 2f,
                    height = 1.4f,
                    widgetType = CanvasWidgetType.SYSTEM_INFO
                ),
                CanvasObject.WidgetObject(
                    id = "d_clock",
                    x = 2f,
                    y = 1f,
                    width = 2f,
                    height = 1.3f,
                    widgetType = CanvasWidgetType.CLOCK
                ),
                CanvasObject.FolderObject(
                    id = "d_folder_hub",
                    title = "Dev Hub",
                    iconName = "Code",
                    accentColorHex = "#10B981",
                    x = 0f,
                    y = 2.4f,
                    width = 2f,
                    height = 1.4f,
                    items = listOf(
                        FolderItem(UUID.randomUUID().toString(), "GitHub", "https://github.com", "Code", "#10B981"),
                        FolderItem(UUID.randomUUID().toString(), "Stack Overflow", "https://stackoverflow.com", "Forum", "#F97316"),
                        FolderItem(UUID.randomUUID().toString(), "Hacker News", "https://news.ycombinator.com", "TrendingUp", "#FF6600"),
                        FolderItem(UUID.randomUUID().toString(), "MDN Web Docs", "https://developer.mozilla.org", "Devices", "#06B6D4")
                    )
                ),
                CanvasObject.NoteObject(
                    id = "d_note_checklist",
                    title = "Terminal Notes",
                    content = "git status\nCheck SOCKS5 Tor status: 127.0.0.1:9050\nVerify Kotlin coroutines flow",
                    colorHex = "#10B981",
                    x = 2f,
                    y = 2.3f,
                    width = 2f,
                    height = 1.5f
                ),
                CanvasObject.LinkObject(
                    id = "d_link_android",
                    title = "Android Docs",
                    url = "https://developer.android.com",
                    iconName = "Devices",
                    accentColorHex = "#3DDC84",
                    x = 0f,
                    y = 3.8f,
                    width = 2f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "d_link_kotlin",
                    title = "Kotlin Lang",
                    url = "https://kotlinlang.org",
                    iconName = "Code",
                    accentColorHex = "#7F52FF",
                    x = 2f,
                    y = 3.8f,
                    width = 2f,
                    height = 1f
                )
            )
        )

        val work = Environment(
            id = "work",
            name = "Work",
            iconName = "Work",
            themeMode = "dark",
            background = EnvironmentBackground(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#0F172A",
                secondaryColorHex = "#1E293B",
                accentColorHex = "#64748B",
                presetId = "slate"
            ),
            layoutMode = EnvironmentLayoutMode.GRID,
            objects = listOf(
                CanvasObject.WidgetObject(
                    id = "w_search",
                    x = 0f,
                    y = 0f,
                    width = 4f,
                    height = 1.0f,
                    widgetType = CanvasWidgetType.SEARCH
                ),
                CanvasObject.WidgetObject(
                    id = "w_clock",
                    x = 0f,
                    y = 1f,
                    width = 2f,
                    height = 1.3f,
                    widgetType = CanvasWidgetType.CLOCK
                ),
                CanvasObject.WidgetObject(
                    id = "w_cal",
                    x = 2f,
                    y = 1f,
                    width = 2f,
                    height = 1.5f,
                    widgetType = CanvasWidgetType.DATE_CALENDAR
                ),
                CanvasObject.LinkObject(
                    id = "w_link_google",
                    title = "Google",
                    url = "https://www.google.com",
                    iconName = "Search",
                    accentColorHex = "#4285F4",
                    x = 0f,
                    y = 2.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "w_link_bloomberg",
                    title = "Bloomberg",
                    url = "https://www.bloomberg.com",
                    iconName = "TrendingUp",
                    accentColorHex = "#10B981",
                    x = 1f,
                    y = 2.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.NoteObject(
                    id = "w_note_agenda",
                    title = "Daily Priorities",
                    content = "1. Sync with engineering\n2. Review UX architecture\n3. Finalize release notes",
                    colorHex = "#3B82F6",
                    x = 0f,
                    y = 3.3f,
                    width = 2f,
                    height = 1.4f
                ),
                CanvasObject.WidgetObject(
                    id = "w_tools",
                    x = 2f,
                    y = 2.5f,
                    width = 2f,
                    height = 1.4f,
                    widgetType = CanvasWidgetType.QUICK_TOOLS
                )
            )
        )

        val gaming = Environment(
            id = "gaming",
            name = "Gaming",
            iconName = "SportsEsports",
            themeMode = "dark",
            background = EnvironmentBackground(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#050811",
                secondaryColorHex = "#1E0D36",
                accentColorHex = "#EC4899",
                presetId = "midnight"
            ),
            layoutMode = EnvironmentLayoutMode.GRID,
            objects = listOf(
                CanvasObject.WidgetObject(
                    id = "g_clock",
                    x = 0f,
                    y = 0f,
                    width = 2f,
                    height = 1.3f,
                    widgetType = CanvasWidgetType.CLOCK
                ),
                CanvasObject.WidgetObject(
                    id = "g_sys",
                    x = 2f,
                    y = 0f,
                    width = 2f,
                    height = 1.3f,
                    widgetType = CanvasWidgetType.SYSTEM_INFO
                ),
                CanvasObject.LinkObject(
                    id = "g_link_twitch",
                    title = "Twitch",
                    url = "https://www.twitch.tv",
                    iconName = "Video",
                    accentColorHex = "#9146FF",
                    x = 0f,
                    y = 1.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "g_link_steam",
                    title = "Steam",
                    url = "https://store.steampowered.com",
                    iconName = "SportsEsports",
                    accentColorHex = "#171A21",
                    x = 1f,
                    y = 1.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "g_link_ytg",
                    title = "YouTube Gaming",
                    url = "https://www.youtube.com/gaming",
                    iconName = "Video",
                    accentColorHex = "#FF0000",
                    x = 2f,
                    y = 1.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.LinkObject(
                    id = "g_link_discord",
                    title = "Discord",
                    url = "https://discord.com",
                    iconName = "Forum",
                    accentColorHex = "#5865F2",
                    x = 3f,
                    y = 1.3f,
                    width = 1f,
                    height = 1f
                ),
                CanvasObject.WidgetObject(
                    id = "g_rss",
                    x = 0f,
                    y = 2.3f,
                    width = 4f,
                    height = 1.6f,
                    widgetType = CanvasWidgetType.RSS_FEED
                )
            )
        )

        val minimal = Environment(
            id = "minimal",
            name = "Zen Minimal",
            iconName = "Spa",
            themeMode = "dark",
            background = EnvironmentBackground(
                type = BackgroundType.GRADIENT,
                primaryColorHex = "#081325",
                secondaryColorHex = "#0C2A4D",
                accentColorHex = "#38BDF8",
                presetId = "ocean"
            ),
            layoutMode = EnvironmentLayoutMode.GRID,
            objects = emptyList() // Demonstrates requirement 10: pure empty canvas!
        )

        return listOf(personal, study, development, work, gaming, minimal)
    }
}
}
