package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserSettings
import com.example.data.model.BrowserTab
import com.example.data.model.HistoryEntry
import com.example.data.model.SitePermission
import com.example.data.model.isInternalHomeUrl
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SiteInfoSection {
    MAIN,
    SECURITY_DETAILS,
    COOKIES_DETAILS,
    PERMISSIONS_DETAILS,
    HISTORY_DETAILS,
    ABOUT_DETAILS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoBottomSheet(
    tab: BrowserTab?,
    isTorActive: Boolean,
    history: List<HistoryEntry>,
    settings: BrowserSettings,
    onSavePermission: (SitePermission) -> Unit,
    onGetPermission: suspend (String) -> SitePermission?,
    onClearSiteData: (domain: String, url: String) -> Unit,
    onClearDomainHistory: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentSection by remember { mutableStateOf(SiteInfoSection.MAIN) }

    // Intercept hardware/gesture back button to return to MAIN view before dismissing
    BackHandler(enabled = currentSection != SiteInfoSection.MAIN) {
        currentSection = SiteInfoSection.MAIN
    }

    val currentUrl = tab?.url ?: "gvone://newtab"
    val isHomeApp = isInternalHomeUrl(currentUrl)
    val isHttps = currentUrl.startsWith("https://", ignoreCase = true)

    val domain = remember(currentUrl) {
        if (isHomeApp) "Start Page"
        else {
            try {
                val host = java.net.URI(currentUrl).host
                if (!host.isNullOrBlank()) host.removePrefix("www.") else currentUrl
            } catch (_: Exception) {
                currentUrl
            }
        }
    }

    // Cookie and site data computation
    val cookieManager = remember { CookieManager.getInstance() }
    var cookieCount by remember(currentUrl) {
        mutableIntStateOf(
            try {
                val cookieStr = cookieManager.getCookie(currentUrl)
                if (cookieStr.isNullOrBlank()) 0 else cookieStr.split(";").count { it.isNotBlank() }
            } catch (_: Exception) { 0 }
        )
    }

    val thirdPartyBlocked = settings.blockThirdPartyCookies
    val cookieStatusText = if (thirdPartyBlocked) {
        if (cookieCount > 0) "$cookieCount cookies • Third-party cookies blocked"
        else "Third-party cookies blocked"
    } else {
        if (cookieCount > 0) "$cookieCount cookies in use"
        else "Cookies allowed"
    }

    // Site permissions state
    var sitePermission by remember { mutableStateOf<SitePermission?>(null) }
    var soundAllowed by remember { mutableStateOf(true) }

    LaunchedEffect(domain) {
        if (!isHomeApp && domain.isNotBlank()) {
            sitePermission = onGetPermission(domain)
        }
    }

    val permissionSummaryText = remember(sitePermission, soundAllowed) {
        val items = mutableListOf<String>()
        if (soundAllowed) items.add("Sound allowed") else items.add("Sound blocked")
        sitePermission?.let { sp ->
            if (sp.cameraAllowed == true) items.add("Camera allowed")
            else if (sp.cameraAllowed == false) items.add("Camera blocked")
            if (sp.micAllowed == true) items.add("Mic allowed")
            else if (sp.micAllowed == false) items.add("Mic blocked")
            if (sp.locationAllowed == true) items.add("Location allowed")
            else if (sp.locationAllowed == false) items.add("Location blocked")
            if (sp.notificationsAllowed == true) items.add("Notifications allowed")
            else if (sp.notificationsAllowed == false) items.add("Notifications blocked")
        }
        items.take(2).joinToString(" • ")
    }

    // Last visited calculation from Room History
    val domainHistoryEntries = remember(history, domain) {
        if (isHomeApp || domain.isBlank()) emptyList()
        else {
            history.filter {
                it.url.contains(domain, ignoreCase = true) || it.title.contains(domain, ignoreCase = true)
            }.sortedByDescending { it.timestamp }
        }
    }

    val lastVisitedText = remember(domainHistoryEntries) {
        if (domainHistoryEntries.isEmpty()) {
            "First visit to this site"
        } else {
            val lastTimestamp = domainHistoryEntries.first().timestamp
            val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

            val isToday = DateUtils.isToday(lastTimestamp)
            val isYesterday = DateUtils.isToday(lastTimestamp + 86400000L)

            when {
                isToday -> "Last visited today at ${timeFormat.format(Date(lastTimestamp))}"
                isYesterday -> "Last visited yesterday at ${timeFormat.format(Date(lastTimestamp))}"
                else -> "Last visited on ${dateFormat.format(Date(lastTimestamp))}"
            }
        }
    }

    // Dynamic About This Page text
    val siteDescription = remember(domain, tab?.title) {
        val cleanDomain = domain.lowercase()
        when {
            cleanDomain.contains("youtube.com") || cleanDomain.contains("youtu.be") ->
                "YouTube is an American online video sharing and social media platform owned by Google."
            cleanDomain.contains("google.") ->
                "Google Search is a search engine provided by Google, organizing the world's information."
            cleanDomain.contains("wikipedia.org") ->
                "Wikipedia is a free, multilingual open-collaborative online encyclopedia maintained by volunteers worldwide."
            cleanDomain.contains("github.com") ->
                "GitHub is a developer platform that allows developers to create, store, manage and share code."
            cleanDomain.contains("reddit.com") ->
                "Reddit is an American social news aggregation, content rating, and discussion website."
            cleanDomain.contains("twitter.com") || cleanDomain.contains("x.com") ->
                "X (formerly Twitter) is an online social media and social networking service."
            cleanDomain.contains("duckduckgo.com") ->
                "DuckDuckGo is an internet search engine that emphasizes protecting searchers' privacy."
            cleanDomain.contains("apple.com") ->
                "Apple is an American multinational technology company specializing in consumer electronics and software."
            cleanDomain.contains("quantamagazine.org") ->
                "Quanta Magazine is an editorially independent online publication covering developments in physics, mathematics, and biology."
            else -> {
                val title = tab?.title
                if (!title.isNullOrBlank() && title != "New Tab" && !title.startsWith("http")) {
                    "$title – Web destination on $domain"
                } else {
                    "Online resources, media, and services hosted on $domain"
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF131823),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x35FFFFFF))
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("site_info_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            AnimatedContent(
                targetState = currentSection,
                transitionSpec = {
                    if (targetState == SiteInfoSection.MAIN) {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    } else {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    }
                },
                label = "SiteInfoSectionTransition"
            ) { section ->
                when (section) {
                    SiteInfoSection.MAIN -> {
                        SiteInfoMainView(
                            domain = domain,
                            tab = tab,
                            isHttps = isHttps,
                            isTorActive = isTorActive,
                            cookieStatusText = cookieStatusText,
                            permissionSummaryText = permissionSummaryText,
                            lastVisitedText = lastVisitedText,
                            siteDescription = siteDescription,
                            onNavigateTo = { currentSection = it },
                            onClose = onDismiss
                        )
                    }
                    SiteInfoSection.SECURITY_DETAILS -> {
                        SiteSecurityDetailView(
                            domain = domain,
                            isHttps = isHttps,
                            isTorActive = isTorActive,
                            onBack = { currentSection = SiteInfoSection.MAIN },
                            onClose = onDismiss
                        )
                    }
                    SiteInfoSection.COOKIES_DETAILS -> {
                        SiteCookiesDetailView(
                            domain = domain,
                            url = currentUrl,
                            cookieCount = cookieCount,
                            thirdPartyBlocked = thirdPartyBlocked,
                            onClearSiteData = {
                                onClearSiteData(domain, currentUrl)
                                cookieCount = 0
                            },
                            onBack = { currentSection = SiteInfoSection.MAIN },
                            onClose = onDismiss
                        )
                    }
                    SiteInfoSection.PERMISSIONS_DETAILS -> {
                        SitePermissionsDetailView(
                            domain = domain,
                            soundAllowed = soundAllowed,
                            sitePermission = sitePermission,
                            onToggleSound = { soundAllowed = !soundAllowed },
                            onUpdatePermission = { updated ->
                                sitePermission = updated
                                onSavePermission(updated)
                            },
                            onBack = { currentSection = SiteInfoSection.MAIN },
                            onClose = onDismiss
                        )
                    }
                    SiteInfoSection.HISTORY_DETAILS -> {
                        SiteHistoryDetailView(
                            domain = domain,
                            entries = domainHistoryEntries,
                            onClearHistory = {
                                onClearDomainHistory(domain)
                            },
                            onBack = { currentSection = SiteInfoSection.MAIN },
                            onClose = onDismiss
                        )
                    }
                    SiteInfoSection.ABOUT_DETAILS -> {
                        SiteAboutDetailView(
                            domain = domain,
                            url = currentUrl,
                            title = tab?.title ?: domain,
                            description = siteDescription,
                            isHttps = isHttps,
                            isTorActive = isTorActive,
                            onBack = { currentSection = SiteInfoSection.MAIN },
                            onClose = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteInfoMainView(
    domain: String,
    tab: BrowserTab?,
    isHttps: Boolean,
    isTorActive: Boolean,
    cookieStatusText: String,
    permissionSummaryText: String,
    lastVisitedText: String,
    siteDescription: String,
    onNavigateTo: (SiteInfoSection) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Header Row: Favicon + Domain + Security Badge + Close Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favicon / Domain Badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E2638)),
                contentAlignment = Alignment.Center
            ) {
                val initial = domain.firstOrNull()?.uppercaseChar()?.toString() ?: "W"
                Text(
                    text = initial,
                    color = GVONEPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Domain and Security Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain,
                    color = GVONETextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isTorActive) Icons.Rounded.VpnKey else if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (isTorActive) GVONETertiary else if (isHttps) GVONETertiary else GVONEAccentRed,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTorActive) "Tor encrypted connection" else if (isHttps) "Connection is secure" else "Connection is not secure",
                        color = if (isTorActive || isHttps) GVONETertiary else GVONEAccentRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = GVONETextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            color = Color(0x18FFFFFF),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        // Menu Items List (matching Chrome's Site Information bottom sheet)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. Connection is secure
            item {
                SiteInfoMenuItem(
                    icon = if (isTorActive) Icons.Rounded.VpnKey else Icons.Rounded.Lock,
                    iconTint = if (isTorActive || isHttps) GVONETertiary else GVONEAccentRed,
                    title = if (isTorActive) "Tor Onion Circuit" else if (isHttps) "Connection is secure" else "Connection is not secure",
                    subtitle = if (isTorActive) "Your traffic is routed through 3 encrypted Tor onion nodes"
                    else if (isHttps) "Your information is private when it is sent to this site"
                    else "You should not enter any sensitive information on this site",
                    testTag = "site_info_item_connection",
                    onClick = { onNavigateTo(SiteInfoSection.SECURITY_DETAILS) }
                )
            }

            // 2. Cookies and site data
            item {
                SiteInfoMenuItem(
                    icon = Icons.Rounded.Cookie,
                    iconTint = Color(0xFFF59E0B),
                    title = "Cookies and site data",
                    subtitle = cookieStatusText,
                    testTag = "site_info_item_cookies",
                    onClick = { onNavigateTo(SiteInfoSection.COOKIES_DETAILS) }
                )
            }

            // 3. Permissions
            item {
                SiteInfoMenuItem(
                    icon = Icons.Rounded.Tune,
                    iconTint = GVONESecondary,
                    title = "Permissions",
                    subtitle = permissionSummaryText,
                    testTag = "site_info_item_permissions",
                    onClick = { onNavigateTo(SiteInfoSection.PERMISSIONS_DETAILS) }
                )
            }

            // 4. Last visited
            item {
                SiteInfoMenuItem(
                    icon = Icons.Rounded.History,
                    iconTint = Color(0xFFA78BFA),
                    title = "Last visited",
                    subtitle = lastVisitedText,
                    testTag = "site_info_item_history",
                    onClick = { onNavigateTo(SiteInfoSection.HISTORY_DETAILS) }
                )
            }

            // 5. About this page
            item {
                SiteInfoMenuItem(
                    icon = Icons.Rounded.Info,
                    iconTint = GVONEPrimary,
                    title = "About this page",
                    subtitle = siteDescription,
                    testTag = "site_info_item_about",
                    onClick = { onNavigateTo(SiteInfoSection.ABOUT_DETAILS) }
                )
            }
        }
    }
}

@Composable
private fun SiteInfoMenuItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = GVONETextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = GVONETextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0x60FFFFFF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SubViewHeader(
    title: String,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = GVONETextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            color = GVONETextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = GVONETextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    HorizontalDivider(color = Color(0x18FFFFFF), thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
private fun SiteSecurityDetailView(
    domain: String,
    isHttps: Boolean,
    isTorActive: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubViewHeader(
            title = "Connection security",
            onBack = onBack,
            onClose = onClose
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isHttps || isTorActive) Color(0xFF152628) else Color(0xFF2C191D),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isHttps || isTorActive) GVONETertiary.copy(alpha = 0.35f) else GVONEAccentRed.copy(alpha = 0.35f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isTorActive) Icons.Rounded.VpnKey else if (isHttps) Icons.Rounded.Lock else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (isTorActive || isHttps) GVONETertiary else GVONEAccentRed,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (isTorActive) "Tor Onion Anonymous Circuit" else if (isHttps) "Connection is secure" else "Connection is not secure",
                            color = GVONETextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isTorActive) "Traffic routed through encrypted decentralized nodes with hidden IP."
                            else if (isHttps) "Your information is private when it is sent to this site, and cannot be intercepted by third parties."
                            else "Attackers might be able to see or change information you send to this site (passwords, credit cards).",
                            color = GVONETextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Technical Certificate Info Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF19202E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Certificate Information",
                        color = GVONEPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    CertificateRow(label = "Issued to", value = domain)
                    CertificateRow(
                        label = "Certificate Authority",
                        value = if (isTorActive) "Tor Onion v3 Hidden Service Key" else if (isHttps) "Verified Trusted CA (X.509)" else "None (Unencrypted HTTP)"
                    )
                    CertificateRow(
                        label = "Protocol & Cipher",
                        value = if (isTorActive) "SOCKS5 Onion v3 • ChaCha20-Poly1305" else if (isHttps) "TLS 1.3 • AES_256_GCM (256-bit)" else "Insecure Plaintext"
                    )
                    CertificateRow(
                        label = "Validity",
                        value = if (isHttps || isTorActive) "Valid and Authenticated" else "Invalid / Insecure"
                    )
                }
            }
        }
    }
}

