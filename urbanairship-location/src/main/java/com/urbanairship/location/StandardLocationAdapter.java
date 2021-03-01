/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import com.urbanairship.Cancelable;
import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.ResultCallback;
import com.urbanairship.util.UAStringUtil;

import java.util.List;

import androidx.annotation.NonNull;

/**
 * Location adapter for the standard Android location.
 * <p>
 * The adapter tries to mimic the Fused Location Provider as much as possible by
 * automatically selecting the best provider based on the request settings. It
 * will reevaluate the best provider when providers are enabled and disabled.
 */
class StandardLocationAdapter implements LocationAdapter {

    private static final int REQUEST_CODE = 2;

    private static String currentProvider;

    @SuppressLint("MissingPermission")
    @Override
    public void requestLocationUpdates(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent) {
        Criteria criteria = createCriteria(options);
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(pendingIntent);

        List<String> providers = locationManager.getProviders(criteria, false);
        for (String provider : providers) {
            Logger.verbose("Update listening provider enable/disabled for: %s", provider);
            //noinspection MissingPermission
            locationManager.requestLocationUpdates(provider, Long.MAX_VALUE, Float.MAX_VALUE, pendingIntent);
        }

        String bestProvider = getBestProvider(context, criteria, options);
        if (!UAStringUtil.isEmpty(bestProvider)) {
            Logger.verbose("Requesting location updates from provider: %s", bestProvider);

            currentProvider = bestProvider;

            //noinspection MissingPermission
            locationManager.requestLocationUpdates(
                    bestProvider,
                    options.getMinTime(),
                    options.getMinDistance(),
                    pendingIntent);
        }
    }

    @Override
    public boolean isAvailable(@NonNull Context context) {
        return true;
    }

    @Override
    public void onSystemLocationProvidersChanged(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent) {
        Criteria criteria = createCriteria(options);
        String bestProvider = getBestProvider(context, criteria, options);

        if (!UAStringUtil.isEmpty(currentProvider) && currentProvider.equals(bestProvider)) {
            Logger.verbose("Already listening for updates from the best provider: %s", currentProvider);
            return;
        }

        Logger.verbose("Refreshing updates, best provider might of changed.");
        requestLocationUpdates(context, options, pendingIntent);
    }

    @Override
    public int getRequestCode() {
        return REQUEST_CODE;
    }

    @Override
    public void cancelLocationUpdates(@NonNull Context context, @NonNull PendingIntent pendingIntent) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(pendingIntent);

