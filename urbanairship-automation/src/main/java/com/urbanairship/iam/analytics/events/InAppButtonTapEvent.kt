package com.urbanairship.iam.analytics.events

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppButtonTapEvent(
    identifier: String,
    reportingMetadata: JsonValue?
) : InAppEvent {

    private var tapData = ButtonTapData(identifier, reportingMetadata)

    override val name: String = NAME
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
