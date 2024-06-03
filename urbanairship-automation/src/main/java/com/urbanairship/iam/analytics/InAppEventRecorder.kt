/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.Event
import com.urbanairship.iam.analytics.events.InAppEvent
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

internal data class InAppEventData(
    val event: InAppEvent,
    val context: InAppEventContext?,
    val source: InAppEventSource,
    val messageId: InAppEventMessageId,
    val renderedLocale: JsonValue?
)

internal interface InAppEventRecorderInterface {
    fun recordEvent(event: InAppEventData)
    fun recordImpressionEvent(event: MeteredUsageEventEntity)
}

internal class InAppEventRecorder(
    private val analytics: Analytics,
    private val meteredUsage: AirshipMeteredUsage,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
): InAppEventRecorderInterface {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    override fun recordEvent(event: InAppEventData) {
        try {
            val trackEvent = AnalyticsEvent(event, analytics)
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
    val name: String,
    val identifier: InAppEventMessageId,
    val source: InAppEventSource,
    val context: InAppEventContext?,
    val conversionSendID: String?,
    val conversionPushMetadata: String?,
    val renderedLocale: JsonValue?,
    val baseData: JsonSerializable?
) : Event() {
    constructor(eventData: InAppEventData, analytics: Analytics) :
            this(
                name = eventData.event.name,
                identifier = eventData.messageId,
                source = eventData.source,
                context = eventData.context,
                conversionSendID = analytics.conversionSendId,
                conversionPushMetadata = analytics.conversionMetadata,
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

    override fun getType(): String = name

    override fun getEventData(): JsonMap {
        return JsonMap.newBuilder()
            .putAll(baseData?.toJsonValue()?.requireMap() ?: jsonMapOf())
            .put(IDENTIFIER, identifier)
            .put(SOURCE, source)
            .putOpt(CONTEXT, context)
            .putOpt(CONVERSION_SEND_ID, conversionSendID)
            .putOpt(CONVERSION_PUSH_METADATA, conversionPushMetadata)
            .putOpt(RENDERED_LOCALE, renderedLocale)
            .build()
    }
}
