/* Copyright Airship and Contributors */
package com.urbanairship.remotedata

import android.database.Cursor
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.spyk
import java.time.Instant
import java.util.Arrays
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RemoteDataStoreTest {

    private var dataStore = RemoteDataStore(ApplicationProvider.getApplicationContext(), "appKey", "test")
    private var payloads = setOf(
        RemoteDataPayload(
            type = "type",
            timestamp = Instant.ofEpochMilli(123),
            data = jsonMapOf("foo" to "bar"),
            remoteDataInfo = RemoteDataInfo("some url", "some last modified", RemoteDataSource.APP)
        ),
        RemoteDataPayload(
            type = "otherType",
            timestamp = Instant.ofEpochMilli(123),
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

    @Test
    public fun testGetPayloadsCorruptCursorReturnsEmptySet() {
        val corruptCursor = mockk<Cursor>()
        every { corruptCursor.moveToFirst() } throws SQLiteDatabaseCorruptException("database disk image is malformed")
        every { corruptCursor.close() } just Runs

        val spyStore = spyk(dataStore)
        every { spyStore.query(any(), any(), any(), any(), any()) } returns corruptCursor

        Assert.assertTrue(spyStore.getPayloads(null).isEmpty())
    }

    @Test
    public fun testGetPayloadsCloseFailureReturnsEmptySet() {
        val cursor = mockk<Cursor>()
        every { cursor.moveToFirst() } returns false
        every { cursor.isAfterLast } returns true
        every { cursor.close() } throws SQLiteException("unable to close database")

        val spyStore = spyk(dataStore)
        every { spyStore.query(any(), any(), any(), any(), any()) } returns cursor

        Assert.assertTrue(spyStore.getPayloads(null).isEmpty())
    }

    @Test
    public fun testGetPayloadsNonSqliteCloseFailureDoesNotOverrideReadFailure() {
        val corruptCursor = mockk<Cursor>()
        every { corruptCursor.moveToFirst() } throws SQLiteDatabaseCorruptException("database disk image is malformed")
        every { corruptCursor.close() } throws IllegalStateException("already closed")

        val spyStore = spyk(dataStore)
        every { spyStore.query(any(), any(), any(), any(), any()) } returns corruptCursor

        var readFailed = false
        val result = spyStore.getPayloads(null, onReadFailed = { readFailed = true })

        // A close() failure of any type must not override the read failure's result.
        Assert.assertTrue(result.isEmpty())
        Assert.assertTrue(readFailed)
    }

    @Test
    public fun testGetPayloadsCloseFailurePreservesSuccessfulRead() {
        val cursor = mockk<Cursor>()
        every { cursor.moveToFirst() } returns true
        every { cursor.isAfterLast } returnsMany listOf(false, true)
        every { cursor.getColumnIndex("type") } returns 0
        every { cursor.getColumnIndex("time") } returns 1
        every { cursor.getColumnIndex("data") } returns 2
        every { cursor.getColumnIndex("metadata") } returns 3
        every { cursor.getString(0) } returns "type"
        every { cursor.getLong(1) } returns 123L
        every { cursor.getString(2) } returns jsonMapOf("foo" to "bar").toString()
        every { cursor.getString(3) } returns JsonValue.NULL.toString()
        every { cursor.moveToNext() } returns true
        every { cursor.close() } throws SQLiteException("unable to close database")

        val spyStore = spyk(dataStore)
        every { spyStore.query(any(), any(), any(), any(), any()) } returns cursor

        var readFailed = false
        val result = spyStore.getPayloads(null, onReadFailed = { readFailed = true })

        // A close() failure must not drop data that was already read successfully.
        Assert.assertEquals(
            setOf(
                RemoteDataPayload(
                    type = "type",
                    timestamp = Instant.ofEpochMilli(123),
                    data = jsonMapOf("foo" to "bar"),
                    remoteDataInfo = null
                )
            ),
            result
        )
        Assert.assertFalse(readFailed)
    }

    @Test
    public fun testGetPayloadsCorruptCursorInvokesOnReadFailed() {
        val corruptCursor = mockk<Cursor>()
        every { corruptCursor.moveToFirst() } throws SQLiteDatabaseCorruptException("database disk image is malformed")
        every { corruptCursor.close() } just Runs

        val spyStore = spyk(dataStore)
        every { spyStore.query(any(), any(), any(), any(), any()) } returns corruptCursor

        var readFailed = false
        spyStore.getPayloads(null, onReadFailed = { readFailed = true })

        Assert.assertTrue(readFailed)
    }

    @Test
    public fun testGetPayloadsTransientSqliteExceptionDoesNotInvokeOnReadFailed() {
        val lockedCursor = mockk<Cursor>()
        every { lockedCursor.moveToFirst() } throws SQLiteException("database is locked")
        every { lockedCursor.close() } just Runs

        val spyStore = spyk(dataStore)
        every { spyStore.query(any(), any(), any(), any(), any()) } returns lockedCursor

        var readFailed = false
        val result = spyStore.getPayloads(null, onReadFailed = { readFailed = true })

        Assert.assertTrue(result.isEmpty())
        Assert.assertFalse(readFailed)
    }

    @Test
    public fun testGetPayloadsUnopenableDatabaseInvokesOnReadFailed() {
        val spyStore = spyk(dataStore)
        every { spyStore.query(any(), any(), any(), any(), any()) } returns null

        var readFailed = false
        val result = spyStore.getPayloads(null, onReadFailed = { readFailed = true })

        Assert.assertTrue(result.isEmpty())
        Assert.assertTrue(readFailed)
    }
}
