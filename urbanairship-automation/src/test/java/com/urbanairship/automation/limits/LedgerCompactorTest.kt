/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import junit.framework.TestCase.assertEquals
import org.junit.Test

/**
 * Covers the pure compaction math: the age tiers, the fields that block a
 * merge, and the oldest-first backstop. The store-level pass is covered by
 * `LedgerStoreTest`.
 */
public class LedgerCompactorTest {

    private val now: Instant = date(2020, 1, 1)

    private fun triggered(
        scheduleId: String = "schedule-1",
        sharedIds: List<String> = emptyList(),
        triggerId: String? = null,
        variantAudienceId: String? = null,
        timestamp: Instant,
        count: Int? = null
    ): LedgerEvent = LedgerEvent.Triggered(
        scheduleId = scheduleId,
        sharedIds = sharedIds.ifEmpty { null },
        triggerId = triggerId,
        variantAudienceId = variantAudienceId,
        timestamp = timestamp,
        count = count
    )

    private fun execution(
        scheduleId: String = "schedule-1",
        sharedIds: List<String> = emptyList(),
        triggerId: String? = null,
        variantAudienceId: String? = null,
        timestamp: Instant,
        count: Int? = null,
        result: LedgerExecutionResult = LedgerExecutionResult.SUCCEEDED,
        cancel: Boolean? = null
    ): LedgerEvent = LedgerEvent.Execution(
        scheduleId = scheduleId,
        sharedIds = sharedIds.ifEmpty { null },
        triggerId = triggerId,
        variantAudienceId = variantAudienceId,
        timestamp = timestamp,
        count = count,
        result = result,
        cancel = cancel
    )

    @Test
    public fun testCompactEmptyIsEmpty() {
        assertEquals(emptyList<LedgerEvent>(), LedgerCompactor.compact(emptyList(), now))
    }

    @Test
    public fun testRawTierKeepsRecentDistinctTimestamps() {
        val events = listOf(
            execution(timestamp = date(2019, 12, 20)),
            execution(timestamp = date(2019, 12, 25))
        )

        // Both younger than a year: raw, so distinct timestamps never merge.
        assertEquals(2, LedgerCompactor.compact(events, now).size)
    }

    @Test
    public fun testRawTierMergesIdenticalRecentTimestamps() {
        val recent = date(2019, 12, 25)
        val events = listOf(execution(timestamp = recent), execution(timestamp = recent))

        val result = LedgerCompactor.compact(events, now)
        assertEquals(1, result.size)
        assertEquals(2, result.first().effectiveCount)
    }

    @Test
    public fun testMonthlyTierMergesWithinSameMonth() {
        val events = listOf(
            execution(timestamp = date(2018, 6, 10), count = 2),
            execution(timestamp = date(2018, 6, 20), count = 3)
        )

        val result = LedgerCompactor.compact(events, now)
        assertEquals(1, result.size)
        assertEquals(5, result.first().effectiveCount)
        // The newest timestamp of the bucket survives as the upper bound.
        assertEquals(date(2018, 6, 20), result.first().timestamp)
    }

    @Test
    public fun testMonthlyTierKeepsDifferentMonths() {
        val events = listOf(
            execution(timestamp = date(2018, 6, 10)),
            execution(timestamp = date(2018, 7, 10))
        )

        assertEquals(2, LedgerCompactor.compact(events, now).size)
    }

    @Test
    public fun testYearlyTierMergesWithinSameYear() {
        val events = listOf(
            execution(timestamp = date(2017, 3, 5)),
            execution(timestamp = date(2017, 9, 20))
        )

        val result = LedgerCompactor.compact(events, now)
        assertEquals(1, result.size)
        assertEquals(2, result.first().effectiveCount)
        assertEquals(date(2017, 9, 20), result.first().timestamp)
    }

    @Test
    public fun testYearlyTierKeepsDifferentYears() {
        val events = listOf(
            execution(timestamp = date(2016, 9, 20)),
            execution(timestamp = date(2017, 9, 20))
        )

        assertEquals(2, LedgerCompactor.compact(events, now).size)
    }

