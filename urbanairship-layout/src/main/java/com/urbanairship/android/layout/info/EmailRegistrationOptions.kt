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
    public abstract val properties: JsonMap?
    /** `true` opts in to open tracking, `false` opts out, `null` sends neither. */
    public abstract val openTrackingOptedIn: Boolean?
    /** `true` opts in to click tracking, `false` opts out, `null` sends neither. */
    public abstract val clickTrackingOptedIn: Boolean?

    public data class Commercial(
        override val properties: JsonMap?,
        public val optedIn: Boolean,
        override val openTrackingOptedIn: Boolean? = null,
        override val clickTrackingOptedIn: Boolean? = null
    ) : ThomasEmailRegistrationOptions() {
        override val type: Type = COMMERCIAL
    }

    public data class Transactional(
        override val properties: JsonMap?,
        override val openTrackingOptedIn: Boolean? = null,
        override val clickTrackingOptedIn: Boolean? = null
    ) : ThomasEmailRegistrationOptions() {
        override val type: Type = TRANSACTIONAL
    }

    public data class DoubleOptIn(
        override val properties: JsonMap?,
        override val openTrackingOptedIn: Boolean? = null,
        override val clickTrackingOptedIn: Boolean? = null
    ): ThomasEmailRegistrationOptions() {
        override val type: Type = DOUBLE_OPT_IN
    }

    public companion object {

        @Throws(JsonException::class)
        internal fun parseType(value: String): Type = when (value.lowercase()) {
            "double_opt_in" -> DOUBLE_OPT_IN
            "commercial" -> COMMERCIAL
            "transactional" -> TRANSACTIONAL
            else -> throw JsonException("Invalid email registration type: $value")
        }

        public fun fromJson(value: JsonValue): ThomasEmailRegistrationOptions {
            val map = value.requireMap()

            val type = parseType(map.requireField("type"))
            val properties: JsonMap? = map.optionalField("properties")
            val openTrackingOptedIn: Boolean? = map.optionalField("open_tracking_opted_in")
            val clickTrackingOptedIn: Boolean? = map.optionalField("click_tracking_opted_in")

            when(type) {
                DOUBLE_OPT_IN -> {
                    return DoubleOptIn(
                        properties = properties,
                        openTrackingOptedIn = openTrackingOptedIn,
                        clickTrackingOptedIn = clickTrackingOptedIn
                    )
                }
                COMMERCIAL -> {
                    return Commercial(
                        properties = properties,
                        optedIn = map.requireField("commercial_opted_in"),
                        openTrackingOptedIn = openTrackingOptedIn,
                        clickTrackingOptedIn = clickTrackingOptedIn
                    )
                }
                TRANSACTIONAL -> {
                    return Transactional(
                        properties = properties,
                        openTrackingOptedIn = openTrackingOptedIn,
                        clickTrackingOptedIn = clickTrackingOptedIn
                    )
                }
            }
        }
    }
}
