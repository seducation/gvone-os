package com.example

import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import com.example.ui.components.suggestions.QuickPrompt
import com.example.ui.components.suggestions.SelectedItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalFileSelectionUnitTest {

    @Test
    fun `TerminalLine supports fileUri attachment`() {
        val fileUri = "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Freport.pdf"
        val line = TerminalLine(
            id = "cmd_file_1",
            text = "Summarize this PDF document",
            type = TerminalLineType.COMMAND,
            fileUri = fileUri,
            fileName = "report.pdf",
            fileSize = 1048576L,
            fileMimeType = "application/pdf"
        )

        assertEquals("Summarize this PDF document", line.text)
        assertEquals(TerminalLineType.COMMAND, line.type)
        assertEquals(fileUri, line.fileUri)
        assertEquals("report.pdf", line.fileName)
        assertEquals(1048576L, line.fileSize)
        assertEquals("application/pdf", line.fileMimeType)
        assertNotNull(line.fileUri)
    }

    @Test
    fun `QuickPrompt selected file item configuration is correct`() {
        val fileName = "project_specs.txt"
        val fileUri = "content://media/external/file/42"
        val prompt = QuickPrompt(
            id = "file_123_456",
            title = "File: $fileName",
            promptText = "/files $fileUri",
            category = "File",
            type = SelectedItemType.FILE,
            uriOrUrl = fileUri
        )

        assertEquals(SelectedItemType.FILE, prompt.type)
        assertEquals("File: project_specs.txt", prompt.title)
        assertEquals(fileUri, prompt.uriOrUrl)
        assertEquals("/files $fileUri", prompt.promptText)
    }

    @Test
    fun `Terminal command with attached file formats correctly`() {
        val fileName = "data_sheet.csv"
        val fileUri = "content://media/external/file/999"
        val userPrompt = "Parse columns and calculate total"

        val formattedCmd = if (userPrompt.isBlank()) {
            "[File: $fileName]"
        } else {
            "$userPrompt [File: $fileName]"
        }

        val cmdLine = TerminalLine(
            id = "test_cmd_file_2",
            text = formattedCmd,
            type = TerminalLineType.COMMAND,
            fileUri = fileUri,
            fileName = fileName
        )

        assertTrue(cmdLine.text.contains("[File: data_sheet.csv]"))
        assertTrue(cmdLine.text.contains("Parse columns and calculate total"))
        assertEquals(fileUri, cmdLine.fileUri)
        assertEquals(fileName, cmdLine.fileName)
    }
}
