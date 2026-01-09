/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class Direction(
    private val value: String
) {
    VERTICAL("vertical"),
    HORIZONTAL("horizontal");

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): Direction {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown Direction value: $value")
        }
    }
}
