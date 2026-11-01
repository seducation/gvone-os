package com.example.ui.screens.ide

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.files.FileType
import com.example.data.files.GVONEFileItem
import com.example.data.files.GVONEFileSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Integrated central workspace editor for the unified Xcode-style IDE browser window.
 * Displays multi-file editor tabs, line numbers gutter, and code editing buffer
 * directly in the central workspace without any separate application screen or activity bar.
 */
@Composable
fun XcodeWorkspaceEditor(
    openTabs: List<IdeTab>,
    activeTabId: String?,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewFileTab: () -> Unit,
    fileSystem: GVONEFileSystem,
    coroutineScope: CoroutineScope,
    onSaveFile: (String, String) -> Unit,
    onFormatDocument: () -> Unit,
    onOpenNavigatorFiles: () -> Unit,
    onSwitchToBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeTab = remember(openTabs, activeTabId) {
        openTabs.find { it.id == activeTabId } ?: openTabs.firstOrNull()
    }

    var editorTextFieldValue by remember { mutableStateOf(TextFieldValue(activeTab?.content.orEmpty())) }

    // Sync editor buffer when active file tab changes
    LaunchedEffect(activeTab?.id) {
        if (activeTab != null) {
            editorTextFieldValue = TextFieldValue(activeTab.content)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("xcode_workspace_editor"),
        color = Color(0xFF0B0E14)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {


            // Quick Editor Action Bar (Format, Save)
            if (activeTab != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp),
                    color = Color(0xFF0F1422),
                    border = BorderStroke(0.5.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = activeTab.fileItem.path,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(
                                onClick = onFormatDocument,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Icon(Icons.Rounded.FormatAlignLeft, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Format", fontSize = 10.sp, color = Color(0xFF38BDF8))
                            }

                            FilledTonalButton(
                                onClick = { onSaveFile(activeTab.fileItem.path, editorTextFieldValue.text) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(24.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFF10B981).copy(alpha = 0.2f),
                                    contentColor = Color(0xFF34D399)
                                )
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Save", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ================= EDITOR CONTENT BUFFER =================
            if (activeTab == null) {
                // Empty state when no files are open
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF7C3AED).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF7C3AED).copy(alpha = 0.4f)),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Code,
                                    contentDescription = null,
                                    tint = Color(0xFFA78BFA),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "Xcode Workspace Editor",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Text(
                            text = "Select a file from the bottom-left Navigator, or switch back to the Web Browser.",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onOpenNavigatorFiles,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Browse Files", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = onSwitchToBrowser,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFF38BDF8))
                            ) {
                                Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Web Browser", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        }
                    }
                }
            } else {
                // Code Buffer with Line Numbers Gutter
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    val text = editorTextFieldValue.text
                    val lineCount = text.lines().size.coerceAtLeast(1)

                    // Line Numbers Gutter
                    Surface(
                        modifier = Modifier
                            .width(42.dp)
                            .fillMaxHeight(),
                        color = Color(0xFF0C101A)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, end = 6.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            for (i in 1..lineCount) {
                                Text(
                                    text = "$i",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.5.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }

                    VerticalDivider(
                        color = Color(0xFF1E293B),
                        thickness = 1.dp
                    )

                    // Monospace Code Editor
                    BasicTextField(
                        value = editorTextFieldValue,
                        onValueChange = { newVal ->
                            editorTextFieldValue = newVal
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFE2E8F0)
                        ),
                        cursorBrush = SolidColor(Color(0xFF38BDF8)),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 60.dp)
                            .testTag("xcode_code_buffer")
                    )
                }
            }
        }
    }
}
