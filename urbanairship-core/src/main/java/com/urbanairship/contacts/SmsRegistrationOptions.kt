/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf

/**
 * Sms registration options.
 *
 * @property senderId The sender Id.
 */
public class SmsRegistrationOptions internal constructor(
    public val senderId: String
) : JsonSerializable {

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        SENDER_ID_KEY to senderId
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SmsRegistrationOptions

        return senderId == other.senderId
    }

    override fun hashCode(): Int = ObjectsCompat.hashCode(senderId)

    override fun toString(): String {
        return "SmsRegistrationOptions(senderId='$senderId')"
    }

    public companion object {

        private const val SENDER_ID_KEY = "sender_id"

        /**
         * Creates default options.
         * @param senderId The sender Id.
         * @return The sms options.
         */
        @JvmStatic
        public fun options(senderId: String): SmsRegistrationOptions {
            return SmsRegistrationOptions(senderId)
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): SmsRegistrationOptions {
            val senderId = value.optMap().opt(SENDER_ID_KEY).requireString()
            return SmsRegistrationOptions(senderId)
        }
    }
}
