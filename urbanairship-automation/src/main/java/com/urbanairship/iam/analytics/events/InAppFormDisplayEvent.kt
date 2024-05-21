/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics.events

import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

internal class InAppFormDisplayEvent(
    identifier: String,
    formType: String,
    responseType: String?
) : InAppEvent {

    constructor(info: FormInfo) : this(info.identifier, info.formType, info.formResponseType)
    private val formData = FormDisplayData(identifier, formType, responseType)

    override val name: String = "in_app_form_display"
    override val data: JsonSerializable = formData

    private data class FormDisplayData(
        val identifier: String,
        val formType: String,
        val responseType: String?
    ) : JsonSerializable {
        companion object {
            private const val IDENTIFIER = "form_identifier"
            private const val FORM_TYPE = "form_type"
            private const val RESPONSE_TYPE = "form_response_type"
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            IDENTIFIER to identifier,
            FORM_TYPE to formType,
            RESPONSE_TYPE to responseType
        ).toJsonValue()
    }
}
