package com.urbanairship.messagecenter

import com.urbanairship.android.layout.analytics.LayoutEventData
import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.android.layout.analytics.LayoutEventRecorderInterface
import com.urbanairship.android.layout.analytics.LayoutEventSource
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue
import java.util.Date
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

public class MessageAnalyticsTest {

    private val eventRecorder: LayoutEventRecorderInterface = mockk(relaxed = true)

    @Test
    public fun `test recordEvent with primary constructor`() {
        val messageId = LayoutEventMessageId.AirshipId("message-id", null)
        val reportingContext = JsonValue.wrap("reporting")
        val eventSource = LayoutEventSource.AIRSHIP
        val layoutContext: LayoutData = mockk(relaxed = true)
        val event: LayoutEvent = mockk()

        val analytics = MessageAnalytics(
            messageId = messageId,
            reportingContext = reportingContext,
            eventRecorder = eventRecorder,
            eventSource = eventSource
        )

        analytics.recordEvent(event, layoutContext)

        val eventDataSlot = slot<LayoutEventData>()
        verify { eventRecorder.recordEvent(capture(eventDataSlot)) }

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
            isDeletedClient = false
        )

        val layoutContext: LayoutData = mockk(relaxed = true)
        val event: LayoutEvent = mockk()

        val analytics = MessageAnalytics(
            message = message,
            eventRecorder = eventRecorder
        )

        analytics.recordEvent(event, layoutContext)

        val eventDataSlot = slot<LayoutEventData>()
        verify { eventRecorder.recordEvent(capture(eventDataSlot)) }

        val capturedData = eventDataSlot.captured
        assertEquals(event, capturedData.event)
        assertEquals(message.reporting, capturedData.context?.reportingContext)
        assertEquals(LayoutEventSource.AIRSHIP, capturedData.source)
        assertEquals(LayoutEventMessageId.AirshipId(message.id, null), capturedData.messageId)
    }
}
