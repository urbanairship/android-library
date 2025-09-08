/* Copyright Airship and Contributors */

package com.urbanairship.iam.content

import androidx.annotation.VisibleForTesting
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Custom In-App content.
 */
public class Custom @VisibleForTesting internal constructor(
    public val value: JsonValue
) : JsonSerializable {

    public fun copy(value: JsonValue = this.value): Custom {
        return Custom(value)
    }

    public companion object {

        /**
         * Parses a json value.
         *
         * @param value The json value.
         * @return A custom display content instance.
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): Custom {
            return Custom(value)
        }
    }

    override fun toJsonValue(): JsonValue = value

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Custom

        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }
}
