package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.google.GooglePlayServicesUtilWrapper;

import java.util.concurrent.Semaphore;

/**
 * Location adapter for Google's fused location provider.
 *
 * @hide
 */
class FusedLocationAdapter implements LocationAdapter {

    private static final int REQUEST_CODE = 1;
    private GoogleApiClient client;

    @Override
    public void requestSingleLocation(final @NonNull Context context, final @NonNull LocationRequestOptions options, final PendingResult<Location> pendingResult) {
        if (client == null || !client.isConnected()) {
            Logger.debug("FusedLocationAdapter - Adapter is not connected. Unable to request single location.");
        }

        CancelableOperation cancelableOperation = new SingleLocationRequest(pendingResult, options);
        pendingResult.addCancelable(cancelableOperation);
        cancelableOperation.run();
    }

    @Override
    public void cancelLocationUpdates(@NonNull Context context, @NonNull PendingIntent pendingIntent) {
        if (client == null || !client.isConnected()) {
            Logger.debug("FusedLocationAdapter - Adapter is not connected. Unable to cancel location updates.");
            return;
        }

        Logger.verbose("FusedLocationAdapter - Canceling updates.");
        LocationServices.FusedLocationApi.removeLocationUpdates(client, pendingIntent);
        pendingIntent.cancel();
    }

    @Override
    public void requestLocationUpdates(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent) {
        if (client == null || !client.isConnected()) {
            Logger.debug("FusedLocationAdapter - Adapter is not connected. Unable to request location updates.");
            return;
        }

        Logger.verbose("FusedLocationAdapter - Requesting updates: " + options);
        LocationRequest locationRequest = createLocationRequest(options);

        //noinspection MissingPermission
        LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, pendingIntent);
    }

    @Override
    public boolean connect(@NonNull Context context) {
        final Semaphore semaphore = new Semaphore(0);

        try {
            int playServicesStatus = GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(context);
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.debug("FusedLocationAdapter - Google Play services is currently unavailable, unable to connect for fused location.");
                return false;
            }
        } catch (IllegalStateException e) {
            // Missing version tag
            Logger.debug("FusedLocationAdapter - Google Play services is currently unavailable, unable to connect for fused location. " + e.getMessage());
            return false;
        }

        client = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Logger.verbose("FusedLocationAdapter - Google Play services connected for fused location.");
                        semaphore.release();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Logger.verbose("FusedLocationAdapter - Google Play services connection suspended for fused location.");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
                        Logger.verbose("FusedLocationAdapter - Google Play services failed to connect for fused location.");
                        semaphore.release();
                    }
                })
                .build();

        client.connect();

        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            Logger.error("FusedLocationAdapter - Exception while connecting to fused location", ex);
            disconnect(context);
            return false;
        }

        return client.isConnected();
    }

    @Override
    public void disconnect(@NonNull Context context) {
        if (client != null && client.isConnected()) {
            client.disconnect();
        }

        // Clear the client so we don't only rely on `isConnected`, which seems to not immediately
        // be updated by disconnect().
        client = null;
    }

    @Override
    public void onSystemLocationProvidersChanged(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent) {
        // fused location handles this internally
    }

    @Override
    public int getRequestCode() {
        return REQUEST_CODE;
    }

    /**
     * Creates Google Play Service's LocationRequest from LocationRequestOptions
     *
     * @param settings The LocationRequestOptions.
     * @return A LocationRequest.
     */
    @NonNull
    private LocationRequest createLocationRequest(@NonNull LocationRequestOptions settings) {
        LocationRequest locationRequest = LocationRequest.create()
                                                         .setInterval(settings.getMinTime())
                                                         .setSmallestDisplacement(settings.getMinDistance());

        // The constants should be the same, but still map them if play services
        // changes them.
        switch (settings.getPriority()) {
            case LocationRequestOptions.PRIORITY_HIGH_ACCURACY:
                locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
                break;
            case LocationRequestOptions.PRIORITY_BALANCED_POWER_ACCURACY:
                locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
                break;
            case LocationRequestOptions.PRIORITY_LOW_POWER:
                locationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
                break;
            case LocationRequestOptions.PRIORITY_NO_POWER:
                locationRequest.setPriority(LocationRequest.PRIORITY_NO_POWER);
                break;
        }

        return locationRequest;
    }

    /**
     * Class that encapsulated the actual request to the play service's fused
     * location provider.
     */
    private class SingleLocationRequest extends CancelableOperation {

        private final LocationRequest locationRequest;
        private final com.google.android.gms.location.LocationListener fusedLocationListener;

        /**
         * FusedLocationRequest constructor.
         *
         * @param pendingResult The pending result.
         * @param options LocationRequestOptions options.
         */
        SingleLocationRequest(final PendingResult<Location> pendingResult, LocationRequestOptions options) {
            super(Looper.getMainLooper());
            this.fusedLocationListener = new com.google.android.gms.location.LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    pendingResult.setResult(location);
                }
            };

            this.locationRequest = createLocationRequest(options)
                    .setNumUpdates(1);
        }

        @Override
        protected void onCancel() {
            Logger.verbose("FusedLocationAdapter - Canceling single location request.");
            LocationServices.FusedLocationApi.removeLocationUpdates(client, fusedLocationListener);
        }

        @Override
        protected void onRun() {
            Logger.verbose("FusedLocationAdapter - Starting single location request.");
            //noinspection MissingPermission
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, fusedLocationListener, Looper.getMainLooper());
        }
    }
}
