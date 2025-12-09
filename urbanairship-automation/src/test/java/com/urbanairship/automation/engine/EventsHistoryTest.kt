package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.automation.EventAutomationTriggerType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EventsHistoryTest {

    private val clock = TestClock()
    private val history = EventsHistory(clock)

    @Test
    fun testAddEvent() {
        val event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT)
        history.add(event)

        assertEquals(listOf(event), history.getEvents())
    }

    @Test
    fun testPruning() {
        val event = AutomationEvent.Event(EventAutomationTriggerType.APP_INIT)
        history.add(event)

        // Advance time past limit
        clock.currentTimeMillis += EventsHistory.HISTORY_DURATION.inWholeMilliseconds + 1

        // Add another event to trigger pruning
        val event2 = AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND)
        history.add(event2)

        assertEquals(listOf(event2), history.getEvents())
    }

    @Test
    fun testMaxEvents() {
        for (i in 0 until EventsHistory.MAX_EVENTS_COUNT * 2) {
            history.add(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))
        }

        assertEquals(EventsHistory.MAX_EVENTS_COUNT, history.getEvents().size)
    }
}
