package com.example.data.connector

/**
 * Context passed automatically to the Connector from the active browser tab.
 */
data class WebsiteAccessContext(
    val currentTabId: String,
    val currentUrl: String,
    val currentDomain: String,
    val currentEnvironmentId: String,
    val currentEnvironmentName: String = "Default"
)

/**
 * Login & Account detection status for the current website.
 * Strict rule: Never guess login status.
 */
sealed interface AccountDetectionStatus {
    data class LoggedIn(
        val accountIdentifier: String,
        val details: String? = null,
        val detectionSource: String = "Verified Session"
    ) : AccountDetectionStatus

    data class LoggedOut(
        val reason: String = "No active session detected"
    ) : AccountDetectionStatus

    object Unknown : AccountDetectionStatus
}

/**
 * Detailed website access report for the active website/tab.
 */
data class WebsiteAccessReport(
    val context: WebsiteAccessContext,
    val accountStatus: AccountDetectionStatus,
    val cookiesCount: Int,
    val cookieNames: List<String>,
    val permissionsCount: Int,
    val grantedPermissions: List<String>,
    val siteDataFormatted: String,
    val siteDataBytes: Long,
    val isHttps: Boolean,
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)
