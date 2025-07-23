/* Copyright Airship and Contributors */
package com.urbanairship.permission

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Device permissions.
 */
public enum class Permission(public val value: String) : JsonSerializable {

    // Display notifications
    DISPLAY_NOTIFICATIONS("display_notifications"),

    // Access location
    LOCATION("location");

    override fun toString(): String = name.lowercase()

    override fun toJsonValue(): JsonValue = JsonValue.wrapOpt(value)

    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): Permission {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.value == content }
                ?: throw JsonException("Invalid permission: $value")
        }
    }
}
