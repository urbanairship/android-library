/* Copyright Airship and Contributors */
package com.urbanairship.config

import androidx.annotation.RestrictTo
import com.urbanairship.preferences.PreferenceStore
import com.urbanairship.preferences.SyncPrefKey
import com.urbanairship.remoteconfig.RemoteConfig

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class RemoteConfigCache(
    private val preferences: PreferenceStore
) {

    private val lock = Any()
    private var _remoteConfig: RemoteConfig? = null

    internal val config: RemoteConfig
        get() {
            synchronized(lock) {
                return _remoteConfig ?: (preferences.get(REMOTE_CONFIG_KEY) ?: RemoteConfig())
                    .also { _remoteConfig = it }
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

        private val REMOTE_CONFIG_KEY = SyncPrefKey.jsonSerializable(
            name = "com.urbanairship.config.REMOTE_CONFIG_KEY",
            fromJson = RemoteConfig::fromJson
        )
    }
}
