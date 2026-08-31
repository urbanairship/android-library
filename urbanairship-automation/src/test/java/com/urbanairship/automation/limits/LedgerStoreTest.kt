/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.limits.storage.LedgerDatabase
import com.urbanairship.automation.limits.storage.LedgerEventEntity
import com.urbanairship.json.JsonValue
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
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

    // MARK: - Retention

    @Test
    public fun testRetainKeepsLiveScheduleAndDropsOrphans(): TestResult = runTest {
        val liveOwn = execution(scheduleId = "live")
        val liveViaGroup = execution(scheduleId = "dead-1", sharedId = "live-group")
        val orphanWithGroup = execution(scheduleId = "dead-2", sharedId = "dead-group")
        val orphanNoGroup = execution(scheduleId = "dead-3")

        store.recordEvents(listOf(liveOwn, liveViaGroup, orphanWithGroup, orphanNoGroup))

        store.retainEvents(liveScheduleIds = setOf("live"), liveSharedIds = setOf("live-group"))

        // Kept: the own event of a live schedule, and an event pooled under a
        // live group even though its recording schedule is gone.
        assertEquals(listOf(liveOwn), store.events(scheduleId = "live", sharedId = null))
        assertEquals(
            listOf(liveViaGroup),
            store.events(scheduleId = "dead-1", sharedId = "live-group")
        )

        // Dropped: fully orphaned events, including one with no shared group.
        assertTrue(store.events(scheduleId = "dead-2", sharedId = "dead-group").isEmpty())
        assertTrue(store.events(scheduleId = "dead-3", sharedId = null).isEmpty())
    }

    @Test
    public fun testRetainWithNoLiveIdsDropsEverything(): TestResult = runTest {
        store.recordEvents(
            listOf(execution(scheduleId = "a"), execution(scheduleId = "b", sharedId = "g"))
        )

        store.retainEvents(liveScheduleIds = emptySet(), liveSharedIds = emptySet())

        assertTrue(store.events(scheduleId = "a", sharedId = "g").isEmpty())
        assertTrue(store.events(scheduleId = "b", sharedId = "g").isEmpty())
    }

    @Test
    public fun testRetainKeepsEverythingWhenAllIdsAreLive(): TestResult = runTest {
        val events = listOf(
            execution(scheduleId = "a"),
            execution(scheduleId = "b", sharedId = "g")
        )
        store.recordEvents(events)

        store.retainEvents(liveScheduleIds = setOf("a", "b"), liveSharedIds = setOf("g"))

        assertEquals(2, db.dao.count())
    }

    /**
     * A row this SDK cannot decode still has readable scope columns, so
     * retention judges it like any other row rather than leaking it forever.
     */
    @Test
    public fun testRetainDropsUndecodableOrphans(): TestResult = runTest {
        db.dao.insertAll(listOf(undecodableRow(scheduleId = "dead", timestamp = 1)))

        store.retainEvents(liveScheduleIds = setOf("live"), liveSharedIds = emptySet())

        assertEquals(0, db.dao.count())
    }

    // MARK: - Compaction

    @Test
    public fun testCompactMergesMergeableRows(): TestResult = runTest {
        store.recordEvents(
            listOf(
                execution(scheduleId = "s", timestamp = date(2017, 3, 5)),
                execution(scheduleId = "s", timestamp = date(2017, 9, 20))
            )
        )

        store.compact(now = date(2020, 1, 1))

        val result = store.events(scheduleId = "s", sharedId = null)
        assertEquals(1, result.size)
        assertEquals(2, result.first().effectiveCount)
        assertEquals(date(2017, 9, 20), result.first().timestamp)
    }

    /**
     * Under the cap with every event younger than a year, the cheap pre-check
     * skips the decode entirely. Two recent events sharing an exact timestamp
     * would merge in the raw tier if compaction ran, so their survival proves
     * the guard short-circuited.
     */
    @Test
    public fun testCompactSkipsWhenAllRecentAndUnderCap(): TestResult = runTest {
        val recent = date(2019, 12, 25)
        store.recordEvents(
            listOf(
                execution(scheduleId = "s", timestamp = recent),
                execution(scheduleId = "s", timestamp = recent)
            )
        )

        store.compact(now = date(2020, 1, 1))

        assertEquals(2, store.events(scheduleId = "s", sharedId = null).size)
    }

    /**
     * Over the cap, the pre-check must not short-circuit even when every event
     * is recent: the backstop still has to run.
     */
    @Test
    public fun testCompactOverCapCompactsEvenWhenRecent(): TestResult = runTest {
        val recent = date(2019, 12, 25)
        store.recordEvents(
            listOf(
                execution(scheduleId = "s", timestamp = recent),
                execution(scheduleId = "s", timestamp = recent)
            )
        )

        store.compact(now = date(2020, 1, 1), maxEvents = 1)

        val result = store.events(scheduleId = "s", sharedId = null)
        assertEquals(1, result.size)
        assertEquals(2, result.first().effectiveCount)
    }

    /**
     * When any event is old enough to age-bucket, the pre-check lets the decode
     * proceed and the pass runs over the whole table — which also merges the
     * recent same-timestamp duplicates a skipped run would have left alone.
     */
    @Test
    public fun testCompactProceedsWhenAnyEventIsOld(): TestResult = runTest {
        val recent = date(2019, 12, 25)
        store.recordEvents(
            listOf(
                execution(scheduleId = "s", timestamp = recent),
                execution(scheduleId = "s", timestamp = recent),
                execution(scheduleId = "old", timestamp = date(2017, 3, 5))
            )
        )

        store.compact(now = date(2020, 1, 1))

        val result = store.events(scheduleId = "s", sharedId = null)
        assertEquals(1, result.size)
        assertEquals(2, result.first().effectiveCount)
    }

    @Test
    public fun testCompactEmptyLedgerIsNoop(): TestResult = runTest {
        store.compact(now = date(2020, 1, 1))

        assertEquals(0, db.dao.count())
    }

    @Test
    public fun testCompactLeavesNonMergeableRowsAlone(): TestResult = runTest {
        val events = listOf(
            execution(scheduleId = "s", timestamp = date(2017, 3, 5)),
            execution(
                scheduleId = "s",
                timestamp = date(2017, 9, 20),
                result = LedgerExecutionResult.AUDIENCE_MISS
            )
        )
        store.recordEvents(events)

        store.compact(now = date(2020, 1, 1))

        assertEquals(events, store.events(scheduleId = "s", sharedId = null))
    }

    /**
     * An event this SDK cannot decode is left in place rather than dropped, so
     * a forward-incompatible event written by a newer SDK survives a compaction
     * pass by this one.
     */
    @Test
    public fun testCompactPreservesUndecodableRows(): TestResult = runTest {
        store.recordEvents(
            listOf(
                execution(scheduleId = "s", timestamp = date(2017, 3, 5)),
                execution(scheduleId = "s", timestamp = date(2017, 9, 20))
            )
        )
        db.dao.insertAll(listOf(undecodableRow(scheduleId = "s", timestamp = 1)))

        store.compact(now = date(2020, 1, 1))

        // The two mergeable events collapsed into one; the undecodable row stayed.
        assertEquals(2, db.dao.count())
        assertEquals(1, store.events(scheduleId = "s", sharedId = null).size)
    }

    /**
     * A merge rewrites only the rows that folded together. Rows nothing merged
     * with keep their primary key, proving they were never deleted and
     * re-inserted — a large ledger is not churned to persist one merged pair.
     */
    @Test
    public fun testCompactRewritesOnlyMergedRows(): TestResult = runTest {
        store.recordEvents(
            listOf(
                execution(scheduleId = "s", timestamp = date(2017, 3, 5)),
                execution(scheduleId = "s", timestamp = date(2017, 9, 20)),
                // Same yearly bucket, different scope: nothing to merge with.
                execution(scheduleId = "other", timestamp = date(2017, 5, 1))
            )
        )
        val untouchedId = db.dao.getAllEvents().single { it.scheduleId == "other" }.id

        store.compact(now = date(2020, 1, 1))

        assertEquals(2, db.dao.count())
        assertEquals(untouchedId, db.dao.getAllEvents().single { it.scheduleId == "other" }.id)
    }

    // MARK: - Retention edge cases

    /**
     * With no live shared groups, retention still judges rows on their schedule
     * alone — SQLite reads `sharedId NOT IN ()` as true, so the empty set must
     * not orphan a live schedule's events.
     */
    @Test
    public fun testRetainWithNoLiveSharedIdsKeepsLiveSchedules(): TestResult = runTest {
        val live = execution(scheduleId = "live", sharedId = "group-1")
        store.recordEvents(listOf(live, execution(scheduleId = "dead", sharedId = "group-1")))

        store.retainEvents(liveScheduleIds = setOf("live"), liveSharedIds = emptySet())

        assertEquals(listOf(live), store.events(scheduleId = "live", sharedId = null))
        assertEquals(1, db.dao.count())
    }

    /** The mirror case: no live schedules, but a live group still pools history. */
    @Test
    public fun testRetainWithNoLiveScheduleIdsKeepsLiveGroups(): TestResult = runTest {
        val pooled = execution(scheduleId = "dead", sharedId = "live-group")
        store.recordEvents(listOf(pooled, execution(scheduleId = "dead", sharedId = null)))

        store.retainEvents(liveScheduleIds = emptySet(), liveSharedIds = setOf("live-group"))

        assertEquals(listOf(pooled), store.events(scheduleId = "dead", sharedId = "live-group"))
        assertEquals(1, db.dao.count())
    }

    /**
     * Past the SQL variable limit the predicate delete cannot be bound, so
     * retention falls back to testing the rows in memory. Same outcome.
     */
    @Test
    public fun testRetainFallsBackForOversizedLiveIdSets(): TestResult = runTest {
        val live = execution(scheduleId = "live")
        store.recordEvents(listOf(live, execution(scheduleId = "dead")))

        val manyLiveIds = (0 until 1200).map { "schedule-$it" }.toSet() + "live"
        store.retainEvents(liveScheduleIds = manyLiveIds, liveSharedIds = emptySet())

        assertEquals(listOf(live), store.events(scheduleId = "live", sharedId = null))
        assertEquals(1, db.dao.count())
    }

    /** UTC, matching the zone the compactor buckets in. */
    private fun date(year: Int, month: Int, day: Int): Instant =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
}
