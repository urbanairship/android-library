/* Copyright Airship and Contributors */
package com.urbanairship.config

import android.os.Build
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PreferenceDataStore
import com.urbanairship.Provider
import com.urbanairship.Platform
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.http.RequestSession
import com.urbanairship.remoteconfig.RemoteConfig
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Airship runtime config.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@OpenForTesting
public class AirshipRuntimeConfig internal constructor (
    private val configOptionsProvider: Provider<AirshipConfigOptions>,
    public val requestSession: RequestSession,
    internal val configObserver: RemoteConfigObserver,
    private val platformProvider: Provider<Platform>
) {

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public constructor(
        configOptionsProvider: Provider<AirshipConfigOptions>,
        requestSession: RequestSession,
        dataStore: PreferenceDataStore,
        platformProvider: Provider<Platform>
    ): this(
        configOptionsProvider, requestSession, RemoteConfigObserver(dataStore), platformProvider
    )

    public fun interface ConfigChangeListener {
        public fun onConfigUpdated()
    }

    private val allowUrlFallback: Boolean = determineUrlFallback(configOptionsProvider.get())

    public val platform: Platform
        get() = platformProvider.get()

    public val remoteConfig: RemoteConfig
        get() = configObserver.remoteConfig

    public val configOptions: AirshipConfigOptions
        get() = configOptionsProvider.get()

    /**
     * Returns a new device URL builder.
     *
     * @return A URL builder.
     */
    public val deviceUrl: UrlBuilder
        get() {
            return UrlBuilder(
                url(
                    remote = remoteConfig.airshipConfig?.deviceApiUrl,
                    fallback = configOptions.deviceUrl,
                    allowUrlFallback = allowUrlFallback
                )
            )
        }

    /**
     * Returns a new wallet URL builder.
     *
     * @return A URL builder.
     */
    public val walletUrl: UrlBuilder
        get() {
            return UrlBuilder(
                url(
                    remote = remoteConfig.airshipConfig?.walletUrl,
                    fallback = configOptions.walletUrl,
                    allowUrlFallback = allowUrlFallback
                )
            )
        }

    /**
     * Returns a new analytics URL builder.
     *
     * @return A URL builder.
     */
    public val analyticsUrl: UrlBuilder
        get() {
            return UrlBuilder(
                url(
                    remote = remoteConfig.airshipConfig?.analyticsUrl,
                    fallback = configOptions.analyticsUrl,
                    allowUrlFallback = allowUrlFallback
                )
            )
        }

    /**
     * Returns a new remote-data URL builder.
     *
     * @return A URL builder.
     */
    public val remoteDataUrl: UrlBuilder
        get() {
            return UrlBuilder(
                url(
                    remote = remoteConfig.airshipConfig?.remoteDataUrl,
                    fallback = configOptions.initialConfigUrl ?: configOptions.remoteDataUrl,
                    allowUrlFallback = true
                )
            )
        }

    /**
     * Returns a new metered usage URL builder.
     *
     * @return A URL builder.
     */
    public val meteredUsageUrl: UrlBuilder
        get() {
            return UrlBuilder(
                url(remoteConfig.airshipConfig?.meteredUsageUrl, null, false)
            )
        }

    /**
     * Checks if the deviceUrl is configured or not.
     * @return `true` if configured, otherwise `false`.
     */
    public val isDeviceUrlAvailable: Boolean
        get() {
            val url = url(
                remote = remoteConfig.airshipConfig?.deviceApiUrl,
                fallback = configOptions.deviceUrl,
                allowUrlFallback = allowUrlFallback
            )
            return url != null
        }

    private fun url(remote: String?, fallback: String?, allowUrlFallback: Boolean): String? {
        return if (!remote.isNullOrEmpty()) {
            remote
        } else if (allowUrlFallback && !fallback.isNullOrEmpty()) {
            fallback
        } else {
            null
        }
    }

    /** Adds a remote config listener. */
    public fun addConfigListener(listener: ConfigChangeListener) {
        configObserver.addConfigListener(listener)
    }

    /** Removes a URL config listener. */
    public fun removeRemoteConfigListener(listener: ConfigChangeListener) {
        configObserver.removeRemoteConfigListener(listener)
    }

    @VisibleForTesting
    public fun updateRemoteConfig(config: RemoteConfig) {
        configObserver.updateRemoteConfig(config)
    }

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private fun determineUrlFallback(configOptions: AirshipConfigOptions): Boolean {
            if ("huawei".equals(Build.MANUFACTURER, ignoreCase = true)) {
                return false
            }

            if (configOptions.requireInitialRemoteConfigEnabled) {
                return false
            }

            return configOptions.initialConfigUrl == null
        }
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteConfigObserver(dataStore: PreferenceDataStore) {
    private val remoteConfigListeners: MutableList<AirshipRuntimeConfig.ConfigChangeListener> = CopyOnWriteArrayList()
    private val remoteConfigCache: RemoteConfigCache = RemoteConfigCache(dataStore)

    public val remoteConfig: RemoteConfig
        get() = remoteConfigCache.config


    /** Adds a remote config listener. */
    public fun addConfigListener(listener: AirshipRuntimeConfig.ConfigChangeListener) {
        remoteConfigListeners.add(listener)
    }

    /** Removes a URL config listener. */
    public fun removeRemoteConfigListener(listener: AirshipRuntimeConfig.ConfigChangeListener) {
        remoteConfigListeners.remove(listener)
    }

    @VisibleForTesting
    public fun updateRemoteConfig(config: RemoteConfig) {
        if (this.remoteConfigCache.updateConfig(config)) {
            this.remoteConfigListeners.forEach { it.onConfigUpdated() }
        }
    }
}
