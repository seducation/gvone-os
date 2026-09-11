package com.example.agent.nodal

import org.json.JSONArray
import org.json.JSONObject

/**
 * Types of nodes supported in the GVONE n8n-style nodal architecture.
 */
enum class NodeType(val displayName: String) {
    TRIGGER("Trigger Node"),
    TRIGGER_COMMAND("Command Trigger"),
    COMMAND("Command Node"),
    INTENT("Intent Router"),
    INTENT_ROUTER("Intent Router"),
    PLANNER("Planner Node"),
    AGENT("Agent Node"),
    AGENT_NODE("Agent Node"),
    TOOL("Tool Node"),
    TOOL_NODE("Tool Node"),
    OBSERVER("Observer Node"),
    OBSERVER_NODE("Observer Node"),
    EVALUATOR("Evaluator / Verification Node"),
    EVALUATOR_NODE("Evaluator / Verification Node"),
    CONDITION("Condition Branch Node"),
    CONDITION_NODE("Condition Branch Node"),
    PARALLEL("Parallel Fan-out Node"),
    SEQUENCE("Sequential Pipeline Node"),
    LOOP("Loop Iterator Node"),
    APPROVAL("User Approval Node"),
    WAIT("Wait / Delay Node"),
    RETRY("Retry Node"),
    FALLBACK("Fallback Handler Node"),
    VOICE_NODE("Voice Node"),
    RESULT("Result Node"),
    RESULT_NODE("Result Node")
}

/**
 * An individual node within a workflow graph.
 */
data class NodalNode(
    val id: String,
    val type: NodeType,
    val name: String,
    val config: Map<String, Any?> = emptyMap(),
    val inputs: List<String> = listOf("in"),
    val outputs: List<String> = listOf("out")
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("name", name)
        put("config", JSONObject(config))
        put("inputs", JSONArray(inputs))
        put("outputs", JSONArray(outputs))
    }

    companion object {
        fun fromJson(json: JSONObject): NodalNode {
            val configMap = mutableMapOf<String, Any?>()
            val configObj = json.optJSONObject("config")
            if (configObj != null) {
                val keys = configObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    configMap[k] = configObj.get(k)
                }
            }
            val inputsList = mutableListOf<String>()
            val inputsArr = json.optJSONArray("inputs")
            if (inputsArr != null) {
                for (i in 0 until inputsArr.length()) inputsList.add(inputsArr.getString(i))
            } else inputsList.add("in")

            val outputsList = mutableListOf<String>()
            val outputsArr = json.optJSONArray("outputs")
            if (outputsArr != null) {
                for (i in 0 until outputsArr.length()) outputsList.add(outputsArr.getString(i))
            } else outputsList.add("out")

            return NodalNode(
                id = json.getString("id"),
                type = NodeType.valueOf(json.getString("type")),
                name = json.getString("name"),
                config = configMap,
                inputs = inputsList,
                outputs = outputsList
            )
        }
    }
}

/**
 * Directed connection between two nodes in the workflow.
 */
data class NodeConnection(
    val fromNodeId: String,
    val toNodeId: String,
    val outputPort: String = "out",
    val inputPort: String = "in"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fromNodeId", fromNodeId)
        put("toNodeId", toNodeId)
        put("outputPort", outputPort)
        put("inputPort", inputPort)
    }

    companion object {
        fun fromJson(json: JSONObject): NodeConnection = NodeConnection(
            fromNodeId = json.getString("fromNodeId"),
            toNodeId = json.getString("toNodeId"),
            outputPort = json.optString("outputPort", "out"),
            inputPort = json.optString("inputPort", "in")
        )
    }
}

/**
 * Complete n8n-style workflow definition.
 */
data class NodalWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val triggerCommands: List<String> = emptyList(),
    val nodes: List<NodalNode> = emptyList(),
    val connections: List<NodeConnection> = emptyList(),
    val isBuiltIn: Boolean = false,
    val isEnabled: Boolean = true
) {
    fun getNode(id: String): NodalNode? = nodes.find { it.id == id }

    fun getOutgoingConnections(nodeId: String): List<NodeConnection> =
        connections.filter { it.fromNodeId == nodeId }

    fun getTriggerNodes(): List<NodalNode> =
        nodes.filter { it.type == NodeType.TRIGGER_COMMAND }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("triggerCommands", JSONArray(triggerCommands))
        put("nodes", JSONArray().apply { nodes.forEach { put(it.toJson()) } })
        put("connections", JSONArray().apply { connections.forEach { put(it.toJson()) } })
        put("isBuiltIn", isBuiltIn)
        put("isEnabled", isEnabled)
    }

    companion object {
        fun fromJson(json: JSONObject): NodalWorkflow {
            val triggers = mutableListOf<String>()
            val triggersArr = json.optJSONArray("triggerCommands")
            if (triggersArr != null) {
                for (i in 0 until triggersArr.length()) triggers.add(triggersArr.getString(i))
            }
            val nodeList = mutableListOf<NodalNode>()
            val nodesArr = json.optJSONArray("nodes")
            if (nodesArr != null) {
                for (i in 0 until nodesArr.length()) nodeList.add(NodalNode.fromJson(nodesArr.getJSONObject(i)))
            }
            val connList = mutableListOf<NodeConnection>()
            val connsArr = json.optJSONArray("connections")
            if (connsArr != null) {
                for (i in 0 until connsArr.length()) connList.add(NodeConnection.fromJson(connsArr.getJSONObject(i)))
            }

            return NodalWorkflow(
                id = json.getString("id"),
                name = json.getString("name"),
                description = json.optString("description", ""),
                triggerCommands = triggers,
                nodes = nodeList,
                connections = connList,
                isBuiltIn = json.optBoolean("isBuiltIn", false),
                isEnabled = json.optBoolean("isEnabled", true)
            )
        }
    }
}

/**
 * Execution step log for observability and debugging.
 */
data class NodalStepLog(
    val nodeId: String,
    val nodeName: String,
    val nodeType: NodeType,
    val status: String,
    val inputSummary: String = "",
    val outputSummary: String = "",
    val error: String? = null,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Mutable context passed through node execution.
 */
class NodalExecutionContext(
    val workflowId: String,
    val rawInput: String,
    val command: String,
    val queryArg: String,
    val parameters: Map<String, Any?> = emptyMap(),
    val variables: MutableMap<String, Any?> = mutableMapOf(),
    val stepLogs: MutableList<NodalStepLog> = mutableListOf(),
    var isCancelled: Boolean = false
) {
    fun getVariable(key: String): Any? = variables[key]
    fun setVariable(key: String, value: Any?) {
        variables[key] = value
    }
}

/**
 * Final result of a nodal workflow execution.
 */
data class NodalWorkflowResult(
    val workflowId: String,
    val success: Boolean,
    val finalOutput: Any? = null,
    val error: String? = null,
    val logs: List<NodalStepLog> = emptyList(),
    val durationMs: Long = 0L
)
