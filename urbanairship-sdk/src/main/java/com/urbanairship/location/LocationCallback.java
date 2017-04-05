/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.location.Location;

import com.urbanairship.PendingResult;

/**
 * A location callback.
 */
public interface LocationCallback extends PendingResult.ResultCallback<Location> {

    /**
     * Called when a new location is received.
     *
     * @param location The new location or null if the request is unable to be made due to insufficient permissions.
     */
    @Override
    void onResult(Location location);
}
