/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;

import com.urbanairship.PendingResult;

/**
 * The common interface for communicating with different location sources.
 *
 * @hide
 */
public interface LocationAdapter {

    /**
     * Requests a single location update.
     *
     * @param context The application context.
     * @param options The location request options.
     * @param pendingResult The pending location result.
     * @return PendingLocationResult that can be used to cancel the request or set a listener for
     * when the result is available.
     */
    void requestSingleLocation(@NonNull Context context, @NonNull LocationRequestOptions options, PendingResult<Location> pendingResult);

    /**
     * Cancels location updates.
     *
     * @param context The application context.
     * @param pendingIntent The pending intent.
     */
    void cancelLocationUpdates(@NonNull Context context, @NonNull PendingIntent pendingIntent);

    /**
     * Requests location updates.
     *
     * @param context The application context.
     * @param options The location request options.
     * @param pendingIntent The pending intent.
     */
    void requestLocationUpdates(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent);

    /**
     * Connects the adapter.
     *
     * @param context The application context.
     * @return <code>true</code> if the adapter connected,
     * <code>false</code> otherwise.
     */
    boolean connect(@NonNull Context context);

    /**
     * Disconnects the adapter.
     *
     * @param context The application context.
     */
    void disconnect(@NonNull Context context);

    /**
     * Called when a system location provider availability changes.
     *
     * @param context The application context.
     * @param options Current location request options.
     * @param pendingIntent The pending intent.
     */
    void onSystemLocationProvidersChanged(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent);

    /**
     * Returns the adapter's request code.
     *
     * @return The adapter's request code.
     */
    int getRequestCode();

}
