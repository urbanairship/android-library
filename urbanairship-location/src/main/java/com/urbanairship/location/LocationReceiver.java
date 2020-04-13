/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * A receiver that handles requesting location from either the Fused Location
 * Provider or standard Android location.
 */
public class LocationReceiver extends BroadcastReceiver {

    /**
     * Time to wait for UAirship when processing messages.
     */
    private static final long AIRSHIP_WAIT_TIME_MS = 9000; // 9 seconds

    /**
     * Action used for location updates.
     */
    static final String ACTION_LOCATION_UPDATE = "com.urbanairship.location.ACTION_LOCATION_UPDATE";

    private final Executor executor;
    private final Callable<AirshipLocationManager> locationManagerCallable;

    @VisibleForTesting
    LocationReceiver(Executor executor, Callable<AirshipLocationManager> locationManagerCallable) {
        this.executor = executor;
        this.locationManagerCallable = locationManagerCallable;
    }

    public LocationReceiver() {
        this(AirshipExecutors.THREAD_POOL_EXECUTOR, new Callable<AirshipLocationManager>() {
            @Override
            public AirshipLocationManager call() {
                UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
                return AirshipLocationManager.shared();
            }
        });
    }

    @Override
    public void onReceive(@NonNull Context context, final Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (!ACTION_LOCATION_UPDATE.equals(intent.getAction())) {
            Logger.verbose("LocationReceiver - Received intent with invalid action: %s", intent.getAction());
            return;
        }

        Logger.verbose("LocationReceiver - Received location update");

        Autopilot.automaticTakeOff(context);

        final PendingResult result = goAsync();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                AirshipLocationManager locationManager;
                try {
                    locationManager = locationManagerCallable.call();
                } catch (Exception e) {
                    Logger.error("Airship took too long to takeOff. Dropping location update.");
                    result.finish();
                    return;
                }

                onLocationUpdate(locationManager, intent);

                if (result != null) {
                    result.finish();
                }
            }
        });
    }

    /**
     * Called when an intent is received with action ACTION_LOCATION_UPDATE.
     *
     * @param locationManager The location manager.
     * @param intent The received intent.
     */
    private void onLocationUpdate(@NonNull AirshipLocationManager locationManager, @NonNull Intent intent) {
        // Fused location sometimes has an "Unmarshalling unknown type" runtime exception on 4.4.2 devices
        Location location;
        try {
            // If a provider is enabled or disabled notify the adapters so they can update providers.
            if (intent.hasExtra(LocationManager.KEY_PROVIDER_ENABLED)) {
                Logger.debug("LocationReceiver - One of the location providers was enabled or disabled.");
                locationManager.onSystemLocationProvidersChanged();
                return;
            }

            location = (Location) (intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED) ?
                    intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED) :
                    intent.getParcelableExtra("com.google.android.location.LOCATION"));
        } catch (Exception e) {
            Logger.error(e, "LocationReceiver - Unable to extract location.");
            return;
        }

        if (location != null) {
            locationManager.onLocationUpdate(location);
        }
    }

}
