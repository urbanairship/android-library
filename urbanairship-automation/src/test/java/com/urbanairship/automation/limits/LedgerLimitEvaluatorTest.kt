/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.VariantAudience
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.ledgerSharedIds
import com.urbanairship.automation.limits.storage.LedgerDatabase
import com.urbanairship.json.JsonValue
import java.time.Instant
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Covers store-backed limit evaluation and `limit_config` schedule parsing. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class LedgerLimitEvaluatorTest {

    private val db =
        LedgerDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val store = LedgerStore(db.dao)
    private val evaluator = LedgerLimitEvaluator(store)

    @After
    public fun tearDown() {
        db.close()
    }

    private fun schedule(
        id: String = "schedule-A",
        limit: UInt?,
        sharedIds: List<String> = emptyList(),
        limitConfig: LimitConfig? = null,
        variantAudienceId: String? = null
    ): AutomationSchedule = AutomationSchedule(
        identifier = id,
        triggers = emptyList(),
        limit = limit,
        data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("actions")),
        ledgerConfig = sharedIds.ifEmpty { null }?.let { LedgerConfig(sharedIds = it) },
        limitConfig = limitConfig,
        variantAudience = variantAudienceId?.let(::variantAudience),
        // Whole seconds: `created` is encoded as an ISO 8601 string, so a
        // sub-second value would not survive a JSON round trip.
        created = Instant.ofEpochSecond(1_700_000_000)
    )

    private fun variantAudience(id: String): VariantAudience = VariantAudience.fromJson(
        JsonValue.parseString(
            """
            {
              "id": "$id",
              "audience_hash": {
                "hash_prefix": "prefix:",
                "num_hash_buckets": 16384,
                "hash_identifier": "contact",
                "hash_algorithm": "farm_hash"
              },
              "audience_subset": { "min_hash_bucket": 0, "max_hash_bucket": 100 }
            }
            """
        ).requireMap()
    )

    private suspend fun recordExecution(
        scheduleId: String = "schedule-A",
        sharedIds: List<String> = emptyList(),
        variantAudienceId: String? = null,
        result: LedgerExecutionResult = LedgerExecutionResult.SUCCEEDED,
        count: Int? = null
    ) {
        store.recordEvents(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = scheduleId,
                    sharedIds = sharedIds.ifEmpty { null },
                    variantAudienceId = variantAudienceId,
                    timestamp = Instant.ofEpochMilli(10_000),
                    count = count,
                    result = result
                )
            )
        )
    }

    @Test
    public fun testUnderLimit(): TestResult = runTest {
        recordExecution()
        assertFalse(evaluator.isOverLimit(schedule(limit = 2U)))
    }

    /**
     * Exclusion rules are covered exhaustively in `LimitConfigTest`, but always
     * against a hand-built [LedgerLimitContext]. These two run a shared-group
     * rule through the real evaluator, so the step that builds that context
     * from the schedule is covered too — the only place the schedule's own
     * groups reach the matcher. Were that set to arrive empty, every `current`
     * rule would silently stop excluding and every `not_current` rule would
     * exclude everything, in both cases with no error anywhere.
     */
    @Test
    public fun testExcludesCurrentSharedGroupEndToEnd(): TestResult = runTest {
        // Recorded by a sibling under the second of this schedule's two groups,
        // so only a context carrying both of them can match it.
        recordExecution(scheduleId = "sibling", sharedIds = listOf("promo"))

        val schedule = schedule(
            limit = 1U,
            sharedIds = listOf("experiment", "promo"),
            limitConfig = LimitConfig.Shared(
                exclude = ExclusionSet(
                    listOf(
                        ExclusionRule(
                            source = LedgerSource.AnySchedule,
                            match = LedgerEventMatch.Execution(
                                sharedGroup = SharedGroupMatch.Current
                            )
                        )
                    )
                )
            )
        )

        // The one pooled event is excluded, so nothing counts against the cap.
        assertFalse(evaluator.isOverLimit(schedule))
    }

    @Test
    public fun testExcludesNotCurrentSharedGroupEndToEnd(): TestResult = runTest {
        recordExecution(scheduleId = "sibling", sharedIds = listOf("promo"))

        val schedule = schedule(
            limit = 1U,
            sharedIds = listOf("experiment", "promo"),
            limitConfig = LimitConfig.Shared(
                exclude = ExclusionSet(
                    listOf(
                        ExclusionRule(
                            source = LedgerSource.AnySchedule,
                            match = LedgerEventMatch.Execution(
                                sharedGroup = SharedGroupMatch.NotCurrent
                            )
                        )
                    )
                )
            )
        )

        // The event IS in one of the current groups, so the rule does not
        // exclude it and it still counts — the opposite direction from above.
        assertTrue(evaluator.isOverLimit(schedule))
    }

    private fun variantAudienceExclusion(match: VariantAudienceMatch) = LimitConfig.Shared(
        exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(variantAudience = match)
                )
            )
        )
    )

    /**
     * Runs `current` and `not_current` through the real evaluator, so the
     * schedule's own `variant_audience.id` reaching the matcher is covered.
     */
    @Test
    public fun testExcludesCurrentVariantAudienceEndToEnd(): TestResult = runTest {
        recordExecution(variantAudienceId = "iteration-2")

        val schedule = schedule(
            limit = 1U,
            variantAudienceId = "iteration-2",
            limitConfig = variantAudienceExclusion(VariantAudienceMatch.Current)
        )

        assertFalse(evaluator.isOverLimit(schedule))
    }

    @Test
    public fun testExcludesNotCurrentVariantAudienceEndToEnd(): TestResult = runTest {
        recordExecution(variantAudienceId = "iteration-1")
        recordExecution(variantAudienceId = null)

        fun schedule(limit: UInt) = schedule(
            limit = limit,
            variantAudienceId = "iteration-2",
            limitConfig = variantAudienceExclusion(VariantAudienceMatch.NotCurrent)
        )

        // The past iteration's event is excluded; the untagged one still counts.
        assertFalse(evaluator.isOverLimit(schedule(limit = 2U)))
        assertTrue(evaluator.isOverLimit(schedule(limit = 1U)))
    }

    @Test
    public fun testCurrentVariantAudienceMatchesNothingForAScheduleWithoutOne(): TestResult = runTest {
        recordExecution(variantAudienceId = "iteration-2")

        val schedule = schedule(
            limit = 1U,
            limitConfig = variantAudienceExclusion(VariantAudienceMatch.Current)
        )

        assertTrue(evaluator.isOverLimit(schedule))
    }

    @Test
    public fun testAtLimit(): TestResult = runTest {
        recordExecution()
        recordExecution()
        assertTrue(evaluator.isOverLimit(schedule(limit = 2U)))
    }

    @Test
    public fun testNullLimitDefaultsToOne(): TestResult = runTest {
        assertFalse(evaluator.isOverLimit(schedule(limit = null)))
        recordExecution()
        assertTrue(evaluator.isOverLimit(schedule(limit = null)))
    }

    @Test
    public fun testZeroLimitIsUnlimited(): TestResult = runTest {
        repeat(50) { recordExecution() }
        assertFalse(evaluator.isOverLimit(schedule(limit = 0U)))
    }

    @Test
    public fun testTriggeredEventsDoNotCount(): TestResult = runTest {
        store.recordEvents(
            listOf(
                LedgerEvent.Triggered(
                    scheduleId = "schedule-A",
                    timestamp = Instant.ofEpochMilli(10_000)
                )
            )
        )
        assertFalse(evaluator.isOverLimit(schedule(limit = 1U)))
    }

    @Test
    public fun testCountsAcrossSharedGroup(): TestResult = runTest {
        // Two different schedules recording under the same shared group pool.
        recordExecution(scheduleId = "schedule-A", sharedIds = listOf("group-1"))
        recordExecution(scheduleId = "schedule-B", sharedIds = listOf("group-1"))

        val config = LimitConfig.Shared()
        assertTrue(
            evaluator.isOverLimit(
                schedule(
                    id = "schedule-A",
                    limit = 2U,
                    sharedIds = listOf("group-1"),
                    limitConfig = config
                )
            )
        )
    }

    @Test
    public fun testSharedGroupIgnoredWithoutSharedConfig(): TestResult = runTest {
        // `shared_ids` alone must not pool anything for this schedule's own
        // limit: without `limit_config: {type: 'shared'}`, only its own events
        // count, no matter what schedule-B declares.
        recordExecution(scheduleId = "schedule-A", sharedIds = listOf("group-1"))
        recordExecution(scheduleId = "schedule-B", sharedIds = listOf("group-1"))

        assertFalse(
            evaluator.isOverLimit(
                schedule(id = "schedule-A", limit = 2U, sharedIds = listOf("group-1"))
            )
        )
    }

    @Test
    public fun testEventsOutsideScopeIgnored(): TestResult = runTest {
        // An event for an unrelated schedule/group must not count, even with
        // pooling turned on.
        recordExecution(scheduleId = "schedule-Z", sharedIds = listOf("other-group"))

        val config = LimitConfig.Shared()
        assertFalse(
            evaluator.isOverLimit(
                schedule(
                    id = "schedule-A",
                    limit = 1U,
                    sharedIds = listOf("group-1"),
                    limitConfig = config
                )
            )
        )
    }

    @Test
    public fun testInheritsNamedSchedulesPreExistingHistory(): TestResult = runTest {
        // schedule-A recorded history before it was ever part of a group (e.g.
        // backfilled), so its events carry no groups at all. schedule-B is
        // created naming schedule-A's own ID as one of its shared_ids, to pick
        // up that history. The cross-field match (schedule_id <-> shared_ids)
        // is what makes this work, since schedule-A's events have no group to
        // match against directly.
        recordExecution(scheduleId = "schedule-A", sharedIds = emptyList(), count = 3)

        val config = LimitConfig.Shared()
        assertTrue(
            evaluator.isOverLimit(
                schedule(
                    id = "schedule-B",
                    limit = 3U,
                    sharedIds = listOf("schedule-A"),
                    limitConfig = config
                )
            )
        )
        assertFalse(
            evaluator.isOverLimit(
                schedule(
                    id = "schedule-B",
                    limit = 4U,
                    sharedIds = listOf("schedule-A"),
                    limitConfig = config
                )
            )
        )
    }

    @Test
    public fun testLimitConfigExcludesOtherSchedules(): TestResult = runTest {
        recordExecution(scheduleId = "schedule-A", sharedIds = listOf("group-1"))
        recordExecution(scheduleId = "schedule-B", sharedIds = listOf("group-1"))

        val config = LimitConfig.Shared(
            exclude = ExclusionSet(listOf(ExclusionRule(source = LedgerSource.OtherSchedules)))
        )

        // schedule-B's event is excluded, leaving only schedule-A's one event.
        assertTrue(
            evaluator.isOverLimit(
                schedule(id = "schedule-A", limit = 1U, sharedIds = listOf("group-1"), limitConfig = config)
            )
        )
        assertFalse(
            evaluator.isOverLimit(
                schedule(id = "schedule-A", limit = 2U, sharedIds = listOf("group-1"), limitConfig = config)
            )
        )
    }

    @Test
    public fun testBackfillCountContributes(): TestResult = runTest {
        recordExecution(result = LedgerExecutionResult.BACKFILL, count = 4)
        assertTrue(evaluator.isOverLimit(schedule(limit = 4U)))
        assertFalse(evaluator.isOverLimit(schedule(limit = 5U)))
    }

    @Test
    public fun testReadFailureIsNotOverLimit(): TestResult = runTest {
        val failing = object : LedgerStoreInterface {
            override suspend fun recordEvents(events: List<LedgerEvent>): Unit = Unit
            override suspend fun recordEventsUnless(
                scheduleId: String,
                events: List<LedgerEvent>,
                alreadyRecorded: (LedgerEvent) -> Boolean
            ): Boolean = true
            override suspend fun events(scheduleId: String, sharedIds: List<String>): List<LedgerEvent> =
                throw IllegalStateException("read failed")
            override suspend fun hasEvents(scheduleId: String): Boolean = false
            override suspend fun deleteEvents(scopes: List<LedgerScope>): Unit = Unit
            override suspend fun retainEvents(
                liveScheduleIds: Set<String>,
                liveSharedIds: Set<String>
            ): Unit = Unit
            override suspend fun compact(now: Instant): Unit = Unit
        }

        // A ledger read failure must never wedge execution.
        assertFalse(LedgerLimitEvaluator(failing).isOverLimit(schedule(limit = 1U)))
    }

    // region Schedule parsing

    @Test
    public fun testScheduleParsesLimitConfig() {
        val schedule = AutomationSchedule.fromJson(
            JsonValue.parseString(
                """
                {
                  "id": "test-schedule",
                  "type": "actions",
                  "actions": { "foo": "bar" },
                  "triggers": [],
                  "created": "2026-01-01T00:00:00",
                  "limit": 3,
                  "ledger_config": { "shared_ids": ["group-1"] },
                  "limit_config": {
                    "type": "shared",
                    "exclude": {
                      "or": [
                        {
                          "source": { "type": "own_schedule" },
                          "match": { "type": "execution", "results": ["holdout"] }
                        }
                      ]
                    }
                  }
                }
                """
            )
        )

        assertEquals(3U, schedule.limit)
        assertEquals(listOf("group-1"), schedule.ledgerSharedIds)
        val limitConfig = schedule.limitConfig
        check(limitConfig is LimitConfig.Shared)
        assertEquals(
            ExclusionRule(
                source = LedgerSource.OwnSchedule,
                match = LedgerEventMatch.Execution(
                    results = listOf(LedgerExecutionResult.HOLDOUT)
                )
            ),
            limitConfig.exclude?.or?.firstOrNull()
        )
    }

    @Test
    public fun testScheduleWithoutLimitConfigIsNull() {
        val schedule = AutomationSchedule.fromJson(
            JsonValue.parseString(
                """
                {
                  "id": "test-schedule",
                  "type": "actions",
                  "actions": { "foo": "bar" },
                  "triggers": [],
                  "created": "2026-01-01T00:00:00"
                }
                """
            )
        )
        assertNull(schedule.limitConfig)
    }

    @Test
    public fun testScheduleLimitConfigSurvivesJsonRoundTrip() {
        val schedule = schedule(
            limit = 2U,
            sharedIds = listOf("group-1"),
            limitConfig = LimitConfig.Shared(
                exclude = ExclusionSet(
                    listOf(ExclusionRule(source = LedgerSource.OtherSchedules))
                )
            )
        )

        assertEquals(schedule, AutomationSchedule.fromJson(schedule.toJsonValue()))
    }

    // endregion
    // region Multiple shared groups

    /**
     * Every test in this region opts in with [LimitConfig.Shared]. Belonging to
     * several groups widens what a schedule reaches once it has opted in; it is
     * never what decides that it reaches at all. Without the config these would
     * all pass trivially — the ones asserting `false` most of all, since with
     * pooling off nothing is ever in scope to count.
     */
    @Test
    public fun testCountsAcrossEveryGroupTheScheduleBelongsTo(): TestResult = runTest {
        recordExecution(scheduleId = "schedule-B", sharedIds = listOf("group-1"))
        recordExecution(scheduleId = "schedule-C", sharedIds = listOf("group-2"))

        // One event from each pool, against a cap of 2.
        assertTrue(
            evaluator.isOverLimit(
                schedule(
                    limit = 2U,
                    sharedIds = listOf("group-1", "group-2"),
                    limitConfig = LimitConfig.Shared()
                )
            )
        )
        assertFalse(
            evaluator.isOverLimit(
                schedule(
                    limit = 3U,
                    sharedIds = listOf("group-1", "group-2"),
                    limitConfig = LimitConfig.Shared()
                )
            )
        )
    }

    @Test
    public fun testGroupTheScheduleHasLeftDoesNotCount(): TestResult = runTest {
        recordExecution(scheduleId = "schedule-B", sharedIds = listOf("group-2"))

        // Only in group-1 now, so group-2's history is out of scope — even
        // though this schedule has opted into pooling.
        assertFalse(
            evaluator.isOverLimit(
                schedule(
                    limit = 1U,
                    sharedIds = listOf("group-1"),
                    limitConfig = LimitConfig.Shared()
                )
            )
        )
    }

    @Test
    public fun testEventInSeveralGroupsCountsOnce(): TestResult = runTest {
        // Recorded by a sibling that was in both pools. The evaluating schedule
        // is in both too, so the event matches twice over — it must still
        // count once.
        recordExecution(scheduleId = "schedule-B", sharedIds = listOf("group-1", "group-2"))

        assertFalse(
            evaluator.isOverLimit(
                schedule(
                    limit = 2U,
                    sharedIds = listOf("group-1", "group-2"),
                    limitConfig = LimitConfig.Shared()
                )
            )
        )
    }

    @Test
    public fun testCombinedExperimentPoolsBothPriorGroups(): TestResult = runTest {
        // A pooled with its partner under group-a, B with its partner under
        // group-b. Combining them adds `shared` without either leaving its own
        // group, so A's tally now sees both partners' history.
        recordExecution(scheduleId = "a-partner", sharedIds = listOf("group-a"))
        recordExecution(scheduleId = "b-partner", sharedIds = listOf("group-b", "shared"))

        assertTrue(
            evaluator.isOverLimit(
                schedule(
                    id = "a",
                    limit = 2U,
                    sharedIds = listOf("group-a", "shared"),
                    limitConfig = LimitConfig.Shared()
                )
            )
        )
    }

    /**
     * The opt-in gates every group, not just the first. A schedule in several
     * groups that has not opted in counts only its own events, the same as an
     * unpooled one.
     */
    @Test
    public fun testSeveralGroupsStillIgnoredWithoutSharedConfig(): TestResult = runTest {
        recordExecution(scheduleId = "schedule-B", sharedIds = listOf("group-1"))
        recordExecution(scheduleId = "schedule-C", sharedIds = listOf("group-2"))

        assertFalse(
            evaluator.isOverLimit(schedule(limit = 1U, sharedIds = listOf("group-1", "group-2")))
        )
    }

    /**
     * The opt-in decides which events are *fetched*; it must not also narrow
     * what `current` *means*. A `Self` schedule still has real groups, and an
     * exclusion rule filtering its own events by `shared_group: current` has
     * to match against them — otherwise `current` would silently come to mean
     * "recorded with no group" for every un-opted-in schedule, and a rule that
     * should subtract the schedule's own pooled history would quietly subtract
     * nothing.
     */
    @Test
    public fun testSelfConfigStillResolvesCurrentAgainstItsRealGroups(): TestResult = runTest {
        // This schedule's OWN event, recorded under its own group — in scope
        // even for `Self`, since it was recorded by the schedule itself.
        recordExecution(scheduleId = "schedule-A", sharedIds = listOf("group-1", "group-2"))

        val schedule = schedule(
            limit = 1U,
            sharedIds = listOf("group-1", "group-2"),
            limitConfig = LimitConfig.Self(
                exclude = listOf(
                    SelfExclusionRule(
                        match = LedgerEventMatch.Execution(
                            sharedGroup = SharedGroupMatch.Current
                        )
                    )
                )
            )
        )

        // Excluded, so nothing counts. Were the context's groups gated by the
        // config variant, they would arrive empty, `current` would match only
        // groupless events, and this event would count instead.
        assertFalse(evaluator.isOverLimit(schedule))
    }

    @Test
    public fun testParsesMultipleSharedIds() {
        val schedule = AutomationSchedule.fromJson(
            JsonValue.parseString(
                """
                {
                  "id": "test-schedule",
                  "type": "actions",
                  "actions": { "foo": "bar" },
                  "triggers": [],
                  "created": "2026-01-01T00:00:00",
                  "ledger_config": { "shared_ids": ["group-2", "group-1", "group-1"] }
                }
                """
            )
        )

        // Canonical form: de-duplicated and sorted, whatever order they arrive in.
        assertEquals(listOf("group-1", "group-2"), schedule.ledgerSharedIds)
    }

    // endregion
}
