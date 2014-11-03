package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Looper;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;

import java.util.concurrent.Semaphore;

/**
 * Location adapter for Google's fused location provider.
 */
class FusedLocationAdapter implements LocationAdapter {

    private Context context;
    private GoogleApiClient client;

    /**
     * Creates a fused location adapter.
     *
     * @param context The application context.
     */
    FusedLocationAdapter(Context context) {
        this.context = context;
    }

    @Override
    public PendingResult<Location> requestSingleLocation(LocationRequestOptions options) {
        return new SingleLocationRequest(options);
    }

    @Override
    public void cancelLocationUpdates(PendingIntent intent) {
        Logger.verbose("Fused location canceling updates.");
        LocationServices.FusedLocationApi.removeLocationUpdates(client, intent);
    }

    @Override
    public void requestLocationUpdates(LocationRequestOptions options, PendingIntent intent) {
        Logger.verbose("Fused location requesting updates.");
        LocationRequest locationRequest = createLocationRequest(options);
        LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, intent);
    }

    @Override
    public boolean connect() {
        final Semaphore semaphore = new Semaphore(0);


        try {
            int playServicesStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.debug("Google Play services is currently unavailable, unable to connect for fused location.");
                return false;
            }
        } catch (IllegalStateException e) {
            // Missing version tag
            Logger.debug("Google Play services is currently unavailable, unable to connect for fused location. " + e.getMessage());
            return false;
        }


        client = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle bundle) {
                        Logger.verbose("Google Play services connected for fused location.");
                        semaphore.release();
                    }

                    @Override
                    public void onConnectionSuspended(int i) {
                        Logger.verbose("Google Play services connection suspended for fused location.");
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult connectionResult) {
                        Logger.verbose("Google Play services failed to connect for fused location.");
                        client = null;
                        semaphore.release();
                    }
                })
                .build();

        client.connect();

        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            Logger.error("Exception while connecting to fused location", ex);
            client.disconnect();
            return false;
        }

        return client.isConnected();
    }

    @Override
    public void disconnect() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
    }

    /**
     * Creates Google Play Service's LocationRequest from LocationRequestOptions
     *
     * @param settings The LocationRequestOptions.
     * @return A LocationRequest.
     */
    private LocationRequest createLocationRequest(LocationRequestOptions settings) {
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
    private class SingleLocationRequest extends PendingLocationResult {

        private LocationRequest locationRequest;
        private com.google.android.gms.location.LocationListener fusedLocationListener;

        /**
         * FusedLocationRequest constructor.
         *
         * @param options LocationRequestOptions options.
         */
        SingleLocationRequest(LocationRequestOptions options) {
            this.locationRequest = createLocationRequest(options);
            locationRequest.setNumUpdates(1);

            this.fusedLocationListener = new com.google.android.gms.location.LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    setResult(location);
                }
            };

            Logger.verbose("Fused location starting single location request.");
            LocationServices.FusedLocationApi.requestLocationUpdates(client, locationRequest, fusedLocationListener, Looper.myLooper());
        }

        @Override
        protected void onCancel() {
            Logger.verbose("Fused location canceling single location request.");
            LocationServices.FusedLocationApi.removeLocationUpdates(client, fusedLocationListener);
        }
    }
}
