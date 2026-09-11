package com.example.agent

import com.example.agent.safety.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PermissionSystemUiUnitTest {

    private lateinit var permissionSystem: PermissionSystem

    @Before
    fun setUp() {
        permissionSystem = PermissionSystem()
    }

    @Test
    fun testDefaultDescriptorsCompleteness() {
        val descriptors = PermissionSystem.ALL_DESCRIPTORS
        assertTrue("Descriptors list must not be empty", descriptors.isNotEmpty())

        val keys = descriptors.map { it.key }
        assertTrue("Contains filesystem read", keys.contains(PermissionSystem.PERM_FILESYSTEM_READ))
        assertTrue("Contains filesystem delete", keys.contains(PermissionSystem.PERM_FILESYSTEM_DELETE))
        assertTrue("Contains shell execute", keys.contains(PermissionSystem.PERM_SHELL_EXECUTE))
        assertTrue("Contains browser navigate", keys.contains(PermissionSystem.PERM_BROWSER_NAVIGATE))
        assertTrue("Contains external API", keys.contains(PermissionSystem.PERM_EXTERNAL_API))

        // Ensure every descriptor has non-blank metadata
        descriptors.forEach { desc ->
            assertTrue("Key must not be blank", desc.key.isNotBlank())
            assertTrue("Title must not be blank", desc.title.isNotBlank())
            assertTrue("Category must not be blank", desc.category.isNotBlank())
            assertTrue("Description must not be blank", desc.description.isNotBlank())
            assertTrue("Risk level must be LOW, HIGH, or CRITICAL",
                listOf("LOW", "HIGH", "CRITICAL").contains(desc.riskLevel.uppercase()))
        }
    }

    @Test
    fun testDefaultPoliciesAndAccess() {
        // Safe actions allowed by default
        assertEquals(PermissionPolicy.ALLOW, permissionSystem.getPolicy(PermissionSystem.PERM_BROWSER_READ))
        assertTrue(permissionSystem.hasPermission(PermissionSystem.PERM_BROWSER_READ))

        // Destructive actions default to ASK
        assertEquals(PermissionPolicy.ASK, permissionSystem.getPolicy(PermissionSystem.PERM_FILESYSTEM_DELETE))
        assertEquals(PermissionPolicy.ASK, permissionSystem.getPolicy(PermissionSystem.PERM_SHELL_EXECUTE))

        // Check permission requiring confirmation in non-headless mode
        val check = permissionSystem.checkPermission(PermissionSystem.PERM_FILESYSTEM_DELETE, "TestAgent", isHeadless = false)
        assertEquals(PermissionStatus.REQUIRES_CONFIRMATION, check.status)

        // In headless mode, ASK should deny
        val checkHeadless = permissionSystem.checkPermission(PermissionSystem.PERM_FILESYSTEM_DELETE, "TestAgent", isHeadless = true)
        assertEquals(PermissionStatus.DENIED, checkHeadless.status)
    }

    @Test
    fun testInteractivePromptAllowOnce() {
        permissionSystem.triggerTestPrompt(
            permission = PermissionSystem.PERM_FILESYSTEM_DELETE,
            agentName = "FileAgent",
            description = "Delete temp files"
        )

        val active = permissionSystem.activePromptFlow.value
        assertNotNull("Active prompt should be populated", active)
        assertEquals(PermissionSystem.PERM_FILESYSTEM_DELETE, active?.permission)

        // User approves once
        permissionSystem.respondToRequest(active!!.id, approved = true, rememberPolicy = false)

        assertNull("Prompt should be cleared", permissionSystem.activePromptFlow.value)
        assertTrue("Permission should now be granted", permissionSystem.hasPermission(PermissionSystem.PERM_FILESYSTEM_DELETE))
        // Policy should remain ASK because rememberPolicy was false
        assertEquals(PermissionPolicy.ASK, permissionSystem.getPolicy(PermissionSystem.PERM_FILESYSTEM_DELETE))
    }

    @Test
    fun testInteractivePromptAlwaysAllow() {
        permissionSystem.triggerTestPrompt(
            permission = PermissionSystem.PERM_SHELL_EXECUTE,
            agentName = "TerminalAgent",
            description = "Run test script"
        )

        val active = permissionSystem.activePromptFlow.value
        assertNotNull(active)

        permissionSystem.respondToRequest(active!!.id, approved = true, rememberPolicy = true)

        assertNull("Prompt should be cleared", permissionSystem.activePromptFlow.value)
        assertTrue(permissionSystem.hasPermission(PermissionSystem.PERM_SHELL_EXECUTE))
        // Policy should now be updated to ALLOW
        assertEquals(PermissionPolicy.ALLOW, permissionSystem.getPolicy(PermissionSystem.PERM_SHELL_EXECUTE))
    }

    @Test
    fun testInteractivePromptAlwaysBlock() {
        permissionSystem.triggerTestPrompt(
            permission = PermissionSystem.PERM_EXTERNAL_API,
            agentName = "ExternalAgent",
            description = "Send webhook"
        )

        val active = permissionSystem.activePromptFlow.value
        assertNotNull(active)

        permissionSystem.respondToRequest(active!!.id, approved = false, rememberPolicy = true)

        assertNull(permissionSystem.activePromptFlow.value)
        assertFalse(permissionSystem.hasPermission(PermissionSystem.PERM_EXTERNAL_API))
        assertEquals(PermissionPolicy.DENY, permissionSystem.getPolicy(PermissionSystem.PERM_EXTERNAL_API))
    }

    @Test
    fun testRevokeAndResetDefaults() {
        permissionSystem.grantPermission(PermissionSystem.PERM_FILESYSTEM_DELETE)
        assertTrue(permissionSystem.hasPermission(PermissionSystem.PERM_FILESYSTEM_DELETE))

        permissionSystem.revokePermission(PermissionSystem.PERM_FILESYSTEM_DELETE)
        assertFalse(permissionSystem.hasPermission(PermissionSystem.PERM_FILESYSTEM_DELETE))

        permissionSystem.revokeAll()
        assertTrue("Granted permissions should be empty after revokeAll", permissionSystem.grantedPermissionsFlow.value.isEmpty())

        permissionSystem.resetToDefaults()
        assertTrue("Defaults should restore browser:read", permissionSystem.hasPermission(PermissionSystem.PERM_BROWSER_READ))
    }

    @Test
    fun testAuditTrailLogging() {
        permissionSystem.clearAuditLogs()
        assertTrue(permissionSystem.auditLogFlow.value.isEmpty())

        permissionSystem.checkPermission(PermissionSystem.PERM_FILESYSTEM_READ, "ReaderAgent")
        permissionSystem.setPolicy(PermissionSystem.PERM_SHELL_EXECUTE, PermissionPolicy.DENY)

        val logs = permissionSystem.auditLogFlow.value
        assertTrue("Logs should contain recorded events", logs.isNotEmpty())
        assertTrue("Log should record set policy", logs.any { it.action == "SET_POLICY" })
    }
}
