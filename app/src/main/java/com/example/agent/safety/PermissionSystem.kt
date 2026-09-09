package com.example.agent.safety

import com.example.agent.core.AgentRequest
import com.example.agent.core.StepLogger
import com.example.agent.core.StepStatus
import com.example.agent.core.StepType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

data class PermissionRequest(
    val id: String = UUID.randomUUID().toString(),
    val agentName: String,
    val permission: String,
    val description: String,
    val risk: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PermissionSystem(
    private val logger: StepLogger = StepLogger.global
) {
    // Granted permissions by domain/scope
    private val grantedPermissions = mutableSetOf<String>()

    private val _pendingRequests = MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 32)
    val pendingRequests: SharedFlow<PermissionRequest> = _pendingRequests.asSharedFlow()

    init {
        // Safe default permissions
        grantedPermissions.add("browser:read")
        grantedPermissions.add("browser:search")
        grantedPermissions.add("file:read")
    }

    fun hasPermission(permission: String): Boolean {
        return grantedPermissions.contains(permission)
    }

    fun grantPermission(permission: String) {
        grantedPermissions.add(permission)
        logger.logInstant(
            agentName = "PermissionSystem",
            action = StepType.VALIDATE,
            target = "Permission granted: $permission",
            status = StepStatus.SUCCESS
        )
    }

    fun revokePermission(permission: String) {
        grantedPermissions.remove(permission)
        logger.logInstant(
            agentName = "PermissionSystem",
            action = StepType.MODIFY,
            target = "Permission revoked: $permission",
            status = StepStatus.SUCCESS
        )
    }

    /**
     * Checks whether an incoming AgentRequest has required permissions.
     */
    fun checkRequestPermissions(request: AgentRequest): Boolean {
        if (request.permissions.isEmpty()) return true
        for (perm in request.permissions) {
            if (!hasPermission(perm)) {
                logger.logInstant(
                    agentName = "PermissionSystem",
                    action = StepType.VALIDATE,
                    target = "Permission DENIED for ${request.sourceAgent} -> ${request.targetAgent} [$perm]",
                    status = StepStatus.FAILED,
                    errorMessage = "Missing permission: $perm"
                )
                return false
            }
        }
        return true
    }

    suspend fun requestUserPermission(agentName: String, permission: String, description: String) {
        val req = PermissionRequest(
            agentName = agentName,
            permission = permission,
            description = description,
            risk = "HIGH"
        )
        _pendingRequests.emit(req)
    }

    companion object {
        val global = PermissionSystem()
    }
}
