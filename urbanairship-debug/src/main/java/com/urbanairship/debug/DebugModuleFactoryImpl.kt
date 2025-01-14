/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.modules.Module
import com.urbanairship.modules.debug.DebugModuleFactory
import com.urbanairship.remotedata.RemoteData

/**
 * Debug module factory implementation.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DebugModuleFactoryImpl : DebugModuleFactory {

    public override fun build(context: Context, dataStore: PreferenceDataStore, remoteData: RemoteData): Module {
        return Module.singleComponent(DebugManager(context, dataStore, remoteData), 0)
    }

    override val airshipVersion: String
        get() = com.urbanairship.BuildConfig.AIRSHIP_VERSION

    override val packageVersion: String
        get() = com.urbanairship.BuildConfig.SDK_VERSION
}
