/* Copyright Airship and Contributors */

package com.urbanairship.iam.legacy

import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.analytics.LayoutEventData
import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.android.layout.analytics.LayoutEventRecorderInterface
import com.urbanairship.android.layout.analytics.LayoutEventSource
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.jsonMapOf

internal class LegacyAnalytics(private val eventRecorder: LayoutEventRecorderInterface) {
    fun recordReplacedEvent(scheduleID: String, replacementID: String) {
        eventRecorder.recordEvent(
            LayoutEventData(
                event = LegacyResolutionEvent.replaced(replacementID),
                context = null,
                messageId = LayoutEventMessageId.Legacy(scheduleID),
                source = LayoutEventSource.AIRSHIP,
                renderedLocale = null
            )
        )
    }

    fun recordDirectOpenEvent(scheduleID: String) {
        eventRecorder.recordEvent(
            LayoutEventData(
                event = LegacyResolutionEvent.directOpen(),
                context = null,
                messageId = LayoutEventMessageId.Legacy(scheduleID),
                source = LayoutEventSource.AIRSHIP,
                renderedLocale = null
            )
        )
    }
}

private class LegacyResolutionEvent(
    reportData: JsonSerializable?
) : LayoutEvent {

    override val eventType: EventType = EventType.IN_APP_RESOLUTION
    override val data: JsonSerializable? = reportData

    companion object {

        private const val RESOLUTION_TYPE = "type"
        private const val REPLACED = "replaced"
        private const val REPLACEMENT_ID = "replacement_id"
        private const val DIRECT_OPEN = "direct_open"

        fun replaced(replacementID: String): LegacyResolutionEvent {
            return LegacyResolutionEvent(
                jsonMapOf(
                    RESOLUTION_TYPE to REPLACED, REPLACEMENT_ID to replacementID
                )
            )
        }

        fun directOpen(): LegacyResolutionEvent {
            return LegacyResolutionEvent(
                jsonMapOf(
                    RESOLUTION_TYPE to DIRECT_OPEN
                )
            )
        }
    }


}
