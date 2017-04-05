/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.location.Location;

/**
 * A location change listener. Used to listen for location updates by adding
 * the listener using {@link com.urbanairship.location.UALocationManager#addLocationListener(LocationListener)}.
 */
public interface LocationListener {

    /**
     * Called when a new location is received.
     *
     * @param location The new location.
     */
    void onLocationChanged(Location location);
}
