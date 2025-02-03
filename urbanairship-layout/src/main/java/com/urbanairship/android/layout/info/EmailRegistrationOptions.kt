/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.info

import com.urbanairship.android.layout.info.EmailRegistrationOptions.Type.COMMERCIAL
import com.urbanairship.android.layout.info.EmailRegistrationOptions.Type.DOUBLE_OPT_IN
import com.urbanairship.android.layout.info.EmailRegistrationOptions.Type.TRANSACTIONAL
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField

public sealed class EmailRegistrationOptions {
    public enum class Type {
        DOUBLE_OPT_IN,
        COMMERCIAL,
        TRANSACTIONAL;

        public companion object {
            public fun fromString(value: String): Type? = when (value.lowercase()) {
                "double_opt_in" -> DOUBLE_OPT_IN
                "commercial" -> COMMERCIAL
                "transactional" -> TRANSACTIONAL
                else -> null
            }
        }
    }

    public abstract val type: Type

    public data class Commercial(public val properties: JsonValue?, public val optedIn: Boolean) : EmailRegistrationOptions() {
        override val type: Type = COMMERCIAL
    }

    public data class Transactional(public val properties: JsonValue?) : EmailRegistrationOptions() {
        override val type: Type = TRANSACTIONAL
    }

    public data class DoubleOptIn(public val properties: JsonValue?): EmailRegistrationOptions() {
        override val type: Type = DOUBLE_OPT_IN
    }

    public companion object {
        public fun fromJson(value: JsonValue): EmailRegistrationOptions {
            val map = value.requireMap()

            val type = requireNotNull(Type.fromString(map.requireField("type")))
            val properties = map.opt("properties")

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
