package com.urbanairship.preferencecenter

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.BuildConfig
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.modules.Module
import com.urbanairship.modules.preferencecenter.PreferenceCenterModuleFactory
import com.urbanairship.remotedata.RemoteData

/**
 * PreferenceCenter module factory implementation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PreferenceCenterModuleFactoryImpl : PreferenceCenterModuleFactory {

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        privacyManager: PrivacyManager,
        remoteData: RemoteData
    ): Module {
        val preferenceCenter = PreferenceCenter(context, dataStore, privacyManager, remoteData)
        return Module.singleComponent(preferenceCenter, R.xml.ua_preference_center_actions)
    }

    override fun getAirshipVersion(): String = BuildConfig.AIRSHIP_VERSION

    override fun getPackageVersion(): String = BuildConfig.SDK_VERSION
}
