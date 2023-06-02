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
import com.urbanairship.json.optionalField
import com.urbanairship.remotedata.RemoteData
import java.util.concurrent.CopyOnWriteArraySet
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
        remoteData: RemoteData
    ) : this(context, dataStore, runtimeConfig, privacyManager, remoteData, ModuleAdapter())

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val listeners: MutableCollection<RemoteAirshipConfigListener> = CopyOnWriteArraySet()
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
                            for ((_, _, data) in payloads) {
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
        var airshipConfig = JsonValue.NULL
        for (key in config.keySet()) {
            val value = config.opt(key)
            if (AIRSHIP_CONFIG_KEY == key) {
                airshipConfig = value
                continue
            }
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
            moduleConfigs[key] = value
        }
        updateRemoteAirshipConfig(airshipConfig)
        apply(DisableInfo.filter(disableInfos, UAirship.getVersion(), UAirship.getAppVersion()))

        // Notify new config
        val modules: MutableSet<String> = HashSet(Modules.ALL_MODULES)
        modules.addAll(moduleConfigs.keys)
        for (module in modules) {
            val moduleConfig = moduleConfigs[module]
            if (moduleConfig == null) {
                moduleAdapter.onNewConfig(module, null)
            } else {
                moduleAdapter.onNewConfig(module, moduleConfig.optMap())
            }
        }

        // Remote data refresh interval
        val contactEnabled = config.optionalField<Boolean>("fetch_contact_remote_data") ?: false
        remoteData.setContactSourceEnabled(contactEnabled)
    }

    /**
     * Adds a listener for [RemoteAirshipConfig] changes.
     *
     * @param listener The listener.
     */
    public fun addRemoteAirshipConfigListener(listener: RemoteAirshipConfigListener) {
        listeners.add(listener)
    }

    private fun updateRemoteAirshipConfig(value: JsonValue) {
        val remoteAirshipConfig = RemoteAirshipConfig.fromJson(value)
        for (listener in listeners) {
            listener.onRemoteConfigUpdated(remoteAirshipConfig)
        }
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

        // Airship config key
        private const val AIRSHIP_CONFIG_KEY = "airship_config"
    }
}
