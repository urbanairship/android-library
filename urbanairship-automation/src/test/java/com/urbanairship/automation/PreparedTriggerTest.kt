package com.urbanairship.automation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.automation.engine.AutomationEvent
import com.urbanairship.automation.engine.TriggerableState
import com.urbanairship.automation.engine.triggerprocessor.PreparedTrigger
import com.urbanairship.automation.engine.triggerprocessor.TriggerData
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.deferred.DeferredTriggerContext
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class PreparedTriggerTest: BaseTestCase() {
    private val clock = TestClock()

    @Test
    public fun testScheduleDatesUpdate(): TestResult = runTest {
        val trigger = EventAutomationTrigger(
            type = EventAutomationTriggerType.APP_INIT,
            goal = 1.0
        )

        val instance = makeTrigger(AutomationTrigger.Event(trigger))
        assertNull(instance.startDate)
        assertNull(instance.endDate)
        assertEquals(0, instance.priority)

        trigger.goal = 3.0

        instance.update(trigger = AutomationTrigger.Event(trigger),
            startDate = clock.currentTimeMillis().toULong(),
            endDate = clock.currentTimeMillis().toULong(),
            priority = 3)
        assertEquals(clock.currentTimeMillis().toULong(), instance.startDate)
        assertEquals(clock.currentTimeMillis().toULong(), instance.endDate)
        assertEquals(3, instance.priority)
        assertEquals(AutomationTrigger.Event(trigger), instance.trigger)
    }

    @Test
    public fun testActivateTrigger(): TestResult = runTest  {
        val initialState = TriggerData(
            scheduleId = "test",
            triggerId = "trigger-id",
            triggerCount = 1.0,
        )

        val execution = makeTrigger(type = TriggerExecutionType.EXECUTION, state = initialState)
        assertFalse(execution.isActive)
        execution.activate()
        assertTrue(execution.isActive)
        assertEquals(initialState, execution.triggerData)

        val cancellation = makeTrigger(type = TriggerExecutionType.DELAY_CANCELLATION, state = initialState)
        assertFalse(cancellation.isActive)
        cancellation.activate()
        assertTrue(cancellation.isActive)
        assertEquals(0.0, cancellation.triggerData.count)
    }

    @Test
    public fun testDisable(): TestResult = runTest {
        val instance = makeTrigger()
        assertFalse(instance.isActive)
        instance.activate()
        assertTrue(instance.isActive)
        instance.disable()
        assertFalse(instance.isActive)
    }

    @Test
    public fun testProcessEventHappyPath(): TestResult = runTest {
        clock.currentTimeMillis = 1
        val trigger = EventAutomationTrigger(type = EventAutomationTriggerType.APP_INIT, goal = 2.0)
        val instance = makeTrigger(trigger = AutomationTrigger.Event(trigger), type = TriggerExecutionType.EXECUTION)
        instance.activate()

        assertEquals(0.0, instance.triggerData.count)
        var result = instance.process(event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(1.0, result?.triggerData?.count)
        assertNull(result?.triggerResult)

        result = instance.process(event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(0.0, result?.triggerData?.count)

        val report = result?.triggerResult
        assertEquals("test-schedule", report?.scheduleId)
        assertEquals(TriggerExecutionType.EXECUTION, report?.triggerExecutionType)
        assertEquals(DeferredTriggerContext(type = "app_init", goal = 2.0, event = JsonValue.NULL),
            report?.triggerInfo?.context)
        assertEquals(clock.currentTimeMillis(), report?.triggerInfo?.date)
    }

    @Test
    public fun testProcessEventDoesNothing(): TestResult = runTest {
        clock.currentTimeMillis = 1
        val trigger = EventAutomationTrigger(type = EventAutomationTriggerType.APP_INIT, goal = 1.0)
        val instance = makeTrigger(trigger = AutomationTrigger.Event(trigger))
        assertNull(instance.process(event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT)))

        instance.activate()
        instance.update(
            trigger = AutomationTrigger.Event(trigger),
            startDate = clock.currentTimeMillis().toULong().plus(1u),
            endDate = null,
            priority = 0
        )
        assertNull(instance.process(event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT)))

        instance.update(
            trigger = AutomationTrigger.Event(trigger),
            startDate = null,
            endDate = null,
            priority = 0
        )
        assertNotNull(instance.process(event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT)))
    }

    @Test
    public fun testProcessEventDoesNothingForInvalidEventType(): TestResult = runTest {
        val trigger = EventAutomationTrigger(type = EventAutomationTriggerType.BACKGROUND, 1.0)
        val instance = makeTrigger(trigger = AutomationTrigger.Event(trigger))
        instance.activate()

        assertNull(instance.process(event = AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND)))
        assertNotNull(instance.process(event = AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND)))
    }

    @Test
    public fun testEventProcessingTypes(): TestResult = runTest {
        EventAutomationTriggerType.entries.forEach {
            val event = AutomationEvent.Event(it, JsonValue.NULL)
            if (!event.isStateEvent) {
                assertEquals(1.0, check(it, event)?.count)
            }
        }
    }

    @Test
    public fun testEventStateProcessingTypes(): TestResult = runTest {
        assertNull(check(EventAutomationTriggerType.VERSION, AutomationEvent.StateChanged(state = TriggerableState())))
        assertEquals(1.0,
            check(
                EventAutomationTriggerType.VERSION,
                AutomationEvent.StateChanged(state = TriggerableState(versionUpdated = "123")))?.count)

        assertNull(check(EventAutomationTriggerType.ACTIVE_SESSION, AutomationEvent.StateChanged(state = TriggerableState())))
        assertEquals(1.0,
            check(
                EventAutomationTriggerType.ACTIVE_SESSION,
                AutomationEvent.StateChanged(state = TriggerableState(appSessionID = "session-id")))?.count)

        val instance = makeTrigger()
        instance.activate()

        val state = TriggerableState(appSessionID = "session-id", versionUpdated = "123")
        instance.process(event = AutomationEvent.StateChanged(state = state))
    }

    @Test
    public fun testCompoundAndTrigger(): TestResult = runTest(timeout = Duration.INFINITE) {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "compound",
                type = CompoundAutomationTriggerType.AND,
                goal = 2.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                    trigger = AutomationTrigger.Event(
                        EventAutomationTrigger(
                            type = EventAutomationTriggerType.FOREGROUND,
                            goal = 1.0,
                            id = "foreground",
                            predicate = null
                        )
                    ),
                    isSticky = null,
                    resetOnIncrement = false
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.APP_INIT,
                                goal = 1.0,
                                id = "init",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = false
                    ),
                    )
            )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND))
        assertNull(state?.triggerData)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        var foreground = state?.triggerData?.children?.get("foreground")
        assertEquals(1.0, foreground?.count)

        var appInit = state?.triggerData?.children?.get("init")
        assertEquals(0.0, appInit?.count)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertEquals(1.0, state?.triggerData?.count)

        foreground = state?.triggerData?.children?.get("foreground")
        assertEquals(0.0, foreground?.count)
        appInit = state?.triggerData?.children?.get("init")
        assertEquals(0.0, appInit?.count)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNotNull(state?.triggerResult)
    }

    @Test
    public fun testCompoundAndComplexTrigger(): TestResult = runTest {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "compound",
                type = CompoundAutomationTriggerType.AND,
                goal = 2.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.FOREGROUND,
                                goal = 1.0,
                                id = "foreground",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = true
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.APP_INIT,
                                goal = 1.0,
                                id = "init",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = true
                    ),
                )
            )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND))
        assertNull(state?.triggerData)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        var foreground = state?.triggerData?.children?.get("foreground")
        assertEquals(1.0, foreground?.count)

        var appInit = state?.triggerData?.children?.get("init")
        assertEquals(0.0, appInit?.count)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertEquals(1.0, state?.triggerData?.count)

        foreground = state?.triggerData?.children?.get("foreground")
        assertEquals(0.0, foreground?.count)
        appInit = state?.triggerData?.children?.get("init")
        assertEquals(0.0, appInit?.count)

        instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNotNull(state?.triggerResult)
    }

    @Test
    public fun testCompoundOrTrigger(): TestResult = runTest {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "simple-or",
                type = CompoundAutomationTriggerType.OR,
                goal = 2.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.FOREGROUND,
                                goal = 2.0,
                                id = "foreground",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = true
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.APP_INIT,
                                goal = 2.0,
                                id = "init",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = true
                    ),
                )
            )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(0.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 0.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNotNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 0.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)
    }

    @Test
    public fun testCompoundComplexOrTrigger(): TestResult = runTest {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "complex-or",
                type = CompoundAutomationTriggerType.OR,
                goal = 2.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.FOREGROUND,
                                goal = 2.0,
                                id = "foreground",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = true
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.APP_INIT,
                                goal = 2.0,
                                id = "init",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = null
                    ),
                )
            )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(0.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 0.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 0.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNotNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 0.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)
    }

    @Test
    public fun testCompoundChainTrigger(): TestResult = runTest {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "simple-chain",
                type = CompoundAutomationTriggerType.CHAIN,
                goal = 2.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.FOREGROUND,
                                goal = 2.0,
                                id = "foreground",
                                predicate = null
                            )
                        ),
                        isSticky = true,
                        resetOnIncrement = null
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.APP_INIT,
                                goal = 2.0,
                                id = "init",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = true
                    ),
                )
            )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertNull(state?.triggerData)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertNull(state?.triggerData?.count)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNotNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)
    }

    @Test
    public fun testCompoundChainTriggerWildChildState(): TestResult = runTest {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "state-child-chain",
                type = CompoundAutomationTriggerType.CHAIN,
                goal = 1.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.CUSTOM_EVENT_VALUE,
                                goal = 1.0,
                                id = "custom-event",
                                predicate = null
                            )
                        ),
                        isSticky = true,
                        resetOnIncrement = null
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.activeSession(1u),
                        isSticky = null,
                        resetOnIncrement = true
                    ),
                )
            )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.StateChanged(TriggerableState("test")))
        assertNull(state?.triggerResult)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.CUSTOM_EVENT_VALUE, JsonValue.NULL, 1.0))
        assertNotNull(state?.triggerResult)
    }

    @Test
    public fun testCompoundComplexChainTrigger(): TestResult = runTest {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "complex-chain",
                type = CompoundAutomationTriggerType.CHAIN,
                goal = 2.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.FOREGROUND,
                                goal = 2.0,
                                id = "foreground",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = null
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Event(
                            EventAutomationTrigger(
                                type = EventAutomationTriggerType.APP_INIT,
                                goal = 2.0,
                                id = "init",
                                predicate = null
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = null
                    ),
                )
            )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertNull(state?.triggerData)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 0.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertNull(state?.triggerData)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertEquals(1.0, state?.triggerData?.count)
        assertNull(state?.triggerResult)
        assertChildDataCount(state?.triggerData, "foreground", 1.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(1.0, state?.triggerData?.count)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertEquals(1.0, state?.triggerData?.count)
        assertChildDataCount(state?.triggerData, "foreground", 2.0)
        assertChildDataCount(state?.triggerData, "init", 1.0)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNotNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)
        assertChildDataCount(state?.triggerData, "foreground", 0.0)
        assertChildDataCount(state?.triggerData, "init", 0.0)
    }

    @Test
    public fun testComplexTrigger(): TestResult = runTest {
        val trigger = AutomationTrigger.Compound(
            CompoundAutomationTrigger(
                id = "complex-trigger",
                type = CompoundAutomationTriggerType.AND,
                goal = 1.0,
                children = listOf(
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Compound(
                            CompoundAutomationTrigger(
                                type = CompoundAutomationTriggerType.OR,
                                goal = 1.0,
                                id = "foreground-or-init",
                                children = listOf(
                                    CompoundAutomationTrigger.Child(
                                        trigger = AutomationTrigger.Event(
                                            EventAutomationTrigger(
                                                type = EventAutomationTriggerType.APP_INIT,
                                                goal = 1.0,
                                                id = "init",
                                                predicate = null
                                            )
                                        ),
                                        isSticky = null,
                                        resetOnIncrement = null
                                    ),
                                    CompoundAutomationTrigger.Child(
                                    trigger = AutomationTrigger.Event(
                                        EventAutomationTrigger(
                                            type = EventAutomationTriggerType.FOREGROUND,
                                            goal = 1.0,
                                            id = "foreground",
                                            predicate = null
                                        )
                                    ),
                                    isSticky = null,
                                    resetOnIncrement = null
                                    )
                                )
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = null,
                    ),
                    CompoundAutomationTrigger.Child(
                        trigger = AutomationTrigger.Compound(
                            CompoundAutomationTrigger(
                                type = CompoundAutomationTriggerType.CHAIN,
                                goal = 1.0,
                                id = "chain-screen-background",
                                children = listOf(
                                    CompoundAutomationTrigger.Child(
                                        trigger = AutomationTrigger.Event(
                                            EventAutomationTrigger(
                                                type = EventAutomationTriggerType.SCREEN,
                                                goal = 1.0,
                                                id = "screen",
                                                predicate = null
                                            )
                                        ),
                                        isSticky = null,
                                        resetOnIncrement = null
                                    ),
                                    CompoundAutomationTrigger.Child(
                                        trigger = AutomationTrigger.Event(
                                            EventAutomationTrigger(
                                                type = EventAutomationTriggerType.BACKGROUND,
                                                goal = 1.0,
                                                id = "background",
                                                predicate = null
                                            )
                                        ),
                                        isSticky = null,
                                        resetOnIncrement = null
                                    )
                                )
                            )
                        ),
                        isSticky = null,
                        resetOnIncrement = null,
                    ),
            )
        )
        )

        val instance = makeTrigger(trigger)
        instance.activate()

        var state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.SCREEN, JsonValue.wrap("screen")))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        assertNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)

        state = instance.process(AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND))
        assertNotNull(state?.triggerResult)
        assertEquals(0.0, state?.triggerData?.count)
    }

    private fun check(type: EventAutomationTriggerType, event: AutomationEvent): TriggerData? {
        val trigger = EventAutomationTrigger(type = type, goal = 3.0)
        val instance = makeTrigger(AutomationTrigger.Event(trigger))
        instance.activate()
        val result = instance.process(event)
        return result?.triggerData
    }

    private fun assertChildDataCount(parent: TriggerData?, triggerID: String, count: Double) {
        assertEquals(count, parent?.children?.get(triggerID)?.count)
    }

    private fun makeTrigger(trigger: AutomationTrigger? = null,
                            type: TriggerExecutionType = TriggerExecutionType.EXECUTION,
                            startDate: ULong? = null,
                            endDate: ULong? = null,
                            state: TriggerData? = null) : PreparedTrigger {
        val triggerData: TriggerData?
        val automationTrigger = trigger ?: AutomationTrigger.Event(
            EventAutomationTrigger(
                type = EventAutomationTriggerType.APP_INIT, goal = 1.0
            )
        )

        triggerData = state ?: TriggerData(
            scheduleId = "test-schedule",
            triggerId = automationTrigger.id,
            triggerCount = 0.0,
        )

        return PreparedTrigger(
            scheduleId = "test-schedule",
            trigger = automationTrigger,
            executionType = type,
            startDate = startDate,
            endDate = endDate,
            triggerData = triggerData,
            priority = 0,
            clock = clock
        )

    }
}
