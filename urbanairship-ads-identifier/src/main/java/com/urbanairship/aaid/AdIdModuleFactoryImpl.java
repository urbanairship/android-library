/* Copyright Airship and Contributors */

package com.urbanairship.aaid;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
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
    public Module build(@NonNull Context context, @NonNull PreferenceDataStore dataStore) {
        return Module.singleComponent(new AdvertisingIdTracker(context, dataStore), 0);
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
