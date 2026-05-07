/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Date

/**
 * Email channel registration options.
 */
public class EmailRegistrationOptions private constructor(
    public val transactionalOptedIn: Long,
    public val commercialOptedIn: Long,
    public val properties: JsonMap?,
    public val isDoubleOptIn: Boolean
) : JsonSerializable {

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        TRANSACTIONAL_OPTED_IN_KEY to transactionalOptedIn,
        COMMERCIAL_OPTED_IN_KEY to commercialOptedIn,
        PROPERTIES_KEY to properties,
        DOUBLE_OPT_IN_KEY to isDoubleOptIn,
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmailRegistrationOptions

        if (transactionalOptedIn != other.transactionalOptedIn) return false
        if (commercialOptedIn != other.commercialOptedIn) return false
        if (properties != other.properties) return false
        if (isDoubleOptIn != other.isDoubleOptIn) return false

        return true
    }

    override fun hashCode(): Int = ObjectsCompat.hash(
        transactionalOptedIn, commercialOptedIn, properties, isDoubleOptIn
    )

    override fun toString(): String {
        return "EmailRegistrationOptions(transactionalOptedIn=$transactionalOptedIn, commercialOptedIn=$commercialOptedIn, properties=$properties, isDoubleOptIn=$isDoubleOptIn)"
    }

    public companion object {

        private const val TRANSACTIONAL_OPTED_IN_KEY = "transactional_opted_in"
        private const val COMMERCIAL_OPTED_IN_KEY = "commercial_opted_in"
        private const val PROPERTIES_KEY = "properties"
        private const val DOUBLE_OPT_IN_KEY = "double_opt_in"

        /**
         * Commercial registration options.
         *
         * @param commercialOptedIn The commercial opted in date.
         * @param transactionalOptedIn The transactional opted in date.
         * @param properties The optional properties.
         * @return The registration options.
         */
        @JvmStatic
        @JvmOverloads
        public fun commercialOptions(
            commercialOptedIn: Date? = null,
            transactionalOptedIn: Date? = null,
            properties: JsonMap? = null
        ): EmailRegistrationOptions {
            return EmailRegistrationOptions(
                transactionalOptedIn = transactionalOptedIn?.time ?: -1,
                commercialOptedIn = commercialOptedIn?.time ?: -1,
                properties = properties,
                isDoubleOptIn = false
            )
        }

        /**
         * Email registration options.
         *
         * @param transactionalOptedIn The transactional opted in date.
         * @param properties The optional properties.
         * @param doubleOptIn `true` to enable double opt-in, otherwise `false`.
         * @return The registration options.
         */
        @JvmStatic
        @JvmOverloads
        public fun options(
            transactionalOptedIn: Date? = null,
            properties: JsonMap? = null,
            doubleOptIn: Boolean
        ): EmailRegistrationOptions {
            return EmailRegistrationOptions(
                transactionalOptedIn = transactionalOptedIn?.time ?: -1,
                commercialOptedIn = -1,
                properties = properties,
                isDoubleOptIn = doubleOptIn
            )
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun fromJson(value: JsonValue): EmailRegistrationOptions {
            val map = value.optMap()
            val commercialOptedIn = map.opt(COMMERCIAL_OPTED_IN_KEY).getLong(-1)
            val transactionalOptedIn = map.opt(TRANSACTIONAL_OPTED_IN_KEY).getLong(-1)
            val properties = map.opt(PROPERTIES_KEY).map
            val doubleOptIn = map.opt(DOUBLE_OPT_IN_KEY).getBoolean(false)
            return EmailRegistrationOptions(
                transactionalOptedIn,
                commercialOptedIn,
                properties,
                doubleOptIn
            )
        }
    }
}
