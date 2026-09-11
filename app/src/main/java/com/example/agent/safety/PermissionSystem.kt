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

/**
 * Explicit policies for permission scopes and destructive operations.
 */
enum class PermissionPolicy {
    ALLOW,
    DENY,
    ASK
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

        // Scopes
        const val SCOPE_BROWSER = "browser.*"
        const val SCOPE_FILE = "file.*"
        const val SCOPE_FILESYSTEM = "filesystem.*"
        const val SCOPE_SHELL = "shell.*"
        const val SCOPE_NETWORK = "network.*"
        const val SCOPE_EXTERNAL = "external.*"

        val global = PermissionSystem()
    }

    // Granted permissions by domain/scope
    private val grantedPermissions = ConcurrentHashMap.newKeySet<String>()
    private val scopePolicies = ConcurrentHashMap<String, PermissionPolicy>()

    private val highRiskPermissions = setOf(
        PERM_FILESYSTEM_DELETE,
        "file.delete",
        PERM_SHELL_EXECUTE,
        "shell.execute",
        PERM_EXTERNAL_API,
        "external.execute"
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
        grantedPermissions.add("file.read")

        // Default policies for scopes
        scopePolicies[SCOPE_BROWSER] = PermissionPolicy.ALLOW
        scopePolicies[SCOPE_NETWORK] = PermissionPolicy.ALLOW
        scopePolicies[PERM_FILESYSTEM_READ] = PermissionPolicy.ALLOW
        scopePolicies["file.read"] = PermissionPolicy.ALLOW

        // Destructive actions default to ASK
        scopePolicies[PERM_FILESYSTEM_DELETE] = PermissionPolicy.ASK
        scopePolicies["file.delete"] = PermissionPolicy.ASK
        scopePolicies[PERM_FILESYSTEM_WRITE] = PermissionPolicy.ALLOW
        scopePolicies["file.write"] = PermissionPolicy.ALLOW
        scopePolicies[PERM_SHELL_EXECUTE] = PermissionPolicy.ASK
        scopePolicies["shell.execute"] = PermissionPolicy.ASK
        scopePolicies[SCOPE_EXTERNAL] = PermissionPolicy.ASK
    }

    fun canonicalPermission(permission: String): String {
        return permission.trim().lowercase()
            .replace("file:read", "file.read")
            .replace("file:write", "file.write")
            .replace("browser:read", PERM_BROWSER_READ)
            .replace("browser:navigate", PERM_BROWSER_NAVIGATE)
            .replace(":", ".")
    }

    fun setPolicy(scopeOrPermission: String, policy: PermissionPolicy) {
        val canonical = canonicalPermission(scopeOrPermission)
        scopePolicies[canonical] = policy
        logger.logInstant(
            agentName = "PermissionSystem",
            action = StepType.MODIFY,
            target = "Policy set for $canonical -> $policy",
            status = StepStatus.SUCCESS
        )
    }

    fun getPolicy(scopeOrPermission: String): PermissionPolicy {
        val canonical = canonicalPermission(scopeOrPermission)
        // Exact match
        scopePolicies[canonical]?.let { return it }

        // Wildcard match (e.g. file.* for file.delete)
        val prefix = canonical.substringBefore(".") + ".*"
        scopePolicies[prefix]?.let { return it }

        // Also check filesystem.* for file.* or vice-versa
        if (canonical.startsWith("filesystem.")) {
            val filePrefix = "file.*"
            scopePolicies[filePrefix]?.let { return it }
        } else if (canonical.startsWith("file.")) {
            val fsPrefix = "filesystem.*"
            scopePolicies[fsPrefix]?.let { return it }
        }

        return if (highRiskPermissions.contains(canonical)) PermissionPolicy.ASK else PermissionPolicy.DENY
    }

    fun hasPermission(permission: String): Boolean {
        val canonical = canonicalPermission(permission)
        val policy = getPolicy(canonical)
        if (policy == PermissionPolicy.DENY) return false
        if (policy == PermissionPolicy.ALLOW) return true

        return grantedPermissions.contains(canonical) ||
                grantedPermissions.contains(permission) ||
                (canonical.startsWith("file.") && grantedPermissions.contains(canonical.replace("file.", "filesystem."))) ||
                (canonical.startsWith("filesystem.") && grantedPermissions.contains(canonical.replace("filesystem.", "file.")))
    }

    fun checkPermission(
        permission: String,
        agentName: String = "unknown",
        isHeadless: Boolean = false
    ): PermissionCheckResult {
        val canonical = canonicalPermission(permission)
        val policy = getPolicy(canonical)

        when (policy) {
            PermissionPolicy.DENY -> {
                return PermissionCheckResult(
                    PermissionStatus.DENIED,
                    canonical,
                    "Permission '$canonical' is DENIED by policy for agent '$agentName'."
                )
            }
            PermissionPolicy.ALLOW -> {
                return PermissionCheckResult(PermissionStatus.GRANTED, canonical)
            }
            PermissionPolicy.ASK -> {
                if (hasPermission(canonical)) {
                    return PermissionCheckResult(PermissionStatus.GRANTED, canonical)
                }
                if (isHeadless) {
                    return PermissionCheckResult(
                        PermissionStatus.DENIED,
                        canonical,
                        "Destructive action requires confirmation (ASK policy), but runtime is headless."
                    )
                }
                return PermissionCheckResult(
                    PermissionStatus.REQUIRES_CONFIRMATION,
                    canonical,
                    "Permission '$canonical' requires explicit user confirmation."
                )
            }
        }
    }

    fun grantPermission(permission: String) {
        val canonical = canonicalPermission(permission)
        grantedPermissions.add(canonical)
        // Also add aliases
        if (canonical.startsWith("file.")) {
            grantedPermissions.add(canonical.replace("file.", "filesystem."))
        }
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
        if (canonical.startsWith("file.")) {
            grantedPermissions.remove(canonical.replace("file.", "filesystem."))
        }
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


