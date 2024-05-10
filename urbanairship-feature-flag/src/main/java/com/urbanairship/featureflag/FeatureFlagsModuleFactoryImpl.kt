/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import android.content.Context
import com.urbanairship.BuildConfig
import com.urbanairship.PreferenceDataStore
import com.urbanairship.analytics.Analytics
import com.urbanairship.cache.AirshipCache
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.modules.Module
import com.urbanairship.modules.featureflag.FeatureFlagsModuleFactory
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.util.Clock

class FeatureFlagsModuleFactoryImpl : FeatureFlagsModuleFactory {

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData,
        analytics: Analytics,
        cache: AirshipCache,
        resolver: DeferredResolver
    ): Module {
        val manager = FeatureFlagManager(
            context = context,
            dataStore = dataStore,
            remoteData = remoteData,
            analytics = analytics,
            clock = Clock.DEFAULT_CLOCK,
            deferredResolver = FlagDeferredResolver(cache, resolver)
        )
        return Module.singleComponent(manager, 0)
    }

    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION
}
