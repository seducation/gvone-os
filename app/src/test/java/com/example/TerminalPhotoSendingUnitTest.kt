package com.example

import com.example.data.terminal.TerminalLine
import com.example.data.terminal.TerminalLineType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPhotoSendingUnitTest {

    @Test
    fun `TerminalLine supports imageUri attachment`() {
        val photoUri = "content://media/external/images/media/12345"
        val line = TerminalLine(
            id = "cmd_100",
            text = "Analyze this diagram",
            type = TerminalLineType.COMMAND,
            imageUri = photoUri
        )

        assertEquals("Analyze this diagram", line.text)
        assertEquals(TerminalLineType.COMMAND, line.type)
        assertEquals(photoUri, line.imageUri)
        assertNotNull(line.imageUri)
    }

    @Test
    fun `Terminal command with attached photo formats correctly`() {
        val photoName = "architecture.png"
        val photoUri = "file:///storage/emulated/0/DCIM/architecture.png"
        val userPrompt = "What is the flow in this architecture diagram?"

        val formattedCmd = if (userPrompt.isBlank()) {
            "[Photo: $photoName]"
        } else {
            "$userPrompt [Photo: $photoName]"
        }

        val cmdLine = TerminalLine(
            id = "test_cmd_1",
            text = formattedCmd,
            type = TerminalLineType.COMMAND,
            imageUri = photoUri
        )

        assertTrue(cmdLine.text.contains("[Photo: architecture.png]"))
        assertTrue(cmdLine.text.contains("What is the flow in this architecture diagram?"))
        assertEquals(photoUri, cmdLine.imageUri)
    }

    @Test
    fun `Terminal command with blank text defaults to photo identifier`() {
        val photoName = "snapshot.jpg"
        val photoUri = "content://media/external/images/media/99"
        val userPrompt = "   "

        val formattedCmd = if (userPrompt.isBlank()) {
            "[Photo: $photoName]"
        } else {
            "$userPrompt [Photo: $photoName]"
        }

        val cmdLine = TerminalLine(
            id = "test_cmd_2",
            text = formattedCmd,
            type = TerminalLineType.COMMAND,
            imageUri = photoUri
        )

        assertEquals("[Photo: snapshot.jpg]", cmdLine.text)
        assertEquals(photoUri, cmdLine.imageUri)
    }
}
