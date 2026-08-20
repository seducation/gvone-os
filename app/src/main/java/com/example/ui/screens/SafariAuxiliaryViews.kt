package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BrowserTab
import com.example.data.model.SavedPasswordEntry
import com.example.ui.theme.*

@Composable
fun AddToHomeScreenDialog(
    tab: BrowserTab?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(tab?.title) { mutableStateOf(tab?.title ?: "Website Shortcut") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161D2A),
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = "Add to Home Screen",
                color = Color(0xFFF1F5F9),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "An icon will be added to your Home screen so you can quickly access this website.",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E2738))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GVONEPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color(0xFFE2E8F0),
                            focusedBorderColor = GVONEPrimary,
                            unfocusedBorderColor = Color(0xFF334155)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GVONEPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        modifier = Modifier.testTag("add_to_home_screen_dialog")
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPasswordsSheet(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    val mockPasswords = remember {
        listOf(
            SavedPasswordEntry("1", "apple.com", "pinakiranjanbera@icloud.com", "••••••••••••", "Yesterday"),
            SavedPasswordEntry("2", "github.com", "pinakiranjan95", "••••••••••••", "3 days ago"),
            SavedPasswordEntry("3", "quantamagazine.org", "subscriber@quanta.org", "••••••••••••", "1 week ago"),
            SavedPasswordEntry("4", "google.com", "pinakiranjanbera95751@gmail.com", "••••••••••••", "2 weeks ago"),
            SavedPasswordEntry("5", "bloomberg.com", "finance_pro@bloomberg.net", "••••••••••••", "1 month ago")
        )
    }

    val filtered = mockPasswords.filter {
        it.website.contains(searchQuery, ignoreCase = true) || it.username.contains(searchQuery, ignoreCase = true)
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = Color(0xF5131822),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF3B485E))
            )
        },
        modifier = modifier.testTag("saved_passwords_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = GVONEPrimary, modifier = Modifier.size(20.dp))
                    Text("Passwords & Autofill", color = Color(0xFFF1F5F9), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = GVONETertiary.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GVONETertiary)
                ) {
                    Text("iCloud Keychain", color = GVONETertiary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Logins & Passwords", color = Color(0xFF64748B), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFF1A2230),
                    unfocusedContainerColor = Color(0xFF1A2230),
                    focusedBorderColor = GVONEPrimary,
                    unfocusedBorderColor = Color(0xFF2E3A4D)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Password items list
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered) { item ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1A2230),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B374C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.website, color = Color(0xFFF1F5F9), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(item.username, color = Color(0xFF94A3B8), fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(item.passwordMasked, color = GVONEPrimary, fontSize = 13.sp, letterSpacing = 2.sp)
                            }

                            Button(
                                onClick = { onClose() },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF263246)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Autofill", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