        Logger.verbose("Canceling location updates.");
        currentProvider = null;
    }

    @NonNull
    @Override
    public Cancelable requestSingleLocation(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull ResultCallback<Location> resultCallback) {
        CancelableOperation cancelableOperation = new SingleLocationRequest(context, options, resultCallback);
        cancelableOperation.run();
        return cancelableOperation;
    }

    /**
     * Creates a location criteria from a LocationRequestOptions.
     *
     * @param options The locationRequestOptions.
     * @return A criteria created from the supplied options.
     */
    @NonNull
    private Criteria createCriteria(@NonNull LocationRequestOptions options) {

        Criteria criteria = new Criteria();

        switch (options.getPriority()) {
            case LocationRequestOptions.PRIORITY_HIGH_ACCURACY:
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setPowerRequirement(Criteria.POWER_HIGH);
                break;
            case LocationRequestOptions.PRIORITY_BALANCED_POWER_ACCURACY:
                criteria.setAccuracy(Criteria.ACCURACY_COARSE);
                criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
                break;
            case LocationRequestOptions.PRIORITY_LOW_POWER:
            case LocationRequestOptions.PRIORITY_NO_POWER:
                criteria.setAccuracy(Criteria.NO_REQUIREMENT);
                criteria.setPowerRequirement(Criteria.POWER_LOW);
                break;
        }

        return criteria;
    }

    /**
     * Gets the best provider for the given criteria and location request options.
     *
     * @param context The application context.
     * @param criteria The criteria that the returned providers must match.
     * @param options The location request options.
     * @return The best provider, or null if one does not exist.
     */
    private String getBestProvider(@NonNull Context context, @NonNull Criteria criteria, @NonNull LocationRequestOptions options) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        if (options.getPriority() == LocationRequestOptions.PRIORITY_NO_POWER) {
            List<String> availableProviders = locationManager.getProviders(criteria, true);
            if (availableProviders.contains(LocationManager.PASSIVE_PROVIDER)) {
                return LocationManager.PASSIVE_PROVIDER;
            } else {
                return null;
            }
        } else {
            return locationManager.getBestProvider(criteria, true);
        }
    }

    /**
     * Class that encapsulated the actual request to the standard Android
     * location.
     */
    private class SingleLocationRequest extends CancelableOperation {

        private final Criteria criteria;
        private final LocationRequestOptions options;
        private String currentProvider = null;
        private final Context context;

        private final AndroidLocationListener currentProviderListener;
        private final AndroidLocationListener providerEnabledListeners;
        private final LocationManager locationManager;

        /**
         * SingleLocationRequest constructor.
         *
         * @param context The application context.
         * @param options The locationRequestOptions.
         * @param resultCallback The result callback.
         */
        SingleLocationRequest(@NonNull final Context context, @NonNull final LocationRequestOptions options, @NonNull final ResultCallback<Location> resultCallback) {
            super();
            this.context = context.getApplicationContext();
            this.options = options;
            this.criteria = createCriteria(options);
            this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            currentProviderListener = new AndroidLocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    stopUpdates();
                    resultCallback.onResult(location);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    Logger.verbose("Provider disabled: %s", provider);
                    synchronized (SingleLocationRequest.this) {
                        if (!isDone()) {
                            listenForLocationChanges(context);
                        }
                    }
                }
            };

            providerEnabledListeners = new AndroidLocationListener() {
                @Override
                public void onProviderEnabled(String provider) {
                    Logger.verbose("Provider enabled: %s", provider);
                    synchronized (SingleLocationRequest.this) {
                        if (!isDone()) {
                            String bestProvider = getBestProvider(context, criteria, options);
                            if (bestProvider != null && !bestProvider.equals(currentProvider)) {
                                listenForLocationChanges(context);
                            }
                        }
                    }
                }
            };
        }

        @Override
        protected void onRun() {
            if (options.getPriority() != LocationRequestOptions.PRIORITY_NO_POWER) {
                listenForProvidersEnabled();
            }

            listenForLocationChanges(context);
        }

        @SuppressLint("MissingPermission")
        private void listenForLocationChanges(@NonNull Context context) {
            if (currentProvider != null) {
                //noinspection MissingPermission
                locationManager.removeUpdates(currentProviderListener);
            }

            String bestProvider = getBestProvider(context, criteria, options);

            currentProvider = bestProvider;

            if (bestProvider != null) {
                Logger.verbose("Single request using provider: %s", bestProvider);
                //noinspection MissingPermission
                locationManager.requestLocationUpdates(bestProvider, 0, 0, currentProviderListener);
            }
        }

        /**
         * Adds a listener to every provider to be notified when providers
         * are enabled/disabled.
         */
        @SuppressLint("MissingPermission")
        private void listenForProvidersEnabled() {
            List<String> providers = locationManager.getProviders(criteria, false);
            for (String provider : providers) {
                Logger.verbose("Single location request listening provider enable/disabled for: %s", provider);

                //noinspection MissingPermission
                locationManager.requestLocationUpdates(provider,
                        Long.MAX_VALUE,
                        Float.MAX_VALUE,
                        providerEnabledListeners);
            }
        }

        @Override
        protected void onCancel() {
            Logger.verbose("Canceling single request.");
            stopUpdates();
        }

        /**
         * Stop listening for updates.
         */
        @SuppressLint("MissingPermission")
        private void stopUpdates() {
            //noinspection MissingPermission
            locationManager.removeUpdates(currentProviderListener);

            //noinspection MissingPermission
            locationManager.removeUpdates(providerEnabledListeners);
        }

    }

    /**
     * Android location listener used to listen for changes on the current best provider.
     */
    private static class AndroidLocationListener implements android.location.LocationListener {

        @Override
        public void onLocationChanged(Location location) {
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

    }

}