    @Test
    public fun testNeverMergesAcrossDistinguishingFields() {
        val old = date(2017, 3, 5)
        val newer = date(2017, 9, 20)

        // Same yearly bucket and same age tier, but each pair differs on one
        // field the limit evaluator can key on, so none may merge.
        val cases = mapOf(
            "result" to listOf(
                execution(timestamp = old, result = LedgerExecutionResult.SUCCEEDED),
                execution(timestamp = newer, result = LedgerExecutionResult.AUDIENCE_MISS)
            ),
            "cancel" to listOf(
                execution(timestamp = old, cancel = true),
                execution(timestamp = newer, cancel = false)
            ),
            "triggerId" to listOf(
                execution(timestamp = old, triggerId = "trigger-1"),
                execution(timestamp = newer, triggerId = "trigger-2")
            ),
            "variantAudienceId" to listOf(
                execution(timestamp = old, variantAudienceId = "iteration-1"),
                execution(timestamp = newer, variantAudienceId = "iteration-2")
            ),
            "variantAudienceId present vs absent" to listOf(
                execution(timestamp = old, variantAudienceId = "iteration-1"),
                execution(timestamp = newer, variantAudienceId = null)
            ),
            "variantAudienceId on triggered" to listOf(
                triggered(timestamp = old, variantAudienceId = "iteration-1"),
                triggered(timestamp = newer, variantAudienceId = "iteration-2")
            ),
            "scheduleId" to listOf(
                execution(scheduleId = "schedule-1", timestamp = old),
                execution(scheduleId = "schedule-2", timestamp = newer)
            ),
            "sharedIds" to listOf(
                execution(timestamp = old, sharedIds = listOf("group-1")),
                execution(timestamp = newer, sharedIds = listOf("group-2"))
            ),
            "type" to listOf(
                triggered(timestamp = old),
                execution(timestamp = newer)
            )
        )

        cases.forEach { (field, events) ->
            assertEquals(field, 2, LedgerCompactor.compact(events, now).size)
        }
    }

    /**
     * Overlapping is not the same as equal. Merging on an intersection — or on
     * any single ID out of the set — would fold an execution recorded under
     * `{promo}` into one recorded under `{promo, holiday}`, and the survivor
     * carries whichever set the merge happened to keep: either `holiday`'s
     * tally jumps by a count it never earned, or `promo`'s history disappears
     * from `holiday` entirely.
     */
    @Test
    public fun testNeverMergesASubsetIntoItsSuperset() {
        val old = date(2017, 3, 5)
        val newer = date(2017, 9, 20)

        // The overlapping ID is placed first in canonical order in one case and
        // last in the other, so keying on any single member of the set — not
        // just on an intersection — shows up here.
        val cases = mapOf(
            "subset" to listOf(
                execution(timestamp = old, sharedIds = listOf("promo")),
                execution(timestamp = newer, sharedIds = listOf("promo", "holiday"))
            ),
            "partial overlap" to listOf(
                execution(timestamp = old, sharedIds = listOf("promo", "spring")),
                execution(timestamp = newer, sharedIds = listOf("promo", "holiday"))
            ),
            "shares the first ID" to listOf(
                execution(timestamp = old, sharedIds = listOf("holiday", "promo")),
                execution(timestamp = newer, sharedIds = listOf("holiday", "spring"))
            ),
            "shares the last ID" to listOf(
                execution(timestamp = old, sharedIds = listOf("holiday", "zulu")),
                execution(timestamp = newer, sharedIds = listOf("promo", "zulu"))
            ),
            "no groups vs one" to listOf(
                execution(timestamp = old, sharedIds = emptyList()),
                execution(timestamp = newer, sharedIds = listOf("promo"))
            )
        )

        cases.forEach { (case, events) ->
            assertEquals(case, 2, LedgerCompactor.compact(events, now).size)
        }
    }

