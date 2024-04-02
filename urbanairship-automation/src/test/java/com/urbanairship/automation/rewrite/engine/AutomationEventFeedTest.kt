package com.urbanairship.automation.rewrite.engine

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
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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
    public fun firstAttachProducesInitAndVersionUpdated(): TestResult = runTest {
        subject.feed.test {
            subject.attach()

            assertEquals(AutomationEvent.AppInit, awaitItem())

            val expectedState = TriggerableState(versionUpdated = "123")
            val state = (awaitItem() as? AutomationEvent.StateChanged)?.state
            assertEquals(expectedState, state)
        }
    }

    @Test
    public fun subsequentAttachEmitsNoEvents(): TestResult = runTest {
        subject.feed.test {
            subject.attach()
            skipItems(2)

            subject.attach()
            expectNoEvents()

            subject.detach()
            expectNoEvents()
        }
    }

    @Test
    public fun testSupportedEvents(): TestResult = runTest {
        subject.feed.test {
            subject.attach()
            skipItems(2)

            activityMonitor.foreground()
            assertEquals(AutomationEvent.Foreground, awaitItem())

            var state = (awaitItem() as? AutomationEvent.StateChanged)?.state
            assertNotNull(state?.appSessionID)

            activityMonitor.background()
            assertEquals(AutomationEvent.Background, awaitItem())

            state = (awaitItem() as? AutomationEvent.StateChanged)?.state
            assertNotNull(state)
            assertNull(state?.appSessionID)

            analytics.trackScreen("test-screen")
            val screenView = (awaitItem() as? AutomationEvent.ScreenView)
            assertEquals("test-screen", screenView?.name)

            val regionEnter = RegionEvent
                .newBuilder()
                .setRegionId("enter-reg-id")
                .setBoundaryEvent(RegionEvent.BOUNDARY_EVENT_ENTER)
                .setSource("test")
                .build()

            analytics.addEvent(regionEnter)

            val regEnter = (awaitItem() as? AutomationEvent.RegionEnter)
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

            val regExit = (awaitItem() as? AutomationEvent.RegionExit)
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

            val customEvent = awaitItem() as? AutomationEvent.CustomEvent
            assertEquals(223.0, customEvent?.count)
            assertEquals(JsonMap.newBuilder().put("event", "property").build().toJsonValue(), customEvent?.data)

            analytics.addEvent(object : Event() {
                override fun getType(): String = "feature_flag_interaction"

                override fun getEventData(): JsonMap {
                    return JsonMap.newBuilder().put("ff", "property").build()
                }
            })

            val ffEvent = awaitItem() as? AutomationEvent.FeatureFlagInteracted
            assertEquals(JsonMap.newBuilder().put("ff", "property").build().toJsonValue(), ffEvent?.data)
        }
    }

    @Test
    public fun testNoEventsIfNotAttached(): TestResult = runTest {
        subject.feed.test {
            expectNoEvents()
            analytics.trackScreen("test")
            expectNoEvents()
        }
    }

    @Test
    public fun testNoEventsAfterDetach(): TestResult = runTest {
        subject.feed.test {
            subject.attach()
            skipItems(2)

            analytics.trackScreen("test")
            awaitItem()

            subject.detach()
            analytics.trackScreen("test")
            expectNoEvents()
        }
    }
}
