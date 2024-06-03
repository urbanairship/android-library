package com.urbanairship.meteredusage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.http.RequestResult
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.jsonMapOf
import com.urbanairship.remoteconfig.MeteredUsageConfig
import com.urbanairship.remoteconfig.RemoteConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
public class AirshipMeteredUsageTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testConfig: TestAirshipRuntimeConfig = TestAirshipRuntimeConfig()
    private val privacyManager: PrivacyManager = mockk()
    private val apiClient: MeteredUsageApiClient = mockk()
    private val mockJobDispatcher: JobDispatcher = mockk(relaxed = true)

    private lateinit var eventsStore: EventsDao
    private lateinit var manager: AirshipMeteredUsage

    @Before
    public fun setUp() {
        eventsStore = EventsDatabase.inMemory(context).eventsDao()

        manager = AirshipMeteredUsage(
            context = context,
            dataStore = PreferenceDataStore.inMemoryStore(context),
            config = testConfig,
            privacyManager = privacyManager,
            store = eventsStore,
            client = apiClient,
            jobDispatcher = mockJobDispatcher
        )

        verify(exactly = 1) { mockJobDispatcher.setRateLimit(any(), any(), any(), any()) }
    }

    @Test
    public fun testConfigUpdate(): TestResult = runTest {
        every { privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS) } returns true

        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(false, 0, 10)
            )
        )
        verify {
            mockJobDispatcher.setRateLimit(
                "MeteredUsage.rateLimit", 1, 10, TimeUnit.MILLISECONDS
            )
        }

        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(true, 15, 30)
            )
        )
        verify {
            mockJobDispatcher.setRateLimit(
                "MeteredUsage.rateLimit", 1, 30, TimeUnit.MILLISECONDS
            )
        }
        verify { mockJobDispatcher.dispatch(makeJobInfo(15)) }

        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(true, 10, 20)
            )
        )

        verify {
            mockJobDispatcher.setRateLimit(
                "MeteredUsage.rateLimit", 1, 20, TimeUnit.MILLISECONDS
            )
        }
        verify(exactly = 0) { mockJobDispatcher.dispatch(makeJobInfo(10)) }
    }

    @Test
    public fun testAddEvent(): TestResult = runTest {
        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(true, 1, 2)
            )
        )

        var events = eventsStore.getAllEvents()
        assert(events.isEmpty())

        every { privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS) } returns true

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
        verify(exactly = 1) { mockJobDispatcher.dispatch(makeJobInfo(0)) }

        events = eventsStore.getAllEvents()
        assertEquals(1, events.size)
        assertEquals(event, events.first())

        eventsStore.deleteAll()

        every { privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS) } returns false
        manager.addEvent(event)
        verify(exactly = 2) { mockJobDispatcher.dispatch(makeJobInfo(0)) }

        events = eventsStore.getAllEvents()
        assertEquals(event.eventId, events.first().eventId)
        assertEquals(event.type, events.first().type)
        assertEquals(event.product, events.first().product)
        assertNull(events.first().entityId)
        assertNull(events.first().reportingContext)
        assertNull(events.first().timestamp)
    }

    @Test
    public fun testAddEventDisabledConfig(): TestResult = runTest {
        every { privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS) } returns true

        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(false, 1, 2)
            )
        )

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
        verify(exactly = 0) { mockJobDispatcher.dispatch(any()) }

        eventsStore.getAllEvents()
        assertEquals(0, eventsStore.getAllEvents().size)
    }

    @Test
    public fun testPerformJobDoesNothing(): TestResult = runTest {
        var jobInfo = makeJobInfo()
        // disabled in config
        var jobResult = manager.onPerformJob(mockk(), jobInfo)
        coVerify(exactly = 0) { apiClient.uploadEvents(any(), any()) }
        assertEquals(JobResult.SUCCESS, jobResult)

        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(true, 1, 2)
            )
        )

        // invalid job id
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
        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(true, 1, 2)
            )
        )

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

        every { privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS) } returns true
        coEvery { apiClient.uploadEvents(any(), any()) } returns RequestResult(200, null, null, null)

        val jobResult = manager.onPerformJob(airship, makeJobInfo())
        coVerify(exactly = 1) { apiClient.uploadEvents(listOf(event), "test-channel-id") }
        assertEquals(JobResult.SUCCESS, jobResult)
        assert(eventsStore.getAllEvents().isEmpty())
    }

    @Test
    public fun testPerformJobStripsEventInfo(): TestResult = runTest {
        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(true, 1, 2)
            )
        )

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

        every { privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS) } returns false
        coEvery { apiClient.uploadEvents(any(), any()) } returns RequestResult(200, null, null, null)

        val jobResult = manager.onPerformJob(airship, makeJobInfo())
        coVerify(exactly = 1) { apiClient.uploadEvents(listOf(event.withAnalyticsDisabled()), null) }
        assertEquals(JobResult.SUCCESS, jobResult)
        assert(eventsStore.getAllEvents().isEmpty())
    }

    @Test
    public fun testPerformJobKeepsEventsOnFailure(): TestResult = runTest {
        testConfig.updateRemoteConfig(
            RemoteConfig(
                meteredUsageConfig = MeteredUsageConfig(true, 1, 2)
            )
        )

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

        every { privacyManager.isEnabled(PrivacyManager.Feature.ANALYTICS) } returns false
        coEvery { apiClient.uploadEvents(any(), any()) } returns RequestResult(Throwable())

        val jobResult = manager.onPerformJob(airship, makeJobInfo())
        coVerify(exactly = 1) { apiClient.uploadEvents(listOf(event.withAnalyticsDisabled()), null) }
        assertEquals(JobResult.FAILURE, jobResult)
        assertEquals(1, eventsStore.getAllEvents().size)
    }

    private fun makeJobInfo(
        delay: Long = 0,
        jobId: String = "MeteredUsage.upload"
    ): JobInfo {
        return JobInfo.newBuilder()
            .setAirshipComponent(AirshipMeteredUsage::class.java)
            .setAction(jobId)
            .setConflictStrategy(JobInfo.KEEP)
            .setNetworkAccessRequired(true)
            .setMinDelay(delay, TimeUnit.MILLISECONDS)
            .build()
    }
}
