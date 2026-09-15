package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.agent.rules.*
import com.example.data.terminal.ConversationTaskNode
import org.json.JSONObject
import java.util.UUID

private val DialogBg = Color(0xFF0B0F17)
private val DialogCardBg = Color(0xFF131B2A)
private val DialogCardSub = Color(0xFF1C2638)
private val DialogBorder = Color(0xFF26354D)
private val CnsCyan = Color(0xFF38BDF8)
private val CnsPurple = Color(0xFFA855F7)
private val CnsGreen = Color(0xFF22C55E)
private val CnsAmber = Color(0xFFF59E0B)
private val CnsRed = Color(0xFFEF4444)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val TextMuted = Color(0xFF64748B)

enum class ManualRuleEditorTab(val label: String, val icon: String) {
    VISUAL_BUILDER("Visual Composer", "🎨"),
    RAW_JSON_CODE("Raw JSON / Code", "💻")
}

/**
 * Dialog allowing users to write rules manually either via a rich Visual Composer
 * or directly by editing/typing Raw JSON rule definitions with live syntax validation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualRuleWriterDialog(
    initialRule: RuleDefinition? = null,
    activeChats: List<ConversationTaskNode> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (RuleDefinition) -> Unit
) {
    var editorTab by remember { mutableStateOf(ManualRuleEditorTab.VISUAL_BUILDER) }

    // Visual Form State
    var ruleId by remember { mutableStateOf(initialRule?.id ?: "rule_user_${System.currentTimeMillis() % 10000}") }
    var ruleName by remember { mutableStateOf(initialRule?.name ?: "") }
    var ruleDescription by remember { mutableStateOf(initialRule?.description ?: "") }
    var priority by remember { mutableIntStateOf(initialRule?.priority ?: RulePriority.COMMAND) }
    var selectedEvent by remember { mutableStateOf(initialRule?.trigger?.event ?: RuleEvents.REQUEST_RECEIVED) }
    var selectedScope by remember { mutableStateOf(initialRule?.scope ?: "GLOBAL") }
    var selectedChatId by remember { mutableStateOf(initialRule?.chatId) }

    // Condition State
    val initialCondition = initialRule?.conditions as? SingleCondition
    var conditionField by remember { mutableStateOf(initialCondition?.field ?: "request.goal") }
    var conditionOperator by remember { mutableStateOf(initialCondition?.operator ?: ConditionOperator.CONTAINS) }
    var conditionValue by remember { mutableStateOf(initialCondition?.value?.toString() ?: "") }

    // Action State
    val initialAction = initialRule?.actions?.firstOrNull()
    var actionType by remember { mutableStateOf(initialAction?.type ?: RuleActionTypes.ROUTE_AGENT) }
    var actionTarget by remember { mutableStateOf(initialAction?.target ?: "CodingAgent") }
    var actionParamValue by remember { mutableStateOf(initialAction?.parameters?.get("reason")?.toString() ?: "") }

    // Raw JSON Code State
    val initialJsonString = remember(initialRule) {
        if (initialRule != null) {
            try {
                initialRule.toJson().toString(2)
            } catch (e: Exception) {
                "{}"
            }
        } else {
            generateSampleRuleJson(
                id = ruleId,
                name = "My Custom Manual Rule",
                event = RuleEvents.REQUEST_RECEIVED,
                field = "request.goal",
                operator = "contains",
                value = "analyze",
                action = RuleActionTypes.ROUTE_AGENT,
                target = "CodingAgent"
            )
        }
    }
    var rawJsonCode by remember { mutableStateOf(initialJsonString) }
    var jsonValidationStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Validation for Raw JSON
    fun validateRawJson(text: String): Pair<Boolean, String> {
        return try {
            val obj = JSONObject(text)
            if (!obj.has("name") || obj.getString("name").isBlank()) {
                Pair(false, "Rule must have a non-empty 'name'")
            } else if (!obj.has("trigger")) {
                Pair(false, "Rule must have a 'trigger' object")
            } else if (!obj.has("actions")) {
                Pair(false, "Rule must have an 'actions' array")
            } else {
                Pair(true, "Valid Rule: '${obj.optString("name")}' (P:${obj.optInt("priority", 0)})")
            }
        } catch (e: Exception) {
            Pair(false, "JSON Syntax Error: ${e.localizedMessage?.take(80) ?: "Invalid JSON"}")
        }
    }

    LaunchedEffect(rawJsonCode) {
        jsonValidationStatus = validateRawJson(rawJsonCode)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DialogBg,
            border = BorderStroke(1.5.dp, CnsPurple.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("manual_rule_writer_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CnsPurple.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, CnsPurple)
                        ) {
                            Text(
                                text = "✍️",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(6.dp)
                            )
                        }

                        Column {
                            Text(
                                text = if (initialRule != null) "Edit Rule Manually" else "Write Rule Manually",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Compose deterministic rules via Visual Builder or Raw JSON",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector Tabs: Visual Builder vs Raw JSON Code
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ManualRuleEditorTab.values().forEach { tab ->
                        val isSelected = editorTab == tab
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) CnsPurple.copy(alpha = 0.25f) else DialogCardBg,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) CnsPurple else DialogBorder
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (tab == ManualRuleEditorTab.RAW_JSON_CODE && editorTab == ManualRuleEditorTab.VISUAL_BUILDER) {
                                        // Sync visual state to raw JSON
                                        val constructed = RuleDefinition(
                                            id = ruleId.ifBlank { "rule_user_${System.currentTimeMillis() % 10000}" },
                                            name = ruleName.ifBlank { "Manual Rule" },
                                            description = ruleDescription,
                                            priority = priority,
                                            trigger = RuleTrigger(event = selectedEvent),
                                            conditions = SingleCondition(
                                                field = conditionField,
                                                operator = conditionOperator,
                                                value = conditionValue
                                            ),
                                            actions = listOf(
                                                RuleAction(
                                                    type = actionType,
                                                    target = actionTarget.ifBlank { null },
                                                    parameters = if (actionParamValue.isNotBlank()) mapOf("reason" to actionParamValue) else emptyMap()
                                                )
                                            ),
                                            scope = selectedScope,
                                            chatId = if (selectedScope == "CHAT") selectedChatId else null
                                        )
                                        rawJsonCode = constructed.toJson().toString(2)
                                    }
                                    editorTab = tab
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = tab.icon, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = tab.label,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFFF3E8FF) else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (editorTab) {
                        ManualRuleEditorTab.VISUAL_BUILDER -> {
                            VisualRuleComposer(
                                ruleId = ruleId, onRuleIdChange = { ruleId = it },
                                ruleName = ruleName, onRuleNameChange = { ruleName = it },
                                ruleDescription = ruleDescription, onRuleDescriptionChange = { ruleDescription = it },
                                priority = priority, onPriorityChange = { priority = it },
                                selectedEvent = selectedEvent, onEventChange = { selectedEvent = it },
                                selectedScope = selectedScope, onScopeChange = { selectedScope = it },
                                selectedChatId = selectedChatId, onChatIdChange = { selectedChatId = it },
                                activeChats = activeChats,
                                conditionField = conditionField, onConditionFieldChange = { conditionField = it },
                                conditionOperator = conditionOperator, onConditionOperatorChange = { conditionOperator = it },
                                conditionValue = conditionValue, onConditionValueChange = { conditionValue = it },
                                actionType = actionType, onActionTypeChange = { actionType = it },
                                actionTarget = actionTarget, onActionTargetChange = { actionTarget = it },
                                actionParamValue = actionParamValue, onActionParamValueChange = { actionParamValue = it }
                            )
                        }

                        ManualRuleEditorTab.RAW_JSON_CODE -> {
                            RawJsonRuleEditor(
                                rawJson = rawJsonCode,
                                onJsonChange = { rawJsonCode = it },
                                validationStatus = jsonValidationStatus,
                                onApplyTemplate = { templateJson ->
                                    rawJsonCode = templateJson
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Footer Buttons: Cancel & Save Rule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DialogBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel", fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }

                    Button(
                        onClick = {
                            if (editorTab == ManualRuleEditorTab.VISUAL_BUILDER) {
                                val nameToUse = ruleName.trim().ifBlank { "Custom Rule #${System.currentTimeMillis() % 10000}" }
                                val finalRule = RuleDefinition(
                                    id = ruleId.trim().ifBlank { "rule_user_${System.currentTimeMillis() % 10000}" },
                                    name = nameToUse,
                                    description = ruleDescription.trim().ifBlank { "Manually written rule" },
                                    priority = priority,
                                    enabled = true,
                                    trigger = RuleTrigger(event = selectedEvent),
                                    conditions = SingleCondition(
                                        field = conditionField.trim().ifBlank { "request.goal" },
                                        operator = conditionOperator,
                                        value = conditionValue.trim().ifBlank { "*" }
                                    ),
                                    actions = listOf(
                                        RuleAction(
                                            type = actionType,
                                            target = actionTarget.trim().ifBlank { null },
                                            parameters = if (actionParamValue.isNotBlank()) mapOf("reason" to actionParamValue.trim()) else emptyMap()
                                        )
                                    ),
                                    scope = selectedScope,
                                    chatId = if (selectedScope == "CHAT") (selectedChatId ?: activeChats.firstOrNull()?.id) else null
                                )
                                onSave(finalRule)
                            } else {
                                try {
                                    val obj = JSONObject(rawJsonCode)
                                    val finalRule = RuleDefinition.fromJson(obj)
                                    onSave(finalRule)
                                } catch (e: Exception) {
                                    // Fallback if parsing fails
                                    jsonValidationStatus = Pair(false, "Cannot save: ${e.localizedMessage}")
                                }
                            }
                        },
                        enabled = if (editorTab == ManualRuleEditorTab.RAW_JSON_CODE) {
                            jsonValidationStatus?.first == true
                        } else {
                            true
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CnsPurple),
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("save_manual_rule_btn")
                    ) {
                        Icon(imageVector = Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (initialRule != null) "Update Rule" else "Save & Register Rule",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF3E8FF)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Visual Form Composer for manual rule creation
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VisualRuleComposer(
    ruleId: String, onRuleIdChange: (String) -> Unit,
    ruleName: String, onRuleNameChange: (String) -> Unit,
    ruleDescription: String, onRuleDescriptionChange: (String) -> Unit,
    priority: Int, onPriorityChange: (Int) -> Unit,
    selectedEvent: String, onEventChange: (String) -> Unit,
    selectedScope: String, onScopeChange: (String) -> Unit,
    selectedChatId: String?, onChatIdChange: (String?) -> Unit,
    activeChats: List<ConversationTaskNode>,
    conditionField: String, onConditionFieldChange: (String) -> Unit,
    conditionOperator: ConditionOperator, onConditionOperatorChange: (ConditionOperator) -> Unit,
    conditionValue: String, onConditionValueChange: (String) -> Unit,
    actionType: String, onActionTypeChange: (String) -> Unit,
    actionTarget: String, onActionTargetChange: (String) -> Unit,
    actionParamValue: String, onActionParamValueChange: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    val eventOptions = remember {
        listOf(
            RuleEvents.REQUEST_RECEIVED to "Request Received (Goal)",
            RuleEvents.COMMAND_PARSED to "Command Parsed (/code, etc.)",
            RuleEvents.TOOL_STARTED to "Tool Started (Execution)",
            RuleEvents.APPROVAL_REQUIRED to "Approval Required (Safety)",
            RuleEvents.ALL to "* (All Events)"
        )
    }

    val operatorOptions = remember {
        listOf(
            ConditionOperator.CONTAINS to "contains",
            ConditionOperator.EQUALS to "equals (==)",
            ConditionOperator.STARTS_WITH to "starts_with",
            ConditionOperator.ENDS_WITH to "ends_with",
            ConditionOperator.REGEX to "regex match",
            ConditionOperator.NOT_EQUALS to "not_equals (!=)",
            ConditionOperator.EXISTS to "exists"
        )
    }

    val actionOptions = remember {
        listOf(
            RuleActionTypes.ROUTE_AGENT to "Route to Agent",
            RuleActionTypes.START_WORKFLOW to "Start Workflow",
            RuleActionTypes.REQUEST_APPROVAL to "Request Human Approval",
            RuleActionTypes.STOP_EXECUTION to "Emergency Halt / Stop",
            RuleActionTypes.CONTINUE_EXECUTION to "Allow & Continue",
            RuleActionTypes.CALL_TOOL to "Invoke Tool",
            RuleActionTypes.SET_CONTEXT to "Set Context Variable"
        )
    }

    val agentOptions = remember {
        listOf("CodingAgent", "BrowserAgent", "FileAgent", "VoiceAgent", "CommandAgent", "SearchAgent", "WebReviewAgent")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Identity & Metadata
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DialogCardBg,
            border = BorderStroke(1.dp, DialogBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "1. RULE IDENTITY & TRIGGER",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = CnsPurple
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = onRuleNameChange,
                        label = { Text("Rule Name", fontSize = 8.5.sp) },
                        placeholder = { Text("e.g. Route Python to CodingAgent", fontSize = 8.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CnsPurple,
                            unfocusedBorderColor = DialogBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("rule_name_input")
                    )

                    OutlinedTextField(
                        value = ruleId,
                        onValueChange = onRuleIdChange,
                        label = { Text("Rule ID", fontSize = 8.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CnsPurple,
                            unfocusedBorderColor = DialogBorder,
                            focusedTextColor = TextSecondary,
                            unfocusedTextColor = TextSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("rule_id_input")
                    )
                }

                OutlinedTextField(
                    value = ruleDescription,
                    onValueChange = onRuleDescriptionChange,
                    label = { Text("Description (Optional)", fontSize = 8.5.sp) },
                    placeholder = { Text("Explain when and why this rule executes...", fontSize = 8.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CnsPurple,
                        unfocusedBorderColor = DialogBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Trigger Event Chips
                Text(
                    text = "Trigger Event:",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = TextMuted
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(eventOptions) { (event, label) ->
                        val isSelected = selectedEvent == event
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) CnsPurple.copy(alpha = 0.25f) else DialogCardSub,
                            border = BorderStroke(0.5.dp, if (isSelected) CnsPurple else DialogBorder),
                            modifier = Modifier.clickable { onEventChange(event) }
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFFF3E8FF) else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Scope Selector: Global vs Chat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Scope:",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = TextMuted
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        val isGlobal = selectedScope == "GLOBAL"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isGlobal) CnsCyan.copy(alpha = 0.2f) else DialogCardSub,
                            border = BorderStroke(0.5.dp, if (isGlobal) CnsCyan else DialogBorder),
                            modifier = Modifier.clickable { onScopeChange("GLOBAL") }
                        ) {
                            Text(
                                text = "🌐 Global (All Chats)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = if (isGlobal) FontWeight.Bold else FontWeight.Normal,
                                color = if (isGlobal) CnsCyan else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }

                        val isChat = selectedScope == "CHAT"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isChat) CnsAmber.copy(alpha = 0.2f) else DialogCardSub,
                            border = BorderStroke(0.5.dp, if (isChat) CnsAmber else DialogBorder),
                            modifier = Modifier.clickable { onScopeChange("CHAT") }
                        ) {
                            Text(
                                text = "💬 Specific Chat",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = if (isChat) FontWeight.Bold else FontWeight.Normal,
                                color = if (isChat) CnsAmber else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Priority Band Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Priority: $priority",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        color = TextMuted
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        listOf(
                            1000 to "Emergency",
                            500 to "Security",
                            300 to "Command",
                            200 to "Routing",
                            50 to "Fallback"
                        ).forEach { (pVal, pLabel) ->
                            val isP = priority == pVal
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = if (isP) CnsPurple.copy(alpha = 0.3f) else DialogCardSub,
                                border = BorderStroke(0.5.dp, if (isP) CnsPurple else DialogBorder),
                                modifier = Modifier.clickable { onPriorityChange(pVal) }
                            ) {
                                Text(
                                    text = "$pVal ($pLabel)",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 7.5.sp,
                                    color = if (isP) Color(0xFFF3E8FF) else TextMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Condition Composer (IF)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DialogCardBg,
            border = BorderStroke(1.dp, DialogBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "2. CONDITION (IF CLAUSE)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFA78BFA)
                    )
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFF2E1065),
                        border = BorderStroke(0.5.dp, Color(0xFFA78BFA))
                    ) {
                        Text(
                            text = "IF: $conditionField ${conditionOperator.symbol} \"$conditionValue\"",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            color = Color(0xFFE9D5FF),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Quick Field Selector Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(listOf("request.goal", "request.command", "agent.name", "permission", "caller", "intent")) { f ->
                        val isF = conditionField == f
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = if (isF) Color(0xFF4C1D95) else DialogCardSub,
                            border = BorderStroke(0.5.dp, if (isF) Color(0xFFA78BFA) else DialogBorder),
                            modifier = Modifier.clickable { onConditionFieldChange(f) }
                        ) {
                            Text(
                                text = f,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                color = if (isF) Color(0xFFF3E8FF) else TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = conditionField,
                        onValueChange = onConditionFieldChange,
                        label = { Text("Field", fontSize = 8.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA78BFA),
                            unfocusedBorderColor = DialogBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = conditionValue,
                        onValueChange = onConditionValueChange,
                        label = { Text("Value to Match", fontSize = 8.5.sp) },
                        placeholder = { Text("e.g. python, search, git", fontSize = 8.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA78BFA),
                            unfocusedBorderColor = DialogBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1.3f)
                    )
                }

                // Operator Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(operatorOptions) { (op, label) ->
                        val isOp = conditionOperator == op
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = if (isOp) Color(0xFF6D28D9) else DialogCardSub,
                            border = BorderStroke(0.5.dp, if (isOp) Color(0xFFA78BFA) else DialogBorder),
                            modifier = Modifier.clickable { onConditionOperatorChange(op) }
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                color = if (isOp) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Action Composer (THEN)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DialogCardBg,
            border = BorderStroke(1.dp, DialogBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "3. ACTION (THEN CLAUSE)",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = Color(0xFF0C4A6E),
                        border = BorderStroke(0.5.dp, Color(0xFF38BDF8))
                    ) {
                        Text(
                            text = "THEN: $actionType -> $actionTarget",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            color = Color(0xFFE0F2FE),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                // Action Type Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(actionOptions) { (act, label) ->
                        val isAct = actionType == act
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = if (isAct) Color(0xFF0369A1) else DialogCardSub,
                            border = BorderStroke(0.5.dp, if (isAct) Color(0xFF38BDF8) else DialogBorder),
                            modifier = Modifier.clickable { onActionTypeChange(act) }
                        ) {
                            Text(
                                text = label,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.sp,
                                fontWeight = if (isAct) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAct) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = actionTarget,
                        onValueChange = onActionTargetChange,
                        label = { Text("Target Agent / Tool / Graph", fontSize = 8.5.sp) },
                        placeholder = { Text("e.g. CodingAgent", fontSize = 8.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = DialogBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = actionParamValue,
                        onValueChange = onActionParamValueChange,
                        label = { Text("Optional Parameter / Reason", fontSize = 8.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = DialogBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Quick Target Agent Chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(agentOptions) { ag ->
                        val isAg = actionTarget == ag
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = if (isAg) Color(0xFF0284C7) else DialogCardSub,
                            border = BorderStroke(0.5.dp, if (isAg) Color(0xFF38BDF8) else DialogBorder),
                            modifier = Modifier.clickable { onActionTargetChange(ag) }
                        ) {
                            Text(
                                text = ag,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 7.5.sp,
                                color = if (isAg) Color.White else TextMuted,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Raw JSON / Code Editor for power users to write complete rules manually
 */
