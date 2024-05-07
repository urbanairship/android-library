package com.urbanairship.iam.analytics

import com.urbanairship.UALog
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.Event
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal data class InAppEventData(
    val event: InAppEvent,
    val context: InAppEventContext?,
    val source: InAppEventSource,
    val messageID: InAppEventMessageID,
    val renderedLocale: Map<String, JsonValue>
)

internal interface InAppEventRecorderInterface {
    fun recordEvent(event: InAppEventData)
}

internal class InAppEventRecorder(
    private val analytics: Analytics
): InAppEventRecorderInterface {

    override fun recordEvent(event: InAppEventData) {
        try {
            val trackEvent = AnalyticsEvent(event, analytics)
            analytics.addEvent(trackEvent)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to record event $event" }
        }
    }
}

private data class AnalyticsEvent(
    val name: String,
    val identifier: InAppEventMessageID,
    val source: InAppEventSource,
    val context: InAppEventContext?,
    val conversionSendID: String?,
    val conversionPushMetadata: String?,
    val renderedLocale: Map<String, JsonValue>?,
    val baseData: JsonSerializable?
) : Event() {
    constructor(eventData: InAppEventData, analytics: Analytics) :
            this(
                name = eventData.event.name,
                identifier = eventData.messageID,
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
