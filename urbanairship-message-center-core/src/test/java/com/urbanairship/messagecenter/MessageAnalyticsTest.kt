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
        assertEquals(reportingContext, capturedData.context?.reportingContext)
        assertEquals(eventSource, capturedData.source)
        assertEquals(messageId, capturedData.messageId)
    }

    @Test
    public fun `test recordEvent with secondary constructor`() {
        val message = Message(
            id = "message-id",
            title = "title",
            bodyUrl = "test://url",
            sentDate = Date(),
            expirationDate = null,
            isUnread = true,
            extras = null,
            contentType = Message.ContentType.HTML,
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
        assertEquals(message.reporting, capturedData.context?.reportingContext)
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
        val lastImpression = MessageDisplayHistory.LastImpression(100L, "impression-session")

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
        val lastImpression = MessageDisplayHistory.LastImpression(100L, "last-session") // same as last display
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
        clock.currentTimeMillis = 100L

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
        assertEquals(100L, impressionSlot.captured.timestamp)

        val historySlot = slot<MessageDisplayHistory>()
        coVerify { historyStore.set(capture(historySlot), "message-id") }
        assertEquals(100L, historySlot.captured.lastImpression?.date)
        assertEquals(sessionId, historySlot.captured.lastImpression?.triggerSessionId)
        assertEquals(sessionId, historySlot.captured.lastDisplay?.triggerSessionId)
    }

    @Test
    fun `test record impression within session length`() = testScope.runTest {
        val lastImpression = MessageDisplayHistory.LastImpression(date = 100L, triggerSessionId = "some-other-session")
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory(lastImpression = lastImpression)

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        // Not enough time has passed
        clock.currentTimeMillis = 100L + 30.minutes.inWholeMilliseconds - 1

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
        val lastImpression = MessageDisplayHistory.LastImpression(date = 100L, triggerSessionId = "some-other-session")
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory(lastImpression = lastImpression)

        val analytics = createAnalytics()
        testDispatcher.scheduler.advanceUntilIdle()

        // Enough time has passed
        clock.currentTimeMillis = 100L + 30.minutes.inWholeMilliseconds

        analytics.recordEvent(InAppDisplayEvent(), null)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { eventRecorder.recordImpressionEvent(any()) }

        val historySlot = slot<MessageDisplayHistory>()
        coVerify { historyStore.set(capture(historySlot), "message-id") }

        // Last impression should be updated
        assertEquals(clock.currentTimeMillis, historySlot.captured.lastImpression?.date)
        assertEquals(sessionId, historySlot.captured.lastImpression?.triggerSessionId)
        // Last display should be updated
        assertEquals(sessionId, historySlot.captured.lastDisplay?.triggerSessionId)
    }

    @Test
    fun `test impression updates display context`() = testScope.runTest {
        // Init with no history
        coEvery { historyStore.get("message-id") } returns MessageDisplayHistory()
        clock.currentTimeMillis = 100

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
        clock.currentTimeMillis = 200

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

    private fun createAnalytics(sessionId: String = this.sessionId): MessageAnalytics {
        return MessageAnalytics(
            messageId = messageId,
            productId = productId,
            reportingContext = reportingContext,
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
