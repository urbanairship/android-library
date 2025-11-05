package com.urbanairship.android.layout.reporting

import com.urbanairship.json.JsonMap.Companion.newBuilder
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

public data class FormInfo public constructor(
    public val identifier: String,
    public val formType: String,
    public val formResponseType: String?,
    public val isFormSubmitted: Boolean?
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        KEY_IDENTIFIER to identifier,
        KEY_FORM_RESPONSE_TYPE to formResponseType,
        KEY_FORM_TYPE to formType,
        KEY_IS_FORM_SUBMITTED to isFormSubmitted
    ).toJsonValue()

    private companion object {
        private const val KEY_IDENTIFIER = "identifier"
        private const val KEY_FORM_RESPONSE_TYPE = "formResponseType"
        private const val KEY_FORM_TYPE = "formType"
        private const val KEY_IS_FORM_SUBMITTED = "isFormSubmitted"
    }
}
