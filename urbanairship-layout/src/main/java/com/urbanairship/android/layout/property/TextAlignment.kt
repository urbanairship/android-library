/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.view.Gravity
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class TextAlignment(
    public val value: String,
    public val gravity: Int
) {

    START("start", Gravity.START),
    END("end", Gravity.END),
    CENTER("center", Gravity.CENTER);

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun from(value: JsonValue): TextAlignment {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown Text Alignment value: $value")
        }
    }
}
