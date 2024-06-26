/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppButtonTapEvent(
    identifier: String,
    reportingMetadata: JsonValue?
) : InAppEvent {

    private var tapData = ButtonTapData(identifier, reportingMetadata)

    override val eventType: EventType = EventType.IN_APP_BUTTON_TAP
    override val data: JsonSerializable = tapData

    companion object {
        private const val NAME = "in_app_button_tap"
    }

    private data class ButtonTapData(
        val identifier: String,
        val metadata: JsonValue?
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "button_identifier"
            private const val REPORTING_METADATA = "reporting_metadata"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            REPORTING_METADATA to metadata
        ).toJsonValue()
    }
}
