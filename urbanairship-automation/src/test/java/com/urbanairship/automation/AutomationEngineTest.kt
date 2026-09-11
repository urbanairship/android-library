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
import com.urbanairship.automation.limits.LedgerGroupReservations
import com.urbanairship.automation.limits.LedgerLimitEvaluator
import com.urbanairship.automation.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.automation.engine.triggerprocessor.TriggerResult
import com.urbanairship.automation.storage.AutomationStoreMigrator
import com.urbanairship.automation.utils.ScheduleConditionsChangedNotifier
import com.urbanairship.util.TaskSleeper
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.PreparedInAppMessageData
import com.urbanairship.iam.content.AirshipLayout
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

    /**
     * Exposed rather than left to the engine's own default, so a test can
     * directly observe whether a schedule is holding a ledger group's
     * reservation or only its in-flight mark.
     */
    private val groupReservations = LedgerGroupReservations()

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
        limitEvaluator = limitEvaluator,
        groupReservations = groupReservations
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
        onFirstRecordedRestoreSecond: ((MutableMap<String, AutomationScheduleData>) -> Unit)? = null,
        onFirstRecorded: () -> Unit
    ): MutableList<String> {
        val log = mutableListOf<String>()

        // Priority orders the pending-execution drain, so "first" is dispatched
        // ahead of "second" instead of the set's arbitrary order deciding.
        val first = ledgerSchedule(sharedId = "group-1", identifier = "first", priority = -1)
        val second = ledgerSchedule(sharedId = "group-1", identifier = "second")

        // Mutable, so a test can model remote data replacing a stored schedule
        // while it waits. The store hands back whatever is in here, which is a
        // different instance from the snapshot the attempt was prepared with -
        // as it is in production, where each read loads afresh.
        val storedById = listOf(first, second).associate { sched ->
            sched.identifier to
                    ledgerScheduleData(sched, AutomationScheduleState.IDLE, triggerInfo = null)
        }.toMutableMap()
        storedForTest = storedById

        coEvery { store.getSchedules() } answers { storedById.values.toList() }
        coEvery { store.getSchedule(any()) } answers { storedById[firstArg()] }
        coEvery { store.updateSchedule(any(), any()) } answers {
            val stored = storedById[firstArg<String>()] ?: return@answers null
            secondArg<(AutomationScheduleData) -> AutomationScheduleData>()(stored)
        }

        coEvery { limitEvaluator.isOverLimit(any()) } answers { isGroupSpent }

        coEvery { preparer.prepare(any(), any(), any(), any()) } answers {
            val sched = firstArg<AutomationSchedule>()
            log.add("prepare:${sched.identifier}")
            SchedulePrepareResult.Prepared(
                PreparedSchedule(
                    info = PreparedScheduleInfo(
                        scheduleId = sched.identifier,
                        triggerSessionId = requireNotNull(storedById[sched.identifier])
                            .triggerSessionId,
                        ledgerSharedId = "group-1",
                        triggerId = "trigger-1"
                    ),
                    data = when (sched.identifier) {
                        in embeddedSchedules -> PreparedScheduleData.InAppMessage(embeddedMessageData())
                        in bannerSchedules -> PreparedScheduleData.InAppMessage(bannerMessageData())
                        else -> PreparedScheduleData.Action(JsonValue.wrap("actions"))
                    },
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
                onFirstRecordedRestoreSecond?.invoke(storedById)
            }
            ScheduleExecuteResult.FINISHED
        }

        every { delayProcessor.areConditionsMet(any()) } answers { areConditionsMet }

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
        assertEquals(listOf("prepare:first", "prepare:second", "execute:first"), log)

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        // The first spent the budget, so the second is dropped on the re-check
        // it does after taking the group.
        assertFalse(log.contains("execute:second"))
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

        assertFalse(log.contains("execute:second"))

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertTrue(log.contains("execute:second"))
    }

    /**
     * Schedules prepared as embedded messages, which cannot hold their ledger
     * group across their own display.
     */
    private val embeddedSchedules = mutableSetOf<String>()

    /**
     * Schedules prepared as banner messages, which — like embedded ones —
     * cannot hold their ledger group across their own display.
     */
    private val bannerSchedules = mutableSetOf<String>()

    /**
     * Whether the delay conditions currently hold. Read by the stubbed delay
     * processor, so a test can have the user leave the gated screen while a
     * sibling waits on its group.
     */
    private var areConditionsMet = true

    /** The schedules the two-sibling harness stored, for asserting final state. */
    private var storedForTest: MutableMap<String, AutomationScheduleData> = mutableMapOf()

    /** A prepared embedded message, so `reservesLedgerGroup` is false for it. */
    private fun embeddedMessageData(): PreparedInAppMessageData {
        val layout = """
            {
              "layout": {
                "version": 1,
                "presentation": {
                  "type": "embedded",
                  "embedded_id": "home_banner",
                  "default_placement": { "size": { "width": "50%", "height": "50%" } }
                },
                "view": { "type": "container", "items": [] }
              }
            }
        """.trimIndent()

        return PreparedInAppMessageData(
            message = InAppMessage(
                name = "embedded",
                displayContent = InAppMessageDisplayContent.AirshipLayoutContent(
                    AirshipLayout.fromJson(JsonValue.parseString(layout))
                )
            ),
            displayAdapter = mockk(relaxed = true),
            displayCoordinator = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
            actionRunner = mockk(relaxed = true)
        )
    }

    /** A prepared banner message, so `reservesLedgerGroup` is false for it. */
    private fun bannerMessageData(): PreparedInAppMessageData {
        val layout = """
            {
              "layout": {
                "version": 1,
                "presentation": {
                  "type": "banner",
                  "default_placement": {
                    "size": { "width": "100%", "height": "25%" },
                    "position": { "horizontal": "center", "vertical": "bottom" }
                  }
                },
                "view": { "type": "container", "items": [] }
              }
            }
        """.trimIndent()

        return PreparedInAppMessageData(
            message = InAppMessage(
                name = "banner",
                displayContent = InAppMessageDisplayContent.AirshipLayoutContent(
                    AirshipLayout.fromJson(JsonValue.parseString(layout))
                )
            ),
            displayAdapter = mockk(relaxed = true),
            displayCoordinator = mockk(relaxed = true),
            analytics = mockk(relaxed = true),
            actionRunner = mockk(relaxed = true)
        )
    }

    /**
     * A banner is queued for a host the same way an embedded message is — it
     * can sit unshown indefinitely if the app never surfaces a banner host —
     * so it must not reserve its group either. Checked directly against
     * [groupReservations]: mid-display, a banner must hold only its in-flight
     * mark, never the reservation itself, which an action or a non-banner
     * message would hold instead.
     */
    @Test
    public fun testBannerDoesNotReserveGroup(): TestResult = runTest {
        isGroupSpent = false
        bannerSchedules.add("first")
        val releaseFirst = CompletableDeferred<Unit>()

        val log = driveTwoPooledSchedules(releaseFirst) { isGroupSpent = true }

        assertTrue(log.contains("execute:first"))
        assertFalse(groupReservations.isReserved("group-1"))
        assertEquals(1, groupReservations.inFlight("group-1"))

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        // Woken once the mark cleared, and the banner spent the budget by then.
        assertFalse(log.contains("execute:second"))
        assertEquals(
            AutomationScheduleState.FINISHED,
            requireNotNull(storedForTest["second"]).scheduleState
        )
    }

    /**
     * A cancelled execution must still drop its in-flight mark. Nothing times
     * the mark out, so leaking one wedges the whole group: every sibling would
     * suspend on `awaitInFlightClear` for the life of the process.
     */
    @Test
    public fun testInFlightMarkIsClearedWhenCancelledMidDisplay(): TestResult = runTest {
        isGroupSpent = false
        embeddedSchedules.add("first")
        val releaseFirst = CompletableDeferred<Unit>()

        driveTwoPooledSchedules(releaseFirst) { isGroupSpent = true }

        // "first" is parked mid-display holding only its in-flight mark.
        assertEquals(1, groupReservations.inFlight("group-1"))

        engine.stop()
        advanceUntilIdle()

        assertEquals(0, groupReservations.inFlight("group-1"))
    }

    /**
     * The contrast case: an action holds the reservation itself for its whole
     * execution, unlike the banner above.
     */
    @Test
    public fun testActionReservesGroup(): TestResult = runTest {
        isGroupSpent = false
        val releaseFirst = CompletableDeferred<Unit>()

        driveTwoPooledSchedules(releaseFirst) { isGroupSpent = true }

        assertTrue(groupReservations.isReserved("group-1"))
        assertEquals(0, groupReservations.inFlight("group-1"))

        releaseFirst.complete(Unit)
        advanceUntilIdle()
    }

    /**
     * An embedded sibling cannot hold its group - it is live in a host view
     * indefinitely - but it must still wait for whoever does, so a non-embedded
     * sibling mid-display gets to record before it reads the tally.
     */
    @Test
    public fun testEmbeddedPooledSiblingWaitsForInFlightExecution(): TestResult = runTest {
        isGroupSpent = false
        embeddedSchedules.add("second")
        val releaseFirst = CompletableDeferred<Unit>()

        val log = driveTwoPooledSchedules(releaseFirst) { isGroupSpent = true }

        // Parked behind the holder despite reserving nothing itself.
        assertFalse(log.contains("execute:second"))

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        // The holder spent the budget, so the embedded sibling is dropped too.
        assertFalse(log.contains("execute:second"))
    }

    /**
     * A schedule that holds its group must still notice a sibling that cannot:
     * an embedded message mid-display has spent the budget without recording
     * it, so the reserving sibling has nothing in the ledger to read until it
     * waits the in-flight mark out.
     */
    @Test
    public fun testReservingSiblingWaitsThenIsSkippedWhenInFlightSpends(): TestResult = runTest {
        isGroupSpent = false
        // The holder is the embedded one, so it registers in flight and releases
        // the group instead of keeping it for its display.
        embeddedSchedules.add("first")
        val releaseFirst = CompletableDeferred<Unit>()

        val log = driveTwoPooledSchedules(releaseFirst) { isGroupSpent = true }

        assertTrue(log.contains("execute:first"))
        // Parked waiting on the in-flight mark, not settled — the ledger still
        // reads under the limit at this point.
        assertFalse(log.contains("execute:second"))

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        // Woken once the mark cleared, and the limit it retried against is now
        // spent, so it is skipped rather than executed.
        assertFalse(log.contains("execute:second"))
        assertEquals(
            AutomationScheduleState.FINISHED,
            requireNotNull(storedForTest["second"]).scheduleState
        )
    }

    /**
     * Waiting rather than being dropped up front is the whole point: an
     * in-flight sibling can resolve without spending anything, and the waiter
     * has to get an actual turn once it does — a fresh trigger cannot be the
     * only way back in, since the schedule never lost its own eligibility.
     */
    @Test
    public fun testReservingSiblingRunsWhenInFlightSiblingSpendsNothing(): TestResult = runTest {
        isGroupSpent = false
        embeddedSchedules.add("first")
        val releaseFirst = CompletableDeferred<Unit>()

        // The holder records nothing (no `isGroupSpent = true`), so the budget
        // is still there once the sibling wakes.
        val log = driveTwoPooledSchedules(releaseFirst) { }

        assertFalse(log.contains("execute:second"))

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertTrue(log.contains("execute:second"))
    }

    /**
     * The delay conditions are checked once in the drain, before the attempt is
     * dispatched. Waiting on a group makes that gap unbounded, so they have to
     * be re-checked — otherwise a schedule gated to a screen displays after the
     * user has left it.
     */
    @Test
    public fun testPooledSiblingWaitsForConditionsAfterWaitingOnGroup(): TestResult = runTest {
        isGroupSpent = false
        areConditionsMet = true
        val releaseFirst = CompletableDeferred<Unit>()

        val log = driveTwoPooledSchedules(releaseFirst) {
            // The holder displayed for a while and the user moved on, so the
            // conditions the sibling was dispatched under no longer hold.
            areConditionsMet = false
        }

        releaseFirst.complete(Unit)
        advanceUntilIdle()

        // The budget is untouched, so only the conditions can hold it back.
        assertFalse(log.contains("execute:second"))
    }

    /**
     * A definition that changed while the sibling waited must send it back to be
     * reprocessed, not finish it.
     *
     * The snapshot it waited with can name a group it has since left, so the
     * budget must not be judged on it — the re-prepare evaluates the group the
     * schedule is actually in now.
     */
    @Test
    public fun testPooledSiblingIsReprocessedWhenDefinitionChangedWhileWaiting(): TestResult =
        runTest {
            isGroupSpent = false
            val releaseFirst = CompletableDeferred<Unit>()

            val log = driveTwoPooledSchedules(
                firstIsHeld = releaseFirst,
                onFirstRecordedRestoreSecond = { stored ->
                    // Remote data moved the sibling to another group while it was
                    // queued. The store now hands back the new definition, while
                    // the waiting attempt still holds the old snapshot.
                    stored["second"] = ledgerScheduleData(
                        ledgerSchedule(sharedId = "group-2", identifier = "second"),
                        AutomationScheduleState.PREPARED,
                        triggerInfo = null
                    )
                }
            ) {
                isGroupSpent = true
            }

            releaseFirst.complete(Unit)
            advanceUntilIdle()

            // Prepared a second time rather than finished off the stale group.
            assertEquals(2, log.count { it == "prepare:second" })
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
