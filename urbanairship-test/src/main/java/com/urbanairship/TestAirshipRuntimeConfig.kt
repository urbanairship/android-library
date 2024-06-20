/* Copyright Airship and Contributors */
package com.urbanairship

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.config.RemoteConfigObserver
import com.urbanairship.http.RequestSession
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.remoteconfig.RemoteConfig

@SuppressLint("VisibleForTests")
public class TestAirshipRuntimeConfig private constructor(
    private val configProvider: SettableProvider<AirshipConfigOptions>,
    dataStore: PreferenceDataStore,
    private val platformProvider: SettableProvider<Int>,
    remoteConfig: RemoteConfig?,
    session: RequestSession? = null
) : AirshipRuntimeConfig(
    configProvider, session ?: TestRequestSession(), dataStore, platformProvider
) {

    @JvmOverloads
    public constructor(remoteConfig: RemoteConfig? = null, session: RequestSession? = null) : this(
        SettableProvider(
            AirshipConfigOptions.Builder().setAppKey("appKey").setAppSecret("appSecret").build()
        ),
        PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext<Context>()),
        SettableProvider(UAirship.ANDROID_PLATFORM),
        remoteConfig,
        session
    )

    init {
        remoteConfig?.let { updateRemoteConfig(it) }
    }

    fun setPlatform(@UAirship.Platform platform: Int) {
        platformProvider.value = platform
    }

    fun setConfigOptions(configOptions: AirshipConfigOptions) {
        configProvider.value = configOptions
    }

    private class SettableProvider<T>(var value: T) : Provider<T> {

        override fun get(): T {
            return value
        }
    }
}
