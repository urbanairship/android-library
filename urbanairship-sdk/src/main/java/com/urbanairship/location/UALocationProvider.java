/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.google.PlayServicesUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Location provider that automatically selects between the standard android location
 * and the fused location.  This class is not thread safe.
 */
class UALocationProvider {

    private final List<LocationAdapter> adapters = new ArrayList<>();
    private LocationAdapter connectedAdapter;
    private boolean isConnected = false;

    /**
     * UALocationProvider constructor.
     *
     * @param context The application context.
     */
    public UALocationProvider(@NonNull Context context) {
        // This is to prevent a log message saying Google Play Services is unavailable on amazon devices.
        if (PlayServicesUtils.isGooglePlayStoreAvailable(context) && PlayServicesUtils.isFusedLocationDependencyAvailable()) {
            adapters.add(new FusedLocationAdapter(context));
        }

        adapters.add(new StandardLocationAdapter(context));
    }

    UALocationProvider(LocationAdapter... adapters) {
        this.adapters.addAll(Arrays.asList(adapters));
    }

    /**
     * Cancels all location requests for a given pending intent.
     *
     * @param intent The intent to cancel.
     */
    public void cancelRequests(@NonNull PendingIntent intent) {
        Logger.verbose("UALocationProvider - Canceling location requests.");

        /*
         * Need to cancel requests on all providers regardless of the current
         * connected provider because pending intents persist between app starts
         * and there is no way to determine what provider was used previously.
         */
        for (LocationAdapter adapter : adapters) {
            Logger.verbose("UALocationProvider - Canceling location requests for adapter: " + adapter);

            if (adapter == connectedAdapter || adapter.connect()) {
                try {
                    adapter.cancelLocationUpdates(intent);
                } catch (Exception ex) {
                    Logger.verbose("Unable to cancel location updates: " + ex.getMessage());
                }
            }

            if (adapter != connectedAdapter) {
                adapter.disconnect();
            }
        }
    }

    /**
     * Requests location updates.
     *
     * @param options The request options.
     * @param intent A pending intent to be sent for each location update.
     * @throws IllegalStateException if the provider is not connected.
     */
    public void requestLocationUpdates(@NonNull LocationRequestOptions options, @NonNull PendingIntent intent) {
        if (!isConnected) {
            throw new IllegalStateException("Provider must be connected before making requests.");
        }

        if (connectedAdapter == null) {
            Logger.debug("UALocationProvider - Ignoring request, connected adapter unavailable.");
            return;
        }

        Logger.verbose("UALocationProvider - Requesting location updates: " + options);
        try {
            connectedAdapter.requestLocationUpdates(options, intent);
        } catch (Exception ex) {
            Logger.error("Unable to request location updates: " + ex.getMessage());
        }
    }

    /**
     * Requests a single location update.
     *
     * @param locationCallback The location callback.
     * @param options The request options.
     * @return A pending location result.
     * @throws IllegalStateException if the provider is not connected.
     */
    @Nullable
    public PendingResult<Location> requestSingleLocation(@NonNull LocationCallback locationCallback, @NonNull LocationRequestOptions options) {
        if (!isConnected) {
            throw new IllegalStateException("Provider must be connected before making requests.");
        }

        if (connectedAdapter == null) {
            Logger.debug("UALocationProvider - Ignoring request, connected adapter unavailable.");
            return null;
        }

        Logger.verbose("UALocationProvider - Requesting single location update: " + options);

        try {
            return connectedAdapter.requestSingleLocation(locationCallback, options);
        } catch (Exception ex) {
            Logger.error("Unable to request location: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Connects to the provider. This method blocks.
     */
    public void connect() {
        if (isConnected) {
            return;
        }

        for (LocationAdapter adapter : adapters) {
            Logger.verbose("UALocationProvider - Attempting to connect to location adapter: " + adapter);
            if (adapter.connect()) {
                Logger.verbose("UALocationProvider - Connected to location adapter: " + adapter);
                connectedAdapter = adapter;
                break;
            } else {
                Logger.verbose("UALocationProvider - Failed to connect to location adapter: " + adapter);
            }
        }

        isConnected = true;
    }

    /**
     * Disconnects the provider and cancel any location requests.
     */
    public void disconnect() {
        if (!isConnected) {
            return;
        }

        Logger.verbose("UALocationProvider - Disconnecting from location provider.");

        if (connectedAdapter != null) {
            connectedAdapter.disconnect();
            connectedAdapter = null;
        }

        isConnected = false;
    }

    /**
     * Called when a system location provider availability changes.
     *
     * @param options Current location request options.
     * @param intent The pending intent used to start location updates.
     */
    public void onSystemLocationProvidersChanged(@NonNull LocationRequestOptions options, @NonNull PendingIntent intent) {
        Logger.verbose("UALocationProvider - Available location providers changed.");

        if (!isConnected) {
            return;
        }

        if (connectedAdapter == null) {
            return;
        }

        connectedAdapter.onSystemLocationProvidersChanged(options, intent);
    }
}