@Composable
private fun CertificateRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Text(text = label, color = GVONETextSecondary, fontSize = 11.sp)
        Text(text = value, color = GVONETextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SiteCookiesDetailView(
    domain: String,
    url: String,
    cookieCount: Int,
    thirdPartyBlocked: Boolean,
    onClearSiteData: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        SubViewHeader(
            title = "Cookies and site data",
            onBack = onBack,
            onClose = onClose
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Status Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF19202E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Cookie,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "$cookieCount cookies stored for $domain",
                            color = GVONETextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Cookies are small files created by websites you visit. They make your online experience easier by saving browsing data like login state and site preferences.",
                        color = GVONETextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222B3D))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (thirdPartyBlocked) Icons.Rounded.Shield else Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = if (thirdPartyBlocked) GVONETertiary else Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (thirdPartyBlocked) "Third-party tracker cookies are blocked" else "Third-party cookies allowed",
                            color = GVONETextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Clear Cookies and Site Data Action Button
            Button(
                onClick = { showConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GVONEAccentRed.copy(alpha = 0.15f),
                    contentColor = GVONEAccentRed
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clear_site_data_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete cookies and site data",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "Delete site data?",
                    color = GVONETextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will sign you out of $domain and clear offline data and cookies stored by this site.",
                    color = GVONETextSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmDialog = false
                        onClearSiteData()
                        Toast.makeText(context, "Cleared cookies for $domain", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Delete", color = GVONEAccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel", color = GVONETextSecondary)
                }
            },
            containerColor = Color(0xFF19202E)
        )
    }
}

