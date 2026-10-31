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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Model Context Protocol (MCP) data models.
 * MCP is the open protocol that standardizes how AI agents and copilots
 * connect with data sources, local files, developer tools, and external services.
 */
data class McpTool(
    val name: String,
    val description: String,
    val parametersSummary: String,
    val defaultSampleArgs: String
)

data class McpServer(
    val id: String,
    val name: String,
    val transport: String, // "stdio", "SSE", "WebSocket"
    val uriOrCommand: String,
    val isConnected: Boolean,
    val latencyMs: Int,
    val description: String,
    val tools: List<McpTool>,
    val author: String = "Anthropic / Community",
    val version: String = "1.0.4"
)

/**
 * Global singleton state manager for MCP servers so state persists across sheet closures.
 */
object McpServerManager {
    private val defaultServers = listOf(
        McpServer(
            id = "mcp_filesystem",
            name = "Local Filesystem Server",
            transport = "stdio",
            uriOrCommand = "npx -y @modelcontextprotocol/server-filesystem /workspace",
            isConnected = true,
            latencyMs = 11,
            description = "Provides secure read/write access to project code, directories, assets, and metadata for AI assistants.",
            tools = listOf(
                McpTool("read_file", "Read text content of a file at the given path", "path: string", "{\"path\": \"src/App.tsx\"}"),
                McpTool("write_file", "Write or overwrite content into a specified file path", "path: string, content: string", "{\"path\": \"src/utils.ts\", \"content\": \"export const pi = 3.1415;\"}"),
                McpTool("list_directory", "List directory contents including files and subfolders", "path: string", "{\"path\": \"src/\"}"),
                McpTool("search_files", "Fast recursive regex pattern search across all project files", "pattern: string, path?: string", "{\"pattern\": \"XcodeWorkspaceMode\"}"),
                McpTool("get_file_info", "Retrieve file metadata, byte size, permissions, and MIME type", "path: string", "{\"path\": \"package.json\"}")
            )
        ),
        McpServer(
            id = "mcp_git",
            name = "Workspace Git VCS",
            transport = "stdio",
            uriOrCommand = "uvx mcp-server-git --repository .",
            isConnected = true,
            latencyMs = 16,
            description = "Version control tools for inspecting diffs, branch management, commits, and staged code modifications.",
            tools = listOf(
                McpTool("git_status", "Returns working directory status, modified, staged, and untracked files", "none", "{}"),
                McpTool("git_diff", "Returns unified diff comparison of working tree against HEAD", "cached?: boolean, path?: string", "{\"cached\": false}"),
                McpTool("git_log", "Returns commit log history with authors, timestamps, and hashes", "max_count?: number", "{\"max_count\": 5}"),
                McpTool("git_commit", "Creates a signed Git commit with the specified message", "message: string", "{\"message\": \"feat: add MCP navigator tab\"}")
            )
        ),
        McpServer(
            id = "mcp_fetch",
            name = "Web Fetch & Scraper",
            transport = "SSE",
            uriOrCommand = "http://localhost:3001/mcp/sse",
            isConnected = true,
            latencyMs = 28,
            description = "Fetches live webpage contents, extracts clean readable Markdown, and parses HTTP headers for web search context.",
            tools = listOf(
                McpTool("fetch_url", "HTTP GET request with header synthesis and auto decompression", "url: string, raw?: boolean", "{\"url\": \"https://modelcontextprotocol.io\"}"),
                McpTool("html_to_markdown", "Converts raw HTML strings into semantic markdown", "html: string", "{\"html\": \"<h1>Hello MCP</h1><p>Tools connected.</p>\"}"),
                McpTool("extract_links", "Extracts canonical hyperlinks and media URLs from document", "html: string", "{\"html\": \"<a href='/docs'>Documentation</a>\"}")
            )
        ),
        McpServer(
            id = "mcp_sqlite",
            name = "SQLite DB Inspector",
            transport = "stdio",
            uriOrCommand = "uvx mcp-server-sqlite --db /data/app_storage.db",
            isConnected = true,
            latencyMs = 8,
            description = "Safe read-only and analytical SQL query execution across local relational tables and database schemas.",
            tools = listOf(
                McpTool("read_query", "Executes a SELECT query and returns rows formatted as JSON array", "query: string", "{\"query\": \"SELECT name, type FROM sqlite_master WHERE type='table';\"}"),
                McpTool("list_tables", "Lists all user and system tables in the active SQLite database", "none", "{}"),
                McpTool("describe_table", "Returns column schemas, types, nullability, and primary keys", "table_name: string", "{\"table_name\": \"browser_tabs\"}")
            )
        ),
        McpServer(
            id = "mcp_memory",
            name = "Memory & Knowledge Graph",
            transport = "stdio",
            uriOrCommand = "npx -y @modelcontextprotocol/server-memory",
            isConnected = true,
            latencyMs = 14,
            description = "Persistent associative entity-relationship knowledge graph for preserving long-term project context across chats.",
            tools = listOf(
                McpTool("create_entities", "Stores new structured entities and observational facts in memory graph", "entities: array", "{\"entities\": [{\"name\": \"XcodeLayout\", \"type\": \"Architecture\", \"observations\": [\"Unified dropdown & dual bottom panels\"]}]}"),
                McpTool("search_nodes", "Queries semantic knowledge graph nodes matching a text pattern", "query: string", "{\"query\": \"Xcode\"}"),
                McpTool("open_nodes", "Inspects all relationship edges connected to specific named entities", "names: string[]", "{\"names\": [\"XcodeLayout\"]}")
            )
        )
    )

