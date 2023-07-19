package com.urbanairship.featureflag

import android.content.Context
import com.urbanairship.BuildConfig
import com.urbanairship.PreferenceDataStore
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.modules.Module
import com.urbanairship.modules.featureflag.FeatureFlagsModuleFactory
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.util.Clock

class FeatureFlagsModuleFactoryImpl : FeatureFlagsModuleFactory {

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData,
        infoProvider: DeviceInfoProvider
    ): Module {
        val manager = AirshipFeatureFlags(
            context = context,
            dataStore = dataStore,
            remoteData = remoteData,
            infoProvider = infoProvider,
            clock = Clock.DEFAULT_CLOCK
        )
        return Module.singleComponent(manager, 0)
    }

    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION
}
