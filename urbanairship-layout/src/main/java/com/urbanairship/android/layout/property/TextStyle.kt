/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

public enum class TextStyle(
    private val value: String
) {
    BOLD("bold"),
    ITALIC("italic"),
    UNDERLINE("underlined");

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @Throws(JsonException::class)
        public fun from(value: JsonValue): TextStyle {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown Text Style value: $value")
        }
    }
}
