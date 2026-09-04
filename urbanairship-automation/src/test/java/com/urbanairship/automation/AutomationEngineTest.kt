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
import com.urbanairship.automation.engine.PreparedScheduleInfo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
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

    private fun ledgerSchedule(sharedId: String?): AutomationSchedule = AutomationSchedule(
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
}
