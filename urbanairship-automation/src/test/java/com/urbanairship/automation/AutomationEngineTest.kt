package com.urbanairship.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.TestTaskSleeper
import com.urbanairship.automation.engine.AutomationDelayProcessor
import com.urbanairship.automation.engine.AutomationEngine
import com.urbanairship.automation.engine.AutomationEvent
import com.urbanairship.automation.engine.AutomationEventFeed
import com.urbanairship.automation.engine.AutomationExecutor
import com.urbanairship.automation.engine.AutomationPreparer
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.AutomationStore
import com.urbanairship.automation.engine.EventsHistory
import com.urbanairship.automation.engine.InterruptedBehavior
import com.urbanairship.automation.engine.PreparedSchedule
import com.urbanairship.automation.engine.PreparedScheduleData
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.automation.engine.SchedulePrepareResult
import com.urbanairship.automation.engine.ScheduleExecuteResult
import com.urbanairship.automation.engine.ScheduleReadyResult
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.automation.limits.LedgerConfig
import com.urbanairship.automation.limits.LedgerExecutionResult
import com.urbanairship.automation.limits.TestAutomationLedger
import com.urbanairship.automation.limits.LedgerLimitEvaluator
import com.urbanairship.automation.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.automation.engine.triggerprocessor.TriggerResult
import com.urbanairship.automation.storage.AutomationStoreMigrator
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.util.TaskSleeper
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.Custom
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonValue
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AutomationEngineTest {
    private val clock = TestClock()

    private val testDispatcher = StandardTestDispatcher()
    private val schedule: AutomationSchedule
        get() = AutomationSchedule(
            identifier = "test",
            triggers = listOf(),
            data = AutomationSchedule.ScheduleData.InAppMessageData(
                InAppMessage(
                    name = "test",
                    displayContent = InAppMessageDisplayContent.CustomContent(
                        Custom(JsonValue.wrap("test"))
                    )
                )
            ),
            created = clock.currentTime
        )

    private val scheduleData: AutomationScheduleData
        get() = AutomationScheduleData(
            schedule = schedule,
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = clock.currentTime,
            executionCount = 0,
            triggerSessionId = UUID.randomUUID().toString()
    )

    private val triggerProcessor: AutomationTriggerProcessor = mockk(relaxed = true)

    private val automationStoreMigrator: AutomationStoreMigrator = mockk(relaxUnitFun = true)

    private val store: AutomationStore = mockk(relaxUnitFun = true) {
        coEvery { getSchedules() } answers { listOf(scheduleData) }
        coEvery { upsertSchedules(any(), any()) } answers { listOf(scheduleData) }
    }

    private val executor: AutomationExecutor = mockk(relaxed = true)

    private val preparer: AutomationPreparer = mockk(relaxed = true)

    private val eventsFeed: AutomationEventFeed = mockk(relaxed = true)

    private val delayProcessor: AutomationDelayProcessor = mockk(relaxed = true)

    private val scheduleConditionsChangedNotifier: ScheduleConditionsChangedNotifier = mockk(relaxed = true)

    private val ledger = TestAutomationLedger()

    private val limitEvaluator: LedgerLimitEvaluator = mockk {
        coEvery { isOverLimit(any()) } returns false
    }

    private val sleeper = TestTaskSleeper(clock) { sleep ->
        clock.currentTime += (sleep.inWholeMilliseconds).milliseconds
    }

    private val engine: AutomationEngine = AutomationEngine(
        store = store,
        executor = executor,
        preparer = preparer,
        scheduleConditionsChangedNotifier = scheduleConditionsChangedNotifier,
        eventsFeed = eventsFeed,
        triggerProcessor = triggerProcessor,
        delayProcessor = delayProcessor,
        clock = clock,
        sleeper = sleeper,
        dispatcher = testDispatcher,
        automationStoreMigrator = automationStoreMigrator,
        eventsHistory = EventsHistory(),
        ledger = ledger,
        limitEvaluator = limitEvaluator
    )

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testStartStop(): TestResult = runTest {
        assertFalse(engine.isStarted())

        engine.start()
        assertTrue(engine.isStarted())

        engine.stop()
        assertFalse(engine.isStarted())

        engine.start()
        assertTrue(engine.isStarted())
    }

    @Test
    public fun testStartCollectsTriggerResults(): TestResult = runTest {
        val triggerResult = TriggerResult(
            scheduleId = "test",
            triggerExecutionType = TriggerExecutionType.EXECUTION,
            triggerInfo = TriggeringInfo(
                null,
                clock.currentTime
            )
        )

        every { triggerProcessor.getTriggerResults() }.answers{ flowOf(triggerResult) }
        coEvery { store.getSchedules() } answers { listOf(scheduleData) }
        coEvery { store.updateSchedule(eq("test"), any()) } answers { scheduleData }
        coEvery { store.getSchedule(eq("test")) } answers { scheduleData }

        engine.start()
        advanceUntilIdle()

        coVerifyOrder {
            store.getSchedules()
            triggerProcessor.restoreSchedules(listOf(scheduleData))
            triggerProcessor.getTriggerResults()
            store.updateSchedule(eq("test"), any())
            triggerProcessor.updateScheduleState(eq("test"), any())
        }
    }

    @Test
    public fun testStartCollectsEventFeed(): TestResult = runTest {
        val event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT)
        every { eventsFeed.feed }.answers{ flowOf(event) }
        coEvery { store.getSchedules() } answers { listOf(scheduleData) }

        engine.start()
        advanceUntilIdle()

        coVerifySequence {
            store.getSchedules()
            triggerProcessor.restoreSchedules(listOf(scheduleData))
            triggerProcessor.getTriggerResults()

            triggerProcessor.processEvent(eq(event))
        }
    }

    @Test
    public fun testSetEnginePaused(): TestResult = runTest {
        coEvery { store.updateSchedule(eq("test"), any()) } answers { scheduleData }

        assertFalse(engine.isPaused())

        engine.setEnginePaused(true)
        assertTrue(engine.isPaused())

        engine.setEnginePaused(false)
        assertFalse(engine.isPaused())
    }

    @Test
    public fun testResumeNotifiesScheduleConditionsChanged(): TestResult = runTest {
        engine.setExecutionPaused(true)
        engine.setEnginePaused(true)
        engine.start()
        advanceUntilIdle()

        verify(exactly = 0) {
            scheduleConditionsChangedNotifier.notifyChanged()
        }

        engine.setExecutionPaused(false)
        advanceUntilIdle()

        verify(exactly = 0) {
            scheduleConditionsChangedNotifier.notifyChanged()
        }

        engine.setEnginePaused(false)
        advanceUntilIdle()

        verify(exactly = 1) {
            scheduleConditionsChangedNotifier.notifyChanged()
        }
    }

    @Test
    public fun testSetExecutionPaused(): TestResult = runTest {
        coEvery { store.updateSchedule(eq("test"), any()) } answers { scheduleData }

        assertFalse(engine.isExecutionPaused())

        engine.setExecutionPaused(true)
        assertTrue(engine.isExecutionPaused())

        engine.setExecutionPaused(false)
        assertFalse(engine.isExecutionPaused())
    }

    @Test
    public fun testStopSchedules(): TestResult = runTest {
        coEvery { store.updateSchedule(eq("test"), any()) } answers { scheduleData }
        coEvery { store.upsertSchedules(any(), any()) } answers { listOf(scheduleData) }
        coEvery { store.getSchedule(eq("test")) } answers { scheduleData }

        engine.start()
        advanceUntilIdle()

        coVerify { triggerProcessor.restoreSchedules(listOf(scheduleData)) }
        coVerify { triggerProcessor.getTriggerResults() }

        engine.upsertSchedules(listOf(schedule))
        advanceUntilIdle()

        assertNotNull(engine.getSchedule(schedule.identifier))

        engine.stopSchedules(listOf(schedule.identifier))
        advanceUntilIdle()

        // Mock result of stopSchedules
        coEvery { store.getSchedule(eq("test")) } answers {
            val stopTime = clock.currentTime
            scheduleData.setSchedule(scheduleData.schedule.copyWith(endDate = stopTime))
                .finished(stopTime)
        }

        assertNull(engine.getSchedule(schedule.identifier))
    }

    @Test
    public fun testUpsertSchedules(): TestResult = runTest {
        // Mock initial and post-upsert state
        coEvery { store.getSchedule(eq("test")) } returnsMany listOf(null, scheduleData)

        engine.start()
        advanceUntilIdle()

        coVerify { triggerProcessor.restoreSchedules(listOf(scheduleData)) }
        coVerify { triggerProcessor.getTriggerResults() }
        assertNull(engine.getSchedule(schedule.identifier))

        engine.upsertSchedules(listOf(schedule))
        advanceUntilIdle()

        val updatedMetadata = JsonValue.parseString("""{"foo": "bar"}""")
        val updated = schedule.copyWith(metadata = updatedMetadata)
        engine.upsertSchedules(listOf(updated))

        advanceUntilIdle()
        coVerify {
            store.upsertSchedules(listOf("test"), any())
        }
    }

    @Test
    public fun testCancelSchedule(): TestResult = runTest {
        // Mock initial (post-upsert) and cancelled state
        coEvery { store.getSchedule(eq("test")) } returnsMany listOf(scheduleData, null)
        coEvery { store.upsertSchedules(any(), any()) } returns listOf(scheduleData)

        engine.start()
        advanceUntilIdle()

        coVerify { triggerProcessor.restoreSchedules(listOf(scheduleData)) }
        coVerify { triggerProcessor.getTriggerResults() }

        engine.upsertSchedules(listOf(schedule))
        advanceUntilIdle()

        assertNotNull(engine.getSchedule(schedule.identifier))

        engine.cancelSchedules(listOf(schedule.identifier))
        advanceUntilIdle()

        coVerify { store.deleteSchedules(listOf(schedule.identifier)) }
        coVerify { triggerProcessor.cancel(listOf(schedule.identifier)) }

        assertNull(engine.getSchedule(schedule.identifier))
    }

    private fun ledgerSchedule(
        sharedId: String?,
        identifier: String = "test",
        priority: Int? = null
    ): AutomationSchedule = AutomationSchedule(
        identifier = identifier,
        priority = priority,
        triggers = listOf(),
        data = AutomationSchedule.ScheduleData.InAppMessageData(
            InAppMessage(
                name = "test",
                displayContent = InAppMessageDisplayContent.CustomContent(
                    Custom(JsonValue.wrap("test"))
                )
            )
        ),
        created = clock.currentTime,
        ledgerConfig = sharedId?.let { LedgerConfig(sharedId = it) }
    )

    private fun ledgerScheduleData(
        schedule: AutomationSchedule,
        state: AutomationScheduleState,
        triggerInfo: TriggeringInfo?
    ): AutomationScheduleData = AutomationScheduleData(
        schedule = schedule,
        scheduleState = state,
        scheduleStateChangeDate = clock.currentTime,
        executionCount = 0,
        triggerInfo = triggerInfo,
        triggerSessionId = UUID.randomUUID().toString()
    )

    /**
     * An execution-type trigger result that moves an idle schedule into
     * TRIGGERED must record a `triggered` ledger event, stamped with the
     * schedule's shared ID and the causing trigger's ID.
     */
    @Test
    public fun testRecordsTriggeredLedgerEvent(): TestResult = runTest {
        val triggerInfo = TriggeringInfo(
            context = null,
            date = clock.currentTime,
            triggerId = "trigger-1"
        )
        val sched = ledgerSchedule(sharedId = "group-1")
        val idleData = ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)

        every { triggerProcessor.getTriggerResults() } answers {
            flowOf(
                TriggerResult(
                    scheduleId = "test",
                    triggerExecutionType = TriggerExecutionType.EXECUTION,
                    triggerInfo = triggerInfo
                )
            )
        }
        coEvery { store.getSchedules() } answers { emptyList() }
        // Apply the update block the way the real store does, against a
        // throwaway IDLE schedule so the transition actually happens. `triggered`
        // mutates in place, so it must not be the instance `getSchedule` returns.
        coEvery { store.updateSchedule(eq("test"), any()) } answers {
            secondArg<(AutomationScheduleData) -> AutomationScheduleData>()(
                ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)
            )
        }
        // Return a non-triggered state so the follow-up processing aborts,
        // isolating the record.
        coEvery { store.getSchedule(eq("test")) } answers { idleData }

        engine.start()
        advanceUntilIdle()

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Triggered(
                    scheduleId = "test",
                    sharedId = "group-1",
                    triggerId = "trigger-1"
                )
            ),
            ledger.recorded
        )
    }

    /**
     * A redundant trigger result for a schedule that is already TRIGGERED must
     * not record a `triggered` event: `triggered` no-ops, so this call did not
     * cause the transition.
     */
    @Test
    public fun testNoTriggeredRecordWhenNotTransitioned(): TestResult = runTest {
        val triggerInfo = TriggeringInfo(
            context = null,
            date = clock.currentTime,
            triggerId = "trigger-1"
        )
        val sched = ledgerSchedule(sharedId = "group-1")
        val idleData = ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)

        every { triggerProcessor.getTriggerResults() } answers {
            flowOf(
                TriggerResult(
                    scheduleId = "test",
                    triggerExecutionType = TriggerExecutionType.EXECUTION,
                    triggerInfo = triggerInfo
                )
            )
        }
        coEvery { store.getSchedules() } answers { emptyList() }
        coEvery { store.updateSchedule(eq("test"), any()) } answers {
            secondArg<(AutomationScheduleData) -> AutomationScheduleData>()(
                ledgerScheduleData(sched, AutomationScheduleState.TRIGGERED, triggerInfo)
            )
        }
        coEvery { store.getSchedule(eq("test")) } answers { idleData }

        engine.start()
        advanceUntilIdle()

        assertTrue(ledger.recorded.isEmpty())
    }

    /**
     * An execution that is interrupted and not retried consumed budget, but the
     * executor never got to record it. Restore must record the outcome so the
     * ledger-backed limit still counts it.
     */
    @Test
    public fun testRecordsExecutionForTerminalInterruption(): TestResult = runTest {
        val sched = ledgerSchedule(sharedId = "group-1")
        val executing = ledgerScheduleData(sched, AutomationScheduleState.EXECUTING, triggerInfo = null)
        executing.setPreparedScheduleInfo(
            PreparedScheduleInfo(
                scheduleId = "test",
                triggerSessionId = UUID.randomUUID().toString(),
                priority = 0,
                ledgerSharedId = "group-1",
                triggerId = "trigger-1"
            )
        )

        coEvery { store.getSchedules() } answers { listOf(executing) }
        coEvery { executor.interrupted(any(), any()) } answers { InterruptedBehavior.FINISH }
        coEvery { store.updateSchedule(eq("test"), any()) } answers { executing }

        engine.start()
        advanceUntilIdle()

        // Guarded on the moment the schedule started executing, so an outcome
        // the executor already recorded is not counted twice.
        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.ExecutionIfNoneSince(
                    scheduleId = "test",
                    sharedId = "group-1",
                    triggerId = "trigger-1",
                    result = LedgerExecutionResult.SUCCEEDED,
                    cancel = false,
                    since = executing.scheduleStateChangeDate
                )
            ),
            ledger.recorded
        )
    }

    /**
     * An interruption that will be retried has not consumed budget: the schedule
     * runs again, and that run records its own outcome.
     */
    @Test
    public fun testRecordsNothingForRetriedInterruption(): TestResult = runTest {
        val sched = ledgerSchedule(sharedId = "group-1")
        val executing = ledgerScheduleData(sched, AutomationScheduleState.EXECUTING, triggerInfo = null)
        executing.setPreparedScheduleInfo(
            PreparedScheduleInfo(
                scheduleId = "test",
                triggerSessionId = UUID.randomUUID().toString(),
                priority = 0,
                ledgerSharedId = "group-1",
                triggerId = "trigger-1"
            )
        )

        coEvery { store.getSchedules() } answers { listOf(executing) }
        coEvery { executor.interrupted(any(), any()) } answers { InterruptedBehavior.RETRY }
        coEvery { store.updateSchedule(eq("test"), any()) } answers { executing }
        coEvery { store.getSchedule(eq("test")) } answers { null }

        engine.start()
        advanceUntilIdle()

        assertTrue(ledger.recorded.isEmpty())
    }

    /**
     * Upsert resolves each schedule's over-limit state from the ledger and feeds
     * it into `updateState`, so a schedule that has already spent its budget is
     * finished rather than left idle.
     */
    @Test
    public fun testUpsertFinishesScheduleOverLedgerLimit(): TestResult = runTest {
        val sched = ledgerSchedule(sharedId = "group-1")
        val stored = ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)

        coEvery { store.getSchedules() } answers { emptyList() }
        // Apply the update block the way the real store does.
        coEvery { store.upsertSchedules(any(), any()) } answers {
            listOf(secondArg<(String, AutomationScheduleData?) -> AutomationScheduleData>()("test", stored))
        }

        coEvery { limitEvaluator.isOverLimit(any()) } returns true

        engine.start()
        advanceUntilIdle()
        engine.upsertSchedules(listOf(sched))
        advanceUntilIdle()

        coVerify { limitEvaluator.isOverLimit(match { it.identifier == "test" }) }
        assertEquals(AutomationScheduleState.FINISHED, stored.scheduleState)
    }

    /** The same upsert leaves a schedule with budget left alone. */
    @Test
    public fun testUpsertKeepsScheduleUnderLedgerLimit(): TestResult = runTest {
        val sched = ledgerSchedule(sharedId = "group-1")
        val stored = ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)

        coEvery { store.getSchedules() } answers { emptyList() }
        coEvery { store.upsertSchedules(any(), any()) } answers {
            listOf(secondArg<(String, AutomationScheduleData?) -> AutomationScheduleData>()("test", stored))
        }

        coEvery { limitEvaluator.isOverLimit(any()) } returns false

        engine.start()
        advanceUntilIdle()
        engine.upsertSchedules(listOf(sched))
        advanceUntilIdle()

        assertEquals(AutomationScheduleState.IDLE, stored.scheduleState)
    }

    /**
     * Reconciliation derives the live ledger IDs from the persisted schedules —
     * every schedule ID, plus the shared IDs of the schedules that have one.
     */
    @Test
    public fun testReconcileLedgerDerivesLiveIds(): TestResult = runTest {
        val withGroup = ledgerSchedule(sharedId = "group-1", identifier = "a")
        val withoutGroup = ledgerSchedule(sharedId = null, identifier = "b")
        val otherGroup = ledgerSchedule(sharedId = "group-2", identifier = "c")

        coEvery { store.getSchedules() } answers {
            listOf(withGroup, withoutGroup, otherGroup).map {
                ledgerScheduleData(it, AutomationScheduleState.IDLE, triggerInfo = null)
            }
        }

        engine.start()
        advanceUntilIdle()
        engine.reconcileLedger()
        advanceUntilIdle()

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Reconciled(
                    liveScheduleIds = setOf("a", "b", "c"),
                    liveSharedIds = setOf("group-1", "group-2")
                )
            ),
            ledger.recorded
        )
    }

    @Test
    public fun testReconcileLedgerWithNoSchedulesPassesEmptyLiveIds(): TestResult = runTest {
        coEvery { store.getSchedules() } answers { emptyList() }

        engine.start()
        advanceUntilIdle()
        engine.reconcileLedger()
        advanceUntilIdle()

        assertEquals(
            listOf(
                TestAutomationLedger.Recorded.Reconciled(
                    liveScheduleIds = emptySet(),
                    liveSharedIds = emptySet()
                )
            ),
            ledger.recorded
        )
    }

    /**
     * Whether the ledger reports the schedule's budget as spent. Read by the
     * stubbed limit evaluator, so a test can spend the group part way through a
     * run rather than having to count evaluator calls.
     */
    private var isGroupSpent = false

    /**
     * Drives one schedule from a trigger result all the way to `attemptExecute`.
     *
     * The engine's execute path had no coverage, so this wires the collaborators
     * it actually consults: a store that holds state and applies update blocks,
     * a preparer that hands back a prepared schedule, and an executor that
     * reports ready.
     *
     * @param sharedId The schedule's ledger group, or null for an unpooled one.
     * @param onConditionsMet Runs when the schedule clears its delay conditions,
     * which is the seam between the prepare-time limit check and the
     * execute-time one — the window a sibling would record in.
     */
    private suspend fun TestScope.driveToExecution(
        sharedId: String?,
        onConditionsMet: () -> Unit = {}
    ): AutomationScheduleData {
        val sched = ledgerSchedule(sharedId = sharedId)
        val stored = ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)
        val triggerInfo = TriggeringInfo(
            context = null,
            date = clock.currentTime,
            triggerId = "trigger-1"
        )

        coEvery { store.getSchedules() } answers { listOf(stored) }
        coEvery { store.getSchedule("test") } answers { stored }
        coEvery { store.updateSchedule("test", any()) } answers {
            secondArg<(AutomationScheduleData) -> AutomationScheduleData>()(stored)
        }

        coEvery { limitEvaluator.isOverLimit(any()) } answers { isGroupSpent }

        val prepared = PreparedSchedule(
            info = PreparedScheduleInfo(
                scheduleId = "test",
                triggerSessionId = stored.triggerSessionId,
                ledgerSharedId = sharedId,
                triggerId = "trigger-1"
            ),
            data = PreparedScheduleData.Action(
                JsonValue.wrap("actions")
            ),
            frequencyChecker = null
        )

        coEvery { preparer.prepare(any(), any(), any(), any()) } answers {
            SchedulePrepareResult.Prepared(prepared)
        }

        every { executor.isValid(any()) } returns true
        every { executor.isReady(any()) } returns ScheduleReadyResult.READY
        coEvery { executor.execute(any()) } returns ScheduleExecuteResult.FINISHED

        every { delayProcessor.areConditionsMet(any()) } answers {
            onConditionsMet()
            true
        }

        every { triggerProcessor.getTriggerResults() } answers {
            flowOf(
                TriggerResult(
                    scheduleId = "test",
                    triggerExecutionType = TriggerExecutionType.EXECUTION,
                    triggerInfo = triggerInfo
                )
            )
        }

        engine.start()
        advanceUntilIdle()

        return stored
    }

    /** A pooled schedule with budget left executes. */
    @Test
    public fun testExecutesPooledScheduleWithBudget(): TestResult = runTest {
        isGroupSpent = false

        driveToExecution(sharedId = "group-1")

        coVerify { executor.execute(any()) }
    }

    /**
     * A pooled schedule whose group is spent between prepare and execute must
     * not execute: the tally is shared, so a sibling can spend it while this
     * one waits on its delay conditions.
     */
    @Test
    public fun testSkipsPooledScheduleWhenGroupSpentBeforeExecuting(): TestResult = runTest {
        isGroupSpent = false

        val stored = driveToExecution(sharedId = "group-1") {
            // A sibling in the group recorded while this schedule waited.
            isGroupSpent = true
        }

        coVerify(exactly = 0) { executor.execute(any()) }
        // `isReady` is where the frequency constraint is charged, so a dropped
        // attempt must not reach it either.
        verify(exactly = 0) { executor.isReady(any()) }
        coVerify { preparer.cancelled(any()) }
        assertEquals(AutomationScheduleState.FINISHED, stored.scheduleState)
    }

    /**
     * Drives two schedules that pool one budget, holding the first inside
     * `execute` so the second has to contend for the group.
     *
     * @param onFirstRecorded Runs when the first schedule's execution finishes,
     * standing in for the ledger write it would have made.
     * @return what each schedule did, keyed by schedule ID.
     */
    private suspend fun TestScope.driveTwoPooledSchedules(
        firstIsHeld: CompletableDeferred<Unit>,
        onFirstRecorded: () -> Unit
    ): MutableList<String> {
        val log = mutableListOf<String>()

        // Priority orders the pending-execution drain, so "first" is dispatched
        // ahead of "second" instead of the set's arbitrary order deciding.
        val first = ledgerSchedule(sharedId = "group-1", identifier = "first", priority = -1)
        val second = ledgerSchedule(sharedId = "group-1", identifier = "second")

        val storedById = listOf(first, second).associate { sched ->
            sched.identifier to
                    ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)
        }

        coEvery { store.getSchedules() } answers { storedById.values.toList() }
        coEvery { store.getSchedule(any()) } answers { storedById[firstArg()] }
        coEvery { store.updateSchedule(any(), any()) } answers {
            val stored = storedById[firstArg<String>()] ?: return@answers null
            secondArg<(AutomationScheduleData) -> AutomationScheduleData>()(stored)
        }

        coEvery { limitEvaluator.isOverLimit(any()) } answers { isGroupSpent }

        coEvery { preparer.prepare(any(), any(), any(), any()) } answers {
            val sched = firstArg<AutomationSchedule>()
            SchedulePrepareResult.Prepared(
                PreparedSchedule(
                    info = PreparedScheduleInfo(
                        scheduleId = sched.identifier,
                        triggerSessionId = requireNotNull(storedById[sched.identifier])
                            .triggerSessionId,
                        ledgerSharedId = "group-1",
                        triggerId = "trigger-1"
                    ),
                    data = PreparedScheduleData.Action(JsonValue.wrap("actions")),
                    frequencyChecker = null
                )
            )
        }

        every { executor.isValid(any()) } returns true
        every { executor.isReady(any()) } returns ScheduleReadyResult.READY
        coEvery { executor.execute(any()) } coAnswers {
            val id = firstArg<PreparedSchedule>().info.scheduleId
            log.add("execute:$id")
            if (id == "first") {
                firstIsHeld.await()
                // The executor records its outcome before returning, so the
                // ledger can answer for it from here on.
                onFirstRecorded()
            }
            ScheduleExecuteResult.FINISHED
        }

        every { delayProcessor.areConditionsMet(any()) } returns true

        every { triggerProcessor.getTriggerResults() } answers {
            flowOf(
                TriggerResult(
                    scheduleId = "first",
                    triggerExecutionType = TriggerExecutionType.EXECUTION,
                    triggerInfo = TriggeringInfo(null, clock.currentTime, "trigger-1")
                ),
                TriggerResult(
                    scheduleId = "second",
                    triggerExecutionType = TriggerExecutionType.EXECUTION,
                    triggerInfo = TriggeringInfo(null, clock.currentTime, "trigger-1")
                )
            )
        }

        engine.start()
        advanceUntilIdle()

        return log
    }

    /**
     * A sibling must not execute while another schedule in its group still is:
     * the holder's event is not written until its execution ends, so a sibling
     * reading the tally mid-flight would read a stale one and execute too.
     */
    @Test
    public fun testPooledSiblingWaitsForInFlightExecution(): TestResult = runTest {
        isGroupSpent = false
        val releaseFirst = CompletableDeferred<Unit>()

        val log = driveTwoPooledSchedules(releaseFirst) { isGroupSpent = true }

        // The first is parked inside execute. The second must be queued behind
        // the group rather than running against a tally that cannot see it.
        assertEquals(listOf("execute:first"), log)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        // The first spent the budget, so the second is dropped on the re-check
        // it does after taking the group.
        assertEquals(listOf("execute:first"), log)
    }

    /**
     * The sibling waits rather than being dropped up front: a holder can fail
     * without spending anything, and then the waiter should still get to run.
     */
    @Test
    public fun testPooledSiblingRunsWhenHolderSpendsNothing(): TestResult = runTest {
        isGroupSpent = false
        val releaseFirst = CompletableDeferred<Unit>()

        // The holder records nothing, so the budget is still there on wake.
        val log = driveTwoPooledSchedules(releaseFirst) { }

        assertEquals(listOf("execute:first"), log)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("execute:first", "execute:second"), log)
    }

    /**
     * An unpooled schedule is unaffected: nothing but its own execution can add
     * to its tally, and that cannot happen while it sits prepared.
     */
    @Test
    public fun testExecutesUnpooledScheduleWithoutRecheck(): TestResult = runTest {
        isGroupSpent = false

        driveToExecution(sharedId = null) {
            // Even if the ledger changed its answer, an unpooled schedule has
            // no sibling that could have spent anything.
            isGroupSpent = true
        }

        coVerify { executor.execute(any()) }
    }
}
