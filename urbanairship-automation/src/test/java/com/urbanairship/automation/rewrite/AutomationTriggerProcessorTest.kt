package com.urbanairship.automation.rewrite

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import com.urbanairship.automation.rewrite.engine.AutomationScheduleState
import com.urbanairship.automation.rewrite.engine.TriggeringInfo
import com.urbanairship.automation.rewrite.engine.triggerprocessor.AutomationTriggerProcessor
import com.urbanairship.automation.rewrite.engine.triggerprocessor.TriggerData
import com.urbanairship.automation.rewrite.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonValue
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
    private val processor = AutomationTriggerProcessor(store, clock)

    @Test
    public fun testRestoreSchedules(): TestResult = runTest {
        store.upsertTriggers(listOf(
            TriggerData(
                scheduleID = "unused-schedule-id",
                triggerID = "unused-trigger-id",
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
            assertEquals(TriggeringInfo(
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
            assertEquals(TriggeringInfo(
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

            processor.processEvent(AutomationEvent.AppInit)

            assertEquals(TriggerData(
                scheduleID = "schedule-id",
                triggerID = "default-trigger",
                triggerCount = 1.0,
            ), store.getTrigger("schedule-id", "default-trigger"))

            processor.cancel(listOf("schedule-id"))

            assertNull(store.getTrigger("schedule-id", "default-trigger"))
            processor.processEvent(AutomationEvent.AppInit)

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
            processor.processEvent(AutomationEvent.AppInit)

            assertEquals(TriggerData(
                scheduleID = "schedule-id",
                triggerID = "trigger-id-2",
                triggerCount = 1.0,
            ), store.getTrigger("schedule-id", "trigger-id-2"))

            processor.cancel("test-group")
            assertNull(store.getTrigger("schedule-id", "trigger-id-2"))
            processor.processEvent(AutomationEvent.AppInit)

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
            processor.processEvent(AutomationEvent.AppInit)
            assertEquals(TriggerData(
                scheduleID = "schedule-id",
                triggerID = "trigger-id-2",
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
            processor.processEvent(AutomationEvent.AppInit)

            awaitItem()

            processor.processEvent(AutomationEvent.AppInit)

            awaitItem()

            processor.setPaused(true)
            processor.processEvent(AutomationEvent.AppInit)

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
            executionCount = 0
        )
    }
}
