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

    private val context = LedgerLimitContext(scheduleId = scheduleId, currentSharedIds = setOf(sharedId))

    private fun execution(
        scheduleId: String = this.scheduleId,
        sharedIds: List<String> = emptyList(),
        triggerId: String? = null,
        timestamp: Instant = now,
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
            execution(scheduleId = otherScheduleId, sharedIds = listOf(sharedId))
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
            execution(scheduleId = otherScheduleId, sharedIds = listOf(sharedId))
        )
        val exclude = ExclusionSet(listOf(ExclusionRule(source = LedgerSource.OwnSchedule)))
        assertTrue(isOverLimit(limit = 1U, events = events, exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = events, exclude = exclude))
    }

    @Test
    public fun testExcludeSpecificSchedule() {
        val events = listOf(
            execution(scheduleId = scheduleId),
            execution(scheduleId = otherScheduleId, sharedIds = listOf(sharedId))
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
            execution(result = LedgerExecutionResult.HOLDOUT)
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(
                        results = listOf(LedgerExecutionResult.HOLDOUT)
                    )
                )
            )
        )
        // The holdout execution is subtracted; only the succeeded one counts.
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
            execution(sharedIds = listOf(sharedId)),
            execution(sharedIds = listOf("other-group"))
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
            execution(sharedIds = listOf(sharedId)),
            execution(sharedIds = listOf("stale-group")),
            execution(sharedIds = emptyList())
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
            execution(sharedIds = listOf(sharedId)),
            execution(sharedIds = listOf("target-group"))
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

    @Test
    public fun testCurrentMatchesOnPartialOverlap() {
        // The schedule is in `group-1`; the event was recorded while its
        // schedule was in `group-1` and another. Sharing one group makes it
        // current.
        val event = execution(sharedIds = listOf(sharedId, "other-group"))
        val rule = ExclusionRule(
            source = LedgerSource.AnySchedule,
            match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.Current)
        )

        assertTrue(rule.matches(event, context))
    }

    @Test
    public fun testNotCurrentNeedsEveryGroupToDiffer() {
        val overlapping = execution(sharedIds = listOf(sharedId, "other-group"))
        val disjoint = execution(sharedIds = listOf("other-group", "third-group"))
        val rule = ExclusionRule(
            source = LedgerSource.AnySchedule,
            match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.NotCurrent)
        )

        assertFalse(rule.matches(overlapping, context))
        assertTrue(rule.matches(disjoint, context))
    }

    /**
     * The other side of the overlap. Every test above varies the event's
     * groups against a schedule in one; a schedule can equally be in several,
     * and `Current` has to consider all of them. Matching on only the first
     * would leave an execution recorded under `promo` unsubtracted for a
     * schedule pooling `[experiment, promo]` — an over-counted tally that
     * suppresses a message still inside its budget.
     */
    @Test
    public fun testCurrentConsidersEveryGroupTheScheduleIsIn() {
        val multiGroup = LedgerLimitContext(
            scheduleId = scheduleId,
            currentSharedIds = setOf("experiment", "promo")
        )
        val rule = ExclusionRule(
            source = LedgerSource.AnySchedule,
            match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.Current)
        )

        // Matches on the schedule's second group, not just its first.
        assertTrue(rule.matches(execution(sharedIds = listOf("promo")), multiGroup))
        assertTrue(rule.matches(execution(sharedIds = listOf("experiment")), multiGroup))
        assertFalse(rule.matches(execution(sharedIds = listOf("unrelated")), multiGroup))
    }

    @Test
    public fun testNotCurrentConsidersEveryGroupTheScheduleIsIn() {
        val multiGroup = LedgerLimitContext(
            scheduleId = scheduleId,
            currentSharedIds = setOf("experiment", "promo")
        )
        val rule = ExclusionRule(
            source = LedgerSource.AnySchedule,
            match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.NotCurrent)
        )

        // Overlapping either of the schedule's groups makes the event current.
        assertFalse(rule.matches(execution(sharedIds = listOf("promo")), multiGroup))
        assertFalse(rule.matches(execution(sharedIds = listOf("unrelated", "promo")), multiGroup))
        assertTrue(rule.matches(execution(sharedIds = listOf("unrelated")), multiGroup))
    }

    /**
     * End to end through the evaluator rather than the rule alone, so the
     * context the evaluator builds is pinned too: it has to carry the
     * schedule's whole group set, not the first of it.
     */
    @Test
    public fun testMultiGroupScheduleExcludesEventsFromEitherGroup() {
        val multiGroup = LedgerLimitContext(
            scheduleId = scheduleId,
            currentSharedIds = setOf("experiment", "promo")
        )
        val events = listOf(
            execution(scheduleId = otherScheduleId, sharedIds = listOf("experiment")),
            execution(scheduleId = otherScheduleId, sharedIds = listOf("promo"))
        )
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.AnySchedule,
                    match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.Current)
                )
            )
        )

        // Both events are in one of the schedule's groups, so both are
        // subtracted and nothing is left to count against a limit of one.
        assertFalse(
            LedgerLimitEvaluator.isOverLimit(
                limit = 1U,
                events = events,
                context = multiGroup,
                exclude = exclude
            )
        )
    }

    /**
     * A schedule with no groups at all. `Current` means "recorded under the
     * groups this schedule is in now", and for such a schedule that is the
     * events recorded under none — the documented rule, and the one case every
     * other test here skips, since they all supply a non-empty group set.
     */
    @Test
    public fun testCurrentForAnUnpooledScheduleMatchesGrouplessEvents() {
        val unpooled = LedgerLimitContext(scheduleId = scheduleId, currentSharedIds = emptySet())
        val rule = ExclusionRule(
            source = LedgerSource.AnySchedule,
            match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.Current)
        )

        assertTrue(rule.matches(execution(sharedIds = emptyList()), unpooled))
        assertFalse(rule.matches(execution(sharedIds = listOf("group-1")), unpooled))
    }

    @Test
    public fun testNotCurrentForAnUnpooledScheduleMatchesGroupedEvents() {
        val unpooled = LedgerLimitContext(scheduleId = scheduleId, currentSharedIds = emptySet())
        val rule = ExclusionRule(
            source = LedgerSource.AnySchedule,
            match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.NotCurrent)
        )

        // The exact complement of Current: a groupless event is this
        // schedule's own history, anything grouped is somebody else's.
        assertFalse(rule.matches(execution(sharedIds = emptyList()), unpooled))
        assertTrue(rule.matches(execution(sharedIds = listOf("group-1")), unpooled))
    }

    @Test
    public fun testAbsoluteIdMatchesOneOfSeveralGroups() {
        val event = execution(sharedIds = listOf("group-a", "group-b"))

        assertTrue(
            ExclusionRule(
                source = LedgerSource.AnySchedule,
                match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.Id("group-b"))
            ).matches(event, context)
        )
        assertFalse(
            ExclusionRule(
                source = LedgerSource.AnySchedule,
                match = LedgerEventMatch.Execution(sharedGroup = SharedGroupMatch.Id("group-c"))
            ).matches(event, context)
        )
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
                                   "results": ["future_result", "variant_miss"] } } ] }
            """
        )
        val events = listOf(
            execution(result = LedgerExecutionResult.VARIANT_MISS),
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
                  "type": "shared",
                  "exclude": {
                    "or": [
                      { "source": { "type": "other_schedules" } },
                      {
                        "source": { "type": "schedule", "schedule_id": "sched-x" },
                        "match": {
                          "type": "execution",
                          "results": ["variant_miss", "holdout"],
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

        check(config is LimitConfig.Shared)
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
                results = listOf(LedgerExecutionResult.VARIANT_MISS, LedgerExecutionResult.HOLDOUT),
                cancel = true,
                triggerId = "trig-1"
            ),
            rules[1].match
        )
    }

    @Test
    public fun testParseSelfLimitConfig() {
        val config = LimitConfig.fromJson(
            JsonValue.parseString(
                """
                {
                  "type": "self",
                  "exclude": {
                    "or": [
                      { "match": { "type": "execution", "results": ["holdout"] } }
                    ]
                  }
                }
                """
            )
        )

        check(config is LimitConfig.Self)
        val rules = requireNotNull(config.exclude)
        assertEquals(1, rules.size)
        assertEquals(
            LedgerEventMatch.Execution(results = listOf(LedgerExecutionResult.HOLDOUT)),
            rules[0].match
        )
    }

    @Test
    public fun testParseLimitConfigWithoutExclude() {
        // `exclude` is optional: a config with no exclusions parses to null, so
        // every execution counts against the cap.
        val config = LimitConfig.fromJson(JsonValue.parseString("""{ "type": "shared" }"""))
        check(config is LimitConfig.Shared)
        assertNull(config.exclude)
    }

    @Test
    public fun testParseLimitConfigDefaultsToSelf() {
        // Omitting `type` (or the whole config) must not silently opt a
        // schedule into pooling — it defaults to the narrow, self-only variant.
        val config = LimitConfig.fromJson(JsonValue.parseString("{}"))
        check(config is LimitConfig.Self)
        assertNull(config.exclude)
    }

    @Test
    public fun testParseExclusionSetWithoutOr() {
        assertEquals(emptyList<ExclusionRule>(), ExclusionSet.fromJson(JsonValue.parseString("{}")).or)
    }

    @Test
    public fun testSharedLimitConfigJsonRoundTrip() {
        val config = LimitConfig.Shared(
            exclude = ExclusionSet(
                listOf(
                    ExclusionRule(
                        source = LedgerSource.OwnSchedule,
                        match = LedgerEventMatch.Execution(
                            results = listOf(LedgerExecutionResult.HOLDOUT)
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

    @Test
    public fun testSelfLimitConfigJsonRoundTrip() {
        val config = LimitConfig.Self(
            exclude = listOf(
                SelfExclusionRule(
                    match = LedgerEventMatch.Execution(
                        results = listOf(LedgerExecutionResult.HOLDOUT)
                    )
                ),
                SelfExclusionRule(match = null)
            )
        )

        assertEquals(config, LimitConfig.fromJson(config.toJsonValue()))
    }

    // endregion
    // region Winner-selection scenarios (web-push-sdk#959)

    /**
     * A pooled A/B group: two variants recorded executions under the shared
     * group; the winner also has a holdout event.
     */
    private fun experimentEvents(): List<LedgerEvent> = listOf(
        execution(scheduleId = scheduleId, sharedIds = listOf(sharedId), result = LedgerExecutionResult.SUCCEEDED),
        execution(scheduleId = scheduleId, sharedIds = listOf(sharedId), result = LedgerExecutionResult.HOLDOUT),
        execution(scheduleId = otherScheduleId, sharedIds = listOf(sharedId), result = LedgerExecutionResult.SUCCEEDED),
        execution(scheduleId = otherScheduleId, sharedIds = listOf(sharedId), result = LedgerExecutionResult.SUCCEEDED)
    )

    @Test
    public fun testScenarioDifference() {
        // Pick up where the winner left off: exclude other schedules' events and
        // the winner's own holdout events. Only the winner's one succeeded
        // execution counts.
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(source = LedgerSource.OtherSchedules),
                ExclusionRule(
                    source = LedgerSource.OwnSchedule,
                    match = LedgerEventMatch.Execution(
                        results = listOf(LedgerExecutionResult.HOLDOUT)
                    )
                )
            )
        )
        assertTrue(isOverLimit(limit = 1U, events = experimentEvents(), exclude = exclude))
        assertFalse(isOverLimit(limit = 2U, events = experimentEvents(), exclude = exclude))
    }

    @Test
    public fun testScenarioContinue() {
        // Count all shared history, excluding only the winner's own holdout.
        // 3 succeeded executions remain (1 own + 2 other).
        val exclude = ExclusionSet(
            listOf(
                ExclusionRule(
                    source = LedgerSource.OwnSchedule,
                    match = LedgerEventMatch.Execution(
                        results = listOf(LedgerExecutionResult.HOLDOUT)
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
            execution(scheduleId = scheduleId, result = LedgerExecutionResult.HOLDOUT)
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
