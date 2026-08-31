/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.automation.AutomationSchedule
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
        sharedId: String? = null,
        limitConfig: LimitConfig? = null
    ): AutomationSchedule = AutomationSchedule(
        identifier = id,
        triggers = emptyList(),
        limit = limit,
        data = AutomationSchedule.ScheduleData.Actions(JsonValue.wrap("actions")),
        ledgerConfig = sharedId?.let { LedgerConfig(sharedId = it) },
        limitConfig = limitConfig,
        // Whole seconds: `created` is encoded as an ISO 8601 string, so a
        // sub-second value would not survive a JSON round trip.
        created = Instant.ofEpochSecond(1_700_000_000)
    )

    private suspend fun recordExecution(
        scheduleId: String = "schedule-A",
        sharedId: String? = null,
        result: LedgerExecutionResult = LedgerExecutionResult.SUCCEEDED,
        count: Int? = null
    ) {
        store.recordEvents(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = scheduleId,
                    sharedId = sharedId,
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
        recordExecution(scheduleId = "schedule-A", sharedId = "group-1")
        recordExecution(scheduleId = "schedule-B", sharedId = "group-1")

        assertTrue(
            evaluator.isOverLimit(schedule(id = "schedule-A", limit = 2U, sharedId = "group-1"))
        )
    }

    @Test
    public fun testEventsOutsideScopeIgnored(): TestResult = runTest {
        // An event for an unrelated schedule/group must not count.
        recordExecution(scheduleId = "schedule-Z", sharedId = "other-group")

        assertFalse(
            evaluator.isOverLimit(schedule(id = "schedule-A", limit = 1U, sharedId = "group-1"))
        )
    }

    @Test
    public fun testLimitConfigExcludesOtherSchedules(): TestResult = runTest {
        recordExecution(scheduleId = "schedule-A", sharedId = "group-1")
        recordExecution(scheduleId = "schedule-B", sharedId = "group-1")

        val config = LimitConfig(
            exclude = ExclusionSet(listOf(ExclusionRule(source = LedgerSource.OtherSchedules)))
        )

        // schedule-B's event is excluded, leaving only schedule-A's one event.
        assertTrue(
            evaluator.isOverLimit(
                schedule(id = "schedule-A", limit = 1U, sharedId = "group-1", limitConfig = config)
            )
        )
        assertFalse(
            evaluator.isOverLimit(
                schedule(id = "schedule-A", limit = 2U, sharedId = "group-1", limitConfig = config)
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
            override suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent> =
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
                  "ledger_config": { "shared_id": "group-1" },
                  "limit_config": {
                    "exclude": {
                      "or": [
                        {
                          "source": { "type": "own_schedule" },
                          "match": { "type": "execution", "results": ["control"] }
                        }
                      ]
                    }
                  }
                }
                """
            )
        )

        assertEquals(3U, schedule.limit)
        assertEquals("group-1", schedule.ledgerConfig?.sharedId)
        assertEquals(
            ExclusionRule(
                source = LedgerSource.OwnSchedule,
                match = LedgerEventMatch.Execution(
                    results = listOf(LedgerExecutionResult.CONTROL)
                )
            ),
            schedule.limitConfig?.exclude?.or?.firstOrNull()
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
            sharedId = "group-1",
            limitConfig = LimitConfig(
                exclude = ExclusionSet(
                    listOf(ExclusionRule(source = LedgerSource.OtherSchedules))
                )
            )
        )

        assertEquals(schedule, AutomationSchedule.fromJson(schedule.toJsonValue()))
    }

    // endregion
}
