package com.urbanairship.automation.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ApplicationMetrics
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.AutomationEvent
import com.urbanairship.automation.AutomationEventFeed
import com.urbanairship.automation.TriggerableState
import com.urbanairship.json.jsonMapOf
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

            assertEquals(AutomationEvent.AppInit, awaitItem())

            val expectedState = TriggerableState(versionUpdated = "123")
            assertEquals(AutomationEvent.StateChanged(expectedState), awaitItem())

            assertEquals(AutomationEvent.Background, awaitItem())
        }
    }

    @Test
    public fun testFirstAttachInForeground(): TestResult = runTest {
        foregroundState.value = true
        subject.attach()
        subject.feed.test {
            assertEquals(AutomationEvent.AppInit, awaitItem())
            assertEquals(AutomationEvent.StateChanged(TriggerableState(versionUpdated = "123")), awaitItem())
            assertEquals(AutomationEvent.Foreground, awaitItem())
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
            assertEquals(AutomationEvent.Background, awaitItem())
        }
    }

    @Test
    public fun testSupportedEvents(): TestResult = runTest {
        subject.attach()

        subject.feed.test {
            /// First attach events
            skipItems(3)

            // Foreground - foreground and app state
            foregroundState.value = true
            assertEquals(AutomationEvent.Foreground, awaitItem())
            var stateEvent = awaitItem() as AutomationEvent.StateChanged
            assertNotNull(stateEvent.state.appSessionID)

            // Background - background and app state
            foregroundState.value = false
            assertEquals(AutomationEvent.Background, awaitItem())
            stateEvent = awaitItem() as AutomationEvent.StateChanged
            assertNull(stateEvent.state.appSessionID)

            // Core events
            listOf(
                AirshipEventFeed.Event.ScreenTracked("test-screen"),
                AirshipEventFeed.Event.RegionEnter(jsonMapOf("region" to "enter")),
                AirshipEventFeed.Event.RegionExit(jsonMapOf("region" to "exit")),
                AirshipEventFeed.Event.CustomEvent(jsonMapOf("custom" to "event"), 100.0),
                AirshipEventFeed.Event.FeatureFlagInteracted(jsonMapOf("ff" to "flag"))
            ).forEach {
                events.emit(it)
                assertEquals(AutomationEvent.CoreEvent(it), awaitItem())
            }
        }
    }

    @Test
    public fun testNoEventsIfNotAttached(): TestResult = runTest {
        events.emit(AirshipEventFeed.Event.ScreenTracked("test-screen"))
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
        events.emit(AirshipEventFeed.Event.ScreenTracked("test-screen"))
        subject.feed.test {
            expectNoEvents()
        }
    }

}
