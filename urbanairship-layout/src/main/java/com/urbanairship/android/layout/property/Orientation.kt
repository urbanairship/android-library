/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class Orientation(
    private val value: String
) {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(json: JsonValue): Orientation {
            val value = json.requireString().lowercase()

            return entries.firstOrNull { it.value == value }
                ?: throw JsonException("Unknown Orientation value: $value")
        }
    }
}
