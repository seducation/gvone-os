package com.example.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.files.GVONEFileSystem
import com.example.data.terminal.TerminalCommandExecutor
import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType

@Composable
fun GVONEMainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fileSystem = remember { GVONEFileSystem(context) }
    val executor = remember { TerminalCommandExecutor(context, fileSystem, scope) }

    val lines = remember {
        mutableStateListOf(
            TerminalLine(
                text = "GVONE Terminal Core v2.4.0 (arm64-v8a)",
                type = TerminalLineType.SYSTEM
            ),
            TerminalLine(
                text = "Multimodal binary payload receiver & image renderer initialized.",
                type = TerminalLineType.INFO
            ),
            TerminalLine(
                text = "Storage directory: ~/Downloads/ (Local Sandboxed FS)",
                type = TerminalLineType.SYSTEM
            ),
            TerminalLine(
                text = "Type 'help' or '/receive <url>' to test image reception.",
                type = TerminalLineType.SUCCESS
            )
        )
    }

    var commandInput by remember { mutableStateOf("") }

    TerminalScreen(
        lines = lines,
        commandInput = commandInput,
        onCommandInputChanged = { commandInput = it },
        onExecuteCommand = { cmd ->
            val inputToRun = cmd
            commandInput = ""
            executor.execute(
                input = inputToRun,
                appendLine = { line -> lines.add(line) },
                clearLines = { lines.clear() }
            )
        },
        modifier = Modifier.fillMaxSize()
    )
}
