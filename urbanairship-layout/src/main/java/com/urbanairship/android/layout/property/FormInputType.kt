/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.text.InputType
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class FormInputType(
    private val value: String,
    @JvmField public val typeMask: Int
) {

    EMAIL(
        value = "email",
        typeMask = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    ),
    NUMBER(
        value = "number",
        typeMask = InputType.TYPE_CLASS_NUMBER
    ),
    SMS(
        value = "sms",
        typeMask = InputType.TYPE_CLASS_NUMBER
    ),
    TEXT(
        value = "text",
        typeMask = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    ),
    TEXT_MULTILINE(
        value = "text_multiline",
        typeMask = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    );


    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): FormInputType {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown Form Input Type value: $value")

        }
    }
}
