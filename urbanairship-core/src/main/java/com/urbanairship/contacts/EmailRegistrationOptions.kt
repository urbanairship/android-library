/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
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

    override fun toJsonValue(): JsonValue {
        return JsonMap.newBuilder().put(TRANSACTIONAL_OPTED_IN_KEY, transactionalOptedIn)
            .put(COMMERCIAL_OPTED_IN_KEY, commercialOptedIn).put(PROPERTIES_KEY, properties)
            .put(DOUBLE_OPT_IN_KEY, isDoubleOptIn).build().toJsonValue()
    }

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
            commercialOptedIn: Date? = null, transactionalOptedIn: Date? = null, properties: JsonMap? = null
        ): EmailRegistrationOptions {
            return EmailRegistrationOptions(
                transactionalOptedIn?.time ?: -1, commercialOptedIn?.time ?: -1, properties, false
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
            transactionalOptedIn: Date? = null, properties: JsonMap? = null, doubleOptIn: Boolean
        ): EmailRegistrationOptions {
            return EmailRegistrationOptions(
                transactionalOptedIn?.time ?: -1, -1, properties, doubleOptIn
            )
        }

        internal fun fromJson(value: JsonValue): EmailRegistrationOptions {
            val map = value.optMap()
            val commercialOptedIn = map.opt(COMMERCIAL_OPTED_IN_KEY).getLong(-1)
            val transactionalOptedIn = map.opt(TRANSACTIONAL_OPTED_IN_KEY).getLong(-1)
            val properties = map.opt(PROPERTIES_KEY).getMap()
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
