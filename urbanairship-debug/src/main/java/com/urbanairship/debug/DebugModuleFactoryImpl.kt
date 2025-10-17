/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.analytics.Analytics
import com.urbanairship.modules.Module
import com.urbanairship.modules.debug.DebugModuleFactory
import com.urbanairship.push.PushManager
import com.urbanairship.remotedata.RemoteData

/**
 * Debug module factory implementation.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DebugModuleFactoryImpl : DebugModuleFactory {

    override val airshipVersion: String
        get() = com.urbanairship.BuildConfig.AIRSHIP_VERSION

    override val packageVersion: String
        get() = com.urbanairship.BuildConfig.SDK_VERSION

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData,
        pushManager: PushManager,
        analytics: Analytics
    ): Module {
        val debugManager = DebugManager(context, dataStore, remoteData, pushManager, analytics)
        return Module.singleComponent(debugManager)
    }
}
