/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Open channel registration options.
 */
public class OpenChannelRegistrationOptions private constructor(
    public val platformName: String,
    public val identifiers: Map<String, String>?
) : JsonSerializable {

    override fun toJsonValue(): JsonValue {
        return JsonMap.newBuilder().put(PLATFORM_NAME_KEY, platformName)
            .putOpt(IDENTIFIERS_KEY, identifiers).build().toJsonValue()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OpenChannelRegistrationOptions

        if (platformName != other.platformName) return false
        if (identifiers != other.identifiers) return false

        return true
    }

    override fun hashCode(): Int = ObjectsCompat.hash(platformName, identifiers)

    override fun toString(): String {
        return "OpenChannelRegistrationOptions(platformName='$platformName', identifiers=$identifiers)"
    }

    public companion object {

        private const val PLATFORM_NAME_KEY = "platform_name"
        private  const val IDENTIFIERS_KEY = "identifiers"

        /**
         * Creates default options.
         *
         * @param platformName The platform name
         * @param identifiers Optional identifiers.
         * @return The open channel options.
         */
        @JvmStatic
        @JvmOverloads
        public fun options(
            platformName: String,
            identifiers: Map<String, String>? = null
        ): OpenChannelRegistrationOptions {
            return OpenChannelRegistrationOptions(platformName, identifiers)
        }


        @Throws(JsonException::class)
        internal fun fromJson(value: JsonValue): OpenChannelRegistrationOptions {
            val platformName = value.optMap().opt(PLATFORM_NAME_KEY).requireString()
            val identifiersJson = value.optMap().opt(IDENTIFIERS_KEY).map
            var parsedIdentifiers: MutableMap<String, String>? = null
            if (identifiersJson != null) {
                parsedIdentifiers = HashMap()
                for ((key, value1) in identifiersJson.entrySet()) {
                    parsedIdentifiers[key] = value1.requireString()
                }
            }
            return OpenChannelRegistrationOptions(platformName, parsedIdentifiers)
        }
    }
}
