/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import com.urbanairship.BaseTestCase
import com.urbanairship.TestApplication
import com.urbanairship.json.jsonMapOf
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
public class RemoteDataStoreTest() : BaseTestCase() {

    private val testDispatcher = StandardTestDispatcher()

    private val dataFileManager = spyk(RemoteDataFileManager(TestApplication.getApplication()))

    private val dataStore = RemoteDataStore(
        context = TestApplication.getApplication(),
        appKey = "appKey",
        dbName = "test",
        dataFileManager = dataFileManager
    )

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun teardown() {
        dataStore.run {
            deletePayloads()
            close()
        }

        Dispatchers.resetMain()
    }

    /**
     * Test saving payloads
     */
    @Test
    public fun testSavePayloads() {
        val success = dataStore.savePayloads(PAYLOADS)
        assertTrue(success)

        verify(exactly = PAYLOADS.size) {
            dataFileManager.save(any(), any())
        }
    }

    /**
     * Test getting payloads
     */
    @Test
    public fun testGetPayloads() {
        dataStore.savePayloads(PAYLOADS)

        verify(exactly = PAYLOADS.size) {
            dataFileManager.save(any(), any())
        }

        var savedPayloads = dataStore.getPayloads(mutableListOf("type", "otherType"))
        assertNotNull(savedPayloads)
        assertEquals(PAYLOADS, savedPayloads)

        verify(exactly = 2) {
            dataFileManager.load(any())
        }

        savedPayloads = dataStore.getPayloads(mutableListOf("type"))
        assertEquals(1, savedPayloads.size.toLong())
        assertEquals("type", savedPayloads.iterator().next().type)

        verify(exactly = 3) {
            dataFileManager.load(any())
        }
    }

    /**
     * Test deleting payloads.
     */
    @Test
    public fun testDeletePayloads() {
        dataStore.run {
            savePayloads(PAYLOADS)
            deletePayloads()
            assertTrue(getPayloads(listOf("type", "otherType")).isEmpty())
        }

        // Verify that we wrote the payloads to disk and then deleted them
        verifyOrder {
            repeat(PAYLOADS.size) {
                // One insert per payload
                dataFileManager.generateDataFilePath()
                dataFileManager.save(any(), any())
            }

            dataFileManager.deleteAll()
        }

        // Verify we didn't read any data from disk (no payloads for types)
        verify(exactly = 0) {
            dataFileManager.load(any())
            dataFileManager.load(any())
        }
    }

    private companion object {
        private var PAYLOADS = setOf(
            RemoteDataPayload("type", 123, jsonMapOf("foo" to "bar"), RemoteDataInfo("some url", "some last modified", RemoteDataSource.APP)),
            RemoteDataPayload("otherType", 123, jsonMapOf("baz" to "boz"), null)
        )
    }
}
