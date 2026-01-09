/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.view.Gravity
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class VerticalPosition(
    private val value: String,
    public val gravity: Int
) {
    TOP("top", Gravity.TOP),
    BOTTOM("bottom", Gravity.BOTTOM),
    CENTER("center", Gravity.CENTER_VERTICAL);

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(json: JsonValue): VerticalPosition {
            val value = json.requireString().lowercase()

            return entries.firstOrNull { it.value == value }
                ?: throw JsonException("Unknown VerticalPosition value: $value")
        }
    }
}
