/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.audience.TimeSpan
import com.urbanairship.json.JsonValue
import java.time.Instant
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the pure limit tally: which recorded events count, and which an
 * [ExclusionSet] subtracts.
 */
@RunWith(AndroidJUnit4::class)
public class LimitConfigTest {

    private val scheduleId = "schedule-A"
    private val otherScheduleId = "schedule-B"
    private val sharedId = "group-1"
    private val now: Instant = Instant.ofEpochMilli(10_000)

    private val context = LedgerLimitContext(scheduleId = scheduleId, currentSharedId = sharedId)

    private fun execution(
        scheduleId: String = this.scheduleId,
        sharedId: String? = null,
        triggerId: String? = null,
        timestamp: Instant = now,
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

    private fun triggered(scheduleId: String = this.scheduleId): LedgerEvent = LedgerEvent.Triggered(
        scheduleId = scheduleId,
        timestamp = now
    )

    private fun isOverLimit(
        limit: UInt,
        events: List<LedgerEvent>,
        exclude: ExclusionSet? = null
    ): Boolean = LedgerLimitEvaluator.isOverLimit(
        limit = limit,
        events = events,
        context = context,
        exclude = exclude
    )

    // region Counting

    @Test
    public fun testCountsExecutionsAgainstLimit() {
        val events = listOf(execution(), execution())
        assertTrue(isOverLimit(limit = 2U, events = events))
        assertFalse(isOverLimit(limit = 3U, events = events))
    }

    @Test
    public fun testTriggeredEventsDoNotCount() {
        val events = listOf(triggered(), triggered(), execution())
        assertFalse(isOverLimit(limit = 2U, events = events))
        assertTrue(isOverLimit(limit = 1U, events = events))
    }

    @Test
    public fun testCountFieldIsSummed() {
        // A single backfill event standing in for 5 legacy executions.
        val events = listOf(execution(count = 5, result = LedgerExecutionResult.BACKFILL))
        assertTrue(isOverLimit(limit = 5U, events = events))
        assertFalse(isOverLimit(limit = 6U, events = events))
    }

    @Test
    public fun testEveryResultCounts() {
        val events = LedgerExecutionResult.known.map { execution(result = it) }
        assertTrue(isOverLimit(limit = events.size.toUInt(), events = events))
        assertFalse(isOverLimit(limit = events.size.toUInt() + 1U, events = events))
    }

    /**
     * A result written by a newer SDK must still count. Dropping it would let a
     * schedule that had spent its budget execute again, which is the opposite
     * of erring toward showing less.
     */
    @Test
    public fun testUnrecognizedResultCounts() {
        val events = listOf(
            execution(result = LedgerExecutionResult.Unknown("some_future_result"))
        )

        assertTrue(isOverLimit(limit = 1U, events = events))
    }

    /** A rule listing only unrecognized results stays a no-op. */
    @Test
    public fun testRuleWithUnrecognizedResultSubtractsNothing() {
        val rule = ExclusionRule.fromJson(
            JsonValue.parseString(
                """{"source":{"type":"any"},"match":{"type":"execution","results":["some_future_result"]}}"""
            )
        )

        // The unknown value is dropped from the rule, leaving nothing to match...
        assertEquals(
            emptyList<LedgerExecutionResult>(),
            (rule.match as LedgerEventMatch.Execution).results
        )

        // ...so the event stays counted.
        assertTrue(
            isOverLimit(
                limit = 1U,
                events = listOf(execution()),
                exclude = ExclusionSet(listOf(rule))
            )
        )
    }

    @Test
    public fun testNoEventsIsUnderLimit() {
        assertFalse(isOverLimit(limit = 1U, events = emptyList()))
    }

    // endregion
    // region Source matching

    @Test
    public fun testExcludeOtherSchedules() {
        val events = listOf(
            execution(scheduleId = scheduleId),
            execution(scheduleId = otherScheduleId, sharedId = sharedId)
        )
        val exclude = ExclusionSet(listOf(ExclusionRule(source = LedgerSource.OtherSchedules)))
        // Only the own-schedule execution remains counted.
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeOwnSchedule() {
        val events = listOf(
            execution(scheduleId = scheduleId),
            execution(scheduleId = otherScheduleId, sharedId = sharedId)
        )
        val exclude = ExclusionSet(listOf(ExclusionRule(source = LedgerSource.OwnSchedule)))
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeSpecificSchedule() {
        val events = listOf(
            execution(scheduleId = scheduleId),
            execution(scheduleId = otherScheduleId, sharedId = sharedId)
        )
        val exclude = ExclusionSet(
            listOf(ExclusionRule(source = LedgerSource.Schedule(otherScheduleId)))
        )
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testAnySourceExcludesEverything() {
        val events = listOf(execution(), execution(scheduleId = otherScheduleId))
        val exclude = ExclusionSet(listOf(ExclusionRule(source = LedgerSource.AnySchedule)))
        assertFalse(isOverLimit(limit = 1U, events = events, exclude = exclude))
    }

    // endregion
    // region Match filters

    @Test
    public fun testExcludeByResult() {
        val events = listOf(
            execution(result = LedgerExecutionResult.SUCCEEDED),
            execution(result = LedgerExecutionResult.CONTROL)
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(
                        results = listOf(LedgerExecutionResult.CONTROL)
                    )
                )
            )
        )
        // The control execution is subtracted; only the succeeded one counts.
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeByCancel() {
        val events = listOf(
            execution(cancel = true),
            execution(cancel = null),
            execution(cancel = false)
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(cancel = true)
                )
            )
        )
        // Only the cancel:true event is removed, leaving two. A null cancel is
        // false, so it survives a `cancel = true` rule.
        assertTrue(isOverLimit(limit = 2U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 3U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeByTriggerId() {
        val events = listOf(execution(triggerId = "t1"), execution(triggerId = "t2"))
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(triggerId = "t1")
                )
            )
        )
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeByTimeBounds() {
        val old = execution(timestamp = Instant.ofEpochMilli(1_000))
        val recent = execution(timestamp = Instant.ofEpochMilli(9_000))
        // Subtract everything recorded before timestamp 5000.
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(
                        timeBounds = TimeSpan(
                            startTimestamp = null,
                            endTimestamp = Instant.ofEpochMilli(5_000)
                        )
                    )
                )
            )
        )
        assertFalse(isOverLimit(limit = 2U, events = listOf(old, recent), exclude = exclude))
        assertTrue(isOverLimit(limit = 1U, events = listOf(old, recent), exclude = exclude))
    }

    @Test
    public fun testTriggeredMatchDoesNotSubtractExecutions() {
        // Only executions are counted, so a `triggered` match subtracts nothing.
        val events = listOf(execution(), execution())
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Triggered()
                )
            )
        )
        assertTrue(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    // endregion
    // region Shared-group matching

    @Test
    public fun testExcludeCurrentSharedGroup() {
        val events = listOf(
            execution(sharedId = sharedId),
            execution(sharedId = "other-group")
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.Current)
                )
            )
        )
        // Only the event in the current shared group is removed.
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeNotCurrentSharedGroup() {
        val events = listOf(
            execution(sharedId = sharedId),
            execution(sharedId = "stale-group"),
            execution(sharedId = null)
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.NotCurrent)
                )
            )
        )
        // The stale group and the no-group event are dropped; only the current
        // group's event remains.
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeSharedGroupById() {
        val events = listOf(
            execution(sharedId = sharedId),
            execution(sharedId = "target-group")
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(
                        sharedGroup = SharedGroupMatch.Id("target-group")
                    )
                )
            )
        )
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    // endregion
    // region Forward compatibility

    private fun parseExclude(json: String): ExclusionSet =
        ExclusionSet.fromJson(JsonValue.parseString(json))

    @Test
    public fun testUnknownSourceIsNoOp() {
        // A rule with an unrecognized source type must subtract nothing.
        val exclude = parseExclude("""{ "or": [ { "source": { "type": "future_source" } } ] }""")
        assertEquals(listOf(ExclusionRule(source = LedgerSource.Unknown)), exclude.or)
        assertTrue(isOverLimit(limit = 2U, events = listOf(execution(), execution()), exclude = exclude))
    }

    @Test
    public fun testUnknownMatchTypeIsNoOp() {
        val exclude = parseExclude(
            """{ "or": [ { "source": { "type": "any" }, "match": { "type": "future_match" } } ] }"""
        )
        assertEquals(LedgerEventMatch.Unknown, exclude.or.first().match)
        assertTrue(isOverLimit(limit = 2U, events = listOf(execution(), execution()), exclude = exclude))
    }

    @Test
    public fun testUnknownResultValueIsDropped() {
        // A results list of only-unknown values matches nothing.
        val exclude = parseExclude(
            """
            { "or": [ { "source": { "type": "any" },
                        "match": { "type": "execution", "results": ["future_result"] } } ] }
            """
        )
        assertTrue(isOverLimit(limit = 1U, events = listOf(execution()), exclude = exclude))
    }

    @Test
    public fun testKnownResultsSurviveAnUnknownOne() {
        // An unknown value is dropped without discarding the values alongside it.
        val exclude = parseExclude(
            """
            { "or": [ { "source": { "type": "any" },
                        "match": { "type": "execution",
                                   "results": ["future_result", "control"] } } ] }
            """
        )
        val events = listOf(
            execution(result = LedgerExecutionResult.CONTROL),
            execution(result = LedgerExecutionResult.SUCCEEDED)
        )
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testUnknownSharedGroupIsNoOp() {
        val exclude = parseExclude(
            """
            { "or": [ { "source": { "type": "any" },
                        "match": { "type": "execution",
                                   "shared_group": { "type": "future" } } } ] }
            """
        )
        assertTrue(isOverLimit(limit = 2U, events = listOf(execution(), execution()), exclude = exclude))
    }

    // endregion
    // region Parsing

    @Test
    public fun testParseLimitConfig() {
        val config = LimitConfig.fromJson(
            JsonValue.parseString(
                """
                {
                  "exclude": {
                    "or": [
                      { "source": { "type": "other_schedules" } },
                      {
                        "source": { "type": "schedule", "schedule_id": "sched-x" },
                        "match": {
                          "type": "execution",
                          "results": ["control", "holdout"],
                          "cancel": true,
                          "trigger_id": "trig-1",
                          "shared_group": { "type": "id", "shared_id": "grp" },
                          "time_bounds": { "start_timestamp": 1000, "end_timestamp": 2000 }
                        }
                      }
                    ]
                  }
                }
                """
            )
        )

        val rules = requireNotNull(config.exclude).or
        assertEquals(2, rules.size)
        assertEquals(LedgerSource.OtherSchedules, rules[0].source)
        assertNull(rules[0].match)
        assertEquals(LedgerSource.Schedule("sched-x"), rules[1].source)
        assertEquals(
            LedgerEventMatch.Execution(
                timeBounds = TimeSpan(
                    startTimestamp = Instant.ofEpochMilli(1000),
                    endTimestamp = Instant.ofEpochMilli(2000)
                ),
                sharedGroup = SharedGroupMatch.Id("grp"),
                results = listOf(LedgerExecutionResult.CONTROL, LedgerExecutionResult.HOLDOUT),
                cancel = true,
                triggerId = "trig-1"
            ),
            rules[1].match
        )
    }

    @Test
    public fun testParseLimitConfigWithoutExclude() {
        // `exclude` is optional: a config with no exclusions parses to null, so
        // every execution counts against the cap.
        assertNull(LimitConfig.fromJson(JsonValue.parseString("{}")).exclude)
    }

    @Test
    public fun testParseExclusionSetWithoutOr() {
        assertEquals(emptyList<ExclusionRule>(), ExclusionSet.fromJson(JsonValue.parseString("{}")).or)
    }

    @Test
    public fun testLimitConfigJsonRoundTrip() {
        val config = LimitConfig(
            exclude = ExclusionSet(
                listOf(
                    ExclusionRule(
                        source = LedgerSource.OwnSchedule,
                        match = LedgerEventMatch.Execution(
                            results = listOf(LedgerExecutionResult.CONTROL)
                        )
                    ),
                    ExclusionRule(
                        source = LedgerSource.Schedule("x"),
                        match = LedgerEventMatch.Triggered(
                            timeBounds = TimeSpan(
                                startTimestamp = Instant.ofEpochMilli(1),
                                endTimestamp = null
                            ),
                            sharedGroup = SharedGroupMatch.NotCurrent,
                            triggerId = "t"
                        )
                    ),
                    ExclusionRule(source = LedgerSource.OtherSchedules),
                    ExclusionRule(source = LedgerSource.AnySchedule, match = LedgerEventMatch.Unknown)
                )
            )
        )

        assertEquals(config, LimitConfig.fromJson(config.toJsonValue()))
    }

    // endregion
    // region Winner-selection scenarios (web-push-sdk#959)

    /**
     * A pooled A/B group: two variants recorded executions under the shared
     * group; the winner also has a variant-control event.
     */
    private fun experimentEvents(): List<LedgerEvent> = listOf(
        execution(scheduleId = scheduleId, sharedId = sharedId, result = LedgerExecutionResult.SUCCEEDED),
        execution(scheduleId = scheduleId, sharedId = sharedId, result = LedgerExecutionResult.CONTROL),
        execution(scheduleId = otherScheduleId, sharedId = sharedId, result = LedgerExecutionResult.SUCCEEDED),
        execution(scheduleId = otherScheduleId, sharedId = sharedId, result = LedgerExecutionResult.SUCCEEDED)
    )

    @Test
    public fun testScenarioDifference() {
        // Pick up where the winner left off: exclude other schedules' events and
        // the winner's own control events. Only the winner's one succeeded
        // execution counts.
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(source = LedgerSource.OtherSchedules),
                ExclusionRule(
                    source = LedgerSource.OwnSchedule,
                    match = LedgerEventMatch.Execution(
                        results = listOf(LedgerExecutionResult.CONTROL)
                    )
                )
            )
        )
        assertTrue(isOverLimit(limit = 1U, events = experimentEvents(), exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = experimentEvents(), exclude = exclude))
    }

    @Test
    public fun testScenarioContinue() {
        // Count all shared history, excluding only the winner's own control.
        // 3 succeeded executions remain (1 own + 2 other).
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.OwnSchedule,
                    match = LedgerEventMatch.Execution(
                        results = listOf(LedgerExecutionResult.CONTROL)
                    )
                )
            )
        )
        assertTrue(isOverLimit(limit = 3U, events = experimentEvents(), exclude = exclude))
        assertFalse(isOverLimit(limit = 4U, events = experimentEvents(), exclude = exclude))
    }

    @Test
    public fun testScenarioReset() {
        // A reset keeps own history via a new shared_id: other variants' events no
        // longer match the schedule's scope, so the store never returns them. With
        // no exclusion, only the events still in scope count. This simulates the
        // post-reset scope: just the winner's own events.
        val ownEvents = listOf(
            execution(scheduleId = scheduleId, result = LedgerExecutionResult.SUCCEEDED),
            execution(scheduleId = scheduleId, result = LedgerExecutionResult.CONTROL)
        )
        assertTrue(isOverLimit(limit = 2U, events = ownEvents))
        assertFalse(isOverLimit(limit = 3U, events = ownEvents))
    }

    @Test
    public fun testScenarioFreshStart() {
        // A new shared_id plus a time-bounded self exclusion drops all of the
        // winner's own history before the cutoff, for a clean slate.
        val ownEvents = listOf(
            execution(timestamp = Instant.ofEpochMilli(5_000)),
            execution(timestamp = Instant.ofEpochMilli(5_001))
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.OwnSchedule,
                    match = LedgerEventMatch.Execution(
                        timeBounds = TimeSpan(
                            startTimestamp = null,
                            endTimestamp = Instant.ofEpochMilli(9_999)
                        )
                    )
                )
            )
        )
        assertFalse(isOverLimit(limit = 1U, events = ownEvents, exclude = exclude))
    }

    // endregion
}
