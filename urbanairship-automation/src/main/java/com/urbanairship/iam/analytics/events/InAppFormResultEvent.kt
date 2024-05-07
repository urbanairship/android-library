package com.urbanairship.iam.analytics.events

import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppFormResultEvent(
    forms: JsonValue?
) : InAppEvent {

    private val reportData = FormResultData(forms)

    override val name: String = "in_app_form_result"
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
