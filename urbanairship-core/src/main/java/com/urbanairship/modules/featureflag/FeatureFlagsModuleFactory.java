package com.urbanairship.modules.featureflag;

import android.content.Context;

import com.urbanairship.AirshipVersionInfo;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.audience.DeviceInfoProvider;
import com.urbanairship.cache.AirshipCache;
import com.urbanairship.deferred.DeferredResolver;
import com.urbanairship.modules.Module;
import com.urbanairship.remotedata.RemoteData;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * AirshipFeatureFlags module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface FeatureFlagsModuleFactory extends AirshipVersionInfo {

    @NonNull
    Module build(
            @NonNull Context Context,
            @NonNull PreferenceDataStore dataStore,
            @NonNull RemoteData remoteData,
            @NonNull Analytics analytics,
            @NonNull AirshipCache cache,
            @NonNull DeferredResolver resolver
    );
}
