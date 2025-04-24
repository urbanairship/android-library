/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.info

import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions.Type.COMMERCIAL
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions.Type.DOUBLE_OPT_IN
import com.urbanairship.android.layout.info.ThomasEmailRegistrationOptions.Type.TRANSACTIONAL
import com.urbanairship.android.layout.property.SmsLocale
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

public sealed class ThomasChannelRegistration {
    public data class Email(val address: String, val options: ThomasEmailRegistrationOptions): ThomasChannelRegistration()
    public data class Sms(val address: String, val registration: SmsLocale.Registration): ThomasChannelRegistration()
}

public sealed class ThomasEmailRegistrationOptions {
    public enum class Type {
        DOUBLE_OPT_IN,
        COMMERCIAL,
        TRANSACTIONAL
    }

    public abstract val type: Type

    public data class Commercial(public val properties: JsonMap?, public val optedIn: Boolean) : ThomasEmailRegistrationOptions() {
        override val type: Type = COMMERCIAL
    }

    public data class Transactional(public val properties: JsonMap?) : ThomasEmailRegistrationOptions() {
        override val type: Type = TRANSACTIONAL
    }

    public data class DoubleOptIn(public val properties: JsonMap?): ThomasEmailRegistrationOptions() {
        override val type: Type = DOUBLE_OPT_IN
    }

    public companion object {

        private  fun parseType(value: String): Type = when (value.lowercase()) {
            "double_opt_in" -> DOUBLE_OPT_IN
            "commercial" -> COMMERCIAL
            "transactional" -> TRANSACTIONAL
            else -> throw JsonException("Invalid email registration type: $value")
        }

        public fun fromJson(value: JsonValue): ThomasEmailRegistrationOptions {
            val map = value.requireMap()

            val type = parseType(map.requireField("type"))
            val properties: JsonMap? = map.optionalField("properties")

            when(type) {
                DOUBLE_OPT_IN -> {
                    return DoubleOptIn(
                        properties = properties
                    )
                }
                COMMERCIAL -> {
                    return Commercial(
                        properties = properties,
                        optedIn = map.requireField("commercial_opted_in")
                    )
                }
                TRANSACTIONAL -> {
                    return Transactional(
                        properties = properties
                    )
                }
            }
        }
    }
}
