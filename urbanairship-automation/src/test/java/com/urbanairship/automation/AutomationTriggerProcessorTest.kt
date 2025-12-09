package com.urbanairship.automation

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import com.urbanairship.automation.engine.AutomationEvent
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationStore
import com.urbanairship.automation.engine.TriggerStoreInterface
import com.urbanairship.automation.engine.TriggerableState
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.EventsHistory
import com.urbanairship.automation.engine.TriggeringInfo
import com.urbanairship.automation.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.engine.triggerprocessor.TriggerData
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonValue
import java.util.UUID
import app.cash.turbine.test
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class AutomationTriggerProcessorTest: BaseTestCase() {
    private val store: TriggerStoreInterface = AutomationStore.createInMemoryDatabase(ApplicationProvider.getApplicationContext())
    private val clock = TestClock()

    private val eventsHistory = EventsHistory(clock)
    private val processor = AutomationTriggerProcessor(store, eventsHistory, clock)

    @Test
    public fun testRestoreSchedules(): TestResult = runTest {
        store.upsertTriggers(listOf(
            TriggerData(
                scheduleId = "unused-schedule-id",
                triggerId = "unused-trigger-id",
                triggerCount = 0.0,
            )
        ))

        val trigger = AutomationTrigger.Event(
            EventAutomationTrigger(
                id = "trigger-id",
                type = EventAutomationTriggerType.ACTIVE_SESSION,
                goal = 1.0,
                predicate = null
            )
        )

        assertNotNull(store.getTrigger("unused-schedule-id", "unused-trigger-id"))
        restoreSchedules(trigger)
        assertNull(store.getTrigger("unused-schedule-id", "unused-trigger-id"))

        processor.getTriggerResults().test {
            processor.processEvent(AutomationEvent.StateChanged(TriggerableState("foreground")))

            val result = awaitItem()

            assertEquals("schedule-id", result.scheduleId)
            assertEquals(TriggerExecutionType.EXECUTION, result.triggerExecutionType)
            assertEquals(
                TriggeringInfo(
                    context = DeferredTriggerContext(
                        type = "active_session",
                        goal = 1.0,
                        event = JsonValue.NULL),
                    date = clock.currentTimeMillis()), result.triggerInfo)
        }
    }

    @Test
    public fun testUpdateTriggersResendsStatus(): TestResult = runTest {
        val trigger = AutomationTrigger.Event(
            EventAutomationTrigger(
                id = "trigger-id",
                type = EventAutomationTriggerType.ACTIVE_SESSION,
                goal = 1.0,
                predicate = null
            )
        )

        processor.getTriggerResults().test {
            processor.processEvent(AutomationEvent.StateChanged(TriggerableState()))

            restoreSchedules(trigger)

            processor.updateScheduleState("schedule-id", AutomationScheduleState.PAUSED)

            processor.processEvent(AutomationEvent.StateChanged(TriggerableState("foreground")))
            processor.updateScheduleState("schedule-id", AutomationScheduleState.IDLE)

            val result = awaitItem()
            assertEquals("schedule-id", result.scheduleId)
            assertEquals(TriggerExecutionType.EXECUTION, result.triggerExecutionType)
            assertEquals(
                TriggeringInfo(
                    context = DeferredTriggerContext(
                        type = "active_session",
                        goal = 1.0,
                        event = JsonValue.NULL),
                    date = clock.currentTimeMillis()), result.triggerInfo)
        }
    }

    @Test
    public fun testCancelSchedule(): TestResult = runTest {
        processor.getTriggerResults().test {
            restoreSchedules()

            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

            assertEquals(
                TriggerData(
                    scheduleId = "schedule-id",
                    triggerId = "default-trigger",
                    triggerCount = 1.0,
                ), store.getTrigger("schedule-id", "default-trigger"))

            processor.cancel(listOf("schedule-id"))

            assertNull(store.getTrigger("schedule-id", "default-trigger"))
            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

            expectNoEvents()
        }
    }

    @Test
    public fun testCancelWithGroup(): TestResult = runTest {
        val trigger = AutomationTrigger.Event(
            EventAutomationTrigger(
                id = "trigger-id-2",
                type = EventAutomationTriggerType.APP_INIT,
                goal = 2.0,
                predicate = null
            )
        )

        val schedule = defaultSchedule(
            trigger = trigger,
            group = "test-group"
        )

        processor.getTriggerResults().test {
            processor.restoreSchedules(listOf(schedule))
            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

            assertEquals(
                TriggerData(
                    scheduleId = "schedule-id",
                    triggerId = "trigger-id-2",
                    triggerCount = 1.0,
                ), store.getTrigger("schedule-id", "trigger-id-2"))

            processor.cancel("test-group")
            assertNull(store.getTrigger("schedule-id", "trigger-id-2"))
            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

            expectNoEvents()
        }
    }

    @Test
    public fun testProcessEventEmitsResults(): TestResult = runTest {
        val trigger = AutomationTrigger.Event(
            EventAutomationTrigger(
                id = "trigger-id-2",
                type = EventAutomationTriggerType.APP_INIT,
                goal = 1.0,
                predicate = null
            )
        )

        processor.getTriggerResults().test {
            restoreSchedules(trigger)
            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
            assertEquals(
                TriggerData(
                    scheduleId = "schedule-id",
                    triggerId = "trigger-id-2",
                    triggerCount = 0.0,
                ), store.getTrigger("schedule-id", "trigger-id-2"))

            awaitItem()
        }
    }

    @Test
    public fun testProcessEventEmitsNothingOnPause(): TestResult = runTest {
        val trigger = AutomationTrigger.Event(
            EventAutomationTrigger(
                id = "trigger-id",
                type = EventAutomationTriggerType.APP_INIT,
                goal = 1.0,
                predicate = null
            )
        )

        processor.getTriggerResults().test {
            restoreSchedules(trigger)
            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

            awaitItem()

            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

            awaitItem()

            processor.setPaused(true)
            processor.processEvent(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

            expectNoEvents()
        }
    }

    @Test
    public fun testReplayEvents(): TestResult = runTest {
        val triggerOld = AutomationTrigger.Event(
            EventAutomationTrigger(
                id = "trigger-id",
                type = EventAutomationTriggerType.APP_INIT,
                goal = 2.0,
                predicate = null
            )
        )

        val schedule = defaultSchedule(triggerOld)
        val event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT)

        processor.getTriggerResults().test {
            processor.updateSchedules(listOf(schedule))
            processor.processEvent(event)
            eventsHistory.add(event)

            expectNoEvents()

            val trigger = AutomationTrigger.Event(
                EventAutomationTrigger(
                    id = "new-trigger-id",
                    type = EventAutomationTriggerType.APP_INIT,
                    goal = 1.0,
                    predicate = null
                )
            )

            val newSchedule = AutomationScheduleData(
                schedule = AutomationSchedule(
                    identifier = "new-schedule-id",
                    data = AutomationSchedule.ScheduleData.Actions(actions = JsonValue.NULL),
                    triggers = listOf(trigger),
                    group = null,
                    created = 0U
                ),
                scheduleState = AutomationScheduleState.IDLE,
                scheduleStateChangeDate = clock.currentTimeMillis(),
                executionCount = 0,
                triggerSessionId = UUID.randomUUID().toString()
            )

            processor.updateSchedules(listOf(schedule, newSchedule))
            val result = awaitItem()

            assertEquals("new-schedule-id", result.scheduleId)
            assertEquals(TriggerExecutionType.EXECUTION, result.triggerExecutionType)

            expectNoEvents()
        }
    }

    private suspend fun restoreSchedules(trigger: AutomationTrigger? = null) {
        val currentTrigger: AutomationTrigger
        if (trigger != null) {
            currentTrigger = trigger
        } else {
            currentTrigger = AutomationTrigger.Event(
                EventAutomationTrigger(
                    id = "default-trigger",
                    type = EventAutomationTriggerType.APP_INIT,
                    goal = 2.0,
                    predicate = null
                )
            )
        }

        val schedule = defaultSchedule(currentTrigger)
        processor.restoreSchedules(listOf(schedule))
    }

    private fun defaultSchedule(trigger: AutomationTrigger, group: String? = null): AutomationScheduleData {
        return AutomationScheduleData(
            schedule = AutomationSchedule(
                identifier = "schedule-id",
                data = AutomationSchedule.ScheduleData.Actions(actions = JsonValue.NULL),
                triggers = listOf(trigger),
                group = group,
                created = 0U
            ),
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = clock.currentTimeMillis(),
            executionCount = 0,
            triggerSessionId = UUID.randomUUID().toString()
        )
    }
}
