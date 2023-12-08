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
public class RemoteConfigManager @VisibleForTesting internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val runtimeConfig: AirshipRuntimeConfig,
    private val privacyManager: PrivacyManager,
    private val remoteData: RemoteData,
    private val moduleAdapter: ModuleAdapter,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) : AirshipComponent(context, dataStore) {

    public constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        remoteData: RemoteData,
    ) : this(context, dataStore, runtimeConfig, privacyManager, remoteData, ModuleAdapter())

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val privacyManagerListener = PrivacyManager.Listener { updateSubscription() }

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
        val disableInfos: MutableList<DisableInfo> = ArrayList()
        val moduleConfigs: MutableMap<String, JsonValue> = HashMap()
        val remoteConfig = RemoteConfig.fromJson(config)

        for (key in config.keySet()) {
            // Skip over fields that we've already parsed into the RemoteConfig object.
            if (key in RemoteConfig.TOP_LEVEL_KEYS) {
                continue
            }

            val value = config.opt(key)

            // Handle disable info
            if (DISABLE_INFO_KEY == key) {
                for (disableInfoJson in value.optList()) {
                    try {
                        disableInfos.add(DisableInfo.fromJson(disableInfoJson))
                    } catch (e: JsonException) {
                        UALog.e(e, "Failed to parse remote config: %s", config)
                    }
                }
                continue
            }

            // Store module configs
            moduleConfigs[key] = value
        }

        this.runtimeConfig.updateRemoteConfig(remoteConfig)

        apply(DisableInfo.filter(disableInfos, UAirship.getVersion(), UAirship.getAppVersion()))
    }

    override fun tearDown() {
        super.tearDown()
        subscription?.cancel()
        privacyManager.removeListener(privacyManagerListener)
    }

    /**
     * Disables and enables airship components.
     *
     * @param disableInfos The list of disable infos.
     */
    private fun apply(disableInfos: List<DisableInfo>) {
        val disableModules: MutableSet<String> = HashSet()
        val enabledModules: MutableSet<String> = HashSet(Modules.ALL_MODULES)
        var remoteDataInterval = RemoteData.DEFAULT_FOREGROUND_REFRESH_INTERVAL_MS

        // Combine the disable modules and remote data interval
        for (info in disableInfos) {
            disableModules.addAll(info.disabledModules)
            enabledModules.removeAll(info.disabledModules)
            remoteDataInterval = remoteDataInterval.coerceAtLeast(info.remoteDataRefreshInterval)
        }

        // Disable
        for (module in disableModules) {
            moduleAdapter.setComponentEnabled(module, false)
        }

        // Enable
        for (module in enabledModules) {
            moduleAdapter.setComponentEnabled(module, true)
        }

        // Remote data refresh interval
        remoteData.foregroundRefreshInterval = remoteDataInterval
    }

    private companion object {

        // Remote config types
        private const val CONFIG_TYPE_COMMON = "app_config"
        private const val CONFIG_TYPE_ANDROID = "app_config:android"
        private const val CONFIG_TYPE_AMAZON = "app_config:amazon"

        // Disable config key
        private const val DISABLE_INFO_KEY = "disable_features"
    }
}
