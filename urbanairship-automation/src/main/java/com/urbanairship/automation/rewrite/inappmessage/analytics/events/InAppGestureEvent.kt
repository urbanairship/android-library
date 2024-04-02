package com.urbanairship.automation.rewrite.inappmessage.analytics.events

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppGestureEvent(
    identifier: String,
    metadata: JsonValue?
) : InAppEvent {

    private val reportingData = GestureData(identifier, metadata)

    override val name: String = "in_app_gesture"
    override val data: JsonSerializable = reportingData

    private data class GestureData(
        val identifier: String,
        val metadata: JsonValue?
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "gesture_identifier"
            private const val REPORTING_METADATA = "reporting_metadata"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            REPORTING_METADATA to metadata
        ).toJsonValue()
    }
}
