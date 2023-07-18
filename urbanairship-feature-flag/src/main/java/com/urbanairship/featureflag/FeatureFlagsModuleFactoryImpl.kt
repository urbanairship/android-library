package com.urbanairship.featureflag

import android.content.Context
import com.urbanairship.BuildConfig
import com.urbanairship.PreferenceDataStore
import com.urbanairship.modules.Module
import com.urbanairship.modules.featureflag.FeatureFlagsModuleFactory
import com.urbanairship.remotedata.RemoteData

class FeatureFlagsModuleFactoryImpl : FeatureFlagsModuleFactory {

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData
    ): Module {
        val manager = AirshipFeatureFlags(
            context = context,
            dataStore = dataStore,
            remoteData = remoteData
        )
        return Module.singleComponent(manager, 0)
    }

    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION
}
