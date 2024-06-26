/* Copyright Airship and Contributors */
package com.urbanairship.remoteconfig

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Remote config manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@OpenForTesting
public class RemoteConfigManager @JvmOverloads constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val runtimeConfig: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val remoteData: RemoteData,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) : AirshipComponent(context, dataStore) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val privacyManagerListener = object : PrivacyManager.Listener {
        override fun onEnabledFeaturesChanged() = updateSubscription()
    }

    private var subscription: Job? = null

    init {
        updateSubscription()
        privacyManager.addListener(privacyManagerListener)
    }

    private fun updateSubscription() {
        if (privacyManager.isAnyFeatureEnabled) {
            if (subscription?.isActive != true) {
                val platformConfig = if (runtimeConfig.platform == UAirship.AMAZON_PLATFORM) CONFIG_TYPE_AMAZON else CONFIG_TYPE_ANDROID

                subscription = scope.launch {
                    remoteData.payloadFlow(listOf(CONFIG_TYPE_COMMON, platformConfig))
                        .collect { payloads ->
                            // combine the payloads, overwriting common config with platform-specific config
                            val combinedPayloadDataBuilder = JsonMap.newBuilder()
                            for ((_, _, data, _) in payloads) {
                                combinedPayloadDataBuilder.putAll(data)
                            }
                            val config = combinedPayloadDataBuilder.build()
                            try {
                                processConfig(config)
                            } catch (e: Exception) {
                                UALog.e(e, "Failed to process remote data")
                            }
                        }
                }
            }
        } else {
            subscription?.cancel()
        }
    }

    /**
     * Processes the remote config.
     *
     * @param config The remote data config.
     */
    private fun processConfig(config: JsonMap) {
        val remoteConfig = RemoteConfig.fromJson(config)
        this.runtimeConfig.updateRemoteConfig(remoteConfig)
    }

    override fun tearDown() {
        super.tearDown()
        subscription?.cancel()
        privacyManager.removeListener(privacyManagerListener)
    }

    private companion object {
        // Remote config types
        private const val CONFIG_TYPE_COMMON = "app_config"
        private const val CONFIG_TYPE_ANDROID = "app_config:android"
        private const val CONFIG_TYPE_AMAZON = "app_config:amazon"
    }
}
