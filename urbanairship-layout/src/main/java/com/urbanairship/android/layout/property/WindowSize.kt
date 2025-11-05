/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class WindowSize(
    private val value: String
) {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(json: JsonValue): WindowSize {
            val value = json.requireString().lowercase()

            return entries.firstOrNull { it.value == value }
                ?: throw JsonException("Unknown WindowSize value: $value")
        }
    }
}
