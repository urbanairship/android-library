/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.view.Gravity
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class HorizontalPosition(
    private val value: String,
    @JvmField public val gravity: Int
) {

    START("start", Gravity.START),
    END("end", Gravity.END),
    CENTER("center", Gravity.CENTER_HORIZONTAL);

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun from(value: JsonValue): HorizontalPosition {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown HorizontalPosition value: $value")
        }
    }
}