    @Test
    public fun testBackstopCollapsesOldestGroupFirst() {
        // Two raw (recent) groups of three distinct-timestamp events each, so
        // the age tiers merge nothing. Group "a" is older than group "b".
        val events = listOf(
            execution(scheduleId = "a", timestamp = date(2019, 7, 1)),
            execution(scheduleId = "a", timestamp = date(2019, 7, 2)),
            execution(scheduleId = "a", timestamp = date(2019, 7, 3)),
            execution(scheduleId = "b", timestamp = date(2019, 11, 1)),
            execution(scheduleId = "b", timestamp = date(2019, 11, 2)),
            execution(scheduleId = "b", timestamp = date(2019, 11, 3))
        )

        val result = LedgerCompactor.compact(events, now, maxEvents = 4)
        assertEquals(4, result.size)

        // The oldest group is fully collapsed into one summed event...
        val groupA = result.filter { it.scheduleId == "a" }
        assertEquals(1, groupA.size)
        assertEquals(3, groupA.first().effectiveCount)

        // ...while the newer group is left untouched.
        assertEquals(3, result.count { it.scheduleId == "b" })
    }

    @Test
    public fun testBackstopLeavesNonMergeableEventsOverTheCap() {
        // Each event has a distinct key, so nothing can merge: the backstop must
        // give up rather than drop history.
        val events = (1..5).map { execution(scheduleId = "schedule-$it", timestamp = now) }

        assertEquals(5, LedgerCompactor.compact(events, now, maxEvents = 2).size)
    }

    @Test
    public fun testOrderingBreaksTiesOnVariantAudienceId() {
        val events = listOf(
            execution(variantAudienceId = "iteration-2", timestamp = now),
            execution(variantAudienceId = "iteration-1", timestamp = now),
            execution(variantAudienceId = null, timestamp = now)
        )

        assertEquals(
            listOf(null, "iteration-1", "iteration-2"),
            LedgerCompactor.compact(events, now).map { it.variantAudienceId }
        )
    }

    @Test
    public fun testCompactIsOrderedByTimestamp() {
        val events = listOf(
            execution(timestamp = date(2019, 12, 25)),
            execution(timestamp = date(2019, 12, 20)),
            execution(timestamp = date(2019, 12, 22))
        )

        val result = LedgerCompactor.compact(events, now)
        assertEquals(
            listOf(date(2019, 12, 20), date(2019, 12, 22), date(2019, 12, 25)),
            result.map { it.timestamp }
        )
    }

    @Test
    public fun testMergePreservesTheRemainingFields() {
        val events = listOf(
            execution(
                scheduleId = "schedule-1",
                sharedIds = listOf("group-1"),
                triggerId = "trigger-1",
                variantAudienceId = "iteration-1",
                timestamp = date(2017, 3, 5),
                result = LedgerExecutionResult.HOLDOUT,
                cancel = true
            ),
            execution(
                scheduleId = "schedule-1",
                sharedIds = listOf("group-1"),
                triggerId = "trigger-1",
                variantAudienceId = "iteration-1",
                timestamp = date(2017, 9, 20),
                result = LedgerExecutionResult.HOLDOUT,
                cancel = true
            )
        )

        val merged = LedgerCompactor.compact(events, now).single()
        assertEquals(
            LedgerEvent.Execution(
                scheduleId = "schedule-1",
                sharedIds = listOf("group-1"),
                triggerId = "trigger-1",
                variantAudienceId = "iteration-1",
                timestamp = date(2017, 9, 20),
                count = 2,
                result = LedgerExecutionResult.HOLDOUT,
                cancel = true
            ),
            merged
        )
    }

