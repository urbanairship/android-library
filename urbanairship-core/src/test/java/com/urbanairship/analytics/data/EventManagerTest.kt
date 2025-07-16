package com.urbanairship.analytics.data

import com.urbanairship.BaseTestCase
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.TestApplication
import com.urbanairship.TestClock
import com.urbanairship.analytics.AirshipEventData
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.EventType
import com.urbanairship.analytics.data.EventEntity.EventIdAndData
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.http.RequestException
import com.urbanairship.http.Response
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import java.net.HttpURLConnection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class EventManagerTest public constructor() : BaseTestCase() {

    private val mockDispatcher: JobDispatcher = mockk(relaxed = true)
    private val mockEventDao: EventDao = mockk(relaxed = true)
    private val mockClient: EventApiClient = mockk()
    private val mockActivityMonitor: ActivityMonitor = mockk()
    private val clock = TestClock()

    private val testAirshipRuntimeConfig = TestAirshipRuntimeConfig()
    private val dataStore: PreferenceDataStore = TestApplication.getApplication().preferenceDataStore

    private val eventManager: EventManager = EventManager(
        preferenceDataStore = dataStore,
        runtimeConfig = testAirshipRuntimeConfig,
        jobDispatcher = mockDispatcher,
        activityMonitor = mockActivityMonitor,
        eventDao = mockEventDao,
        apiClient = mockClient,
        clock = clock
    )

    private val testEvent = AirshipEventData(
        "testEvent",
        "session-testEvent",
        JsonMap.EMPTY_MAP.toJsonValue(),
        EventType.APP_FOREGROUND,
        System.currentTimeMillis()
    )

    /**
     * Tests adding an event after the next send time schedules an upload with a 10 second delay.
     */
    @Test
    public fun testAddEventAfterNextSendTime() {
        val entity = EventEntity(testEvent)

        every { mockDispatcher.dispatch(any()) } answers {
            val info: JobInfo = firstArg()

            assertEquals(EventManager.ACTION_SEND, info.action)
            assertEquals(10000L, info.minDelayMs)
        }

        eventManager.addEvent(testEvent, Event.Priority.NORMAL)
        // Verify we add an event.
        verify(exactly = 1) { mockEventDao.insert(entity) }

        // Check it schedules an upload
        verify { mockDispatcher.dispatch(any()) }
    }

    /**
     * Tests adding an event  before the next send time schedules an upload with the remaining delay.
     */
    @Test
    public fun testAddEventBeforeNextSendTime() {
        // Set the last send time to the current time so the next send time is minBatchInterval
        dataStore.put(EventManager.LAST_SEND_KEY, clock.currentTimeMillis)

        // Set the minBatchInterval to 20 seconds
        dataStore.put(EventManager.MIN_BATCH_INTERVAL_KEY, 20000)

        // Check it schedules an upload with a time greater than 10 seconds
        every { mockDispatcher.dispatch(any()) } answers {
            val info: JobInfo = firstArg()
            assertEquals(EventManager.ACTION_SEND, info.action)
            assertEquals(20000L, info.minDelayMs)
        }

        eventManager.addEvent(testEvent, Event.Priority.NORMAL)

        verify { mockDispatcher.dispatch(any()) }
    }

    /**
     * Tests sending events
     */
    @Test
    public fun testSendingEvents() {
        val data = JsonValue.parseString("{ \"body\": \"firstEventBody\" }")
        val payload = EventIdAndData(1, "firstEvent", data)
        val events = listOf(payload)
        val eventPayloads = listOf(payload.data)

        val headers = mapOf("foo" to "bar")

        // Set up data manager to return 2 count for events.
        // Note: we only have one event, but it should only ask for one to upload
        // having it return 2 will make it schedule to upload events in the future
        every { mockEventDao.count() } returns 2

        // Return 200 bytes in size.  It should only be able to do 100 bytes so only
        // the first event.
        every { mockEventDao.databaseSize() } returns 200

        // Return the event when it asks for 1
        every { mockEventDao.getBatch(1) } returns events

        // Set the max batch size to 100
        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100)

        // Set up the response
        val eventResponse: EventResponse = mockk() {
            every { maxTotalSize } returns 200
            every { maxBatchSize } returns 300
            every { minBatchInterval } returns 100
        }

        // Return the response
        every { mockClient.sendEvents("some channel", eventPayloads, headers) } answers {
            Response(HttpURLConnection.HTTP_OK, eventResponse)
        }

        every { mockDispatcher.dispatch(any()) } answers {
            val info: JobInfo = firstArg()
            assertEquals(EventManager.ACTION_SEND, info.action)
        }

        // Start the upload process
        assertTrue(eventManager.uploadEvents("some channel", headers))

        // Check mockClients receives the events
        verify { mockClient.sendEvents("some channel", eventPayloads, headers) }

        // Check data manager deletes events
        verify { mockEventDao.deleteBatch(events) }

        // Verify responses are being saved
        assertEquals(200, dataStore.getInt(EventManager.MAX_TOTAL_DB_SIZE_KEY, 0))
        assertEquals(300, dataStore.getInt(EventManager.MAX_BATCH_SIZE_KEY, 0))
        assertEquals(100, dataStore.getInt(EventManager.MIN_BATCH_INTERVAL_KEY, 0))

        // Check it schedules an upload
        verify { mockDispatcher.dispatch(any()) }
    }

    /**
     * Test event batching only sends a max of 500 events.
     */
    @Test
    public fun testSendEventMaxCount() {
        // Make the match batch size greater than 500
        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100000)

        // Fake the resolver to act like it has more than 500 events
        every { mockEventDao.databaseSize() } returns 100000
        every { mockEventDao.count() } returns 1000

        eventManager.uploadEvents("some channel", emptyMap())

        // Verify it only asked for 500
        verify { mockEventDao.getBatch(500) }
    }

    /**
     * Test sending events when the upload fails.
     */
    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSendEventsFails() {
        val data = JsonValue.parseString("{ \"body\": \"firstEventBody\" }")
        val payload = EventIdAndData(1, "firstEvent", data)
        val events = listOf(payload)
        val eventPayloads = listOf(payload.data)

        val headers = mapOf("foo" to "bar")

        every { mockEventDao.count() } returns 1
        every { mockEventDao.databaseSize() } returns 100
        every { mockEventDao.getBatch(1) } returns events

        dataStore.put(EventManager.MAX_BATCH_SIZE_KEY, 100)

        every { mockClient.sendEvents("some channel", eventPayloads, headers) } answers {
            Response(HttpURLConnection.HTTP_BAD_REQUEST, mockk())
        }

        assertFalse(eventManager.uploadEvents("some channel", headers))

        // Check mockClient receives the events
        verify { mockClient.sendEvents("some channel", eventPayloads, headers) }

        // If it fails, it should skip deleting events
        verify(exactly = 0) { mockEventDao.deleteBatch(any()) }
    }

    /**
     * Test adding a region event schedules an upload immediately.
     */
    @Test
    public fun testAddingHighPriorityEvents() {
        // Set last send time to year 3005 so next send time is way in the future
        dataStore.put(EventManager.LAST_SEND_KEY, 32661446400000L)

        every { mockDispatcher.dispatch(any()) } answers {
            val info: JobInfo = firstArg()
            assertEquals(EventManager.ACTION_SEND, info.action)
            assertEquals(0L, info.minDelayMs)
        }

        eventManager.addEvent(testEvent, Event.Priority.HIGH)

        // Check it schedules an upload
        verify { mockDispatcher.dispatch(any()) }
    }

    /**
     * Test delete all.
     */
    @Test
    public fun testDeleteAll() {
        eventManager.deleteEvents()
        verify(exactly = 1) { mockEventDao.deleteAll() }
    }
}
