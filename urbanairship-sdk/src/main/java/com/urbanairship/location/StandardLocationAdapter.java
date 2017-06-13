/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.location;

import android.app.PendingIntent;
import android.content.Context;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.util.UAStringUtil;

import java.util.List;

/**
 * Location adapter for the standard Android location.
 * <p/>
 * The adapter tries to mimic the Fused Location Provider as much as possible by
 * automatically selecting the best provider based on the request settings. It
 * will reevaluate the best provider when providers are enabled and disabled.
 */
class StandardLocationAdapter implements LocationAdapter {

    private static final int REQUEST_CODE = 2;

    private static String currentProvider;

    @Override
    public void requestLocationUpdates(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent) {
        Criteria criteria = createCriteria(options);
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(pendingIntent);

        List<String> providers = locationManager.getProviders(criteria, false);
        if (providers != null) {
            for (String provider : providers) {
                Logger.verbose("StandardLocationAdapter - Update " +
                        "listening provider enable/disabled for: " + provider);
                //noinspection MissingPermission
                locationManager.requestLocationUpdates(provider, Long.MAX_VALUE, Float.MAX_VALUE, pendingIntent);
            }
        }

        String bestProvider = getBestProvider(context, criteria, options);
        if (!UAStringUtil.isEmpty(bestProvider)) {
            Logger.verbose("StandardLocationAdapter - Requesting location updates from provider: " + bestProvider);

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
    public boolean connect(@NonNull Context context) {
        return true;
    }

    @Override
    public void onSystemLocationProvidersChanged(@NonNull Context context, @NonNull LocationRequestOptions options, @NonNull PendingIntent pendingIntent) {
        Criteria criteria = createCriteria(options);
        String bestProvider = getBestProvider(context, criteria, options);

        if (!UAStringUtil.isEmpty(currentProvider) && currentProvider.equals(bestProvider)) {
            Logger.verbose("StandardLocationAdapter - Already listening for updates from the best provider: " + currentProvider);
            return;
        }

        Logger.verbose("StandardLocationAdapter - Refreshing updates, best provider might of changed.");
        requestLocationUpdates(context, options, pendingIntent);
    }

    @Override
    public int getRequestCode() {
        return REQUEST_CODE;
    }

    @Override
    public void disconnect(@NonNull Context context) {
    }


    @Override
    public void cancelLocationUpdates(@NonNull Context context, @NonNull PendingIntent pendingIntent) {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(pendingIntent);

        Logger.verbose("StandardLocationAdapter - Canceling location updates.");
        currentProvider = null;
    }

    @Override
    public void requestSingleLocation(@NonNull Context context, @NonNull LocationRequestOptions options, PendingResult<Location> pendingResult) {
        CancelableOperation cancelableOperation = new SingleLocationRequest(context, pendingResult, options);
        pendingResult.addCancelable(cancelableOperation);
        cancelableOperation.run();
    }

    /**
     * Creates a location criteria from a LocationRequestOptions.
     *
     * @param options The locationRequestOptions.
     * @return A criteria created from the supplied options.
     */
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
            if (availableProviders == null) {
                return null;
            }

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
        private Context context;

        private final AndroidLocationListener currentProviderListener;
        private final AndroidLocationListener providerEnabledListeners;
        private LocationManager locationManager;

        /**
         * SingleLocationRequest constructor.
         *
         * @param context The application context.
         * @param pendingResult The pending result.
         * @param options The locationRequestOptions.
         */
        SingleLocationRequest(@NonNull final Context context, final @NonNull PendingResult<Location> pendingResult, @NonNull final LocationRequestOptions options) {
            super();
            this.context = context.getApplicationContext();
            this.options = options;
            this.criteria = createCriteria(options);
            this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            currentProviderListener = new AndroidLocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    stopUpdates();
                    pendingResult.setResult(location);
                }

                @Override
                public void onProviderDisabled(String provider) {
                    Logger.verbose("StandardLocationAdapter - Provider disabled: " + provider);
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
                    Logger.verbose("StandardLocationAdapter - Provider enabled: " + provider);
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

        private void listenForLocationChanges(@NonNull Context context) {
            if (currentProvider != null) {
                //noinspection MissingPermission
                locationManager.removeUpdates(currentProviderListener);
            }

            String bestProvider = getBestProvider(context, criteria, options);

            currentProvider = bestProvider;

            if (bestProvider != null) {
                Logger.verbose("StandardLocationAdapter - Single request using provider: " + bestProvider);
                //noinspection MissingPermission
                locationManager.requestLocationUpdates(bestProvider, 0, 0, currentProviderListener);
            }
        }

        /**
         * Adds a listener to every provider to be notified when providers
         * are enabled/disabled.
         */
        private void listenForProvidersEnabled() {
            List<String> providers = locationManager.getProviders(criteria, false);
            if (providers != null) {
                for (String provider : providers) {
                    Logger.verbose("StandardLocationAdapter - Single location request " +
                            "listening provider enable/disabled for: " + provider);

                    //noinspection MissingPermission
                    locationManager.requestLocationUpdates(provider,
                            Long.MAX_VALUE,
                            Float.MAX_VALUE,
                            providerEnabledListeners);
                }
            }
        }

        @Override
        protected void onCancel() {
            Logger.verbose("StandardLocationAdapter - Canceling single request.");
            stopUpdates();
        }

        /**
         * Stop listening for updates.
         */
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
