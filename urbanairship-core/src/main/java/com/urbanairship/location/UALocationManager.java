/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.LocationEvent;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.AirshipHandlerThread;

import java.util.ArrayList;
import java.util.List;

/**
 * High level interface for interacting with location.
 */
public class UALocationManager extends AirshipComponent {

    private static final String LAST_REQUESTED_LOCATION_OPTIONS_KEY = "com.urbanairship.location.LAST_REQUESTED_LOCATION_OPTIONS";
    private static final String LOCATION_UPDATES_ENABLED_KEY = "com.urbanairship.location.LOCATION_UPDATES_ENABLED";
    private static final String BACKGROUND_UPDATES_ALLOWED_KEY = "com.urbanairship.location.BACKGROUND_UPDATES_ALLOWED";
    private static final String LOCATION_OPTIONS_KEY = "com.urbanairship.location.LOCATION_OPTIONS";

    private final Context context;
    private final UALocationProvider locationProvider;
    private final ApplicationListener listener;
    private final PreferenceDataStore preferenceDataStore;
    private final ActivityMonitor activityMonitor;
    private final List<LocationListener> locationListeners = new ArrayList<>();

    @VisibleForTesting
    final HandlerThread backgroundThread;
    private Handler backgroundHandler;

    /**
     * When preferences are changed on the current process or other processes,
     * it will trigger the PreferenceChangeListener.  Instead of dealing
     * with the changes twice (one in the set method, one here), we will
     * just deal with changes when the listener notifies the manager.
     */
    private final PreferenceDataStore.PreferenceChangeListener preferenceChangeListener = new PreferenceDataStore.PreferenceChangeListener() {
        @Override
        public void onPreferenceChange(@NonNull String key) {
            switch (key) {
                case BACKGROUND_UPDATES_ALLOWED_KEY:
                case LOCATION_UPDATES_ENABLED_KEY:
                case LOCATION_OPTIONS_KEY:
                    updateServiceConnection();
                    break;
            }
        }
    };

    /**
     * Creates a UALocationManager. Normally only one UALocationManager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getLocationManager()}.
     *
     * @param context Application context
     * @param preferenceDataStore The preferences data store.
     * @hide
     */
    public UALocationManager(@NonNull final Context context, @NonNull PreferenceDataStore preferenceDataStore, @NonNull ActivityMonitor activityMonitor) {
        super(context, preferenceDataStore);

        this.context = context.getApplicationContext();
        this.preferenceDataStore = preferenceDataStore;
        this.listener = new ApplicationListener() {
            @Override
            public void onForeground(long time) {
                UALocationManager.this.updateServiceConnection();
            }

            @Override
            public void onBackground(long time) {
                UALocationManager.this.updateServiceConnection();
            }
        };
        this.activityMonitor = activityMonitor;

        Intent updateIntent = new Intent(context, LocationReceiver.class).setAction(LocationReceiver.ACTION_LOCATION_UPDATE);
        this.locationProvider = new UALocationProvider(context, updateIntent);
        this.backgroundThread = new AirshipHandlerThread("location");
    }

    @Override
    protected void init() {
        super.init();
        this.backgroundThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());

        preferenceDataStore.addListener(preferenceChangeListener);
        activityMonitor.addApplicationListener(listener);
        updateServiceConnection();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void onComponentEnableChange(boolean isEnabled) {
        updateServiceConnection();
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeApplicationListener(listener);
        backgroundThread.quit();
    }

    /**
     * Checks if continuous location updates is enabled or not.
     * <p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @return <code>true</code> if location updates are enabled, otherwise
     * <code>false</code>.
     */
    public boolean isLocationUpdatesEnabled() {
        return preferenceDataStore.getBoolean(LOCATION_UPDATES_ENABLED_KEY, false);
    }

    /**
     * Enable or disable continuous location updates.
     * <p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @param enabled If location updates should be enabled or not.
     */
    public void setLocationUpdatesEnabled(boolean enabled) {
        preferenceDataStore.put(LOCATION_UPDATES_ENABLED_KEY, enabled);
    }

