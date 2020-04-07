/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.modules.location.LocationModuleLoader;
import com.urbanairship.modules.location.LocationModuleLoaderFactory;

import java.util.Collections;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Location module loader factory implementation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LocationModuleLoaderFactoryImpl implements LocationModuleLoaderFactory {

    @Override
    public LocationModuleLoader build(@NonNull Context context, @NonNull PreferenceDataStore dataStore, @NonNull AirshipChannel airshipChannel, @NonNull Analytics analytics) {
        AirshipLocationManager locationManager = new AirshipLocationManager(context, dataStore, airshipChannel, analytics);
        return new Loader(locationManager);
    }

    private static class Loader implements LocationModuleLoader {

        private final AirshipLocationManager locationManager;

        Loader(@NonNull AirshipLocationManager locationManager) {
            this.locationManager = locationManager;
        }

        @Override
        @NonNull
        public AirshipLocationClient getLocationClient() {
            return locationManager;
        }

        @Override
        @NonNull
        public Set<? extends AirshipComponent> getComponents() {
            return Collections.singleton(locationManager);
        }

    }

}
