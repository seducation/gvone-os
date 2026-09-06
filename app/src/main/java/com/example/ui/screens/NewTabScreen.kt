package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.environment.EnvironmentManager
import com.example.data.model.CanvasObject
import com.example.data.model.Environment
import com.example.ui.screens.canvas.EnvironmentStartPageCanvas

data class ShortcutItem(
    val title: String,
    val url: String,
    val iconVector: ImageVector,
    val accentColor: Color
)

@Composable
fun NewTabScreen(
    isPrivate: Boolean,
    isTorActive: Boolean,
    onNavigate: (String) -> Unit,
    onAskAI: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultEnvs = remember { EnvironmentManager.createDefaultEnvironments() }
    var envs by remember { mutableStateOf(defaultEnvs) }
    var currentEnvId by remember { mutableStateOf(defaultEnvs.first().id) }
    val currentEnv = envs.find { it.id == currentEnvId } ?: envs.first()
    var isEditMode by remember { mutableStateOf(false) }

    EnvironmentStartPageCanvas(
        environment = currentEnv,
        allEnvironments = envs,
        isPrivate = isPrivate,
        isTorActive = isTorActive,
        isEditMode = isEditMode,
        onToggleEditMode = { isEditMode = !isEditMode },
        onSelectEnvironment = { id ->
            currentEnvId = id
        },
        onCreateEnvironment = { name, icon, theme, preset, initialLinkUrl, initialLinkTitle ->
            val startingObjects = if (!initialLinkUrl.isNullOrBlank()) {
                listOf(
                    CanvasObject.LinkObject(
                        id = java.util.UUID.randomUUID().toString(),
                        title = initialLinkTitle?.ifBlank { "Link" } ?: "Link",
                        url = initialLinkUrl,
                        iconName = "Globe",
                        accentColorHex = "#38BDF8",
                        x = 0f,
                        y = 0f,
                        width = 1f,
                        height = 1f
                    )
                )
            } else emptyList<CanvasObject>()

            val newEnv = Environment(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                iconName = icon,
                themeMode = theme,
                background = com.example.data.model.EnvironmentBackground.fromPreset(preset),
                objects = startingObjects
            )
            envs = envs + newEnv
            currentEnvId = newEnv.id
        },
        onDuplicateEnvironment = { id ->
            val target = envs.find { it.id == id } ?: return@EnvironmentStartPageCanvas
            val dup = target.copy(
                id = java.util.UUID.randomUUID().toString(),
                name = "${target.name} Copy"
            )
            envs = envs + dup
            currentEnvId = dup.id
        },
        onDeleteEnvironment = { id ->
            if (envs.size > 1) {
                envs = envs.filter { it.id != id }
                currentEnvId = envs.first().id
            }
        },
        onNavigate = onNavigate,
        onAddObject = { obj ->
            envs = envs.map { env ->
                if (env.id == currentEnv.id) env.copy(objects = env.objects + obj) else env
            }
        },
        onUpdateObject = { obj ->
            envs = envs.map { env ->
                if (env.id == currentEnv.id) env.copy(objects = env.objects.map { if (it.id == obj.id) obj else it }) else env
            }
        },
        onDeleteObject = { objId ->
            envs = envs.map { env ->
                if (env.id == currentEnv.id) env.copy(objects = env.objects.filter { it.id != objId }) else env
            }
        },
        onReorderObjects = { newObjs ->
            envs = envs.map { env ->
                if (env.id == currentEnv.id) env.copy(objects = newObjs) else env
            }
        },
        onUpdateBackground = { bg ->
            envs = envs.map { env ->
                if (env.id == currentEnv.id) env.copy(background = bg) else env
            }
        },
        onUpdateLayoutMode = { mode ->
            envs = envs.map { env ->
                if (env.id == currentEnv.id) env.copy(layoutMode = mode) else env
            }
        },
        modifier = modifier
    )
}