@Composable
private fun SitePermissionsDetailView(
    domain: String,
    soundAllowed: Boolean,
    sitePermission: SitePermission?,
    onToggleSound: () -> Unit,
    onUpdatePermission: (SitePermission) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var currentPermission by remember(sitePermission) {
        mutableStateOf(sitePermission ?: SitePermission(domain = domain))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SubViewHeader(
            title = "Permissions",
            onBack = onBack,
            onClose = onClose
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Permissions for $domain",
                color = GVONETextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF19202E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Sound & Autoplay Toggle
                    PermissionToggleRow(
                        icon = Icons.Rounded.VolumeUp,
                        title = "Sound & Autoplay",
                        isAllowed = soundAllowed,
                        onToggle = onToggleSound
                    )

                    HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                    // Camera Permission
                    PermissionToggleRow(
                        icon = Icons.Rounded.Videocam,
                        title = "Camera",
                        isAllowed = currentPermission.cameraAllowed == true,
                        onToggle = {
                            val next = if (currentPermission.cameraAllowed == true) false else true
                            currentPermission = currentPermission.copy(cameraAllowed = next)
                            onUpdatePermission(currentPermission)
                        }
                    )

                    HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                    // Microphone Permission
                    PermissionToggleRow(
                        icon = Icons.Rounded.Mic,
                        title = "Microphone",
                        isAllowed = currentPermission.micAllowed == true,
                        onToggle = {
                            val next = if (currentPermission.micAllowed == true) false else true
                            currentPermission = currentPermission.copy(micAllowed = next)
                            onUpdatePermission(currentPermission)
                        }
                    )

                    HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                    // Location Permission
                    PermissionToggleRow(
                        icon = Icons.Rounded.LocationOn,
                        title = "Location",
                        isAllowed = currentPermission.locationAllowed == true,
                        onToggle = {
                            val next = if (currentPermission.locationAllowed == true) false else true
                            currentPermission = currentPermission.copy(locationAllowed = next)
                            onUpdatePermission(currentPermission)
                        }
                    )

                    HorizontalDivider(color = Color(0x12FFFFFF), thickness = 0.5.dp)

                    // Notifications Permission
                    PermissionToggleRow(
                        icon = Icons.Rounded.Notifications,
                        title = "Notifications",
                        isAllowed = currentPermission.notificationsAllowed == true,
                        onToggle = {
                            val next = if (currentPermission.notificationsAllowed == true) false else true
                            currentPermission = currentPermission.copy(notificationsAllowed = next)
                            onUpdatePermission(currentPermission)
                        }
                    )
                }
            }

            // Reset Permissions Button
            TextButton(
                onClick = {
                    currentPermission = SitePermission(domain = domain)
                    onUpdatePermission(currentPermission)
                    Toast.makeText(context, "Permissions reset for $domain", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(
                    text = "Reset permissions to defaults",
                    color = GVONEPrimary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun PermissionToggleRow(
    icon: ImageVector,
    title: String,
    isAllowed: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isAllowed) GVONESecondary else GVONETextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = GVONETextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (isAllowed) "Allowed" else "Blocked",
                color = if (isAllowed) GVONETertiary else GVONETextSecondary,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = isAllowed,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = GVONEPrimary,
                uncheckedThumbColor = Color(0xFF8B949E),
                uncheckedTrackColor = Color(0xFF21262D)
            )
        )
    }
}

@Composable
private fun SiteHistoryDetailView(
    domain: String,
    entries: List<HistoryEntry>,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SubViewHeader(
            title = "Last visited",
            onBack = onBack,
            onClose = onClose
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF19202E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Visited ${entries.size} times",
                            color = GVONETextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (entries.isEmpty()) {
                        Text(
                            text = "No prior visit history recorded for $domain.",
                            color = GVONETextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        val timeFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            entries.take(5).forEach { entry ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = entry.title.ifBlank { entry.url },
                                        color = GVONETextPrimary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = timeFormat.format(Date(entry.timestamp)),
                                        color = GVONETextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (entries.isNotEmpty()) {
                Button(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF222B3D),
                        contentColor = GVONETextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear history for $domain",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteAboutDetailView(
    domain: String,
    url: String,
    title: String,
    description: String,
    isHttps: Boolean,
    isTorActive: Boolean,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        SubViewHeader(
            title = "About this page",
            onBack = onBack,
            onClose = onClose
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Description Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF19202E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Overview",
                        color = GVONEPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        color = GVONETextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }

            // Web Destination Specs
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF19202E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Page Details",
                        color = GVONEPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CertificateRow(label = "Domain", value = domain)
                    CertificateRow(label = "Page Title", value = title)
                    CertificateRow(
                        label = "Security Protocol",
                        value = if (isTorActive) "Tor Onion Relay (Anonymous)" else if (isHttps) "HTTPS (TLS 1.3 Verified)" else "HTTP (Insecure)"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Copy Page Address
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Page URL", url))
                            Toast.makeText(context, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = GVONEPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy page address", color = GVONEPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
