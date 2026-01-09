/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import java.util.Locale

public enum class ToggleType(
    private val value: String
) {
    SWITCH("switch"),
    CHECKBOX("checkbox");

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): ToggleType {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown ToggleType value: $value")
        }
    }
}
