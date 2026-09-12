package com.example.agent.safety

import com.example.agent.core.AgentRequest
import com.example.agent.core.StepLogger
import com.example.agent.core.StepStatus
import com.example.agent.core.StepType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PermissionDescriptor(
    val key: String,
    val title: String,
    val category: String,
    val description: String,
    val riskLevel: String // "LOW", "HIGH", "CRITICAL"
)

enum class PermissionPolicy {
    ALLOW,
    ASK,
    DENY
}

data class PermissionAuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val caller: String,
    val permission: String,
    val action: String,
    val outcome: String,
    val details: String? = null
)

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

        val ALL_DESCRIPTORS: List<PermissionDescriptor> = listOf(
            PermissionDescriptor(
                key = PERM_FILESYSTEM_READ,
                title = "Filesystem Read",
                category = "Filesystem",
                description = "Read files and inspect directories within the local workspace.",
                riskLevel = "LOW"
            ),
            PermissionDescriptor(
                key = PERM_FILESYSTEM_WRITE,
                title = "Filesystem Write",
                category = "Filesystem",
                description = "Create, modify, or overwrite files within the local workspace.",
                riskLevel = "HIGH"
            ),
            PermissionDescriptor(
                key = PERM_FILESYSTEM_DELETE,
                title = "Filesystem Delete",
                category = "Filesystem",
                description = "Permanently delete files or directories in the workspace.",
                riskLevel = "CRITICAL"
            ),
            PermissionDescriptor(
                key = PERM_BROWSER_NAVIGATE,
                title = "Browser Navigation",
                category = "Browser",
                description = "Navigate browser tabs to web URLs and domains.",
                riskLevel = "LOW"
            ),
            PermissionDescriptor(
                key = PERM_BROWSER_READ,
                title = "Browser Read",
                category = "Browser",
                description = "Read web page contents, DOM, and text previews.",
                riskLevel = "LOW"
            ),
            PermissionDescriptor(
                key = PERM_BROWSER_INTERACT,
                title = "Browser Interaction",
                category = "Browser",
                description = "Click links, type input, and interact with loaded web pages.",
                riskLevel = "LOW"
            ),
            PermissionDescriptor(
                key = PERM_SHELL_EXECUTE,
                title = "Shell Execution",
                category = "System & Shell",
                description = "Execute shell commands and scripts in the host terminal.",
                riskLevel = "CRITICAL"
            ),
            PermissionDescriptor(
                key = PERM_GIT_READ,
                title = "Version Control Read",
                category = "Version Control",
                description = "Read Git status, branch log, and repository history.",
                riskLevel = "LOW"
            ),
            PermissionDescriptor(
                key = PERM_GIT_WRITE,
                title = "Version Control Write",
                category = "Version Control",
                description = "Commit changes, create branches, or push to git remotes.",
                riskLevel = "HIGH"
            ),
            PermissionDescriptor(
                key = PERM_NETWORK_REQUEST,
                title = "Network Request",
                category = "Network",
                description = "Perform HTTP/HTTPS network requests and API calls.",
                riskLevel = "LOW"
            ),
            PermissionDescriptor(
                key = PERM_EXTERNAL_API,
                title = "External API Integration",
                category = "Integrations",
                description = "Invoke third-party cloud APIs, webhooks, and external services.",
                riskLevel = "CRITICAL"
            )
        )

        val global = PermissionSystem()
    }

    // Granted permissions by domain/scope
    private val grantedPermissions = ConcurrentHashMap.newKeySet<String>()
    private val highRiskPermissions = setOf(
        PERM_FILESYSTEM_DELETE,
        PERM_SHELL_EXECUTE,
        PERM_EXTERNAL_API
    )

    private val _grantedPermissionsFlow = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissionsFlow: StateFlow<Set<String>> = _grantedPermissionsFlow.asStateFlow()

    private val _scopePoliciesFlow = MutableStateFlow<Map<String, PermissionPolicy>>(emptyMap())
    val scopePoliciesFlow: StateFlow<Map<String, PermissionPolicy>> = _scopePoliciesFlow.asStateFlow()

    private val _auditLogFlow = MutableStateFlow<List<PermissionAuditEvent>>(emptyList())
    val auditLogFlow: StateFlow<List<PermissionAuditEvent>> = _auditLogFlow.asStateFlow()

    private val _activePromptFlow = MutableStateFlow<PermissionRequest?>(null)
    val activePromptFlow: StateFlow<PermissionRequest?> = _activePromptFlow.asStateFlow()

    private val _pendingRequests = MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 32)
    val pendingRequests: SharedFlow<PermissionRequest> = _pendingRequests.asSharedFlow()

    init {
        initDefaults()
    }

    private fun initDefaults() {
        grantedPermissions.clear()
        val defaultGrants = setOf(
            PERM_FILESYSTEM_READ,
            PERM_BROWSER_NAVIGATE,
            PERM_BROWSER_READ,
            PERM_BROWSER_INTERACT,
            PERM_GIT_READ,
            PERM_NETWORK_REQUEST,
            "browser:read",
            "browser:search",
            "file:read"
        )
        grantedPermissions.addAll(defaultGrants)
        _grantedPermissionsFlow.value = grantedPermissions.toSet()

        val defaultPolicies = mutableMapOf<String, PermissionPolicy>(
            PERM_FILESYSTEM_READ to PermissionPolicy.ALLOW,
            PERM_BROWSER_NAVIGATE to PermissionPolicy.ALLOW,
            PERM_BROWSER_READ to PermissionPolicy.ALLOW,
            PERM_BROWSER_INTERACT to PermissionPolicy.ALLOW,
            PERM_GIT_READ to PermissionPolicy.ALLOW,
            PERM_NETWORK_REQUEST to PermissionPolicy.ALLOW,
            "browser:read" to PermissionPolicy.ALLOW,
            "browser:search" to PermissionPolicy.ALLOW,
            "file:read" to PermissionPolicy.ALLOW,
            PERM_FILESYSTEM_WRITE to PermissionPolicy.ASK,
            PERM_FILESYSTEM_DELETE to PermissionPolicy.ASK,
            PERM_SHELL_EXECUTE to PermissionPolicy.ASK,
            PERM_GIT_WRITE to PermissionPolicy.ASK,
            PERM_EXTERNAL_API to PermissionPolicy.ASK
        )
        _scopePoliciesFlow.value = defaultPolicies
    }

    private fun logAudit(
        caller: String,
        permission: String,
        action: String,
        outcome: String,
        details: String? = null
    ) {
        val event = PermissionAuditEvent(
            caller = caller,
            permission = permission,
            action = action,
            outcome = outcome,
            details = details
        )
        val current = _auditLogFlow.value.toMutableList()
        current.add(0, event)
        _auditLogFlow.value = if (current.size > 200) current.take(200) else current
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

    fun getGrantedPermissions(): Set<String> = grantedPermissions.toSet()

    fun getPolicy(permission: String): PermissionPolicy {
        val canonical = canonicalPermission(permission)
        val policies = _scopePoliciesFlow.value
        policies[canonical]?.let { return it }
        policies[permission]?.let { return it }

        // Match wildcard policies (e.g. "system.*" or "filesystem.*")
        for ((pattern, pol) in policies) {
            if (pattern.endsWith(".*")) {
                val prefix = pattern.removeSuffix(".*")
                if (canonical.startsWith(prefix) || permission.startsWith(prefix)) {
                    return pol
                }
            } else if (pattern.endsWith("*")) {
                val prefix = pattern.removeSuffix("*")
                if (canonical.startsWith(prefix) || permission.startsWith(prefix)) {
                    return pol
                }
            }
        }

        return if (highRiskPermissions.contains(canonical)) {
            PermissionPolicy.ASK
        } else {
            PermissionPolicy.ALLOW
        }
    }

    fun setPolicy(permission: String, policy: PermissionPolicy) {
        val canonical = canonicalPermission(permission)
        val current = _scopePoliciesFlow.value.toMutableMap()
        current[canonical] = policy
        if (canonical != permission) {
            current[permission] = policy
        }
        _scopePoliciesFlow.value = current
        if (policy == PermissionPolicy.ALLOW) {
            grantPermission(canonical)
        } else if (policy == PermissionPolicy.DENY) {
            revokePermission(canonical)
        }
        logAudit(caller = "System", permission = canonical, action = "SET_POLICY", outcome = policy.name)
    }

    fun checkPermission(
        permission: String,
        agentName: String = "unknown",
        isHeadless: Boolean = false
    ): PermissionCheckResult {
        val canonical = canonicalPermission(permission)
        val policy = getPolicy(canonical)

        if (policy == PermissionPolicy.DENY) {
            logAudit(caller = agentName, permission = canonical, action = "CHECK", outcome = "DENIED", details = "Policy is DENY")
            return PermissionCheckResult(
                status = PermissionStatus.DENIED,
                permission = canonical,
                reason = "Permission '$canonical' has not been granted or is DENIED by policy."
            )
        }

        if (policy == PermissionPolicy.ALLOW) {
            if (!hasPermission(canonical)) {
                grantPermission(canonical)
            }
            logAudit(caller = agentName, permission = canonical, action = "CHECK", outcome = "GRANTED")
            return PermissionCheckResult(PermissionStatus.GRANTED, canonical)
        }

        // policy == PermissionPolicy.ASK
        if (hasPermission(canonical)) {
            logAudit(caller = agentName, permission = canonical, action = "CHECK", outcome = "GRANTED")
            return PermissionCheckResult(PermissionStatus.GRANTED, canonical)
        }

        if (isHeadless) {
            logAudit(caller = agentName, permission = canonical, action = "CHECK", outcome = "DENIED (HEADLESS)")
            return PermissionCheckResult(
                status = PermissionStatus.DENIED,
                permission = canonical,
                reason = "Permission '$canonical' requires explicit user confirmation (denied in headless mode)."
            )
        }

        val req = PermissionRequest(
            agentName = agentName,
            permission = canonical,
            description = "Agent '$agentName' requested permission '$canonical'.",
            risk = if (highRiskPermissions.contains(canonical)) "CRITICAL" else "HIGH"
        )
        _activePromptFlow.value = req
        _pendingRequests.tryEmit(req)
        logAudit(caller = agentName, permission = canonical, action = "CHECK", outcome = "PROMPT")
        return PermissionCheckResult(
            status = PermissionStatus.REQUIRES_CONFIRMATION,
            permission = canonical,
            reason = "Permission '$canonical' requires explicit user confirmation."
        )
    }

    fun grantPermission(permission: String) {
        val canonical = canonicalPermission(permission)
        grantedPermissions.add(canonical)
        _grantedPermissionsFlow.value = grantedPermissions.toSet()
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
        _grantedPermissionsFlow.value = grantedPermissions.toSet()
        logger.logInstant(
            agentName = "PermissionSystem",
            action = StepType.MODIFY,
            target = "Permission revoked: $canonical",
            status = StepStatus.SUCCESS
        )
    }

    fun revokeAll() {
        grantedPermissions.clear()
        _grantedPermissionsFlow.value = emptySet()
        logAudit(caller = "User", permission = "*", action = "REVOKE_ALL", outcome = "REVOKED")
    }

    fun resetToDefaults() {
        initDefaults()
        logAudit(caller = "User", permission = "*", action = "RESET_DEFAULTS", outcome = "RESET")
    }

    fun clearAuditLogs() {
        _auditLogFlow.value = emptyList()
    }

    fun triggerTestPrompt(
        permission: String,
        agentName: String = "TestAgent",
        description: String = "Permission test request"
    ) {
        val canonical = canonicalPermission(permission)
        val req = PermissionRequest(
            agentName = agentName,
            permission = canonical,
            description = description,
            risk = if (highRiskPermissions.contains(canonical)) "CRITICAL" else "HIGH"
        )
        _activePromptFlow.value = req
        _pendingRequests.tryEmit(req)
        logAudit(caller = agentName, permission = canonical, action = "TEST_PROMPT", outcome = "PROMPT")
    }

    fun respondToRequest(requestId: String, approved: Boolean, rememberPolicy: Boolean) {
        val currentPrompt = _activePromptFlow.value
        val targetPermission = if (currentPrompt?.id == requestId) currentPrompt.permission else null
        _activePromptFlow.value = null

        if (targetPermission != null) {
            if (approved) {
                grantPermission(targetPermission)
                if (rememberPolicy) {
                    setPolicy(targetPermission, PermissionPolicy.ALLOW)
                }
                logAudit(caller = "User", permission = targetPermission, action = "RESPOND_PROMPT", outcome = "APPROVED")
            } else {
                revokePermission(targetPermission)
                if (rememberPolicy) {
                    setPolicy(targetPermission, PermissionPolicy.DENY)
                }
                logAudit(caller = "User", permission = targetPermission, action = "RESPOND_PROMPT", outcome = "DENIED")
            }
        }
    }

    fun dismissPendingRequest(requestId: String) {
        if (_activePromptFlow.value?.id == requestId) {
            val perm = _activePromptFlow.value?.permission ?: "unknown"
            _activePromptFlow.value = null
            logAudit(caller = "User", permission = perm, action = "DISMISS", outcome = "DISMISSED")
        }
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
        _activePromptFlow.value = req
        _pendingRequests.emit(req)
        logAudit(caller = agentName, permission = canonical, action = "REQUEST", outcome = "PROMPT", details = description)
    }
}
