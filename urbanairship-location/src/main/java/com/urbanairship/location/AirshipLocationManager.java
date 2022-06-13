/* Copyright Airship and Contributors */

package com.urbanairship.location;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.location.LocationEvent;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.modules.location.AirshipLocationClient;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.permission.SinglePermissionDelegate;
import com.urbanairship.util.AirshipHandlerThread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;
import androidx.core.util.Consumer;

/**
 * High level interface for interacting with location.
 */
public class AirshipLocationManager extends AirshipComponent implements AirshipLocationClient {

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
    private final AirshipChannel airshipChannel;
    private final PrivacyManager privacyManager;
    private final PermissionsManager permissionsManager;

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
     * Gets the shared location instance.
     *
     * @return The shared location instance.
     */
    @NonNull
    public static AirshipLocationManager shared() {
        return UAirship.shared().requireComponent(AirshipLocationManager.class);
    }

    /**
     * Default constructor.
     *
     * @param context The context.
     * @param preferenceDataStore The data store.
     * @param privacyManager The privacy manager.
     * @param airshipChannel The channel instance.
     * @param permissionsManager The permissions manager.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AirshipLocationManager(@NonNull Context context,
                                  @NonNull PreferenceDataStore preferenceDataStore,
                                  @NonNull PrivacyManager privacyManager,
                                  @NonNull AirshipChannel airshipChannel,
                                  @NonNull PermissionsManager permissionsManager) {
        this(context, preferenceDataStore, privacyManager, airshipChannel, permissionsManager, GlobalActivityMonitor.shared(context));
    }

    @VisibleForTesting
    AirshipLocationManager(@NonNull final Context context,
                           @NonNull PreferenceDataStore preferenceDataStore,
                           @NonNull PrivacyManager privacyManager,
                           @NonNull AirshipChannel airshipChannel,
                           @NonNull PermissionsManager permissionsManager,
                           @NonNull ActivityMonitor activityMonitor) {
        super(context, preferenceDataStore);

        this.context = context.getApplicationContext();
        this.preferenceDataStore = preferenceDataStore;
        this.privacyManager = privacyManager;
        this.listener = new ApplicationListener() {
            @Override
            public void onForeground(long time) {
                AirshipLocationManager.this.updateServiceConnection();
            }

            @Override
            public void onBackground(long time) {
                AirshipLocationManager.this.updateServiceConnection();
            }
        };
        this.activityMonitor = activityMonitor;

        Intent updateIntent = new Intent(context, LocationReceiver.class).setAction(LocationReceiver.ACTION_LOCATION_UPDATE);
        this.locationProvider = new UALocationProvider(context, updateIntent);
        this.backgroundThread = new AirshipHandlerThread("location");

        this.airshipChannel = airshipChannel;
        this.permissionsManager = permissionsManager;
    }

    @Override
    protected void init() {
        super.init();
        this.backgroundThread.start();
        this.backgroundHandler = new Handler(this.backgroundThread.getLooper());

        preferenceDataStore.addListener(preferenceChangeListener);
        activityMonitor.addApplicationListener(listener);
        updateServiceConnection();

        airshipChannel.addChannelRegistrationPayloadExtender(builder -> {
            if (privacyManager.isEnabled(PrivacyManager.FEATURE_LOCATION)) {
                return builder.setLocationSettings(isLocationUpdatesEnabled());
            }

            return builder;
        });

        privacyManager.addListener(this::updateServiceConnection);

        permissionsManager.addAirshipEnabler(permission -> {
            if (permission == Permission.LOCATION) {
                privacyManager.enable(PrivacyManager.FEATURE_LOCATION);
                setLocationUpdatesEnabled(true);
            }
        });

        permissionsManager.setPermissionDelegate(Permission.LOCATION, new SinglePermissionDelegate(Manifest.permission.ACCESS_COARSE_LOCATION));
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.LOCATION;
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
     * {@inheritDoc}
     */
    @Override
    public boolean isLocationUpdatesEnabled() {
        return preferenceDataStore.getBoolean(LOCATION_UPDATES_ENABLED_KEY, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLocationUpdatesEnabled(boolean enabled) {
        preferenceDataStore.put(LOCATION_UPDATES_ENABLED_KEY, enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBackgroundLocationAllowed() {
        return preferenceDataStore.getBoolean(BACKGROUND_UPDATES_ALLOWED_KEY, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
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

        if (!isLocationPermitted() || !privacyManager.isEnabled(PrivacyManager.FEATURE_LOCATION)) {
            pendingResult.cancel();
            return pendingResult;
        }

        pendingResult.addResultCallback(Looper.getMainLooper(), new ResultCallback<Location>() {
            @Override
            public void onResult(@Nullable Location result) {
                if (result != null) {
                    Logger.info("Received single location update: %s", result);
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
        return privacyManager.isEnabled(PrivacyManager.FEATURE_LOCATION) && isLocationUpdatesEnabled() && (isBackgroundLocationAllowed() || activityMonitor.isAppForegrounded());
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
     * {@inheritDoc}
     */
    @Override
    public boolean isOptIn() {
        return isLocationPermitted() && isLocationUpdatesEnabled() && privacyManager.isEnabled(PrivacyManager.FEATURE_LOCATION);
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

    /**
     * Records a location.
     *
     * @param location The new location.
     * @param options The location request options.
     * @param updateType The update type.
     * @deprecated Airship no longer provides historic location support.
     */
    @Deprecated
    public void recordLocation(@NonNull Location location, @Nullable LocationRequestOptions options, @LocationEvent.UpdateType int updateType) {
        // no-op
    }

}
