/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppPageActionEvent(
    identifier: String,
    metadata: JsonValue?
) : InAppEvent {
    private val reportingData = PageActionData(identifier, metadata)

    override val eventType: EventType = EventType.IN_APP_PAGE_ACTION

    override val data: JsonSerializable = reportingData

    private data class PageActionData(
        val identifier: String,
        val metadata: JsonValue?
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "action_identifier"
            private const val REPORTING_METADATA = "reporting_metadata"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            REPORTING_METADATA to metadata
        ).toJsonValue()
    }
}