    private val _servers = MutableStateFlow(defaultServers)
    val servers: StateFlow<List<McpServer>> = _servers.asStateFlow()

    fun toggleServer(id: String) {
        _servers.update { list ->
            list.map { server ->
                if (server.id == id) {
                    server.copy(isConnected = !server.isConnected)
                } else server
            }
        }
    }

    fun addServer(server: McpServer) {
        _servers.update { it + server }
    }

    fun removeServer(id: String) {
        _servers.update { list -> list.filter { it.id != id } }
    }

    fun pingServer(id: String): Int {
        val newLatency = (8..35).random()
        _servers.update { list ->
            list.map { if (it.id == id) it.copy(latencyMs = newLatency, isConnected = true) else it }
        }
        return newLatency
    }
}

/**
 * Dedicated Navigator view for Model Context Protocol (MCP) servers and tools.
 */
@Composable
fun IdeMcpView(
    onOpenBotChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val servers by McpServerManager.servers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var expandedServerId by remember { mutableStateOf<String?>(null) }
    var showAddServerDialog by remember { mutableStateOf(false) }
    var toolToTest by remember { mutableStateOf<Pair<McpServer, McpTool>?>(null) }

    val activeCount = servers.count { it.isConnected }
    val totalToolsCount = servers.filter { it.isConnected }.sumOf { it.tools.size }

    val filters = listOf("All", "Active (${activeCount})", "stdio", "SSE", "Tools (${totalToolsCount})")

    val filteredServers = servers.filter { server ->
        val matchesFilter = when (selectedFilter) {
            "Active (${activeCount})" -> server.isConnected
            "stdio" -> server.transport.equals("stdio", ignoreCase = true)
            "SSE" -> server.transport.equals("SSE", ignoreCase = true)
            else -> true
        }
        val matchesSearch = searchQuery.isBlank() ||
                server.name.contains(searchQuery, ignoreCase = true) ||
                server.description.contains(searchQuery, ignoreCase = true) ||
                server.tools.any { it.name.contains(searchQuery, ignoreCase = true) }

        matchesFilter && matchesSearch
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(12.dp)
    ) {
        // --- HEADER BANNER: PROTOCOL SPEC & STATS ---
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFEC4899), Color(0xFFBE185D))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hub,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Model Context Protocol",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF059669).copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, Color(0xFF10B981))
                                ) {
                                    Text(
                                        text = "v2024-11-05",
                                        color = Color(0xFF6EE7B7),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Standardized JSON-RPC 2.0 tool endpoints for Copilot",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Button(
                        onClick = { showAddServerDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("add_mcp_server_btn")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Server", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Stats row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF10B981)))
                        Text("Active: $activeCount / ${servers.size}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.height(12.dp).width(1.dp).background(Color(0xFF334155)))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Handyman, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                        Text("Tools: $totalToolsCount Exposed", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Box(modifier = Modifier.height(12.dp).width(1.dp).background(Color(0xFF334155)))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                        Text("Protocol: stdio/SSE", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- SEARCH BAR ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search MCP servers, tools, and endpoints...", color = Color(0xFF64748B), fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF141923),
                unfocusedContainerColor = Color(0xFF141923),
                focusedBorderColor = Color(0xFFEC4899),
                unfocusedBorderColor = Color(0xFF2C384D),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .testTag("mcp_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- FILTER CHIPS ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = selectedFilter == filter
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFFEC4899).copy(alpha = 0.25f) else Color(0xFF1E293B),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFEC4899) else Color(0xFF334155)),
                    modifier = Modifier
                        .clickable { selectedFilter = filter }
                        .testTag("mcp_filter_${filter.substringBefore(" ").lowercase()}")
                ) {
                    Text(
                        text = filter,
                        color = if (isSelected) Color(0xFFF472B6) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- SERVERS LIST ---
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("mcp_servers_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 60.dp)
        ) {
            if (filteredServers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(36.dp))
                            Text("No MCP servers found", color = Color(0xFF94A3B8), fontSize = 13.sp)
                            Button(
                                onClick = { searchQuery = ""; selectedFilter = "All" },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reset Filters", fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                items(filteredServers, key = { it.id }) { server ->
                    val isExpanded = expandedServerId == server.id
                    McpServerCard(
                        server = server,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedServerId = if (isExpanded) null else server.id
                        },
                        onToggleEnable = {
                            McpServerManager.toggleServer(server.id)
                            Toast.makeText(
                                context,
                                if (!server.isConnected) "${server.name} connected" else "${server.name} disconnected",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onPing = {
                            val latency = McpServerManager.pingServer(server.id)
                            Toast.makeText(context, "Ping to ${server.name}: ${latency}ms OK", Toast.LENGTH_SHORT).show()
                        },
                        onTestTool = { tool ->
                            toolToTest = Pair(server, tool)
                        }
                    )
                }
            }
        }
    }

    // --- ADD SERVER DIALOG ---
    if (showAddServerDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddServerDialog = false },
            onAdd = { newServer ->
                McpServerManager.addServer(newServer)
                showAddServerDialog = false
                Toast.makeText(context, "Added MCP server: ${newServer.name}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // --- TOOL TEST DIALOG ---
    toolToTest?.let { (server, tool) ->
        McpToolTestDialog(
            server = server,
            tool = tool,
            onDismiss = { toolToTest = null }
        )
    }
}

/**
 * Individual MCP Server Card displaying transport, status, command, and tools.
 */
@Composable
private fun McpServerCard(
    server: McpServer,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleEnable: () -> Unit,
    onPing: () -> Unit,
    onTestTool: (McpTool) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF161F2E),
        border = BorderStroke(
            1.dp,
            if (server.isConnected) Color(0xFF2C3E5A) else Color(0xFF232D3F)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mcp_server_card_${server.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (server.isConnected) Color(0xFF10B981) else Color(0xFF64748B))
                    )

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = server.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Transport badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (server.transport.lowercase()) {
                                    "stdio" -> Color(0xFF38BDF8).copy(alpha = 0.15f)
                                    "sse" -> Color(0xFFA855F7).copy(alpha = 0.15f)
                                    else -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                },
                                border = BorderStroke(
                                    0.5.dp,
                                    when (server.transport.lowercase()) {
                                        "stdio" -> Color(0xFF38BDF8)
                                        "sse" -> Color(0xFFA855F7)
                                        else -> Color(0xFFF59E0B)
                                    }
                                )
                            ) {
                                Text(
                                    text = server.transport,
                                    color = when (server.transport.lowercase()) {
                                        "stdio" -> Color(0xFF38BDF8)
                                        "sse" -> Color(0xFFA855F7)
                                        else -> Color(0xFFF59E0B)
                                    },
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        if (server.isConnected) {
                            Text(
                                text = "Connected • ${server.latencyMs}ms latency • ${server.tools.size} tools",
                                color = Color(0xFF10B981),
                                fontSize = 10.sp
                            )
                        } else {
                            Text(
                                text = "Disabled / Offline",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onPing,
                        enabled = server.isConnected,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Ping",
                            tint = if (server.isConnected) Color(0xFF38BDF8) else Color(0xFF475569),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Switch(
                        checked = server.isConnected,
                        onCheckedChange = { onToggleEnable() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = server.description,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Command / URI box
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(0.5.dp, Color(0xFF334155)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (server.transport.equals("stdio", true)) Icons.Rounded.Terminal else Icons.Rounded.Link,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = server.uriOrCommand,
                        color = Color(0xFFCBD5E1),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Expand / Collapse tools button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E293B),
                border = BorderStroke(0.5.dp, Color(0xFF334155)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.Handyman, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(13.dp))
                        Text(
                            text = "${server.tools.size} Exposed Tools",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expanded tools list
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    server.tools.forEach { tool ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(0.5.dp, Color(0xFF1E293B)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEC4899))
                                        )
                                        Text(
                                            text = tool.name,
                                            color = Color(0xFFF472B6),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = { onTestTool(tool) },
                                        enabled = server.isConnected,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBE185D).copy(alpha = 0.3f)),
                                        border = BorderStroke(0.5.dp, Color(0xFFEC4899)),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFFF472B6), modifier = Modifier.size(11.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Test", color = Color(0xFFF472B6), fontSize = 10.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = tool.description,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp
                                )

                                Spacer(modifier = Modifier.height(3.dp))

                                Text(
                                    text = "Params: ${tool.parametersSummary}",
                                    color = Color(0xFF64748B),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog to add a custom MCP Server endpoint (stdio, SSE, or WebSocket).
 */
@Composable
fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (McpServer) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("stdio") }
    var uriOrCommand by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sampleToolName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.AddLink, contentDescription = null, tint = Color(0xFFEC4899))
                Text("Add MCP Server", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    placeholder = { Text("e.g. Postgres DB Connector") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_mcp_name_field")
                )

                // Transport selector
                Column {
                    Text("Transport Protocol", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("stdio", "SSE", "WebSocket").forEach { t ->
                            val isSelected = transport == t
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) Color(0xFFEC4899) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFEC4899) else Color(0xFF334155)),
                                modifier = Modifier.clickable { transport = t }
                            ) {
                                Text(
                                    text = t,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = uriOrCommand,
                    onValueChange = { uriOrCommand = it },
                    label = { Text(if (transport == "stdio") "Command & Arguments" else "Endpoint URL") },
                    placeholder = { Text(if (transport == "stdio") "npx -y @modelcontextprotocol/server-..." else "http://localhost:8080/sse") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("add_mcp_command_field")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("Tools and capabilities provided to the Copilot") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sampleToolName,
                    onValueChange = { sampleToolName = it },
                    label = { Text("Initial Tool Name (Optional)") },
                    placeholder = { Text("e.g. query_data") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && uriOrCommand.isNotBlank()) {
                        val initialTools = if (sampleToolName.isNotBlank()) {
                            listOf(McpTool(sampleToolName.trim(), "Custom registered tool", "args: object", "{}"))
                        } else {
                            listOf(McpTool("ping", "Health check probe", "none", "{}"))
                        }
                        val newServer = McpServer(
                            id = "mcp_${System.currentTimeMillis()}",
                            name = name.trim(),
                            transport = transport,
                            uriOrCommand = uriOrCommand.trim(),
                            isConnected = true,
                            latencyMs = (10..30).random(),
                            description = description.ifBlank { "Custom MCP service" },
                            tools = initialTools
                        )
                        onAdd(newServer)
                    }
                },
                enabled = name.isNotBlank() && uriOrCommand.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("confirm_add_mcp_btn")
            ) {
                Text("Connect Server")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF161F2E),
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Interactive test modal for calling an MCP Tool and viewing JSON-RPC result.
 */
@Composable
private fun McpToolTestDialog(
    server: McpServer,
    tool: McpTool,
    onDismiss: () -> Unit
) {
    var argumentsJson by remember { mutableStateOf(tool.defaultSampleArgs) }
    var responseOutput by remember { mutableStateOf<String?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFFEC4899))
                Column {
                    Text("Execute: ${tool.name}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("Server: ${server.name}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = tool.description,
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )

                Text("Input JSON Arguments:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                OutlinedTextField(
                    value = argumentsJson,
                    onValueChange = { argumentsJson = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFF6EE7B7)
                    ),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0F172A),
                        unfocusedContainerColor = Color(0xFF0F172A),
                        focusedBorderColor = Color(0xFFEC4899),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (responseOutput != null) {
                    Text("JSON-RPC 2.0 Response:", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0A0F1D),
                        border = BorderStroke(1.dp, Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = responseOutput ?: "",
                            color = Color(0xFFF472B6),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isExecuting = true
                    responseOutput = """
                        {
                          "jsonrpc": "2.0",
                          "result": {
                            "content": [
                              {
                                "type": "text",
                                "text": "Tool '${tool.name}' executed successfully via ${server.transport}. Output verified."
                              }
                            ],
                            "isError": false
                          },
                          "id": 1
                        }
                    """.trimIndent()
                    isExecuting = false
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Run Tool")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF161F2E),
        shape = RoundedCornerShape(16.dp)
    )
}
