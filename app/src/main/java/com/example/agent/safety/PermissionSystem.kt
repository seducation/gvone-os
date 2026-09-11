package com.example.agent.safety

import com.example.agent.core.AgentRequest
import com.example.agent.core.RiskLevel
import com.example.agent.core.StepLogger
import com.example.agent.core.StepStatus
import com.example.agent.core.StepType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PermissionRequest(
    val id: String = UUID.randomUUID().toString(),
    val agentName: String,
    val permission: String,
    val description: String,
    val risk: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class PermissionStatus {
    GRANTED,
    DENIED,
    REQUIRES_CONFIRMATION
}

data class PermissionCheckResult(
    val status: PermissionStatus,
    val permission: String,
    val reason: String? = null
)

class PermissionSystem(
    private val logger: StepLogger = StepLogger.global
) {
    // Standard system permissions
    companion object {
        const val PERM_FILESYSTEM_READ = "filesystem.read"
        const val PERM_FILESYSTEM_WRITE = "filesystem.write"
        const val PERM_FILESYSTEM_DELETE = "filesystem.delete"
        const val PERM_BROWSER_NAVIGATE = "browser.navigate"
        const val PERM_BROWSER_READ = "browser.read"
        const val PERM_BROWSER_INTERACT = "browser.interact"
        const val PERM_SHELL_EXECUTE = "shell.execute"
        const val PERM_GIT_READ = "git.read"
        const val PERM_GIT_WRITE = "git.write"
        const val PERM_NETWORK_REQUEST = "network.request"
        const val PERM_EXTERNAL_API = "external_api.execute"

        val global = PermissionSystem()
    }

    // Granted permissions by domain/scope
    private val grantedPermissions = ConcurrentHashMap.newKeySet<String>()
    private val highRiskPermissions = setOf(
        PERM_FILESYSTEM_DELETE,
        PERM_SHELL_EXECUTE,
        PERM_EXTERNAL_API
    )

    private val _pendingRequests = MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 32)
    val pendingRequests: SharedFlow<PermissionRequest> = _pendingRequests.asSharedFlow()

    init {
        // Safe default permissions
        grantedPermissions.add(PERM_FILESYSTEM_READ)
        grantedPermissions.add(PERM_BROWSER_NAVIGATE)
        grantedPermissions.add(PERM_BROWSER_READ)
        grantedPermissions.add(PERM_BROWSER_INTERACT)
        grantedPermissions.add(PERM_GIT_READ)
        grantedPermissions.add(PERM_NETWORK_REQUEST)
        // Also add legacy aliases
        grantedPermissions.add("browser:read")
        grantedPermissions.add("browser:search")
        grantedPermissions.add("file:read")
    }

    fun canonicalPermission(permission: String): String {
        return permission.trim().lowercase()
            .replace("file:read", PERM_FILESYSTEM_READ)
            .replace("file:write", PERM_FILESYSTEM_WRITE)
            .replace("browser:read", PERM_BROWSER_READ)
            .replace("browser:navigate", PERM_BROWSER_NAVIGATE)
            .replace(":", ".")
    }

    fun hasPermission(permission: String): Boolean {
        val canonical = canonicalPermission(permission)
        return grantedPermissions.contains(canonical) || grantedPermissions.contains(permission)
    }

    fun checkPermission(permission: String, agentName: String = "unknown"): PermissionCheckResult {
        val canonical = canonicalPermission(permission)
        if (hasPermission(canonical)) {
            return PermissionCheckResult(PermissionStatus.GRANTED, canonical)
        }
        if (highRiskPermissions.contains(canonical)) {
            return PermissionCheckResult(
                PermissionStatus.REQUIRES_CONFIRMATION,
                canonical,
                "Permission '$canonical' requires explicit user confirmation."
            )
        }
        return PermissionCheckResult(
            PermissionStatus.DENIED,
            canonical,
            "Permission '$canonical' has not been granted to agent '$agentName'."
        )
    }

    fun grantPermission(permission: String) {
        val canonical = canonicalPermission(permission)
        grantedPermissions.add(canonical)
        logger.logInstant(
            agentName = "PermissionSystem",
            action = StepType.VALIDATE,
            target = "Permission granted: $canonical",
            status = StepStatus.SUCCESS
        )
    }

    fun revokePermission(permission: String) {
        val canonical = canonicalPermission(permission)
        grantedPermissions.remove(canonical)
        logger.logInstant(
            agentName = "PermissionSystem",
            action = StepType.MODIFY,
            target = "Permission revoked: $canonical",
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
        val canonical = canonicalPermission(permission)
        val req = PermissionRequest(
            agentName = agentName,
            permission = canonical,
            description = description,
            risk = if (highRiskPermissions.contains(canonical)) "CRITICAL" else "HIGH"
        )
        _pendingRequests.emit(req)
    }
}

