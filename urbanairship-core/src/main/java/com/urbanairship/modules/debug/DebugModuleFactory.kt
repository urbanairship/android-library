/* Copyright Airship and Contributors */
package com.urbanairship.modules.debug

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.modules.Module
import com.urbanairship.remotedata.RemoteData

/**
 * Debug module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface DebugModuleFactory : AirshipVersionInfo {

    public fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData
    ): Module
}