@Composable
private fun RawJsonRuleEditor(
    rawJson: String,
    onJsonChange: (String) -> Unit,
    validationStatus: Pair<Boolean, String>?,
    onApplyTemplate: (String) -> Unit
) {
    val templates = remember {
        listOf(
            "Auto-Route Agent" to generateSampleRuleJson(
                id = "rule_route_coding_${System.currentTimeMillis() % 1000}",
                name = "Auto-Route Coding Goal",
                event = RuleEvents.REQUEST_RECEIVED,
                field = "request.goal",
                operator = "contains",
                value = "code",
                action = RuleActionTypes.ROUTE_AGENT,
                target = "CodingAgent"
            ),
            "Security Approval Gate" to generateSampleRuleJson(
                id = "rule_gate_dangerous_action_${System.currentTimeMillis() % 1000}",
                name = "Require Approval for Terminal Shell",
                event = RuleEvents.TOOL_STARTED,
                field = "toolName",
                operator = "equals",
                value = "ShellTool",
                action = RuleActionTypes.REQUEST_APPROVAL,
                target = "User"
            ),
            "Emergency Halt Block" to generateSampleRuleJson(
                id = "rule_emergency_block_${System.currentTimeMillis() % 1000}",
                name = "Emergency Block Destructive Shell",
                event = RuleEvents.REQUEST_RECEIVED,
                field = "request.rawInput",
                operator = "contains",
                value = "rm -rf",
                action = RuleActionTypes.STOP_EXECUTION,
                target = null
            ),
            "Slash Command Override" to generateSampleRuleJson(
                id = "rule_cmd_search_${System.currentTimeMillis() % 1000}",
                name = "Direct /search to SearchAgent",
                event = RuleEvents.COMMAND_PARSED,
                field = "request.command",
                operator = "equals",
                value = "/search",
                action = RuleActionTypes.ROUTE_AGENT,
                target = "SearchAgent"
            )
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Quick Template Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "TEMPLATES:",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = CnsPurple
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).padding(start = 6.dp)
            ) {
                items(templates) { (name, json) ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = DialogCardSub,
                        border = BorderStroke(0.5.dp, CnsPurple.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onApplyTemplate(json) }
                    ) {
                        Text(
                            text = name,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 7.5.sp,
                            color = Color(0xFFF3E8FF),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // Live Syntax Validation Banner
        if (validationStatus != null) {
            val (isValid, msg) = validationStatus
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isValid) Color(0xFF064E3B).copy(alpha = 0.4f) else Color(0xFF7F1D1D).copy(alpha = 0.4f),
                border = BorderStroke(1.dp, if (isValid) CnsGreen else CnsRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = if (isValid) "✅" else "⚠️", fontSize = 10.sp)
                    Text(
                        text = msg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        color = if (isValid) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Large Code / JSON Text Editor Area
        OutlinedTextField(
            value = rawJson,
            onValueChange = onJsonChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("raw_json_editor"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DialogCardBg,
                unfocusedContainerColor = DialogCardBg,
                focusedBorderColor = CnsPurple,
                unfocusedBorderColor = DialogBorder,
                focusedTextColor = Color(0xFFE2E8F0),
                unfocusedTextColor = Color(0xFFE2E8F0)
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 13.sp
            ),
            shape = RoundedCornerShape(8.dp)
        )
    }
}

/**
 * Generates sample formatted JSON string for custom rules
 */
private fun generateSampleRuleJson(
    id: String,
    name: String,
    event: String,
    field: String,
    operator: String,
    value: String,
    action: String,
    target: String?
): String {
    val targetField = if (target != null) ",\n      \"target\": \"$target\"" else ""
    return """{
  "id": "$id",
  "name": "$name",
  "description": "Manual custom rule",
  "priority": 300,
  "enabled": true,
  "trigger": {
    "event": "$event"
  },
  "conditions": {
    "field": "$field",
    "operator": "$operator",
    "value": "$value"
  },
  "actions": [
    {
      "type": "$action"$targetField
    }
  ],
  "scope": "GLOBAL"
}"""
}
