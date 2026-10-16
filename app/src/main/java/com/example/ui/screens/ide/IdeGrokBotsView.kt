package com.example.ui.screens.ide

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.GVONEAIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Grok-Style Multi-Bot AI Coding Copilot View for GVONE Code Studio.
 * Features customizable bot personas (Grok Coder, Code Architect, Bug Hunter, Test Specialist)
 * with awareness of open tabs, files, and chats, plus one-tap code apply and file creation.
 */
@Composable
fun IdeGrokBotsView(
    activeTab: IdeTab?,
    openTabs: List<IdeTab>,
    aiService: GVONEAIService,
    coroutineScope: CoroutineScope,
    onApplyCodeToEditor: (String) -> Unit,
    onCreateFileFromCode: (filename: String, code: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPersona by remember { mutableStateOf(GrokBotPersona.GROK_CODER) }
    var promptInput by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    // Multi-turn chat message history
    val messages = remember { mutableStateListOf<BotChatMessage>() }
    val chatListState = rememberLazyListState()

    // Initialize with a welcome message from the active bot
    LaunchedEffect(selectedPersona) {
        if (messages.none { it.senderId == selectedPersona.id }) {
            messages.add(
                BotChatMessage(
                    senderId = selectedPersona.id,
                    senderName = selectedPersona.displayName,
                    content = "Hey! I am ${selectedPersona.displayName}. ${selectedPersona.tagline}. How can I supercharge your workspace right now?"
                )
            )
        }
    }

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    val sendMessage: (String) -> Unit = { rawPrompt ->
        val text = rawPrompt.trim()
        if (text.isNotEmpty() && !isThinking) {
            val userMsg = BotChatMessage(
                senderId = "user",
                senderName = "You",
                content = text
            )
            messages.add(userMsg)
            promptInput = ""
            isThinking = true

            coroutineScope.launch {
                try {
                    val fileContext = if (activeTab != null) {
                        "Active File: ${activeTab.fileItem.name} (${activeTab.fileItem.path})\n```\n${activeTab.content}\n```\n"
                    } else {
                        "No active file open.\n"
                    }

                    val tabsContext = "Open Editor Tabs: ${openTabs.joinToString { it.fileItem.name }.ifEmpty { "None" }}\n"

                    val fullPrompt = """
                        ${selectedPersona.systemRole}
                        
                        Workspace Context:
                        $fileContext
                        $tabsContext
                        
                        User Request:
                        $text
                        
                        Give a clear, direct answer with code wrapped in ``` if applicable.
                    """.trimIndent()

                    val responseText = aiService.askAI(fullPrompt)
                        ?: aiService.chatResponse(text)

                    // Extract code block if present
                    val extracted = if (responseText.contains("```")) {
                        responseText.substringAfter("```")
                            .substringAfter("\n")
                            .substringBefore("```")
                    } else null

                    messages.add(
                        BotChatMessage(
                            senderId = selectedPersona.id,
                            senderName = selectedPersona.displayName,
                            content = responseText,
                            extractedCode = extracted
                        )
                    )
                } catch (e: Exception) {
                    messages.add(
                        BotChatMessage(
                            senderId = selectedPersona.id,
                            senderName = selectedPersona.displayName,
                            content = "Error communicating with AI model: ${e.message}"
                        )
                    )
                } finally {
                    isThinking = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(10.dp)
            .testTag("ide_grok_bots_view")
    ) {
        // ================= BOT PERSONA SELECTOR =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            GrokBotPersona.values().forEach { persona ->
                val isSelected = selectedPersona == persona
                val personaColor = Color(persona.avatarColorHex)

                Surface(
                    color = if (isSelected) personaColor.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) personaColor
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .clickable { selectedPersona = persona }
                        .testTag("grok_bot_picker_${persona.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = persona.icon,
                            contentDescription = persona.displayName,
                            tint = if (isSelected) personaColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = persona.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            color = if (isSelected) personaColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Active Bot Tagline Banner
        Surface(
            color = Color(selectedPersona.avatarColorHex).copy(alpha = 0.12f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(selectedPersona.avatarColorHex).copy(alpha = 0.3f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(selectedPersona.avatarColorHex))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = selectedPersona.tagline,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = Color(selectedPersona.avatarColorHex)
                )
                Spacer(modifier = Modifier.weight(1f))
                if (activeTab != null) {
                    Text(
                        text = "@${activeTab.fileItem.name}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Bot-Tailored Prompt Chips
        val quickChips = when (selectedPersona) {
            GrokBotPersona.GROK_CODER -> listOf("Optimize algorithm", "Explain like I'm 5", "Make this 10x faster", "Witty code review")
            GrokBotPersona.CODE_ARCHITECT -> listOf("Design modular structure", "Clean Architecture review", "Extract reusable component", "Decouple layers")
            GrokBotPersona.BUG_HUNTER -> listOf("Audit null safety", "Find edge cases", "Security audit", "Check memory leaks")
            GrokBotPersona.TEST_SPECIALIST -> listOf("Generate unit tests", "Create mock data", "Edge case tests", "Refactor for testability")
            GrokBotPersona.WEB_SCRAPER -> listOf("Extract page DOM", "Scrape all links", "Extract JSON tables", "Extract script endpoints")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            quickChips.forEach { chipText ->
                SuggestionChip(
                    onClick = { sendMessage(chipText) },
                    label = { Text(chipText, fontSize = 10.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Multi-Turn Chat Conversation List
        LazyColumn(
            state = chatListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.senderId == "user"
                val personaColor = Color(selectedPersona.avatarColorHex)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    // Sender name
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isUser) MaterialTheme.colorScheme.primary else personaColor,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )

                    Surface(
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isUser) 12.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 12.dp
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            else personaColor.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.widthIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    fontFamily = if (msg.content.contains("```")) FontFamily.Monospace else FontFamily.Default
                                ),
                                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )

                            // Apply / Create File actions if code was returned
                            if (!isUser && msg.extractedCode != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onApplyCodeToEditor(msg.extractedCode)
                                            Toast.makeText(context, "Applied to active editor buffer", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = personaColor),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Apply to File", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val generatedName = "solution_${System.currentTimeMillis() % 1000}.kt"
                                            onCreateFileFromCode(generatedName, msg.extractedCode)
                                            Toast.makeText(context, "Created $generatedName", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("New File", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isThinking) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${selectedPersona.displayName} is thinking...",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = Color(selectedPersona.avatarColorHex)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Prompt Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptInput,
                onValueChange = { promptInput = it },
                placeholder = { Text("Ask ${selectedPersona.displayName}...", fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("ide_grok_bot_prompt_input")
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { sendMessage(promptInput) },
                enabled = !isThinking && promptInput.isNotBlank(),
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(selectedPersona.avatarColorHex), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
