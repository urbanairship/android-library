package com.urbanairship.messagecenter

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.android.layout.analytics.LayoutEventData
import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.android.layout.analytics.LayoutEventRecorderInterface
import com.urbanairship.android.layout.analytics.LayoutEventSource
import com.urbanairship.android.layout.analytics.MessageDisplayHistory
import com.urbanairship.android.layout.analytics.MessageDisplayHistoryStoreInterface
import com.urbanairship.android.layout.analytics.events.InAppDisplayEvent
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue
import com.urbanairship.meteredusage.MeteredUsageEventEntity
import com.urbanairship.meteredusage.MeteredUsageType
import java.time.Instant
import java.util.Date
import kotlin.time.Duration.Companion.minutes
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageAnalyticsTest {
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val clock = TestClock()

    private val eventRecorder: LayoutEventRecorderInterface = mockk(relaxed = true)
    private val historyStore: MessageDisplayHistoryStoreInterface = mockk(relaxed = true)

    private val messageId = LayoutEventMessageId.AirshipId("message-id", null)
    private val productId = "test-product"
    private val reportingContext = JsonValue.wrap("reporting")
    private val eventReportingContext = JsonValue.wrap("event-reporting")
    private val sessionId = "test-session-id"

    @Test
    public fun `test recordEvent with primary constructor`() {
        val messageId = LayoutEventMessageId.AirshipId("message-id", null)
        val eventSource = LayoutEventSource.AIRSHIP
        val layoutContext: LayoutData = mockk(relaxed = true)
        val event: LayoutEvent = mockk()

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        analytics.recordEvent(event, layoutContext)
        testDispatcher.scheduler.advanceUntilIdle()

        val eventDataSlot = slot<LayoutEventData>()
        verify(exactly = 1) { eventRecorder.recordEvent(capture(eventDataSlot)) }

        val capturedData = eventDataSlot.captured
        assertEquals(event, capturedData.event)
        assertEquals(eventReportingContext, capturedData.context?.reportingContext)
        assertEquals(eventSource, capturedData.source)
        assertEquals(messageId, capturedData.messageId)
    }

    @Test
    public fun `test recordEvent with secondary constructor`() {
        val message = Message(
            id = "message-id",
            title = "title",
            bodyUrl = "test://url",
            sentDate = Instant.now(),
            expirationDate = null,
            isUnread = true,
            extras = null,
            contentType = Message.ContentType.Html,
            messageUrl = "test://url.message",
            reporting = JsonValue.wrap("reporting"),
            rawMessageJson = JsonValue.NULL,
            isDeletedClient = false,
            associatedData = null
        )

        val layoutContext: LayoutData = mockk(relaxed = true)
        val event: LayoutEvent = mockk()

        val analytics = MessageAnalytics(
            messageId = messageId,
            productId = productId,
            reportingContext = reportingContext,
            eventReportingContext = eventReportingContext,
            eventRecorder = eventRecorder,
            eventSource = LayoutEventSource.AIRSHIP,
            displayHistoryStore = historyStore,
            dispatcher = testDispatcher,
            clock = clock,
            sessionId = sessionId
        )
        testDispatcher.scheduler.advanceUntilIdle()

        analytics.recordEvent(event, layoutContext)
        testDispatcher.scheduler.advanceUntilIdle()

        val eventDataSlot = slot<LayoutEventData>()
        verify(exactly = 1) { eventRecorder.recordEvent(capture(eventDataSlot)) }

        val capturedData = eventDataSlot.captured
        assertEquals(event, capturedData.event)
        assertEquals(eventReportingContext, capturedData.context?.reportingContext)
        assertEquals(LayoutEventSource.AIRSHIP, capturedData.source)
        assertEquals(LayoutEventMessageId.AirshipId(message.id, null), capturedData.messageId)
    }


    @Test
    fun `test init no history`() = testScope.runTest {
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory()

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        val eventData = captureRecordEvent(analytics)

        analytics.recordEvent(mockk(), null) // Trigger event recording to capture context
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, eventData.captured.context?.display?.isFirstDisplay)
        assertEquals(true, eventData.captured.context?.display?.isFirstDisplayTriggerSessionId)
    }

    @Test
    fun `test init with history different session`() = testScope.runTest {
        val lastDisplay = MessageDisplayHistory.LastDisplay("last-session")
        val lastImpression = MessageDisplayHistory.LastImpression(Instant.ofEpochMilli(100), "impression-session")

        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory(
            lastDisplay = lastDisplay,
            lastImpression = lastImpression
        )

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        val eventData = captureRecordEvent(analytics)
        analytics.recordEvent(mockk(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, eventData.captured.context?.display?.isFirstDisplay)
        assertEquals(false, eventData.captured.context?.display?.isFirstDisplayTriggerSessionId)
    }

    @Test
    fun `test init with history same session`() = testScope.runTest {
        val lastDisplay = MessageDisplayHistory.LastDisplay("last-session")
        val lastImpression = MessageDisplayHistory.LastImpression(Instant.ofEpochMilli(100), "last-session") // same as last display
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory(
            lastDisplay = lastDisplay,
            lastImpression = lastImpression
        )

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        val eventData = captureRecordEvent(analytics)

        analytics.recordEvent(mockk(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(false, eventData.captured.context?.display?.isFirstDisplay)
        assertEquals(true, eventData.captured.context?.display?.isFirstDisplayTriggerSessionId)
    }

    @Test
    fun `test record generic event`() = testScope.runTest {
        coEvery { historyStore.get(any()) } returns MessageDisplayHistory()
        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        analytics.recordEvent(mockk(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { eventRecorder.recordEvent(any()) }
        coVerify(exactly = 0) { eventRecorder.recordImpressionEvent(any()) }
        // get is called in init, but not again. set is never called.
        coVerify(exactly = 1) { historyStore.get(any()) }
        coVerify(exactly = 0) { historyStore.set(any(), any()) }
    }

    @Test
    fun `test record first impression`() = testScope.runTest {
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory()
        clock.currentTime = Instant.ofEpochMilli(100L)

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        val impressionSlot = slot<MeteredUsageEventEntity>()
        coVerify { eventRecorder.recordImpressionEvent(capture(impressionSlot)) }
        assertEquals("message-id", impressionSlot.captured.entityId)
        assertEquals(MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION, impressionSlot.captured.type)
        assertEquals(productId, impressionSlot.captured.product)
        assertEquals(reportingContext, impressionSlot.captured.reportingContext)
        assertEquals(Instant.ofEpochMilli(100), impressionSlot.captured.timestamp)

        val historySlot = slot<MessageDisplayHistory>()
        coVerify { historyStore.set(capture(historySlot), "message-id") }
        assertEquals(Instant.ofEpochMilli(100), historySlot.captured.lastImpression?.date)
        assertEquals(sessionId, historySlot.captured.lastImpression?.triggerSessionId)
        assertEquals(sessionId, historySlot.captured.lastDisplay?.triggerSessionId)
    }

    @Test
    fun `test record impression within session length`() = testScope.runTest {
        val lastImpression = MessageDisplayHistory.LastImpression(date = Instant.ofEpochMilli(100), triggerSessionId = "some-other-session")
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory(lastImpression = lastImpression)

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        // Not enough time has passed
        clock.currentTime = Instant.ofEpochMilli(100L + 30.minutes.inWholeMilliseconds - 1)

        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { eventRecorder.recordImpressionEvent(any()) }

        val historySlot = slot<MessageDisplayHistory>()
        coVerify { historyStore.set(capture(historySlot), "message-id") }

        // Last impression should be unchanged
        assertEquals(lastImpression, historySlot.captured.lastImpression)
        // Last display should be updated
        assertEquals(sessionId, historySlot.captured.lastDisplay?.triggerSessionId)
    }

    @Test
    fun `test record impression after session length`() = testScope.runTest {
        val lastImpression = MessageDisplayHistory.LastImpression(date = Instant.ofEpochMilli(100), triggerSessionId = "some-other-session")
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory(lastImpression = lastImpression)

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        // Enough time has passed
        clock.currentTime = Instant.ofEpochMilli(100L + 30.minutes.inWholeMilliseconds)

        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { eventRecorder.recordImpressionEvent(any()) }

        val historySlot = slot<MessageDisplayHistory>()
        coVerify { historyStore.set(capture(historySlot), "message-id") }

        // Last impression should be updated
        assertEquals(clock.currentTime, historySlot.captured.lastImpression?.date)
        assertEquals(sessionId, historySlot.captured.lastImpression?.triggerSessionId)
        // Last display should be updated
        assertEquals(sessionId, historySlot.captured.lastDisplay?.triggerSessionId)
    }

    @Test
    fun `test impression updates display context`() = testScope.runTest {
        // Init with no history
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory()
        clock.currentTime = Instant.ofEpochMilli(100)

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        // --- First Event ---
        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify it was recorded and capture what was stored
        val eventDataSlot = slot<LayoutEventData>()
        val historySlot = slot<MessageDisplayHistory>()
        coVerify(exactly = 1) { eventRecorder.recordEvent(capture(eventDataSlot)) }
        coVerify(exactly = 1) { historyStore.set(capture(historySlot), "message-id") }
        verify(exactly = 1) { eventRecorder.recordImpressionEvent(any()) }

        // The first event should have isFirstDisplay = true
        assertEquals(true, eventDataSlot.captured.context?.display?.isFirstDisplay)
        val firstHistory = historySlot.captured

        // --- Second Event ---
        // Setup mocks to return the history from the first event
        coEvery { historyStore.get("message-id") } returns firstHistory
        clock.currentTime = Instant.ofEpochMilli(200)

        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify a second event was recorded and capture it
        coVerify(exactly = 2) { eventRecorder.recordEvent(any()) }

        // The second event's context should now have isFirstDisplay = false
        assertEquals(false, eventDataSlot.captured.context?.display?.isFirstDisplay)

        // And history should be set again
        coVerify(exactly = 2) { historyStore.set(any(), "message-id") }
        verify(exactly = 1) { eventRecorder.recordImpressionEvent(any()) }
    }

    @Test
    fun `test productId from message`() = testScope.runTest {
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory()

        val message = Message(
            id = "message-id",
            title = "title",
            bodyUrl = "test://url",
            sentDate = Instant.now(),
            expirationDate = null,
            isUnread = true,
            extras = null,
            contentType = Message.ContentType.Html,
            messageUrl = "test://url.message",
            reporting = JsonValue.wrap("reporting"),
            rawMessageJson = JsonValue.parseString("""{"product_id": "message-product-id"}"""),
            isDeletedClient = false,
            associatedData = null
        )

        val analytics = MessageAnalytics(
            message = message,
            eventRecorder = eventRecorder,
            displayHistoryStore = historyStore,
            dispatcher = testDispatcher,
            clock = clock
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger impression
        clock.currentTime = Instant.ofEpochMilli(100L)
        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        val impressionSlot = slot<MeteredUsageEventEntity>()
        coVerify { eventRecorder.recordImpressionEvent(capture(impressionSlot)) }
        assertEquals("message-product-id", impressionSlot.captured.product)
    }

    @Test
    fun `test default productId`() = testScope.runTest {
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory()

        val message = Message(
            id = "message-id",
            title = "title",
            bodyUrl = "test://url",
            sentDate = Instant.now(),
            expirationDate = null,
            isUnread = true,
            extras = null,
            contentType = Message.ContentType.Html,
            messageUrl = "test://url.message",
            reporting = JsonValue.wrap("reporting"),
            rawMessageJson = JsonValue.parseString("""{}"""),
            isDeletedClient = false,
            associatedData = null
        )

        val analytics = MessageAnalytics(
            message = message,
            eventRecorder = eventRecorder,
            displayHistoryStore = historyStore,
            dispatcher = testDispatcher,
            clock = clock
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // Trigger impression
        clock.currentTime = Instant.ofEpochMilli(100L)
        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        val impressionSlot = slot<MeteredUsageEventEntity>()
        coVerify { eventRecorder.recordImpressionEvent(capture(impressionSlot)) }
        assertEquals("default_native_mc", impressionSlot.captured.product)
    }

    @Test
    fun `reporting_context is pulled out of message_reporting, falling back to the whole object`() {
        val context = JsonValue.parseString("""{"content_types": ["scene"], "experiment_id": ""}""")
        val withContext = JsonValue.parseString(
            """{"message_id": "xzlgYcEbEfGkBAAAChrOWw", "reporting_context": $context, "campaign_categories": []}"""
        )
        val withoutContext = JsonValue.parseString("""{"message_id": "xzlgYcEbEfGkBAAAChrOWw"}""")

        assertEquals(context, MessageAnalytics.reportingContext(withContext))
        assertEquals(withoutContext, MessageAnalytics.reportingContext(withoutContext))
        assertEquals(JsonValue.wrap("reporting"), MessageAnalytics.reportingContext(JsonValue.wrap("reporting")))
        assertNull(MessageAnalytics.reportingContext(null))
    }

    @Test
    fun `eventReportingContext flattens message_reporting into reporting_context`() {
        val cases = listOf(
            """{"message_id":"m","reporting_context":{"content_types":["scene"],"experiment_id":""},"campaign_categories":[]}""" to
                """{"message_id":"m","campaign_categories":[],"content_types":["scene"],"experiment_id":""}""",
            """{"message_id":"m","group_id":"g","reporting_context":{"message_id":"other","content_types":["scene"]}}""" to
                """{"message_id":"m","group_id":"g","content_types":["scene"]}""",
            """{"message_id":"m","group_id":"g"}""" to
                """{"message_id":"m","group_id":"g"}""",
            """{"message_id":"m","reporting_context":"text"}""" to
                """{"message_id":"m","reporting_context":"text"}""",
            """{"reporting_context":{"content_types":["scene"]}}""" to
                """{"content_types":["scene"]}"""
        )

        cases.forEach { (input, expected) ->
            assertEquals(
                input,
                JsonValue.parseString(expected),
                MessageAnalytics.eventReportingContext(JsonValue.parseString(input))
            )
        }
    }

    @Test
    fun `a message without message_reporting has no reporting context`() {
        assertNull(MessageAnalytics.reportingContext(null))
        assertNull(MessageAnalytics.eventReportingContext(null))
    }

    @Test
    fun `a message's events carry the flattened reporting context and its impression only the inner one`() = testScope.runTest {
        val context = JsonValue.parseString("""{"content_types": ["scene"]}""")
        val message = Message(
            id = "message-id",
            title = "title",
            bodyUrl = "test://url",
            sentDate = Instant.now(),
            expirationDate = null,
            isUnread = true,
            extras = null,
            contentType = Message.ContentType.Native(version = 1),
            messageUrl = "test://url.message",
            reporting = JsonValue.parseString("""{"message_id": "message-id", "reporting_context": $context}"""),
            rawMessageJson = JsonValue.NULL,
            isDeletedClient = false,
            associatedData = null
        )
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory()

        val analytics = MessageAnalytics(message, eventRecorder, historyStore, dispatcher = testDispatcher, clock = clock)
        testDispatcher.scheduler.advanceUntilIdle()
        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        val eventSlot = slot<LayoutEventData>()
        verify { eventRecorder.recordEvent(capture(eventSlot)) }
        assertEquals(
            JsonValue.parseString("""{"message_id": "message-id", "content_types": ["scene"]}"""),
            eventSlot.captured.context?.reportingContext
        )

        val impressionSlot = slot<MeteredUsageEventEntity>()
        coVerify { eventRecorder.recordImpressionEvent(capture(impressionSlot)) }
        assertEquals(context, impressionSlot.captured.reportingContext)
    }

    private fun createAnalytics(sessionId: String = this.sessionId): MessageAnalytics {
        return MessageAnalytics(
            messageId = messageId,
            productId = productId,
            reportingContext = reportingContext,
            eventReportingContext = eventReportingContext,
            eventRecorder = eventRecorder,
            eventSource = LayoutEventSource.AIRSHIP,
            displayHistoryStore = historyStore,
            dispatcher = testDispatcher,
            clock = clock,
            sessionId = sessionId
        )
    }

    private fun captureRecordEvent(analytics: MessageAnalytics): CapturingSlot<LayoutEventData> {
        val slot = slot<LayoutEventData>()
        coEvery { eventRecorder.recordEvent(capture(slot)) } just runs
        return slot
    }
}
