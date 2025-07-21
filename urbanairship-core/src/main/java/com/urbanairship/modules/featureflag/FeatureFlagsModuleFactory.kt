package com.urbanairship.modules.featureflag

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.analytics.Analytics
import com.urbanairship.cache.AirshipCache
import com.urbanairship.deferred.DeferredResolver
import com.urbanairship.modules.Module
import com.urbanairship.remotedata.RemoteData

/**
 * AirshipFeatureFlags module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface FeatureFlagsModuleFactory : AirshipVersionInfo {

    public fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        remoteData: RemoteData,
        analytics: Analytics,
        cache: AirshipCache,
        resolver: DeferredResolver,
        privacyManager: PrivacyManager
    ): Module
}
