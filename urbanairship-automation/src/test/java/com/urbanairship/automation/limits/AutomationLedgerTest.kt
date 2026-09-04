/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.limits.storage.LedgerDatabase
import java.time.Instant
import java.time.temporal.ChronoUnit
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [AutomationLedger.recordExecutionIfNoneSince], the guarded record used
 * by interruption recovery, and [AutomationLedger.reconcile], the maintenance
 * pass run off remote-data reconciliation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AutomationLedgerTest {

    private val db =
        LedgerDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val store = LedgerStore(db.dao)
    private val clock = TestClock().apply { currentTime = Instant.ofEpochMilli(10_000) }
    private val ledger = AutomationLedger(store, clock)

    private val startedExecuting: Instant = Instant.ofEpochMilli(5_000)

    @After
    public fun tearDown() {
        db.close()
    }

    private suspend fun recordIfNoneSince() = ledger.recordExecutionIfNoneSince(
        scheduleId = "schedule-A",
        sharedId = "group-1",
        triggerId = "trigger-1",
        result = LedgerExecutionResult.SUCCEEDED,
        cancel = false,
        since = startedExecuting
    )

    private suspend fun executions(): List<LedgerEvent.Execution> =
        store.events("schedule-A", "group-1").filterIsInstance<LedgerEvent.Execution>()

    @Test
    public fun testRecordsWhenNothingRecorded(): TestResult = runTest {
        recordIfNoneSince()
        assertEquals(1, executions().size)
    }

    @Test
    public fun testSkipsWhenTheExecutorAlreadyRecorded(): TestResult = runTest {
        // The executor recorded the outcome, then the app died before the state
        // change persisted. Recovery must not record a second execution.
        ledger.recordExecution(
            scheduleId = "schedule-A",
            sharedId = "group-1",
            triggerId = "trigger-1",
            result = LedgerExecutionResult.SUCCEEDED,
            cancel = false
        )

        recordIfNoneSince()

        assertEquals(1, executions().size)
    }

    @Test
    public fun testRecordsWhenOnlyOlderExecutionsExist(): TestResult = runTest {
        // A previous run's execution predates this one, so it must not suppress
        // the record for the interrupted attempt.
        store.recordEvents(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = "schedule-A",
                    sharedId = "group-1",
                    timestamp = startedExecuting.minusMillis(1),
                    result = LedgerExecutionResult.SUCCEEDED
                )
            )
        )

        recordIfNoneSince()

        assertEquals(2, executions().size)
    }

    @Test
    public fun testAnotherSchedulesExecutionDoesNotSuppress(): TestResult = runTest {
        // A sibling in the same shared group is in scope for the limit tally but
        // says nothing about whether this schedule recorded its own outcome.
        store.recordEvents(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = "schedule-B",
                    sharedId = "group-1",
                    timestamp = clock.now(),
                    result = LedgerExecutionResult.SUCCEEDED
                )
            )
        )

        recordIfNoneSince()

        assertEquals(1, executions().count { it.scheduleId == "schedule-A" })
    }

    @Test
    public fun testBackfillDoesNotSuppress(): TestResult = runTest {
        // Migration backfills a pre-ledger count timestamped at migration, which
        // is newer than the interrupted attempt's start. It stands for history,
        // not for this attempt's outcome, so it must not suppress the record.
        store.recordEvents(
            listOf(
                LedgerEvent.Execution(
                    scheduleId = "schedule-A",
                    timestamp = clock.now(),
                    count = 3,
                    result = LedgerExecutionResult.BACKFILL
                )
            )
        )

        recordIfNoneSince()

        assertEquals(1, executions().count { it.result == LedgerExecutionResult.SUCCEEDED })
    }

    @Test
    public fun testTriggeredEventDoesNotSuppress(): TestResult = runTest {
        // Only an execution outcome counts as already recorded.
        ledger.recordTriggered(
            scheduleId = "schedule-A",
            sharedId = "group-1",
            triggerId = "trigger-1"
        )

        recordIfNoneSince()

        assertEquals(1, executions().size)
    }

    // MARK: - Reconciliation

    @Test
    public fun testReconcileRetainsBeforeCompacting(): TestResult = runTest {
        // Retention has to run first: compacting the events of schedules that
        // are about to be dropped is wasted work.
        val spy = RecordingStore()
        val spiedLedger = AutomationLedger(spy, clock)

        spiedLedger.reconcile(liveScheduleIds = setOf("schedule-A"), liveSharedIds = setOf("group-1"))

        assertEquals(listOf("retain", "compact"), spy.calls)
        assertEquals(setOf("schedule-A"), spy.liveScheduleIds)
        assertEquals(setOf("group-1"), spy.liveSharedIds)
        assertEquals(clock.now(), spy.compactNow)
    }

    @Test
    public fun testReconcileDropsOrphanedEvents(): TestResult = runTest {
        store.recordEvents(
            listOf(
                LedgerEvent.Triggered(scheduleId = "schedule-A", timestamp = clock.now()),
                LedgerEvent.Triggered(scheduleId = "gone", timestamp = clock.now())
            )
        )

        ledger.reconcile(liveScheduleIds = setOf("schedule-A"), liveSharedIds = emptySet())

        assertEquals(1, store.events("schedule-A", null).size)
        assertTrue(store.events("gone", null).isEmpty())
    }

    @Test
    public fun testReconcileCompactsSurvivors(): TestResult = runTest {
        val old = clock.now()
        store.recordEvents(
            listOf(
                LedgerEvent.Triggered(scheduleId = "schedule-A", timestamp = old),
                LedgerEvent.Triggered(
                    scheduleId = "schedule-A",
                    timestamp = old.plus(1, ChronoUnit.DAYS)
                )
            )
        )

        // Both events are now over two years old, so they share a yearly bucket.
        clock.currentTime = old.plus(3 * 365, ChronoUnit.DAYS)

        ledger.reconcile(liveScheduleIds = setOf("schedule-A"), liveSharedIds = emptySet())

        val result = store.events("schedule-A", null)
        assertEquals(1, result.size)
        assertEquals(2, result.first().effectiveCount)
    }

    /** Logs the maintenance calls so their order and arguments can be asserted. */
    private class RecordingStore : LedgerStoreInterface {
        val calls: MutableList<String> = mutableListOf()
        var liveScheduleIds: Set<String>? = null
        var liveSharedIds: Set<String>? = null
        var compactNow: Instant? = null

        override suspend fun recordEvents(events: List<LedgerEvent>) {}

        override suspend fun events(scheduleId: String, sharedId: String?): List<LedgerEvent> =
            emptyList()

        override suspend fun hasEvents(scheduleId: String): Boolean = false

        override suspend fun deleteEvents(scopes: List<LedgerScope>) {}

        override suspend fun retainEvents(
            liveScheduleIds: Set<String>,
            liveSharedIds: Set<String>
        ) {
            calls.add("retain")
            this.liveScheduleIds = liveScheduleIds
            this.liveSharedIds = liveSharedIds
        }

        override suspend fun compact(now: Instant) {
            calls.add("compact")
            compactNow = now
        }
    }
}
