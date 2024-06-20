/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.analytics.EventType
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppFormResultEvent(
    forms: JsonValue?
) : InAppEvent {

    private val reportData = FormResultData(forms)

    override val eventType: EventType = EventType.IN_APP_FORM_RESULT
    override val data: JsonSerializable = reportData

    private data class FormResultData(
        val forms: JsonValue?
    ) : JsonSerializable {
        companion object {
            private const val FORMS = "forms"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(FORMS  to forms).toJsonValue()
    }
}
