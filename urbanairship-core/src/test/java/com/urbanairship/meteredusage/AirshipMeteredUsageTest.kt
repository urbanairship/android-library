package com.urbanairship.meteredusage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestActivityMonitor
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.http.RequestResult
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.jsonMapOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
public class AirshipMeteredUsageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val activityMonitor = TestActivityMonitor()
    private val privacyManager: PrivacyManager = mockk()
    private val apiClient: MeteredUsageApiClient = mockk()
    private val mockJobDispatcher: JobDispatcher = mockk(relaxed = true)

    private lateinit var eventsStore: EventsDao
    private lateinit var manager: AirshipMeteredUsage

    @Before
    public fun setUp() {
        eventsStore = EventsDatabase.inMemory(context).eventsDao()

        manager = spyk(AirshipMeteredUsage(
            context = context,
            dataStore = PreferenceDataStore.inMemoryStore(context),
            config = TestAirshipRuntimeConfig.newTestConfig(),
            activityMonitor = activityMonitor,
            privacyManager = privacyManager,
            store = eventsStore,
            client = apiClient,
            jobDispatcher = mockJobDispatcher
        ), recordPrivateCalls = true)
    }

    @Test
    public fun testConfigUpdate(): TestResult = runTest {

        every { privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS) } returns true

        manager.setConfig(Config.default())
        verify(exactly = 0) { mockJobDispatcher.setRateLimit(any(), any(), any(), any()) }

        val configWithRateLimit = Config(false, 0, 10)
        manager.setConfig(configWithRateLimit)
        verify { mockJobDispatcher.setRateLimit("MeteredUsage.rateLimit", 1,
            configWithRateLimit.interval, TimeUnit.MILLISECONDS) }

        val enabledConfig = Config(true, 15, 30)
        manager.setConfig(enabledConfig)
        verify { mockJobDispatcher.setRateLimit("MeteredUsage.rateLimit", 1,
            enabledConfig.interval, TimeUnit.MILLISECONDS) }
        verify { manager.scheduleUpload(enabledConfig.initialDelay) }

        val anotherEnabledConfig = Config(true, 10, 20)
        manager.setConfig(anotherEnabledConfig)
        verify { mockJobDispatcher.setRateLimit("MeteredUsage.rateLimit", 1,
            anotherEnabledConfig.interval, TimeUnit.MILLISECONDS) }
        verify(exactly = 0) { manager.scheduleUpload() }
    }

    @Test
    public fun testScheduleUpload(): TestResult = runTest {
        manager.scheduleUpload()
        verify(exactly = 0) { mockJobDispatcher.dispatch(any()) }

        val slot = slot<JobInfo>()
        every { mockJobDispatcher.dispatch(capture(slot)) } returns Unit

        manager.setConfig(Config(true, 10, 20))
        manager.scheduleUpload()
        verify { mockJobDispatcher.dispatch(any()) }
        assertEquals(JobInfo.KEEP, slot.captured.conflictStrategy)

        manager.scheduleUpload(conflictStrategy = JobInfo.REPLACE)
        verify { mockJobDispatcher.dispatch(any()) }

        assertTrue(slot.isCaptured)
        assertFalse(slot.isNull)

        assertEquals(AirshipMeteredUsage::class.java.name, slot.captured.airshipComponentName)
        assertEquals("MeteredUsage.upload", slot.captured.action)
        assertEquals(JobInfo.REPLACE, slot.captured.conflictStrategy)
        assertTrue(slot.captured.isNetworkAccessRequired)
        assertEquals(0, slot.captured.minDelayMs)
    }

    @Test
    public fun testEventUploadOnAppBackground() {
        manager.setConfig(Config(true, 1, 2))

        val slot = slot<JobInfo>()
        every { mockJobDispatcher.dispatch(capture(slot)) } returns Unit

        activityMonitor.background()
        assert(slot.isCaptured)
        assert(!slot.isNull)
        assertEquals(JobInfo.REPLACE, slot.captured.conflictStrategy)
        assertEquals(0, slot.captured.minDelayMs)
    }

    @Test
    public fun testAddEvent(): TestResult = runTest {
        var events = eventsStore.getAllEvents()
        assert(events.isEmpty())

        every { privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS) } returns true

        val event = MeteredUsageEventEntity(
            eventId = "event-id",
            entityId = "entity-id",
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = "test-product",
            reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
            timestamp = 12,
            contactId = "test-contact-id"
        )

        manager.addEvent(event)
        verify(exactly = 1) { manager.scheduleUpload() }

        events = eventsStore.getAllEvents()
        assertEquals(1, events.size)
        assertEquals(event, events.first())

        eventsStore.deleteAll()

        every { privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS) } returns false
        manager.addEvent(event)
        verify(exactly = 2) { manager.scheduleUpload() }
        events = eventsStore.getAllEvents()
        assertEquals(event.eventId, events.first().eventId)
        assertEquals(event.type, events.first().type)
        assertEquals(event.product, events.first().product)
        assertNull(events.first().entityId)
        assertNull(events.first().reportingContext)
        assertNull(events.first().timestamp)
    }

    @Test
    public fun testPerformJobDoesNothing(): TestResult = runTest {
        var jobInfo = makeJobInfo()
        // disabled in config
        var jobResult = manager.onPerformJob(mockk(), jobInfo)
        coVerify(exactly = 0) { apiClient.uploadEvents(any(), any()) }
        assertEquals(JobResult.SUCCESS, jobResult)

        // invalid job id
        manager.setConfig(Config(true, 1, 2))
        jobInfo = makeJobInfo(jobId = "invalid.job.id")
        jobResult = manager.onPerformJob(mockk(), jobInfo)
        coVerify(exactly = 0) { apiClient.uploadEvents(any(), any()) }
        assertEquals(JobResult.SUCCESS, jobResult)

        // no stored events
        jobInfo = makeJobInfo()
        jobResult = manager.onPerformJob(mockk(), jobInfo)
        coVerify(exactly = 0) { apiClient.uploadEvents(any(), any()) }
        assertEquals(JobResult.SUCCESS, jobResult)
    }

    @Test
    public fun testPerformJobUploadsEvents(): TestResult = runTest {
        manager.setConfig(Config(true, 1, 2))

        val event = MeteredUsageEventEntity(
            eventId = "event-id",
            entityId = "entity-id",
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = "test-product",
            reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
            timestamp = 12,
            contactId = "test-contact-id"
        )

        eventsStore.addEvent(event)

        val airship: UAirship = mockk()
        val channel: AirshipChannel = mockk()
        every { airship.channel } returns channel
        every { channel.id } returns "test-channel-id"

        every { privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS) } returns true
        coEvery { apiClient.uploadEvents(any(), any()) } returns RequestResult(200, null, null, null)

        val jobResult = manager.onPerformJob(airship, makeJobInfo())
        coVerify(exactly = 1) { apiClient.uploadEvents(listOf(event), "test-channel-id") }
        assertEquals(JobResult.SUCCESS, jobResult)
        assert(eventsStore.getAllEvents().isEmpty())
    }

    @Test
    public fun testPerformJobStripsEventInfo(): TestResult = runTest {
        manager.setConfig(Config(true, 1, 2))

        val event = MeteredUsageEventEntity(
            eventId = "event-id",
            entityId = "entity-id",
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = "test-product",
            reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
            timestamp = 12,
            contactId = "test-contact-id"
        )

        eventsStore.addEvent(event)

        val airship: UAirship = mockk()
        val channel: AirshipChannel = mockk()
        every { airship.channel } returns channel
        every { channel.id } returns "test-channel-id"

        every { privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS) } returns false
        coEvery { apiClient.uploadEvents(any(), any()) } returns RequestResult(200, null, null, null)

        val jobResult = manager.onPerformJob(airship, makeJobInfo())
        coVerify(exactly = 1) { apiClient.uploadEvents(listOf(event.withAnalyticsDisabled()), null) }
        assertEquals(JobResult.SUCCESS, jobResult)
        assert(eventsStore.getAllEvents().isEmpty())
    }

    @Test
    public fun testPerformJobKeepsEventsOnFailure(): TestResult = runTest {
        manager.setConfig(Config(true, 1, 2))

        val event = MeteredUsageEventEntity(
            eventId = "event-id",
            entityId = "entity-id",
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = "test-product",
            reportingContext = jsonMapOf("reporting" to "context").toJsonValue(),
            timestamp = 12,
            contactId = "test-contact-id"
        )

        eventsStore.addEvent(event)

        val airship: UAirship = mockk()
        val channel: AirshipChannel = mockk()
        every { airship.channel } returns channel
        every { channel.id } returns "test-channel-id"

        every { privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS) } returns false
        coEvery { apiClient.uploadEvents(any(), any()) } returns RequestResult(Throwable())

        val jobResult = manager.onPerformJob(airship, makeJobInfo())
        coVerify(exactly = 1) { apiClient.uploadEvents(listOf(event.withAnalyticsDisabled()), null) }
        assertEquals(JobResult.FAILURE, jobResult)
        assertEquals(1, eventsStore.getAllEvents().size)
    }

    private fun makeJobInfo(
        delay: Long = 0,
        strategy: Int = JobInfo.KEEP,
        jobId: String = "MeteredUsage.upload"
    ): JobInfo {
        return JobInfo.newBuilder()
            .setAirshipComponent(AirshipMeteredUsage::class.java)
            .setAction(jobId)
            .setConflictStrategy(strategy)
            .setNetworkAccessRequired(true)
            .setMinDelay(delay, TimeUnit.MILLISECONDS)
            .build()
    }
}
