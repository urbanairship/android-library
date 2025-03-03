/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import com.urbanairship.BaseTestCase
import com.urbanairship.TestApplication
import com.urbanairship.json.jsonMapOf
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

    private val dataStore = RemoteDataStore(
        context = TestApplication.getApplication(),
        appKey = "appKey",
        dbName = "test",
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
    }

    /**
     * Test getting payloads
     */
    @Test
    public fun testGetPayloads() {
        dataStore.savePayloads(PAYLOADS)

        var savedPayloads = dataStore.getPayloads(mutableListOf("type", "otherType"))
        assertNotNull(savedPayloads)
        assertEquals(PAYLOADS, savedPayloads)

        savedPayloads = dataStore.getPayloads(mutableListOf("type"))
        assertEquals(1, savedPayloads.size.toLong())
        assertEquals("type", savedPayloads.iterator().next().type)
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
    }

    private companion object {
        private var PAYLOADS = setOf(
            RemoteDataPayload("type", 123, jsonMapOf("foo" to "bar"), RemoteDataInfo("some url", "some last modified", RemoteDataSource.APP)),
            RemoteDataPayload("otherType", 123, jsonMapOf("baz" to "boz"), null)
        )
    }
}
