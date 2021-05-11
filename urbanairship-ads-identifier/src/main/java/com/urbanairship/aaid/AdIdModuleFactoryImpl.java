/* Copyright Airship and Contributors */

package com.urbanairship.aaid;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.modules.Module;
import com.urbanairship.modules.aaid.AdIdModuleFactory;

import androidx.annotation.NonNull;

/**
 * Ad Id module factory implementation.
 *
 * @hide
 */
public class AdIdModuleFactoryImpl implements AdIdModuleFactory {

    @NonNull
    @Override
    public Module build(@NonNull Context context,
                        @NonNull PreferenceDataStore dataStore,
                        @NonNull AirshipRuntimeConfig runtimeConfig,
                        @NonNull PrivacyManager privacyManager,
                        @NonNull Analytics analytics) {
        return Module.singleComponent(new AdvertisingIdTracker(context, dataStore, runtimeConfig, privacyManager, analytics), 0);
    }

    @NonNull
    @Override
    public String getAirshipVersion() {
        return BuildConfig.AIRSHIP_VERSION;
    }

    @NonNull
    @Override
    public String getPackageVersion() {
        return BuildConfig.SDK_VERSION;
    }


}
