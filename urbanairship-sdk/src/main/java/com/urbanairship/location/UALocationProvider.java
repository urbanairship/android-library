/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.google.PlayServicesUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.app.PendingIntent.getService;

/**
 * Location provider that automatically selects between the standard android location
 * and the fused location. This class is not thread safe.
 */
class UALocationProvider {

    @Nullable
    private LocationAdapter connectedAdapter;
    private boolean isConnected = false;

    private final List<LocationAdapter> adapters = new ArrayList<>();
    private final Context context;
    private final Intent locationUpdateIntent;

    /**
     * UALocationProvider constructor.
     *
     * @param context The application context.
     * @param locationUpdateIntent The update intent to send for location responses.
     */
    UALocationProvider(@NonNull Context context, @NonNull Intent locationUpdateIntent) {
        this.context = context;
        this.locationUpdateIntent = locationUpdateIntent;

        // This is to prevent a log message saying Google Play Services is unavailable on amazon devices.
        if (PlayServicesUtils.isGooglePlayStoreAvailable(context) && PlayServicesUtils.isFusedLocationDependencyAvailable()) {
            adapters.add(new FusedLocationAdapter());
        }

        adapters.add(new StandardLocationAdapter());
    }

    @VisibleForTesting
    UALocationProvider(@NonNull Context context, @NonNull Intent locationUpdateIntent, LocationAdapter... adapters) {
        this.context = context;
        this.locationUpdateIntent = locationUpdateIntent;
        this.adapters.addAll(Arrays.asList(adapters));
    }

    /**
     * Cancels all location requests for the connected adapter's pending intent.
     */
    @WorkerThread
    void cancelRequests() {
        Logger.verbose("UALocationProvider - Canceling location requests.");
        connect();

        if (connectedAdapter == null) {
            Logger.debug("UALocationProvider - Ignoring request, connected adapter unavailable.");
            return;
        }

        try {
            PendingIntent pendingIntent = PendingIntent.getService(context, connectedAdapter.getRequestCode(), this.locationUpdateIntent, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                connectedAdapter.cancelLocationUpdates(context, pendingIntent);
            }
        } catch (Exception ex) {
            Logger.error("Unable to cancel location updates: " + ex.getMessage());
        }
    }

    /**
     * Requests location updates.
     *
     * @param options The request options.
     * @throws IllegalStateException if the provider is not connected.
     */
    @WorkerThread
    void requestLocationUpdates(@NonNull LocationRequestOptions options) {
        connect();

        if (connectedAdapter == null) {
            Logger.debug("UALocationProvider - Ignoring request, connected adapter unavailable.");
            return;
        }

        Logger.verbose("UALocationProvider - Requesting location updates: " + options);
        try {
            PendingIntent pendingIntent = PendingIntent.getService(context, connectedAdapter.getRequestCode(), this.locationUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            connectedAdapter.requestLocationUpdates(context, options, pendingIntent);
        } catch (Exception ex) {
            Logger.error("Unable to request location updates: " + ex.getMessage());
        }
    }

    /**
     * Requests a single location update.
     *
     * @param pendingResult The pending result.
     * @param options The request options.
     * @return A pending location result.
     */
    @WorkerThread
    void requestSingleLocation(final PendingResult<Location> pendingResult, @NonNull LocationRequestOptions options) {
        connect();

        if (connectedAdapter == null) {
            Logger.debug("UALocationProvider - Ignoring request, connected adapter unavailable.");
        }

        Logger.verbose("UALocationProvider - Requesting single location update: " + options);

        try {
            connectedAdapter.requestSingleLocation(context, options, pendingResult);
        } catch (Exception ex) {
            Logger.error("Unable to request location: " + ex.getMessage());
        }
    }

    /**
     * Connects to the provider.
     */
    @WorkerThread
    private void connect() {
        if (isConnected) {
            return;
        }

        for (LocationAdapter adapter : adapters) {
            Logger.verbose("UALocationProvider - Attempting to connect to location adapter: " + adapter);

            if (adapter.connect(context)) {
                Logger.verbose("UALocationProvider - Connected to location adapter: " + adapter);

                if (connectedAdapter == null) {
                    connectedAdapter = adapter;
                } else {

                    /*
                     * Need to cancel requests on all providers regardless of the current
                     * connected provider because pending intents persist between app starts
                     * and there is no way to determine what provider was used previously.
                     */

                    try {
                        PendingIntent pendingIntent = PendingIntent.getService(context, adapter.getRequestCode(), this.locationUpdateIntent, PendingIntent.FLAG_NO_CREATE);
                        if (pendingIntent != null) {
                            adapter.cancelLocationUpdates(context, pendingIntent);
                        }
                    } catch (Exception ex) {
                        Logger.error("Unable to cancel location updates: " + ex.getMessage());
                    }

                    adapter.disconnect(context);
                }
            } else {
                Logger.verbose("UALocationProvider - Failed to connect to location adapter: " + adapter);
            }
        }

        isConnected = true;
    }

    /**
     * Disconnects the provider and cancel any location requests.
     */
    void disconnect() {
        if (!isConnected) {
            return;
        }

        Logger.verbose("UALocationProvider - Disconnecting from location provider.");

        if (connectedAdapter != null) {
            connectedAdapter.disconnect(context);
            connectedAdapter = null;
        }

        isConnected = false;
    }

    /**
     * Called when a system location provider availability changes.
     *
     * @param options Current location request options.
     */
    @WorkerThread
    void onSystemLocationProvidersChanged(@NonNull LocationRequestOptions options) {
        Logger.verbose("UALocationProvider - Available location providers changed.");

        connect();

        if (connectedAdapter != null) {
            PendingIntent pendingIntent = PendingIntent.getService(context, connectedAdapter.getRequestCode(), this.locationUpdateIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            connectedAdapter.onSystemLocationProvidersChanged(context, options, pendingIntent);
        }
    }

    /**
     * Checks if updates are currently being requested or not.
     *
     * @return {@code true} if updates are being requested, otherwise {@code false}.
     */
    @WorkerThread
    boolean areUpdatesRequested() {
        connect();

        if (connectedAdapter == null) {
            return false;
        }

        return getService(context, connectedAdapter.getRequestCode(), this.locationUpdateIntent, PendingIntent.FLAG_NO_CREATE) != null;
    }
}
