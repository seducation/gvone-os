package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.files.FileType
import com.example.data.files.GVONEFileSystem
import com.example.data.files.StorageLocation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GVONEFileSystemUnitTest {

    private lateinit var context: Context
    private lateinit var fileSystem: GVONEFileSystem

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        fileSystem = GVONEFileSystem(context)
    }

    @Test
    fun testDefaultDirectoriesExist() = runBlocking {
        val rootItems = fileSystem.listFiles(StorageLocation.MY_FILES, "")
        val folderNames = rootItems.filter { it.isDirectory }.map { it.name }

        assertTrue(folderNames.contains("Documents"))
        assertTrue(folderNames.contains("Projects"))
        assertTrue(folderNames.contains("Images"))
        assertTrue(folderNames.contains("Downloads"))
        assertTrue(folderNames.contains("GVONE"))
    }

    @Test
    fun testCreateAndReadFile() = runBlocking {
        val result = fileSystem.createFile("Documents", "test_notes", "md", "# Hello GVONE\nUnified FS test")
        assertNotNull(result)
        assertEquals("test_notes.md", result.name)
        assertEquals(FileType.MARKDOWN, result.fileType)

        val content = fileSystem.readFileContent("Documents/test_notes.md")
        assertEquals("# Hello GVONE\nUnified FS test", content)
    }

    @Test
    fun testFileFavoriteToggle() = runBlocking {
        val file = fileSystem.createFile("Projects", "sample_app", "py", "print('hello world')")
        assertNotNull(file)
        assertFalse(file.isFavorite)

        val toggled = fileSystem.toggleFavorite("Projects/sample_app.py")
        assertTrue(toggled)

        val favorites = fileSystem.listFiles(StorageLocation.FAVORITES)
        assertTrue(favorites.any { it.name == "sample_app.py" })
    }

    @Test
    fun testSearchFiles() = runBlocking {
        fileSystem.createFile("Documents", "unique_searchable_file", "txt", "GVONE specifications")

        val searchResults = fileSystem.listFiles(StorageLocation.MY_FILES, "Documents", searchQuery = "unique_searchable")
        assertEquals(1, searchResults.size)
        assertEquals("unique_searchable_file.txt", searchResults[0].name)
    }

    @Test
    fun testDeleteFile() = runBlocking {
        val file = fileSystem.createFile("Documents", "temp_to_delete", "txt", "delete me")
        assertNotNull(file)

        val deleted = fileSystem.deleteItem("Documents/temp_to_delete.txt")
        assertTrue(deleted)

        val check = fileSystem.getFile("Documents/temp_to_delete.txt")
        assertFalse(check.exists())
    }

    @Test
    fun testRenameFile() = runBlocking {
        fileSystem.createFile("Documents", "rename_old", "txt", "content")
        val renamed = fileSystem.renameItem("Documents/rename_old.txt", "rename_new.txt")
        assertNotNull(renamed)
        assertEquals("rename_new.txt", renamed?.name)

        assertFalse(fileSystem.getFile("Documents/rename_old.txt").exists())
        assertTrue(fileSystem.getFile("Documents/rename_new.txt").exists())
    }

    @Test
    fun testFileTypeDetection() {
        assertEquals(FileType.PDF, fileSystem.determineFileType("document.pdf", false))
        assertEquals(FileType.MARKDOWN, fileSystem.determineFileType("readme.md", false))
        assertEquals(FileType.JSON, fileSystem.determineFileType("data.json", false))
        assertEquals(FileType.CODE, fileSystem.determineFileType("main.kt", false))
        assertEquals(FileType.IMAGE, fileSystem.determineFileType("photo.png", false))
        assertEquals(FileType.TEXT, fileSystem.determineFileType("notes.txt", false))
        assertEquals(FileType.FOLDER, fileSystem.determineFileType("Projects", true))
    }
}
