package com.urbanairship.automation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ApplicationMetrics
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.location.RegionEvent
import com.urbanairship.automation.rewrite.AutomationEvent
import com.urbanairship.automation.rewrite.AutomationEventFeed
import com.urbanairship.automation.rewrite.TriggerableState
import com.urbanairship.json.JsonMap
import com.urbanairship.locale.LocaleManager
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class AutomationEventFeedTest {
    private val metrics: ApplicationMetrics = mockk()
    private val activityMonitor = TestActivityMonitor()
    private lateinit var analytics: Analytics
    private lateinit var subject: AutomationEventFeed

    @Before
    public fun setup() {
        every { metrics.appVersionUpdated } returns true
        every { metrics.currentAppVersion } returns 123L

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStore = PreferenceDataStore.inMemoryStore(context)

        analytics = Analytics(
            context,
            dataStore,
            TestAirshipRuntimeConfig(),
            PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL),
            mockk(),
            LocaleManager(context, dataStore),
            mockk()
        )

        subject = AutomationEventFeed(metrics, activityMonitor, analytics)
    }

    @Test
    public fun firstAttachProducesInitAndVersionUpdated(): TestResult = runTest(
        UnconfinedTestDispatcher()
    ) {

        val events = mutableListOf<AutomationEvent>()
        collect(events, backgroundScope)

        subject.attach()

        val expectedState = TriggerableState(versionUpdated = "123")
        assertEquals(2, events.count())
        assertEquals(AutomationEvent.AppInit, events[0])

        val state = (events[1] as? AutomationEvent.StateChanged)?.state
        assertEquals(expectedState, state)
    }

    @Test
    public fun subsequentAttachEmitsNoEvents(): TestResult = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<AutomationEvent>()
        collect(events, backgroundScope)

        subject.attach()

        assertEquals(2, events.count())

        events.clear()
        subject.attach()
        assertEquals(0, events.count())

        subject.detach()
        subject.attach()
        assertEquals(0, events.count())
    }

    @Test
    public fun testSupportedEvents(): TestResult = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<AutomationEvent>()
        collect(events, backgroundScope)

        subject.attach()
        events.clear()

        activityMonitor.foreground()
        assertEquals(AutomationEvent.Foreground, events.removeFirst())

        var state = (events.removeFirst() as? AutomationEvent.StateChanged)?.state
        assertNotNull(state?.appSessionID)

        activityMonitor.background()
        assertEquals(AutomationEvent.Background, events.removeFirst())
        state = (events.removeFirst() as? AutomationEvent.StateChanged)?.state
        assertNull(state?.appSessionID)

        analytics.trackScreen("test-screen")
        val screenView = (events.removeFirst() as? AutomationEvent.ScreenView)
        assertEquals("test-screen", screenView?.name)

        val regionEnter = RegionEvent
            .newBuilder()
            .setRegionId("enter-reg-id")
            .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_ENTER)
            .setSource("test")
            .build()

        analytics.addEvent(regionEnter)

        val regEnter = (events.removeFirst() as? AutomationEvent.RegionEnter)
        assertEquals(JsonMap.newBuilder()
            .put("region_id", "enter-reg-id")
            .put("action", "enter")
            .put("source", "test")
            .build().toJsonValue() , regEnter?.data)

        val regionExit = RegionEvent
            .newBuilder()
            .setRegionId("exit-reg-id")
            .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_EXIT)
            .setSource("test2")
            .build()

        analytics.addEvent(regionExit)

        val regExit = (events.removeFirst() as? AutomationEvent.RegionExit)
        assertEquals(JsonMap.newBuilder()
            .put("region_id", "exit-reg-id")
            .put("action", "exit")
            .put("source", "test2")
            .build().toJsonValue() , regExit?.data)

        val customEventAnalytic = CustomEvent
            .newBuilder("test-custom")
            .setEventValue(223)
            .setProperties(JsonMap.newBuilder().put("event", "property").build())
            .build()
        analytics.addEvent(customEventAnalytic)

        val customEvent = events.removeFirst() as? AutomationEvent.CustomEvent
        assertEquals(223.0, customEvent?.count)
        assertEquals(JsonMap.newBuilder().put("event", "property").build().toJsonValue(), customEvent?.data)

        analytics.addEvent(object : Event() {
            override fun getType(): String = "feature_flag_interaction"

            override fun getEventData(): JsonMap {
                return JsonMap.newBuilder().put("ff", "property").build()
            }
        })

        val ffEvent = events.removeFirst() as? AutomationEvent.FeatureFlagInteracted
        assertEquals(JsonMap.newBuilder().put("ff", "property").build().toJsonValue(), ffEvent?.data)
    }

    @Test
    public fun testNoEventsIfNotAttached(): TestResult = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<AutomationEvent>()
        collect(events, backgroundScope)

        assert(events.isEmpty())
        analytics.trackScreen("test")
        assert(events.isEmpty())
    }

    @Test
    public fun testNoEventsAfterDetach(): TestResult = runTest(UnconfinedTestDispatcher()) {
        val events = mutableListOf<AutomationEvent>()
        collect(events, backgroundScope)

        subject.attach()
        assertEquals(2, events.count())

        analytics.trackScreen("test")
        assertEquals(3, events.count())
        events.clear()

        subject.detach()
        analytics.trackScreen("test")
        assert(events.isEmpty())
    }

    private fun collect(destination: MutableList<AutomationEvent>, scope: CoroutineScope) {
        scope.launch { subject.feed.toList(destination) }
    }
}
