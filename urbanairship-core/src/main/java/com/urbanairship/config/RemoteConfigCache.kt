/* Copyright Airship and Contributors */
package com.urbanairship.config

import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.remoteconfig.RemoteConfig

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class RemoteConfigCache(
    private val preferences: PreferenceDataStore
) {

    private val lock = Any()
    private var _remoteConfig: RemoteConfig? = null

    internal val config: RemoteConfig
        get() {
            synchronized(lock) {
                return _remoteConfig ?: RemoteConfig.fromJson(
                    preferences.getJsonValue(REMOTE_CONFIG_KEY)
                ).also { _remoteConfig = it }
            }
        }

    internal fun updateConfig(config: RemoteConfig): Boolean {
        synchronized(lock) {
            if (config == _remoteConfig) {
                return false
            }
            _remoteConfig = config
            preferences.put(REMOTE_CONFIG_KEY, config)
            return true
        }
    }

    private companion object {

        private const val REMOTE_CONFIG_KEY = "com.urbanairship.config.REMOTE_CONFIG_KEY"
    }
}
