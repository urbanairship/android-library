/* Copyright Airship and Contributors */

package com.urbanairship.iam.info

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Button layout type.
 */
public enum class InAppMessageButtonLayoutType(internal val json: String) : JsonSerializable {
    /**
     * Buttons are stacked.
     */
    STACKED("stacked"),

    /**
     * Buttons are displayed right next to each other.
     */
    JOINED("joined"),

    /**
     * Buttons are displayed with a space between them.
     */
    SEPARATE("separate");

    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): InAppMessageButtonLayoutType {
            val content = value.requireString()
            return entries.firstOrNull { it.json == content }
                ?: throw JsonException("Invalid button layout $content")
        }
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
}
