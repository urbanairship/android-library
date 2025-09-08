/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/** Locale configuration for a phone number */
public class SmsLocale(
    /** Country locale code (two letters) */
    public val countryCode: String,

    /** Country phone code */
    public val prefix: String,

    /** Registration info */
    internal val registration: Registration?,

    /** Validation hints */
    internal val validationHints: ValidationHints?
): JsonSerializable {

    /** Phone number sender info */
    public sealed class Registration(public val type: RegistrationType): JsonSerializable {

        internal data class OptIn(val data: OptInData): Registration(RegistrationType.OPT_IN)

        internal data class OptInData(
            val senderId: String
        ): JsonSerializable {

            companion object {
                private const val SENDER_ID_KEY = "sender_id"

                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): OptInData {
                    val content = value.requireMap()
                    return OptInData(
                        senderId = content.requireField(SENDER_ID_KEY)
                    )
                }
            }

            override fun toJsonValue(): JsonValue = jsonMapOf(
                SENDER_ID_KEY to senderId
            ).toJsonValue()
        }

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

        override fun toJsonValue(): JsonValue {
            val result = JsonMap.newBuilder()
            result.put(TYPE, type)

            when(this) {
                is OptIn -> result.putAll(data.toJsonValue().optMap())
            }

            return result.build().toJsonValue()
        }

        internal companion object {
            private const val TYPE = "type"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Registration {
                val content = value.requireMap()

                try {
                    return when(RegistrationType.fromJson(content.require(TYPE))) {
                        RegistrationType.OPT_IN -> OptIn(
                            data = OptInData.fromJson(value)
                        )
                    }
                } catch (ex: NoSuchElementException) {
                    throw JsonException("Invalid value of the registration type", ex)
                }
            }
        }
    }

    public class ValidationHints(
        public val minDigits: Int?,
        public val maxDigits: Int?
    ): JsonSerializable {

        internal companion object {
            private const val MIN_DIGITS = "min_digits"
            private const val MAX_DIGITS = "max_digits"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): ValidationHints {
                val content = value.requireMap()

                return ValidationHints(
                    minDigits = content.optionalField(MIN_DIGITS),
                    maxDigits = content.optionalField(MAX_DIGITS)
                )
            }
         }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            MIN_DIGITS to minDigits,
            MAX_DIGITS to maxDigits
        ).toJsonValue()
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        COUNTRY_CODE to countryCode,
        PREFIX to prefix,
        REGISTRATION to registration,
        VALIDATION_HINTS to validationHints
    ).toJsonValue()

    internal companion object {
        private const val COUNTRY_CODE = "country_code"
        private const val PREFIX = "prefix"
        private const val REGISTRATION = "registration"
        private const val VALIDATION_HINTS = "validation_hints"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): SmsLocale {
            val content = value.requireMap()

            return SmsLocale(
                countryCode = content.requireField(COUNTRY_CODE),
                prefix = content.requireField(PREFIX),
                registration = content[REGISTRATION]?.let(Registration::fromJson),
                validationHints = content[VALIDATION_HINTS]?.let(ValidationHints::fromJson)
            )
        }
    }
}
