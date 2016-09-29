package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.google.GooglePlayServicesUtilWrapper;

import java.util.concurrent.Semaphore;

/**
 * Location adapter for Google's fused location provider.
 */
class FusedLocationAdapter implements LocationAdapter {

    private static final int FUSED_LOCATION_ADAPTER_INTENT_FLAG = 1;

    private final Context context;
    private final Intent locationUpdatesIntent;

    private GoogleApiClient client;

    /**
     * Creates a fused location adapter.
     *
     * @param context The application context.
     */
    FusedLocationAdapter(Context context) {
        this.context = context;
        this.locationUpdatesIntent = new Intent(context, LocationService.class).setAction(UALocationProvider.ACTION_LOCATION_UPDATE);
    }

    @Override
    public PendingResult<Location> requestSingleLocation(@NonNull LocationCallback locationCallback, @NonNull LocationRequestOptions options) {
        if (client == null || !client.isConnected()) {
            Logger.debug("FusedLocationAdapter - Adapter is not connected. Unable to request single location.");
            return null;
        }
        return new SingleLocationRequest(locationCallback, options);
    }

    @Override
    public void cancelLocationUpdates() {
        PendingIntent pendingIntent = PendingIntent.getService(context, FUSED_LOCATION_ADAPTER_INTENT_FLAG, locationUpdatesIntent, PendingIntent.FLAG_NO_CREATE);
        if (pendingIntent == null) {
            return;
        }

        if (client == null || !client.isConnected()) {
            Logger.debug("FusedLocationAdapter - Adapter is not connected. Unable to cancel location updates.");
            return;
        }

        Logger.verbose("FusedLocationAdapter - Canceling updates.");
        LocationServices.FusedLocationApi.removeLocationUpdates(client, pendingIntent);
        pendingIntent.cancel();
    }

    @Override
    public void requestLocationUpdates(@NonNull LocationRequestOptions options) {
        PendingIntent intent = PendingIntent.getService(context, FUSED_LOCATION_ADAPTER_INTENT_FLAG, locationUpdatesIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (client == null || !client.isConnected()) {
            Logger.debug("FusedLocationAdapter - Adapter is not connected. Unable to request location updates.");
            return;
        }

        Logger.verbose("FusedLocationAdapter - Requesting updates: " + options);
        LocationRequest locationRequest = createLocationRequest(options);

        //noinspection MissingPermission
        LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, intent);
    }

    @Override
    public boolean connect() {
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
            disconnect();
            return false;
        }

        return client.isConnected();
    }

    @Override
    public void disconnect() {
        if (client != null && client.isConnected()) {
            client.disconnect();
        }

        // Clear the client so we don't only rely on `isConnected`, which seems to not immediately
        // be updated by disconnect().
        client = null;
    }

    @Override
    public void onSystemLocationProvidersChanged(@NonNull LocationRequestOptions options) {
        // fused location handles this internally
    }

    @Override
    public boolean isUpdatesRequested() {
        return PendingIntent.getService(context, FUSED_LOCATION_ADAPTER_INTENT_FLAG, locationUpdatesIntent, PendingIntent.FLAG_NO_CREATE) != null;
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
    private class SingleLocationRequest extends PendingResult<Location> {

        private final LocationRequest locationRequest;
        private final com.google.android.gms.location.LocationListener fusedLocationListener;

        /**
         * FusedLocationRequest constructor.
         *
         * @param locationCallback The location callback.
         * @param options LocationRequestOptions options.
         */
        SingleLocationRequest(LocationCallback locationCallback, LocationRequestOptions options) {
            super(locationCallback);
            this.locationRequest = createLocationRequest(options);
            locationRequest.setNumUpdates(1);

            this.fusedLocationListener = new com.google.android.gms.location.LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    setResult(location);
                }
            };

            Logger.verbose("FusedLocationAdapter - Starting single location request.");

            //noinspection MissingPermission
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, fusedLocationListener, Looper.myLooper());
        }

        @Override
        protected void onCancel() {
            Logger.verbose("FusedLocationAdapter - Canceling single location request.");
            LocationServices.FusedLocationApi.removeLocationUpdates(client, fusedLocationListener);
        }
    }
}
