/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.limits.storage.LedgerDatabase
import java.time.Instant
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers [AutomationLedger.recordExecutionIfNoneSince], the guarded record used
 * by interruption recovery.
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
}
