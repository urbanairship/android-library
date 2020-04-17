/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.messagecenter.BuildConfig
import com.urbanairship.modules.Module
import com.urbanairship.modules.debug.DebugModuleFactory

/**
 * Debug module factory implementation.
 * @suppress
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DebugModuleFactoryImpl : DebugModuleFactory {

    override fun build(context: Context, dataStore: PreferenceDataStore): Module {
        return Module.singleComponent(DebugManager(context, dataStore), 0)
    }

    override fun getAirshipVersion() = BuildConfig.AIRSHIP_VERSION

    override fun getPackageVersion() = BuildConfig.SDK_VERSION
}
