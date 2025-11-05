/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue

/**
 * Defines how a layout should be presented.
 */
public enum class PresentationType(private val value: String) {

    BANNER("banner"),
    MODAL("modal"),
    EMBEDDED("embedded");

    override fun toString(): String {
        return name.lowercase()
    }

    public companion object {

        @JvmStatic
        @Throws(JsonException::class)
        public fun from(value: JsonValue): PresentationType {
            val content = value.requireString().lowercase()

            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Unknown PresentationType value: $value")
        }
    }
}
