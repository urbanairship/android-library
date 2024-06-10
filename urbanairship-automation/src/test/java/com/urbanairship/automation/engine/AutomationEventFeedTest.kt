package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ApplicationMetrics
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.EventType
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.automation.EventAutomationTriggerType
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.UUID
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AutomationEventFeedTest {
    private val metrics: ApplicationMetrics = mockk() {
        every { this@mockk.appVersionUpdated } returns true
        every { this@mockk.currentAppVersion } returns 123L
    }

    private val foregroundState = MutableStateFlow(false)

    private val activityMonitor: ActivityMonitor = mockk() {
        every { this@mockk.foregroundState } returns this@AutomationEventFeedTest.foregroundState
    }

    private val events = MutableSharedFlow<AirshipEventFeed.Event>()
    private val airshipEventFeed: AirshipEventFeed = mockk() {
        every { this@mockk.events } returns this@AutomationEventFeedTest.events
    }
    private val testDispatcher = StandardTestDispatcher()

    private val subject = AutomationEventFeed(metrics, activityMonitor, airshipEventFeed, testDispatcher)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testFirstAttachInBackground(): TestResult = runTest {

        subject.feed.test {
            subject.attach()

            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT), awaitItem())

            val expectedState = TriggerableState(versionUpdated = "123")
            assertEquals(AutomationEvent.StateChanged(expectedState), awaitItem())

            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND), awaitItem())
        }
    }

    @Test
    public fun testFirstAttachInForeground(): TestResult = runTest {
        foregroundState.value = true
        subject.attach()
        subject.feed.test {
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT), awaitItem())
            assertEquals(AutomationEvent.StateChanged(TriggerableState(versionUpdated = "123")), awaitItem())
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND), awaitItem())
        }
    }

    @Test
    public fun testSubsequentAttach(): TestResult = runTest {
        subject.attach()
        subject.feed.test {
            skipItems(3)
        }

        subject.attach()
        subject.detach()
        subject.attach()

        // Expect get state and background events again
        subject.feed.test {
            val expectedState = TriggerableState(versionUpdated = "123")
            assertEquals(AutomationEvent.StateChanged(expectedState), awaitItem())
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND), awaitItem())
        }
    }

    @Test
    public fun testForegroundEvents(): TestResult = runTest {
        subject.attach()

        subject.feed.test {
            /// First attach events
            skipItems(3)

            // Foreground - foreground and app state
            foregroundState.value = true
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND), awaitItem())
            var stateEvent = awaitItem() as AutomationEvent.StateChanged
            assertNotNull(stateEvent.state.appSessionID)

            // Background - background and app state
            foregroundState.value = false
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND), awaitItem())
            stateEvent = awaitItem() as AutomationEvent.StateChanged
            assertNull(stateEvent.state.appSessionID)
        }
    }

    @Test
    public fun testAnalyticFeedEvents(): TestResult = runTest {
        subject.attach()

        val eventMap = mapOf(
            EventType.CUSTOM_EVENT to listOf(
                EventAutomationTriggerType.CUSTOM_EVENT_COUNT,
                EventAutomationTriggerType.CUSTOM_EVENT_VALUE
            ),
            EventType.REGION_EXIT to listOf(EventAutomationTriggerType.REGION_EXIT),
            EventType.REGION_ENTER to listOf(EventAutomationTriggerType.REGION_ENTER),
            EventType.FEATURE_FLAG_INTERACTION to listOf(EventAutomationTriggerType.FEATURE_FLAG_INTERACTION),
            EventType.IN_APP_DISPLAY to listOf(EventAutomationTriggerType.IN_APP_DISPLAY),
            EventType.IN_APP_RESOLUTION to listOf(EventAutomationTriggerType.IN_APP_RESOLUTION),
            EventType.IN_APP_BUTTON_TAP to listOf(EventAutomationTriggerType.IN_APP_BUTTON_TAP),
            EventType.IN_APP_PERMISSION_RESULT to listOf(EventAutomationTriggerType.IN_APP_PERMISSION_RESULT),
            EventType.IN_APP_FORM_DISPLAY to listOf(EventAutomationTriggerType.IN_APP_FORM_DISPLAY),
            EventType.IN_APP_FORM_RESULT to listOf(EventAutomationTriggerType.IN_APP_FORM_RESULT),
            EventType.IN_APP_GESTURE to listOf(EventAutomationTriggerType.IN_APP_GESTURE),
            EventType.IN_APP_PAGER_COMPLETED to listOf(EventAutomationTriggerType.IN_APP_PAGER_COMPLETED),
            EventType.IN_APP_PAGER_SUMMARY to listOf(EventAutomationTriggerType.IN_APP_PAGER_SUMMARY),
            EventType.IN_APP_PAGE_SWIPE to listOf(EventAutomationTriggerType.IN_APP_PAGE_SWIPE),
            EventType.IN_APP_PAGE_VIEW to listOf(EventAutomationTriggerType.IN_APP_PAGE_VIEW),
            EventType.IN_APP_PAGE_ACTION to listOf(EventAutomationTriggerType.IN_APP_PAGE_ACTION)
        )

        subject.feed.test {
            /// First attach events
            skipItems(3)

            EventType.entries.forEach {
                val data = JsonValue.wrap(UUID.randomUUID().toString())
                events.emit(AirshipEventFeed.Event.Analytics(it, data))

                eventMap[it]?.forEach { triggerType ->
                    assertEquals(AutomationEvent.Event(triggerType, data, 1.0), awaitItem())
                }
            }

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testScreenEvent(): TestResult = runTest {
        subject.attach()

        subject.feed.test {
            /// First attach events
            skipItems(3)

            events.emit(AirshipEventFeed.Event.Screen("foo"))
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.SCREEN, JsonValue.wrap("foo"), 1.0), awaitItem())
            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testCustomEventValues(): TestResult = runTest {
        subject.attach()

        subject.feed.test {
            /// First attach events
            skipItems(3)

            events.emit(AirshipEventFeed.Event.Analytics(EventType.CUSTOM_EVENT, JsonValue.NULL, 10.0))
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.CUSTOM_EVENT_COUNT, JsonValue.NULL, 1.0), awaitItem())
            assertEquals(AutomationEvent.Event(EventAutomationTriggerType.CUSTOM_EVENT_VALUE, JsonValue.NULL, 10.0), awaitItem())

            ensureAllEventsConsumed()
        }
    }

    @Test
    public fun testNoEventsIfNotAttached(): TestResult = runTest {
        events.emit(AirshipEventFeed.Event.Screen("test-screen"))
        subject.feed.test {
            expectNoEvents()
        }
    }

    @Test
    public fun testNoEventsAfterDetach(): TestResult = runTest {
        subject.attach()
        subject.feed.test {
            skipItems(3)
        }
        subject.detach()
        events.emit(AirshipEventFeed.Event.Screen("test-screen"))
        subject.feed.test {
            expectNoEvents()
        }
    }

}
