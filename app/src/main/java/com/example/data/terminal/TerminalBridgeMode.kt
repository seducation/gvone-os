package com.example.data.terminal

/**
 * Terminal Bridge Modes:
 * - CHAT: Bridges terminal inputs to the AI Chatbot (Gemini API) -> "CHAT BRIDGE: ON"
 * - WEB: Bridges terminal inputs to the active webpage / Web App -> "WEB BRIDGE: ON"
 * - OFF: Disables bridge; pure local shell command environment -> "BRIDGE: OFF"
 *
 * This mode is completely independent of the browser's global bidirectional bridge
 * and general bridge apply-to-all settings.
 */
enum class TerminalBridgeMode {
    CHAT,
    WEB
}
