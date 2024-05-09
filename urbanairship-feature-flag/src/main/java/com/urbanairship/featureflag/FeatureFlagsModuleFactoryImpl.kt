/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import androidx.annotation.Keep
import com.urbanairship.BuildConfig
import com.urbanairship.PreferenceDataStore
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.Analytics
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.cache.AirshipCache
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.modules.Module
import com.urbanairship.modules.featureflag.FeatureFlagsModuleFactory
import com.urbanairship.remotedata.RemoteData

@Keep
class FeatureFlagsModuleFactoryImpl : FeatureFlagsModuleFactory {

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData,
        analytics: Analytics,
        infoProvider: DeviceInfoProvider,
        cache: AirshipCache,
        resolver: DeferredResolver,
        eventFeed: AirshipEventFeed
    ): Module {
        val manager = FeatureFlagManager(
            context = context.applicationContext,
            dataStore = dataStore,
            audienceEvaluator = AudienceEvaluator(context.applicationContext),
            remoteData = FeatureFlagRemoteDataAccess(remoteData),
            infoProvider = infoProvider,
            deferredResolver = FlagDeferredResolver(cache, resolver),
            featureFlagAnalytics = FeatureFlagAnalytics(eventFeed, analytics)
        )
        return Module.singleComponent(manager, 0)
    }

    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION
}
