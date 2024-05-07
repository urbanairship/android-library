package com.urbanairship.iam.content

import androidx.annotation.VisibleForTesting
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

public class Custom @VisibleForTesting internal constructor(
    public val value: JsonValue
) : JsonSerializable {
    public companion object {
        private const val CUSTOM_KEY = "custom"

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
