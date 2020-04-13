/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.location.Location;

import androidx.annotation.NonNull;

/**
 * A location change listener. Used to listen for location updates by adding
 * the listener using {@link AirshipLocationManager#addLocationListener(LocationListener)}.
 */
public interface LocationListener {

    /**
     * Called when a new location is received.
     *
     * @param location The new location.
     */
    void onLocationChanged(@NonNull Location location);

}
