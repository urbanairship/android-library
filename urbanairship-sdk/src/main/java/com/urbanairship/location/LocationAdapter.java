/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.location.Location;
import android.support.annotation.NonNull;

import com.urbanairship.PendingResult;

/**
 * The common interface for communicating with different location sources.
 */
interface LocationAdapter {
    /**
     * Requests a single location update.
     *
     * @param locationCallback The location callback.
     * @param options The location request options.
     * @return PendingLocationResult that can be used to cancel the request or set a listener for
     * when the result is available.
     */
    PendingResult<Location> requestSingleLocation(@NonNull LocationCallback locationCallback, @NonNull LocationRequestOptions options);

    /**
     * Cancels location updates.
     *
     * @param intent The pending intent used to start location updates.
     */
    void cancelLocationUpdates(@NonNull PendingIntent intent);

    /**
     * Requests location updates.
     *
     * @param options The location request options.
     * @param intent The pending intent used to start location updates.
     */
    void requestLocationUpdates(@NonNull LocationRequestOptions options, @NonNull PendingIntent intent);

    /**
     * Connects the adapter.
     *
     * @return <code>true</code> if the adapter connected,
     * <code>false</code> otherwise.
     */
    boolean connect();

    /**
     * Disconnects the adapter.
     */
    void disconnect();
}
