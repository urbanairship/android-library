/* Copyright Airship and Contributors */
package com.urbanairship.remoteconfig

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RemoteConfig @JvmOverloads constructor(
    public val airshipConfig: RemoteAirshipConfig? = null,
    public val meteredUsageConfig: MeteredUsageConfig? = null,
    public val fetchContactRemoteData: Boolean? = null,
    public val contactConfig: ContactConfig? = null
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        AIRSHIP_CONFIG_KEY to airshipConfig?.toJsonValue(),
        METERED_USAGE_CONFIG_KEY to meteredUsageConfig?.toJsonValue(),
        FETCH_CONTACT_REMOTE_DATA_KEY to fetchContactRemoteData,
        CONTACT_CONFIG_KEY to contactConfig?.toJsonValue()
    ).toJsonValue()

    public companion object {
        private const val AIRSHIP_CONFIG_KEY = "airship_config"
        private const val METERED_USAGE_CONFIG_KEY = "metered_usage"
        private const val FETCH_CONTACT_REMOTE_DATA_KEY = "fetch_contact_remote_data"
        private const val CONTACT_CONFIG_KEY = "contact_config"

        internal val TOP_LEVEL_KEYS = setOf(
            AIRSHIP_CONFIG_KEY,
            METERED_USAGE_CONFIG_KEY,
            FETCH_CONTACT_REMOTE_DATA_KEY,
            CONTACT_CONFIG_KEY
        )

        @JvmStatic
        public fun fromJson(json: JsonMap): RemoteConfig {
            return RemoteConfig(
                airshipConfig = json.optionalField<JsonValue>(AIRSHIP_CONFIG_KEY)?.let {
                    RemoteAirshipConfig.fromJson(it)
                },
                meteredUsageConfig = json.optionalField<JsonValue>(METERED_USAGE_CONFIG_KEY)?.let {
                    MeteredUsageConfig.fromJson(it)
                },
                fetchContactRemoteData = json.optionalField<Boolean>(FETCH_CONTACT_REMOTE_DATA_KEY),
                contactConfig = json.optionalField<JsonValue>(CONTACT_CONFIG_KEY)?.let {
                    ContactConfig.fromJson(it)
                }
            )
        }

        @JvmStatic
        public fun fromJson(json: JsonValue): RemoteConfig = fromJson(json.optMap())
    }
}

/**
 * Remote Airship config from remote-data.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RemoteAirshipConfig @JvmOverloads constructor(
    public val remoteDataUrl: String? = null,
    public val deviceApiUrl: String? = null,
    public val walletUrl: String? = null,
    public val analyticsUrl: String? = null,
    public val meteredUsageUrl: String? = null
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        REMOTE_DATA_URL_KEY to remoteDataUrl,
        DEVICE_API_URL_KEY to deviceApiUrl,
        ANALYTICS_URL_KEY to analyticsUrl,
        WALLET_URL_KEY to walletUrl,
        METERED_USAGE_URL_KEY to meteredUsageUrl
    ).toJsonValue()

    public constructor(jsonValue: JsonValue) : this(
        remoteDataUrl = jsonValue.optMap().optionalField(REMOTE_DATA_URL_KEY),
        deviceApiUrl = jsonValue.optMap().optionalField(DEVICE_API_URL_KEY),
        walletUrl = jsonValue.optMap().optionalField(WALLET_URL_KEY),
        analyticsUrl = jsonValue.optMap().optionalField(ANALYTICS_URL_KEY),
        meteredUsageUrl = jsonValue.optMap().optionalField(METERED_USAGE_URL_KEY)
    )

    public companion object {
        private const val REMOTE_DATA_URL_KEY = "remote_data_url"
        private const val DEVICE_API_URL_KEY = "device_api_url"
        private const val WALLET_URL_KEY = "wallet_url"
        private const val ANALYTICS_URL_KEY = "analytics_url"
        private const val METERED_USAGE_URL_KEY = "metered_usage_url"

        @JvmStatic
        public fun fromJson(json: JsonValue): RemoteAirshipConfig = RemoteAirshipConfig(json)
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class ContactConfig @JvmOverloads constructor(
    public val foregroundIntervalMs: Long? = null,
    public val channelRegistrationMaxResolveAgeMs: Long? = null
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        FOREGROUND_INTERVAL_MS_KEY to foregroundIntervalMs,
        CHANNEL_REGISTRATION_MAX_RESOLVE_AGE_MS_KEY to channelRegistrationMaxResolveAgeMs
    ).toJsonValue()

    public companion object {
        private const val FOREGROUND_INTERVAL_MS_KEY = "foreground_resolve_interval_ms"
        private const val CHANNEL_REGISTRATION_MAX_RESOLVE_AGE_MS_KEY = "max_cra_resolve_age_ms"

        @JvmStatic
        public fun fromJson(json: JsonValue): ContactConfig = ContactConfig(
            foregroundIntervalMs = json.optMap().optionalField(FOREGROUND_INTERVAL_MS_KEY),
            channelRegistrationMaxResolveAgeMs = json.optMap().optionalField(CHANNEL_REGISTRATION_MAX_RESOLVE_AGE_MS_KEY)
        )
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class MeteredUsageConfig internal constructor(
    public val isEnabled: Boolean,
    public val initialDelayMs: Long,
    public val intervalMs: Long
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IS_ENABLED_KEY to isEnabled,
        INITIAL_DELAY_MS_KEY to initialDelayMs,
        INTERVAL_MS_KEY to intervalMs
    ).toJsonValue()

    public companion object {
        private const val IS_ENABLED_KEY = "enabled"
        private const val INITIAL_DELAY_MS_KEY = "initial_delay_ms"
        private const val INTERVAL_MS_KEY = "interval_ms"

        private const val DEFAULT_INITIAL_DELAY = 15L
        private const val DEFAULT_INTERVAL = 30L

        @JvmStatic
        public fun fromJson(json: JsonMap): MeteredUsageConfig = MeteredUsageConfig(
            isEnabled = json.optionalField(IS_ENABLED_KEY) ?: false,
            initialDelayMs = json.optionalField(INITIAL_DELAY_MS_KEY) ?: DEFAULT_INITIAL_DELAY,
            intervalMs = json.optionalField(INTERVAL_MS_KEY) ?: DEFAULT_INTERVAL
        )

        @JvmStatic
        public fun fromJson(json: JsonValue): MeteredUsageConfig = fromJson(json.optMap())

        @JvmStatic
        public val DEFAULT: MeteredUsageConfig =
            MeteredUsageConfig(false, DEFAULT_INITIAL_DELAY, DEFAULT_INTERVAL)
    }
}
