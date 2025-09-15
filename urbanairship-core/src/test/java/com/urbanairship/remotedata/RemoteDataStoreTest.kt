/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestApplication
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import java.util.Arrays
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RemoteDataStoreTest {

    private var dataStore = RemoteDataStore(TestApplication.getApplication(), "appKey", "test")
    private var payloads = setOf(
        RemoteDataPayload(
            type = "type",
            timestamp = 123,
            data = jsonMapOf("foo" to "bar"),
            remoteDataInfo = RemoteDataInfo("some url", "some last modified", RemoteDataSource.APP)
        ),
        RemoteDataPayload(
            type = "otherType",
            timestamp = 123,
            data = jsonMapOf("baz" to "boz"),
            remoteDataInfo = null
        )
    )

    @After
    public fun teardown() {
        dataStore.deletePayloads()
        dataStore.close()
    }

    /**
     * Test saving payloads
     */
    @Test
    public fun testSavePayloads() {
        val success = dataStore.savePayloads(payloads)
        Assert.assertTrue(success)
    }

    /**
     * Test getting payloads
     */
    @Test
    public fun testGetPayloads() {
        dataStore.savePayloads(payloads)
        var savedPayloads = dataStore.getPayloads(listOf("type", "otherType"))
        Assert.assertNotNull(savedPayloads)
        Assert.assertEquals(payloads, savedPayloads)

        savedPayloads = dataStore.getPayloads(listOf("type"))
        Assert.assertEquals(1, savedPayloads.size.toLong())
        Assert.assertEquals("type", savedPayloads.iterator().next().type)
    }

    /**
     * Test deleting payloads.
     */
    @Test
    public fun testDeletePayloads() {
        dataStore.savePayloads(payloads)
        dataStore.deletePayloads()
        Assert.assertTrue(dataStore.getPayloads(listOf("type", "otherType")).isEmpty())
    }
}
