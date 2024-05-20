package com.urbanairship.iam.legacy

import com.urbanairship.iam.analytics.InAppEventData
import com.urbanairship.iam.analytics.InAppEventMessageID
import com.urbanairship.iam.analytics.InAppEventRecorderInterface
import com.urbanairship.iam.analytics.InAppEventSource
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.jsonMapOf

internal class LegacyAnalytics(private val eventRecorder: InAppEventRecorderInterface) {
    fun recordReplacedEvent(scheduleID: String, replacementID: String) {
        eventRecorder.recordEvent(
            InAppEventData(
                event = LegacyResolutionEvent.replaced(replacementID),
                context = null,
                messageID = InAppEventMessageID.Legacy(scheduleID),
                source = InAppEventSource.AIRSHIP,
                renderedLocale = null
            )
        )
    }

    fun recordDirectOpenEvent(scheduleID: String) {
        eventRecorder.recordEvent(
            InAppEventData(
                event = LegacyResolutionEvent.directOpen(),
                context = null,
                messageID = InAppEventMessageID.Legacy(scheduleID),
                source = InAppEventSource.AIRSHIP,
                renderedLocale = null
            )
        )
    }
}

private class LegacyResolutionEvent(
    reportData: JsonSerializable?
) : InAppEvent {

    override val name: String = "in_app_resolution"
    override val data: JsonSerializable? = reportData

    internal companion object {

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
