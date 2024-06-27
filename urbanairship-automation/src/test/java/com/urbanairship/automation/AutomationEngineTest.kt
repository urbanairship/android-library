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
import com.urbanairship.automation.engine.TriggeringInfo
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
import java.util.UUID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.coVerifySequence
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
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
            created = clock.currentTimeMillis.toULong()
        )

    private val scheduleData: AutomationScheduleData
        get() = AutomationScheduleData(
            schedule = schedule,
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = clock.currentTimeMillis,
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

    private val sleeper = TestTaskSleeper(clock) { sleep ->
        clock.currentTimeMillis += sleep.inWholeMilliseconds
    }

    private val engine: AutomationEngine = AutomationEngine(
        store = store,
        executor = executor,
        preparer = preparer,
        scheduleConditionsChangedNotifier = ScheduleConditionsChangedNotifier(),
        eventsFeed = eventsFeed,
        triggerProcessor = triggerProcessor,
        delayProcessor = delayProcessor,
        clock = clock,
        sleeper = sleeper,
        dispatcher = testDispatcher,
        automationStoreMigrator = automationStoreMigrator
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
                clock.currentTimeMillis
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
            val stopTime = clock.currentTimeMillis
            scheduleData.setSchedule(scheduleData.schedule.copyWith(endDate = stopTime.toULong()))
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
}
