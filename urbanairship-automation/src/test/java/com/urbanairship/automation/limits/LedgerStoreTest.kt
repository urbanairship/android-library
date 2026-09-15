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
        sharedIds: List<String> = emptyList(),
        triggerId: String? = null,
        timestamp: Instant = Instant.EPOCH,
        count: Int? = null
    ): LedgerEvent = LedgerEvent.Triggered(
        scheduleId = scheduleId,
        sharedIds = sharedIds.ifEmpty { null },
        triggerId = triggerId,
        timestamp = timestamp,
        count = count
    )

    private fun execution(
        scheduleId: String,
        sharedIds: List<String> = emptyList(),
        triggerId: String? = null,
        timestamp: Instant = Instant.EPOCH,
        count: Int? = null,
        result: LedgerExecutionResult = LedgerExecutionResult.SUCCEEDED,
        cancel: Boolean? = null
    ): LedgerEvent = LedgerEvent.Execution(
        scheduleId = scheduleId,
        sharedIds = sharedIds.ifEmpty { null },
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

        val result = store.events(scheduleId = "schedule-1", sharedIds = emptyList())
        assertEquals(listOf(event), result)
    }

    @Test
    public fun testQueryReturnsEmptyWhenNoMatch(): TestResult = runTest {
        store.recordEvents(listOf(execution(scheduleId = "schedule-1")))

        val result = store.events(scheduleId = "other", sharedIds = emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    public fun testQueryMatchesScheduleOrSharedID(): TestResult = runTest {
        // Recorded by another schedule but under the shared group.
        val sharedEvent = execution(scheduleId = "schedule-2", sharedIds = listOf("group-1"))
        // Recorded by this schedule with no shared group.
        val ownEvent = execution(scheduleId = "schedule-1")
        // Unrelated.
        val unrelated = execution(scheduleId = "schedule-3", sharedIds = listOf("group-2"))

        store.recordEvents(listOf(sharedEvent, ownEvent, unrelated))

        val result = store.events(scheduleId = "schedule-1", sharedIds = listOf("group-1"))
        assertEquals(setOf(sharedEvent, ownEvent), result.toSet())
    }

    @Test
    public fun testQueryIgnoresSharedWhenNull(): TestResult = runTest {
        val sharedEvent = execution(scheduleId = "schedule-2", sharedIds = listOf("group-1"))
        val ownEvent = execution(scheduleId = "schedule-1", sharedIds = listOf("group-1"))

        store.recordEvents(listOf(sharedEvent, ownEvent))

        // Without a shared ID, only the schedule's own events are eligible.
        val result = store.events(scheduleId = "schedule-1", sharedIds = emptyList())
        assertEquals(listOf(ownEvent), result)
    }

    @Test
    public fun testQueryMatchesSharedIdAgainstBareScheduleId(): TestResult = runTest {
        // schedule-2's own pre-group history, recorded before it ever had a
        // group (e.g. backfilled). A schedule naming schedule-2's own ID among
        // its shared_ids must still pick this up, even though the event
        // carries no group itself.
        val preGroupEvent = execution(scheduleId = "schedule-2")
        // Unrelated schedule sharing neither ID.
        val unrelated = execution(scheduleId = "schedule-3", sharedIds = listOf("group-x"))

        store.recordEvents(listOf(preGroupEvent, unrelated))

        val result = store.events(scheduleId = "schedule-1", sharedIds = listOf("schedule-2"))
        assertEquals(listOf(preGroupEvent), result)
    }

    @Test
    public fun testRecordEmptyIsNoop(): TestResult = runTest {
        store.recordEvents(emptyList())
        val result = store.events(scheduleId = "schedule-1", sharedIds = listOf("group-1"))
        assertTrue(result.isEmpty())
    }

    @Test
    public fun testDeleteByScheduleScope(): TestResult = runTest {
        val keep = execution(scheduleId = "schedule-2", sharedIds = listOf("group-1"))
        val remove = execution(scheduleId = "schedule-1", sharedIds = listOf("group-1"))
        store.recordEvents(listOf(keep, remove))

        store.deleteEvents(listOf(LedgerScope.Schedule("schedule-1")))

        val result = store.events(scheduleId = "schedule-1", sharedIds = listOf("group-1"))
        assertEquals(listOf(keep), result)
    }

    @Test
    public fun testDeleteBySharedScope(): TestResult = runTest {
        val removeA = execution(scheduleId = "schedule-1", sharedIds = listOf("group-1"))
        val removeB = execution(scheduleId = "schedule-2", sharedIds = listOf("group-1"))
        val keep = execution(scheduleId = "schedule-3")
        store.recordEvents(listOf(removeA, removeB, keep))

        store.deleteEvents(listOf(LedgerScope.Shared("group-1")))

        assertTrue(store.events(scheduleId = "schedule-1", sharedIds = listOf("group-1")).isEmpty())
        assertTrue(store.events(scheduleId = "schedule-2", sharedIds = listOf("group-1")).isEmpty())
        assertEquals(listOf(keep), store.events(scheduleId = "schedule-3", sharedIds = emptyList()))
    }

    @Test
    public fun testDeleteMultipleScopes(): TestResult = runTest {
        val removeA = execution(scheduleId = "schedule-1")
        val removeB = execution(scheduleId = "schedule-2", sharedIds = listOf("group-1"))
        val keep = execution(scheduleId = "schedule-3")
        store.recordEvents(listOf(removeA, removeB, keep))

        store.deleteEvents(
            listOf(LedgerScope.Schedule("schedule-1"), LedgerScope.Shared("group-1"))
        )

        assertTrue(store.events(scheduleId = "schedule-1", sharedIds = emptyList()).isEmpty())
        assertTrue(store.events(scheduleId = "schedule-2", sharedIds = listOf("group-1")).isEmpty())
        assertEquals(listOf(keep), store.events(scheduleId = "schedule-3", sharedIds = emptyList()))
    }

    @Test
    public fun testDeleteEmptyScopesIsNoop(): TestResult = runTest {
        val event = execution(scheduleId = "schedule-1")
        store.recordEvents(listOf(event))

        store.deleteEvents(emptyList())

        assertEquals(listOf(event), store.events(scheduleId = "schedule-1", sharedIds = emptyList()))
    }

    @Test
    public fun testPersistsAllFields(): TestResult = runTest {
        val triggeredEvent = triggered(
            scheduleId = "schedule-1",
            sharedIds = listOf("group-1"),
            triggerId = "trigger-1",
            timestamp = Instant.ofEpochMilli(123),
            count = 3
        )
        val executionEvent = execution(
            scheduleId = "schedule-1",
            sharedIds = listOf("group-1"),
            triggerId = "trigger-2",
            timestamp = Instant.ofEpochMilli(456),
            count = 5,
            result = LedgerExecutionResult.AUDIENCE_MISS,
            cancel = true
        )

        store.recordEvents(listOf(triggeredEvent, executionEvent))

        val result = store.events(scheduleId = "schedule-1", sharedIds = listOf("group-1"))
        assertEquals(setOf(triggeredEvent, executionEvent), result.toSet())
    }

    @Test
    public fun testQueryReturnsEventsSortedByTimestamp(): TestResult = runTest {
        val third = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(300))
        val first = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(100))
        val second = execution(scheduleId = "schedule-1", timestamp = Instant.ofEpochMilli(200))

        // Recorded out of timestamp order.
        store.recordEvents(listOf(third, first, second))

        val result = store.events(scheduleId = "schedule-1", sharedIds = emptyList())
        assertEquals(listOf(first, second, third), result)
    }

    @Test
    public fun testQueryBreaksTimestampTiesByInsertOrder(): TestResult = runTest {
        val tie = Instant.ofEpochMilli(100)
        val first = execution(scheduleId = "schedule-1", triggerId = "first", timestamp = tie)
        val second = execution(scheduleId = "schedule-1", triggerId = "second", timestamp = tie)
        val third = execution(scheduleId = "schedule-1", triggerId = "third", timestamp = tie)

        store.recordEvents(listOf(first, second, third))

        val result = store.events(scheduleId = "schedule-1", sharedIds = emptyList())
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
            store.events(scheduleId = "schedule-1", sharedIds = emptyList())
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

        assertTrue(store.events(scheduleId = "schedule-1", sharedIds = emptyList()).isEmpty())
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

        val event = store.events(scheduleId = "s", sharedIds = emptyList()).single()
        val result = (event as LedgerEvent.Execution).result
        assertEquals(LedgerExecutionResult.Unknown("some_future_result"), result)

        // Rewriting must not flatten the value to a placeholder.
        assertEquals(body, event.toJsonValue())
    }

    @Test
    public fun testEventJsonRoundTrip() {
        val events: List<LedgerEvent> = listOf(
            triggered(scheduleId = "s", sharedIds = listOf("g"), triggerId = "t", count = 2),
            execution(scheduleId = "s", result = LedgerExecutionResult.BACKFILL, cancel = false)
        )

        for (event in events) {
            // Round-trip through a String to exercise the same TEXT
            // serialization path the store uses for the persisted body.
            val decoded = LedgerEvent.fromJson(JsonValue.parseString(event.toJsonValue().toString()))
            assertEquals(event, decoded)
        }
    }

    // MARK: - Multiple shared groups

    @Test
    public fun testQueryFindsEventByAnyOfItsGroups(): TestResult = runTest {
        // Recorded while the schedule belonged to both groups.
        val event = execution(scheduleId = "schedule-1", sharedIds = listOf("g1", "g2"))
        store.recordEvents(listOf(event))

        // A sibling in either group alone still sees it.
        assertEquals(listOf(event), store.events(scheduleId = "sibling", sharedIds = listOf("g1")))
        assertEquals(listOf(event), store.events(scheduleId = "sibling", sharedIds = listOf("g2")))
        assertTrue(store.events(scheduleId = "sibling", sharedIds = listOf("g3")).isEmpty())
    }

    /**
     * The shared-group half of the query resolves matching rows first and
     * leaves the clause out entirely when none match. The schedule's own
     * events must still come back in that case.
     */
    @Test
    public fun testQueryFallsBackToScheduleWhenNoGroupMatches(): TestResult = runTest {
        val own = execution(scheduleId = "schedule-1")
        store.recordEvents(listOf(own, execution(scheduleId = "other", sharedIds = listOf("g1"))))

        val result = store.events(scheduleId = "schedule-1", sharedIds = listOf("no-such-group"))
        assertEquals(listOf(own), result)
    }

    @Test
    public fun testQueryMatchingSeveralWaysReturnsTheEventOnce(): TestResult = runTest {
        // Matches on its own schedule ID and on both groups at once. The row must
        // not be counted more than once, or a pooled limit would over-count and
        // suppress messages that are still within budget.
        val event = execution(scheduleId = "schedule-1", sharedIds = listOf("g1", "g2"))
        store.recordEvents(listOf(event))

        val result = store.events(scheduleId = "schedule-1", sharedIds = listOf("g1", "g2"))
        assertEquals(listOf(event), result)
    }

    @Test
    public fun testCombiningTwoExistingGroupsPoolsBothHistories(): TestResult = runTest {
        // A and B each already pool with a partner under their own group; the two
        // are then combined under `shared` without either leaving its original.
        val aPartner = execution(scheduleId = "a-partner", sharedIds = listOf("group-a"))
        val bPartner = execution(scheduleId = "b-partner", sharedIds = listOf("group-b"))
        store.recordEvents(listOf(aPartner, bPartner))

        val forA = store.events(scheduleId = "a", sharedIds = listOf("group-a", "shared"))
        assertEquals(listOf(aPartner), forA)

        val forB = store.events(scheduleId = "b", sharedIds = listOf("group-b", "shared"))
        assertEquals(listOf(bPartner), forB)
    }

    @Test
    public fun testDeleteBySharedScopeRemovesEventInSeveralGroups(): TestResult = runTest {
        val event = execution(scheduleId = "schedule-1", sharedIds = listOf("g1", "g2"))
        store.recordEvents(listOf(event))

        // Deleting one of its groups takes the whole event, not just that scope.
        store.deleteEvents(listOf(LedgerScope.Shared("g2")))

        assertTrue(store.events(scheduleId = "schedule-1", sharedIds = listOf("g1")).isEmpty())
    }

    @Test
    public fun testRetainKeepsEventWhileAnyOfItsGroupsIsLive(): TestResult = runTest {
        val event = execution(scheduleId = "dead", sharedIds = listOf("dead-group", "live-group"))
        store.recordEvents(listOf(event))

        store.retainEvents(liveScheduleIds = emptySet(), liveSharedIds = setOf("live-group"))

        assertEquals(listOf(event), store.events(scheduleId = "dead", sharedIds = listOf("live-group")))
    }

    @Test
    public fun testRetainDropsEventWhenEveryGroupIsDead(): TestResult = runTest {
        val event = execution(scheduleId = "dead", sharedIds = listOf("g1", "g2"))
        store.recordEvents(listOf(event))

        store.retainEvents(liveScheduleIds = emptySet(), liveSharedIds = setOf("other"))

        assertTrue(store.events(scheduleId = "dead", sharedIds = listOf("g1", "g2")).isEmpty())
    }

    /**
     * `insertEvents` canonicalizes rather than trusting its caller, so a
     * payload carrying the same group twice writes one row, not two.
     */
    @Test
    public fun testRecordEventsCanonicalizesDuplicateSharedIds(): TestResult = runTest {
        store.recordEvents(listOf(execution(scheduleId = "s", sharedIds = listOf("g1", "g1", "g1"))))

        assertEquals(1, store.sharedIdRowCount())
        val result = store.events(scheduleId = "s", sharedIds = emptyList())
        assertEquals(listOf("g1"), (result.single() as LedgerEvent.Execution).sharedIds)
    }

    /**
     * A compaction rewrite goes through the same insert path as direct
     * recording, so it canonicalizes too — the merged event does not carry
     * forward a duplicate from whichever input event happened to be picked as
     * the merge's representative.
     */
    @Test
    public fun testCompactionRewritesSharedIdRowsRatherThanDuplicatingThem(): TestResult = runTest {
        val old = date(2017, 3, 5)
        // Two mergeable events in the same pair of groups.
        store.recordEvents(
            listOf(
                execution(scheduleId = "s", sharedIds = listOf("g1", "g2"), timestamp = old),
                execution(scheduleId = "s", sharedIds = listOf("g1", "g2"), timestamp = old)
            )
        )
        assertEquals(4, store.sharedIdRowCount())

        store.compact(now = date(2020, 1, 1))

        val result = store.events(scheduleId = "s", sharedIds = emptyList())
        assertEquals(1, result.size)
        assertEquals(2, result.first().effectiveCount)
        assertEquals(listOf("g1", "g2"), (result.first() as LedgerEvent.Execution).sharedIds)
        // One event left, so two rows — not four, and not zero.
        assertEquals(2, store.sharedIdRowCount())
    }

    /**
     * A delete propagates through the foreign key's cascade rule rather than
     * needing its own cleanup step: a shared-ID row is only reachable through
     * its event, so a delete that dropped events but left their rows would
     * leak storage no query could ever surface.
     */
    @Test
    public fun testDeleteByScopeLeavesNoStrandedSharedIdRows(): TestResult = runTest {
        store.recordEvents(
            listOf(
                execution(scheduleId = "schedule-1", sharedIds = listOf("g1", "g2")),
                execution(scheduleId = "schedule-2", sharedIds = listOf("g1"))
            )
        )
        assertEquals(3, store.sharedIdRowCount())

        store.deleteEvents(listOf(LedgerScope.Shared("g1")))

        assertTrue(store.events(scheduleId = "schedule-1", sharedIds = listOf("g2")).isEmpty())
        assertEquals(0, store.sharedIdRowCount())
    }

    @Test
    public fun testRetentionLeavesNoStrandedSharedIdRows(): TestResult = runTest {
        store.recordEvents(
            listOf(
                execution(scheduleId = "live", sharedIds = listOf("live-group")),
                execution(scheduleId = "dead", sharedIds = listOf("dead-group", "other-dead"))
            )
        )
        assertEquals(3, store.sharedIdRowCount())

        store.retainEvents(liveScheduleIds = setOf("live"), liveSharedIds = setOf("live-group"))

        assertTrue(store.events(scheduleId = "dead", sharedIds = listOf("dead-group")).isEmpty())
        // Only the surviving event's single row is left.
        assertEquals(1, store.sharedIdRowCount())
    }

    /**
     * Past SQLite's ~999 bound-parameter limit, `getEvents` can't bind
     * `sharedIds` to one query and falls back to fetching the schedule's own
     * events plus every shared-group match in batches. This is the only test
     * that actually drives that fallback rather than the single-query path.
     */
    @Test
    public fun testQueryBatchesWhenSharedIdsExceedTheStatementLimit(): TestResult = runTest {
        // Recorded by an unrelated schedule, findable only through the huge
        // shared-ID list — forces every match to come from the batched half.
        val event = execution(scheduleId = "schedule-1", sharedIds = listOf("match"))
        store.recordEvents(listOf(event))

        val manyGroups = (0 until 1200).map { "group-$it" } + "match"
        val result = store.events(scheduleId = "unrelated", sharedIds = manyGroups)

        assertEquals(listOf(event), result)
    }

    @Test
    public fun testBatchedFallbackMergesOwnAndSharedMatchesWithoutDuplicates(): TestResult = runTest {
        // Matches both by owning schedule and (redundantly) by one of the
        // oversized shared IDs — the fallback's merge must still return it once.
        val ownAndShared = execution(scheduleId = "schedule-1", sharedIds = listOf("group-500"))
        val unrelated = execution(scheduleId = "other")
        store.recordEvents(listOf(ownAndShared, unrelated))

        val manyGroups = (0 until 1200).map { "group-$it" }
        val result = store.events(scheduleId = "schedule-1", sharedIds = manyGroups)

        assertEquals(listOf(ownAndShared), result)
    }

    /**
     * The batched sub-queries carry no `ORDER BY` of their own — each chunk
     * comes back in whatever order SQLite produced it and the results are
     * merged through a map — so the Kotlin-side sort is the only thing
     * restoring the oldest-first contract on this path. Both tests above
     * assert on a single row and so cannot see it at all.
     *
     * The order matters to every consumer downstream: [LedgerCompactor] folds
     * the oldest events first, and a time-windowed exclusion rule reads the
     * sequence as a history.
     */
    @Test
    public fun testBatchedFallbackStillReturnsOldestFirst(): TestResult = runTest {
        // Recorded newest-first and reachable only through the oversized list,
        // so insertion order, ID order and timestamp order all disagree.
        val newest = execution(
            scheduleId = "other",
            sharedIds = listOf("group-900"),
            timestamp = Instant.ofEpochMilli(3_000)
        )
        val oldest = execution(
            scheduleId = "other",
            sharedIds = listOf("group-100"),
            timestamp = Instant.ofEpochMilli(1_000)
        )
        // Matches by owning schedule instead, so it arrives from the other half
        // of the merge entirely.
        val middle = execution(
            scheduleId = "schedule-1",
            timestamp = Instant.ofEpochMilli(2_000)
        )
        store.recordEvents(listOf(newest, oldest, middle))

        val manyGroups = (0 until 1200).map { "group-$it" }
        val result = store.events(scheduleId = "schedule-1", sharedIds = manyGroups)

        assertEquals(listOf(oldest, middle, newest), result)
    }

    // MARK: - Retention

    @Test
    public fun testRetainKeepsLiveScheduleAndDropsOrphans(): TestResult = runTest {
        val liveOwn = execution(scheduleId = "live")
        val liveViaGroup = execution(scheduleId = "dead-1", sharedIds = listOf("live-group"))
        val orphanWithGroup = execution(scheduleId = "dead-2", sharedIds = listOf("dead-group"))
        val orphanNoGroup = execution(scheduleId = "dead-3")

        store.recordEvents(listOf(liveOwn, liveViaGroup, orphanWithGroup, orphanNoGroup))

        store.retainEvents(liveScheduleIds = setOf("live"), liveSharedIds = setOf("live-group"))

        // Kept: the own event of a live schedule, and an event pooled under a
        // live group even though its recording schedule is gone.
        assertEquals(listOf(liveOwn), store.events(scheduleId = "live", sharedIds = emptyList()))
        assertEquals(
            listOf(liveViaGroup),
            store.events(scheduleId = "dead-1", sharedIds = listOf("live-group"))
        )

        // Dropped: fully orphaned events, including one with no shared group.
        assertTrue(store.events(scheduleId = "dead-2", sharedIds = listOf("dead-group")).isEmpty())
        assertTrue(store.events(scheduleId = "dead-3", sharedIds = emptyList()).isEmpty())
    }

    @Test
    public fun testRetainWithNoLiveIdsDropsEverything(): TestResult = runTest {
        store.recordEvents(
            listOf(execution(scheduleId = "a"), execution(scheduleId = "b", sharedIds = listOf("g")))
        )

        store.retainEvents(liveScheduleIds = emptySet(), liveSharedIds = emptySet())

        assertTrue(store.events(scheduleId = "a", sharedIds = listOf("g")).isEmpty())
        assertTrue(store.events(scheduleId = "b", sharedIds = listOf("g")).isEmpty())
    }

    @Test
    public fun testRetainKeepsEverythingWhenAllIdsAreLive(): TestResult = runTest {
        val events = listOf(
            execution(scheduleId = "a"),
            execution(scheduleId = "b", sharedIds = listOf("g"))
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

        val result = store.events(scheduleId = "s", sharedIds = emptyList())
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

        assertEquals(2, store.events(scheduleId = "s", sharedIds = emptyList()).size)
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

        val result = store.events(scheduleId = "s", sharedIds = emptyList())
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

        val result = store.events(scheduleId = "s", sharedIds = emptyList())
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

        assertEquals(events, store.events(scheduleId = "s", sharedIds = emptyList()))
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
        assertEquals(1, store.events(scheduleId = "s", sharedIds = emptyList()).size)
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
     * alone — the shared-group subquery is empty, so `id NOT IN (...)` reads as
     * true for every row and the empty set must not orphan a live schedule's
     * events.
     */
    @Test
    public fun testRetainWithNoLiveSharedIdsKeepsLiveSchedules(): TestResult = runTest {
        val live = execution(scheduleId = "live", sharedIds = listOf("group-1"))
        store.recordEvents(listOf(live, execution(scheduleId = "dead", sharedIds = listOf("group-1"))))

        store.retainEvents(liveScheduleIds = setOf("live"), liveSharedIds = emptySet())

        assertEquals(listOf(live), store.events(scheduleId = "live", sharedIds = emptyList()))
        assertEquals(1, db.dao.count())
    }

    /** The mirror case: no live schedules, but a live group still pools history. */
    @Test
    public fun testRetainWithNoLiveScheduleIdsKeepsLiveGroups(): TestResult = runTest {
        val pooled = execution(scheduleId = "dead", sharedIds = listOf("live-group"))
        store.recordEvents(listOf(pooled, execution(scheduleId = "dead", sharedIds = emptyList())))

        store.retainEvents(liveScheduleIds = emptySet(), liveSharedIds = setOf("live-group"))

        assertEquals(listOf(pooled), store.events(scheduleId = "dead", sharedIds = listOf("live-group")))
        assertEquals(1, db.dao.count())
    }

    /** The DAO reports what it removed, on both of its strategies. */
    @Test
    public fun testDeleteOrphanedEventsReturnsDeletedCount(): TestResult = runTest {
        store.recordEvents(
            listOf(
                execution(scheduleId = "live"),
                execution(scheduleId = "dead-1"),
                execution(scheduleId = "dead-2")
            )
        )

        assertEquals(2, db.dao.deleteOrphanedEvents(setOf("live"), emptySet()))
        assertEquals(0, db.dao.deleteOrphanedEvents(setOf("live"), emptySet()))

        // And again past the variable limit, where it deletes by primary key.
        store.recordEvents(listOf(execution(scheduleId = "dead-3")))
        val oversized = (0 until 1200).map { "schedule-$it" }.toSet() + "live"
        assertEquals(1, db.dao.deleteOrphanedEvents(oversized, emptySet()))
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

        assertEquals(listOf(live), store.events(scheduleId = "live", sharedIds = emptyList()))
        assertEquals(1, db.dao.count())
    }

    /**
     * The same fallback, on the shared-group axis. The two tests above it stay
     * under the variable limit and so take the SQL path; the one above takes
     * the fallback but over rows that have no groups at all. Only this drives
     * the in-memory predicate's group half, which is the half that decides
     * whether an event outlives the schedule that recorded it.
     */
    @Test
    public fun testRetainFallbackKeepsEventsWhoseGroupIsStillLive(): TestResult = runTest {
        // Both recorded by schedules that are gone. Only the group tells them
        // apart.
        val pooledUnderLiveGroup =
            execution(scheduleId = "dead-1", sharedIds = listOf("dead-group", "live-group"))
        val pooledUnderDeadGroupOnly =
            execution(scheduleId = "dead-2", sharedIds = listOf("dead-group"))
        store.recordEvents(listOf(pooledUnderLiveGroup, pooledUnderDeadGroupOnly))

        // Oversized on the schedule axis, so the delete cannot be bound and
        // retention has to fall back to filtering in memory.
        val manyLiveIds = (0 until 1200).map { "schedule-$it" }.toSet()
        store.retainEvents(liveScheduleIds = manyLiveIds, liveSharedIds = setOf("live-group"))

        assertEquals(
            listOf(pooledUnderLiveGroup),
            store.events(scheduleId = "dead-1", sharedIds = listOf("live-group"))
        )
        assertEquals(1, db.dao.count())
        // The survivor keeps both of its group rows, not just the live one.
        assertEquals(2, store.sharedIdRowCount())
    }

    /** UTC, matching the zone the compactor buckets in. */
    private fun date(year: Int, month: Int, day: Int): Instant =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()

    @Test
    public fun testRecordEventsUnlessAppendsWhenNothingMatches(): TestResult = runTest {
        store.recordEvents(listOf(triggered("schedule-1")))

        val appended = store.recordEventsUnless(
            scheduleId = "schedule-1",
            sharedIds = emptyList(),
            events = listOf(execution("schedule-1"))
        ) { it is LedgerEvent.Execution }

        assertTrue(appended)
        assertEquals(2, store.events("schedule-1", emptyList()).size)
    }

    @Test
    public fun testRecordEventsUnlessSkipsWhenSomethingMatches(): TestResult = runTest {
        store.recordEvents(listOf(execution("schedule-1")))

        val appended = store.recordEventsUnless(
            scheduleId = "schedule-1",
            sharedIds = emptyList(),
            events = listOf(execution("schedule-1"))
        ) { it is LedgerEvent.Execution }

        assertFalse(appended)
        assertEquals(1, store.events("schedule-1", emptyList()).size)
    }

    @Test
    public fun testRecordEventsUnlessJudgesTheSharedGroupToo(): TestResult = runTest {
        // Recorded by a sibling under the shared group, so it is part of the
        // set the predicate has to see.
        store.recordEvents(listOf(execution("schedule-2", sharedIds = listOf("group-1"))))

        val appended = store.recordEventsUnless(
            scheduleId = "schedule-1",
            sharedIds = listOf("group-1"),
            events = listOf(execution("schedule-1", sharedIds = listOf("group-1")))
        ) { it is LedgerEvent.Execution }

        assertFalse(appended)
    }
}
