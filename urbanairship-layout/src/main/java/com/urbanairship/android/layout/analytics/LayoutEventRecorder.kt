/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.analytics

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.ConversionData
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.EventType
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.meteredusage.MeteredUsageEventEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class LayoutEventData(
    val event: LayoutEvent,
    val context: LayoutEventContext?,
    val source: LayoutEventSource,
    val messageId: LayoutEventMessageId,
    val renderedLocale: JsonValue?
)

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LayoutEventRecorderInterface {
    public fun recordEvent(event: LayoutEventData)
    public fun recordImpressionEvent(event: MeteredUsageEventEntity)
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LayoutEventRecorder(
    private val analytics: Analytics,
    private val meteredUsage: AirshipMeteredUsage,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
): LayoutEventRecorderInterface {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    override fun recordEvent(event: LayoutEventData) {
        try {
            val trackEvent = AnalyticsEvent(event)
            analytics.addEvent(trackEvent)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to record event $event" }
        }
    }

    override fun recordImpressionEvent(event: MeteredUsageEventEntity) {
        scope.launch { meteredUsage.addEvent(event) }
    }

}

private data class AnalyticsEvent(
    val eventType: EventType,
    val identifier: LayoutEventMessageId,
    val source: LayoutEventSource,
    val context: LayoutEventContext?,
    val renderedLocale: JsonValue?,
    val baseData: JsonSerializable?
) : Event() {
    constructor(eventData: LayoutEventData) :
            this(
                eventType = eventData.event.eventType,
                identifier = eventData.messageId,
                source = eventData.source,
                context = eventData.context,
                renderedLocale = eventData.renderedLocale,
                baseData = eventData.event.data
            )
    companion object {
        private const val IDENTIFIER = "id"
        private const val SOURCE = "source"
        private const val CONTEXT = "context"
        private const val CONVERSION_SEND_ID = "conversion_send_id"
        private const val CONVERSION_PUSH_METADATA = "conversion_metadata"
        private const val RENDERED_LOCALE = "rendered_locale"
    }

    override val type: EventType = eventType

    override fun getEventData(context: Context, conversionData: ConversionData): JsonMap {
        return JsonMap.newBuilder()
            .putAll(baseData?.toJsonValue()?.requireMap() ?: jsonMapOf())
            .put(IDENTIFIER, identifier)
            .put(SOURCE, source)
            .putOpt(CONTEXT, this.context)
            .putOpt(CONVERSION_SEND_ID, conversionData.conversionSendId)
            .putOpt(CONVERSION_PUSH_METADATA, conversionData.conversionMetadata)
            .putOpt(RENDERED_LOCALE, renderedLocale)
            .build()
    }
}
