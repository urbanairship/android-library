/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.NonNull;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * A service that handles requesting location from either the Fused Location
 * Provider or standard Android location.
 */
public class LocationService extends IntentService {

    /**
     * Time to wait for UAirship when processing messages.
     */
    private static final long AIRSHIP_WAIT_TIME_MS = 10000; // 10 seconds

    /**
     * Action used for location updates.
     */
    static final String ACTION_LOCATION_UPDATE = "com.urbanairship.location.ACTION_LOCATION_UPDATE";

    public LocationService() {
        super("Location Service");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Autopilot.automaticTakeOff(getApplicationContext());
    }

    public void onHandleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("LocationService - Received intent with action: " + intent.getAction());

        final UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
        if (airship == null) {
            Logger.error("LocationService - UAirship not ready. Dropping intent: " + intent);
            return;
        }

        if (ACTION_LOCATION_UPDATE.equals(intent.getAction())) {
            onLocationUpdate(airship, intent);
        }
    }

    /**
     * Called when an intent is received with action ACTION_LOCATION_UPDATE.
     *
     * @param intent The received intent.
     */
    private void onLocationUpdate(UAirship airship, @NonNull Intent intent) {
        // Fused location sometimes has an "Unmarshalling unknown type" runtime exception on 4.4.2 devices
        Location location;
        try {
            // If a provider is enabled or disabled notify the adapters so they can update providers.
            if (intent.hasExtra(LocationManager.KEY_PROVIDER_ENABLED)) {
                Logger.debug("LocationService - One of the location providers was enabled or disabled.");
                airship.getLocationManager().onSystemLocationProvidersChanged();
                return;
            }

            location = (Location) (intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED) ?
                        intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED) :
                        intent.getParcelableExtra("com.google.android.location.LOCATION"));
        } catch (Exception e) {
            Logger.error("Unable to extract location.", e);
            return;
        }

        if (location != null) {
            airship.getLocationManager().onLocationUpdate(location);
        }
    }
}
