package com.urbanairship.messagecenter

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.analytics.LayoutEventContext
import com.urbanairship.android.layout.analytics.LayoutEventData
import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.android.layout.analytics.LayoutEventRecorder
import com.urbanairship.android.layout.analytics.LayoutEventRecorderInterface
import com.urbanairship.android.layout.analytics.LayoutEventSource
import com.urbanairship.android.layout.analytics.LayoutMessageAnalyticsInterface
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.android.layout.analytics.makeContext
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue

internal class MessageAnalytics(
    private val messageId: LayoutEventMessageId,
    private val reportingContext: JsonValue?,
    private val eventRecorder: LayoutEventRecorderInterface,
    private val eventSource: LayoutEventSource,
): LayoutMessageAnalyticsInterface {

    constructor(
        message: Message,
        eventRecorder: LayoutEventRecorderInterface
    ): this(
        messageId = LayoutEventMessageId.AirshipId(
            identifier = message.id,
            campaigns = null
        ),
        reportingContext = message.reporting,
        eventRecorder = eventRecorder,
        eventSource = LayoutEventSource.AIRSHIP
    )

    override fun recordEvent(
        event: LayoutEvent, layoutContext: LayoutData?
    ) {
        val data = LayoutEventData(
            event = event,
            context = LayoutEventContext.makeContext(
                reportingContext = reportingContext,
                experimentResult = null,
                layoutContext = layoutContext,
                displayContext = null //TODO: we probably need to fill it
            ),
            source = eventSource,
            messageId = messageId,
            renderedLocale = null
        )

        eventRecorder.recordEvent(data)
    }
}