    /**
     * Checks if continuous location updates are allowed to continue
     * when the application is in the background.
     *
     * @return <code>true</code> if continuous location update are allowed,
     * otherwise <code>false</code>.
     */
    public boolean isBackgroundLocationAllowed() {
        return preferenceDataStore.getBoolean(BACKGROUND_UPDATES_ALLOWED_KEY, false);
    }

    /**
     * Enable or disable allowing continuous updates to continue in
     * the background.
     *
     * @param enabled If background updates are allowed in the background or not.
     */
    public void setBackgroundLocationAllowed(boolean enabled) {
        preferenceDataStore.put(BACKGROUND_UPDATES_ALLOWED_KEY, enabled);
    }

    /**
     * Sets the location request options for continuous updates.
     *
     * @param options The location request options, or null to reset the options to
     * the default settings.
     */
    public void setLocationRequestOptions(@Nullable LocationRequestOptions options) {
        preferenceDataStore.put(LOCATION_OPTIONS_KEY, options);
    }

    /**
     * Gets the location request options for continuous updates.  If no options
     * have been set, it will default to {@link LocationRequestOptions#createDefaultOptions()}.
     *
     * @return The continuous location request options.
     */
    @NonNull
    public LocationRequestOptions getLocationRequestOptions() {
        LocationRequestOptions options = parseLocationRequests(LOCATION_OPTIONS_KEY);
        if (options == null) {
            options = LocationRequestOptions.newBuilder().build();
        }
        return options;
    }

    /**
     * Adds a listener for locations updates.  The listener will only be notified
     * of continuous location updates, not single location requests.
     *
     * @param listener A location listener.
     */
    public void addLocationListener(@NonNull LocationListener listener) {
        if (!UAirship.isMainProcess()) {
            Logger.error("Continuous location update are only available on the main process.");
            return;
        }

        synchronized (locationListeners) {
            locationListeners.add(listener);
        }
    }

    /**
     * Removes location update listener.
     *
     * @param listener A location listener.
     */
    public void removeLocationListener(@NonNull LocationListener listener) {
        synchronized (locationListeners) {
            locationListeners.remove(listener);
        }
    }

    /**
     * Records a single location using either the foreground request options
     * or the background request options depending on the application's state.
     * <p>
     * The request may fail due to insufficient permissions.
     *
     * @return A cancelable object that can be used to cancel the request.
     */
    @NonNull
    public PendingResult<Location> requestSingleLocation() {
        return requestSingleLocation(getLocationRequestOptions());
    }

