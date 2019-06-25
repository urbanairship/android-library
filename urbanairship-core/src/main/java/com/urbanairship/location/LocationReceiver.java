/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.util.concurrent.Executor;

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

    @VisibleForTesting
    LocationReceiver(Executor executor) {
        this.executor = executor;
    }

    public LocationReceiver() {
        this(AirshipExecutors.THREAD_POOL_EXECUTOR);
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
                UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);

                if (airship == null) {
                    Logger.error("Airship took too long to takeOff. Dropping location update.");
                    result.finish();
                    return;
                }

                onLocationUpdate(airship, intent);

                if (result != null) {
                    result.finish();
                }
            }
        });
    }

    /**
     * Called when an intent is received with action ACTION_LOCATION_UPDATE.
     *
     * @param intent The received intent.
     */
    private void onLocationUpdate(@NonNull UAirship airship, @NonNull Intent intent) {
        // Fused location sometimes has an "Unmarshalling unknown type" runtime exception on 4.4.2 devices
        Location location;
        try {
            // If a provider is enabled or disabled notify the adapters so they can update providers.
            if (intent.hasExtra(LocationManager.KEY_PROVIDER_ENABLED)) {
                Logger.debug("LocationReceiver - One of the location providers was enabled or disabled.");
                airship.getLocationManager().onSystemLocationProvidersChanged();
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
            airship.getLocationManager().onLocationUpdate(location);
        }
    }

}
