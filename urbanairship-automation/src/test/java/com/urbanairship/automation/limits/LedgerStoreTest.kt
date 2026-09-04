/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.limits.storage.LedgerDatabase
import com.urbanairship.automation.limits.storage.LedgerEventEntity
import com.urbanairship.json.JsonValue
import java.time.Instant
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class LedgerStoreTest {

    private val db =
        LedgerDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val store: LedgerStore = LedgerStore(db.dao)

    @After
    public fun tearDown() {
        db.close()
    }

    private fun triggered(
        scheduleId: String,
        sharedId: String? = null,
        triggerId: String? = null,
        timestamp: Instant = Instant.EPOCH,
        count: Int? = null
    ): LedgerEvent = LedgerEvent.Triggered(
        scheduleId = scheduleId,
        sharedId = sharedId,
        triggerId = triggerId,
        timestamp = timestamp,
        count = count
    )

    private fun execution(
        scheduleId: String,
        sharedId: String? = null,
        triggerId: String? = null,
        timestamp: Instant = Instant.EPOCH,
        count: Int? = null,
        result: LedgerExecutionResult = LedgerExecutionResult.SUCCEEDED,
        cancel: Boolean? = null
    ): LedgerEvent = LedgerEvent.Execution(
        scheduleId = scheduleId,
        sharedId = sharedId,
        triggerId = triggerId,
        timestamp = timestamp,
        count = count,
        result = result,
        cancel = cancel
    )

    @Test
    public fun testRecordAndQueryBySchedule(): TestResult = runTest {
        val event = execution(scheduleId = "schedule-1")
        store.recordEvents(listOf(event))

        val result = store.events(scheduleId = "schedule-1", sharedId = null)
        assertEquals(listOf(event), result)
    }

    @Test
    public fun testQueryReturnsEmptyWhenNoMatch(): TestResult = runTest {
        store.recordEvents(listOf(execution(scheduleId = "schedule-1")))

        val result = store.events(scheduleId = "other", sharedId = null)
        assertTrue(result.isEmpty())
    }

    @Test
    public fun testQueryMatchesScheduleOrSharedID(): TestResult = runTest {
        // Recorded by another schedule but under the shared group.
        val sharedEvent = execution(scheduleId = "schedule-2", sharedId = "group-1")
        // Recorded by this schedule with no shared group.
        val ownEvent = execution(scheduleId = "schedule-1")
        // Unrelated.
        val unrelated = execution(scheduleId = "schedule-3", sharedId = "group-2")

        store.recordEvents(listOf(sharedEvent, ownEvent, unrelated))

        val result = store.events(scheduleId = "schedule-1", sharedId = "group-1")
        assertEquals(setOf(sharedEvent, ownEvent), result.toSet())
    }

    @Test
    public fun testQueryIgnoresSharedWhenNull(): TestResult = runTest {
        val sharedEvent = execution(scheduleId = "schedule-2", sharedId = "group-1")
        val ownEvent = execution(scheduleId = "schedule-1", sharedId = "group-1")

        store.recordEvents(listOf(sharedEvent, ownEvent))

        // Without a shared ID, only the schedule's own events are eligible.
        val result = store.events(scheduleId = "schedule-1", sharedId = null)
        assertEquals(listOf(ownEvent), result)
    }

    @Test
    public fun testRecordEmptyIsNoop(): TestResult = runTest {
        store.recordEvents(emptyList())
        val result = store.events(scheduleId = "schedule-1", sharedId = "group-1")
        assertTrue(result.isEmpty())
    }

    @Test
    public fun testDeleteByScheduleScope(): TestResult = runTest {
        val keep = execution(scheduleId = "schedule-2", sharedId = "group-1")
        val remove = execution(scheduleId = "schedule-1", sharedId = "group-1")
        store.recordEvents(listOf(keep, remove))

        store.deleteEvents(listOf(LedgerScope.Schedule("schedule-1")))

        val result = store.events(scheduleId = "schedule-1", sharedId = "group-1")
        assertEquals(listOf(keep), result)
    }

    @Test
    public fun testDeleteBySharedScope(): TestResult = runTest {
        val removeA = execution(scheduleId = "schedule-1", sharedId = "group-1")
        val removeB = execution(scheduleId = "schedule-2", sharedId = "group-1")
        val keep = execution(scheduleId = "schedule-3")
        store.recordEvents(listOf(removeA, removeB, keep))

        store.deleteEvents(listOf(LedgerScope.Shared("group-1")))

        assertTrue(store.events(scheduleId = "schedule-1", sharedId = "group-1").isEmpty())
        assertTrue(store.events(scheduleId = "schedule-2", sharedId = "group-1").isEmpty())
        assertEquals(listOf(keep), store.events(scheduleId = "schedule-3", sharedId = null))
    }

    @Test
    public fun testDeleteMultipleScopes(): TestResult = runTest {
        val removeA = execution(scheduleId = "schedule-1")
        val removeB = execution(scheduleId = "schedule-2", sharedId = "group-1")
        val keep = execution(scheduleId = "schedule-3")
        store.recordEvents(listOf(removeA, removeB, keep))

        store.deleteEvents(
            listOf(LedgerScope.Schedule("schedule-1"), LedgerScope.Shared("group-1"))
        )

        assertTrue(store.events(scheduleId = "schedule-1", sharedId = null).isEmpty())
        assertTrue(store.events(scheduleId = "schedule-2", sharedId = "group-1").isEmpty())
        assertEquals(listOf(keep), store.events(scheduleId = "schedule-3", sharedId = null))
    }

    @Test
    public fun testDeleteEmptyScopesIsNoop(): TestResult = runTest {
        val event = execution(scheduleId = "schedule-1")
        store.recordEvents(listOf(event))

        store.deleteEvents(emptyList())

        assertEquals(listOf(event), store.events(scheduleId = "schedule-1", sharedId = null))
    }

    @Test
    public fun testPersistsAllFields(): TestResult = runTest {
        val triggeredEvent = triggered(
            scheduleId = "schedule-1",
            sharedId = "group-1",
            triggerId = "trigger-1",
            timestamp = Instant.ofEpochMilli(123),
            count = 3
        )
        val executionEvent = execution(
            scheduleId = "schedule-1",
            sharedId = "group-1",
            triggerId = "trigger-2",
            timestamp = Instant.ofEpochMilli(456),
            count = 5,
            result = LedgerExecutionResult.AUDIENCE_MISS,
            cancel = true
        )

        store.recordEvents(listOf(triggeredEvent, executionEvent))

        val result = store.events(scheduleId = "schedule-1", sharedId = "group-1")
        assertEquals(setOf(triggeredEvent, executionEvent), result.toSet())
    }

    @Test
    public fun testQueryReturnsEventsSortedByTimestamp(): TestResult = runTest {
        val third = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(300))
        val first = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(100))
        val second = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(200))

        // Recorded out of timestamp order.
        store.recordEvents(listOf(third, first, second))

        val result = store.events(scheduleId = "schedule-1", sharedId = null)
        assertEquals(listOf(first, second, third), result)
    }

    @Test
    public fun testQueryBreaksTimestampTiesByInsertOrder(): TestResult = runTest {
        val tie = Instant.ofEpochMilli(100)
        val first = execution(scheduleId = "schedule-1", triggerId = "first", timestamp = tie)
        val second = execution(scheduleId = "schedule-1", triggerId = "second", timestamp = tie)
        val third = execution(scheduleId = "schedule-1", triggerId = "third", timestamp = tie)

        store.recordEvents(listOf(first, second, third))

        val result = store.events(scheduleId = "schedule-1", sharedId = null)
        assertEquals(listOf(first, second, third), result)
    }

    /**
     * A row whose body cannot be decoded is skipped, and must not cost us the
     * decodable events recorded either side of it.
     */
    @Test
    public fun testQuerySkipsUndecodableRowsAndKeepsTheRest(): TestResult = runTest {
        val first = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(100))
        val last = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(300))

        store.recordEvents(listOf(first, last))
        db.dao.insertAll(listOf(undecodableRow(scheduleId = "schedule-1", timestamp = 200)))

        // Ordering is preserved across the gap the skipped row leaves behind.
        assertEquals(
            listOf(first, last),
            store.events(scheduleId = "schedule-1", sharedId = null)
        )
    }

    /** A row that [LedgerEvent.fromJson] rejects, written straight to the DAO. */
    private fun undecodableRow(scheduleId: String, timestamp: Long): LedgerEventEntity =
        LedgerEventEntity(
            scheduleId = scheduleId,
            timestamp = timestamp,
            body = JsonValue.wrap("not an event")
        )

    @Test
    public fun testHasEvents(): TestResult = runTest {
        assertFalse(store.hasEvents("schedule-1"))

        store.recordEvents(listOf(execution(scheduleId = "schedule-1")))

        assertTrue(store.hasEvents("schedule-1"))
        assertFalse(store.hasEvents("schedule-2"))
    }

    /**
     * The whole point of [LedgerStoreInterface.hasEvents] over `events().isNotEmpty()`:
     * a row that cannot be decoded still counts as recorded, so a caller asking
     * "did I already write here?" is not told "no" and made to write again.
     */
    @Test
    public fun testHasEventsCountsUndecodableRows(): TestResult = runTest {
        db.dao.insertAll(listOf(undecodableRow(scheduleId = "schedule-1", timestamp = 1)))

        assertTrue(store.events(scheduleId = "schedule-1", sharedId = null).isEmpty())
        assertTrue(store.hasEvents("schedule-1"))
    }

    @Test
    public fun testEffectiveCountDefaultsToOne() {
        assertEquals(1, execution(scheduleId = "s", count = null).effectiveCount)
        assertEquals(5, execution(scheduleId = "s", count = 5).effectiveCount)
        assertEquals(1, triggered(scheduleId = "s", count = null).effectiveCount)
        assertEquals(3, triggered(scheduleId = "s", count = 3).effectiveCount)
    }

    /**
     * An execution recorded by a newer SDK with a result this version does not
     * know still parses, keeps the raw value through a round trip, and is
     * visible to the limit read.
     */
    @Test
    public fun testUnrecognizedResultRoundTripsAndIsVisible(): TestResult = runTest {
        val body = JsonValue.parseString(
            """{"type":"execution","schedule_id":"s","timestamp":1000,"result":"some_future_result"}"""
        )
        db.dao.insertAll(
            listOf(LedgerEventEntity(scheduleId = "s", timestamp = 1000, body = body))
        )

        val event = store.events(scheduleId = "s", sharedId = null).single()
        val result = (event as LedgerEvent.Execution).result
        assertEquals(LedgerExecutionResult.Unknown("some_future_result"), result)

        // Rewriting must not flatten the value to a placeholder.
        assertEquals(body, event.toJsonValue())
    }

    @Test
    public fun testEventJsonRoundTrip() {
        val events: List<LedgerEvent> = listOf(
            triggered(scheduleId = "s", sharedId = "g", triggerId = "t", count = 2),
            execution(scheduleId = "s", result = LedgerExecutionResult.BACKFILL, cancel = false)
        )

        for (event in events) {
            // Round-trip through a String to exercise the same TEXT
            // serialization path the store uses for the persisted body.
            val decoded = LedgerEvent.fromJson(JsonValue.parseString(event.toJsonValue().toString()))
            assertEquals(event, decoded)
        }
    }
}
