/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Defines the scope of channels.
 */
public enum class Scope(private val value: String) : JsonSerializable {

    /**
     * App channels - Android, Amazon, and iOS.
     */
    APP("app"),

    /**
     * Web channels.
     */
    WEB("web"),

    /**
     * EMAIL channels.
     */
    EMAIL("email"),

    /**
     * SMS channels.
     */
    SMS("sms");

    override fun toString(): String = name.lowercase()

    override fun toJsonValue(): JsonValue = JsonValue.wrapOpt(value)

    public companion object {
        @JvmStatic
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): Scope {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.value == content } ?: throw JsonException("Invalid scope: $content")
        }
    }
}
