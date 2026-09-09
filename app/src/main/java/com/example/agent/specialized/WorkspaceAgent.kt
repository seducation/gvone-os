package com.example.agent.specialized

import com.example.agent.core.*
import com.example.data.environment.EnvironmentManager

/**
 * WorkspaceAgent coordinates environments, canvas widgets, and tab grouping workspaces.
 */
class WorkspaceAgent(
    private val environmentManager: EnvironmentManager,
    logger: StepLogger = StepLogger.global
) : AgentBase(name = "WorkspaceAgent", logger = logger) {

    override fun capabilities(): List<AgentCapability> = listOf(
        AgentCapability(
            name = "workspace_management",
            description = "Manage start page canvas environments and productivity presets",
            supportedActions = listOf("list_environments", "get_active_environment", "switch_environment"),
            riskLevel = RiskLevel.LOW
        )
    )

    override fun observe(): Map<String, Any?> = mapOf(
        "totalEnvironments" to environmentManager.environments.value.size,
        "activeEnvironment" to (environmentManager.currentEnvironment.value?.name ?: "none")
    )

    override suspend fun onExecute(request: AgentRequest, token: CancellationToken): AgentResult {
        val startTime = System.currentTimeMillis()

        return when (request.action) {
            "list_environments" -> {
                executeAction(StepType.FETCH, "Query available workspace environments") {
                    val list = environmentManager.environments.value.map {
                        mapOf("id" to it.id, "name" to it.name, "theme" to it.themeMode)
                    }
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = list,
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "get_active_environment" -> {
                executeAction(StepType.FETCH, "Get current workspace environment") {
                    val current = environmentManager.currentEnvironment.value
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = current?.let { mapOf("id" to it.id, "name" to it.name) },
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            "switch_environment" -> {
                val id = request.parameters["id"] as? String ?: return AgentResult(
                    requestId = request.requestId,
                    status = AgentStatus.FAILED,
                    error = "Missing 'id' parameter"
                )
                executeAction(StepType.MODIFY, "Switch to environment: $id") {
                    environmentManager.switchEnvironment(id)
                    AgentResult(
                        requestId = request.requestId,
                        status = AgentStatus.COMPLETED,
                        data = mapOf("switchedTo" to id),
                        durationMs = System.currentTimeMillis() - startTime
                    )
                }
            }

            else -> {
                AgentResult(
                    requestId = request.requestId,
                    status = AgentStatus.FAILED,
                    error = "Unsupported action '${request.action}' for WorkspaceAgent"
                )
            }
        }
    }
}
