/* Copyright Airship and Contributors */
package com.urbanairship.modules

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.ApplicationMetrics
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.audience.AudienceEvaluator
import com.urbanairship.cache.AirshipCache
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.experiment.ExperimentManager
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.modules.aaid.AdIdModuleFactory
import com.urbanairship.modules.automation.AutomationModuleFactory
import com.urbanairship.modules.debug.DebugModuleFactory
import com.urbanairship.modules.featureflag.FeatureFlagsModuleFactory
import com.urbanairship.modules.liveupdate.LiveUpdateModuleFactory
import com.urbanairship.modules.messagecenter.MessageCenterModuleFactory
import com.urbanairship.modules.preferencecenter.PreferenceCenterModuleFactory
import com.urbanairship.push.PushManager
import com.urbanairship.remotedata.RemoteData

/**
 * Creates module used by [com.urbanairship.UAirship].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object Modules {

    private const val MESSAGE_CENTER_MODULE_FACTORY = "com.urbanairship.messagecenter.MessageCenterModuleFactoryImpl"
    private const val AUTOMATION_MODULE_FACTORY = "com.urbanairship.automation.AutomationModuleFactoryImpl"
    private const val DEBUG_MODULE_FACTORY = "com.urbanairship.debug.DebugModuleFactoryImpl"
    private const val AD_ID_FACTORY = "com.urbanairship.aaid.AdIdModuleFactoryImpl"
    private const val LIVE_UPDATE_FACTORY = "com.urbanairship.liveupdate.LiveUpdateModuleFactoryImpl"
    private const val PREFERENCE_CENTER_FACTORY = "com.urbanairship.preferencecenter.PreferenceCenterModuleFactoryImpl"
    private const val FEATURE_FLAGS_FACTORY = "com.urbanairship.featureflag.FeatureFlagsModuleFactoryImpl"

    @JvmStatic
    public fun messageCenter(
        context: Context,
        preferenceDataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        channel: AirshipChannel,
        pushManager: PushManager
    ): Module? {
        try {
            return createFactory(
                MESSAGE_CENTER_MODULE_FACTORY, MessageCenterModuleFactory::class.java
            )?.build(
                context = context,
                dataStore = preferenceDataStore,
                config = config,
                privacyManager = privacyManager,
                airshipChannel = channel,
                pushManager = pushManager)
        } catch (e: Exception) {
            UALog.e(e, "Failed to build Message Center module")
        }
        return null
    }

    @JvmStatic
    public fun automation(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager,
        analytics: Analytics,
        remoteData: RemoteData,
        experimentManager: ExperimentManager,
        meteredUsage: AirshipMeteredUsage,
        deferredResolver: DeferredResolver,
        eventFeed: AirshipEventFeed,
        metrics: ApplicationMetrics,
        cache: AirshipCache,
        audienceEvaluator: AudienceEvaluator
    ): Module? {
        try {
            return createFactory(
                AUTOMATION_MODULE_FACTORY, AutomationModuleFactory::class.java
            )?.build(
                context = context,
                dataStore = dataStore,
                runtimeConfig = runtimeConfig,
                privacyManager = privacyManager,
                airshipChannel = airshipChannel,
                pushManager = pushManager,
                analytics = analytics,
                remoteData = remoteData,
                experimentManager = experimentManager,
                meteredUsage = meteredUsage,
                deferredResolver = deferredResolver,
                eventFeed = eventFeed,
                metrics = metrics,
                cache = cache,
                audienceEvaluator = audienceEvaluator
            )
        } catch (e: Exception) {
            UALog.e(e, "Failed to build Automation module")
        }
        return null
    }

    @JvmStatic
    public fun debug(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData,
        pushManager: PushManager,
        analytics: Analytics
    ): Module? {
        try {
            return createFactory(
                DEBUG_MODULE_FACTORY, DebugModuleFactory::class.java
            )?.build(
                context = context,
                dataStore = dataStore,
                remoteData = remoteData,
                pushManager = pushManager,
                analytics = analytics
            )
        } catch (e: Exception) {
            UALog.e(e, "Failed to build Debug module")
        }
        return null
    }

    @JvmStatic
    public fun adId(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        analytics: Analytics
    ): Module? {
        try {
            return createFactory(
                AD_ID_FACTORY, AdIdModuleFactory::class.java
            )?.build(
                context = context,
                dataStore = dataStore,
                runtimeConfig = runtimeConfig,
                privacyManager = privacyManager,
                analytics = analytics
            )
        } catch (e: Exception) {
            UALog.e(e, "Failed to build Ad Id module")
        }
        return null
    }

    @JvmStatic
    public fun preferenceCenter(
        context: Context,
        dataStore: PreferenceDataStore,
        privacyManager: PrivacyManager,
        remoteData: RemoteData,
        validator: AirshipInputValidation.Validator
    ): Module? {
        try {
            return createFactory(
                PREFERENCE_CENTER_FACTORY, PreferenceCenterModuleFactory::class.java
            )?.build(
                context = context,
                dataStore = dataStore,
                privacyManager = privacyManager,
                remoteData = remoteData,
                inputValidator = validator
            )
        } catch (e: Exception) {
            UALog.e(e, "Failed to build Preference Center module")
        }
        return null
    }

    @JvmStatic
    public fun liveUpdateManager(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager
    ): Module? {
        try {
            return createFactory(
                LIVE_UPDATE_FACTORY, LiveUpdateModuleFactory::class.java
            )?.build(
                context = context,
                dataStore = dataStore,
                config = config,
                privacyManager = privacyManager,
                airshipChannel = airshipChannel,
                pushManager = pushManager
            )
        } catch (e: Exception) {
            UALog.e(e, "Failed to build Live Update module")
        }
        return null
    }

    @JvmStatic
    public fun featureFlags(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData,
        analytics: Analytics,
        cache: AirshipCache,
        resolver: DeferredResolver,
        privacyManager: PrivacyManager
    ): Module? {
        try {
            return createFactory(
                FEATURE_FLAGS_FACTORY, FeatureFlagsModuleFactory::class.java
            )?.build(
                context = context,
                dataStore = dataStore,
                remoteData = remoteData,
                analytics = analytics,
                cache = cache,
                resolver = resolver,
                privacyManager = privacyManager
            )
        } catch (e: Exception) {
            UALog.e(e, "Failed to build Feature Flags module")
        }
        return null
    }

    /**
     * Creates the factory instance.
     *
     * @param className The class name.
     * @param factoryClass The factory class.
     * @return The instance or null if not available.
     */
    private fun <T : AirshipVersionInfo?> createFactory(
        className: String,
        factoryClass: Class<T>
    ): T? {
        try {
            val clazz = Class.forName(className).asSubclass(factoryClass)
            val instance = clazz.getDeclaredConstructor().newInstance()
            if (UAirship.getVersion() != instance?.airshipVersion) {
                UALog.e(
                    "Unable to load module with factory $factoryClass, versions do not match. " +
                            "Core Version: ${UAirship.getVersion()}, Module Version: ${instance?.airshipVersion}.",
                )
                return null
            }
            return instance
        } catch (ignored: ClassNotFoundException) {
        } catch (e: Exception) {
            UALog.e(e, "Unable to create module factory $factoryClass")
        }

        return null
    }
}