    /**
     * [LedgerCompactor.Key] canonicalizes `sharedIds` rather than trusting its
     * caller, so two events belonging to the same set of groups merge
     * regardless of the order the IDs happened to arrive in.
     */
    @Test
    public fun testMergesEventsWithSameGroupsRegardlessOfOrder() {
        val events = listOf(
            execution(sharedIds = listOf("g1", "g2"), timestamp = date(2017, 3, 5)),
            execution(sharedIds = listOf("g2", "g1"), timestamp = date(2017, 9, 20))
        )

        val merged = LedgerCompactor.compact(events, now).single()
        assertEquals(2, merged.effectiveCount)
        // The survivor carries the canonical set, not whichever arrival order
        // the merge happened to keep.
        assertEquals(listOf("g1", "g2"), merged.sharedIds)
    }

    @Test
    public fun testSingleEventKeepsItsOriginalCountAndTimestamp() {
        val event = execution(timestamp = date(2017, 3, 5), count = 7)

        assertEquals(listOf(event), LedgerCompactor.compact(listOf(event), now))
    }

    @Test
    public fun testFutureTimestampsStayRaw() {
        // A clock that moved backwards must not push events into an age tier.
        val events = listOf(
            execution(timestamp = date(2021, 6, 1)),
            execution(timestamp = date(2021, 7, 1))
        )

        assertEquals(2, LedgerCompactor.compact(events, now).size)
    }

    @Test
    public fun testBucketTiers() {
        assertEquals(
            LedgerCompactor.Bucket.Raw(date(2019, 6, 1)),
            LedgerCompactor.bucket(date(2019, 6, 1), now)
        )
        assertEquals(
            LedgerCompactor.Bucket.Monthly(year = 2018, month = 6),
            LedgerCompactor.bucket(date(2018, 6, 1), now)
        )
        assertEquals(
            LedgerCompactor.Bucket.Yearly(year = 2017),
            LedgerCompactor.bucket(date(2017, 6, 1), now)
        )
    }

    // MARK: - Plan

    /**
     * The plan names only the rows that fold together, so a caller applying it
     * leaves everything else alone.
     */
    @Test
    public fun testPlanNamesOnlyFoldedRows() {
        val rows = listOf(
            1 to execution(timestamp = date(2017, 3, 5)),
            2 to execution(timestamp = date(2017, 9, 20)),
            3 to execution(scheduleId = "other", timestamp = date(2017, 5, 1))
        )

        val plan = LedgerCompactor.plan(rows, now)

        assertEquals(listOf(1, 2), plan.replacedIds.sorted())
        assertEquals(1, plan.merged.size)
        assertEquals(2, plan.merged.first().effectiveCount)
        assertEquals(date(2017, 9, 20), plan.merged.first().timestamp)
    }

    @Test
    public fun testPlanIsEmptyWhenNothingMerges() {
        val rows = listOf(
            1 to execution(timestamp = date(2019, 12, 20)),
            2 to execution(timestamp = date(2019, 12, 25))
        )

        val plan = LedgerCompactor.plan(rows, now)

        assertEquals(emptyList<Int>(), plan.replacedIds)
        assertEquals(emptyList<LedgerEvent>(), plan.merged)
    }

    @Test
    public fun testPlanEmptyRowsIsEmpty() {
        val plan = LedgerCompactor.plan(emptyList<Pair<Int, LedgerEvent>>(), now)

        assertEquals(emptyList<Int>(), plan.replacedIds)
        assertEquals(emptyList<LedgerEvent>(), plan.merged)
    }

    /** The backstop folds across age buckets, so a plan spans their rows. */
    @Test
    public fun testPlanUnderBackstopSpansBuckets() {
        val rows = listOf(
            1 to execution(timestamp = date(2019, 7, 1)),
            2 to execution(timestamp = date(2019, 7, 2)),
            3 to execution(timestamp = date(2017, 3, 5))
        )

        val plan = LedgerCompactor.plan(rows, now, maxEvents = 1)

        assertEquals(listOf(1, 2, 3), plan.replacedIds.sorted())
        assertEquals(1, plan.merged.size)
        assertEquals(3, plan.merged.first().effectiveCount)
    }

    /** UTC, matching the zone the compactor buckets in. */
    private fun date(year: Int, month: Int, day: Int): Instant =
        ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
}