    /**
     * Records a single location using custom location request options.
     *
     * @param requestOptions The location request options.
     * @return A cancelable object that can be used to cancel the request.
     */
    @NonNull
    public PendingResult<Location> requestSingleLocation(@NonNull final LocationRequestOptions requestOptions) {

        final PendingResult<Location> pendingResult = new PendingResult<>();

        if (!isLocationPermitted()) {
            pendingResult.cancel();
            return pendingResult;
        }

        pendingResult.addResultCallback(Looper.getMainLooper(), new ResultCallback<Location>() {
            @Override
            public void onResult(@Nullable Location result) {
                if (result != null) {
                    Logger.info("Received single location update: %s", result);
                    UAirship.shared().getAnalytics().recordLocation(result, requestOptions, LocationEvent.UPDATE_TYPE_SINGLE);
                }
            }
        });

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (pendingResult.isDone()) {
                    return;
                }

                Cancelable cancelable = locationProvider.requestSingleLocation(requestOptions, new ResultCallback<Location>() {
                    @Override
                    public void onResult(@Nullable Location result) {
                        pendingResult.setResult(result);
                    }
                });

                if (cancelable != null) {
                    pendingResult.addCancelable(cancelable);
                }
            }
        });

        return pendingResult;
    }

    /**
     * Updates the service connection. Handles binding and subscribing to
     * the location service.
     */
    private void updateServiceConnection() {
        if (!UAirship.isMainProcess()) {
            return;
        }

        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isComponentEnabled() && isContinuousLocationUpdatesAllowed()) {
                    LocationRequestOptions options = getLocationRequestOptions();
                    LocationRequestOptions lastLocationOptions = getLastUpdateOptions();

                    if (!options.equals(lastLocationOptions) || !locationProvider.areUpdatesRequested()) {
                        Logger.info("Requesting location updates");

                        locationProvider.requestLocationUpdates(options);
                        setLastUpdateOptions(options);
                    }
                } else {
                    if (locationProvider.areUpdatesRequested()) {
                        Logger.info("Stopping location updates.");
                        locationProvider.cancelRequests();
                    }
                }
            }
        });
    }

    /**
     * Sets the last update's location request options.
     *
     * @param lastUpdateOptions The last update's location request options.
     */
    void setLastUpdateOptions(@Nullable LocationRequestOptions lastUpdateOptions) {
        preferenceDataStore.put(LAST_REQUESTED_LOCATION_OPTIONS_KEY, lastUpdateOptions);
    }

    /**
     * Gets the last update's location request options.  If no options have been set, it will default to null.
     *
     * @return The last update's location request options.
     */
    @Nullable
    LocationRequestOptions getLastUpdateOptions() {
        return parseLocationRequests(LAST_REQUESTED_LOCATION_OPTIONS_KEY);
    }

    /**
     * Checks if location updates should be enabled.
     *
     * @return <code>true</code> if location updates should be enabled,
     * otherwise <code>false</code>.
     */
    boolean isContinuousLocationUpdatesAllowed() {
        return isLocationUpdatesEnabled() && (isBackgroundLocationAllowed() || activityMonitor.isAppForegrounded());
    }

    /**
     * Checks for location permissions in the manifest.
     *
     * @return <code>true</code> if location is allowed,
     * otherwise <code>false</code>.
     */
    boolean isLocationPermitted() {
        try {
            int fineLocationPermissionCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION);
            int coarseLocationPermissionCheck = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION);
            return fineLocationPermissionCheck == PackageManager.PERMISSION_GRANTED || coarseLocationPermissionCheck == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            Logger.error(e, "UALocationManager - Unable to retrieve location permissions.");
            return false;
        }
    }

    /**
     * Called by {@link LocationReceiver} when a new continuous location update is available.
     *
     * @param location The location update.
     */
    void onLocationUpdate(@NonNull final Location location) {
        if (!isContinuousLocationUpdatesAllowed()) {
            return;
        }

        Logger.info("Received location update: %s", location);

        // Notify the listeners of the new location
        synchronized (locationListeners) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    for (LocationListener listener : new ArrayList<>(locationListeners)) {
                        listener.onLocationChanged(location);
                    }
                }
            });
        }

        // Record the location
        UAirship.shared()
                .getAnalytics()
                .recordLocation(location, getLocationRequestOptions(), LocationEvent.UPDATE_TYPE_CONTINUOUS);
    }

    /**
     * Called by {@link LocationReceiver} when a location provider changes.
     */
    void onSystemLocationProvidersChanged() {
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                locationProvider.onSystemLocationProvidersChanged(getLocationRequestOptions());
            }
        });
    }

    /**
     * Returns {@code true} if location is permitted and the location manager updates are enabled, otherwise {@code false}.
     *
     * @return <{@code true} if location is permitted and the location manager updates are enabled, otherwise {@code false}.
     */
    public boolean isOptIn() {
        return isLocationPermitted() && isLocationUpdatesEnabled();
    }

    /**
     * Helper method to parse {@link LocationRequestOptions} from the preference data store.
     *
     * @param key The preference key.
     * @return The parsed location requests options or null.
     */
    @Nullable
    private LocationRequestOptions parseLocationRequests(@NonNull String key) {
        JsonValue jsonValue = preferenceDataStore.getJsonValue(key);
        if (jsonValue.isNull()) {
            return null;
        }

        try {
            return LocationRequestOptions.fromJson(jsonValue);
        } catch (JsonException e) {
            Logger.error(e, "UALocationManager - Failed parsing LocationRequestOptions from JSON.");
        } catch (IllegalArgumentException e) {
            Logger.error(e, "UALocationManager - Invalid LocationRequestOptions from JSON.");
        }

        return null;
    }

}
