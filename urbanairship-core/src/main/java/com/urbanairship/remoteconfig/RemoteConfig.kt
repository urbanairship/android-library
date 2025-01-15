/* Copyright Airship and Contributors */
package com.urbanairship.remoteconfig

import androidx.annotation.RestrictTo
import com.urbanairship.PrivacyManager
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import kotlin.jvm.Throws

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RemoteConfig @JvmOverloads constructor(
    public val airshipConfig: RemoteAirshipConfig? = null,
    public val meteredUsageConfig: MeteredUsageConfig? = null,
    public val fetchContactRemoteData: Boolean? = null,
    public val contactConfig: ContactConfig? = null,
    public val disabledFeatures: PrivacyManager.Feature? = null,
    public val remoteDataRefreshInterval: Long? = null,
    public val iaaConfig: IAAConfig? = null
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        AIRSHIP_CONFIG_KEY to airshipConfig?.toJsonValue(),
        METERED_USAGE_CONFIG_KEY to meteredUsageConfig?.toJsonValue(),
        FETCH_CONTACT_REMOTE_DATA_KEY to fetchContactRemoteData,
        CONTACT_CONFIG_KEY to contactConfig?.toJsonValue(),
        DISABLED_FEATURES_KEY to disabledFeatures,
        REMOTE_DATA_REFRESH_INTERVAL_KEY to remoteDataRefreshInterval,
        IAA_CONFIG to iaaConfig
    ).toJsonValue()

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val AIRSHIP_CONFIG_KEY = "airship_config"
        private const val METERED_USAGE_CONFIG_KEY = "metered_usage"
        private const val FETCH_CONTACT_REMOTE_DATA_KEY = "fetch_contact_remote_data"
        private const val CONTACT_CONFIG_KEY = "contact_config"
        private const val DISABLED_FEATURES_KEY = "disabled_features"
        private const val REMOTE_DATA_REFRESH_INTERVAL_KEY = "remote_data_refresh_interval"
        private const val IAA_CONFIG = "in_app_config"

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
                },
                disabledFeatures = json.get(DISABLED_FEATURES_KEY)?.let(PrivacyManager.Feature::fromJson),
                remoteDataRefreshInterval = json.optionalField(REMOTE_DATA_REFRESH_INTERVAL_KEY),
                iaaConfig = json.get(IAA_CONFIG)?.let(IAAConfig::fromJson)
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

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val REMOTE_DATA_URL_KEY = "remote_data_url"
        private const val DEVICE_API_URL_KEY = "device_api_url"
        private const val WALLET_URL_KEY = "wallet_url"
        private const val ANALYTICS_URL_KEY = "analytics_url"
        private const val METERED_USAGE_URL_KEY = "metered_usage_url"

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        public fun fromJson(json: JsonValue): RemoteAirshipConfig = RemoteAirshipConfig(json)
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class ContactConfig @JvmOverloads constructor(
    public val foregroundIntervalMs: Long? = null,
    public val channelRegistrationMaxResolveAgeMs: Long? = null
) : JsonSerializable {

    override fun toJsonValue(): JsonValue = jsonMapOf(
        FOREGROUND_INTERVAL_MS_KEY to foregroundIntervalMs,
        CHANNEL_REGISTRATION_MAX_RESOLVE_AGE_MS_KEY to channelRegistrationMaxResolveAgeMs
    ).toJsonValue()

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val FOREGROUND_INTERVAL_MS_KEY = "foreground_resolve_interval_ms"
        private const val CHANNEL_REGISTRATION_MAX_RESOLVE_AGE_MS_KEY = "max_cra_resolve_age_ms"

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @JvmStatic
        public fun fromJson(json: JsonValue): ContactConfig = ContactConfig(
            foregroundIntervalMs = json.optMap().optionalField(FOREGROUND_INTERVAL_MS_KEY),
            channelRegistrationMaxResolveAgeMs = json.optMap().optionalField(CHANNEL_REGISTRATION_MAX_RESOLVE_AGE_MS_KEY)
        )
    }
}

/** @hide */
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

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
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

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class IAAConfig (
    public val retryingQueue: RetryingQueueConfig? = null,
    public val additionalAudienceCheck: AdditionalAudienceCheckConfig? = null
) : JsonSerializable {

    internal companion object {
        private const val RETRYING_QUEUE = "queue"
        private const val ADDITIONAL_AUDIENCE_CONFIG = "additional_audience_check"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): IAAConfig {
            val content = value.requireMap()

            return IAAConfig(
                retryingQueue = content.get(RETRYING_QUEUE)
                    ?.let(RetryingQueueConfig::fromJson),
                additionalAudienceCheck = content.get(ADDITIONAL_AUDIENCE_CONFIG)
                    ?.let(AdditionalAudienceCheckConfig::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        RETRYING_QUEUE to retryingQueue,
        ADDITIONAL_AUDIENCE_CONFIG to additionalAudienceCheck
    ).toJsonValue()
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class RetryingQueueConfig (
    public val maxConcurrentOperations: Int?,
    public val maxPendingResults: Int?,
    public val initialBackoff: Long?,
    public val maxBackOff: Long?
) : JsonSerializable {

    internal companion object {
        private const val MAX_CONCURRENT_OPERATIONS = "max_concurrent_operations"
        private const val MAX_PENDING_RESULTS = "max_pending_results"
        private const val INITIAL_BACKOFF = "initial_back_off_seconds"
        private const val MAX_BACK_OFF = "max_back_off_seconds"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): RetryingQueueConfig {
            val content = value.requireMap()

            return RetryingQueueConfig(
                maxConcurrentOperations = content.optionalField(MAX_CONCURRENT_OPERATIONS),
                maxPendingResults = content.optionalField(MAX_PENDING_RESULTS),
                initialBackoff = content.optionalField(INITIAL_BACKOFF),
                maxBackOff = content.optionalField(MAX_BACK_OFF)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        MAX_CONCURRENT_OPERATIONS to maxConcurrentOperations,
        MAX_PENDING_RESULTS to maxPendingResults,
        INITIAL_BACKOFF to initialBackoff,
        MAX_BACK_OFF to maxBackOff
    ).toJsonValue()
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class AdditionalAudienceCheckConfig(
    public val isEnabled: Boolean,
    public val context: JsonValue?,
    public val url: String?
) : JsonSerializable {

    internal companion object {
        private const val IS_ENABLED = "enabled"
        private const val CONTEXT = "context"
        private const val URL = "url"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): AdditionalAudienceCheckConfig {
            val content = value.requireMap()

            return AdditionalAudienceCheckConfig(
                isEnabled = content.requireField(IS_ENABLED),
                context = content.get(CONTEXT),
                url = content.optionalField(URL)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        IS_ENABLED to isEnabled,
        CONTEXT to context,
        URL to url
    ).toJsonValue()
}
