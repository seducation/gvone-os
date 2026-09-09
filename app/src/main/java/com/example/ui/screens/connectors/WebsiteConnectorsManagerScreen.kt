package com.example.ui.screens.connectors

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.connector.WebsiteAccessConnectorService
import com.example.data.model.SavedPasswordEntry
import com.example.ui.viewmodel.BrowserViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Model representing a unified website connector entry with account, cookies, and password.
 */
data class UnifiedWebsiteAccount(
    val id: String,
    val domain: String,
    val title: String,
    val username: String,
    val password: String,
    val status: String, // "Active Session", "Connected Account", "Saved Credential"
    val cookiesCount: Int,
    val cookieNames: List<String>,
    val lastActive: String,
    val isSecure: Boolean = true
)

/**
 * Screen / Sheet presenting all website connectors, login accounts, cookies, and passwords in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteConnectorsManagerScreen(
    viewModel: BrowserViewModel,
    onOpenUrl: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val connectorService = remember { WebsiteAccessConnectorService(context) }

    var searchQuery by remember { mutableStateOf("") }
    var accountsList by remember {
        mutableStateOf(
            listOf(
                UnifiedWebsiteAccount(
                    id = "1",
                    domain = "github.com",
                    title = "GitHub",
                    username = "pinakiranjan95",
                    password = "ghp_secure_token_8941",
                    status = "Active Session",
                    cookiesCount = 8,
                    cookieNames = listOf("logged_in=yes", "dotcom_user=pinakiranjan95", "user_session", "_gh_sess", "tz=UTC"),
                    lastActive = "Active now"
                ),
                UnifiedWebsiteAccount(
                    id = "2",
                    domain = "google.com",
                    title = "Google Account",
                    username = "pinakiranjanbera95751@gmail.com",
                    password = "Google#AppPass2025!",
                    status = "Active Session",
                    cookiesCount = 12,
                    cookieNames = listOf("SID", "HSID", "SSID", "APISID", "SAPISID", "login_info", "1P_JAR"),
                    lastActive = "Today"
                ),
                UnifiedWebsiteAccount(
                    id = "3",
                    domain = "apple.com",
                    title = "Apple ID",
                    username = "pinakiranjanbera@icloud.com",
                    password = "iCloudKey_Pass_9921",
                    status = "Connected Account",
                    cookiesCount = 6,
                    cookieNames = listOf("myacinfo", "dslang=US-EN", "geo=US", "as_dc"),
                    lastActive = "Yesterday"
                ),
                UnifiedWebsiteAccount(
                    id = "4",
                    domain = "quantamagazine.org",
                    title = "Quanta Magazine",
                    username = "subscriber@quanta.org",
                    password = "ScienceReaderPass#2024",
                    status = "Saved Credential",
                    cookiesCount = 4,
                    cookieNames = listOf("wordpress_logged_in", "wp-settings", "cookie_consent"),
                    lastActive = "1 week ago"
                ),
                UnifiedWebsiteAccount(
                    id = "5",
                    domain = "charassist-c4uzg7hb.manus.space",
                    title = "GVONE CharAssist",
                    username = "gvone_bridge_client",
                    password = "ws_token_bidirectional_v2",
                    status = "Active Session",
                    cookiesCount = 5,
                    cookieNames = listOf("sessionid", "csrftoken", "ws_auth_handshake"),
                    lastActive = "Connected"
                ),
                UnifiedWebsiteAccount(
                    id = "6",
                    domain = "bloomberg.com",
                    title = "Bloomberg Terminal",
                    username = "finance_pro@bloomberg.net",
                    password = "Bbg_Market_Pass#881",
                    status = "Saved Credential",
                    cookiesCount = 7,
                    cookieNames = listOf("bbg_user", "bb_region", "auth_token"),
                    lastActive = "1 month ago"
                )
            )
        )
    }

    var showAddDialog by remember { mutableStateOf(false) }

    val filteredAccounts = remember(accountsList, searchQuery) {
        if (searchQuery.isBlank()) accountsList
        else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            accountsList.filter {
                it.domain.lowercase(Locale.ROOT).contains(q) ||
                it.title.lowercase(Locale.ROOT).contains(q) ||
                it.username.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Website Connectors & Logins",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Connected accounts, cookies & stored passwords",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Login", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search domain, account, or cookie...", color = Color(0xFF64748B), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    )
                }
            }
        },
        containerColor = Color(0xFF090D16),
        modifier = modifier.testTag("website_connectors_manager_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Summary KPI Cards
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Connected Sites",
                    value = accountsList.size.toString(),
                    icon = Icons.Rounded.Language,
                    color = Color(0xFF38BDF8),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Saved Passwords",
                    value = accountsList.count { it.password.isNotBlank() }.toString(),
                    icon = Icons.Rounded.Key,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Active Cookies",
                    value = accountsList.sumOf { it.cookiesCount }.toString(),
                    icon = Icons.Rounded.Cookie,
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "LOGGED IN WEBSITES (${filteredAccounts.size})",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(filteredAccounts, key = { it.id }) { item ->
                    WebsiteAccountCard(
                        account = item,
                        onOpenWebsite = {
                            onOpenUrl("https://${item.domain}")
                            onClose()
                        },
                        onClearCookies = {
                            coroutineScope.launch {
                                connectorService.clearCookiesForDomain("https://${item.domain}", item.domain)
                                accountsList = accountsList.map {
                                    if (it.id == item.id) it.copy(cookiesCount = 0, cookieNames = emptyList()) else it
                                }
                                Toast.makeText(context, "Cookies cleared for ${item.domain}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            accountsList = accountsList.filter { it.id != item.id }
                            Toast.makeText(context, "Removed login for ${item.domain}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddLoginDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { domain, username, password ->
                val newEntry = UnifiedWebsiteAccount(
                    id = System.currentTimeMillis().toString(),
                    domain = domain,
                    title = domain.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
                    username = username,
                    password = password,
                    status = "Saved Credential",
                    cookiesCount = 1,
                    cookieNames = listOf("session"),
                    lastActive = "Just now"
                )
                accountsList = listOf(newEntry) + accountsList
                showAddDialog = false
                Toast.makeText(context, "Added login for $domain", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF131A29),
        border = BorderStroke(1.dp, Color(0xFF222E42)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = title, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WebsiteAccountCard(
    account: UnifiedWebsiteAccount,
    onOpenWebsite: () -> Unit,
    onClearCookies: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isCookiesExpanded by remember { mutableStateOf(false) }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label to clipboard", Toast.LENGTH_SHORT).show()
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, Color(0xFF1F293D)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Header: Domain, Status Badge, and Open action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = account.domain.take(2).uppercase(),
                                color = Color(0xFF38BDF8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = account.title,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (account.isSecure) {
                                Icon(Icons.Rounded.Lock, contentDescription = "Secure", tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                            }
                        }
                        Text(
                            text = account.domain,
                            color = Color(0xFF64748B),
                            fontSize = 12.sp
                        )
                    }
                }

                // Status Badge & Open Icon
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (account.status == "Active Session") Color(0xFF064E3B) else Color(0xFF1E293B)
                    ) {
                        Text(
                            text = account.status,
                            color = if (account.status == "Active Session") Color(0xFF34D399) else Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    IconButton(onClick = onOpenWebsite, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = "Open", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

            // 2. Account & Password Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Username Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Person, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Account:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = account.username, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(
                        onClick = { copyToClipboard("Username", account.username) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Username", tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                    }
                }

                // Password Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Password:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPasswordVisible) account.password else "••••••••••••",
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { isPasswordVisible = !isPasswordVisible },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Toggle password visibility",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        IconButton(
                            onClick = { copyToClipboard("Password", account.password) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Password", tint = Color(0xFF64748B), modifier = Modifier.size(13.dp))
                        }
                    }
                }
            }

            // 3. Cookies Inspector Section
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0B101B),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isCookiesExpanded = !isCookiesExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Cookie, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cookies: ${account.cookiesCount} active tokens",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isCookiesExpanded) "Collapse" else "Inspect",
                                color = Color(0xFF38BDF8),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Icon(
                                imageVector = if (isCookiesExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (isCookiesExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (cookie in account.cookieNames) {
                                Text(
                                    text = "• $cookie",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = onClearCookies,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                                    border = BorderStroke(1.dp, Color(0xFF7F1D1D)),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Site Cookies", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 4. Footer: Last active & Delete option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Last active: ${account.lastActive}",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp
                )

                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Text("Delete Account", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AddLoginDialog(
    onDismiss: () -> Unit,
    onAdd: (domain: String, username: String, pass: String) -> Unit
) {
    var domain by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Website Login", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = domain,
                    onValueChange = { domain = it },
                    label = { Text("Website Domain") },
                    placeholder = { Text("e.g. github.com") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or Email") },
                    placeholder = { Text("user@example.com") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (domain.isNotBlank() && username.isNotBlank()) {
                        onAdd(domain.trim(), username.trim(), password)
                    }
                },
                enabled = domain.isNotBlank() && username.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF141C2B)
    )
}
