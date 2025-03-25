/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import kotlin.jvm.Throws

/** Locale configuration for a phone number */
public class SmsLocale(
    /** Country locale code (two letters) */
    public val countryCode: String,

    /** Country phone code */
    public val prefix: String,

    /** Registration info */
    public val registration: Registration?
): JsonSerializable {

    /** Phone number sender info */
    public class Registration(
        /** Registration type */
        public val type: RegistrationType,

        /** Sender ID */
        public val senderId: String
    ): JsonSerializable {

        public enum class RegistrationType(private val json: String): JsonSerializable {
            OPT_IN("opt_in");

            override fun toJsonValue(): JsonValue = JsonValue.wrap(json)

            internal companion object {
                @Throws(JsonException::class, NoSuchElementException::class)
                fun fromJson(value: JsonValue): RegistrationType {
                    val content = value.requireString()
                    return entries.first { it.json == content }
                }
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            SENDER_ID to senderId
        ).toJsonValue()

        internal companion object {
            private const val TYPE = "type"
            private const val SENDER_ID = "sender_id"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Registration {
                val content = value.requireMap()

                return try {
                    Registration(
                        type = RegistrationType.fromJson(content.require(TYPE)),
                        senderId = content.requireField(SENDER_ID)
                    )
                } catch (ex: NoSuchElementException) {
                    throw JsonException("Invalid value of the registration type", ex)
                }
            }
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        COUNTRY_CODE to countryCode,
        PREFIX to prefix,
        REGISTRATION to registration
    ).toJsonValue()

    internal companion object {
        private const val COUNTRY_CODE = "country_code"
        private const val PREFIX = "prefix"
        private const val REGISTRATION = "registration"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): SmsLocale {
            val content = value.requireMap()

            return SmsLocale(
                countryCode = content.requireField(COUNTRY_CODE),
                prefix = content.requireField(PREFIX),
                registration = content.get(REGISTRATION)?.let(Registration::fromJson)
            )
        }
    }
}
