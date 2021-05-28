/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;

import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.ResultCallback;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.util.PendingIntentCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * Location provider that automatically selects between the standard android location
 * and the fused location. This class is not thread safe.
 */
class UALocationProvider {

    @Nullable
    private LocationAdapter availableAdapter;
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
            adapters.add(new FusedLocationAdapter(context));
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
        Logger.verbose("Canceling location requests.");
        connect();

        if (availableAdapter == null) {
            Logger.debug("Ignoring request, connected adapter unavailable.");
            return;
        }

        try {
            PendingIntent pendingIntent = getPendingIntent(availableAdapter, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                availableAdapter.cancelLocationUpdates(context, pendingIntent);
            }
        } catch (Exception e) {
            Logger.error(e, "Unable to cancel location updates.");
        }
    }

    /**
     * Requests location updates.
     *
     * @param options The request options.
     */
    @WorkerThread
    void requestLocationUpdates(@NonNull LocationRequestOptions options) {
        connect();

        if (availableAdapter == null) {
            Logger.debug("Ignoring request, connected adapter unavailable.");
            return;
        }

        Logger.verbose("Requesting location updates: %s", options);
        try {
            PendingIntent pendingIntent = getPendingIntent(availableAdapter, PendingIntent.FLAG_UPDATE_CURRENT);
            if (pendingIntent != null) {
                availableAdapter.requestLocationUpdates(context, options, pendingIntent);
            } else {
                Logger.error("Unable to request location updates. Null pending intent.");
            }
        } catch (Exception e) {
            Logger.error(e, "Unable to request location updates.");
        }
    }

    /**
     * Requests a single location update.
     *
     * @param options The request options.
     * @return A pending location result.
     */
    @Nullable
    @WorkerThread
    Cancelable requestSingleLocation(@NonNull LocationRequestOptions options, ResultCallback<Location> resultCallback) {
        connect();

        if (availableAdapter == null) {
            Logger.debug("Ignoring request, connected adapter unavailable.");
        }

        Logger.verbose("Requesting single location update: %s", options);

        try {
            return availableAdapter.requestSingleLocation(context, options, resultCallback);
        } catch (Exception e) {
            Logger.error(e, "Unable to request location.");
        }

        return null;
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
            Logger.verbose("Attempting to connect to location adapter: %s", adapter);

            if (adapter.isAvailable(context)) {

                if (availableAdapter == null) {
                    Logger.verbose("Using adapter: %s", adapter);
                    availableAdapter = adapter;
                }

                /*
                 * Need to cancel requests on all providers regardless of the current
                 * connected provider because pending intents persist between app starts
                 * and there is no way to determine what provider was used previously.
                 */
                try {
                    PendingIntent pendingIntent = getPendingIntent(adapter, PendingIntent.FLAG_NO_CREATE);
                    if (pendingIntent != null) {
                        adapter.cancelLocationUpdates(context, pendingIntent);
                    }
                } catch (Exception e) {
                    Logger.error(e, "Unable to cancel location updates.");
                }
            } else {
                Logger.verbose("Adapter unavailable: %s", adapter);
            }
        }

        isConnected = true;
    }

    /**
     * Called when a system location provider availability changes.
     *
     * @param options Current location request options.
     */
    @WorkerThread
    void onSystemLocationProvidersChanged(@NonNull LocationRequestOptions options) {
        Logger.verbose("Available location providers changed.");

        connect();

        if (availableAdapter != null) {
            PendingIntent pendingIntent = getPendingIntent(availableAdapter, PendingIntent.FLAG_UPDATE_CURRENT);
            if (pendingIntent != null) {
                availableAdapter.onSystemLocationProvidersChanged(context, options, pendingIntent);
            }
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

        if (availableAdapter == null) {
            return false;
        }

        return getPendingIntent(availableAdapter, PendingIntent.FLAG_NO_CREATE) != null;
    }

    /**
     * Gets the pending intent for the location adapter.
     * @param adapter The adapter.
     * @param flags The pending intent flags.
     * @return
     */
    @Nullable
    PendingIntent getPendingIntent(@NonNull LocationAdapter adapter, int flags) {
        try {
            return PendingIntentCompat.getBroadcast(context, adapter.getRequestCode(), this.locationUpdateIntent, flags);
        } catch (Exception e) {
            Logger.error(e, "Unable to get pending intent.");
            return null;
        }
    }
}
