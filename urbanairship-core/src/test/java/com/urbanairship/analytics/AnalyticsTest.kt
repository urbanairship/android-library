/* Copyright Airship and Contributors */
package com.urbanairship.analytics

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.Airship
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PendingResult
import com.urbanairship.Platform
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.analytics.data.EventManager
import com.urbanairship.analytics.location.RegionEvent
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.requireField
import com.urbanairship.locale.LocaleManager
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import com.urbanairship.util.LocaleCompat
import java.util.TimeZone
import kotlin.time.Duration.Companion.milliseconds
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
public class AnalyticsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val mockEventManager: EventManager = mockk(relaxed = true)
    private val mockChannel: AirshipChannel = mockk(relaxed = true)
    private val mockPermissionsManager: PermissionsManager = mockk(relaxed = true)
    private val mockEventFeed: AirshipEventFeed = mockk(relaxed = true)

    private val dataStore = PreferenceDataStore.inMemoryStore(context)
    private val localeManager = LocaleManager(context, dataStore)
    private val runtimeConfig = TestAirshipRuntimeConfig()
    private val activityMonitor = TestActivityMonitor()
    private val privacyManager = PrivacyManager(dataStore, PrivacyManager.Feature.ALL, dispatcher = UnconfinedTestDispatcher())


    private val testDispatcher = StandardTestDispatcher()

    private val analytics = Analytics(
        context,
        dataStore,
        runtimeConfig,
        privacyManager,
        mockChannel,
        activityMonitor,
        localeManager,
        mockEventManager,
        mockPermissionsManager,
        mockEventFeed,
        dispatcher = testDispatcher
    )

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Test that a session id is created when analytics is created
     */
    @Test
    public fun testOnCreate() {
        assertThat(analytics.sessionId).isNotEmpty()
    }

    /**
     * Test that when the app goes into the foreground, a new
     * session id is created, a broadcast is sent for foreground, isAppForegrounded
     * is set to true, and a foreground event job is dispatched.
     */
    @Test
    public fun testOnForeground() {
        // Start analytics in the background
        activityMonitor.background()

        // Grab the session id to compare it to a new session id
        val sessionId = analytics.sessionId
        activityMonitor.foreground()

        // Verify that we generate a new session id
        assertThat(analytics.sessionId).isNotEmpty()
        assertThat(analytics.sessionId).isNotEqualTo(sessionId)
    }

    /**
     * Test that when the app goes into the background, the conversion send id is
     * cleared, a background event is sent to be added, and a broadcast for background
     * is sent.
     */
    @Test
    public fun testOnBackground(): TestResult = runTest(testDispatcher) {
        // Start analytics in the foreground
        analytics.conversionSendId = "some-id"
        activityMonitor.background()

        // Verify that we clear the conversion send id
        assertThat(analytics.conversionSendId).isNull()

        advanceUntilIdle()

        coVerify {
            mockEventManager.addEvent(
                match {
                    it.type == EventType.APP_BACKGROUND
                },
                eq(Event.Priority.NORMAL),
            )
        }
    }


    @Test
    public fun testOnBackgroundSchedulesEventUpload() {
        activityMonitor.background()
        verify {
            mockEventManager.scheduleEventUpload(0.milliseconds)
        }
    }

    /**
     * Test setting the conversion conversion send id
     */
    @Test
    public fun testSetConversionPushId() {
        analytics.conversionSendId = "some-id"
        assertThat(analytics.conversionSendId).isEqualTo("some-id")
        analytics.conversionSendId = null
        assertThat(analytics.conversionSendId).isNull()
    }

    /**
     * Test adding an event
     */
    @Test
    public fun testAddEvent(): TestResult = runTest(testDispatcher) {
        val event = CustomEvent.newBuilder("cool").build()
        analytics.addEvent(event)

        advanceUntilIdle()

        coVerify {
            mockEventManager.addEvent(
                AirshipEventData(
                    event.eventId,
                    analytics.sessionId,
                    event.getEventData(context, ConversionData()).toJsonValue(),
                    event.type,
                    event.timeMilliseconds
                ),
                event.priority
            )
        }
    }

    /**
     * Test adding an event when analytics is disabled through airship config.
     */
    @Test
    public fun testAddEventDisabledAnalyticsConfig(): TestResult = runTest(testDispatcher) {
        val options = AirshipConfigOptions.Builder()
            .setDevelopmentAppKey("appKey")
            .setDevelopmentAppSecret("appSecret")
            .setAnalyticsEnabled(false)
            .build()

        runtimeConfig.setConfigOptions(options)
        analytics.addEvent(AppForegroundEvent(100))
        coVerify(exactly = 0) { mockEventManager.addEvent(any(), any())  }
    }

    /**
     * Test adding an event when analytics is disabled
     */
    @Test
    public fun testAddEventDisabledAnalytics(): TestResult = runTest(testDispatcher) {
        privacyManager.disable(PrivacyManager.Feature.ANALYTICS)
        analytics.addEvent(AppForegroundEvent(100))
        coVerify(exactly = 0) { mockEventManager.addEvent(any(), any())  }
    }

    @Test
    public fun testCustomEventFeed(): TestResult = runTest(testDispatcher) {
        val event = CustomEvent.newBuilder("cool").build()
        analytics.recordCustomEvent(event)

        advanceUntilIdle()

        val expectedFeedEvent =  AirshipEventFeed.Event.Analytics(
            event.type,
            event.toJsonValue(),
            event.eventValue?.toDouble()
        )

        verify(exactly = 1) {
            mockEventFeed.emit(expectedFeedEvent)
        }
    }

    /**
     * Test adding an invalid event
     */
    @Test
    public fun testAddInvalidEvent(): TestResult = runTest(testDispatcher) {
        val event: Event = mockk() {
            every { eventId } returns "event-id"
            every { type } returns EventType.APP_BACKGROUND
            every { time } returns "1000"
            every { priority } returns Event.Priority.HIGH
            every { isValid() } returns false
        }

        analytics.addEvent(event)
        coVerify(exactly = 0) { mockEventManager.addEvent(any(), any())  }
    }

    /**
     * Test disabling analytics should start dispatch a job to delete all events.
     */
    @Test
    public fun testDisableAnalytics(): TestResult = runTest(testDispatcher) {
        privacyManager.disable(PrivacyManager.Feature.ANALYTICS)
        advanceUntilIdle()
        coVerify { mockEventManager.deleteEvents() }
    }

    /**
     * Test editAssociatedIdentifiers dispatches a job to add a new associate_identifiers event.
     */
    @Test
    public fun testEditAssociatedIdentifiers(): TestResult = runTest(testDispatcher) {
        analytics.editAssociatedIdentifiers().addIdentifier("customKey", "customValue").apply()
        advanceUntilIdle()
        coVerify {
            mockEventManager.addEvent(
                match {
                    it.type == EventType.ASSOCIATE_IDENTIFIERS
                },
                eq(Event.Priority.NORMAL),
            )
        }

        // Verify identifiers are stored
        assertThat(analytics.associatedIdentifiers.ids).isEqualTo(mapOf("customKey" to "customValue"))
    }

    @Test
    public fun testEditAssociatedIdentifiersClosure(): TestResult = runTest(testDispatcher) {
        analytics.editAssociatedIdentifiers {
            addIdentifier("customKey", "customValue")
        }
        advanceUntilIdle()
        coVerify {
            mockEventManager.addEvent(
                match {
                    it.type == EventType.ASSOCIATE_IDENTIFIERS
                },
                eq(Event.Priority.NORMAL),
            )
        }

        // Verify identifiers are stored
        assertThat(analytics.associatedIdentifiers.ids).isEqualTo(mapOf("customKey" to "customValue"))
    }

    /**
     * Test editAssociatedIdentifiers doesn't dispatch a job when adding a duplicate associate_identifier.
     */
    @Test
    public fun testEditDuplicateAssociatedIdentifiers(): TestResult = runTest(testDispatcher) {
        analytics.editAssociatedIdentifiers().addIdentifier("customKey", "customValue").apply()

        // Edit with a duplicate identifier
        analytics.editAssociatedIdentifiers().addIdentifier("customKey", "customValue").apply()

        advanceUntilIdle()

        // Verify we don't add an event more than once
        coVerify(exactly = 1) {
            mockEventManager.addEvent(
                match {
                    it.type == EventType.ASSOCIATE_IDENTIFIERS
                },
                eq(Event.Priority.NORMAL),
            )
        }
    }

    /**
     * Test that tracking event adds itself on background
     */
    @Test
    public fun testTrackingEventBackground(): TestResult = runTest(testDispatcher) {
        analytics.trackScreen("test_screen")

        // Make call to background
        activityMonitor.background()

        advanceUntilIdle()

        coVerify{
            mockEventManager.addEvent(
                match {
                    it.type == EventType.SCREEN_TRACKING && it.body.requireMap().requireField<String>("screen") == "test_screen"
                },
                eq(Event.Priority.NORMAL),
            )
        }
    }

    /**
     * Test that tracking event adds itself upon adding a new screen
     */
    @Test
    public fun testTrackingEventAddNewScreen(): TestResult = runTest(testDispatcher) {
        analytics.trackScreen("test_screen_1")

        // Add another screen
        analytics.trackScreen("test_screen_2")

        advanceUntilIdle()

        // Verify we started an add event job
        coVerify{
            mockEventManager.addEvent(
                match {
                    it.type == EventType.SCREEN_TRACKING && it.body.requireMap().requireField<String>("screen") == "test_screen_1"
                },
                eq(Event.Priority.NORMAL),
            )
        }
    }


    /**
     * Test that tracking event ignores duplicate tracking calls for same screen
     */
    @Test
    public fun testTrackingEventAddSameScreen(): TestResult = runTest(testDispatcher) {
        analytics.trackScreen("test_screen_1")

        // Add another screen
        analytics.trackScreen("test_screen_1")

        advanceUntilIdle()

        coVerify(exactly = 0){
            mockEventManager.addEvent(
                any(),
                any()
            )
        }
    }

    @Test
    public fun testEditAssociatedIdentifiersDataCollectionDisabled() {
        analytics.editAssociatedIdentifiers().addIdentifier("customKey", "customValue").apply()

        // Verify identifiers are stored
        assertThat(analytics.associatedIdentifiers.ids).isEqualTo(mapOf("customKey" to "customValue"))

        privacyManager.disable(PrivacyManager.Feature.ANALYTICS)
        assertThat(analytics.associatedIdentifiers.ids).isEmpty()

        analytics.editAssociatedIdentifiers().addIdentifier("customKey", "customValue").apply()
        assertThat(analytics.associatedIdentifiers.ids).isEmpty()
    }


    /**
     * Tests sending events
     */
    @Test
    public fun testSendingEvents(): TestResult = runTest(testDispatcher) {
        every { mockChannel.id } returns "some channel"
        coEvery { mockEventManager.uploadEvents(any(), any()) } returns true

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        assertThat(analytics.onPerformJob( jobInfo)).isEqualTo(JobResult.SUCCESS)

        advanceUntilIdle()

        coVerify { mockEventManager.uploadEvents("some channel", any()) }
    }


    /**
     * Test sending events when there's no channel ID present
     */
    @Test
    public fun testSendingWithNoChannelID(): TestResult = runTest(testDispatcher) {
        every { mockChannel.id } returns null

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        assertThat(analytics.onPerformJob(jobInfo)).isEqualTo(JobResult.SUCCESS)

        advanceUntilIdle()

        coVerify(exactly = 0) { mockEventManager.uploadEvents(any(), any()) }
    }

    /**
     * Test sending events when analytics is disabled.
     */
    @Test
    public fun testSendingWithAnalyticsDisabled(): TestResult = runTest(testDispatcher) {
        privacyManager.disable(PrivacyManager.Feature.ANALYTICS)
        every { mockChannel.id } returns "channel"

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        assertThat(analytics.onPerformJob(jobInfo)).isEqualTo(JobResult.SUCCESS)

        advanceUntilIdle()

        coVerify(exactly = 0) { mockEventManager.uploadEvents(any(), any()) }
    }

    /**
     * Test sending events when the upload fails
     */
    @Test
    public fun testSendEventsFails(): TestResult = runTest(testDispatcher) {
        every { mockChannel.id } returns "channel"
        coEvery { mockEventManager.uploadEvents(any(), any()) } returns false

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        assertThat(analytics.onPerformJob(jobInfo)).isEqualTo(JobResult.RETRY)

        advanceUntilIdle()

        coVerify(exactly = 1) { mockEventManager.uploadEvents(any(), any()) }
    }


    /**
     * This verifies all required and most optional headers.
     */
    @Test
    public fun testRequestHeaders(): TestResult = runTest(testDispatcher) {
        val locale = LocaleCompat.of("en", "US", "POSIX")
        localeManager.setLocaleOverride(locale)
        analytics.registerSDKExtension(Extension.CORDOVA, "1.2.3")

        every { mockChannel.id } returns "channel"

        val expectedHeaders = mapOf(
            "X-UA-Device-Family" to "android",
            "X-UA-Package-Name" to context.packageName,
            "X-UA-Package-Version" to (
                context
                .packageManager
                .getPackageInfo(context.packageName, 0)
                ?.versionName
                ?: ""
            ),
            "X-UA-App-Key" to  runtimeConfig.configOptions.appKey,
            "X-UA-In-Production" to  runtimeConfig.configOptions.inProduction.toString(),
            "X-UA-Device-Model" to  Build.MODEL,
            "X-UA-Android-Version-Code" to  Build.VERSION.SDK_INT.toString(),
            "X-UA-Lib-Version" to Airship.version,
            "X-UA-Timezone" to TimeZone.getDefault().id,
            "X-UA-Locale-Language" to "en",
            "X-UA-Locale-Country" to "US",
            "X-UA-Locale-Variant" to "POSIX",
            "X-UA-Frameworks" to "cordova:1.2.3",
            "X-UA-Channel-ID" to "channel",
            "X-UA-Package-Version" to "1.0",
            "X-UA-Push-Address" to "channel"
        )

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", expectedHeaders)
        }
    }

    /**
     * Test that amazon is set as the device family when the platform is amazon.
     */
    @Test
    public fun testAmazonDeviceFamily(): TestResult = runTest(testDispatcher) {
        every { mockChannel.id } returns "channel"
        runtimeConfig.setPlatform(Platform.AMAZON)

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", match {
                it["X-UA-Device-Family"] == "amazon"
            })
        }
    }

    /**
     * This verifies that we don't add the X-UA-Locale-Country if the country
     * field is blank on the locale.
     */
    @Test
    public fun testRequestHeaderEmptyLocaleCountryHeaders(): TestResult = runTest(testDispatcher) {
        localeManager.setLocaleOverride(LocaleCompat.of("en", "", "POSIX"))
        every { mockChannel.id } returns "channel"

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", match {
                !it.containsKey("X-UA-Locale-Country")
            })
        }
    }

    /**
     * This verifies that we don't add the X-UA-Locale-Variant if the variant
     * field is blank on the locale.
     */
    @Test
    public fun testRequestHeaderEmptyLocaleVariantHeaders(): TestResult = runTest(testDispatcher) {
        localeManager.setLocaleOverride(LocaleCompat.of("en", "US", ""))

        every { mockChannel.id } returns "channel"

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", match {
                !it.containsKey("X-UA-Locale-Variant")
            })
        }
    }

    /**
     * This verifies that we don't add any locale fields if the language
     * is empty.
     */
    @Test
    public fun testRequestHeaderEmptyLanguageLocaleHeaders(): TestResult = runTest(testDispatcher) {
        localeManager.setLocaleOverride(LocaleCompat.of("", "US", "POSIX"))

        every { mockChannel.id } returns "channel"

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", match {
                !it.containsKey("X-UA-Locale-Language")
                        && !it.containsKey("X-UA-Locale-Country")
                        && !it.containsKey("X-UA-Locale-Variant")
            })
        }
    }

    /**
     * Test that SDK extensions are registered correctly
     */
    @Test
    public fun testSDKExtensions(): TestResult = runTest(testDispatcher) {
        analytics.registerSDKExtension(Extension.CORDOVA, "1.0.0")
        analytics.registerSDKExtension(Extension.UNITY, "2.0.0")
        analytics.registerSDKExtension(Extension.FLUTTER, "3.0.0")
        analytics.registerSDKExtension(Extension.REACT_NATIVE, "4.0.0")
        analytics.registerSDKExtension(Extension.XAMARIN, "5.0.0")
        analytics.registerSDKExtension(Extension.TITANIUM, "6.0.0")
        analytics.registerSDKExtension(Extension.DOT_NET, "18.0.0")

        val expected =
            "cordova:1.0.0,unity:2.0.0,flutter:3.0.0,react-native:4.0.0,xamarin:5.0.0,titanium:6.0.0,dot-net:18.0.0"


        every { mockChannel.id } returns "channel"

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", match {
                it["X-UA-Frameworks"] == expected
            })
        }
    }

    @Test
    public fun testPermissionHeaders(): TestResult = runTest(testDispatcher) {
        every { mockChannel.id } returns "channel"

        every { mockPermissionsManager.configuredPermissions } returns setOf(Permission.LOCATION, Permission.DISPLAY_NOTIFICATIONS)
        every { mockPermissionsManager.checkPermissionStatusPendingResult(Permission.LOCATION) } returns PendingResult<PermissionStatus?>().apply {
            setResult(PermissionStatus.NOT_DETERMINED)
        }

        every { mockPermissionsManager.checkPermissionStatusPendingResult(Permission.DISPLAY_NOTIFICATIONS) } returns PendingResult<PermissionStatus?>().apply {
            setResult(PermissionStatus.GRANTED)
        }

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", match {
                it["X-UA-Permission-location"] == "not_determined" &&
                        it["X-UA-Permission-display_notifications"] == "granted"
            })
        }
    }

    /**
     * Test that analytics header delegates are able to provide additional headers.
     */
    @Test
    public fun testAnalyticHeaderDelegate(): TestResult = runTest(testDispatcher) {
        every { mockChannel.id } returns "channel"
        analytics.addHeaderDelegate { mapOf("foo" to "bar") }

        analytics.addHeaderDelegate { mapOf("cool" to "story", "neat" to "rad") }

        val jobInfo = JobInfo.newBuilder().setAction(EventManager.ACTION_SEND).build()
        analytics.onPerformJob(jobInfo)

        advanceUntilIdle()

        coVerify {
            mockEventManager.uploadEvents("channel", match {
                it["foo"] == "bar" && it["cool"] == "story" && it["neat"] == "rad" && it["X-UA-Device-Family"] == "android"
            })
        }
    }

    @Test
    public fun testScreenState(): TestResult = runTest(testDispatcher) {
        analytics.screenState.test {
            assertThat(awaitItem()).isNull()

            analytics.trackScreen("foo")
            assertThat(awaitItem()).isEqualTo("foo")

            analytics.trackScreen(null)
            assertThat(awaitItem()).isNull()

            analytics.trackScreen("foo")
            assertThat(awaitItem()).isEqualTo("foo")

            analytics.trackScreen("foo")
            expectNoEvents()

            activityMonitor.background()
            assertThat(awaitItem()).isNull()

            activityMonitor.foreground()
            assertThat(awaitItem()).isEqualTo("foo")
        }
    }

    @Test
    public fun testRegionState(): TestResult = runTest(testDispatcher) {
        analytics.regionState.test {
            assertThat(awaitItem()).isEmpty()

            analytics.recordRegionEvent(
                RegionEvent.newBuilder("foo", RegionEvent.Boundary.ENTER)
                    .setSource("source")
                    .build()
            )

            assertThat(awaitItem()).isEqualTo(setOf("foo"))

            analytics.recordRegionEvent(
                RegionEvent.newBuilder("bar", RegionEvent.Boundary.EXIT)
                    .setSource("source")
                    .build()
            )

            expectNoEvents()

            analytics.recordRegionEvent(
                RegionEvent.newBuilder("baz", RegionEvent.Boundary.ENTER)
                    .setSource("source")
                    .build()
            )
            assertThat(awaitItem()).isEqualTo(setOf("foo", "baz"))
        }
    }

    @Test
    public fun testEventFeed(): TestResult = runTest(testDispatcher) {
        analytics.trackScreen("foo")
        verify {
            mockEventFeed.emit(AirshipEventFeed.Event.Screen("foo"))
        }

        val regionEnter = RegionEvent.newBuilder("foo", RegionEvent.Boundary.ENTER)
            .setSource("source")
            .build()

        analytics.recordRegionEvent(regionEnter)
        verify {
            mockEventFeed.emit(
                AirshipEventFeed.Event.Analytics(
                    EventType.REGION_ENTER,
                    regionEnter.getEventData(context, ConversionData(null, null, null)).toJsonValue(),
                    null
                )
            )
        }

        val regionExit = RegionEvent.newBuilder("bar", RegionEvent.Boundary.EXIT)
            .setSource("source")
            .build()

        analytics.recordRegionEvent(regionExit)
        verify {
            mockEventFeed.emit(
                AirshipEventFeed.Event.Analytics(
                    EventType.REGION_EXIT,
                    regionExit.getEventData(context, ConversionData(null, null, null)).toJsonValue(),
                    null
                )
            )
        }

        val customEvent = CustomEvent.newBuilder("foo")
            .setEventValue(100.0)
            .build()

        analytics.recordCustomEvent(customEvent)
        verify {
            mockEventFeed.emit(
                AirshipEventFeed.Event.Analytics(
                    EventType.CUSTOM_EVENT,
                    customEvent.toJsonValue(),
                    100.0
                )
            )
        }
    }
}
