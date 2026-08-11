/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class VerticalEdge(private val value: String) {
    TOP("top"),
    BOTTOM("bottom");

    public val baseType: VerticalPosition
        get() = when (this) {
            TOP -> VerticalPosition.TOP
            BOTTOM -> VerticalPosition.BOTTOM
        }

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun from(value: JsonValue): VerticalEdge {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown VerticalEdge value: $value")
        }
    }
}
