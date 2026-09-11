package com.example.agent.safety

import com.example.agent.core.AgentRequest
import com.example.agent.core.RiskLevel
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

data class PermissionRequest(
    val id: String = UUID.randomUUID().toString(),
    val agentName: String,
    val permission: String,
    val description: String,
    val risk: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class PermissionDescriptor(
    val key: String,
    val title: String,
    val category: String,
    val description: String,
    val riskLevel: String, // "LOW", "HIGH", "CRITICAL"
    val defaultPolicy: PermissionPolicy
)

data class PermissionAuditEvent(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val permission: String,
    val caller: String,
    val action: String,
    val outcome: String
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

        val ALL_DESCRIPTORS = listOf(
            PermissionDescriptor(
                key = PERM_FILESYSTEM_READ,
                title = "Filesystem Read",
                category = "Filesystem",
                description = "Read files, sandbox workspaces, and inspect directory hierarchies.",
                riskLevel = "LOW",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_FILESYSTEM_WRITE,
                title = "Filesystem Write",
                category = "Filesystem",
                description = "Create, modify, append, and edit files within the sandbox filesystem.",
                riskLevel = "HIGH",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_FILESYSTEM_DELETE,
                title = "Filesystem Delete",
                category = "Filesystem",
                description = "Permanently remove files and purge directories within the filesystem.",
                riskLevel = "CRITICAL",
                defaultPolicy = PermissionPolicy.ASK
            ),
            PermissionDescriptor(
                key = PERM_BROWSER_NAVIGATE,
                title = "Browser Navigation",
                category = "Browser",
                description = "Navigate web views to new domains, URLs, and follow link redirects.",
                riskLevel = "LOW",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_BROWSER_READ,
                title = "Browser DOM Read",
                category = "Browser",
                description = "Inspect web page DOM structure, read text content, headings, and metadata.",
                riskLevel = "LOW",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_BROWSER_INTERACT,
                title = "Browser Interaction",
                category = "Browser",
                description = "Simulate user clicks, fill out input fields, and submit web forms.",
                riskLevel = "HIGH",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_SHELL_EXECUTE,
                title = "Shell Execution",
                category = "System & Shell",
                description = "Execute command-line scripts, shell utilities, and terminal subprocesses.",
                riskLevel = "CRITICAL",
                defaultPolicy = PermissionPolicy.ASK
            ),
            PermissionDescriptor(
                key = PERM_GIT_READ,
                title = "Git Repository Read",
                category = "Version Control",
                description = "Read Git status, branch commits, diffs, and repository history.",
                riskLevel = "LOW",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_GIT_WRITE,
                title = "Git Commit & Branch",
                category = "Version Control",
                description = "Stage files, create git commits, switch branches, and write repository state.",
                riskLevel = "HIGH",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_NETWORK_REQUEST,
                title = "Network & Tor Requests",
                category = "Network",
                description = "Send outbound HTTP/SOCKS5 requests, API calls, and onion-routed packets.",
                riskLevel = "HIGH",
                defaultPolicy = PermissionPolicy.ALLOW
            ),
            PermissionDescriptor(
                key = PERM_EXTERNAL_API,
                title = "External API Calls",
                category = "Integrations",
                description = "Dispatch requests to external cloud services, remote LLM endpoints, and webhooks.",
                riskLevel = "CRITICAL",
                defaultPolicy = PermissionPolicy.ASK
            )
        )

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

    private val _grantedPermissionsFlow = MutableStateFlow<Set<String>>(emptySet())
    val grantedPermissionsFlow: StateFlow<Set<String>> = _grantedPermissionsFlow.asStateFlow()

    private val _scopePoliciesFlow = MutableStateFlow<Map<String, PermissionPolicy>>(emptyMap())
    val scopePoliciesFlow: StateFlow<Map<String, PermissionPolicy>> = _scopePoliciesFlow.asStateFlow()

    private val _activePrompt = MutableStateFlow<PermissionRequest?>(null)
    val activePromptFlow: StateFlow<PermissionRequest?> = _activePrompt.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<PermissionAuditEvent>>(emptyList())
    val auditLogFlow: StateFlow<List<PermissionAuditEvent>> = _auditLogs.asStateFlow()

    private fun syncFlows() {
        _grantedPermissionsFlow.value = grantedPermissions.toSet()
        _scopePoliciesFlow.value = HashMap(scopePolicies)
    }

    private fun recordAuditEvent(permission: String, caller: String, action: String, outcome: String) {
        val event = PermissionAuditEvent(
            permission = canonicalPermission(permission),
            caller = caller,
            action = action,
            outcome = outcome
        )
        val current = _auditLogs.value.toMutableList()
        current.add(0, event)
        if (current.size > 100) {
            _auditLogs.value = current.take(100)
        } else {
            _auditLogs.value = current
        }
    }

    init {
        initDefaults()
    }

    private fun initDefaults() {
        grantedPermissions.clear()
        scopePolicies.clear()

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

        syncFlows()
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
        syncFlows()
        recordAuditEvent(canonical, "UserOrSystem", "SET_POLICY", policy.name)
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
                recordAuditEvent(canonical, agentName, "CHECK_PERMISSION", "DENIED")
                return PermissionCheckResult(
                    PermissionStatus.DENIED,
                    canonical,
                    "Permission '$canonical' is DENIED by policy for agent '$agentName'."
                )
            }
            PermissionPolicy.ALLOW -> {
                recordAuditEvent(canonical, agentName, "CHECK_PERMISSION", "ALLOWED")
                return PermissionCheckResult(PermissionStatus.GRANTED, canonical)
            }
            PermissionPolicy.ASK -> {
                if (hasPermission(canonical)) {
                    recordAuditEvent(canonical, agentName, "CHECK_PERMISSION", "GRANTED_PREVIOUSLY")
                    return PermissionCheckResult(PermissionStatus.GRANTED, canonical)
                }
                if (isHeadless) {
                    recordAuditEvent(canonical, agentName, "CHECK_PERMISSION", "DENIED_HEADLESS")
                    return PermissionCheckResult(
                        PermissionStatus.DENIED,
                        canonical,
                        "Destructive action requires confirmation (ASK policy), but runtime is headless."
                    )
                }
                recordAuditEvent(canonical, agentName, "CHECK_PERMISSION", "PROMPT_REQUIRED")
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
        syncFlows()
        recordAuditEvent(canonical, "User", "GRANT_PERMISSION", "SUCCESS")
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
        syncFlows()
        recordAuditEvent(canonical, "User", "REVOKE_PERMISSION", "SUCCESS")
        logger.logInstant(
            agentName = "PermissionSystem",
            action = StepType.MODIFY,
            target = "Permission revoked: $canonical",
            status = StepStatus.SUCCESS
        )
    }

    fun revokeAll() {
        grantedPermissions.clear()
        syncFlows()
        recordAuditEvent("*", "User", "REVOKE_ALL", "SUCCESS")
    }

    fun resetToDefaults() {
        initDefaults()
        recordAuditEvent("*", "User", "RESET_DEFAULTS", "SUCCESS")
    }

    fun clearAuditLogs() {
        _auditLogs.value = emptyList()
    }

    /**
     * Responds to an interactive UI permission prompt.
     */
    fun respondToRequest(requestId: String, approved: Boolean, rememberPolicy: Boolean = false) {
        val currentPrompt = _activePrompt.value
        val perm = currentPrompt?.permission
        val agent = currentPrompt?.agentName ?: "UserPrompt"

        if (approved && perm != null) {
            grantPermission(perm)
            if (rememberPolicy) {
                setPolicy(perm, PermissionPolicy.ALLOW)
            }
            recordAuditEvent(perm, agent, "USER_PROMPT_RESPONSE", if (rememberPolicy) "APPROVED_ALWAYS" else "APPROVED_ONCE")
        } else if (!approved && perm != null) {
            if (rememberPolicy) {
                setPolicy(perm, PermissionPolicy.DENY)
            }
            recordAuditEvent(perm, agent, "USER_PROMPT_RESPONSE", if (rememberPolicy) "DENIED_PERMANENTLY" else "DENIED_ONCE")
        }

        if (_activePrompt.value?.id == requestId || requestId.isBlank()) {
            _activePrompt.value = null
        }
    }

    fun dismissPendingRequest(requestId: String) {
        if (_activePrompt.value?.id == requestId || requestId.isBlank()) {
            _activePrompt.value = null
        }
    }

    fun triggerTestPrompt(
        permission: String = PERM_FILESYSTEM_DELETE,
        agentName: String = "FileAgent",
        description: String = "Requested deletion of sandbox directory /workspace/temp_build/"
    ) {
        val canonical = canonicalPermission(permission)
        val req = PermissionRequest(
            agentName = agentName,
            permission = canonical,
            description = description,
            risk = if (highRiskPermissions.contains(canonical)) "CRITICAL" else "HIGH"
        )
        _activePrompt.value = req
        _pendingRequests.tryEmit(req)
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
        _activePrompt.value = req
        _pendingRequests.emit(req)
    }
}


