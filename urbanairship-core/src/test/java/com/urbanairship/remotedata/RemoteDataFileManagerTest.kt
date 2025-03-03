package com.urbanairship.remotedata

import com.urbanairship.BaseTestCase
import com.urbanairship.TestApplication
import com.urbanairship.json.jsonMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.UUID


@OptIn(ExperimentalCoroutinesApi::class)
public class RemoteDataFileManagerTest(): BaseTestCase() {
    @Rule
    @JvmField
    public val tempFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var manager: RemoteDataFileManager
    private lateinit var dataDir: File

    @Before
    public fun setUp() {
        dataDir = tempFolder.newFolder()
        dataDir.mkdirs()

        manager = RemoteDataFileManager(
            context = TestApplication.getApplication(),
            dataDirectory = dataDir
        )
    }

    @Test
    public fun testSave() {
        // Save data
        val filename = manager.generateDataFilePath()
        val result = manager.save(filename, TEST_DATA)
        assertTrue(result.isSuccess)

        // Verify filesystem
        dataDir.resolve(filename).run {
            assertTrue(exists())
            assertEquals(TEST_DATA.toString(), readText())
        }
    }

    @Test
    public fun testLoadSucceeds() {
        // Generate a saved data file
        val filename = manager.generateDataFilePath()
        dataDir.resolve(filename).writeText(TEST_DATA.toString())

        // Verify manager is able to load the data back from the file
        val result = manager.load(filename)
        assertTrue(result.isSuccess)
        assertEquals(TEST_DATA, result.getOrThrow())
    }


    @Test
    public fun testLoadFileDoesNotExist() {
        // Load a file that doesn't exist
        val result = manager.load("does-not-exist.json")

        // Verify manager returns a failure with the expected exception type
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    public fun testDeleteAll() {
        // Generate two saved data files
        repeat(2) {
            val filename = manager.generateDataFilePath()
            dataDir.resolve(filename).writeText(TEST_DATA.toString())
        }

        // Verify saved data, before delete
        assertEquals(2, dataDir.listFiles()?.size)

        // Verify manager is able to delete the data
        assertTrue(manager.deleteAll())

        // Verify all saved data was deleted
        assertFalse(dataDir.exists())
    }

    @Test
    public fun testGenerateDataFilePath() {
        val filename = manager.generateDataFilePath()

        // Verify filename has .json extension
        assertTrue(filename.endsWith(".json"))

        // Verify filename is a valid UUID
        val withoutExt = filename.substringBeforeLast(".")
        val uuid = UUID.fromString(withoutExt)
        assertEquals(36, uuid.toString().length)
    }

    private companion object {
        private val TEST_DATA = jsonMapOf("foo" to "bar")
    }
}
