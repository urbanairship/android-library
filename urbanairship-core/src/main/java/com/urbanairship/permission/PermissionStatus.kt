/* Copyright Airship and Contributors */
package com.urbanairship.permission

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Permissions status.
 */
public enum class PermissionStatus(public val value: String) : JsonSerializable {

    /**
     * Granted status.
     */
    GRANTED("granted"),

    /**
     * Denied status.
     */
    DENIED("denied"),

    /**
     * Not determined status.
     */
    NOT_DETERMINED("not_determined");

    override fun toString(): String = name.lowercase()

    override fun toJsonValue(): JsonValue = JsonValue.wrapOpt(value)

    public companion object {

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): PermissionStatus {
            val content = value.requireString().lowercase()
            return fromString(content) ?: throw JsonException("Invalid permission status: $content")
        }

        internal fun fromString(value: String): PermissionStatus? {
            return entries.firstOrNull { it.value == value.lowercase() }
        }
    }
}
