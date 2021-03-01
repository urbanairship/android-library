
package com.urbanairship.location;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.urbanairship.Cancelable;
import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.ResultCallback;
import com.urbanairship.google.GooglePlayServicesUtilWrapper;

import androidx.annotation.NonNull;

/**
 * Location adapter for Google's fused location provider.
 *
 * @hide
 */
class FusedLocationAdapter implements LocationAdapter {

    private static final int REQUEST_CODE = 1;
    private final FusedLocationProviderClient client;

    public FusedLocationAdapter(Context context) {
        this.client = LocationServices.getFusedLocationProviderClient(context);
    }

    @NonNull
    @Override
    public Cancelable requestSingleLocation(final @NonNull Context context, final @NonNull LocationRequestOptions options, @NonNull final ResultCallback<Location> resultCallback) {
        CancelableOperation cancelableOperation = new SingleLocationRequest(options, resultCallback);
        cancelableOperation.run();
        return cancelableOperation;
    }

    @Override
    public void cancelLocationUpdates(@NonNull Context context, @NonNull PendingIntent pendingIntent) {
        Logger.verbose("Canceling updates.");
        client.removeLocationUpdates(pendingIntent);
        pendingIntent.cancel();
    }

    @SuppressLint("MissingPermission")
    @Override
    public void requestLocationUpdates(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent) {
        Logger.verbose("Requesting updates: %s", options);
        LocationRequest locationRequest = createLocationRequest(options);

        client.requestLocationUpdates(locationRequest, pendingIntent);
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        try {
            int playServicesStatus = GooglePlayServicesUtilWrapper.isGooglePlayServicesAvailable(context);
            if (ConnectionResult.SUCCESS != playServicesStatus) {
                Logger.debug("Google Play services is currently unavailable, unable to connect for fused location.");
                return false;
            }
        } catch (IllegalStateException e) {
            // Missing version tag
            Logger.debug(e, "Google Play services is currently unavailable, unable to connect for fused location.");
            return false;
        }

        return true;
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
        private final LocationCallback locationCallback;

        /**
         * FusedLocationRequest constructor.
         *
         * @param resultCallback The result callback.
         * @param options LocationRequestOptions options.
         */
        SingleLocationRequest(@NonNull LocationRequestOptions options, @NonNull final ResultCallback<Location> resultCallback) {
            super(Looper.getMainLooper());
            this.locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    resultCallback.onResult(locationResult.getLastLocation());
                }
            };

            this.locationRequest = createLocationRequest(options)
                    .setNumUpdates(1);
        }

        @Override
        protected void onCancel() {
            Logger.verbose("Canceling single location request.");
            client.removeLocationUpdates(locationCallback);

        }

        @SuppressLint("MissingPermission")
        @Override
        protected void onRun() {
            Logger.verbose("Starting single location request.");
            client.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }

    }

}
