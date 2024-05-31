/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Channel types.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class ChannelType(public val json: String): JsonSerializable {

    /**
     * Open channel
     */
    OPEN("open"),

    /**
     * Sms channel
     */
    SMS("sms"),

    /**
     * Email channel
     */
    EMAIL("email");

    override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): ChannelType {
            val content = value.requireString().lowercase()
            return entries.firstOrNull { it.json == content }
                ?: throw JsonException("invalid channel type $content")
        }
    }

}
