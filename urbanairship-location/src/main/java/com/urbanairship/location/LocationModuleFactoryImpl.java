/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.content.Context;

import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.location.LocationModule;
import com.urbanairship.modules.location.LocationModuleFactory;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Location module loader factory implementation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocationModuleFactoryImpl implements LocationModuleFactory {

    @Override
    public LocationModule build(@NonNull Context context, @NonNull PreferenceDataStore dataStore, @NonNull AirshipChannel airshipChannel, @NonNull Analytics analytics) {
        AirshipLocationManager locationManager = new AirshipLocationManager(context, dataStore, airshipChannel, analytics);
        return new LocationModule(locationManager, locationManager);
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
